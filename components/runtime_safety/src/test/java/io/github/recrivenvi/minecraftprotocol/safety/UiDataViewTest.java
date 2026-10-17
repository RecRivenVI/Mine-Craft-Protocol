package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class UiDataViewTest {
    @Test
    void completeTextGuardMetadataRemainsDebugOnly() {
        JsonObject raw =
                object(
                        """
            {"children":[{"nodeId":"text","role":"text_field","visible":true,"value":"published prefix",
            "textReplaceStrategy":"backspace_from_end","valueComplete":true,
            "debugTextGuardAvailable":true,"resourceVersion":{"revision":1}}]}
            """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertFalse(user.toString().contains("debugTextGuardAvailable"));
        assertFalse(user.toString().contains("resourceVersion"));
        JsonObject field = user.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("backspace_from_end", field.get("textReplaceStrategy").getAsString());
        assertTrue(field.get("valueComplete").getAsBoolean());
        assertEquals(
                "published prefix",
                user.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("value")
                        .getAsString());
        JsonObject debug = UiDataView.tree(raw, UiDataView.Category.DEBUG);
        assertTrue(
                debug.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("debugTextGuardAvailable")
                        .getAsBoolean());
    }

    @Test
    void visibleDisabledCrafterCellRemainsActionableWithoutRevealingHiddenCells() {
        JsonObject raw =
                object(
                        """
            {"children":[{"nodeId":"cell","role":"slot","slot":0,"visible":true,
            "active":true,"slotDisabled":true,"actions":["hover","click"]},
            {"nodeId":"hidden","role":"slot","visible":false,"slotDisabled":true}]}
            """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertEquals(1, user.getAsJsonArray("children").size());
        JsonObject cell = user.getAsJsonArray("children").get(0).getAsJsonObject();
        assertTrue(cell.get("slotDisabled").getAsBoolean());
        assertTrue(cell.get("active").getAsBoolean());
        assertTrue(
                cell.getAsJsonArray("actions")
                        .contains(new com.google.gson.JsonPrimitive("click")));
        assertFalse(user.toString().contains("hidden"));
    }

    @Test
    void userItemRevisionsAreOpaqueAndPrivateCrossSlotFingerprintsNeverEscape() {
        JsonObject raw =
                object(
                        """
            {"children":[{"nodeId":"slot","role":"slot","visible":true,"active":true,
            "contentRevision":"opaque-context-token","contentGuardAvailable":true,"contentFingerprint":"PRIVATE"}],
            "carriedStack":{"id":"minecraft:stone","count":8,"empty":false,"contentRevision":"opaque-carried-token",
            "contentGuardAvailable":true,"contentFingerprint":"PRIVATE"}}
            """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertFalse(user.toString().contains("PRIVATE"));
        assertFalse(user.toString().contains("contentFingerprint"));
        assertEquals(
                "opaque-context-token",
                user.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("contentRevision")
                        .getAsString());
        JsonObject action = new JsonObject();
        action.add("resolvedNode", raw.getAsJsonArray("children").get(0).deepCopy());
        assertFalse(UiDataView.actionResult(action).toString().contains("PRIVATE"));
        assertTrue(UiDataView.tree(raw, UiDataView.Category.DEBUG).toString().contains("PRIVATE"));
    }

    @Test
    void spectatorUserProjectionKeepsNativeShortcutsButHidesUnselectedInternalNames() {
        JsonObject raw =
                object(
                        """
          {"uiInputContext":"spectator_menu","spectatorContextKey":"menu:1:0","children":[
          {"nodeId":"a","role":"spectator_item","visible":true,"label":"","debugLabel":"Hidden category name",
           "inputRoute":"spectator_hotbar","inputKey":49,"activationEnabled":false,"boundsSemantics":"display_only","actions":["select"]},
          {"nodeId":"b","visible":false,"debugLabel":"Hidden HUD"}]}
          """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertEquals("spectator_menu", user.get("uiInputContext").getAsString());
        assertEquals(1, user.getAsJsonArray("children").size());
        assertEquals(
                49,
                user.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("inputKey")
                        .getAsInt());
        assertFalse(user.toString().contains("Hidden"));
        assertFalse(user.toString().contains("debugLabel"));
        assertTrue(
                UiDataView.tree(raw, UiDataView.Category.DEBUG)
                        .toString()
                        .contains("Hidden category name"));
    }

    @Test
    void toastProjectionKeepsRenderedPhaseButNeverPendingDebugText() {
        JsonObject raw =
                object(
                        """
            {"children":[],"presentation":{"queuedToastCount":1,"children":[
              {"visible":true,"role":"toast","label":"Header","labelSource":"last_native_toast_render","textPhase":"header","textSupported":true,"debugLabel":"Secret title","actions":[]},
              {"visible":false,"role":"toast","label":"Stale frame","debugLabel":"Pending title"}]}}
            """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        var toast =
                user.getAsJsonObject("presentation")
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject();
        assertEquals("Header", toast.get("label").getAsString());
        assertEquals("header", toast.get("textPhase").getAsString());
        assertTrue(toast.get("textSupported").getAsBoolean());
        for (String hidden :
                new String[] {"Secret", "Pending", "Stale", "queuedToastCount", "debugLabel"})
            assertFalse(user.toString().contains(hidden));
        assertTrue(
                UiDataView.tree(raw, UiDataView.Category.DEBUG)
                        .toString()
                        .contains("Secret title"));
    }

    @Test
    void effectIconsDoNotRevealInvisibleEffectsOrUndisplayedDurationAndAmplifier() {
        JsonObject raw =
                object(
                        """
                {"children":[],"presentation":{"children":[
                  {"visible":true,"role":"status_icon","effect":"minecraft:speed","label":"","actions":[],"duration":1200,"amplifier":4,"iconSource":"last_native_effect_icon_draw","renderPhase":"drawn","opacity":0.5},
                  {"visible":false,"role":"status_icon","effect":"minecraft:strength","renderPhase":"transparent","opacity":0},
                  {"visible":false,"role":"status_icon","effect":"minecraft:invisibility","duration":1200}]}}
                """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        var icons = user.getAsJsonObject("presentation").getAsJsonArray("children");
        assertEquals(1, icons.size());
        assertEquals("minecraft:speed", icons.get(0).getAsJsonObject().get("effect").getAsString());
        assertEquals(
                "last_native_effect_icon_draw",
                icons.get(0).getAsJsonObject().get("iconSource").getAsString());
        assertEquals("drawn", icons.get(0).getAsJsonObject().get("renderPhase").getAsString());
        assertEquals(0.5, icons.get(0).getAsJsonObject().get("opacity").getAsDouble());
        for (String hidden : new String[] {"duration", "amplifier", "invisibility", "strength"})
            assertFalse(user.toString().contains(hidden));
        assertTrue(
                UiDataView.tree(raw, UiDataView.Category.DEBUG)
                        .toString()
                        .contains("invisibility"));
    }

    @Test
    void hudProjectionRetainsVisibleIconProgressAndFormattedTextWithoutHiddenData() {
        JsonObject raw =
                object(
                        """
                {"children":[],"presentation":{"children":[
                {"visible":true,"role":"hud_item","item":"minecraft:stone","count":12,"selected":true,"label":"","actions":[],"nbt":"secret"},
                {"visible":true,"role":"progress_bar","value":0.25,"valueMinimum":0,"valueMaximum":1,"label":"Boss","actions":[],"serverMaximum":100},
                {"visible":true,"role":"hud_text","label":"Entry","actions":[],"rawScore":999},
                {"visible":false,"role":"hud_item","item":"hidden:item"}]}}
                """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        var nodes = user.getAsJsonObject("presentation").getAsJsonArray("children");
        assertEquals(3, nodes.size());
        assertEquals(12, nodes.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(0.25, nodes.get(1).getAsJsonObject().get("value").getAsDouble());
        assertEquals("Entry", nodes.get(2).getAsJsonObject().get("label").getAsString());
        for (String hidden : new String[] {"secret", "serverMaximum", "rawScore", "hidden:item"})
            assertFalse(user.toString().contains(hidden));
        assertTrue(UiDataView.tree(raw, UiDataView.Category.DEBUG).toString().contains("rawScore"));
    }

    @Test
    void presentationFiltersHiddenHudAndQueuedToastStateButKeepsDebugContext() {
        JsonObject raw =
                object(
                        """
            {"overlayIdentity":0,"uiContextId":"ui-session:epoch","children":[],"presentation":{
              "coverage":"partial_typed_presentation","queuedToastCount":3,
              "children":[{"nodeId":"title","visible":true,"label":"Visible","actions":[]},
                          {"nodeId":"hidden","visible":false,"label":"Hidden"}]}}
            """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertFalse(user.has("uiContextId"));
        assertFalse(user.getAsJsonObject("presentation").has("queuedToastCount"));
        assertEquals(1, user.getAsJsonObject("presentation").getAsJsonArray("children").size());
        JsonObject debug = UiDataView.tree(raw, UiDataView.Category.DEBUG);
        assertTrue(debug.has("uiContextId"));
        assertEquals(3, debug.getAsJsonObject("presentation").get("queuedToastCount").getAsInt());
        assertEquals(2, debug.getAsJsonObject("presentation").getAsJsonArray("children").size());
        raw.addProperty("overlayIdentity", 7);
        assertEquals(
                0,
                UiDataView.tree(raw, UiDataView.Category.USER)
                        .getAsJsonObject("presentation")
                        .getAsJsonArray("children")
                        .size());
    }

    @Test
    void playerListUserReadKeepsOnlyVisibleDisplayTextNotProfileOrLatency() {
        JsonObject raw =
                object(
                        """
            {"children":[],"presentation":{"children":[
              {"nodeId":"hud.playerlist.row.0","controlKey":"hud.playerlist.row.0","visible":true,
               "label":"Public display name","role":"hud_text","active":false,"actions":[],
               "profile":"private-profile","latency":123,"score":99},
              {"nodeId":"hud.playerlist.row.1","visible":false,"label":"Hidden player"}]}}
            """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        JsonArray rows = user.getAsJsonObject("presentation").getAsJsonArray("children");
        assertEquals(1, rows.size());
        assertEquals(
                "Public display name", rows.get(0).getAsJsonObject().get("label").getAsString());
        assertFalse(user.toString().contains("private-profile"));
        assertFalse(user.toString().contains("latency"));
        assertFalse(user.toString().contains("score"));
        assertFalse(user.toString().contains("Hidden player"));
        assertTrue(
                UiDataView.tree(raw, UiDataView.Category.DEBUG)
                        .toString()
                        .contains("Hidden player"));
    }

    private static JsonObject object(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    @Test
    void userReadExcludesHiddenOccludedAncestorContentAndUnknownInternalFields() {
        JsonObject raw =
                object(
                        """
                {"screenClass":"OptionsScreen","overlayIdentity":0,"serverSecret":"secret",
                 "children":[
                  {"nodeId":"visible","visible":true,"active":false,"label":"Disabled","entryCount":999,"private":"secret","children":[]},
                  {"nodeId":"hidden","visible":false,"label":"secret"},
                  {"nodeId":"blocked","visible":true,"occluded":true,"label":"secret"},
                  {"nodeId":"parent","visible":false,"children":[{"nodeId":"child","visible":true,"label":"secret"}]},
                  {"nodeId":"unknown","label":"secret"}]}
                """);
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertEquals("USER", user.get("dataCategory").getAsString());
        assertEquals(1, user.get("treeNodeCount").getAsInt());
        assertEquals(
                "Disabled",
                user.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("label")
                        .getAsString());
        assertFalse(user.toString().contains("secret"));
        assertFalse(user.toString().contains("entryCount"));
        assertEquals(5, raw.getAsJsonArray("children").size());
    }

    @Test
    void debugReadRetainsBoundedInternalDataWithoutClaimingAllGameData() {
        JsonObject raw =
                object(
                        "{\"children\":[{\"visible\":false,\"label\":\"hidden\"}],\"entryCount\":99}");
        JsonObject debug = UiDataView.tree(raw, UiDataView.Category.DEBUG);
        assertEquals("DEBUG", debug.get("dataCategory").getAsString());
        assertEquals(99, debug.get("entryCount").getAsInt());
        assertEquals(1, debug.getAsJsonObject("debugFacts").get("capturedNodeCount").getAsInt());
        assertFalse(debug.getAsJsonObject("debugFacts").get("completeGameData").getAsBoolean());
    }

    @Test
    void overlayBlocksUnderlyingControlsAndEditorContent() {
        JsonObject raw =
                object(
                        "{\"overlayIdentity\":1,\"screenData\":{\"pageText\":\"hidden\"},\"children\":[{\"visible\":true,\"label\":\"hidden\"}]}");
        JsonObject user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertEquals(0, user.get("treeNodeCount").getAsInt());
        assertFalse(user.toString().contains("hidden"));
    }

    @Test
    void tooltipRequiresHoverAndActionResponsesCannotLeakHiddenNodes() {
        JsonObject node =
                object(
                        "{\"visible\":true,\"label\":\"Tooltip\",\"labelSource\":\"native_tooltip\",\"hovered\":false}");
        assertFalse(UiDataView.userNode(node).has("label"));
        node.addProperty("hovered", true);
        assertEquals("Tooltip", UiDataView.userNode(node).get("label").getAsString());
        JsonObject action =
                object("{\"resolvedNode\":{\"visible\":false,\"label\":\"secret\"},\"x\":10.25}");
        JsonObject user = UiDataView.actionResult(action);
        assertFalse(user.has("resolvedNode"));
        assertEquals(10.25, user.get("x").getAsDouble());
        assertEquals("USER", user.get("dataCategory").getAsString());
    }

    @Test
    void categoriesAreExplicitAndContractDoesNotClaimMilestoneAcceptance() {
        assertEquals(UiDataView.Category.USER, UiDataView.Category.parse(null));
        assertEquals(UiDataView.Category.DEBUG, UiDataView.Category.parse("DEBUG"));
        assertThrows(IllegalArgumentException.class, () -> UiDataView.Category.parse("debug"));
        assertFalse(UiDataView.contract().get("milestoneComplete").getAsBoolean());
        for (UiDataView.Category category : UiDataView.Category.values())
            assertTrue(UiDataView.allowsRead("READ", category));
        assertTrue(UiDataView.allowsRead("TAKEOVER", UiDataView.Category.USER));
        assertFalse(UiDataView.allowsRead("TAKEOVER", UiDataView.Category.DEBUG));
        assertTrue(UiDataView.allowsRead("OPERATE", UiDataView.Category.DEBUG));
        assertFalse(UiDataView.allowsRead("OPERATE", UiDataView.Category.USER));
        assertFalse(UiDataView.allowsRead("unknown", UiDataView.Category.USER));
    }

    @Test
    void hudSamplingStatusAndVisibleGlyphMetadataSurviveWithoutPrivateStats() {
        var captured =
                object(
                        "{\"presentation\":{\"hudIconSampleStatus\":\"CURRENT\",\"rawHealth\":13.8,\"children\":[{\"visible\":true,\"active\":false,\"role\":\"status_icon\",\"controlKey\":\"hud.vitals.health.0\",\"resourceId\":\"minecraft:hud/heart/full\",\"health\":13.8,\"entryIndex\":0,\"actions\":[]},{\"visible\":false,\"label\":\"hidden glyph\"}]}}");
        var view =
                UiDataView.tree(captured, UiDataView.Category.USER).getAsJsonObject("presentation");
        assertEquals("CURRENT", view.get("hudIconSampleStatus").getAsString());
        assertFalse(view.has("rawHealth"));
        assertEquals(1, view.getAsJsonArray("children").size());
        var icon = view.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("minecraft:hud/heart/full", icon.get("resourceId").getAsString());
        assertEquals(0, icon.get("entryIndex").getAsInt());
        assertFalse(icon.has("health"));
    }
}
