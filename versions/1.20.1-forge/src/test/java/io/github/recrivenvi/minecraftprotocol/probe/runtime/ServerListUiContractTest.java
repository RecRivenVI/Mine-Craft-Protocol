package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

final class ServerListUiContractTest {
    private JsonArray controls(boolean hover, int index, ScreenRectangle clip) {
        JsonObject row = new JsonObject();
        row.addProperty("active", true);
        row.addProperty("visible", true);
        JsonArray result =
                new RenderedUiAdapter(7, ignored -> 11)
                        .serverControls(
                                new Object(),
                                "Visible name",
                                row,
                                new ScreenRectangle(100, 50, 32, 32),
                                clip,
                                index,
                                2,
                                hover);
        assertEquals("Visible name", row.get("label").getAsString());
        assertEquals("server.entry", row.get("controlKey").getAsString());
        return result;
    }

    @Test
    void hoverRevealsOnlyNativeAvailableIconRegions() {
        ScreenRectangle clip = new ScreenRectangle(0, 0, 640, 360);
        JsonArray hidden = controls(false, 0, clip);
        for (JsonElement e : hidden) {
            assertFalse(e.getAsJsonObject().get("visible").getAsBoolean());
            assertEquals(0, e.getAsJsonObject().getAsJsonArray("actions").size());
        }
        JsonArray first = controls(true, 0, clip);
        assertEquals(124, first.get(0).getAsJsonObject().get("interactionX").getAsDouble());
        assertEquals(66, first.get(0).getAsJsonObject().get("interactionY").getAsDouble());
        assertFalse(first.get(1).getAsJsonObject().get("visible").getAsBoolean());
        assertEquals(108, first.get(2).getAsJsonObject().get("interactionX").getAsDouble());
        assertEquals(74, first.get(2).getAsJsonObject().get("interactionY").getAsDouble());
        assertFalse(controls(true, 1, clip).get(2).getAsJsonObject().get("visible").getAsBoolean());
    }

    @Test
    void clippedIconsCannotClickOutsideListAndOrderChangesInvalidateIdentity() {
        JsonArray first = controls(true, 0, new ScreenRectangle(0, 70, 640, 290));
        JsonObject join = first.get(0).getAsJsonObject();
        assertEquals(70, join.get("y").getAsDouble());
        assertEquals(76, join.get("interactionY").getAsDouble());
        JsonArray next = controls(true, 1, new ScreenRectangle(0, 70, 640, 290));
        assertNotEquals(join.get("nodeId"), next.get(0).getAsJsonObject().get("nodeId"));
        assertNotEquals(
                join.get("semanticState"), next.get(0).getAsJsonObject().get("semanticState"));
    }
}
