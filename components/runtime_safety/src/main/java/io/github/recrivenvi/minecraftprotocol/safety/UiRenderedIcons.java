package io.github.recrivenvi.minecraftprotocol.safety;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Bounded observations of completed native icon draws; never predicts a renderer's alpha. */
public final class UiRenderedIcons {
    public interface Source {
        UiRenderedIcons minecraftProtocolProbe$effectIcons();
    }

    public record Icon(String sprite, int x, int y, int width, int height, float opacity) {}

    private final List<Icon> icons = new ArrayList<>();
    private WeakReference<Object> owner = new WeakReference<>(null);
    private WeakReference<Object> screen = new WeakReference<>(null);
    private boolean screenPresent;
    private int width;
    private int height;
    private long capturedAt;
    private boolean complete;

    public void begin(
            Object currentOwner, Object currentScreen, int currentWidth, int currentHeight) {
        icons.clear();
        complete = false;
        owner = new WeakReference<>(currentOwner);
        screen = new WeakReference<>(currentScreen);
        screenPresent = currentScreen != null;
        width = currentWidth;
        height = currentHeight;
    }

    public void observe(String sprite, int x, int y, int iconWidth, int iconHeight, float opacity) {
        if (complete || icons.size() >= 64 || sprite == null || !Float.isFinite(opacity)) return;
        if (iconWidth <= 0 || iconHeight <= 0) return;
        icons.add(new Icon(sprite, x, y, iconWidth, iconHeight, Math.max(0, Math.min(1, opacity))));
    }

    public void finish(long nowNanos) {
        capturedAt = nowNanos;
        complete = true;
    }

    public Icon sample(
            String sprite,
            int x,
            int y,
            Object currentOwner,
            Object currentScreen,
            int currentWidth,
            int currentHeight,
            long nowNanos) {
        long age = nowNanos - capturedAt;
        if (!complete
                || currentOwner == null
                || owner.get() != currentOwner
                || screenPresent != (currentScreen != null)
                || screen.get() != currentScreen
                || width <= 0
                || height <= 0
                || width != currentWidth
                || height != currentHeight
                || age < 0
                || age > 250_000_000L) return null;
        return icons.stream()
                .filter(icon -> icon.sprite().equals(sprite) && icon.x() == x && icon.y() == y)
                .findFirst()
                .orElse(null);
    }
}
