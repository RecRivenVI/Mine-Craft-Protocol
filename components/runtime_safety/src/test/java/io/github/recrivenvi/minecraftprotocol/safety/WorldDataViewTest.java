package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class WorldDataViewTest {
    @Test
    void actualWorldContractAdvertisesDistinctUseAndPlacementSurfaceCapabilities() {
        var contract = WorldDataView.contract();
        assertEquals(
                "optional_named_face_bounded_uv_native_pick",
                contract.get("userUseSurface").getAsString());
        assertEquals(
                "named_face_bounded_uv_native_pick", contract.get("userPlacement").getAsString());
        assertEquals(
                "live_eye_position_and_fresh_native_pick",
                contract.get("userSemanticPosition").getAsString());
        assertFalse(contract.get("milestoneComplete").getAsBoolean());
    }

    @Test
    void commandEvidenceProjectionPreservesProvenanceWithoutCommandOrInternalFields() {
        JsonObject source = new JsonObject();
        source.addProperty("type", "command.player.submission");
        source.addProperty("effectClassification", "STATE_MUTATION");
        source.addProperty("mode", "DEBUG_PRIVILEGED");
        source.addProperty("evidenceContaminated", true);
        source.addProperty("command", "hidden raw command");
        source.addProperty("uuid", "private player");
        source.add("internal", new JsonObject());
        var projected = WorldDataView.userEvent(source);
        assertEquals("USER", projected.get("dataCategory").getAsString());
        assertEquals("STATE_MUTATION", projected.get("effectClassification").getAsString());
        assertEquals("DEBUG_PRIVILEGED", projected.get("mode").getAsString());
        assertTrue(projected.get("evidenceContaminated").getAsBoolean());
        assertFalse(projected.has("command"));
        assertFalse(projected.has("uuid"));
        assertFalse(projected.has("internal"));
        assertTrue(source.has("command"));
    }

    private static JsonObject node(String key, boolean visible) {
        JsonObject value = new JsonObject();
        value.addProperty("controlKey", key);
        value.addProperty("visible", visible);
        value.addProperty("item", "minecraft:stone");
        value.addProperty("count", 64);
        value.addProperty("x", 12.5);
        value.addProperty("debugLabel", "hidden internal text");
        value.addProperty("uuid", "hidden entity");
        return value;
    }

    @Test
    void userUsesOnlyWhitelistedVisiblePresentationNotRawPlayerOrHiddenCounts() {
        JsonObject tree = new JsonObject();
        tree.addProperty("x", 123.456789);
        tree.addProperty("health", 17.125);
        tree.addProperty("serverTick", 777);
        for (String extent : new String[] {"width", "height", "windowWidth", "windowHeight"})
            tree.addProperty(extent, 720);
        JsonObject presentation = new JsonObject();
        JsonArray children = new JsonArray();
        children.add(node("hud.hotbar.0", true));
        children.add(node("hud.offhand", false));
        children.add(node("hud.effect.0", true));
        children.add(node("hud.scoreboard.secret", true));
        presentation.add("children", children);
        tree.add("presentation", presentation);
        JsonObject value = WorldDataView.userPlayer(tree);
        assertEquals("USER", value.get("dataCategory").getAsString());
        assertFalse(value.has("x"));
        assertFalse(value.has("health"));
        assertFalse(value.has("serverTick"));
        assertEquals(2, value.getAsJsonArray("presentation").size());
        JsonObject slot = value.getAsJsonArray("presentation").get(0).getAsJsonObject();
        assertEquals(12.5, slot.get("x").getAsDouble());
        assertFalse(slot.has("uuid"));
        assertFalse(slot.has("debugLabel"));
        assertTrue(tree.has("health"));
        tree.addProperty("overlayIdentity", 1);
        assertTrue(WorldDataView.userPlayer(tree).getAsJsonArray("presentation").isEmpty());
        tree.remove("overlayIdentity");
        tree.addProperty("windowWidth", 0);
        assertFalse(WorldDataView.userPlayer(tree).get("available").getAsBoolean());
    }

    @Test
    void playerPresentationBoundAndUnavailableAreExplicit() {
        JsonObject tree = new JsonObject();
        assertFalse(WorldDataView.userPlayer(tree).get("available").getAsBoolean());
        for (String extent : new String[] {"width", "height", "windowWidth", "windowHeight"})
            tree.addProperty(extent, 720);
        JsonObject presentation = new JsonObject();
        JsonArray children = new JsonArray();
        for (int i = 0; i < 90; i++) children.add(node("hud.effect." + i, true));
        presentation.add("children", children);
        tree.add("presentation", presentation);
        JsonObject value = WorldDataView.userPlayer(tree);
        assertEquals(64, value.getAsJsonArray("presentation").size());
        assertTrue(value.get("presentationTruncated").getAsBoolean());
    }

    @Test
    void debugPreservesPrecisionAndAuthorityWithoutPretendingPerception() {
        JsonObject source = new JsonObject();
        source.addProperty("x", 1.23456789);
        source.addProperty("perspective", "server_authoritative_live");
        JsonObject result = WorldDataView.debug(source);
        assertEquals(1.23456789, result.get("x").getAsDouble());
        assertEquals("DEBUG", result.get("dataCategory").getAsString());
        assertFalse(result.get("perceptionFiltered").getAsBoolean());
        assertFalse(source.has("dataCategory"));
        assertFalse(WorldDataView.contract().get("consumerIsolationComplete").getAsBoolean());
    }
}
