package io.github.recrivenvi.minecraftprotocol.safety;

/** Small shared pixel geometry and one clock for presence, mode strength, text and pointer Fade. */
public final class ControlChrome {
    public static final long FADE_NANOS = 160_000_000L;
    public static final String READ_MESSAGE = "智能体正在读取您的实例";
    public static final String OPERATE_MESSAGE = "智能体正在操控您的实例";
    public static final String MESSAGE = "智能体已接管您的实例 · Esc 以退出";
    private long previousNanos, modeChangedAt;
    private float controlChromeAlpha, strength, pointerAlpha;
    private AgentControlSession.Mode mode = AgentControlSession.Mode.READ;
    private AgentControlSession.Mode previousMode = mode;
    public float update(boolean controlled, long nowNanos) {
        return update(controlled ? AgentControlSession.Mode.TAKEOVER : AgentControlSession.Mode.READ, controlled, false, nowNanos);
    }
    public float update(AgentControlSession.Mode next, boolean present, boolean gui, long nowNanos) {
        long elapsed = previousNanos == 0 ? 0 : Math.max(0, nowNanos - previousNanos);
        previousNanos = nowNanos;
        if (mode != next) { previousMode = mode; mode = next; modeChangedAt = nowNanos; }
        float change = Math.min(1F, (float) elapsed / FADE_NANOS);
        controlChromeAlpha = approach(controlChromeAlpha, present ? 1F : 0F, change);
        strength = approach(strength, switch (mode) { case READ -> 0.25F; case OPERATE -> 0.55F; case TAKEOVER -> 1F; }, change);
        pointerAlpha = approach(pointerAlpha, present && gui && mode == AgentControlSession.Mode.TAKEOVER ? 1F : 0F, change);
        return controlChromeAlpha;
    }
    private static float approach(float from, float to, float change) {
        return from < to ? Math.min(to, from + change) : Math.max(to, from - change);
    }
    public float alpha() { return controlChromeAlpha; }
    public float edgeAlpha() { return controlChromeAlpha * strength; }
    public float pointerAlpha() { return pointerAlpha * controlChromeAlpha; }
    public float textMix() { return modeChangedAt == 0 ? 1F : Math.min(1F, Math.max(0F, (float)(previousNanos - modeChangedAt) / FADE_NANOS)); }
    public String message() { return message(mode); }
    public String previousMessage() { return message(previousMode); }
    public static String message(AgentControlSession.Mode mode) {
        return switch (mode) { case READ -> READ_MESSAGE; case OPERATE -> OPERATE_MESSAGE; case TAKEOVER -> MESSAGE; };
    }
    @FunctionalInterface public interface Rectangles { void fill(int x0, int y0, int x1, int y1, int color); }
    public static int color(int alpha, int rgb, float fade) {
        return (Math.max(0, Math.min(255, Math.round(alpha * fade))) << 24) | (rgb & 0xFFFFFF);
    }
    public static void edges(Rectangles draw, int width, int height, float fade) {
        int depth = Math.max(1, Math.min(16, Math.min(width, height) / 8));
        for (int inset = 0; inset < depth; inset++) {
            float distance = depth == 1 ? 1F : 1F - (float) inset / (depth - 1);
            int color = color(Math.round(245 * distance * distance), 0x299BFF, fade);
            if ((color >>> 24) == 0) continue;
            draw.fill(inset, inset, width - inset, inset + 1, color);
            draw.fill(inset, height - inset - 1, width - inset, height - inset, color);
            draw.fill(inset, inset + 1, inset + 1, height - inset - 1, color);
            draw.fill(width - inset - 1, inset + 1, width - inset, height - inset - 1, color);
        }
    }
    public static void panel(Rectangles draw, int x, int y, int width, int height, float fade) {
        for (int spread = 5; spread > 0; spread--)
            draw.fill(x - spread, y - spread, x + width + spread, y + height + spread, color(8, 0x208DDD, fade));
        draw.fill(x + 2, y + 2, x + width + 2, y + height + 2, color(180, 0x071D35, fade));
        draw.fill(x, y, x + width, y + height, color(240, 0x065794, fade));
        draw.fill(x + 1, y + 1, x + width - 1, y + 2, color(245, 0x77CDFF, fade));
        draw.fill(x + 1, y + 2, x + 2, y + height - 1, color(235, 0x48AFF2, fade));
        draw.fill(x + 2, y + 2, x + width - 2, y + height - 2, color(240, 0x103452, fade));
    }
    /** Pixel cursor geometry, tip at the actual delivered GUI coordinate; not an OS cursor. */
    public static void pointer(Rectangles draw, int x, int y, float fade) {
        String[] pixels = {"X...........", "XX..........", "XOX.........", "XOOX........",
                "XOOOX.......", "XOOOOX......", "XOOOOOX.....", "XOOOOOOX....",
                "XOOOOOOOX...", "XOOOOXXXXX..", "XOOXOX......", "XOX.XOX.....",
                "XX..XOX.....", "X....XOX....", ".....XOX....", "......X....."};
        for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++) {
            char value = pixels[row].charAt(col);
            if (value == '.') continue;
            if (value == 'X') draw.fill(x + col - 1, y + row - 1, x + col + 2, y + row + 2, color(32, 0x289AFF, fade));
        }
        for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++) {
            char value = pixels[row].charAt(col);
            if (value != '.') draw.fill(x + col, y + row, x + col + 1, y + row + 1,
                    color(255, value == 'X' ? 0x167FC4 : 0xD9F5FF, fade));
        }
    }
}
