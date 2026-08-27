package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.HashSet;
import java.util.Set;

/** Detached logical coordinates and deterministic trajectory math; never touches a host cursor. */
public final class AgentPointer {
    public static final double RESPONSE_SCALER = 0.9;
    public static final double RESPONSE_MIN_SECONDS = 0.12;
    public static final double RESPONSE_MAX_SECONDS = 2.2;
    public static final double DAMPING_FRACTION = 0.9;
    public static final double SCOOT_DISTANCE = 196;
    public static final double SCOOT_RESPONSE_SECONDS = 0.24;
    public static final double SCOOT_DAMPING_FRACTION = 0.84;
    public static final double SETTLE_VELOCITY = 12;
    public static final float PRESSED_SCALE = 0.7F;

    public enum Plane {
        HIDDEN,
        GUI_ABSOLUTE,
        GAMEPLAY_RELATIVE
    }

    private double x, y;
    private double guiX, guiY, viewportWidth, viewportHeight;
    private boolean guiPositionKnown;
    private boolean initialized;
    private boolean visualPositionKnown;
    private Plane plane = Plane.HIDDEN;
    private long revision;
    private boolean gameCaptured;
    private boolean gameCursorVisible = true;
    private long gameCursorRevision;
    private final Set<Integer> visualButtons = new HashSet<>();
    private float pressScaleFrom = 1F, pressScaleTarget = 1F;
    private long pressChangedAt;

    public synchronized void plane(Plane next, double centerX, double centerY) {
        if (plane != next) {
            plane = next;
            releaseButtons();
            if (next == Plane.GUI_ABSOLUTE) {
                x = guiPositionKnown ? guiX : centerX;
                y = guiPositionKnown ? guiY : centerY;
                guiX = x;
                guiY = y;
                guiPositionKnown = true;
                initialized = true;
                visualPositionKnown = true;
            }
            revision++;
        }
    }

