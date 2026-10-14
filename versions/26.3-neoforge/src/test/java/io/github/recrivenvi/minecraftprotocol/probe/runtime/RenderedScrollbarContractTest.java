package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.AutomationEngine;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

final class RenderedScrollbarContractTest {
    private JsonArray controls(boolean enabled, int y, String context) {
        return new RenderedUiAdapter(7, ignored -> 11)
                .scrollbar(
                        new Object(),
                        "loom.scrollbar",
                        new ScreenRectangle(100, 9, 12, 56),
                        new ScreenRectangle(100, y, 12, 15),
                        new ScreenRectangle(0, 0, 640, 360),
                        enabled,
                        y == 13 ? 0 : 1,
                        context);
    }

    @Test
    void nativeDragRegionsUseRealHitIntersectionAndSemanticRoles() {
        JsonArray result = controls(true, 54, "banner:red");
        JsonObject track = result.get(0).getAsJsonObject(), thumb = result.get(1).getAsJsonObject();
        assertEquals("scrollbar", track.get("role").getAsString());
        assertEquals("scrollbar_thumb", thumb.get("role").getAsString());
        assertEquals(11, thumb.get("height").getAsInt());
        assertEquals(59.5, thumb.get("interactionY").getAsDouble());
        assertTrue(thumb.getAsJsonArray("actions").contains(new JsonPrimitive("drag")));
        assertTrue(
                thumb.get("interactionLayer").getAsInt()
                        > track.get("interactionLayer").getAsInt());
    }

    @Test
    void ownScrollKeepsGuardButInputContextReplacementInvalidatesIt() {
        JsonObject before = controls(true, 13, "banner:red").get(1).getAsJsonObject();
        JsonObject after = controls(true, 54, "banner:red").get(1).getAsJsonObject();
        assertEquals(before.get("semanticState"), after.get("semanticState"));
        assertNotEquals(before.get("y"), after.get("y"));
        assertNotEquals(
                before.get("semanticState"),
                controls(true, 13, "banner:blue").get(1).getAsJsonObject().get("semanticState"));
    }

    @Test
    void dragAllowsClippedThumbMotionButStillRejectsChangedContextOrFrame() {
        JsonObject original = controls(true, 13, "banner:red").get(1).getAsJsonObject();
        JsonObject guard = new JsonObject(), frame = new JsonObject(), selector = new JsonObject();
        frame.addProperty("width", 640);
        selector.add("nodeId", original.get("nodeId"));
        guard.add("frame", frame);
        guard.add("selector", selector);
        guard.add("node", original);
        guard.addProperty("action", "click");
        guard.addProperty("dragActive", true);
        JsonObject tree = new JsonObject();
        tree.addProperty("width", 640);
        tree.add("children", controls(true, 54, "banner:red"));
        assertDoesNotThrow(() -> AutomationEngine.validatePointerGuard(guard, tree));
        guard.addProperty("dragActive", false);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> AutomationEngine.validatePointerGuard(guard, tree));
        guard.addProperty("dragActive", true);
        tree.add("children", controls(true, 54, "banner:blue"));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> AutomationEngine.validatePointerGuard(guard, tree));
        tree.add("children", controls(true, 54, "banner:red"));
        tree.addProperty("width", 800);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> AutomationEngine.validatePointerGuard(guard, tree));
    }

    @Test
    void disabledScrollerRemainsVisibleWithoutActions() {
        for (JsonElement value : controls(false, 13, "empty")) {
            JsonObject node = value.getAsJsonObject();
            assertTrue(node.get("visible").getAsBoolean());
            assertFalse(node.get("active").getAsBoolean());
            assertEquals(0, node.getAsJsonArray("actions").size());
        }
    }
}
