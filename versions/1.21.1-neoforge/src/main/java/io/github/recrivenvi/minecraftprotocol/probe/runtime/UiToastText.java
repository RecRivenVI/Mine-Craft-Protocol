package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.github.recrivenvi.minecraftprotocol.core.UiToastDraw;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.AdvancementToastUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.RecipeToastUiAccessor;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.*;

/** Mirrors this Target's native text branch using the actual render callback's clock/font. */
public final class UiToastText {
    private UiToastText() {}

    public interface FrameSource {
        Frame minecraftProtocolProbe$toastFrame();
    }

    /** Native Toast-local geometry is sampled before its resident transform is popped. */
    public static final class Frame {
        private final String contextKey;
        private final WeakReference<Object> owner, world, camera, screen, overlay;
        private final boolean worldPresent, cameraPresent, screenPresent, overlayPresent;
        private final UiRenderedText text;
        private final double x, y;
        private final int width, height;

        Frame(
                String contextKey,
                Object owner,
                Object world,
                Object camera,
                Object screen,
                Object overlay,
                UiRenderedText text,
                double x,
                double y,
                int width,
                int height) {
            this.contextKey = contextKey;
            this.owner = new WeakReference<>(owner);
            this.world = new WeakReference<>(world);
            this.camera = new WeakReference<>(camera);
            this.screen = new WeakReference<>(screen);
            this.overlay = new WeakReference<>(overlay);
            worldPresent = world != null;
            cameraPresent = camera != null;
            screenPresent = screen != null;
            overlayPresent = overlay != null;
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Frame current(
                Object owner,
                Object world,
                Object camera,
                Object screen,
                Object overlay,
                int guiWidth,
                int guiHeight,
                long now) {
            if (owner == null
                    || this.owner.get() != owner
                    || this.world.get() != world
                    || worldPresent != (world != null)
                    || this.camera.get() != camera
                    || cameraPresent != (camera != null)
                    || this.screen.get() != screen
                    || screenPresent != (screen != null)
                    || this.overlay.get() != overlay
                    || overlayPresent != (overlay != null)
                    || text == null
                    || guiWidth <= 0
                    || guiHeight <= 0
                    || width <= 0
                    || height <= 0
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || contextKey == null
                    || contextKey.isBlank()
                    || text.width() != guiWidth
                    || text.height() != guiHeight
                    || now < text.capturedAtNanos()
                    || now - text.capturedAtNanos() > 250_000_000L) return null;
            return this;
        }

        public UiToastDraw.Sample sample(float portion, boolean hiding) {
            return new UiToastDraw.Sample(
                    text.label(),
                    x,
                    y,
                    width,
                    height,
                    portion,
                    hiding ? "exiting" : portion < 1 ? "entering" : "fully_shown");
        }

        public String textPhase() {
            return text.phase();
        }

        public String contextKey() {
            return contextKey;
        }
    }

    public static Frame captureFrame(
            Toast toast, GuiGraphics graphics, UiRenderedText text, String contextKey) {
        if (text == null) return null;
        Minecraft client = Minecraft.getInstance();
        return new Frame(
                contextKey,
                toast,
                client.level,
                client.getCameraEntity(),
                client.screen,
                client.getOverlay(),
                text,
                graphics.pose().last().pose().m30(),
                graphics.pose().last().pose().m31(),
                toast.width(),
                toast.height());
    }

    public static UiRenderedText capture(Toast toast, Font font, long elapsed) {
        // Native RecipeToast returns before drawing when its own recipe list is empty.
        if (toast instanceof RecipeToast
                && ((RecipeToastUiAccessor) toast).minecraftProtocolProbe$recipes().isEmpty())
            return null;
        String label = UiPresentationSnapshot.toastText(toast), phase = "static";
        if (toast instanceof AdvancementToast) {
            var display =
                    ((AdvancementToastUiAccessor) toast)
                            .minecraftProtocolProbe$advancement()
                            .value()
                            .display()
                            .orElse(null);
            if (display == null) {
                label = "";
                phase = "empty";
            } else {
                var lines = font.split(display.getTitle(), 125);
                String[] selected =
                        advancement(
                                display.getType().getDisplayName().getString(),
                                UiPresentationSnapshot.lines(lines),
                                lines.size(),
                                elapsed);
                label = selected[0];
                phase = selected[1];
            }
        }
        var window = Minecraft.getInstance().getWindow();
        return new UiRenderedText(
                label,
                phase,
                System.nanoTime(),
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight());
    }

    static String[] advancement(String header, String title, int lines, long elapsed) {
        if (lines == 1) return new String[] {header + "\n" + title, "header_and_title"};
        // 1.x's native renderer ORs alpha with 4, so even the phase boundary is not transparent.
        return elapsed < 1500 ? new String[] {header, "header"} : new String[] {title, "title"};
    }
}