    public synchronized void move(double nextX, double nextY) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY))
            throw new IllegalArgumentException("Non-finite pointer coordinate");
        x = nextX;
        y = nextY;
        if (plane == Plane.GUI_ABSOLUTE) {
            guiX = x;
            guiY = y;
            guiPositionKnown = true;
        }
        initialized = true;
        visualPositionKnown = true;
        revision++;
    }

    public synchronized void reset() {
        initialized = false;
        plane = Plane.HIDDEN;
        releaseButtons();
        revision++;
    }

    /** Seeds logical ownership once; never imports the host cursor position. */
    public synchronized void beginGameControl(boolean captured) {
        gameCaptured = captured;
        gameCursorVisible = !captured;
        gameCursorRevision++;
    }

    /** Vanilla capture/release is a no-op when the logical state already matches. */
    public synchronized boolean gameCapture(boolean captured, double centerX, double centerY) {
        if (gameCaptured == captured) return false;
        return gameRequest(captured, !captured, centerX, centerY);
    }

    /** An explicit game positioning/mode request, not a native motion or host resync. */
    public synchronized boolean gameRequest(
            boolean captured, boolean visible, double nextX, double nextY) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY))
            throw new IllegalArgumentException("Non-finite game cursor coordinate");
        // GUI coordinates are continuous; subtracting a whole pixel clips valid edge fractions.
        if (viewportWidth > 0) nextX = Math.max(0, Math.min(Math.nextDown(viewportWidth), nextX));
        if (viewportHeight > 0) nextY = Math.max(0, Math.min(Math.nextDown(viewportHeight), nextY));
        if (gameCaptured == captured
                && gameCursorVisible == visible
                && guiPositionKnown
                && guiX == nextX
                && guiY == nextY
                && x == nextX
                && y == nextY) return false;
        gameCaptured = captured;
        gameCursorVisible = visible;
        guiX = nextX;
        guiY = nextY;
        guiPositionKnown = true;
        x = nextX;
        y = nextY;
        initialized = true;
        visualPositionKnown = true;
        gameCursorRevision++;
        revision++;
        return true;
    }

    public synchronized boolean gameCaptured() {
        return gameCaptured;
    }

    public synchronized boolean gameCursorVisible() {
        return gameCursorVisible;
    }

    public synchronized long gameCursorRevision() {
        return gameCursorRevision;
    }

    /**
     * A new Screen invalidates a gesture, not its cursor. Resize preserves normalized GUI position.
     */
    public synchronized void viewport(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid pointer viewport");
        if (guiPositionKnown) {
            if (viewportWidth > 0 && viewportHeight > 0) {
                guiX *= width / viewportWidth;
                guiY *= height / viewportHeight;
            }
            guiX = Math.max(0, Math.min(Math.nextDown(width), guiX));
            guiY = Math.max(0, Math.min(Math.nextDown(height), guiY));
            if (plane == Plane.GUI_ABSOLUTE) {
                x = guiX;
                y = guiY;
            }
        }
        viewportWidth = width;
        viewportHeight = height;
    }

    /** Visual feedback follows delivered buttons only; it never schedules or delays input. */
    public synchronized void button(int button, boolean pressed, long nowNanos) {
        if (plane != Plane.GUI_ABSOLUTE) {
            releaseButtons();
            return;
        }
        boolean changed = pressed ? visualButtons.add(button) : visualButtons.remove(button);
        float target = visualButtons.isEmpty() ? 1F : PRESSED_SCALE;
        if (changed && target != pressScaleTarget) {
            pressScaleFrom = pressScale(nowNanos);
            pressScaleTarget = target;
            pressChangedAt = nowNanos;
        }
    }

    public synchronized float pressScale(long nowNanos) {
        long duration = pressScaleTarget < pressScaleFrom ? 60_000_000L : 140_000_000L;
        double progress = Math.max(0, nowNanos - pressChangedAt) / (double) duration;
        return (float) interpolate(pressScaleFrom, pressScaleTarget, progress);
    }

    public synchronized void releaseButtons() {
        visualButtons.clear();
        pressScaleFrom = pressScaleTarget = 1F;
        pressChangedAt = 0;
    }

    public synchronized double x() {
        return x;
    }

    public synchronized double y() {
        return y;
    }

    public synchronized boolean initialized() {
        return initialized;
    }

    /** Retains only the last drawn hotspot for fade-out; it grants no input ownership. */
    public synchronized boolean hasVisualPosition() {
        return visualPositionKnown;
    }

    public synchronized Plane plane() {
        return plane;
    }

    public synchronized long revision() {
        return revision;
    }

    /** Continuous GUI coordinates: odd-sized targets retain their half-pixel center. */
    public static double center(double origin, double extent) {
        if (!Double.isFinite(origin)
                || !Double.isFinite(extent)
                || extent <= 0
                || !Double.isFinite(origin + extent))
            throw new IllegalArgumentException("Invalid interaction bounds");
        return origin + extent / 2.0;
    }

    public static double ease(double progress) {
        double t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }

    public static double interpolate(double from, double to, double progress) {
        return from + (to - from) * ease(progress);
    }

    /** Response is the natural period, not the duration of a hand-authored easing curve. */
    public static double responseSeconds(double distance) {
        requireDistance(distance);
        if (distance <= SCOOT_DISTANCE) return SCOOT_RESPONSE_SECONDS;
        // GUI-distance normalization is our adaptation, not an inferred native path algorithm.
        return Math.max(
                RESPONSE_MIN_SECONDS,
                Math.min(RESPONSE_MAX_SECONDS, RESPONSE_SCALER * distance / 1000));
    }

    public static double dampingFraction(double distance) {
        requireDistance(distance);
        return distance <= SCOOT_DISTANCE ? SCOOT_DAMPING_FRACTION : DAMPING_FRACTION;
    }

    private static void requireDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("Invalid pointer distance");
    }

    /** Exact underdamped step response. Sampling frequency does not alter the spring. */
    public static double springProgress(double seconds, double response, double damping) {
        if (!Double.isFinite(seconds)
                || !Double.isFinite(response)
                || !Double.isFinite(damping)
                || response <= 0
                || damping <= 0
                || damping >= 1) throw new IllegalArgumentException("Invalid spring parameters");
        if (seconds <= 0) return 0;
        double omega = 2 * Math.PI / response;
        double root = Math.sqrt(1 - damping * damping);
        double phase = omega * root * seconds;
        return 1
                - Math.exp(-damping * omega * seconds)
                        * (Math.cos(phase) + damping / root * Math.sin(phase));
    }

    /** Conservative error/velocity envelopes bound the gesture before the exact final landing. */
    public static long settleMillis(double distance) {
        requireDistance(distance);
        if (distance == 0) return 0;
        double response = responseSeconds(distance), damping = dampingFraction(distance);
        double omega = 2 * Math.PI / response, root = Math.sqrt(1 - damping * damping);
        double tolerance = Math.min(0.005, 3.157 / distance);
        double positionTime = -Math.log(tolerance * root) / (damping * omega);
        double velocityTime =
                Math.max(0, Math.log(distance * omega / (root * SETTLE_VELOCITY)))
                        / (damping * omega);
        return (long) Math.ceil(1000 * Math.max(positionTime, velocityTime));
    }

    /** Free motion may overshoot; the click remains at the exact original double coordinate. */
    public static double approach(double from, double to, double progress, double distance) {
        requireDistance(distance);
        if (distance == 0 || progress >= 1) return to;
        double t = Math.max(0, Math.min(1, progress));
        double seconds = t * settleMillis(distance) / 1000.0;
        return from
                + (to - from)
                        * springProgress(
                                seconds, responseSeconds(distance), dampingFraction(distance));
    }
}
