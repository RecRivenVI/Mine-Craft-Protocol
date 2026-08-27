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
            "read", "ui", "input", "capture", "event", "diagnostics", "control");

    private final Set<String> scopes;
    private final Consumer<String> inputCleanup;
    private final ScheduledExecutorService scheduler;
    private final Map<String, CompletableFuture<JsonObject>> idempotentResults = new ConcurrentHashMap<>();
    private final Map<String, Operation> operations = new ConcurrentHashMap<>();
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private final AtomicLong auditSequence = new AtomicLong();
    private ControlLease lease;
    private DebugArm debugArm;

    ProtocolState(Set<String> scopes, Consumer<String> inputCleanup) {
        this.scopes = Set.copyOf(scopes);
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
        this.inputCleanup.accept(reason);
        return true;
    }

    synchronized JsonObject emergencyRelease(String reason) {
        boolean hadLease = this.lease != null;
        this.lease = null;
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
            return CompletableFuture.failedFuture(
                    new ProtocolException("REQUEST_DEADLINE_EXCEEDED", 408, "Request deadline has elapsed"));
        }
        return future.orTimeout(remaining, TimeUnit.MILLISECONDS);
    }

    CompletableFuture<JsonObject> idempotent(String key, Supplier<CompletableFuture<JsonObject>> action) {
        if (key == null || key.isBlank()) return action.get();
        if (this.idempotentResults.size() > 256) this.idempotentResults.clear();
        return this.idempotentResults.computeIfAbsent(key, ignored -> action.get());
    }

    JsonObject startOperation(CompletableFuture<JsonObject> future) {
        if (this.operations.size() >= 256) {
            this.operations.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        }
        if (this.operations.size() >= 256) {
            future.cancel(true);
            throw new ProtocolException("TOO_MANY_OPERATIONS", 429, "Too many active operations");
        }
        String id = UUID.randomUUID().toString();
        Operation operation = new Operation(id, future);
        this.operations.put(id, operation);
        future.whenComplete((result, error) -> operation.complete(result, error));
        return operation.snapshot();
    }

    JsonObject operationStatus(String operationId) {
        return this.requireOperation(operationId).snapshot();
    }

    JsonObject cancelOperation(String operationId) {
        Operation operation = this.requireOperation(operationId);
        operation.cancel();
        return operation.snapshot();
    }

    synchronized void audit(String requestId, String path, String outcome) {
        this.audit.addLast(new AuditEntry(
                this.auditSequence.incrementAndGet(),
                System.currentTimeMillis(),
                requestId,
                path,
                outcome));
        while (this.audit.size() > 256) this.audit.removeFirst();
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
            item.addProperty("path", entry.path());
            item.addProperty("outcome", entry.outcome());
            entries.add(item);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "audit");
        json.add("entries", entries);
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
        json.addProperty("bindAddress", "127.0.0.1");
        json.add("grantedScopes", grantedScopes);
        return json;
    }

    @Override
    public synchronized void close() {
        this.lease = null;
        this.debugArm = null;
        this.inputCleanup.accept("transport_close");
        for (Operation operation : this.operations.values()) operation.cancel();
        this.scheduler.shutdownNow();
    }

    private synchronized void expireLeaseIfNeeded(long now) {
        if (this.lease != null && this.lease.expiresAtMillis() <= now) {
            this.lease = null;
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
                    this.inputCleanup.accept("lease_expired");
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
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
            long sequence, long timestampMillis, String requestId, String path, String outcome) {
    }

    private static final class Operation {
        private final String id;
        private final CompletableFuture<JsonObject> future;
        private volatile String status = "running";
        private volatile JsonObject result;
        private volatile String error;

        private Operation(String id, CompletableFuture<JsonObject> future) {
            this.id = id;
            this.future = future;
        }

        private void complete(JsonObject result, Throwable error) {
            if (this.status.equals("cancelled")) return;
            if (error == null) {
                this.result = result;
                this.status = "completed";
            } else if (error instanceof CancellationException) {
                this.status = "cancelled";
            } else {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                this.error = cause instanceof TimeoutException ? "operation timed out" : cause.getMessage();
                this.status = "failed";
            }
        }

        private void cancel() {
            if (this.status.equals("running")) {
                this.status = "cancelled";
                this.future.cancel(true);
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
            if (this.result != null) json.add("result", this.result.deepCopy());
            if (this.error != null) json.addProperty("error", this.error);
            return json;
        }
    }
}
