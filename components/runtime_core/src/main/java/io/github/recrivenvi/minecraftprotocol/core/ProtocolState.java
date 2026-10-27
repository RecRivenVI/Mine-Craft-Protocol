package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.AgentTaskLifecycle;
import io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ProtocolState implements AutoCloseable {
    static final int MAX_ACTIVE_DEEP_OBSERVATIONS = 16;
    static final String LEASE_HEADER = "X-MCP-Control-Lease";
    static final String REQUEST_ID_HEADER = "X-MCP-Request-Id";
    static final String PROTOCOL_HEADER = "X-MCP-Protocol-Version";
    static final String PROTOCOL_VERSION = "v0";
    static final String DEADLINE_HEADER = "X-MCP-Deadline-Ms";
    static final String IDEMPOTENCY_HEADER = "X-MCP-Idempotency-Key";
    static final String EXPECTED_SCREEN_HEADER = "X-MCP-Expected-Screen-Revision";
    static final String EXPECTED_MENU_HEADER = "X-MCP-Expected-Menu-Revision";
    static final String DEBUG_ARM_HEADER = "X-MCP-Debug-Arm";
    static final long DEFAULT_CONTROL_LEASE_MILLIS = 15_000L;
    static final long MIN_CONTROL_LEASE_MILLIS = 1_000L;
    static final long MAX_CONTROL_LEASE_MILLIS = 60_000L;

    private static final Set<String> DEFAULT_SCOPES =
            Set.of("read", "ui", "input", "capture", "event", "diagnostics", "control", "command");

    private final Set<String> scopes;
    private final boolean inputSurfaceAvailable;
    private final String principalId;
    private final Function<String, CompletableFuture<JsonObject>> inputCleanup;
    private volatile CompletableFuture<JsonObject> inputCleanupBarrier =
            CompletableFuture.completedFuture(new JsonObject());
    private final AtomicLong inputGeneration = new AtomicLong();
    private volatile String inputCleanupReason = "";
    private volatile InputContextEpoch.Snapshot nativeInputContext;
    private boolean modeTransitionPending;
    private volatile boolean closed;
    private final ScheduledExecutorService scheduler;
    private final Map<String, CompletableFuture<JsonObject>> idempotentResults =
            new ConcurrentHashMap<>();
    private final Map<String, Operation> operations = new ConcurrentHashMap<>();
    private final Map<String, DeepObservationRequestContext> deepObservations =
            new ConcurrentHashMap<>();
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private final Deque<JsonObject> providerAudit = new ArrayDeque<>();
    private final AtomicLong auditSequence = new AtomicLong();
    private final AtomicLong debugMutationSequence = new AtomicLong();
    private final Map<String, GameplayActWindow> gameplayActs = new ConcurrentHashMap<>();
    private volatile ControlLease lease;
    private DebugArm debugArm;
    private final AgentControlSession controlSession;
    private final AgentTaskLifecycle agentTask = new AgentTaskLifecycle();
    private String leaseTaskId;
    private Consumer<JsonObject> taskListener = ignored -> {};

    ProtocolState(Set<String> scopes, String principalId, Consumer<String> inputCleanup) {
        this(
                scopes,
                principalId,
                reason -> {
                    inputCleanup.accept(reason);
                    return CompletableFuture.completedFuture(new JsonObject());
                },
                snapshot -> {});
    }

    ProtocolState(
            Set<String> scopes,
            String principalId,
            Function<String, CompletableFuture<JsonObject>> inputCleanup,
            Consumer<AgentControlSession.Snapshot> controlListener) {
        this(scopes, principalId, inputCleanup, controlListener, true);
    }

    ProtocolState(
            Set<String> scopes,
            String principalId,
            Function<String, CompletableFuture<JsonObject>> inputCleanup,
            Consumer<AgentControlSession.Snapshot> controlListener,
            boolean inputSurfaceAvailable) {
        this.inputSurfaceAvailable = inputSurfaceAvailable;
        this.scopes = Set.copyOf(scopes);
        this.principalId = principalId;
        this.inputCleanup = inputCleanup;
        this.controlSession = new AgentControlSession(controlListener);
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "minecraft-protocol-control");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.scheduler.scheduleWithFixedDelay(
                this::expireTaskIfNeeded, 250, 250, TimeUnit.MILLISECONDS);
    }

    synchronized void taskListener(Consumer<JsonObject> listener) {
        this.taskListener = listener;
    }

    void agentActivity() {
        this.agentTask.activity();
    }

    boolean taskPresent() {
        return this.agentTask.present();
    }

    boolean explicitTaskLifecycle() {
        return this.agentTask.explicit();
    }

    synchronized JsonObject taskStatus() {
        this.expireTaskIfNeeded();
        return this.taskJson();
    }

    synchronized JsonObject beginTask(long ttl, long budget) {
        this.expireTaskIfNeeded();
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        this.requireModeChangeReady();
        if (this.lease != null || this.controlSession.hasOperateWork())
            throw new ProtocolException(
                    "AGENT_TASK_CONFLICT",
                    409,
                    "Finish existing control work before starting a task");
        this.agentTask.begin(ttl, budget);
        this.publishTask();
        return this.taskJson();
    }

    synchronized JsonObject heartbeatTask(String taskId, long ttl) {
        this.expireTaskIfNeeded();
        this.agentTask.heartbeat(taskId, ttl);
        return this.taskJson();
    }

    synchronized CompletableFuture<JsonObject> endTask(String taskId) {
        this.expireTaskIfNeeded();
        boolean active = this.agentTask.snapshot().active();
        this.agentTask.end(taskId, "task_ended");
        if (active) {
            this.releaseTaskInput(taskId, "task_ended");
            this.publishTask();
        }
        JsonObject result = this.taskJson();
        return this.inputCleanupBarrier.thenApply(ignored -> result);
    }

    private synchronized void expireTaskIfNeeded() {
        if (this.closed || !this.agentTask.due()) return;
        String taskId = this.agentTask.snapshot().taskId();
        this.agentTask.expire();
        this.releaseTaskInput(taskId, "task_expired");
        this.publishTask();
    }

    private void releaseTaskInput(String taskId, String reason) {
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease != null && taskId.equals(this.leaseTaskId))
            this.releaseLeaseIfMatches(this.lease.id(), reason);
    }

    private JsonObject taskJson() {
        var task = this.agentTask.snapshot();
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.task");
        json.addProperty("taskId", task.taskId());
        json.addProperty("status", task.status());
        json.addProperty("reason", task.reason());
        json.addProperty("generation", task.generation());
        json.addProperty("expiresAtMillis", task.expiresAtMillis());
        json.addProperty("deadlineAtMillis", task.deadlineAtMillis());
        json.addProperty("active", task.active());
        json.addProperty("controlSessionId", this.controlSession.snapshot().controlSessionId());
        json.addProperty("grantsAuthority", false);
        return json;
    }

    private void publishTask() {
        JsonObject event = this.taskJson();
        event.addProperty("category", "control");
        event.addProperty("principalId", this.principalId);
        this.audit(
                UUID.randomUUID().toString(),
                "agent-task",
                "/v0/control/task",
                event.get("reason").getAsString());
        this.taskListener.accept(event);
    }

    static Set<String> configuredScopes() {
        return configuredScopes(false);
    }

    static Set<String> configuredScopes(boolean headless) {
        String configured = System.getProperty("minecraft.protocol.scopes");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("MCP_RUNTIME_SCOPES");
        }
        if (configured == null || configured.isBlank()) {
            return headless ? Set.of("read", "event", "control", "diagnostics") : DEFAULT_SCOPES;
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            if (!value.isBlank()) scopes.add(value.trim());
        }
        return scopes;
    }

    void requireSessionOpen() {
        if (this.closed)
            throw new ProtocolException(
                    "CONTROL_SESSION_CLOSED", 409, "Runtime control session is closed");
    }

    void requireScope(String scope) {
        requireSessionOpen();
        if (!this.scopes.contains(scope)) {
            throw new ProtocolException(
                    "SCOPE_DENIED", 403, "Required scope is not granted: " + scope);
        }
    }

    // Called under the native control-session lock: never acquire ProtocolState in reverse order.
    void requireCommandLease() {
        ControlLease current = this.lease;
        if (current == null || current.expiresAtMillis() <= System.currentTimeMillis())
            throw new ProtocolException(
                    "CONTROL_LEASE_REQUIRED", 409, "A current unexpired input Lease is required");
    }

    // Diagnostic attribution only; never acquires ProtocolState under a native dispatch lock.
    boolean nativeInputLeaseActive(InputContextEpoch.Snapshot expected) {
        ControlLease current = this.lease;
        if (this.closed
                || expected == null
                || expected != this.nativeInputContext
                || !expected.worldAvailable()
                || current == null
                || current.expiresAtMillis() <= System.currentTimeMillis()
                || !this.hasScope("input")
                || !this.hasScope("control")
                || !this.inputCleanupBarrier.isDone()
                || this.inputCleanupBarrier.isCompletedExceptionally()) return false;
        return this.controlSession.snapshot().agentControlled()
                && this.lease == current
                && !this.closed;
    }

    boolean hasScope(String scope) {
        return this.scopes.contains(scope);
    }

    io.github.recrivenvi.minecraftprotocol.safety.UiDataView.Category requireUiCategory(
            String value) {
        requireScope("ui");
        io.github.recrivenvi.minecraftprotocol.safety.UiDataView.Category category;
        try {
            category =
                    io.github.recrivenvi.minecraftprotocol.safety.UiDataView.Category.parse(value);
        } catch (IllegalArgumentException error) {
            throw new ProtocolException(
                    "INVALID_UI_CATEGORY", 400, "UI category must be USER or DEBUG");
        }
        io.github.recrivenvi.minecraftprotocol.safety.UiDataView.requireReadScopes(
                category, this::requireScope);
        if (!io.github.recrivenvi.minecraftprotocol.safety.UiDataView.allowsRead(
                controlSession().snapshot().mode().name(), category))
            throw new ProtocolException(
                    "UI_CATEGORY_MODE_MISMATCH",
                    409,
                    "UI reads require a valid control session and independent category scopes");
        return category;
    }

    String principalId() {
        return this.principalId;
    }

    Runnable uiReadGuard(String category) {
        requireUiCategory(category);
        return () -> requireUiCategory(category);
    }

    static boolean uiDiagnosticOperation(String operation) {
        return operation.startsWith("ui.") || operation.startsWith("fixture.ui.");
    }

    void requireDebugScope(String domain) {
        for (String scope : List.of("debug", "debug.write", "debug." + domain)) {
            if (!this.scopes.contains(scope)) {
                throw new ProtocolException(
                        "DEBUG_SCOPE_DENIED",
                        403,
                        "Typed Debug requires authenticated scope: " + scope);
            }
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

    synchronized void registerDeepObservation(
            String requestId, DeepObservationRequestContext requestContext) {
        if (this.deepObservations.size() >= MAX_ACTIVE_DEEP_OBSERVATIONS) {
            throw new ProtocolException(
                    "TOO_MANY_DEEP_OBSERVATIONS",
                    429,
                    "At most "
                            + MAX_ACTIVE_DEEP_OBSERVATIONS
                            + " Deep Observation requests may be active");
        }
        DeepObservationRequestContext previous =
                this.deepObservations.putIfAbsent(requestId, requestContext);
        if (previous != null) {
            throw new ProtocolException(
                    "DUPLICATE_ACTIVE_REQUEST_ID",
                    409,
                    "A Deep Observation with this request ID is already active");
        }
    }

    void unregisterDeepObservation(String requestId, DeepObservationRequestContext requestContext) {
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

    synchronized JsonObject acquireLease(long requestedTtlMillis, JsonObject expectedVersion) {
        if (expectedVersion != null) this.requireModeVersion(expectedVersion);
        return this.acquireLease(requestedTtlMillis);
    }

    synchronized JsonObject acquireLease(long ttl, JsonObject expectedVersion, String taskId) {
        this.expireTaskIfNeeded();
        if (taskId != null || this.agentTask.snapshot().active())
            this.agentTask.requireActive(taskId);
        JsonObject result = this.acquireLease(ttl, expectedVersion);
        this.leaseTaskId = taskId;
        return result;
    }

    synchronized JsonObject acquireLease(long requestedTtlMillis) {
        if (!inputSurfaceAvailable)
            throw new ProtocolException(
                    "TAKEOVER_NOT_APPLICABLE", 409, "No player input surface on dedicated Runtime");
        long now = System.currentTimeMillis();
        this.expireLeaseIfNeeded(now);
        this.requireModeChangeReady();
        if (this.lease != null) {
            throw new ProtocolException(
                    "CONTROL_LEASE_CONFLICT", 409, "An input control lease is already active");
        }
        boolean reacquiredAfterManualRevocation = this.controlSession.snapshot().manuallyRevoked();
        long ttl =
                Math.max(
                        MIN_CONTROL_LEASE_MILLIS,
                        Math.min(requestedTtlMillis, MAX_CONTROL_LEASE_MILLIS));
        this.lease = new ControlLease(UUID.randomUUID().toString(), now + ttl);
        this.leaseTaskId = null;
        this.scheduleLeaseExpiry(this.lease);
        this.controlSession.acquire();
        this.audit(
                UUID.randomUUID().toString(),
                "control",
                "/v0/control/acquire",
                reacquiredAfterManualRevocation
                        ? "agent_control_reacquired"
                        : "agent_control_acquired");
        return this.leaseJson("acquired");
    }

    synchronized JsonObject renewLease(String leaseId, long requestedTtlMillis) {
        this.requireLease(leaseId);
        long ttl =
                Math.max(
                        MIN_CONTROL_LEASE_MILLIS,
                        Math.min(requestedTtlMillis, MAX_CONTROL_LEASE_MILLIS));
        this.lease = new ControlLease(this.lease.id(), System.currentTimeMillis() + ttl);
        this.scheduleLeaseExpiry(this.lease);
        return this.leaseJson("renewed");
    }

    synchronized JsonObject releaseLease(String leaseId, String reason) {
        this.requireLease(leaseId);
        this.lease = null;
        this.controlSession.release(reason);
        this.cancelLeaseBoundOperations(reason);
        this.cleanupInput(reason);
        this.audit(
                UUID.randomUUID().toString(), "control", "/v0/control/release", "control_released");
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", "released");
        json.addProperty("reason", reason);
        return this.controlPresence(json);
    }

    synchronized CompletableFuture<JsonObject> releaseLeaseAndWait(String leaseId, String reason) {
        JsonObject result = this.releaseLease(leaseId, reason);
        return this.inputCleanupBarrier.thenApply(ignored -> result);
    }

    synchronized CompletableFuture<JsonObject> emergencyReleaseAndWait(String reason) {
        JsonObject result = this.emergencyRelease(reason);
        return this.inputCleanupBarrier.thenApply(ignored -> result);
    }

    synchronized boolean releaseLeaseIfMatches(String leaseId, String reason) {
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease == null || leaseId == null || !this.lease.id().equals(leaseId)) return false;
        this.lease = null;
        this.controlSession.release(reason);
        this.cancelLeaseBoundOperations(reason);
        this.cleanupInput(reason);
        this.audit(
                UUID.randomUUID().toString(), "control", "/v0/control/release", "control_released");
        return true;
    }

    synchronized JsonObject emergencyRelease(String reason) {
        boolean hadLease = this.lease != null;
        this.lease = null;
        if (hadLease) this.controlSession.release(reason);
        this.cancelLeaseBoundOperations(reason);
        this.cleanupInput(reason);
        if (hadLease) {
            this.audit(
                    UUID.randomUUID().toString(),
                    "control",
                    "/v0/control/emergency-release",
                    "control_released");
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", "released");
        json.addProperty("hadLease", hadLease);
        json.addProperty("reason", reason);
        return this.controlPresence(json);
    }

    synchronized JsonObject leaseStatus() {
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease == null) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "control.lease");
            json.addProperty("status", "available");
            return this.controlPresence(json);
        }
        return this.leaseJson("active");
    }

    synchronized JsonObject revokeHumanControl() {
        if (this.lease == null || !this.controlSession.snapshot().agentControlled()) {
            return this.controlPresence(new JsonObject());
        }
        String leaseId = this.lease.id();
        this.lease = null;
        this.controlSession.manuallyRevoke();
        this.cancelLeaseBoundOperations("human_manual_revocation");
        this.cleanupInput("human_manual_revocation");
        this.audit(
                UUID.randomUUID().toString(),
                "native-input",
                "/v0/control/human-revoke",
                "human_manual_revocation");
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", "manually_revoked");
        json.addProperty("previousLeaseId", leaseId);
        return this.controlPresence(json);
    }

    AgentControlSession.Snapshot controlPresence() {
        return this.controlSession.snapshot();
    }

    AgentControlSession controlSession() {
        return this.controlSession;
    }

    private CompletableFuture<JsonObject> cleanupInput(String reason) {
        return this.cleanupInput(reason, false);
    }

    private CompletableFuture<JsonObject> cleanupInput(String reason, boolean forceNewWave) {
        this.inputCleanupReason = reason;
        CompletableFuture<JsonObject> previous = this.inputCleanupBarrier;
        if (!forceNewWave && !previous.isDone()) return previous;
        this.inputGeneration.incrementAndGet();
        CompletableFuture<JsonObject> barrier = new CompletableFuture<>();
        this.inputCleanupBarrier = barrier;
        try {
            CompletableFuture<JsonObject> cleanup = this.inputCleanup.apply(reason);
            CompletableFuture.allOf(
                            previous.isDone() ? CompletableFuture.completedFuture(null) : previous,
                            cleanup)
                    .whenComplete(
                            (ignored, error) -> {
                                if (error == null) barrier.complete(cleanup.join());
                                else barrier.completeExceptionally(error);
                            });
        } catch (Throwable error) {
            barrier.completeExceptionally(error);
        }
        return barrier;
    }

    synchronized void nativeInputContextChanged(InputContextEpoch.Snapshot context) {
        if (this.closed || context.equals(this.nativeInputContext)) return;
        InputContextEpoch.Snapshot previous = this.nativeInputContext;
        if (previous != null && context.generation() <= previous.generation())
            throw new IllegalArgumentException("Native input context generation must advance");
        this.nativeInputContext = context;
        if (previous == null && this.lease == null) return;
        // A second replacement must fence admission even while a previous cleanup is pending.
        this.cleanupInput("native_input_context_changed", true);
        this.cancelLeaseBoundOperations("native_input_context_changed");
        this.audit(
                UUID.randomUUID().toString(),
                "native-input",
                "/v0/input/context",
                "native_input_context_changed");
    }

    synchronized CompletableFuture<JsonObject> releaseAllInputAndWait(String leaseId) {
        this.requireScope("input");
        this.requireScope("control");
        this.requireTakeover(leaseId);
        CompletableFuture<JsonObject> cleanup = this.cleanupInput("client_release_all");
        long generation = this.inputGeneration.get();
        // Cancelling this response must not cancel the Runtime-owned safety cleanup.
        return cleanup.thenApply(
                value -> {
                    JsonObject result = value == null ? new JsonObject() : value.deepCopy();
                    result.addProperty("type", "input.release_all");
                    result.addProperty("inputGeneration", generation);
                    return result;
                });
    }

    JsonObject inputStateMetadata(JsonObject snapshot) {
        JsonObject result = snapshot.deepCopy();
        CompletableFuture<JsonObject> cleanup = this.inputCleanupBarrier;
        result.addProperty("inputGeneration", this.inputGeneration.get());
        result.addProperty("inputCleanupPending", !cleanup.isDone());
        result.addProperty("inputCleanupFailed", cleanup.isCompletedExceptionally());
        InputContextEpoch.Snapshot context = this.nativeInputContext;
        result.addProperty("inputContextGeneration", context == null ? 0 : context.generation());
        result.addProperty("inputContextId", context == null ? "" : context.opaqueId());
        result.addProperty("inputContextAvailable", context != null && context.worldAvailable());
        return result;
    }

    synchronized InputGuard captureInputGuard(String leaseId) {
        this.requireScope("input");
        this.requireTakeover(leaseId);
        InputGuard guard = new InputGuard(this, leaseId, this.inputGeneration.get());
        guard.run();
        return guard;
    }

    static final class InputGuard implements Runnable {
        private final ProtocolState state;
        private final String leaseId;
        private final long generation;

        private InputGuard(ProtocolState state, String leaseId, long generation) {
            this.state = state;
            this.leaseId = leaseId;
            this.generation = generation;
        }

        String leaseId() {
            return this.leaseId;
        }

        long generation() {
            return this.generation;
        }

        @Override
        public void run() {
            // A native callback already owns ControlSession's monitor. Never take State's here.
            if (this.state.closed)
                throw new ProtocolException(
                        "CONTROL_SESSION_CLOSED", 409, "Runtime control session is closed");
            ControlLease lease = this.state.lease;
            if (lease == null
                    || !this.leaseId.equals(lease.id())
                    || lease.expiresAtMillis() <= System.currentTimeMillis()) {
                boolean manual =
                        lease == null
                                && this.state.inputCleanupReason.equals("human_manual_revocation");
                throw new ProtocolException(
                        manual ? "USER_MANUALLY_ENDED_CONTROL" : "CONTROL_LEASE_REQUIRED",
                        409,
                        manual ? "用户手动结束控制" : "The originating input Lease is no longer active");
            }
            if (this.state.inputGeneration.get() != this.generation)
                throw new ProtocolException(
                        "CONTROL_INPUT_GENERATION_CHANGED",
                        409,
                        "Input was stopped after this request was admitted");
            CompletableFuture<JsonObject> cleanup = this.state.inputCleanupBarrier;
            if (!cleanup.isDone())
                throw new ProtocolException(
                        "CONTROL_INPUT_CLEANUP_PENDING",
                        409,
                        "Input cleanup must complete before admitting more player input");
            if (cleanup.isCompletedExceptionally())
                throw new ProtocolException(
                        "CONTROL_INPUT_CLEANUP_FAILED", 409, "Input cleanup failed");
        }
    }

    private void requireModeChangeReady() {
        if (this.closed)
            throw new ProtocolException(
                    "CONTROL_SESSION_CLOSED", 409, "Runtime control session is closed");
        if (this.modeTransitionPending || !this.inputCleanupBarrier.isDone())
            throw new ProtocolException(
                    "CONTROL_INPUT_CLEANUP_PENDING",
                    409,
                    "Input cleanup must complete before changing intent");
        if (this.inputCleanupBarrier.isCompletedExceptionally())
            throw new ProtocolException(
                    "CONTROL_INPUT_CLEANUP_FAILED",
                    409,
                    "Input cleanup failed; intent escalation is unavailable");
    }

    synchronized void requireTakeover(String leaseId) {
        this.expireTaskIfNeeded();
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        AgentControlSession.Snapshot snapshot = this.controlSession.snapshot();
        if (!snapshot.agentControlled())
            throw new ProtocolException(
                    snapshot.manuallyRevoked()
                            ? "USER_MANUALLY_ENDED_CONTROL"
                            : "TAKEOVER_REQUIRED",
                    409,
                    snapshot.manuallyRevoked()
                            ? "用户手动结束控制"
                            : "Explicit TAKEOVER intent is required");
        this.requireLease(leaseId);
    }

    synchronized <T> CompletableFuture<T> admitInput(
            InputGuard guard, Supplier<CompletableFuture<T>> factory) {
        this.requireTakeover(guard.leaseId());
        guard.run();
        return factory.get();
    }

    synchronized void requireDebugCredential(String armId) {
        this.expireDebugIfNeeded(System.currentTimeMillis());
        if (this.debugArm == null || armId == null || !this.debugArm.id().equals(armId))
            throw new ProtocolException("DEBUG_NOT_ARMED", 409, "A valid Debug Arm is required");
    }

    synchronized void requireOperateIntent() {
        this.requireSessionOpen();
        if (this.controlSession.snapshot().mode() == AgentControlSession.Mode.READ)
            throw new ProtocolException(
                    "OPERATE_REQUIRED", 409, "Explicit OPERATE or TAKEOVER intent is required");
    }

    synchronized AgentControlSession.OperateWork beginOperate() {
        this.requireSessionOpen();
        return this.controlSession.beginOperate();
    }

    CompletableFuture<JsonObject> operate(
            Function<AgentControlSession.OperateWork, CompletableFuture<JsonObject>> factory) {
        AgentControlSession.OperateWork work = this.beginOperate();
        CompletableFuture<JsonObject> source;
        try {
            source = factory.apply(work);
        } catch (Throwable error) {
            work.close();
            throw error;
        }
        CompletableFuture<JsonObject> result =
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        work.close();
                        source.cancel(interrupt);
                        return super.cancel(interrupt);
                    }
                };
        source.whenComplete(
                (value, error) -> {
                    try {
                        if (error == null) result.complete(value);
                        else result.completeExceptionally(error);
                    } finally {
                        work.close();
                    }
                });
        return result;
    }

    synchronized JsonObject modeStatus() {
        this.expireTaskIfNeeded();
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        JsonObject json = this.controlPresence(new JsonObject());
        json.addProperty("type", "control.mode");
        json.add("agentTask", this.taskStatus());
        json.addProperty("modeScope", "authenticated_runtime_control_session");
        json.addProperty("authorizationIndependent", true);
        json.addProperty("inputCleanupPending", !this.inputCleanupBarrier.isDone());
        json.addProperty("modeTransitionPending", this.modeTransitionPending);
        json.addProperty("activeOperateRequests", this.controlSession.activeOperateRequests());
        json.add("authorization", this.authorizationStatus());
        JsonArray transitions = new JsonArray();
        if (!this.closed
                && !this.modeTransitionPending
                && this.inputCleanupBarrier.isDone()
                && !this.inputCleanupBarrier.isCompletedExceptionally()) {
            for (AgentControlSession.Mode mode : AgentControlSession.Mode.values()) {
                if (mode == AgentControlSession.Mode.TAKEOVER && !inputSurfaceAvailable) continue;
                if (mode == AgentControlSession.Mode.TAKEOVER && this.lease != null) continue;
                JsonObject transition = new JsonObject();
                transition.addProperty("mode", mode.name());
                transition.addProperty("method", "POST");
                transition.addProperty(
                        "path",
                        mode == AgentControlSession.Mode.TAKEOVER
                                ? "/v0/control/acquire"
                                : "/v0/control/mode");
                transition.addProperty("requiresControlLease", this.lease != null);
                transition.addProperty(
                        "requiresConversationReconsent",
                        mode == AgentControlSession.Mode.TAKEOVER
                                && this.controlSession.snapshot().reconsentRequired());
                transitions.add(transition);
            }
        }
        json.add("availableTransitions", transitions);
        return json;
    }

    private JsonObject authorizationStatus() {
        long now = System.currentTimeMillis();
        this.expireDebugIfNeeded(now);
        JsonObject json = new JsonObject();
        json.addProperty("principalId", this.principalId);
        JsonArray granted = new JsonArray();
        this.scopes.stream().sorted().forEach(granted::add);
        json.add("grantedScopes", granted);
        json.addProperty("grantsAuthority", false);
        json.addProperty("sessionOpen", !this.closed);
        JsonObject input = new JsonObject();
        input.addProperty("held", this.lease != null);
        input.addProperty(
                "remainingTtlMs",
                this.lease == null ? 0L : Math.max(0L, this.lease.expiresAtMillis() - now));
        json.add("inputLease", input);
        JsonObject arm = new JsonObject();
        arm.addProperty("status", this.debugArm == null ? "disarmed" : "armed");
        arm.addProperty(
                "remainingTtlMs",
                this.debugArm == null ? 0L : Math.max(0L, this.debugArm.expiresAtMillis() - now));
        JsonArray namespaces = new JsonArray();
        if (this.debugArm != null)
            this.debugArm.namespaces().stream().sorted().forEach(namespaces::add);
        arm.add("namespaces", namespaces);
        // This is a session summary, not a world/owner-thread authorization or an Arm credential.
        arm.addProperty("contextValidated", false);
        json.add("debugArm", arm);
        return json;
    }

    synchronized void requireModeVersion(JsonObject version) {
        if (version == null || !version.has("controlSessionId") || !version.has("generation"))
            throw new ProtocolException(
                    "MODE_PRECONDITION_REQUIRED", 400, "expectedModeVersion is required");
        try {
            if (!version.get("controlSessionId").isJsonPrimitive()
                    || !version.get("controlSessionId").getAsJsonPrimitive().isString()
                    || !version.get("generation").isJsonPrimitive()
                    || !version.get("generation").getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Mode version types");
            long generation = version.get("generation").getAsBigDecimal().longValueExact();
            if (generation < 0L) throw new IllegalArgumentException("Negative generation");
            this.controlSession.requireVersion(
                    version.get("controlSessionId").getAsString(), generation);
        } catch (AgentControlSession.ModeException error) {
            throw error;
        } catch (RuntimeException invalid) {
            throw new ProtocolException(
                    "INVALID_MODE_VERSION",
                    400,
                    "Mode version requires session UUID and non-negative integer generation");
        }
    }

    synchronized CompletableFuture<JsonObject> selectMode(
            AgentControlSession.Mode requested,
            JsonObject expectedVersion,
            String leaseId,
            String requestId,
            String connectionId) {
        this.requireModeChangeReady();
        this.requireModeVersion(expectedVersion);
        if (requested == AgentControlSession.Mode.TAKEOVER)
            throw new ProtocolException(
                    "TAKEOVER_REQUIRES_LEASE_ACQUIRE",
                    409,
                    "Use Control Lease acquire for TAKEOVER");
        if (this.lease == null) {
            this.controlSession.select(requested, "explicit_mode_transition");
            this.audit(
                    requestId,
                    connectionId,
                    "/v0/control/mode",
                    "mode_" + requested.name().toLowerCase(java.util.Locale.ROOT));
            return CompletableFuture.completedFuture(this.modeStatus());
        }
        this.requireLease(leaseId);
        this.modeTransitionPending = true;
        this.releaseLease(leaseId, "mode_transition");
        long handbackGeneration = this.controlSession.snapshot().transitionSequence();
        return this.inputCleanupBarrier.handle(
                (cleanup, error) -> {
                    synchronized (this) {
                        this.modeTransitionPending = false;
                        if (error != null)
                            throw new ProtocolException(
                                    "CONTROL_INPUT_CLEANUP_FAILED", 409, "Input cleanup failed");
                        if (this.closed
                                || this.controlSession.snapshot().transitionSequence()
                                        != handbackGeneration)
                            throw new ProtocolException(
                                    "STALE_MODE_REVISION",
                                    409,
                                    "Intent changed during input cleanup");
                        this.controlSession.select(requested, "explicit_mode_transition");
                        this.audit(
                                requestId,
                                connectionId,
                                "/v0/control/mode",
                                "mode_" + requested.name().toLowerCase(java.util.Locale.ROOT));
                        return this.modeStatus();
                    }
                });
    }

    synchronized JsonObject armDebug(
            String expectedFingerprint,
            String currentFingerprint,
            String sessionEpoch,
            Set<String> requestedNamespaces,
            long requestedTtlMillis) {
        if (currentFingerprint == null || !currentFingerprint.equals(expectedFingerprint)) {
            throw new ProtocolException(
                    "WORLD_FINGERPRINT_MISMATCH", 409, "World fingerprint does not match");
        }
        if (sessionEpoch == null || sessionEpoch.isBlank()) {
            throw new ProtocolException(
                    "INVALID_DEBUG_ARM", 400, "Runtime session epoch is required");
        }
        Set<String> namespaces =
                requestedNamespaces == null || requestedNamespaces.isEmpty()
                        ? Set.of(
                                "player",
                                "entity",
                                "world",
                                "block_entity",
                                "chunk",
                                "menu",
                                "client",
                                "network",
                                "provider")
                        : Set.copyOf(requestedNamespaces);
        for (String namespace : namespaces) {
            if (!Set.of(
                            "player",
                            "entity",
                            "world",
                            "block_entity",
                            "chunk",
                            "menu",
                            "client",
                            "network",
                            "provider",
                            "ui")
                    .contains(namespace)) {
                throw new ProtocolException(
                        "INVALID_DEBUG_ARM", 400, "Unsupported Debug namespace: " + namespace);
            }
        }
        long ttl = Math.max(1_000L, Math.min(requestedTtlMillis, 60_000L));
        if (namespaces.contains("ui") != currentFingerprint.startsWith("ui-session:")
                || (namespaces.contains("ui") && !namespaces.equals(Set.of("ui"))))
            throw new ProtocolException(
                    "INVALID_DEBUG_ARM", 400, "UI and world Arm contexts cannot be mixed");
        this.debugArm =
                new DebugArm(
                        UUID.randomUUID().toString(),
                        currentFingerprint,
                        sessionEpoch,
                        namespaces,
                        this.principalId,
                        System.currentTimeMillis() + ttl);
        this.scheduleDebugExpiry(this.debugArm);
        return this.debugArmJson("armed");
    }

    synchronized JsonObject renewDebug(
            String debugArmId,
            String currentFingerprint,
            String sessionEpoch,
            long requestedTtlMillis) {
        this.requireDebugArm(debugArmId, currentFingerprint, sessionEpoch, null);
        long ttl = Math.max(1_000L, Math.min(requestedTtlMillis, 60_000L));
        this.debugArm =
                new DebugArm(
                        this.debugArm.id(),
                        currentFingerprint,
                        sessionEpoch,
                        this.debugArm.namespaces(),
                        this.principalId,
                        System.currentTimeMillis() + ttl);
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
        this.requireDebugArm(debugArmId, currentFingerprint, null, null);
    }

    synchronized void requireDebugAuthorization(
            String debugArmId,
            String currentFingerprint,
            String sessionEpoch,
            String domain,
            String namespace) {
        this.requireDebugScope(domain);
        this.requireDebugArm(debugArmId, currentFingerprint, sessionEpoch, namespace);
    }

    private synchronized void requireDebugArm(
            String debugArmId, String currentFingerprint, String sessionEpoch, String namespace) {
        this.expireDebugIfNeeded(System.currentTimeMillis());
        if (this.debugArm == null || debugArmId == null || !this.debugArm.id().equals(debugArmId)) {
            throw new ProtocolException("DEBUG_NOT_ARMED", 409, "A valid Debug Arm is required");
        }
        if (!this.debugArm.worldFingerprint().equals(currentFingerprint)) {
            this.debugArm = null;
            throw new ProtocolException(
                    "WORLD_FINGERPRINT_MISMATCH", 409, "Debug Arm belongs to another world");
        }
        if (sessionEpoch != null && !this.debugArm.sessionEpoch().equals(sessionEpoch)) {
            this.debugArm = null;
            throw new ProtocolException(
                    "STALE_SESSION_EPOCH", 409, "Debug Arm belongs to another Runtime session");
        }
        if (!this.debugArm.principalId().equals(this.principalId)) {
            this.debugArm = null;
            throw new ProtocolException("DEBUG_SCOPE_DENIED", 403, "Debug Arm principal mismatch");
        }
        if (namespace != null && !this.debugArm.namespaces().contains(namespace)) {
            throw new ProtocolException(
                    "DEBUG_SCOPE_DENIED", 403, "Debug Arm does not allow namespace: " + namespace);
        }
    }

    synchronized void requireLease(String leaseId) {
        this.expireTaskIfNeeded();
        this.expireLeaseIfNeeded(System.currentTimeMillis());
        if (this.lease == null || leaseId == null || !this.lease.id().equals(leaseId)) {
            if (this.controlSession.snapshot().manuallyRevoked()) {
                throw new ProtocolException(
                        "USER_MANUALLY_ENDED_CONTROL", 409, "用户手动结束控制; reconsentRequired=true");
            }
            throw new ProtocolException(
                    "CONTROL_LEASE_REQUIRED", 409, "A valid input control lease is required");
        }
    }

    <T> CompletableFuture<T> applyDeadline(CompletableFuture<T> future, long deadlineAtMillis) {
        if (deadlineAtMillis <= 0L) return future;
        long remaining = deadlineAtMillis - System.currentTimeMillis();
        if (remaining <= 0L) {
            future.cancel(true);
            return CompletableFuture.failedFuture(
                    new ProtocolException(
                            "REQUEST_DEADLINE_EXCEEDED", 408, "Request deadline has elapsed"));
        }
        AtomicBoolean terminalClaimed = new AtomicBoolean();
        CompletableFuture<T> deadline =
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        if (!terminalClaimed.compareAndSet(false, true)) return isCancelled();
                        future.cancel(mayInterruptIfRunning);
                        return super.cancel(mayInterruptIfRunning);
                    }
                };
        var timeout =
                this.scheduler.schedule(
                        () -> {
                            if (terminalClaimed.compareAndSet(false, true)) {
                                future.cancel(true);
                                deadline.completeExceptionally(
                                        new ProtocolException(
                                                "REQUEST_DEADLINE_EXCEEDED",
                                                408,
                                                "Request deadline has elapsed"));
                            }
                        },
                        remaining,
                        TimeUnit.MILLISECONDS);
        future.whenComplete(
                (value, error) -> {
                    timeout.cancel(false);
                    if (!terminalClaimed.compareAndSet(false, true)) return;
                    if (error == null) deadline.complete(value);
                    else deadline.completeExceptionally(error);
                });
        return deadline;
    }

    CompletableFuture<JsonObject> idempotent(
            String key, Supplier<CompletableFuture<JsonObject>> action) {
        if (key == null || key.isBlank()) return action.get();
        if (this.idempotentResults.size() > 256) this.idempotentResults.clear();
        return this.idempotentResults.computeIfAbsent(key, ignored -> action.get());
    }

    JsonObject startOperation(CompletableFuture<JsonObject> future) {
        return this.startOperation(future, false);
    }

    JsonObject startOperation(
            Function<String, CompletableFuture<JsonObject>> factory, boolean leaseBound) {
        return startOperation(factory, leaseBound, Set.of());
    }

    JsonObject startOperation(
            Function<String, CompletableFuture<JsonObject>> factory,
            boolean leaseBound,
            Set<String> diagnosticDomains) {
        String id = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = factory.apply(id);
        return this.startOperation(id, future, leaseBound, diagnosticDomains);
    }

    JsonObject startOperation(CompletableFuture<JsonObject> future, boolean leaseBound) {
        return this.startOperation(UUID.randomUUID().toString(), future, leaseBound, Set.of());
    }

    private JsonObject startOperation(
            String id,
            CompletableFuture<JsonObject> future,
            boolean leaseBound,
            Set<String> diagnosticDomains) {
        if (this.operations.size() >= 16) {
            this.operations.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        }
        if (this.operations.size() >= 16) {
            future.cancel(true);
            throw new ProtocolException("TOO_MANY_OPERATIONS", 429, "Too many active operations");
        }
        Operation operation = new Operation(id, future, leaseBound, diagnosticDomains);
        this.operations.put(id, operation);
        future.whenComplete((result, error) -> operation.complete(result, error));
        JsonObject started = operation.snapshot();
        if (!diagnosticDomains.isEmpty()) {
            started.remove("result");
            started.remove("error");
            started.remove("errorCode");
            started.remove("errorDetails");
        }
        return started;
    }

    JsonObject operationStatus(String operationId) {
        return this.requireOperation(operationId).snapshot();
    }

    void onOperationTerminal(String operationId, Consumer<JsonObject> observer) {
        Operation operation = this.requireOperation(operationId);
        operation.terminal.thenRun(() -> observer.accept(operation.snapshot()));
    }

    Set<String> operationDiagnosticDomains(String operationId) {
        Operation operation = this.operations.get(operationId);
        return operation == null ? Set.of() : operation.diagnosticDomains;
    }

    Runnable operationReadGuard(
            String operationId, String category, Consumer<String> authorizeWorld) {
        Set<String> domains = operationDiagnosticDomains(operationId);
        if (domains.isEmpty()) return () -> {};
        Runnable authorize =
                () -> {
                    if (domains.contains("UI")) requireUiCategory(category);
                    if (domains.contains("WORLD")) authorizeWorld.accept(category);
                    if (!"DEBUG".equals(category))
                        throw new ProtocolException(
                                "OPERATION_NOT_FOUND", 404, "Unknown operation");
                };
        authorize.run();
        return authorize;
    }

    JsonObject operationSnapshot() {
        JsonArray values = new JsonArray();
        this.operations.values().stream()
                .sorted(
                        (left, right) ->
                                Long.compare(left.receivedAtMillis, right.receivedAtMillis))
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

    synchronized JsonObject noteDebugMutation(
            String debugOperationId, String namespace, String target) {
        long sequence = this.debugMutationSequence.incrementAndGet();
        long now = System.currentTimeMillis();
        for (GameplayActWindow window : this.gameplayActs.values()) {
            window.noteMutation(sequence, debugOperationId);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.evidence.mutation");
        json.addProperty("debugMutationSequence", sequence);
        json.addProperty("timestampMillis", now);
        json.addProperty("debugOperationId", debugOperationId);
        json.addProperty("namespace", namespace);
        json.addProperty("target", target);
        json.addProperty("evidence", "diagnostic");
        json.addProperty("gameplayEvidence", false);
        return json;
    }

    synchronized void noteFixtureMutation(String requestId) {
        for (GameplayActWindow window : this.gameplayActs.values()) window.noteFixture(requestId);
    }

    synchronized void notePeerPrivilegedSubmission(String requestId) {
        for (GameplayActWindow window : this.gameplayActs.values()) {
            window.peerPrivilegedSubmissions++;
            window.latestPeerSubmission = requestId;
        }
    }

    JsonObject startGameplayAct() {
        String id = UUID.randomUUID().toString();
        GameplayActWindow window =
                new GameplayActWindow(
                        id, this.debugMutationSequence.get(), System.currentTimeMillis());
        this.gameplayActs.put(id, window);
        JsonObject json = window.snapshot(false);
        json.addProperty("status", "active");
        return json;
    }

    JsonObject finishGameplayAct(String actId) {
        GameplayActWindow window = this.gameplayActs.remove(actId);
        if (window == null) {
            throw new ProtocolException(
                    "GAMEPLAY_ACT_NOT_FOUND", 404, "Unknown gameplay Act: " + actId);
        }
        JsonObject json = window.snapshot(true);
        json.addProperty("status", "completed");
        return json;
    }

    JsonObject debugEvidenceStatus() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.evidence.status");
        json.addProperty("lastDebugMutationSequence", this.debugMutationSequence.get());
        json.addProperty("activeGameplayActs", this.gameplayActs.size());
        return json;
    }

    CompletableFuture<JsonObject> waitOperation(String operationId, long requestedTimeoutMillis) {
        Operation operation = this.requireOperation(operationId);
        if (!operation.isRunning()) return CompletableFuture.completedFuture(operation.snapshot());
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        operation.terminal.whenComplete((ignored, error) -> result.complete(operation.snapshot()));
        long timeout = Math.max(1L, Math.min(requestedTimeoutMillis, 300_000L));
        var timeoutHandle =
                this.scheduler.schedule(
                        () -> result.complete(operation.snapshot()),
                        timeout,
                        TimeUnit.MILLISECONDS);
        result.whenComplete((ignored, error) -> timeoutHandle.cancel(false));
        return result;
    }

    synchronized void audit(String requestId, String connectionId, String path, String outcome) {
        this.audit.addLast(
                new AuditEntry(
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
                                ? path.substring("/v0/operations/".length()).replace("/wait", "")
                                : ""));
        while (this.audit.size() > 256) this.audit.removeFirst();
    }

    private synchronized void auditProvider(
            DeepObservationRequestContext.ProviderAuditEvent event) {
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
        operations.add(
                descriptor(
                        "ui.resolve",
                        "ui",
                        false,
                        false,
                        false,
                        "screenRevision",
                        "menuRevision",
                        "selectorReresolution"));
        operations.add(
                descriptor(
                        "ui.action",
                        "input",
                        true,
                        true,
                        false,
                        "screenRevision",
                        "menuRevision",
                        "selectorReresolution"));
        operations.add(
                descriptor(
                        "input.mouse",
                        "input",
                        true,
                        true,
                        false,
                        "screenRevision",
                        "menuRevision"));
        operations.add(
                descriptor(
                        "input.key", "input", true, true, false, "screenRevision", "menuRevision"));
        operations.add(descriptor("input.release_all", "input", true, false, false));
        operations.add(
                descriptor(
                        "command.player.execute",
                        "command",
                        false,
                        false,
                        false,
                        "currentPlayerPermissions",
                        "nativePlayerCommandTree",
                        "serverValidation"));
        JsonObject commandDescriptor = operations.get(operations.size() - 1).getAsJsonObject();
        commandDescriptor.addProperty("modeRequirement", "COMMAND_ROUTE_DEPENDENT");
        JsonObject commandRoutes = new JsonObject();
        JsonObject chat = new JsonObject();
        chat.addProperty("modeRequirement", "TAKEOVER_REQUIRED");
        chat.addProperty("requiresControlLease", true);
        JsonArray commandScopes = new JsonArray();
        commandScopes.add("control");
        commandScopes.add("input");
        chat.add("additionalScopes", commandScopes);
        commandRoutes.add("CHAT", chat);
        JsonObject nativeRoute = new JsonObject();
        nativeRoute.addProperty("modeRequirement", "OPERATE_OR_TAKEOVER");
        nativeRoute.addProperty("requiresControlLease", false);
        nativeRoute.add("additionalScopes", new JsonArray());
        commandRoutes.add("NATIVE", nativeRoute);
        commandDescriptor.add("routes", commandRoutes);
        commandDescriptor.addProperty("defaultRoute", "AUTO");
        commandDescriptor.addProperty("autoRoute", "OPERATE:NATIVE;READ/TAKEOVER:CHAT");
        operations.add(descriptor("capture.composite", "capture", false, false, true));
        operations.add(descriptor("wait.screen", "read", false, false, true));
        operations.add(descriptor("wait.until", "read", false, false, true));
        operations.add(descriptor("assert.that", "read", false, false, false));
        operations.add(
                descriptor(
                        "pipeline.execute",
                        "input",
                        true,
                        false,
                        true,
                        "screenRevision",
                        "menuRevision",
                        "leasePerStep",
                        "selectorReresolution"));
        operations.add(descriptor("observation.client_live", "read", false, false, false));
        operations.add(
                descriptor("observation.server_authoritative_live", "read", false, false, false));
        operations.add(descriptor("server.peer.status", "read", false, false, false));
        operations.add(
                descriptor(
                        "server.peer.probe",
                        "read",
                        false,
                        false,
                        true,
                        "peerNegotiated",
                        "serverAuthority"));
        operations.add(descriptor("provider.read", "read", false, false, true));
        operations.add(descriptor("state.frame", "read", false, false, true));
        operations.add(descriptor("fixture.player.teleport", "fixture", false, false, false));
        operations.add(
                descriptor(
                        "debug.player.health",
                        "debug",
                        false,
                        false,
                        false,
                        "debugArm",
                        "worldFingerprint"));
        operations.add(
                descriptor(
                        "debug.world.block",
                        "debug",
                        false,
                        false,
                        false,
                        "debugArm",
                        "worldFingerprint",
                        "expectedBlockState"));
        operations.add(
                descriptor(
                        "debug.mutation",
                        "debug.write",
                        false,
                        true,
                        true,
                        "debugArm",
                        "worldFingerprint",
                        "expectedResourceVersion",
                        "valuePreconditions"));
        operations.add(
                descriptor(
                        "debug.batch",
                        "debug.write",
                        false,
                        false,
                        true,
                        "debugArmPerItem",
                        "worldFingerprintPerItem",
                        "resourceVersionPerItem",
                        "valuePreconditionsPerItem"));
        operations.add(
                descriptor(
                        "debug.evidence.gameplay_act",
                        "debug",
                        false,
                        false,
                        false,
                        "debugContaminationWindow"));
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
        json.addProperty("activeDeepObservations", this.deepObservations.size());
        json.addProperty("maxActiveDeepObservations", MAX_ACTIVE_DEEP_OBSERVATIONS);
        json.addProperty("debugMutationSequence", this.debugMutationSequence.get());
        this.controlPresence(json);
        return json;
    }

    @Override
    public void close() {
        List<Operation> pendingOperations;
        List<DeepObservationRequestContext> pendingReads;
        synchronized (this) {
            if (this.closed) return;
            pendingOperations =
                    this.operations.values().stream()
                            .filter(operation -> operation.markCancelled("transport_close"))
                            .toList();
            this.closed = true;
            this.modeTransitionPending = false;
            boolean hadLease = this.lease != null;
            this.lease = null;
            this.debugArm = null;
            this.controlSession.close();
            this.agentTask.close();
            this.cleanupInput("transport_close");
            if (hadLease)
                this.audit(
                        UUID.randomUUID().toString(),
                        "control",
                        "/v0/control/transport-close",
                        "control_released");
            pendingReads = List.copyOf(this.deepObservations.values());
            this.deepObservations.clear();
            this.gameplayActs.clear();
        }
        // Native cancellation barriers may be held by an owner waiting to reauthorize here.
        // Never wait for those barriers while holding the ProtocolState monitor.
        for (Operation operation : pendingOperations) operation.cancelMarkedFuture();
        for (DeepObservationRequestContext requestContext : pendingReads) {
            requestContext.cancel("transport_close");
        }
        this.scheduler.shutdownNow();
    }

    private synchronized void expireLeaseIfNeeded(long now) {
        if (this.lease != null && this.lease.expiresAtMillis() <= now) {
            this.lease = null;
            this.controlSession.release("lease_expired");
            this.cancelLeaseBoundOperations("lease_expired");
            this.cleanupInput("lease_expired");
            this.audit(
                    UUID.randomUUID().toString(),
                    "control",
                    "/v0/control/lease-expiry",
                    "control_released");
        }
    }

    private void scheduleLeaseExpiry(ControlLease scheduledLease) {
        long delay = Math.max(1L, scheduledLease.expiresAtMillis() - System.currentTimeMillis());
        this.scheduler.schedule(
                () -> {
                    synchronized (this) {
                        if (this.lease != null
                                && this.lease.id().equals(scheduledLease.id())
                                && this.lease.expiresAtMillis() <= System.currentTimeMillis()) {
                            this.lease = null;
                            this.controlSession.release("lease_expired");
                            this.cancelLeaseBoundOperations("lease_expired");
                            this.cleanupInput("lease_expired");
                            this.audit(
                                    UUID.randomUUID().toString(),
                                    "control",
                                    "/v0/control/lease-expiry",
                                    "control_released");
                        }
                    }
                },
                delay,
                TimeUnit.MILLISECONDS);
    }

    private void cancelLeaseBoundOperations(String reason) {
        // Cancelling one Future can synchronously drain its queue and start another entry.
        // Commit every affected terminal before permitting any of those callbacks to run.
        List<Operation> cancelled =
                this.operations.values().stream()
                        .filter(
                                operation ->
                                        operation.leaseBound && operation.markCancelled(reason))
                        .toList();
        for (Operation operation : cancelled) operation.cancelMarkedFuture();
    }

    private synchronized void expireDebugIfNeeded(long now) {
        if (this.debugArm != null && this.debugArm.expiresAtMillis() <= now) this.debugArm = null;
    }

    private void scheduleDebugExpiry(DebugArm scheduledArm) {
        long delay = Math.max(1L, scheduledArm.expiresAtMillis() - System.currentTimeMillis());
        this.scheduler.schedule(
                () -> {
                    synchronized (this) {
                        if (this.debugArm != null
                                && this.debugArm.id().equals(scheduledArm.id())
                                && this.debugArm.expiresAtMillis() <= System.currentTimeMillis()) {
                            this.debugArm = null;
                        }
                    }
                },
                delay,
                TimeUnit.MILLISECONDS);
    }

    private JsonObject leaseJson(String status) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "control.lease");
        json.addProperty("status", status);
        json.addProperty("leaseId", this.lease.id());
        json.addProperty("expiresAtMillis", this.lease.expiresAtMillis());
        return this.controlPresence(json);
    }

    JsonObject controlPresence(JsonObject json) {
        AgentControlSession.Snapshot snapshot = this.controlSession.snapshot();
        json.addProperty("controlState", snapshot.state().name());
        json.addProperty("reconsentRequired", snapshot.reconsentRequired());
        json.addProperty("reconsentScope", "TAKEOVER_ONLY");
        json.addProperty("controlTransitionSequence", snapshot.transitionSequence());
        json.addProperty("mode", snapshot.mode().name());
        json.addProperty("takeoverActive", snapshot.agentControlled());
        json.addProperty("modeTransitionReason", snapshot.reason());
        JsonObject version = new JsonObject();
        version.addProperty("controlSessionId", snapshot.controlSessionId());
        version.addProperty("generation", snapshot.transitionSequence());
        json.add("modeVersion", version);
        if (snapshot.manuallyRevoked()) {
            json.addProperty("message", snapshot.message());
            json.addProperty("manualRevocationReason", "human_manual_revocation");
        }
        return json;
    }

    private JsonObject debugArmJson(String status) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.arm");
        json.addProperty("status", status);
        json.addProperty("debugArmId", this.debugArm.id());
        if (this.debugArm.namespaces().equals(Set.of("ui"))) {
            json.addProperty("uiContextId", this.debugArm.worldFingerprint());
            json.addProperty("authority", "CLIENT_UI");
        } else json.addProperty("worldFingerprint", this.debugArm.worldFingerprint());
        json.addProperty("sessionEpoch", this.debugArm.sessionEpoch());
        json.addProperty("principalId", this.debugArm.principalId());
        json.addProperty("expiresAtMillis", this.debugArm.expiresAtMillis());
        JsonArray namespaces = new JsonArray();
        this.debugArm.namespaces().stream().sorted().forEach(namespaces::add);
        json.add("namespaces", namespaces);
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
            String id,
            String scope,
            boolean lease,
            boolean idempotency,
            boolean cancellation,
            String... preconditions) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("scope", scope);
        json.addProperty("requiresControlLease", lease);
        json.addProperty(
                "modeRequirement",
                lease
                        ? "TAKEOVER_REQUIRED"
                        : id.startsWith("fixture.")
                                        || id.startsWith("debug.")
                                                && !id.startsWith("debug.evidence.")
                                ? "OPERATE_OR_TAKEOVER"
                                : "READ_COMPATIBLE");
        json.addProperty("supportsIdempotency", idempotency);
        json.addProperty("supportsCancellation", cancellation);
        json.addProperty(
                "requiresDebugArm", id.startsWith("debug.") && !id.startsWith("debug.evidence."));
        String affinity =
                id.startsWith("capture")
                        ? "render_thread"
                        : id.startsWith("server.peer")
                                ? "multi_thread"
                                : id.contains("server_authoritative")
                                        ? "server_thread"
                                        : id.equals("state.frame") || id.equals("provider.read")
                                                ? "multi_thread"
                                                : "client_thread";
        json.addProperty("threadAffinity", affinity);
        JsonArray supportedPreconditions = new JsonArray();
        for (String precondition : preconditions) supportedPreconditions.add(precondition);
        json.add("supportedPreconditions", supportedPreconditions);
        if (id.equals("ui.action")) {
            JsonArray additional = new JsonArray();
            AutomationEngine.STEP_ADDITIONAL_SCOPES.get("ui.action").forEach(additional::add);
            json.add("additionalScopes", additional);
        }
        if (id.equals("input.release_all")) {
            JsonArray additional = new JsonArray();
            additional.add("control");
            json.add("additionalScopes", additional);
        }
        if (id.equals("pipeline.execute")) {
            JsonObject byStep = new JsonObject();
            new java.util.TreeMap<>(AutomationEngine.STEP_ADDITIONAL_SCOPES)
                    .forEach(
                            (type, scopes) -> {
                                JsonArray additional = new JsonArray();
                                scopes.forEach(additional::add);
                                byStep.add(type, additional);
                            });
            json.add("additionalScopesByStep", byStep);
        }
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
            String debugArmId) {}

    public static final class ProtocolException extends RuntimeException {
        private final String code;
        private final int httpStatus;
        private final JsonObject details;

        public ProtocolException(String code, int httpStatus, String message) {
            this(code, httpStatus, message, new JsonObject());
        }

        public ProtocolException(String code, int httpStatus, String message, JsonObject details) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
            this.details = details.deepCopy();
        }

        public String code() {
            return this.code;
        }

        public int httpStatus() {
            return this.httpStatus;
        }

        public JsonObject details() {
            return this.details.deepCopy();
        }
    }

    private record ControlLease(String id, long expiresAtMillis) {}

    private record DebugArm(
            String id,
            String worldFingerprint,
            String sessionEpoch,
            Set<String> namespaces,
            String principalId,
            long expiresAtMillis) {}

    static final class CancellableOperationFuture extends CompletableFuture<JsonObject> {
        private final Runnable cancelHook;
        private final Supplier<JsonObject> cancellationSnapshot;

        CancellableOperationFuture(Runnable cancelHook, Supplier<JsonObject> cancellationSnapshot) {
            this.cancelHook = cancelHook;
            this.cancellationSnapshot = cancellationSnapshot;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            this.cancelHook.run();
            return super.cancel(mayInterruptIfRunning);
        }

        JsonObject cancellationSnapshot() {
            return this.cancellationSnapshot.get();
        }
    }

    private static final class GameplayActWindow {
        private final String id;
        private final long debugSequenceAtStart;
        private final long startedAtMillis;
        private volatile long latestDebugSequence;
        private volatile String latestDebugOperationId = "";
        private volatile int fixtureMutationCount;
        private volatile String latestFixtureOperationId = "";
        private volatile int peerPrivilegedSubmissions;
        private volatile String latestPeerSubmission = "";

        private GameplayActWindow(String id, long debugSequenceAtStart, long startedAtMillis) {
            this.id = id;
            this.debugSequenceAtStart = debugSequenceAtStart;
            this.startedAtMillis = startedAtMillis;
            this.latestDebugSequence = debugSequenceAtStart;
        }

        private void noteMutation(long sequence, String operationId) {
            this.latestDebugSequence = sequence;
            this.latestDebugOperationId = operationId;
        }

        private void noteFixture(String requestId) {
            this.fixtureMutationCount++;
            this.latestFixtureOperationId = requestId;
        }

        private JsonObject snapshot(boolean terminal) {
            boolean contaminated =
                    this.latestDebugSequence > this.debugSequenceAtStart
                            || this.fixtureMutationCount > 0
                            || this.peerPrivilegedSubmissions > 0;
            JsonObject json = new JsonObject();
            json.addProperty("type", "debug.evidence.gameplay_act");
            json.addProperty("actId", this.id);
            json.addProperty("startedAtMillis", this.startedAtMillis);
            if (terminal) json.addProperty("completedAtMillis", System.currentTimeMillis());
            json.addProperty("debugSequenceAtStart", this.debugSequenceAtStart);
            json.addProperty("lastDebugMutationSequence", this.latestDebugSequence);
            json.addProperty(
                    "debugMutationCount",
                    Math.max(0L, this.latestDebugSequence - this.debugSequenceAtStart));
            json.addProperty("debugOperationDuringAct", this.latestDebugOperationId);
            json.addProperty("fixtureMutationCount", this.fixtureMutationCount);
            json.addProperty("fixtureOperationDuringAct", this.latestFixtureOperationId);
            json.addProperty("peerPrivilegedSubmissionCount", this.peerPrivilegedSubmissions);
            json.addProperty("peerSubmissionDuringAct", this.latestPeerSubmission);
            json.addProperty("contaminated", contaminated);
            json.addProperty(
                    "gameplayEvidence", contaminated ? "invalid_for_acceptance" : "gameplay");
            return json;
        }
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
            String operationId) {}

    private static final class Operation {
        private final Set<String> diagnosticDomains;
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
        private volatile JsonObject errorDetails;
        private volatile String cancellationReason;
        private volatile long completedAtMillis;

        private Operation(
                String id,
                CompletableFuture<JsonObject> future,
                boolean leaseBound,
                Set<String> diagnosticDomains) {
            this.id = id;
            this.future = future;
            this.leaseBound = leaseBound;
            this.diagnosticDomains = Set.copyOf(diagnosticDomains);
        }

        private void complete(JsonObject result, Throwable error) {
            synchronized (this) {
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
                    this.error =
                            cause instanceof TimeoutException
                                    ? "operation timed out"
                                    : cause.getMessage();
                    if (cause instanceof ProtocolException protocolException) {
                        this.errorCode = protocolException.code();
                        JsonObject details = protocolException.details();
                        JsonObject stale =
                                ResourceVersionVerifier.staleMetadata(this.errorCode, details);
                        if (stale.size() > 0) this.errorDetails = stale;
                        if ((this.errorCode.equals("ASSERTION_FAILED")
                                        || this.errorCode.equals("WAIT_TIMEOUT"))
                                && details.has("readiness")
                                && details.get("readiness").isJsonObject()) {
                            this.errorDetails = new JsonObject();
                            this.errorDetails.add("readiness", details.get("readiness").deepCopy());
                        }
                        if (this.errorCode.equals("WORLD_TARGET_NOT_HIT")
                                && details.has("actualHit")
                                && details.get("actualHit").isJsonObject())
                            this.errorDetails =
                                    WorldHitEvidence.details(details.getAsJsonObject("actualHit"));
                    } else if (cause instanceof TimeoutException)
                        this.errorCode = "OPERATION_TIMEOUT";
                    boolean timedOut =
                            cause instanceof TimeoutException
                                    || "PIPELINE_TIMEOUT".equals(this.errorCode)
                                    || "WAIT_TIMEOUT".equals(this.errorCode)
                                    || "REQUEST_DEADLINE_EXCEEDED".equals(this.errorCode);
                    this.status = timedOut ? "timed_out" : "failed";
                    this.state = this.status;
                }
            }
            // Observers can take control/session locks; never invoke them under the Operation lock.
            this.terminal.complete(null);
        }

        private void cancel(String reason) {
            if (this.markCancelled(reason)) this.cancelMarkedFuture();
        }

        private boolean markCancelled(String reason) {
            synchronized (this) {
                if (this.state.equals("executing")
                        || this.state.equals("scheduled")
                        || this.state.equals("accepted")) {
                    this.status = "cancelled";
                    this.state = "cancelled";
                    this.cancellationReason = reason;
                    this.completedAtMillis = System.currentTimeMillis();
                    return true;
                }
                return false;
            }
        }

        private void cancelMarkedFuture() {
            try {
                this.future.cancel(true);
                if (this.future instanceof CancellableOperationFuture cancellable) {
                    JsonObject captured = cancellable.cancellationSnapshot();
                    synchronized (this) {
                        this.result = captured;
                    }
                }
            } finally {
                this.terminal.complete(null);
            }
        }

        private boolean isRunning() {
            return this.status.equals("running");
        }

        private synchronized JsonObject snapshot() {
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
            if (this.completedAtMillis > 0L)
                json.addProperty("completedAtMillis", this.completedAtMillis);
            JsonObject capturedResult = this.result == null ? null : this.result.deepCopy();
            if (this.future instanceof PlayerCommandEvidence.Source source) {
                // Read completeness first: true must never accompany an earlier incomplete list.
                // Cancellation can become terminal before the native owner-thread cleanup drains.
                json.addProperty("commandSubmissionsComplete", source.commandSubmissionsComplete());
                var receipts = source.commandSubmissions();
                if (!receipts.isEmpty()) {
                    if (capturedResult == null) {
                        capturedResult = new JsonObject();
                        capturedResult.addProperty("type", "input.command.provenance");
                        capturedResult.addProperty("partial", true);
                    }
                    PlayerCommandEvidence.attach(capturedResult, receipts);
                }
            }
            if (capturedResult != null) json.add("result", capturedResult);
            if (this.error != null) json.addProperty("error", this.error);
            if (this.errorCode != null) json.addProperty("errorCode", this.errorCode);
            if (this.errorDetails != null) json.add("errorDetails", this.errorDetails.deepCopy());
            if (this.cancellationReason != null)
                json.addProperty("cancellationReason", this.cancellationReason);
            return json;
        }
    }
}
