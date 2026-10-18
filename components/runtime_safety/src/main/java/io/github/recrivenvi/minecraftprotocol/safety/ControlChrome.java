package io.github.recrivenvi.minecraftprotocol.safety;

/** Small shared pixel geometry and one clock for presence, mode strength, text and pointer Fade. */
public final class ControlChrome {
    private static final AgentControlSession.Mode[] MODES = AgentControlSession.Mode.values();
    public static final int ACCENT_RGB = 0x339CFF;
    public static final long FADE_NANOS = 500_000_000L;
    public static final long POINTER_FADE_NANOS = 440_000_000L;
    public static final long BREATH_LEG_NANOS = 1_500_000_000L;
    public static final int POINTER_DIAMETER = 24;
    public static final String READ_MESSAGE = "智能体正在读取您的实例";
    public static final String OPERATE_MESSAGE = "智能体正在操控您的实例";
    public static final String MESSAGE = "智能体已接管您的实例 · Esc 以退出";
    private long previousNanos, modeChangedAt, breathStartedAt;
    private boolean wasPresent;
    private final Fade presence = new Fade(0, FADE_NANOS);
    private final Fade strength = new Fade(0.25F, FADE_NANOS);
    private final Fade pointer = new Fade(0, POINTER_FADE_NANOS);
    private AgentControlSession.Mode mode = AgentControlSession.Mode.READ;
    private final float[] textFrom = {1F, 0F, 0F};
    private final float[] textWeights = {1F, 0F, 0F};
    private boolean textTransition;

    public float update(boolean controlled, long nowNanos) {
        return update(
                controlled ? AgentControlSession.Mode.TAKEOVER : AgentControlSession.Mode.READ,
                controlled,
                false,
                nowNanos);
    }

    public float update(
            AgentControlSession.Mode next, boolean present, boolean gui, long nowNanos) {
        previousNanos = nowNanos;
        if (present && !wasPresent) breathStartedAt = nowNanos;
        wasPresent = present;
        sampleText(nowNanos);
        if (mode != next) {
            System.arraycopy(textWeights, 0, textFrom, 0, textWeights.length);
            mode = next;
            modeChangedAt = nowNanos;
            textTransition = true;
        }
        presence.update(present ? 1F : 0F, nowNanos);
        strength.update(
                switch (mode) {
                    case READ -> 0.25F;
                    case OPERATE -> 0.55F;
                    case TAKEOVER -> 1F;
                },
                nowNanos);
        pointer.update(
                present && gui && mode == AgentControlSession.Mode.TAKEOVER ? 1F : 0F, nowNanos);
        return alpha();
    }

    private void sampleText(long nowNanos) {
        if (!textTransition) return;
        float mix = transitionMix(nowNanos);
        for (var candidate : MODES) {
            int index = candidate.ordinal();
            float target = candidate == mode ? 1F : 0F;
            textWeights[index] = textFrom[index] + (target - textFrom[index]) * mix;
        }
    }

    private float transitionMix(long nowNanos) {
        return !textTransition
                ? 1F
                : bezier(
                        Math.max(0, nowNanos - modeChangedAt) / (double) FADE_NANOS, 0, 0, 0.58, 1);
    }

    public float textWeight(AgentControlSession.Mode candidate) {
        return textWeights[candidate.ordinal()];
    }

    /** Measure the same continuous mixture that will actually be drawn in this frame. */
    public float textWidth(java.util.function.ToIntFunction<String> measure) {
        float width = 0;
        for (var candidate : MODES)
            width += measure.applyAsInt(message(candidate)) * textWeight(candidate);
        return width;
    }

    public interface TextRenderer {
        void enableScissor(int left, int top, int right, int bottom);

        void disableScissor();

        void text(String message, int x, int y, int color);
    }

    /** Native clipping follows the current panel, never the destination mode's full width. */
    public void text(TextRenderer renderer, int x, int y, int width, int height, float fade) {
        int left = x + 10, top = y + 2, right = x + width - 10, bottom = y + height - 2;
        if (right <= left || bottom <= top || !textVisible(fade)) return;
        renderer.enableScissor(left, top, right, bottom);
        try {
            for (var candidate : MODES) {
                float opacity = fade * textWeight(candidate);
                if (textVisible(opacity))
                    renderer.text(message(candidate), left, y + 8, color(255, 0xFFFFFF, opacity));
            }
        } finally {
            renderer.disableScissor();
        }
    }

