package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControlChromeTransitionTest {
    private static final AgentControlSession.Mode READ = AgentControlSession.Mode.READ;
    private static final AgentControlSession.Mode OPERATE = AgentControlSession.Mode.OPERATE;
    private static final AgentControlSession.Mode TAKEOVER = AgentControlSession.Mode.TAKEOVER;

    @Test
    void interruptedTransitionKeepsEveryCurrentTextWeightWidthAndFade() {
        ControlChrome chrome = settled(TAKEOVER);
        long now = 1 + ControlChrome.FADE_NANOS;
        chrome.update(READ, true, false, now);
        now += 170_000_000L;
        chrome.update(READ, true, false, now);
        float[] before = weights(chrome);
        float width = chrome.textWidth(ControlChromeTransitionTest::measure);
        float alpha = chrome.alpha(), edge = chrome.edgeAlpha(), pointer = chrome.pointerAlpha();
        chrome.update(OPERATE, true, false, now);
        assertArrayEquals(before, weights(chrome));
        assertEquals(width, chrome.textWidth(ControlChromeTransitionTest::measure));
        assertEquals(alpha, chrome.alpha());
        assertEquals(edge, chrome.edgeAlpha());
        assertEquals(pointer, chrome.pointerAlpha());
        chrome.update(OPERATE, true, false, now + ControlChrome.FADE_NANOS);
        assertArrayEquals(new float[] {0, 1, 0}, weights(chrome));
    }

    @Test
    void repeatedRetargetsStayNormalizedAndSettleWithoutDiscardingVisibleLayers() {
        ControlChrome chrome = settled(READ);
        long now = 1 + ControlChrome.FADE_NANOS;
        var schedule = new AgentControlSession.Mode[] {TAKEOVER, READ, OPERATE};
        boolean sawThreeLayers = false;
        AgentControlSession.Mode current = READ;
        for (int step = 0; step < 90; step++) {
            now += 60_000_000L;
            chrome.update(current, true, current == TAKEOVER, now);
            float[] before = weights(chrome);
            float width = chrome.textWidth(ControlChromeTransitionTest::measure);
            current = schedule[step % schedule.length];
            chrome.update(current, true, current == TAKEOVER, now);
            assertArrayEquals(before, weights(chrome));
            assertEquals(width, chrome.textWidth(ControlChromeTransitionTest::measure));
            float sum = 0;
            int layers = 0;
            for (float weight : weights(chrome)) {
                assertTrue(Float.isFinite(weight) && weight >= 0 && weight <= 1);
                sum += weight;
                if (weight > 0.001F) layers++;
            }
            assertEquals(1F, sum, 0.000001F);
            sawThreeLayers |= layers == 3;
        }
        assertTrue(sawThreeLayers, "The previous visible mixture must survive a third mode");
        chrome.update(READ, true, false, now);
        chrome.update(READ, true, false, now + ControlChrome.FADE_NANOS);
        assertArrayEquals(new float[] {1, 0, 0}, weights(chrome));
    }

    @Test
    void denseAndSparseFramesUseTheSameElapsedTimeTransition() {
        ControlChrome sparse = settled(READ), dense = settled(READ);
        long last = 1 + ControlChrome.FADE_NANOS;
        var current = READ;
        var schedule = new AgentControlSession.Mode[] {TAKEOVER, OPERATE, READ, TAKEOVER, READ};
        for (var next : schedule) {
            long at = last + 130_000_000L;
            for (long frame = last + 10_000_000L; frame < at; frame += 10_000_000L)
                dense.update(current, true, current == TAKEOVER, frame);
            sparse.update(next, true, next == TAKEOVER, at);
            dense.update(next, true, next == TAKEOVER, at);
            assertArrayEquals(weights(sparse), weights(dense));
            assertEquals(
                    sparse.textWidth(ControlChromeTransitionTest::measure),
                    dense.textWidth(ControlChromeTransitionTest::measure));
            current = next;
            last = at;
        }
    }

    @Test
    void uninterruptedTransitionKeepsTheExistingHalfSecondBezier() {
        ControlChrome chrome = new ControlChrome();
        chrome.update(TAKEOVER, true, true, 0);
        assertArrayEquals(new float[] {1, 0, 0}, weights(chrome));
        chrome.update(TAKEOVER, true, true, ControlChrome.FADE_NANOS / 2);
        assertEquals(0.684643F, chrome.textWeight(TAKEOVER), 0.00001F);
        assertEquals(0.315357F, chrome.textWeight(READ), 0.00001F);
        chrome.update(TAKEOVER, true, true, ControlChrome.FADE_NANOS);
        assertArrayEquals(new float[] {0, 0, 1}, weights(chrome));
    }

    @Test
    void productionTextDrawingClipsEveryShrinkingFrameToItsCurrentPanel() {
        ControlChrome chrome = settled(TAKEOVER);
        long start = 1 + ControlChrome.FADE_NANOS;
        chrome.update(READ, true, false, start);
        boolean clippedLongText = false;
        for (long elapsed = 0; elapsed <= ControlChrome.FADE_NANOS; elapsed += 10_000_000L) {
            chrome.update(READ, true, false, start + elapsed);
            int barWidth = Math.round(chrome.textWidth(ControlChromeTransitionTest::measure)) + 20;
            int left = (640 - barWidth) / 2;
            Canvas canvas = new Canvas();
            chrome.text(canvas, left, 8, barWidth, 23, chrome.alpha());
            Box interior = new Box(left + 10, 10, left + barWidth - 10, 29);
            assertFalse(canvas.pixels.isEmpty());
            for (Pixel pixel : canvas.pixels) assertTrue(interior.contains(pixel.x, pixel.y));
            assertEquals(1, canvas.enables);
            assertEquals(1, canvas.disables);
            assertTrue(canvas.clips.isEmpty());
            clippedLongText |= canvas.clippedPixels > 0;
        }
        assertTrue(clippedLongText, "The fixture must actually exercise overflow of the old label");
    }

    @Test
    void nativeClipIsRestoredEvenWhenTextSubmissionThrows() {
        ControlChrome chrome = settled(TAKEOVER);
        Canvas canvas = new Canvas();
        canvas.throwDuringText = true;
        assertThrows(IllegalStateException.class, () -> chrome.text(canvas, 100, 8, 140, 23, 1));
        assertEquals(1, canvas.enables);
        assertEquals(1, canvas.disables);
        assertTrue(canvas.clips.isEmpty());
        canvas.pixel(639, 359);
        assertTrue(
                canvas.pixels.contains(new Pixel(639, 359)),
                "Subsequent Pointer/edge drawing must not inherit the message clip");
    }

    @Test
    void invisibleOrEmptyTextAreasDoNotMutateTheNativeScissorStack() {
        ControlChrome chrome = settled(READ);
        for (float alpha : new float[] {0, 0.001F, Float.NaN}) {
            Canvas canvas = new Canvas();
            chrome.text(canvas, 100, 8, 140, 23, alpha);
            assertEquals(0, canvas.enables);
        }
        Canvas empty = new Canvas();
        chrome.text(empty, 100, 8, 20, 23, 1);
        assertEquals(0, empty.enables);
    }

    private static ControlChrome settled(AgentControlSession.Mode mode) {
        ControlChrome chrome = new ControlChrome();
        chrome.update(mode, true, mode == TAKEOVER, 1);
        chrome.update(mode, true, mode == TAKEOVER, 1 + ControlChrome.FADE_NANOS);
        return chrome;
    }

    private static float[] weights(ControlChrome chrome) {
        return new float[] {
            chrome.textWeight(READ), chrome.textWeight(OPERATE), chrome.textWeight(TAKEOVER)
        };
    }

    private static int measure(String message) {
        return message.equals(ControlChrome.MESSAGE)
                ? 300
                : message.equals(ControlChrome.OPERATE_MESSAGE) ? 130 : 100;
    }

    private record Pixel(int x, int y) {}

    private record Box(int left, int top, int right, int bottom) {
        boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private static class Canvas implements ControlChrome.TextRenderer {
        final ArrayDeque<Box> clips = new ArrayDeque<>();
        final List<Pixel> pixels = new ArrayList<>();
        int enables, disables, clippedPixels;
        boolean throwDuringText;

        public void enableScissor(int left, int top, int right, int bottom) {
            clips.push(new Box(left, top, right, bottom));
            enables++;
        }

        public void disableScissor() {
            clips.pop();
            disables++;
        }

        public void text(String message, int x, int y, int color) {
            if (throwDuringText) throw new IllegalStateException("native text submission failure");
            assertTrue((color >>> 24) >= 4);
            for (int dx = 0; dx < measure(message); dx++)
                for (int dy = 0; dy < 9; dy++) pixel(x + dx, y + dy);
        }

        void pixel(int x, int y) {
            if (clips.isEmpty() || clips.peek().contains(x, y)) pixels.add(new Pixel(x, y));
            else clippedPixels++;
        }
    }
}
