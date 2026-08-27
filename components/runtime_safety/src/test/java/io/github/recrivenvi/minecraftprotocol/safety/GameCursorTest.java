package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class GameCursorTest {
    private AgentPointer pointer() {
        var pointer = new AgentPointer();
        pointer.viewport(427, 240);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        pointer.beginGameControl(false);
        pointer.move(91.5, 77.25);
        return pointer;
    }

    @Test
    void ordinaryPageReleaseIsANoopAndKeepsFractionalHotspot() {
        var pointer = pointer();
        long revision = pointer.gameCursorRevision();
        assertFalse(pointer.gameCapture(false, 213.5, 120));
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        assertEquals(91.5, pointer.x());
        assertEquals(77.25, pointer.y());
        assertEquals(revision, pointer.gameCursorRevision());
        assertTrue(pointer.gameCursorVisible());
    }

    @Test
    void effectiveCaptureAndReleaseFollowVanillaCenterAndVisibility() {
        var pointer = pointer();
        assertTrue(pointer.gameCapture(true, 213.5, 120));
        assertTrue(pointer.gameCaptured());
        assertFalse(pointer.gameCursorVisible());
        pointer.plane(AgentPointer.Plane.GAMEPLAY_RELATIVE, 0, 0);
        pointer.move(900, 700);
        assertTrue(pointer.gameCapture(false, 213.5, 120));
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        assertEquals(213.5, pointer.x());
        assertEquals(120, pointer.y());
        assertTrue(pointer.gameCursorVisible());
        assertFalse(pointer.gameCaptured());
        assertFalse(pointer.gameCapture(false, 213.5, 120));
    }

    @Test
    void explicitGameRequestCanPositionOrHideWithoutCapture() {
        var pointer = pointer();
        assertTrue(pointer.gameRequest(false, false, 12.5, 23.25));
        assertFalse(pointer.gameCaptured());
        assertFalse(pointer.gameCursorVisible());
        assertEquals(12.5, pointer.x());
        assertEquals(23.25, pointer.y());
        long revision = pointer.gameCursorRevision();
        assertFalse(pointer.gameRequest(false, false, 12.5, 23.25));
        assertEquals(revision, pointer.gameCursorRevision());
        assertTrue(pointer.gameRequest(false, true, 12.5, 23.25));
        assertTrue(pointer.gameCursorVisible());
    }

    @Test
    void gameRequestsPreserveFractionsInTheLastViewportCell() {
        var pointer = pointer();
        assertTrue(pointer.gameRequest(false, true, 426.75, 239.75));
        assertEquals(426.75, pointer.x());
        assertEquals(239.75, pointer.y());
        long revision = pointer.gameCursorRevision();
        assertFalse(pointer.gameRequest(false, true, 426.75, 239.75));
        assertEquals(revision, pointer.gameCursorRevision());
    }

    @Test
    void physicalWindowEdgeRequestKeepsItsExactGuiCoordinate() {
        var pointer = pointer();
        double x = 2559.0 * 427 / 2560;
        double y = 1439.0 * 240 / 1440;
        pointer.gameRequest(false, true, x, y);
        assertEquals(x, pointer.x());
        assertEquals(y, pointer.y());
        assertEquals(2559, pointer.x() * 2560 / 427, 1e-9);
        assertEquals(1439, pointer.y() * 1440 / 240, 1e-9);
    }

    @Test
    void outOfBoundsRequestsUseTheExclusiveContinuousViewportLimit() {
        for (double extent : new double[] {427, 240, 1, 0.5}) {
            var pointer = new AgentPointer();
            pointer.viewport(extent, extent);
            pointer.gameRequest(false, true, extent + 1, extent);
            assertEquals(Math.nextDown(extent), pointer.x());
            assertEquals(Math.nextDown(extent), pointer.y());
            assertTrue(pointer.x() >= 0 && pointer.x() < extent);
            assertTrue(pointer.y() >= 0 && pointer.y() < extent);
            pointer.gameRequest(false, true, -1, -1);
            assertEquals(0, pointer.x());
            assertEquals(0, pointer.y());
        }
    }

    @Test
    void gameRequestsStayInViewportAndDoNotGrantControl() {
        var pointer = pointer();
        pointer.gameRequest(false, true, 900, -10);
        assertEquals(Math.nextDown(427.0), pointer.x());
        assertEquals(0, pointer.y());
        assertThrows(
                IllegalArgumentException.class,
                () -> pointer.gameRequest(false, true, Double.NaN, 1));
        pointer.reset();
        assertEquals(AgentPointer.Plane.HIDDEN, pointer.plane());
        pointer.beginGameControl(false);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        assertEquals(Math.nextDown(427.0), pointer.x());
    }

    @Test
    void explicitRepeatedPositionIsEffectiveAfterRelativeMovement() {
        var pointer = pointer();
        pointer.gameCapture(true, 213.5, 120);
        pointer.plane(AgentPointer.Plane.GAMEPLAY_RELATIVE, 213.5, 120);
        pointer.move(230, 130);
        assertTrue(pointer.gameRequest(true, false, 213.5, 120));
        assertEquals(213.5, pointer.x());
        assertEquals(120, pointer.y());
        assertFalse(pointer.gameRequest(true, false, 213.5, 120));
    }
}
