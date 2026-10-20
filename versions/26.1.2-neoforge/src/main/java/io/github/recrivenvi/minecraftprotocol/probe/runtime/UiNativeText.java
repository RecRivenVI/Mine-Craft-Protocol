package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Converts only the actual native text argument, withholding source text for obfuscated glyphs. */
public final class UiNativeText {
    private UiNativeText() {}

    public static void component(
            String family,
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int color) {
        sequence(family, graphics, font, text.getVisualOrderText(), x, y, color);
    }

    public static void literal(
            String family,
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int color) {
        observe(family, graphics, font, text, x, y, font.width(text), color);
    }

    public static void sequence(
            String family,
            GuiGraphicsExtractor graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color) {
        observe(family, graphics, font, plain(text), x, y, font.width(text), color);
    }

    private static void observe(
            String family,
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int color) {
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame != null)
            frame.nativeText(
                    family,
                    text,
                    x,
                    y,
                    width,
                    font.lineHeight,
                    (color >>> 24) / 255.0F,
                    graphics.pose(),
                    graphics.peekScissorStack());
    }

    public static void active(
            String family,
            TextAlignment alignment,
            int x,
            int y,
            ActiveTextCollector.Parameters parameters,
            FormattedCharSequence text) {
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame == null) return;
        Font font = Minecraft.getInstance().font;
        int width = font.width(text);
        frame.nativeText(
                family,
                plain(text),
                alignment.calculateLeft(x, width),
                y,
                width,
                font.lineHeight,
                parameters.opacity(),
                parameters.pose(),
                parameters.scissor());
    }

    static String plain(FormattedCharSequence text) {
        StringBuilder label = new StringBuilder();
        boolean[] obfuscated = {false};
        text.accept(
                (index, style, codepoint) -> {
                    if (style.isObfuscated()) {
                        obfuscated[0] = true;
                        return false;
                    }
                    if (label.length() >= 2048) return false;
                    label.appendCodePoint(codepoint);
                    return true;
                });
        return obfuscated[0] ? "" : label.toString();
    }
}
