package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ProtocolState implements AutoCloseable {
    static final String LEASE_HEADER = "X-MCP-Control-Lease";
    static final String REQUEST_ID_HEADER = "X-MCP-Request-Id";
    static final String PROTOCOL_HEADER = "X-MCP-Protocol-Version";
    static final String PROTOCOL_VERSION = "v0";
    static final String DEADLINE_HEADER = "X-MCP-Deadline-Ms";
    static final String IDEMPOTENCY_HEADER = "X-MCP-Idempotency-Key";
    static final String EXPECTED_SCREEN_HEADER = "X-MCP-Expected-Screen-Revision";
    static final String EXPECTED_MENU_HEADER = "X-MCP-Expected-Menu-Revision";
    static final String DEBUG_ARM_HEADER = "X-MCP-Debug-Arm";

    private static final Set<String> DEFAULT_SCOPES = Set.of(
            "read", "ui", "input", "capture", "event", "diagnostics", "control", "command");

    private final Set<String> scopes;
    private final String principalId;
    private final Consumer<String> inputCleanup;
    private final ScheduledExecutorService scheduler;
    private final Map<String, CompletableFuture<JsonObject>> idempotentResults = new ConcurrentHashMap<>();
    private final Map<String, Operation> operations = new ConcurrentHashMap<>();
    private final Map<String, DeepObservationRequestContext> deepObservations = new ConcurrentHashMap<>();
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private final Deque<JsonObject> providerAudit = new ArrayDeque<>();
    private final AtomicLong auditSequence = new AtomicLong();
    private ControlLease lease;
    private DebugArm debugArm;

    ProtocolState(Set<String> scopes, String principalId, Consumer<String> inputCleanup) {
        this.scopes = Set.copyOf(scopes);
        this.principalId = principalId;
        this.inputCleanup = inputCleanup;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-control");
            thread.setDaemon(true);
            return thread;
        });
    }

    static Set<String> configuredScopes() {
        String configured = System.getProperty("minecraft.protocol.scopes");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("MCP_RUNTIME_SCOPES");
        }
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SCOPES;
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            if (!value.isBlank()) scopes.add(value.trim());
        }
        return scopes;
    }

    void requireScope(String scope) {
        if (!this.scopes.contains(scope)) {
            throw new ProtocolException("SCOPE_DENIED", 403, "Required scope is not granted: " + scope);
        }
    }

    DeepObservationRequestContext deepObservationContext(
            RequestMetadata metadata, String connectionId) {
        return new DeepObservationRequestContext(
                this.scopes,
                this.principalId,
                metadata.requestId(),
                connectionId,
                metadata.deadlineAtMillis(),
                this::auditProvider);
    }

    void registerDeepObservation(
            String requestId, DeepObservationRequestContext requestContext) {
        DeepObservationRequestContext previous = this.deepObservations.putIfAbsent(
                requestId, requestContext);
        if (previous != null) {
            throw new ProtocolException(
                    "DUPLICATE_ACTIVE_REQUEST_ID", 409,
                    "A Deep Observation with this request ID is already active");
        }
    }

    void unregisterDeepObservation(
            String requestId, DeepObservationRequestContext requestContext) {
        this.deepObservations.remove(requestId, requestContext);
    }

    JsonObject cancelDeepObservation(String requestId) {
        DeepObservationRequestContext requestContext = this.deepObservations.remove(requestId);
        JsonObject json = new JsonObject();
        json.addProperty("type", "request.cancellation");
        json.addProperty("requestId", requestId);
        if (requestContext == null) {
            json.addProperty("status", "already_terminal_or_unknown");
            return json;
        }
        requestContext.cancel("explicit_request_cancel");
        json.addProperty("status", "cancelled");
        json.addProperty("pendingProviderWork", requestContext.pendingCount());
        return json;
    }

    synchronized JsonObject acquireLease(long requestedTtlMillis) {
        long now = System.currentTimeMillis();
        this.expireLeaseIfNeeded(now);
        if (this.lease != null) {
            throw new ProtocolException("CONTROL_LEASE_CONFLICT", 409, "An input control lease is already active");
        }
        long ttl = Math.max(1_000L, Math.min(requestedTtlMillis, 60_000L));
        this.lease = new ControlLease(UUID.randomUUID().toString(), now + ttl);
        this.scheduleLeaseExpiry(this.lease);
        return this.leaseJson("acquired");
    }

    synchronized JsonObject renewLease(String leaseId, long requestedTtlMillis) {
        this.requireLease(leaseId);
        long ttl = Math.max(1_000L, Math.min(requestedTtlMillis, 60_000L));
        this.lease = new ControlLease(this.lease.id(), System.currentTimeMillis() + ttl);
        this.scheduleLeaseExpiry(this.lease);
        return this.leaseJson("renewed");
    }

    synchronized JsonObject releaseLease(String leaseId, String reason) {
        this.requireLease(leaseId);
        this.lease = null;
        this.cancelLeaseBoundOperations(reason);
        this.inputCleanup.accept(reason);
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", "released");
        json.addProperty("reason", reason);
        return json;
    }

    synchronized boolean releaseLeaseIfMatches(String leaseId, String reason) {
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease == null || leaseId == null || !this.lease.id().equals(leaseId)) return false;
        this.lease = null;
        this.cancelLeaseBoundOperations(reason);
        this.inputCleanup.accept(reason);
        return true;
    }

    synchronized JsonObject emergencyRelease(String reason) {
        boolean hadLease = this.lease != null;
        this.lease = null;
        this.cancelLeaseBoundOperations(reason);
        this.inputCleanup.accept(reason);
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", "released");
        json.addProperty("hadLease", hadLease);
        json.addProperty("reason", reason);
        return json;
    }

    synchronized JsonObject leaseStatus() {
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease == null) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "control.lease");
            json.addProperty("status", "available");
            return json;
        }
        return this.leaseJson("active");
    }

    synchronized JsonObject armDebug(String expectedFingerprint, String currentFingerprint, long requestedTtlMillis) {
        if (currentFingerprint == null || !currentFingerprint.equals(expectedFingerprint)) {
            throw new ProtocolException("WORLD_FINGERPRINT_MISMATCH", 409, "World fingerprint does not match");
        }
        long ttl = Math.max(1_000L, Math.min(requestedTtlMillis, 60_000L));
        this.debugArm = new DebugArm(UUID.randomUUID().toString(), currentFingerprint, System.currentTimeMillis() + ttl);
        this.scheduleDebugExpiry(this.debugArm);
        return this.debugArmJson("armed");
    }

    synchronized JsonObject renewDebug(String debugArmId, String currentFingerprint, long requestedTtlMillis) {
        this.requireDebugArm(debugArmId, currentFingerprint);
        long ttl = Math.max(1_000L, Math.min(requestedTtlMillis, 60_000L));
        this.debugArm = new DebugArm(this.debugArm.id(), currentFingerprint, System.currentTimeMillis() + ttl);
        this.scheduleDebugExpiry(this.debugArm);
        return this.debugArmJson("renewed");
    }

    synchronized JsonObject disarmDebug(String reason) {
        boolean wasArmed = this.debugArm != null;
        this.debugArm = null;
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.arm");
        json.addProperty("status", "disarmed");
        json.addProperty("wasArmed", wasArmed);
        json.addProperty("reason", reason);
        return json;
    }

    synchronized JsonObject debugStatus() {
        this.expireDebugIfNeeded(System.currentTimeMillis());
        if (this.debugArm == null) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "debug.arm");
            json.addProperty("status", "disarmed");
            return json;
        }
        return this.debugArmJson("armed");
    }

    synchronized void requireDebugArm(String debugArmId, String currentFingerprint) {
        this.expireDebugIfNeeded(System.currentTimeMillis());
        if (this.debugArm == null || debugArmId == null || !this.debugArm.id().equals(debugArmId)) {
            throw new ProtocolException("DEBUG_ARM_REQUIRED", 409, "A valid Debug Arm is required");
        }
        if (!this.debugArm.worldFingerprint().equals(currentFingerprint)) {
            this.debugArm = null;
            throw new ProtocolException("WORLD_FINGERPRINT_MISMATCH", 409, "Debug Arm belongs to another world");
        }
    }

    synchronized void requireLease(String leaseId) {
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease == null || leaseId == null || !this.lease.id().equals(leaseId)) {
            throw new ProtocolException("CONTROL_LEASE_REQUIRED", 409, "A valid input control lease is required");
        }
    }

    <T> CompletableFuture<T> applyDeadline(CompletableFuture<T> future, long deadlineAtMillis) {
        if (deadlineAtMillis <= 0L) return future;
        long remaining = deadlineAtMillis - System.currentTimeMillis();
        if (remaining <= 0L) {
            future.cancel(true);
            return CompletableFuture.failedFuture(
                    new ProtocolException("REQUEST_DEADLINE_EXCEEDED", 408, "Request deadline has elapsed"));
        }
        CompletableFuture<T> deadline = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                future.cancel(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        var timeout = this.scheduler.schedule(() -> {
            if (deadline.completeExceptionally(new ProtocolException(
                    "REQUEST_DEADLINE_EXCEEDED", 408, "Request deadline has elapsed"))) {
                future.cancel(true);
            }
        }, remaining, TimeUnit.MILLISECONDS);
        future.whenComplete((value, error) -> {
            timeout.cancel(false);
            if (error == null) deadline.complete(value);
            else deadline.completeExceptionally(error);
        });
        return deadline;
    }

    CompletableFuture<JsonObject> idempotent(String key, Supplier<CompletableFuture<JsonObject>> action) {
        if (key == null || key.isBlank()) return action.get();
        if (this.idempotentResults.size() > 256) this.idempotentResults.clear();
        return this.idempotentResults.computeIfAbsent(key, ignored -> action.get());
    }

    JsonObject startOperation(CompletableFuture<JsonObject> future) {
        return this.startOperation(future, false);
    }

    JsonObject startOperation(CompletableFuture<JsonObject> future, boolean leaseBound) {
        if (this.operations.size() >= 16) {
            this.operations.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        }
        if (this.operations.size() >= 16) {
            future.cancel(true);
            throw new ProtocolException("TOO_MANY_OPERATIONS", 429, "Too many active operations");
        }
        String id = UUID.randomUUID().toString();
        Operation operation = new Operation(id, future, leaseBound);
        this.operations.put(id, operation);
        future.whenComplete((result, error) -> operation.complete(result, error));
        return operation.snapshot();
    }

    JsonObject operationStatus(String operationId) {
        return this.requireOperation(operationId).snapshot();
    }

    JsonObject operationSnapshot() {
        JsonArray values = new JsonArray();
        this.operations.values().stream()
                .sorted((left, right) -> Long.compare(left.receivedAtMillis, right.receivedAtMillis))
                .forEach(operation -> values.add(operation.snapshot()));
        JsonObject json = new JsonObject();
        json.addProperty("type", "operation.snapshot");
        json.add("operations", values);
        return json;
    }

    JsonObject cancelOperation(String operationId) {
        Operation operation = this.requireOperation(operationId);
        operation.cancel("client_cancel");
        return operation.snapshot();
    }

    CompletableFuture<JsonObject> waitOperation(String operationId, long requestedTimeoutMillis) {
        Operation operation = this.requireOperation(operationId);
        if (!operation.isRunning()) return CompletableFuture.completedFuture(operation.snapshot());
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        operation.terminal.whenComplete((ignored, error) -> result.complete(operation.snapshot()));
        long timeout = Math.max(1L, Math.min(requestedTimeoutMillis, 300_000L));
        var timeoutHandle = this.scheduler.schedule(
                () -> result.complete(operation.snapshot()), timeout, TimeUnit.MILLISECONDS);
        result.whenComplete((ignored, error) -> timeoutHandle.cancel(false));
        return result;
    }

    synchronized void audit(String requestId, String connectionId, String path, String outcome) {
        this.audit.addLast(new AuditEntry(
                this.auditSequence.incrementAndGet(),
                System.currentTimeMillis(),
                requestId,
                this.principalId,
                connectionId,
                path,
                outcome,
                this.lease == null ? "" : this.lease.id(),
                this.debugArm == null ? "" : this.debugArm.id(),
                path.startsWith("/v0/operations/")
                        ? path.substring("/v0/operations/".length()).replace("/wait", "") : ""));
        while (this.audit.size() > 256) this.audit.removeFirst();
    }

    private synchronized void auditProvider(DeepObservationRequestContext.ProviderAuditEvent event) {
        JsonObject item = new JsonObject();
        item.addProperty("sequence", this.auditSequence.incrementAndGet());
        item.addProperty("timestampMillis", System.currentTimeMillis());
        item.addProperty("requestId", event.requestId());
        item.addProperty("principalId", event.principalId());
        item.addProperty("connectionId", event.connectionId());
        item.addProperty("providerId", event.providerId());
        JsonArray scopes = new JsonArray();
        event.requiredScopes().stream().sorted().forEach(scopes::add);
        item.add("requiredScopes", scopes);
        item.addProperty("decision", event.decision());
        item.addProperty("perspective", event.perspective());
        item.addProperty("readEffects", event.readEffects());
        item.addProperty("durationMicros", event.durationMicros());
        item.addProperty("status", event.status());
        this.providerAudit.addLast(item);
        while (this.providerAudit.size() > 256) this.providerAudit.removeFirst();
    }

    synchronized JsonObject auditSnapshot(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 256));
        JsonArray entries = new JsonArray();
        int skip = Math.max(0, this.audit.size() - limit);
        int index = 0;
        for (AuditEntry entry : this.audit) {
            if (index++ < skip) continue;
            JsonObject item = new JsonObject();
            item.addProperty("sequence", entry.sequence());
            item.addProperty("timestampMillis", entry.timestampMillis());
            item.addProperty("requestId", entry.requestId());
            item.addProperty("principalId", entry.principalId());
            item.addProperty("connectionId", entry.connectionId());
            item.addProperty("path", entry.path());
            item.addProperty("outcome", entry.outcome());
            item.addProperty("leaseId", entry.leaseId());
            item.addProperty("debugArmId", entry.debugArmId());
            item.addProperty("operationId", entry.operationId());
            entries.add(item);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "audit");
        json.add("entries", entries);
        JsonArray providers = new JsonArray();
        int providerSkip = Math.max(0, this.providerAudit.size() - limit);
        int providerIndex = 0;
        for (JsonObject item : this.providerAudit) {
            if (providerIndex++ >= providerSkip) providers.add(item.deepCopy());
        }
        json.add("providerInvocations", providers);
        return json;
    }

    JsonObject descriptors() {
        JsonArray operations = new JsonArray();
        operations.add(descriptor("session.get", "read", false, false, false));
        operations.add(descriptor("diagnostics.hooks", "diagnostics", false, false, false));
        operations.add(descriptor("ui.get_tree", "ui", false, false, false));
        operations.add(descriptor("ui.resolve", "ui", false, false, false,
                "screenRevision", "menuRevision", "selectorReresolution"));
        operations.add(descriptor("ui.action", "input", true, true, false,
                "screenRevision", "menuRevision", "selectorReresolution"));
        operations.add(descriptor("input.mouse", "input", true, true, false,
                "screenRevision", "menuRevision"));
        operations.add(descriptor("input.key", "input", true, true, false,
                "screenRevision", "menuRevision"));
        operations.add(descriptor("command.player.execute", "command", true, false, false,
                "currentPlayerPermissions", "normalNetwork", "serverValidation"));
        operations.add(descriptor("capture.composite", "capture", false, false, true));
        operations.add(descriptor("wait.screen", "read", false, false, true));
        operations.add(descriptor("wait.until", "read", false, false, true));
        operations.add(descriptor("assert.that", "read", false, false, false));
        operations.add(descriptor("pipeline.execute", "input", true, false, true,
                "leasePerStep", "selectorReresolution"));
        operations.add(descriptor("observation.client_live", "read", false, false, false));
        operations.add(descriptor("observation.server_authoritative_live", "read", false, false, false));
        operations.add(descriptor("server.peer.status", "read", false, false, false));
        operations.add(descriptor("server.peer.probe", "read", false, false, true,
                "peerNegotiated", "serverAuthority"));
        operations.add(descriptor("provider.read", "read", false, false, true));
        operations.add(descriptor("state.frame", "read", false, false, true));
        operations.add(descriptor("fixture.player.teleport", "fixture", true, false, false));
        operations.add(descriptor("debug.player.health", "debug", true, false, false,
                "debugArm", "worldFingerprint"));
        operations.add(descriptor("debug.world.block", "debug", true, false, false,
                "debugArm", "worldFingerprint", "expectedBlockState"));
        JsonObject json = new JsonObject();
        json.addProperty("type", "operation.descriptors");
        json.add("operations", operations);
        return json;
    }

    JsonObject securityContext() {
        JsonArray grantedScopes = new JsonArray();
        this.scopes.stream().sorted().forEach(grantedScopes::add);
        JsonObject json = new JsonObject();
        json.addProperty("type", "security.context");
        json.addProperty("protocolVersion", PROTOCOL_VERSION);
        json.addProperty("authentication", "bearer");
        json.addProperty("principalId", this.principalId);
        json.addProperty("principalLifecycle", "runtime_token_lifetime");
        json.addProperty("bindAddress", "127.0.0.1");
        json.add("grantedScopes", grantedScopes);
        return json;
    }

    @Override
    public synchronized void close() {
        this.lease = null;
        this.debugArm = null;
        this.inputCleanup.accept("transport_close");
        for (Operation operation : this.operations.values()) operation.cancel("transport_close");
        for (DeepObservationRequestContext requestContext : this.deepObservations.values()) {
            requestContext.cancel("transport_close");
        }
        this.deepObservations.clear();
        this.scheduler.shutdownNow();
    }

    private synchronized void expireLeaseIfNeeded(long now) {
        if (this.lease != null && this.lease.expiresAtMillis() <= now) {
            this.lease = null;
            this.cancelLeaseBoundOperations("lease_expired");
            this.inputCleanup.accept("lease_expired");
        }
    }

    private void scheduleLeaseExpiry(ControlLease scheduledLease) {
        long delay = Math.max(1L, scheduledLease.expiresAtMillis() - System.currentTimeMillis());
        this.scheduler.schedule(() -> {
            synchronized (this) {
                    if (this.lease != null
                        && this.lease.id().equals(scheduledLease.id())
                        && this.lease.expiresAtMillis() <= System.currentTimeMillis()) {
                    this.lease = null;
                    this.cancelLeaseBoundOperations("lease_expired");
                    this.inputCleanup.accept("lease_expired");
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelLeaseBoundOperations(String reason) {
        for (Operation operation : this.operations.values()) {
            if (operation.leaseBound) operation.cancel(reason);
        }
    }

    private synchronized void expireDebugIfNeeded(long now) {
        if (this.debugArm != null && this.debugArm.expiresAtMillis() <= now) this.debugArm = null;
    }

    private void scheduleDebugExpiry(DebugArm scheduledArm) {
        long delay = Math.max(1L, scheduledArm.expiresAtMillis() - System.currentTimeMillis());
        this.scheduler.schedule(() -> {
            synchronized (this) {
                if (this.debugArm != null
                        && this.debugArm.id().equals(scheduledArm.id())
                        && this.debugArm.expiresAtMillis() <= System.currentTimeMillis()) {
                    this.debugArm = null;
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private JsonObject leaseJson(String status) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", status);
        json.addProperty("leaseId", this.lease.id());
        json.addProperty("expiresAtMillis", this.lease.expiresAtMillis());
        return json;
    }

    private JsonObject debugArmJson(String status) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.arm");
        json.addProperty("status", status);
        json.addProperty("debugArmId", this.debugArm.id());
        json.addProperty("worldFingerprint", this.debugArm.worldFingerprint());
        json.addProperty("expiresAtMillis", this.debugArm.expiresAtMillis());
        return json;
    }

    private Operation requireOperation(String id) {
        Operation operation = this.operations.get(id);
        if (operation == null) {
            throw new ProtocolException("OPERATION_NOT_FOUND", 404, "Unknown operation: " + id);
        }
        return operation;
    }

    private static JsonObject descriptor(
            String id, String scope, boolean lease, boolean idempotency, boolean cancellation,
            String... preconditions) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("scope", scope);
        json.addProperty("requiresControlLease", lease);
        json.addProperty("supportsIdempotency", idempotency);
        json.addProperty("supportsCancellation", cancellation);
        json.addProperty("requiresDebugArm", id.startsWith("debug."));
        String affinity = id.startsWith("capture") ? "render_thread"
                : id.startsWith("server.peer") ? "multi_thread"
                : id.contains("server_authoritative") ? "server_thread"
                : id.equals("state.frame") || id.equals("provider.read") ? "multi_thread"
                : "client_thread";
        json.addProperty("threadAffinity", affinity);
        JsonArray supportedPreconditions = new JsonArray();
        for (String precondition : preconditions) supportedPreconditions.add(precondition);
        json.add("supportedPreconditions", supportedPreconditions);
        return json;
    }

    record RequestMetadata(
            String requestId,
            String protocolVersion,
            long deadlineAtMillis,
            String leaseId,
            String idempotencyKey,
            Long expectedScreenRevision,
            Long expectedMenuRevision,
            String debugArmId) {
    }

    static final class ProtocolException extends RuntimeException {
        private final String code;
        private final int httpStatus;

        ProtocolException(String code, int httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        String code() {
            return this.code;
        }

        int httpStatus() {
            return this.httpStatus;
        }
    }

    private record ControlLease(String id, long expiresAtMillis) {
    }

    private record DebugArm(String id, String worldFingerprint, long expiresAtMillis) {
    }

    private record AuditEntry(
            long sequence,
            long timestampMillis,
            String requestId,
            String principalId,
            String connectionId,
            String path,
            String outcome,
            String leaseId,
            String debugArmId,
            String operationId) {
    }

    private static final class Operation {
        private final String id;
        private final CompletableFuture<JsonObject> future;
        private final CompletableFuture<Void> terminal = new CompletableFuture<>();
        private final boolean leaseBound;
        private final long receivedAtMillis = System.currentTimeMillis();
        private final long acceptedAtMillis = this.receivedAtMillis;
        private final long scheduledAtMillis = this.receivedAtMillis;
        private final long startedAtMillis = this.receivedAtMillis;
        private volatile String status = "running";
        private volatile String state = "executing";
        private volatile JsonObject result;
        private volatile String error;
        private volatile String errorCode;
        private volatile String cancellationReason;
        private volatile long completedAtMillis;

        private Operation(String id, CompletableFuture<JsonObject> future, boolean leaseBound) {
            this.id = id;
            this.future = future;
            this.leaseBound = leaseBound;
        }

        private synchronized void complete(JsonObject result, Throwable error) {
            if (this.state.equals("cancelled")) return;
            this.completedAtMillis = System.currentTimeMillis();
            if (error == null) {
                this.result = result;
                this.status = "completed";
                this.state = "completed";
            } else if (error instanceof CancellationException) {
                this.status = "cancelled";
                this.state = "cancelled";
            } else {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                this.error = cause instanceof TimeoutException ? "operation timed out" : cause.getMessage();
                if (cause instanceof ProtocolException protocolException) this.errorCode = protocolException.code();
                else if (cause instanceof TimeoutException) this.errorCode = "OPERATION_TIMEOUT";
                boolean timedOut = cause instanceof TimeoutException
                        || "PIPELINE_TIMEOUT".equals(this.errorCode)
                        || "WAIT_TIMEOUT".equals(this.errorCode)
                        || "REQUEST_DEADLINE_EXCEEDED".equals(this.errorCode);
                this.status = timedOut ? "timed_out" : "failed";
                this.state = this.status;
            }
            this.terminal.complete(null);
        }

        private synchronized void cancel(String reason) {
            if (this.state.equals("executing") || this.state.equals("scheduled") || this.state.equals("accepted")) {
                this.status = "cancelled";
                this.state = "cancelled";
                this.cancellationReason = reason;
                this.completedAtMillis = System.currentTimeMillis();
                this.future.cancel(true);
                this.terminal.complete(null);
            }
        }

        private boolean isRunning() {
            return this.status.equals("running");
        }

        private JsonObject snapshot() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "operation");
            json.addProperty("operationId", this.id);
            json.addProperty("status", this.status);
            json.addProperty("state", this.state);
            json.addProperty("received", true);
            json.addProperty("accepted", true);
            json.addProperty("scheduled", true);
            json.addProperty("executing", this.state.equals("executing"));
            json.addProperty("completed", this.state.equals("completed"));
            json.addProperty("failed", this.state.equals("failed"));
            json.addProperty("cancelled", this.state.equals("cancelled"));
            json.addProperty("timedOut", this.state.equals("timed_out"));
            json.addProperty("leaseBound", this.leaseBound);
            json.addProperty("receivedAtMillis", this.receivedAtMillis);
            json.addProperty("acceptedAtMillis", this.acceptedAtMillis);
            json.addProperty("scheduledAtMillis", this.scheduledAtMillis);
            json.addProperty("startedAtMillis", this.startedAtMillis);
            if (this.completedAtMillis > 0L) json.addProperty("completedAtMillis", this.completedAtMillis);
            if (this.result != null) json.add("result", this.result.deepCopy());
            if (this.error != null) json.addProperty("error", this.error);
            if (this.errorCode != null) json.addProperty("errorCode", this.errorCode);
            if (this.cancellationReason != null) json.addProperty("cancellationReason", this.cancellationReason);
            return json;
        }
    }
}
