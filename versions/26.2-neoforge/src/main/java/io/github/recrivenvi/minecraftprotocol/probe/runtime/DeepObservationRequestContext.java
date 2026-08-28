package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated request context and cancellation tree for one formal Deep Observation. */
final class DeepObservationRequestContext {
    @FunctionalInterface
    interface AuditSink {
        void record(ProviderAuditEvent event);
    }

    record ProviderAuditEvent(
            String requestId,
            String connectionId,
            String principalId,
            String providerId,
            Set<String> requiredScopes,
            String decision,
            String perspective,
            String readEffects,
            long durationMicros,
            String status) {
    }

    private final Set<String> grantedScopes;
    private final String principalId;
    private final String requestId;
    private final String connectionId;
    private final long deadlineAtMillis;
    private final AuditSink auditSink;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private volatile String cancellationReason = "";

    DeepObservationRequestContext(
            Set<String> grantedScopes,
            String principalId,
            String requestId,
            String connectionId,
            long deadlineAtMillis,
            AuditSink auditSink) {
        this.grantedScopes = Set.copyOf(grantedScopes);
        this.principalId = principalId;
        this.requestId = requestId;
        this.connectionId = connectionId;
        this.deadlineAtMillis = deadlineAtMillis;
        this.auditSink = auditSink;
    }

    boolean hasScopes(Set<String> required) {
        return this.grantedScopes.containsAll(required);
    }

    long deadlineAtMillis() {
        return this.deadlineAtMillis;
    }

    boolean isCancelled() {
        return this.cancelled.get();
    }

    String cancellationReason() {
        return this.cancellationReason;
    }

    <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        if (this.cancelled.get()) {
            future.cancel(true);
            return future;
        }
        this.pending.add(future);
        if (this.cancelled.get() && this.pending.remove(future)) future.cancel(true);
        future.whenComplete((value, error) -> this.pending.remove(future));
        return future;
    }

    void cancel(String reason) {
        if (!this.cancelled.compareAndSet(false, true)) return;
        this.cancellationReason = reason;
        for (CompletableFuture<?> future : this.pending) future.cancel(true);
        this.pending.clear();
    }

    int pendingCount() {
        return this.pending.size();
    }

    void audit(
            String providerId,
            Set<String> requiredScopes,
            String decision,
            String perspective,
            String readEffects,
            long durationMicros,
            String status) {
        this.auditSink.record(new ProviderAuditEvent(
                this.requestId,
                this.connectionId,
                this.principalId,
                providerId,
                Set.copyOf(requiredScopes),
                decision,
                perspective,
                readEffects,
                durationMicros,
                status));
    }
}

