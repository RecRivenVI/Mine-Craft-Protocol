package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.font.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/** Reads the same prepared glyph active areas used by Vanilla's clickable-style finder. */
final class RichTextUiRegions implements ActiveTextCollector {
    record Region(Style style, String text, double left, double top, double right, double bottom) {}

    private final Font font;
    private final Consumer<Region> output;
    private Parameters parameters = new Parameters(new Matrix3x2f());
    private int glyphs;
    private Region pending;
    private final Map<Style, StringBuilder> labels = new HashMap<>();

    RichTextUiRegions(Font font, Consumer<Region> output) {
        this.font = font;
        this.output = output;
    }

    public Parameters defaultParameters() {
        return parameters;
    }

    public void defaultParameters(Parameters value) {
        parameters = value;
    }

    public void accept(
            TextAlignment alignment, int x, int y, Parameters p, FormattedCharSequence text) {
        if (glyphs >= 16384) return;
        labels.clear();
        int[] read = {0};
        text.accept(
                (index, style, codePoint) -> {
                    if (++read[0] > 16384) return false;
                    StringBuilder label =
                            labels.computeIfAbsent(style, ignored -> new StringBuilder());
                    if (label.length() < 2048) label.appendCodePoint(codePoint);
                    return true;
                });
        GuiTextRenderState state =
                new GuiTextRenderState(
                        font,
                        text,
                        p.pose(),
                        alignment.calculateLeft(x, font, text),
                        y,
                        -1,
                        0,
                        false,
                        true,
                        p.scissor());
        state.ensurePrepared()
                .visit(
                        new Font.GlyphVisitor() {
                            public void acceptGlyph(TextRenderable.Styled area) {
                                active(area, p);
                            }

                            public void acceptEmptyArea(EmptyArea area) {
                                active(area, p);
                            }
                        });
        flush();
    }

    public void acceptScrolling(
            Component text, int centerX, int left, int right, int top, int bottom, Parameters p) {
        defaultScrollingHelper(
                text, centerX, left, right, top, bottom, font.width(text), font.lineHeight, p);
    }

    private void active(ActiveArea area, Parameters p) {
        if (++glyphs > 16384) return;
        Style style = area.style();
        Vector2f a = p.pose().transformPosition(new Vector2f(area.activeLeft(), area.activeTop()));
        Vector2f b =
                p.pose().transformPosition(new Vector2f(area.activeRight(), area.activeBottom()));
        double l = Math.min(a.x, b.x),
                t = Math.min(a.y, b.y),
                r = Math.max(a.x, b.x),
                d = Math.max(a.y, b.y);
        ScreenRectangle clip = p.scissor();
        if (clip != null) {
            l = Math.max(l, clip.left());
            t = Math.max(t, clip.top());
            r = Math.min(r, clip.right());
            d = Math.min(d, clip.bottom());
        }
        if (r <= l || d <= t) {
            flush();
            return;
        }
        if (pending != null
                && pending.style().equals(style)
                && Math.abs(pending.right() - l) < 0.1
                && Math.abs(pending.top() - t) < 0.1
                && Math.abs(pending.bottom() - d) < 0.1)
            pending = new Region(style, pending.text(), pending.left(), t, r, d);
        else {
            flush();
            pending =
                    new Region(
                            style,
                            labels.getOrDefault(style, new StringBuilder()).toString(),
                            l,
                            t,
                            r,
                            d);
        }
    }

    private void flush() {
        if (pending != null) {
            output.accept(pending);
            pending = null;
        }
    }
}
