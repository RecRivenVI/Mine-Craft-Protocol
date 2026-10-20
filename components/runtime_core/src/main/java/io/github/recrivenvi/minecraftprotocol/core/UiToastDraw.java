package io.github.recrivenvi.minecraftprotocol.core;

import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;

/** Text submitted while a resident native Toast instance is actually being drawn. */
public final class UiToastDraw {
    public interface Source {
        UiToastDraw minecraftProtocolProbe$toastDraw();
    }

    public record Sample(
            String label,
            double x,
            double y,
            int width,
            int height,
            float slidePortion,
            String renderPhase) {}

    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private volatile Completed completed;

    private record Completed(
            WeakReference<Object> owner,
            WeakReference<Object> world,
            WeakReference<Object> camera,
            WeakReference<Object> screen,
            boolean worldPresent,
            boolean cameraPresent,
            boolean screenPresent,
            int guiWidth,
            int guiHeight,
            long capturedAt,
            Sample sample) {}

    private static final class Scope {
        final Object graphics;
        final StringBuilder text = new StringBuilder();
        int width;
        int height;
        int lines;
        boolean overflow;

        Scope(Object graphics) {
            this.graphics = graphics;
        }
    }

    public void capture(
            Object graphics,
            Object owner,
            Object world,
            Object camera,
            Object screen,
            int guiWidth,
            int guiHeight,
            double x,
            double y,
            float slidePortion,
            boolean hiding,
            Runnable nativeDraw) {
        capture(
                graphics,
                owner,
                world,
                camera,
                screen,
                guiWidth,
                guiHeight,
                x,
                y,
                slidePortion,
                hiding,
                System::nanoTime,
                nativeDraw);
    }

    public void capture(
            Object graphics,
            Object owner,
            Object world,
            Object camera,
            Object screen,
            int guiWidth,
            int guiHeight,
            double x,
            double y,
            float slidePortion,
            boolean hiding,
            LongSupplier clock,
            Runnable nativeDraw) {
        completed = null;
        Scope previous = ACTIVE.get();
        Scope scope = new Scope(graphics);
        ACTIVE.set(scope);
        boolean success = false;
        try {
            nativeDraw.run();
            success = true;
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
            if (!success) completed = null;
            if (success
                    && owner != null
                    && guiWidth > 0
                    && guiHeight > 0
                    && Double.isFinite(x)
                    && Double.isFinite(y)
                    && Float.isFinite(slidePortion)
                    && slidePortion >= 0
                    && slidePortion <= 1
                    && scope.width > 0
                    && scope.height > 0
                    && !scope.overflow) {
                completed =
                        new Completed(
                                new WeakReference<>(owner),
                                new WeakReference<>(world),
                                new WeakReference<>(camera),
                                new WeakReference<>(screen),
                                world != null,
                                camera != null,
                                screen != null,
                                guiWidth,
                                guiHeight,
                                clock.getAsLong(),
                                new Sample(
                                        UiRenderedText.bounded(scope.text.toString()),
                                        x,
                                        y,
                                        scope.width,
                                        scope.height,
                                        slidePortion,
                                        hiding
                                                ? "exiting"
                                                : slidePortion < 1 ? "entering" : "fully_shown"));
            }
        }
    }

    public static void background(Object graphics, int x, int y, int width, int height) {
        Scope scope = ACTIVE.get();
        if (scope != null
                && scope.graphics == graphics
                && x == 0
                && y == 0
                && width > 0
                && height > 0) {
            scope.width = width;
            scope.height = height;
        }
    }

    public static void text(Object graphics, String text, int color) {
        Scope scope = ACTIVE.get();
        if (scope == null || scope.graphics != graphics || (color >>> 24) == 0 || text.isBlank())
            return;
        if (++scope.lines > 64) {
            scope.overflow = true;
            return;
        }
        if (scope.text.length() < 2048) {
            if (!scope.text.isEmpty()) scope.text.append('\n');
            scope.text.append(UiRenderedText.bounded(text));
        }
    }

    public Sample current(
            Object owner,
            Object world,
            Object camera,
            Object screen,
            int guiWidth,
            int guiHeight,
            long now) {
        Completed frame = completed;
        if (frame == null
                || owner == null
                || frame.owner.get() != owner
                || frame.world.get() != world
                || frame.worldPresent != (world != null)
                || frame.camera.get() != camera
                || frame.cameraPresent != (camera != null)
                || frame.screen.get() != screen
                || frame.screenPresent != (screen != null)
                || frame.guiWidth != guiWidth
                || frame.guiHeight != guiHeight
                || now < frame.capturedAt
                || now - frame.capturedAt > 250_000_000L) return null;
        return frame.sample;
    }
}
