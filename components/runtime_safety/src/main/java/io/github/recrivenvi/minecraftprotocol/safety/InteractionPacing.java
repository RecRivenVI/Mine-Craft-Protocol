package io.github.recrivenvi.minecraftprotocol.safety;

/** Deterministic viewing time for GUI actions; callers schedule waits, never block the owner. */
public final class InteractionPacing {
    public static final long POINTER_FRAME_MILLIS = 16;
    public static final long HOVER_MILLIS = 180;
    public static final long ACTION_GAP_MILLIS = 280;
    public static final long SCREEN_DWELL_MILLIS = 700;
    private String screen = "";
    private long screenReadyNanos, actionReadyNanos;

    public static long movementMillis(double distance) {
        return AgentPointer.settleMillis(distance);
    }

    public static int movementSteps(long durationMillis) {
        return (int)
                Math.max(
                        1,
                        Math.min(
                                240,
                                (durationMillis + POINTER_FRAME_MILLIS - 1)
                                        / POINTER_FRAME_MILLIS));
    }

    public synchronized long remainingMillis(boolean gui, String identity, long nowNanos) {
        observe(gui, identity, nowNanos);
        if (!gui) return 0;
        long remaining = Math.max(screenReadyNanos, actionReadyNanos) - nowNanos;
        return Math.max(0, (remaining + 999_999) / 1_000_000);
    }

    public synchronized void completed(boolean gui, String identity, long nowNanos, boolean pause) {
        observe(gui, identity, nowNanos);
        if (gui && pause) actionReadyNanos = nowNanos + ACTION_GAP_MILLIS * 1_000_000;
    }

    private void observe(boolean gui, String identity, long nowNanos) {
        if (!gui) {
            screen = "";
            screenReadyNanos = 0;
            actionReadyNanos = 0;
        } else if (!identity.equals(screen)) {
            screen = identity;
            screenReadyNanos = nowNanos + SCREEN_DWELL_MILLIS * 1_000_000;
        }
    }
}
