package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

/** Bounded, cancellation-aware scheduler for typed Phase 9C Debug mutations. */
final class DebugBatchEngine implements AutoCloseable {
    static final int MAX_BATCH_ITEMS = 64;
    static final int MAX_BATCH_BYTES = 256 * 1024;
    static final int MAX_PER_TICK_MUTATIONS = 4;
    static final long MAX_TOTAL_DURATION_MILLIS = 30_000L;
    private static final Gson GSON = new Gson();

    private final ProbeService service;
    private final ProtocolState protocolState;
    private final Consumer<JsonObject> mutationObserver;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    DebugBatchEngine(
            ProbeService service,
            ProtocolState protocolState,
            Consumer<JsonObject> mutationObserver) {
        this.service = service;
        this.protocolState = protocolState;
        this.mutationObserver = mutationObserver;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-debug-batch");
            thread.setDaemon(true);
            return thread;
        });
    }

    CompletableFuture<JsonObject> start(
            String operationId,
            JsonObject request,
            ProtocolState.RequestMetadata metadata) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new CancellationException("debug_batch_runtime_closed"));
        }
        if (!request.has("items") || !request.get("items").isJsonArray()) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEBUG_BATCH", 400, "Debug batch requires items");
        }
        JsonArray items = request.getAsJsonArray("items");
        if (items.isEmpty() || items.size() > MAX_BATCH_ITEMS) {
            throw new ProtocolState.ProtocolException(
                    "BATCH_BUDGET_EXCEEDED", 413,
                    "Debug batch supports 1 to " + MAX_BATCH_ITEMS + " items");
        }
        int bytes = GSON.toJson(request).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BATCH_BYTES) {
            throw new ProtocolState.ProtocolException(
                    "BATCH_BUDGET_EXCEEDED", 413,
                    "Debug batch exceeds " + MAX_BATCH_BYTES + " serialized bytes");
        }
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                throw new ProtocolState.ProtocolException(
                        "INVALID_DEBUG_BATCH", 400, "Every Debug batch item must be an object");
            }
        }
        String failurePolicy = request.has("failurePolicy")
                ? request.get("failurePolicy").getAsString() : "STOP_ON_FAILURE";
        if (!failurePolicy.equals("STOP_ON_FAILURE")
                && !failurePolicy.equals("CONTINUE_ON_FAILURE")) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEBUG_BATCH", 400,
                    "failurePolicy must be STOP_ON_FAILURE or CONTINUE_ON_FAILURE");
        }
        int perTick = request.has("maxPerTickMutations")
                ? request.get("maxPerTickMutations").getAsInt() : MAX_PER_TICK_MUTATIONS;
        if (perTick < 1 || perTick > MAX_PER_TICK_MUTATIONS) {
            throw new ProtocolState.ProtocolException(
                    "BATCH_BUDGET_EXCEEDED", 413,
                    "maxPerTickMutations must be 1 to " + MAX_PER_TICK_MUTATIONS);
        }
        long duration = request.has("maxTotalDurationMs")
                ? request.get("maxTotalDurationMs").getAsLong() : MAX_TOTAL_DURATION_MILLIS;
        if (duration < 1L || duration > MAX_TOTAL_DURATION_MILLIS) {
            throw new ProtocolState.ProtocolException(
                    "BATCH_BUDGET_EXCEEDED", 413,
                    "maxTotalDurationMs must be 1 to " + MAX_TOTAL_DURATION_MILLIS);
        }
        Execution execution = new Execution(
                operationId,
                items.deepCopy(),
                failurePolicy,
                perTick,
                Math.min(System.currentTimeMillis() + duration,
                        metadata.deadlineAtMillis() > 0L
                                ? metadata.deadlineAtMillis() : Long.MAX_VALUE),
                metadata);
        execution.start();
        return execution.result;
    }

    JsonObject capabilities() {
        JsonObject json = new JsonObject();
        json.addProperty("maxBatchItems", MAX_BATCH_ITEMS);
        json.addProperty("maxBatchBytes", MAX_BATCH_BYTES);
        json.addProperty("maxPerTickMutations", MAX_PER_TICK_MUTATIONS);
        json.addProperty("maxTotalDurationMs", MAX_TOTAL_DURATION_MILLIS);
        json.addProperty("transactional", false);
        json.addProperty("failurePolicies", "STOP_ON_FAILURE,CONTINUE_ON_FAILURE");
        json.addProperty("cancellationBarrier", "owner_thread_permit");
        return json;
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.scheduler.shutdownNow();
    }

    private final class Execution implements DebugMutationAuthorization {
        private final String operationId;
        private final JsonArray items;
        private final String failurePolicy;
        private final int perTick;
        private final long deadlineAtMillis;
        private final ProtocolState.RequestMetadata metadata;
        private final JsonArray results = new JsonArray();
        private final StampedLock cancellationBarrier = new StampedLock();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final long startedAtMillis = System.currentTimeMillis();
        private final ProtocolState.CancellableOperationFuture result =
                new ProtocolState.CancellableOperationFuture(this::cancel, this::snapshot);
        private volatile CompletableFuture<JsonObject> currentChild;
        private volatile ScheduledFuture<?> pendingSchedule;
        private int index;
        private int inTick;
        private int succeeded;
        private int failed;
        private String terminalReason = "";

        private Execution(
                String operationId,
                JsonArray items,
                String failurePolicy,
                int perTick,
                long deadlineAtMillis,
                ProtocolState.RequestMetadata metadata) {
            this.operationId = operationId;
            this.items = items;
            this.failurePolicy = failurePolicy;
            this.perTick = perTick;
            this.deadlineAtMillis = deadlineAtMillis;
            this.metadata = metadata;
        }

        private void start() {
            this.schedule(0L);
        }

        private void schedule(long delayMillis) {
            if (this.cancelled.get() || this.terminal.get() || closed.get()) return;
            this.pendingSchedule = scheduler.schedule(this::runNext, delayMillis, TimeUnit.MILLISECONDS);
        }

        private void runNext() {
            if (this.cancelled.get() || this.terminal.get() || closed.get()) return;
            if (System.currentTimeMillis() >= this.deadlineAtMillis) {
                this.finish("timed_out", "deadline_exceeded");
                return;
            }
            if (this.index >= this.items.size()) {
                this.finish(this.failed == 0 ? "completed" : "partial", "");
                return;
            }
            if (this.inTick >= this.perTick) {
                this.inTick = 0;
                this.schedule(50L);
                return;
            }
            int itemIndex = this.index++;
            this.inTick++;
            JsonObject item = this.items.get(itemIndex).getAsJsonObject().deepCopy();
            item.addProperty("debugOperationId", this.operationId);
            long itemStarted = System.nanoTime();
            CompletableFuture<JsonObject> child;
            try {
                child = service.phase9cDebugMutation(item, this);
            } catch (Throwable throwable) {
                this.acceptFailure(itemIndex, item, throwable, itemStarted);
                return;
            }
            this.currentChild = child;
            child.whenComplete((value, error) -> {
                this.currentChild = null;
                if (this.cancelled.get() || this.terminal.get()) return;
                if (error != null) {
                    this.acceptFailure(itemIndex, item, error, itemStarted);
                    return;
                }
                JsonObject itemResult = itemResult(itemIndex, "completed", item, itemStarted);
                itemResult.add("result", value.deepCopy());
                this.results.add(itemResult);
                this.succeeded++;
                mutationObserver.accept(value.deepCopy());
                this.schedule(0L);
            });
        }

        private void acceptFailure(
                int itemIndex, JsonObject item, Throwable throwable, long itemStarted) {
            Throwable cause = ProbeTransport.unwrapForInternalUse(throwable);
            JsonObject itemResult = itemResult(itemIndex, "failed", item, itemStarted);
            if (cause instanceof ProtocolState.ProtocolException protocolException) {
                itemResult.addProperty("error", protocolException.code());
            } else if (cause instanceof CancellationException) {
                itemResult.addProperty("error", "DEBUG_OPERATION_CANCELLED");
            } else {
                itemResult.addProperty("error", cause.getClass().getSimpleName());
            }
            itemResult.addProperty("message", cause.getMessage() == null ? "unknown" : cause.getMessage());
            this.results.add(itemResult);
            this.failed++;
            boolean authorizationFailure = itemResult.get("error").getAsString().startsWith("DEBUG_")
                    || itemResult.get("error").getAsString().equals("WORLD_FINGERPRINT_MISMATCH")
                    || itemResult.get("error").getAsString().equals("STALE_SESSION_EPOCH");
            String errorCode = itemResult.get("error").getAsString();
            String errorMessage = itemResult.has("message")
                    ? itemResult.get("message").getAsString() : "";
            boolean worldLifecycleFailure = java.util.List.of(
                    "SERVER_AUTHORITATIVE_UNAVAILABLE", "SERVER_PLAYER_UNAVAILABLE",
                    "RUNTIME_NOT_READY").contains(errorCode)
                    || errorCode.equals("CAPABILITY_UNAVAILABLE")
                    && (errorMessage.contains("Integrated Server authority")
                            || errorMessage.contains("Dedicated Peer"));
            if (authorizationFailure || worldLifecycleFailure
                    || this.failurePolicy.equals("STOP_ON_FAILURE")) {
                this.finish("partial", itemResult.get("error").getAsString());
            } else {
                this.schedule(0L);
            }
        }

        private JsonObject itemResult(
                int itemIndex, String status, JsonObject item, long itemStarted) {
            JsonObject json = new JsonObject();
            json.addProperty("index", itemIndex);
            json.addProperty("status", status);
            json.addProperty("operation", item.has("operation")
                    ? item.get("operation").getAsString() : "unknown");
            json.addProperty("durationMicros", (System.nanoTime() - itemStarted) / 1_000L);
            return json;
        }

        private void finish(String status, String reason) {
            if (!this.terminal.compareAndSet(false, true)) return;
            this.terminalReason = reason;
            JsonObject snapshot = this.snapshot();
            snapshot.addProperty("status", status);
            if (!reason.isEmpty()) snapshot.addProperty("reason", reason);
            this.result.complete(snapshot);
        }

        private void cancel() {
            if (!this.cancelled.compareAndSet(false, true)) return;
            long stamp = this.cancellationBarrier.writeLock();
            try {
                this.terminalReason = "client_cancel";
                ScheduledFuture<?> schedule = this.pendingSchedule;
                if (schedule != null) schedule.cancel(false);
                CompletableFuture<JsonObject> child = this.currentChild;
                if (child != null) child.cancel(false);
            } finally {
                this.cancellationBarrier.unlockWrite(stamp);
            }
        }

        private JsonObject snapshot() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "debug.batch.result");
            json.addProperty("operationId", this.operationId);
            json.addProperty("status", this.cancelled.get() ? "cancelled"
                    : this.terminal.get() ? (this.failed == 0 ? "completed" : "partial") : "running");
            json.addProperty("failurePolicy", this.failurePolicy);
            json.addProperty("itemCount", this.items.size());
            json.addProperty("completedItems", this.succeeded + this.failed);
            json.addProperty("succeededItems", this.succeeded);
            json.addProperty("failedItems", this.failed);
            json.addProperty("notStartedItems", Math.max(0, this.items.size() - this.index));
            json.addProperty("startedAtMillis", this.startedAtMillis);
            json.addProperty("completedAtMillis", System.currentTimeMillis());
            json.addProperty("postCancelMutations", 0);
            if (!this.terminalReason.isEmpty()) json.addProperty("reason", this.terminalReason);
            json.add("items", this.results.deepCopy());
            return json;
        }

        @Override
        public Permit authorize(
                String currentWorldFingerprint,
                String sessionEpoch,
                String domain,
                String namespace) {
            long stamp = this.cancellationBarrier.readLock();
            boolean accepted = false;
            try {
                if (this.cancelled.get() || this.terminal.get()) {
                    throw new CancellationException("debug_batch_cancelled");
                }
                protocolState.requireDebugAuthorization(
                        this.metadata.debugArmId(), currentWorldFingerprint,
                        sessionEpoch, domain, namespace);
                accepted = true;
                return () -> this.cancellationBarrier.unlockRead(stamp);
            } finally {
                if (!accepted) this.cancellationBarrier.unlockRead(stamp);
            }
        }

        @Override
        public boolean hasScope(String scope) {
            return protocolState.hasScope(scope);
        }

        @Override
        public String principalId() {
            return protocolState.principalId();
        }

        @Override
        public String debugArmId() {
            return this.metadata.debugArmId();
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled.get() || this.terminal.get();
        }
    }
}
