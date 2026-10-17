package io.github.recrivenvi.minecraftprotocol.safety;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Fixed-family HUD draw observations, not player statistics or general render inspection. */
public final class UiHudIcons {
    public interface Source {
        UiHudIcons minecraftProtocolProbe$hudIcons();
    }

    public enum Kind {
        HEALTH,
        ARMOR,
        FOOD,
        AIR,
        MOUNT_HEALTH
    }

    public record Icon(
            Kind kind, String resourceId, String label, int x, int y, int width, int height) {}

    public record Sample(String status, List<Icon> icons) {}

    private static final int MAX_ICONS = 256;
    private final List<Icon> pending = new ArrayList<>();
    private WeakReference<Object> world = new WeakReference<>(null);
    private WeakReference<Object> owner = new WeakReference<>(null);
    private WeakReference<Object> screen = new WeakReference<>(null);
    private boolean screenPresent;
    private int width;
    private int height;
    private boolean collecting;
    private boolean overflow;
    private volatile Completed completed;

    private record Completed(
            WeakReference<Object> world,
            WeakReference<Object> owner,
            WeakReference<Object> screen,
            boolean screenPresent,
            int width,
            int height,
            long time,
            boolean overflow,
            List<Icon> icons) {}

    public void begin(
            Object currentWorld, Object currentOwner, Object currentScreen, int w, int h) {
        completed = null;
        pending.clear();
        collecting = currentWorld != null && currentOwner != null && w > 0 && h > 0;
        overflow = false;
        world = new WeakReference<>(currentWorld);
        owner = new WeakReference<>(currentOwner);
        screen = new WeakReference<>(currentScreen);
        screenPresent = currentScreen != null;
        width = w;
        height = h;
    }

    public void observe(Kind kind, String resourceId, String label, int x, int y, int w, int h) {
        if (!collecting || kind == null || resourceId == null || label == null) return;
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || (long) x + w > width || (long) y + h > height)
            return;
        if (resourceId.length() > 256 || label.length() > 256) return;
        if (pending.size() == MAX_ICONS) {
            overflow = true;
            return;
        }
        pending.add(new Icon(kind, resourceId, label, x, y, w, h));
    }

    public void finish(long now) {
        if (!collecting) return;
        collecting = false;
        completed =
                new Completed(
                        world,
                        owner,
                        screen,
                        screenPresent,
                        width,
                        height,
                        now,
                        overflow,
                        overflow ? List.of() : List.copyOf(pending));
    }

    public Sample sample(
            Object currentWorld,
            Object currentOwner,
            Object currentScreen,
            int w,
            int h,
            long now) {
        Completed frame = completed;
        if (frame == null) return new Sample("UNOBSERVED", List.of());
        if (currentWorld == null
                || currentOwner == null
                || frame.world.get() != currentWorld
                || frame.owner.get() != currentOwner
                || frame.screenPresent != (currentScreen != null)
                || frame.screen.get() != currentScreen
                || frame.width != w
                || frame.height != h) return new Sample("CONTEXT_CHANGED", List.of());
        long age = now - frame.time;
        if (age < 0 || age > 250_000_000L) return new Sample("STALE", List.of());
        if (frame.overflow) return new Sample("BUDGET_EXCEEDED", List.of());
        return new Sample("CURRENT", frame.icons);
    }
}
