package io.github.recrivenvi.minecraftprotocol.safety;

/** Detached text from a completed native UI render callback, not a framebuffer assertion. */
public record UiRenderedText(
        String label, String phase, long capturedAtNanos, int width, int height) {
    public interface Source {
        UiRenderedText minecraftProtocolProbe$renderedText();
    }

    public UiRenderedText {
        label = bounded(label);
    }

    public static String bounded(String label) {
        if (label.length() > 2048) {
            int end = Character.isHighSurrogate(label.charAt(2047)) ? 2047 : 2048;
            label = label.substring(0, end);
        }
        return label;
    }

    public boolean current(long nowNanos, int currentWidth, int currentHeight) {
        long age = nowNanos - capturedAtNanos;
        return age >= 0
                && age <= 1_000_000_000L
                && width == currentWidth
                && height == currentHeight;
    }
}
