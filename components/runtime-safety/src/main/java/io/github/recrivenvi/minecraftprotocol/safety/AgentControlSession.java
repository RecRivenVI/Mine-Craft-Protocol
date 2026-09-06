package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Intent and human override. Authentication, Lease and Debug Arm remain external. */
public final class AgentControlSession {
    public enum Mode { READ, OPERATE, TAKEOVER }
    /** Compatibility presentation state, not an intent/permission model. */
    public enum State { IDLE, AGENT_CONTROLLED, MANUALLY_REVOKED }

    public record Snapshot(State state, boolean reconsentRequired, String message,
            String reason, long transitionSequence, Mode mode, String controlSessionId) {
        public boolean agentControlled() { return mode == Mode.TAKEOVER; }
        public boolean manuallyRevoked() { return reconsentRequired; }
    }

    public static final class ModeException extends RuntimeException {
        private final String code;
        public ModeException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    private final Consumer<Snapshot> listener;
    private final String sessionId = UUID.randomUUID().toString();
    private final Set<OperateWork> operateWork = new HashSet<>();
    private Mode mode = Mode.READ;
    private boolean manuallyRevoked;
    private boolean closed;
    private String reason = "initial";
    private long generation;

    public AgentControlSession() { this(snapshot -> { }); }
    public AgentControlSession(Consumer<Snapshot> listener) { this.listener = Objects.requireNonNull(listener); }

    /** Called only after ProtocolState has acquired the existing input Lease. */
    public synchronized Snapshot acquire() {
        requireOpen();
        requireNoOperateWork();
        String cause = manuallyRevoked ? "agent_control_reacquired" : "agent_control_acquired";
        manuallyRevoked = false;
        return transition(Mode.TAKEOVER, cause);
    }

    public synchronized Snapshot release(String cause) {
        return transition(Mode.READ, cause == null || cause.isBlank() ? "agent_control_released" : cause);
    }

    public synchronized Snapshot manuallyRevoke() {
        if (mode != Mode.TAKEOVER) return snapshot();
        manuallyRevoked = true;
        return transition(Mode.READ, "human_manual_revocation");
    }

    public synchronized Snapshot select(Mode next, String cause) {
        requireOpen();
        if (next == Mode.TAKEOVER) throw new ModeException(
                "TAKEOVER_REQUIRES_LEASE_ACQUIRE", "Use the explicit Control Lease acquire path");
        if (mode == Mode.TAKEOVER) throw new ModeException(
                "CONTROL_INPUT_CLEANUP_PENDING", "Release and drain input before selecting another intent");
        if (mode == next) return snapshot();
        requireNoOperateWork();
        return transition(next, cause);
    }

    public synchronized void requireVersion(String expectedSession, long expectedGeneration) {
        requireOpen();
        if (!sessionId.equals(expectedSession)) throw new ModeException(
                "STALE_CONTROL_SESSION", "Mode version belongs to another Runtime control session");
        if (generation != expectedGeneration) throw new ModeException(
                "STALE_MODE_REVISION", "Mode generation changed; inspect the current mode before retrying");
    }

    public synchronized void requireNoOperateWork() {
        if (!operateWork.isEmpty()) throw new ModeException(
                "MODE_OPERATION_IN_PROGRESS", "Finish or cancel active OPERATE work before changing intent");
    }

    /** Owner-thread input and intent exit are linearized; body must not acquire ProtocolState. */
    public synchronized <T> T withTakeover(Snapshot accepted, Supplier<T> body) {
        requireOpen();
        if (mode != Mode.TAKEOVER || accepted.mode() != Mode.TAKEOVER
                || generation != accepted.transitionSequence() || !sessionId.equals(accepted.controlSessionId())) {
            throw new ModeException(manuallyRevoked ? "USER_MANUALLY_ENDED_CONTROL" : "TAKEOVER_REQUIRED",
                    manuallyRevoked ? "用户手动结束控制" : "TAKEOVER ended before owner-thread input dispatch");
        }
        return body.get();
    }

    public synchronized OperateWork beginOperate() {
        requireOpen();
        if (mode != Mode.OPERATE) throw new ModeException("OPERATE_REQUIRED", "Explicit OPERATE intent is required");
        if (operateWork.size() >= 16) throw new ModeException("TOO_MANY_OPERATE_REQUESTS", "At most 16 OPERATE requests may be retained");
        OperateWork work = new OperateWork(generation);
        operateWork.add(work);
        return work;
    }

    public synchronized int activeOperateRequests() { return operateWork.size(); }

    /** Cancelled queued callbacks cannot start; active owner work keeps transitions blocked. */
    public final class OperateWork implements AutoCloseable {
        private final long acceptedGeneration;
        private boolean retired;
        private int entered;
        private OperateWork(long acceptedGeneration) { this.acceptedGeneration = acceptedGeneration; }

        public Guard enter() {
            synchronized (AgentControlSession.this) {
                requireOpen();
                if (retired || mode != Mode.OPERATE || generation != acceptedGeneration) throw new ModeException(
                        "STALE_MODE_REVISION", "OPERATE work was cancelled or belongs to an earlier intent");
                entered++;
                return new Guard(this);
            }
        }

        public <T> T call(Supplier<T> body) {
            try (Guard ignored = enter()) { return body.get(); }
        }

        public boolean isCancelled() {
            synchronized (AgentControlSession.this) { return retired || closed || generation != acceptedGeneration; }
        }

        @Override public void close() {
            synchronized (AgentControlSession.this) {
                retired = true;
                if (entered == 0) operateWork.remove(this);
            }
        }
    }

    public final class Guard implements AutoCloseable {
        private OperateWork work;
        private Guard(OperateWork work) { this.work = work; }
        @Override public void close() {
            synchronized (AgentControlSession.this) {
                if (work == null) return;
                work.entered--;
                if (work.retired && work.entered == 0) operateWork.remove(work);
                work = null;
            }
        }
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (OperateWork work : Set.copyOf(operateWork)) work.close();
        transition(Mode.READ, "transport_close");
    }

    public synchronized boolean hasOperateWork() { return !this.operateWork.isEmpty(); }

    public synchronized Snapshot snapshot() {
        State state = mode == Mode.TAKEOVER ? State.AGENT_CONTROLLED
                : manuallyRevoked ? State.MANUALLY_REVOKED : State.IDLE;
        return new Snapshot(state, manuallyRevoked, manuallyRevoked ? "用户手动结束控制" : "",
                reason, generation, mode, sessionId);
    }

    private void requireOpen() {
        if (closed) throw new ModeException("CONTROL_SESSION_CLOSED", "Runtime control session is closed");
    }

    private Snapshot transition(Mode next, String cause) {
        if (mode == next && reason.equals(cause)) return snapshot();
        mode = next;
        reason = cause;
        generation++;
        Snapshot result = snapshot();
        listener.accept(result);
        return result;
    }
}
