package io.github.recrivenvi.minecraftprotocol.safety;

/** Small renderer-independent geometry/fade policy; it owns no control authority. */
public final class ControlChrome {
    public static final String MESSAGE = "智能体正在控制您的实例 · Esc 以退出";
    public static final long FADE_NANOS = 160_000_000L;
    private long previousNanos;
    private float controlChromeAlpha;

    public float update(boolean controlled, long nowNanos) {
        long elapsed = previousNanos == 0L ? 0L : Math.max(0L, nowNanos - previousNanos);
        previousNanos = nowNanos;
        float change = (float) elapsed / FADE_NANOS;
        controlChromeAlpha = controlled ? Math.min(1F, controlChromeAlpha + change)
                : Math.max(0F, controlChromeAlpha - change);
        return controlChromeAlpha;
    }

    public float alpha() { return controlChromeAlpha; }

    @FunctionalInterface public interface Rectangles { void fill(int x0, int y0, int x1, int y1, int color); }

    public static int color(int alpha, int rgb, float fade) {
        return (Math.max(0, Math.min(255, Math.round(alpha * fade))) << 24) | (rgb & 0xFFFFFF);
    }

    public static void edges(Rectangles draw, int width, int height, float fade) {
        int depth = Math.max(1, Math.min(16, Math.min(width, height) / 8));
        for (int inset = 0; inset < depth; inset++) {
            float distance = depth == 1 ? 1F : 1F - (float) inset / (depth - 1);
            int color = color(Math.round(235 * distance * distance), 0x299BFF, fade);
            if ((color >>> 24) == 0) continue;
            draw.fill(inset, inset, width - inset, inset + 1, color);
            draw.fill(inset, height - inset - 1, width - inset, height - inset, color);
            draw.fill(inset, inset + 1, inset + 1, height - inset - 1, color);
            draw.fill(width - inset - 1, inset + 1, width - inset, height - inset - 1, color);
        }
    }

    public static void pill(Rectangles draw, int x, int y, int width, int height, float fade) {
        for (int spread = 8; spread > 0; spread--) {
            rounded(draw, x - spread, y - spread, width + 2 * spread, height + 2 * spread,
                    7 + spread, color(8 + (8 - spread) * 2, 0x32AEFF, fade));
        }
        rounded(draw, x, y, width, height, 7, color(238, 0x0878C4, fade));
        rounded(draw, x + 1, y + 1, width - 2, height - 2, 6, color(245, 0x096EAF, fade));
    }

    private static void rounded(Rectangles draw, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            double edge = Math.max(0, r - Math.min(row + 0.5, height - row - 0.5));
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - edge * edge)));
            draw.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }
}
