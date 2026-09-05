package io.github.recrivenvi.minecraftprotocol.safety;

/** Detached logical coordinates and deterministic trajectory math; never touches a host cursor. */
public final class AgentPointer {
    public enum Plane { HIDDEN, GUI_ABSOLUTE, GAMEPLAY_RELATIVE }
    private double x, y;
    private boolean initialized;
    private Plane plane = Plane.HIDDEN;
    private long revision;
    public synchronized void plane(Plane next, double centerX, double centerY) {
        if (plane != next) {
            plane = next;
            if (next == Plane.GUI_ABSOLUTE) { x = centerX; y = centerY; initialized = true; }
            revision++;
        }
    }
    public synchronized void move(double nextX, double nextY) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY)) throw new IllegalArgumentException("Non-finite pointer coordinate");
        x = nextX; y = nextY; initialized = true; revision++;
    }
    public synchronized void reset() { initialized = false; plane = Plane.HIDDEN; revision++; }
    public synchronized double x() { return x; }
    public synchronized double y() { return y; }
    public synchronized boolean initialized() { return initialized; }
    public synchronized Plane plane() { return plane; }
    public synchronized long revision() { return revision; }
    public static double ease(double progress) {
        double t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }
    public static double interpolate(double from, double to, double progress) {
        return from + (to - from) * ease(progress);
    }
}
