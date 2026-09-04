package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runtime-neutral state machine for the single Agent control presence.
 * The Minecraft targets own rendering and native input hooks; this class owns
 * the state transitions so those surfaces cannot drift apart.
 */
public final class AgentControlSession {
    public enum State {
        IDLE,
        AGENT_CONTROLLED,
        MANUALLY_REVOKED
    }

    public record Snapshot(
            State state,
            boolean reconsentRequired,
            String message,
            String reason,
            long transitionSequence) {
        public boolean agentControlled() {
            return this.state == State.AGENT_CONTROLLED;
        }

        public boolean manuallyRevoked() {
            return this.state == State.MANUALLY_REVOKED;
        }
    }

    private final Consumer<Snapshot> listener;
    private State state = State.IDLE;
    private String reason = "initial";
    private long transitionSequence;

    public AgentControlSession() {
        this(snapshot -> {
        });
    }

    public AgentControlSession(Consumer<Snapshot> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public synchronized Snapshot acquire() {
        return transition(State.AGENT_CONTROLLED, "agent_control_acquired");
    }

    public synchronized Snapshot release(String reason) {
        return transition(State.IDLE, reason == null || reason.isBlank() ? "agent_control_released" : reason);
    }

    public synchronized Snapshot manuallyRevoke() {
        return transition(State.MANUALLY_REVOKED, "human_manual_revocation");
    }

    public synchronized Snapshot snapshot() {
        boolean revoked = this.state == State.MANUALLY_REVOKED;
        return new Snapshot(this.state, revoked, revoked ? "用户手动结束控制" : "", this.reason, this.transitionSequence);
    }

    private Snapshot transition(State next, String nextReason) {
        this.state = Objects.requireNonNull(next, "next");
        this.reason = nextReason;
        this.transitionSequence++;
        Snapshot snapshot = snapshot();
        this.listener.accept(snapshot);
        return snapshot;
    }
}
