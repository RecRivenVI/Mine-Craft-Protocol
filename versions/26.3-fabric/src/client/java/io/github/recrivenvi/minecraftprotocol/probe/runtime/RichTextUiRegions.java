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
    private final ScreenRectangle outerClip;
    private Parameters parameters = new Parameters(new Matrix3x2f());
    private int glyphs;
    private Region pending;
    private final Map<Style, StringBuilder> labels = new HashMap<>();

    RichTextUiRegions(Font font, Consumer<Region> output) {
        this(font, output, null);
    }

    RichTextUiRegions(Font font, Consumer<Region> output, ScreenRectangle outerClip) {
        this.font = font;
        this.output = output;
        this.outerClip = outerClip;
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
        Region clipped = clip(clip(new Region(style, "", l, t, r, d), p.scissor()), outerClip);
        if (clipped == null) {
            flush();
            return;
        }
        l = clipped.left();
        t = clipped.top();
        r = clipped.right();
        d = clipped.bottom();
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

    static Region clip(Region region, ScreenRectangle clip) {
        if (region == null) return null;
        double left = region.left(),
                top = region.top(),
                right = region.right(),
                bottom = region.bottom();
        if (clip != null) {
            left = Math.max(left, clip.left());
            top = Math.max(top, clip.top());
            right = Math.min(right, clip.right());
            bottom = Math.min(bottom, clip.bottom());
        }
        return right <= left || bottom <= top
                ? null
                : new Region(region.style(), region.text(), left, top, right, bottom);
    }

    private void flush() {
        if (pending != null) {
            output.accept(pending);
            pending = null;
        }
    }
}
