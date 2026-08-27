package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentPointer;
import org.junit.jupiter.api.Test;

final class GameCursorGuardTest {
    @Test
    void gameRequestInvalidatesPendingGestureWithoutChangingScreen() {
        var pointer = new AgentPointer();
        pointer.viewport(427, 240);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        pointer.beginGameControl(false);
        JsonObject tree = new JsonObject(), guard = new JsonObject();
        tree.addProperty("screenRevision", 5);
        tree.addProperty("gameCursorRevision", pointer.gameCursorRevision());
        guard.add("frame", tree.deepCopy());
        AutomationEngine.validatePointerGuard(guard, tree);
        pointer.move(91.5, 77.25);
        assertFalse(pointer.gameCapture(false, 213.5, 120));
        tree.addProperty("gameCursorRevision", pointer.gameCursorRevision());
        AutomationEngine.validatePointerGuard(guard, tree);
        assertTrue(pointer.gameRequest(false, false, 10, 20));
        tree.addProperty("gameCursorRevision", pointer.gameCursorRevision());
        var error =
                assertThrows(
                        ProtocolState.ProtocolException.class,
                        () -> AutomationEngine.validatePointerGuard(guard, tree));
        assertEquals("UI_TARGET_INVALIDATED", error.code());
    }
}
