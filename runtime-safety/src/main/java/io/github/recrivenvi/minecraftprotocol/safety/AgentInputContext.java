package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.function.Supplier;

/** Marks synchronous dispatch performed by the Agent, not by a human device. */
public final class AgentInputContext {
    private static final ThreadLocal<Integer> ROUTED_DEPTH = ThreadLocal.withInitial(() -> 0);

    private AgentInputContext() {
    }

    public static boolean isAgentRouted() {
        return ROUTED_DEPTH.get() > 0;
    }

    public static <T> T routed(Supplier<T> action) {
        int previous = ROUTED_DEPTH.get();
        ROUTED_DEPTH.set(previous + 1);
        try {
            return action.get();
        } finally {
            if (previous == 0) ROUTED_DEPTH.remove();
            else ROUTED_DEPTH.set(previous);
        }
    }

    public static void routed(Runnable action) {
        routed(() -> {
            action.run();
            return null;
        });
    }
}
