package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class PointerFeedbackTest {
    @Test
    void repeatedViewportAndPageTransitionsPreserveLastCellFractions() {
        var pointer = new AgentPointer();
        pointer.viewport(427, 240);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        pointer.move(426.75, 239.75);
        for (int page = 0; page < 4; page++) {
            pointer.viewport(427, 240);
            pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
            assertEquals(426.75, pointer.x());
            assertEquals(239.75, pointer.y());
        }
        pointer.plane(AgentPointer.Plane.GAMEPLAY_RELATIVE, 213.5, 120);
        pointer.move(10, 20);
        pointer.viewport(427, 240);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        assertEquals(426.75, pointer.x());
        assertEquals(239.75, pointer.y());
    }

    @Test
    void resizePreservesFractionalNormalizedPositionAtBothEdges() {
        var pointer = new AgentPointer();
        pointer.viewport(427, 240);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        pointer.move(426.75, 239.75);
        pointer.viewport(854, 480);
        assertEquals(853.5, pointer.x());
        assertEquals(479.5, pointer.y());
        pointer.viewport(427, 240);
        assertEquals(426.75, pointer.x());
        assertEquals(239.75, pointer.y());
    }

    @Test
    void guiPositionSurvivesPageAndGameplayTransitionsAndScalesOnResize() {
        var pointer = new AgentPointer();
        pointer.viewport(800, 450);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 400, 225);
        pointer.move(120.5, 320.25);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 400, 225);
        assertEquals(120.5, pointer.x());
        pointer.plane(AgentPointer.Plane.GAMEPLAY_RELATIVE, 400, 225);
        pointer.move(999, 777);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 400, 225);
        assertEquals(120.5, pointer.x());
        assertEquals(320.25, pointer.y());
        pointer.viewport(400, 225);
        assertEquals(60.25, pointer.x());
        assertEquals(160.125, pointer.y());
        pointer.reset();
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 200, 112.5);
        assertEquals(60.25, pointer.x());
    }

    @Test
    void targetCentersPreserveFractionsAndRejectInvalidBounds() {
        assertEquals(108, AgentPointer.center(99, 18));
        assertEquals(25.5, AgentPointer.center(10, 31));
        assertEquals(2.75, AgentPointer.center(2.5, 0.5));
        for (double extent : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> AgentPointer.center(10, extent));
        assertThrows(IllegalArgumentException.class, () -> AgentPointer.center(Double.NaN, 16));
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentPointer.center(Double.MAX_VALUE, Double.MAX_VALUE));
    }

    @Test
    void pressShrinksReleaseRestoresWithoutMovingTheHotspot() {
        var pointer = new AgentPointer();
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 50, 60);
        long now = 1_000_000_000L;
        long revision = pointer.revision();
        pointer.button(0, true, now);
        assertEquals(1F, pointer.pressScale(now));
        assertEquals(0.85F, pointer.pressScale(now + 30_000_000L));
        assertEquals(0.7F, pointer.pressScale(now + 60_000_000L));
        assertEquals(0.7F, pointer.pressScale(now + 5_000_000_000L));
        pointer.button(0, false, now + 60_000_000L);
        assertEquals(0.85F, pointer.pressScale(now + 130_000_000L));
        assertEquals(1F, pointer.pressScale(now + 200_000_000L));
        assertEquals(50, pointer.x());
        assertEquals(60, pointer.y());
        assertEquals(revision, pointer.revision());
    }

    @Test
    void shortClicksAndRepeatedClicksContinueFromCurrentScale() {
        var pointer = new AgentPointer();
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 0, 0);
        long now = 1_000_000_000L;
        pointer.button(0, true, now);
        float pressed = pointer.pressScale(now + 40_000_000L);
        pointer.button(0, false, now + 40_000_000L);
        assertEquals(pressed, pointer.pressScale(now + 40_000_000L));
        float recovering = pointer.pressScale(now + 70_000_000L);
        pointer.button(0, true, now + 70_000_000L);
        assertEquals(recovering, pointer.pressScale(now + 70_000_000L));
        assertEquals(0.7F, pointer.pressScale(now + 130_000_000L));
        pointer.button(1, true, now + 130_000_000L);
        pointer.button(0, false, now + 130_000_000L);
        assertEquals(0.7F, pointer.pressScale(now + 200_000_000L));
        pointer.button(1, false, now + 200_000_000L);
        assertEquals(1F, pointer.pressScale(now + 340_000_000L));
    }

    @Test
    void cleanupPlaneChangeAndResetCannotLeaveAPressedOrb() {
        var pointer = new AgentPointer();
        long now = 1_000_000_000L;
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 1, 2);
        pointer.button(0, true, now);
        pointer.releaseButtons();
        assertEquals(1F, pointer.pressScale(now + 60_000_000L));
        pointer.button(0, true, now);
        pointer.plane(AgentPointer.Plane.GAMEPLAY_RELATIVE, 0, 0);
        pointer.button(0, true, now);
        assertEquals(1F, pointer.pressScale(now + 60_000_000L));
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 1, 2);
        pointer.button(0, true, now);
        pointer.reset();
        assertEquals(1F, pointer.pressScale(now + 60_000_000L));
        assertFalse(pointer.initialized());
        assertEquals(AgentPointer.Plane.HIDDEN, pointer.plane());
        assertTrue(pointer.hasVisualPosition());
        assertEquals(1, pointer.x());
        assertEquals(2, pointer.y());
    }

    @Test
    void geometryStaysEvenAndSymmetricThroughoutTheClick() {
        assertEquals(24, ControlChrome.POINTER_DIAMETER);
        for (float scale : new float[] {1F, 0.94F, 0.85F, 0.81F, 0.7F}) {
            int[][] pixels = new int[40][40];
            ControlChrome.pointer(
                    (x0, y0, x1, y1, color) -> pixels[y0][x0] = color, 20, 20, 1F, scale);
            int minX = 40, maxX = -1;
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    assertEquals(pixels[y][x], pixels[y][39 - x]);
                    assertEquals(pixels[y][x], pixels[39 - y][x]);
                    if (pixels[y][x] != 0) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                }
            }
            assertEquals(0, (maxX - minX + 1) % 2);
            assertEquals(245, pixels[20][20] >>> 24);
            assertEquals(0, pixels[0][0]);
        }
    }
}
