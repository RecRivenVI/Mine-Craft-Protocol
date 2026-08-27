package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class InteractionPacingTest {
    @Test
    void springApproachOvershootsAndSettlesExactly() {
        double furthest = 0;
        for (int i = 0; i <= 100; i++) {
            double point = AgentPointer.approach(0, 400, i / 100.0, 400);
            assertEquals(point, AgentPointer.approach(0, 400, i / 100.0, 400));
            assertTrue(point >= 0 && point <= 404);
            furthest = Math.max(furthest, point);
        }
        assertTrue(furthest > 400);
        assertEquals(0, AgentPointer.approach(0, 400, 0, 400));
        assertEquals(400, AgentPointer.approach(0, 400, 1, 400));
        assertEquals(5, AgentPointer.approach(5, 5, 0.8, 0));
        assertTrue(AgentPointer.approach(10, 0, 0.9, 10) < 0);
        // Held drag uses the monotone trajectory instead of crossing another slot.
        assertEquals(100, AgentPointer.interpolate(0, 100, 1));
    }

    @Test
    void guiDwellAndActionPauseUseOneClockAndGameplayHasNoDelay() {
        var pacing = new InteractionPacing();
        long now = 1_000_000_000L;
        assertEquals(700, pacing.remainingMillis(true, "screen-a", now));
        assertEquals(0, pacing.remainingMillis(true, "screen-a", now + 700_000_000L));
        pacing.completed(true, "screen-a", now + 700_000_000L, true);
        assertEquals(280, pacing.remainingMillis(true, "screen-a", now + 700_000_000L));
        assertEquals(700, pacing.remainingMillis(true, "screen-b", now + 800_000_000L));
        assertEquals(0, pacing.remainingMillis(false, "", now + 800_000_001L));
        assertEquals(700, pacing.remainingMillis(true, "screen-a", now + 800_000_002L));
        assertEquals(0, InteractionPacing.movementMillis(0));
        assertEquals(AgentPointer.settleMillis(1000), InteractionPacing.movementMillis(1000));
    }

    @Test
    void orbIsSymmetricCenteredAndFadesOutwardWithoutTintingTheScreen() {
        int[][] pixels = new int[41][41];
        ControlChrome.pointer((x0, y0, x1, y1, color) -> pixels[y0][x0] = color, 20, 20, 1);
        assertEquals(245, pixels[20][20] >>> 24);
        assertEquals(245, pixels[20][23] >>> 24);
        assertTrue((pixels[20][23] >>> 24) > (pixels[20][29] >>> 24));
        assertEquals(pixels[20][22], pixels[20][17]);
        assertEquals(pixels[22][20], pixels[17][20]);
        assertEquals(0, pixels[20][32]);
        assertEquals(0, pixels[0][0]);
        assertEquals(0, pixels[40][40]);
        ControlChrome.pointer(
                (x0, y0, x1, y1, color) -> fail("Invisible orb emitted pixels"), 20, 20, 0);
    }
}
