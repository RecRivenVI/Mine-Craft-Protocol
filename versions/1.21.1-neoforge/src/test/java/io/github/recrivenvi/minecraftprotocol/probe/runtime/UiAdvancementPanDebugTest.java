package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiAdvancementPanDebugTest {
    @Test
    void absoluteCoordinatesKeepFractionsAndNonPannablePositiveCenter() {
        JsonObject range = state(true).getAsJsonObject("range");
        var pair = JsonParser.parseString("{\"x\":-12.125,\"y\":17.5}");
        assertEquals(pair, UiAdvancementPanDebug.validate(pair, range));
        assertEquals(-12.125, UiAdvancementPanDebug.validate(pair, range).get("x").getAsDouble());
        assertEquals(17.5, UiAdvancementPanDebug.validate(pair, range).get("y").getAsDouble());
    }

    @Test
    void invalidShapesNonFiniteAndOutOfRangeValuesNeverBecomePans() {
        for (String value :
                List.of(
                        "null",
                        "false",
                        "[]",
                        "0.5",
                        "{}",
                        "{\"x\":0}",
                        "{\"x\":0,\"y\":17.5,\"z\":0}",
                        "{\"x\":null,\"y\":17.5}",
                        "{\"x\":\"-12\",\"y\":17.5}",
                        "{\"x\":false,\"y\":17.5}",
                        "{\"x\":1e999,\"y\":17.5}",
                        "{\"x\":-101,\"y\":17.5}",
                        "{\"x\":1,\"y\":17.5}",
                        "{\"x\":0,\"y\":0}")) {
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiAdvancementPanDebug.validate(
                                                    JsonParser.parseString(value),
                                                    state(true).getAsJsonObject("range")))
                            .code(),
                    value);
        }
    }

    @Test
    void nativeCenteringMustCompleteBeforeAUsableVersionIsPublished() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            JsonObject tree = tree();
            JsonObject node = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(false));
            assertFalse(node.get("debugPanReady").getAsBoolean());
            assertFalse(node.has("resourceVersion"));
            assertEquals(
                    0,
                    node.getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .size());
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(true));
            assertTrue(node.get("debugPanReady").getAsBoolean());
            assertTrue(node.has("resourceVersion"));
            assertEquals(
                    "ui.canvas.pan.set",
                    node.getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .get(0)
                            .getAsString());
        }
    }

    @Test
    void completeNativeValueRangeSelectedTabAndGuiContextBindTheVersion() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            JsonObject tree = tree();
            JsonObject node = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(true));
            var before = node.get("resourceVersion").deepCopy();
            node.getAsJsonObject("debugMutation").addProperty("untrusted_extra", true);
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(true));
            assertEquals(
                    before,
                    node.get("resourceVersion"),
                    "Descriptor must not feed back into its token");
            for (String changed : List.of("value", "range", "tabOwner", "widgetCount")) {
                var state = state(true);
                switch (changed) {
                    case "value" -> state.getAsJsonObject("value").addProperty("x", -20.125);
                    case "range" ->
                            state.getAsJsonObject("range")
                                    .getAsJsonObject("x")
                                    .addProperty("minimum", -200);
                    case "tabOwner" -> state.addProperty("tabOwner", 99);
                    case "widgetCount" -> state.addProperty("widgetCount", 9);
                }
                UiAdvancementPanDebug.describeState(tree, node, revisions, state);
                assertNotEquals(before, node.get("resourceVersion"), changed);
            }
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(true));
            var current = node.get("resourceVersion").deepCopy();
            tree.addProperty("guiScale", 3);
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(true));
            assertNotEquals(current, node.get("resourceVersion"));
        }
    }

    @Test
    void nativeRangeMustBeFiniteAndOrderedEvenForATypedPair() {
        var range = state(true).getAsJsonObject("range");
        range.getAsJsonObject("x").addProperty("minimum", 4);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> UiAdvancementPanDebug.validate(UiAdvancementPanDebug.pair(0, 17.5), range));
        range.getAsJsonObject("x").addProperty("minimum", Double.NEGATIVE_INFINITY);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> UiAdvancementPanDebug.validate(UiAdvancementPanDebug.pair(0, 17.5), range));
    }

    @Test
    void productionUserProjectionKeepsVisibleOffsetsButNotTypedPanState() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            JsonObject tree = tree();
            JsonObject node = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            UiAdvancementPanDebug.describeState(tree, node, revisions, state(true));
            var publicNode =
                    UiDataView.tree(tree, UiDataView.Category.USER)
                            .getAsJsonArray("children")
                            .get(0)
                            .getAsJsonObject();
            for (String field :
                    List.of(
                            "debugPanValue",
                            "debugPanRange",
                            "debugPanReady",
                            "debugMutation",
                            "resourceVersion")) assertFalse(publicNode.has(field), field);
            assertEquals(-20, publicNode.get("scrollX").getAsDouble());
            assertEquals(17.5, publicNode.get("scrollY").getAsDouble());
        }
    }

    @Test
    void missingNativeOwnerCannotBeReplacedWithAnArbitraryRenderedRegion() {
        var node = tree().getAsJsonArray("children").get(0).getAsJsonObject();
        assertNull(UiAdvancementPanDebug.target(null, node, System::identityHashCode));
        assertEquals(
                "UI_DEBUG_UNSUPPORTED",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        UiAdvancementPanDebug.prepare(
                                                null,
                                                UiAdvancementPanDebug.pair(0, 0),
                                                System::identityHashCode))
                        .code());
    }

    private static JsonObject tree() {
        return JsonParser.parseString(
                        """
                {"screenIdentity":7,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
                  {"nodeId":"native_canvas","elementIdentity":10,"role":"canvas","class":"native.AdvancementTab","controlKey":"advancement.canvas","visible":true,"active":true,"x":9,"y":18,"width":234,"height":113,"scrollX":-20,"scrollY":17.5}
                ]}
                """)
                .getAsJsonObject();
    }

    private static JsonObject state(boolean ready) {
        var state =
                JsonParser.parseString(
                                """
                {"value":{"x":-20,"y":17.5},"range":{"x":{"enabled":true,"minimum":-100,"maximum":0},"y":{"enabled":false,"minimum":17.5,"maximum":17.5},"unit":"native_gui_pixels"},"tabOwner":10,"screenOwner":7,"root":"minecraft:story/root","widgetCollection":11,"widgetCount":8,"minX":0,"maxX":334,"minY":0,"maxY":78}
                """)
                        .getAsJsonObject();
        state.addProperty("ready", ready);
        return state;
    }
}
