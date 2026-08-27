package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ControlMotionTest {
    @Test
    void textFadeDoesNotSubmitVanillaOpaqueAlphaSentinels() {
        for (int alpha = 0; alpha <= 3; alpha++)
            assertFalse(ControlChrome.textVisible(alpha / 255F));
        assertTrue(ControlChrome.textVisible(4 / 255F));
        assertTrue(ControlChrome.textVisible(1));
        assertFalse(ControlChrome.textVisible(Float.NaN));
        assertFalse(ControlChrome.textVisible(Float.NEGATIVE_INFINITY));
    }

    @Test
    void springIsASecondOrderResponseNotATimedOvershootCurve() {
        double response = 0.24, damping = 0.84, h = 0.00001;
        double omega = 2 * Math.PI / response;
        for (double t : new double[] {0.02, 0.1, 0.2, 0.3}) {
            double y = AgentPointer.springProgress(t, response, damping);
            double a = AgentPointer.springProgress(t - h, response, damping);
            double b = AgentPointer.springProgress(t + h, response, damping);
            double velocity = (b - a) / (2 * h);
            double acceleration = (b - 2 * y + a) / (h * h);
            assertEquals(
                    0,
                    acceleration + 2 * damping * omega * velocity + omega * omega * (y - 1),
                    0.0001);
        }
        assertEquals(0, AgentPointer.springProgress(0, response, damping));
        assertTrue(AgentPointer.springProgress(0.24, response, damping) > 1);
        assertEquals(1, AgentPointer.springProgress(10, response, damping), 1e-12);
    }

    @Test
    void distanceResponseHasBoundsAndExactFractionalLandings() {
        assertEquals(0.24, AgentPointer.responseSeconds(196));
        assertEquals(0.84, AgentPointer.dampingFraction(196));
        assertEquals(0.9, AgentPointer.responseSeconds(1000));
        assertEquals(0.9, AgentPointer.dampingFraction(1000));
        assertEquals(2.2, AgentPointer.responseSeconds(10000));
        for (double distance : new double[] {0, 1, 196, 197, 500, 1000, 4000}) {
            long duration = InteractionPacing.movementMillis(distance);
            int steps = InteractionPacing.movementSteps(duration);
            assertTrue(steps >= 1 && steps <= 240);
            assertTrue(duration <= 4000);
            assertEquals(25.5, AgentPointer.approach(10, 25.5, 1, distance));
            if (distance > 0) {
                double atEnd =
                        AgentPointer.springProgress(
                                duration / 1000.0,
                                AgentPointer.responseSeconds(distance),
                                AgentPointer.dampingFraction(distance));
                assertTrue(Math.abs(1 - atEnd) * distance <= 3.157);
                assertTrue(Math.abs(1 - atEnd) <= 0.005);
            }
        }
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> AgentPointer.settleMillis(invalid));
    }

    @Test
    void fadesUseElapsedTimeAndRetargetWithoutAVisualJump() {
        var chrome = new ControlChrome();
        long t = 1;
        assertEquals(0, chrome.update(AgentControlSession.Mode.TAKEOVER, true, true, t));
        chrome.update(AgentControlSession.Mode.TAKEOVER, true, true, t + 250_000_000L);
        assertEquals(0.684643, chrome.alpha(), 0.00001);
        float before = chrome.alpha();
        chrome.update(AgentControlSession.Mode.READ, false, false, t + 250_000_000L);
        assertEquals(before, chrome.alpha());
        chrome.update(AgentControlSession.Mode.READ, false, false, t + 750_000_000L);
        assertEquals(0, chrome.alpha());
        assertEquals(0, chrome.pointerAlpha());
        var sparse = new ControlChrome();
        sparse.update(AgentControlSession.Mode.TAKEOVER, true, true, t);
        sparse.update(AgentControlSession.Mode.TAKEOVER, true, true, t + 440_000_000L);
        assertEquals(1, sparse.pointerAlpha());
        var dense = new ControlChrome();
        for (long offset = 0; offset <= 440_000_000L; offset += 10_000_000L)
            dense.update(AgentControlSession.Mode.TAKEOVER, true, true, t + offset);
        assertEquals(sparse.alpha(), dense.alpha());
        assertEquals(sparse.pointerAlpha(), dense.pointerAlpha());
    }

    @Test
    void blueBreathingHasThreeSecondPeriodAndDoesNotTouchScreenCentre() {
        var chrome = new ControlChrome();
        long t = 1;
        chrome.update(AgentControlSession.Mode.TAKEOVER, true, true, t);
        chrome.update(AgentControlSession.Mode.TAKEOVER, true, true, t + 1_500_000_000L);
        assertEquals(1, chrome.edgeAlpha());
        assertEquals(1, chrome.edgeWidth());
        chrome.update(AgentControlSession.Mode.TAKEOVER, true, true, t + 3_000_000_000L);
        assertEquals(0.76F, chrome.edgeAlpha());
        assertEquals(0.88F, chrome.edgeWidth());
        int[][] pixels = new int[360][640];
        ControlChrome.edges(
                (x0, y0, x1, y1, color) -> {
                    for (int y = y0; y < y1; y++)
                        for (int x = x0; x < x1; x++) pixels[y][x] = color;
                },
                640,
                360,
                chrome.edgeAlpha(),
                chrome.edgeWidth(),
                4);
        assertEquals(0x339CFF, pixels[0][0] & 0xFFFFFF);
        assertEquals(pixels[0][0], pixels[359][639]);
        assertEquals(0, pixels[180][320]);
        assertEquals(0, pixels[24][320]);
    }
}
