package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Native font advances and read-only native style hit tests, never screenshot geometry. */
final class RichTextUiRegions {
    record Region(Style style, String text, double left, double top, double right, double bottom) {}

    private final Font font;
    private final Consumer<Region> output;
    private int glyphs;

    RichTextUiRegions(Font font, Consumer<Region> output) {
        this.font = font;
        this.output = output;
    }

    void line(double x, double y, double scale, double height, FormattedCharSequence text) {
        StringBuilder label = new StringBuilder();
        double[] cursor = {x};
        Style[] style = {null};
        double[] start = {x};
        Runnable flush =
                () -> {
                    if (style[0] != null && cursor[0] > start[0])
                        output.accept(
                                new Region(
                                        style[0],
                                        label.toString(),
                                        start[0],
                                        y,
                                        cursor[0],
                                        y + height));
                };
        text.accept(
                (i, s, cp) -> {
                    if (++glyphs > 16384) return false;
                    if (!s.equals(style[0])) {
                        flush.run();
                        start[0] = cursor[0];
                        style[0] = s;
                        label.setLength(0);
                    }
                    if (label.length() < 2048) label.appendCodePoint(cp);
                    cursor[0] +=
                            font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp, s))
                                    * scale;
                    return true;
                });
        flush.run();
    }
}
