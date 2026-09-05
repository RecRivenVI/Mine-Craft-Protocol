package io.github.recrivenvi.minecraftprotocol.safety;

/** Minecraft callback policy only; never hooks or consumes OS-wide events. */
public final class ExclusiveInputPolicy {
    public enum Decision { PASS, SUPPRESS, MANUAL_REVOKE }
    private ExclusiveInputPolicy() { }
    public static Decision decide(boolean takeover, boolean admittedAgent, AgentInputContext.Kind kind, int key, int action) {
        if (!takeover || admittedAgent) return Decision.PASS;
        return kind == AgentInputContext.Kind.KEY && key == 256 && action == 1
                ? Decision.MANUAL_REVOKE : Decision.SUPPRESS;
    }
}
