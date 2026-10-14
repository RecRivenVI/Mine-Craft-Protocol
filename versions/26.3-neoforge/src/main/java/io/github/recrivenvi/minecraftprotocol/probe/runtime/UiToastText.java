package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.github.recrivenvi.minecraftprotocol.probe.mixin.AdvancementToastUiAccessor;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.*;

/** Mirrors this Target's native text branch using the actual render callback's clock/font. */
public final class UiToastText {
    private UiToastText() {}

    public static UiRenderedText capture(Toast toast, Font font, long elapsed) {
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
                var lines = font.split(display.title(), 125);
                String[] selected =
                        advancement(
                                display.type().getDisplayName().getString(),
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
        // 26.x permits a genuinely transparent frame at the phase boundary; do not leak its text.
        float fade = elapsed < 1500 ? (1500L - elapsed) / 300.0F : (elapsed - 1500L) / 300.0F;
        int alpha = (int) Math.floor(Math.max(0, Math.min(1, fade)) * (elapsed < 1500 ? 255 : 252));
        if (alpha == 0) return new String[] {"", "transparent"};
        return elapsed < 1500 ? new String[] {header, "header"} : new String[] {title, "title"};
    }
}