    /** Solve x(t) before evaluating y(t); time itself is not the Bezier parameter. */
    public static float bezier(double progress, double x1, double y1, double x2, double y2) {
        if (progress <= 0) return 0;
        if (progress >= 1) return 1;
        double low = 0, high = 1;
        for (int i = 0; i < 32; i++) {
            double t = (low + high) / 2;
            if (cubic(t, x1, x2) < progress) low = t;
            else high = t;
        }
        return (float) cubic((low + high) / 2, y1, y2);
    }

    private static double cubic(double t, double first, double second) {
        double inverse = 1 - t;
        return 3 * inverse * inverse * t * first + 3 * inverse * t * t * second + t * t * t;
    }

    private static final class Fade {
        private final long duration;
        private long changedAt;
        private float from, target, value;

        private Fade(float initial, long duration) {
            from = target = value = initial;
            this.duration = duration;
        }

        private void update(float next, long now) {
            value =
                    from
                            + (target - from)
                                    * bezier(
                                            Math.max(0, now - changedAt) / (double) duration,
                                            0,
                                            0,
                                            0.58,
                                            1);
            if (next != target) {
                from = value;
                target = next;
                changedAt = now;
            }
        }
    }

    public float alpha() {
        return presence.value;
    }

    public float edgeAlpha() {
        return alpha() * strength.value * (0.76F + 0.24F * breath());
    }

    public float edgeWidth() {
        return 0.88F + 0.12F * breath();
    }

    private float breath() {
        long elapsed = Math.max(0, previousNanos - breathStartedAt);
        double leg = (elapsed % (2 * BREATH_LEG_NANOS)) / (double) BREATH_LEG_NANOS;
        return bezier(leg <= 1 ? leg : 2 - leg, 0.42, 0, 0.58, 1);
    }

    public float pointerAlpha() {
        return pointer.value;
    }

    public String message() {
        return message(mode);
    }

    public static String message(AgentControlSession.Mode mode) {
        return switch (mode) {
            case READ -> READ_MESSAGE;
            case OPERATE -> OPERATE_MESSAGE;
            case TAKEOVER -> MESSAGE;
        };
    }

    @FunctionalInterface
    public interface Rectangles {
        void fill(int x0, int y0, int x1, int y1, int color);
    }

    /** Target-native pose stack; keep the fractional origin until the GPU float conversion. */
    public interface PointerTransform {
        void push(double x, double y);

        void pop();
    }

    public static int color(int alpha, int rgb, float fade) {
        return (Math.max(0, Math.min(255, Math.round(alpha * fade))) << 24) | (rgb & 0xFFFFFF);
    }

    /** Vanilla fonts may normalize alpha 0..3 to opaque; never submit those Fade endpoints. */
    public static boolean textVisible(float fade) {
        return Float.isFinite(fade) && Math.round(255 * fade) >= 4;
    }

    public static void edges(Rectangles draw, int width, int height, float fade) {
        edges(draw, width, height, fade, 1F, 6);
    }

    /** Convert the 96 display-pixel band into GUI units without changing input coordinates. */
    public static void edges(
            Rectangles draw, int width, int height, float fade, float band, double guiScale) {
        if (width <= 0
                || height <= 0
                || !Double.isFinite(guiScale)
                || guiScale <= 0
                || !Float.isFinite(fade)
                || fade <= 0
                || !Float.isFinite(band)
                || band <= 0) return;
        if (width == 1 || height == 1) {
            draw.fill(0, 0, width, height, color(245, ACCENT_RGB, fade));
            return;
        }
        int depth =
                Math.max(
                        1,
                        Math.min(
                                (int) Math.round(96 * band / guiScale),
                                Math.min(width, height) / 2));
        float[] profile = new float[depth];
        for (int inset = 0; inset < depth; inset++) {
            float distance = depth == 1 ? 1F : 1F - (float) inset / (depth - 1);
            profile[inset] = distance * distance;
            int color = color(Math.round(245 * profile[inset]), ACCENT_RGB, fade);
            if ((color >>> 24) == 0) continue;
            if (width > depth * 2) {
                draw.fill(depth, inset, width - depth, inset + 1, color);
                draw.fill(depth, height - inset - 1, width - depth, height - inset, color);
            }
            if (height > depth * 2) {
                draw.fill(inset, depth, inset + 1, height - depth, color);
                draw.fill(width - inset - 1, depth, width - inset, height - depth, color);
            }
        }
        // Smooth union of the two edge profiles. A minimum-distance square ring has a
        // diagonal derivative seam; overlapping edge draws instead accumulate corner alpha.
        // Submit each corner pixel once and merge equal-color horizontal runs.
        for (int y = 0; y < depth; y++) {
            int run = 0, previous = cornerColor(profile[0], profile[y], fade);
            for (int x = 1; x <= depth; x++) {
                int next = x == depth ? 0 : cornerColor(profile[x], profile[y], fade);
                if (x < depth && next == previous) continue;
                if ((previous >>> 24) != 0) {
                    draw.fill(run, y, x, y + 1, previous);
                    draw.fill(width - x, y, width - run, y + 1, previous);
                    draw.fill(run, height - y - 1, x, height - y, previous);
                    draw.fill(width - x, height - y - 1, width - run, height - y, previous);
                }
                run = x;
                previous = next;
            }
        }
    }

