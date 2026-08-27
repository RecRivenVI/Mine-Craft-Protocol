package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded logical activity, independent of sockets, mode, permissions and the input Lease. */
public final class AgentTaskLifecycle {
    public static final long ACTIVITY_GRACE_MILLIS = 15_000;

    public record Snapshot(
            String taskId,
            String status,
            String reason,
            long generation,
            long expiresAtMillis,
            long deadlineAtMillis) {
        public boolean active() {
            return status.equals("ACTIVE");
        }
    }

    private final LongSupplier clock;
    private Snapshot snapshot = new Snapshot("", "IDLE", "initial", 0, 0, 0);
    private boolean explicitLifecycle, closed;
    private long lastActivity = Long.MIN_VALUE;

    public AgentTaskLifecycle() {
        this(System::currentTimeMillis);
    }

    public AgentTaskLifecycle(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void activity() {
        if (!closed && !explicitLifecycle) lastActivity = clock.getAsLong();
    }

    public synchronized boolean present() {
        long now = clock.getAsLong();
        return !closed
                && (snapshot.active() && now < snapshot.expiresAtMillis()
                        || !explicitLifecycle
                                && lastActivity != Long.MIN_VALUE
                                && now >= lastActivity
                                && now - lastActivity < ACTIVITY_GRACE_MILLIS);
    }

    public synchronized Snapshot begin(long ttlMillis, long budgetMillis) {
        requireOpen();
        if (snapshot.active())
            throw failure("AGENT_TASK_CONFLICT", "An Agent task is already active");
        requireBudgets(ttlMillis, budgetMillis);
        long now = clock.getAsLong();
        explicitLifecycle = true;
        snapshot =
                new Snapshot(
                        UUID.randomUUID().toString(),
                        "ACTIVE",
                        "task_started",
                        snapshot.generation() + 1,
                        now + Math.min(ttlMillis, budgetMillis),
                        now + budgetMillis);
        return snapshot;
    }

    public synchronized Snapshot heartbeat(String id, long ttlMillis) {
        requireActive(id);
        requireBudgets(ttlMillis, 300_000);
        long now = clock.getAsLong();
        if (now >= snapshot.expiresAtMillis())
            throw failure(
                    "AGENT_TASK_EXPIRED", "Agent task expired; do not replay previous actions");
        snapshot =
                new Snapshot(
                        id,
                        "ACTIVE",
                        "task_heartbeat",
                        snapshot.generation(),
                        Math.min(snapshot.deadlineAtMillis(), now + ttlMillis),
                        snapshot.deadlineAtMillis());
        return snapshot;
    }

    public synchronized Snapshot end(String id, String reason) {
        requireOpen();
        requireIdentity(id);
        if (snapshot.active()) terminal("ENDED", reason);
        return snapshot;
    }

    public synchronized boolean due() {
        return snapshot.active() && clock.getAsLong() >= snapshot.expiresAtMillis();
    }

    public synchronized Snapshot expire() {
        if (due()) terminal("EXPIRED", "task_expired");
        return snapshot;
    }

    public synchronized Snapshot interrupt() {
        if (snapshot.active()) terminal("INTERRUPTED", "human_manual_revocation");
        return snapshot;
    }

    public synchronized void requireActive(String id) {
        requireOpen();
        requireIdentity(id);
        if (!snapshot.active() || due())
            throw failure("AGENT_TASK_INACTIVE", "Inspect task state before starting new work");
    }

    public synchronized Snapshot snapshot() {
        return snapshot;
    }

    public synchronized boolean explicit() {
        return explicitLifecycle;
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        terminal("CLOSED", "runtime_close");
    }

    private void terminal(String status, String reason) {
        snapshot =
                new Snapshot(
                        snapshot.taskId(),
                        status,
                        reason,
                        snapshot.generation() + 1,
                        snapshot.expiresAtMillis(),
                        snapshot.deadlineAtMillis());
    }

    private void requireIdentity(String id) {
        if (id == null || id.isBlank() || !id.equals(snapshot.taskId()))
            throw failure("AGENT_TASK_MISMATCH", "Task identity belongs to another workflow");
    }

    private void requireOpen() {
        if (closed) throw failure("AGENT_TASK_CLOSED", "Runtime task lifecycle is closed");
    }

    private static void requireBudgets(long ttl, long budget) {
        if (ttl < 1000 || ttl > 60_000 || budget < 1000 || budget > 300_000)
            throw failure(
                    "INVALID_AGENT_TASK_BUDGET", "Task TTL is 1..60 s; total budget is 1..300 s");
    }

    private static AgentControlSession.ModeException failure(String code, String message) {
        return new AgentControlSession.ModeException(code, message);
    }
}