    private static int cornerColor(float x, float y, float fade) {
        return color(Math.round(245 * (x + y - x * y)), ACCENT_RGB, fade);
    }

    public static void panel(Rectangles draw, int x, int y, int width, int height, float fade) {
        for (int spread = 5; spread > 0; spread--)
            draw.fill(
                    x - spread,
                    y - spread,
                    x + width + spread,
                    y + height + spread,
                    color(8, ACCENT_RGB, fade));
        draw.fill(x + 2, y + 2, x + width + 2, y + height + 2, color(180, 0x071D35, fade));
        draw.fill(x, y, x + width, y + height, color(240, ACCENT_RGB, fade));
        draw.fill(x + 1, y + 1, x + width - 1, y + 2, color(245, ACCENT_RGB, fade));
        draw.fill(x + 1, y + 2, x + 2, y + height - 1, color(235, ACCENT_RGB, fade));
        draw.fill(x + 2, y + 2, x + width - 2, y + height - 2, color(240, 0x103452, fade));
    }

    /**
     * Center the orb on the delivered input in the renderer's projection. Minecraft can ceil its
     * input GUI size but project using framebuffer size / GUI scale. Correct only that drawing
     * transform, never the input coordinate, and keep local orb pixels symmetric around (0, 0).
     */
    public static void pointer(
            Rectangles draw,
            PointerTransform transform,
            double x,
            double y,
            int guiWidth,
            int guiHeight,
            double projectionWidth,
            double projectionHeight,
            float fade,
            float scale) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || guiWidth <= 0
                || guiHeight <= 0
                || !Double.isFinite(projectionWidth)
                || !Double.isFinite(projectionHeight)
                || projectionWidth <= 0
                || projectionHeight <= 0) return;
        transform.push(x * projectionWidth / guiWidth, y * projectionHeight / guiHeight);
        try {
            pointer(draw, 0, 0, fade, scale);
        } finally {
            transform.pop();
        }
    }

    /** Local even-sized orb geometry; its absolute geometric center is the translated origin. */
    public static void pointer(Rectangles draw, int x, int y, float fade) {
        pointer(draw, x, y, fade, 1F);
    }

    public static void pointer(Rectangles draw, int x, int y, float fade, float scale) {
        int diameter = 2 * Math.max(1, Math.round(POINTER_DIAMETER * scale / 2));
        int radius = diameter / 2;
        double coreRadius = diameter * 0.1875;
        for (int dy = -radius; dy < radius; dy++) {
            for (int dx = -radius; dx < radius; dx++) {
                // Half-pixel centers keep the even-sized orb symmetric around its input hotspot.
                double distance = Math.hypot(dx + 0.5, dy + 0.5);
                if (distance >= radius) continue;
                double falloff = 1 - Math.max(0, distance - coreRadius) / (radius - coreRadius);
                int alpha = (int) Math.round(245 * falloff * falloff);
                double core = Math.max(0, 1 - distance / coreRadius);
                int red = (int) Math.round(0x33 + (0xD9 - 0x33) * core);
                int green = (int) Math.round(0x9C + (0xF5 - 0x9C) * core);
                int blue = 0xFF;
                int color = color(alpha, (red << 16) | (green << 8) | blue, fade);
                if ((color >>> 24) != 0) draw.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
            }
        }
    }
}
