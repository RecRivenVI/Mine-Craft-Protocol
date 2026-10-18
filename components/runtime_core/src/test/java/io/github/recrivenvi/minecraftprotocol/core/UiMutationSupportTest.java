package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UiMutationSupportTest {
    @Test
    void actualTypedFacetsDescribeOperationsAndDoNotGrantPermission() {
        JsonObject node = node();
        UiMutationSupport.describe(
                node,
                EnumSet.of(
                        UiMutationSupport.Facet.WIDGET_FLAGS, UiMutationSupport.Facet.EDITOR_TEXT),
                null);
        var support = node.getAsJsonObject("debugMutation");
        assertEquals(3, support.getAsJsonArray("supportedOperations").size());
        assertTrue(support.getAsJsonObject("operations").has("ui.text.set"));
        assertFalse(support.getAsJsonObject("operations").has("ui.slider.set"));
        assertFalse(support.get("grantsPermission").getAsBoolean());
        assertFalse(support.get("requiresInputLease").getAsBoolean());
        assertTrue(support.get("requiresUiArm").getAsBoolean());
        assertEquals(0, support.get("inputDispatchCount").getAsInt());
    }

    @Test
    void textAndToastBudgetFailureNeverAdvertiseAnExecutableSetter() {
        for (String guard : Set.of("debugTextGuardAvailable", "debugToastGuardAvailable")) {
            JsonObject node = node();
            node.addProperty(guard, false);
            UiMutationSupport.describe(
                    node,
                    EnumSet.of(
                            UiMutationSupport.Facet.EDITOR_TEXT,
                            UiMutationSupport.Facet.WIDGET_FLAGS),
                    null);
            var support = node.getAsJsonObject("debugMutation");
            assertEquals(0, support.getAsJsonArray("supportedOperations").size());
            assertEquals(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    support.getAsJsonObject("blockedOperations").get("ui.text.set").getAsString());
        }
    }

    @Test
    void missingVersionAndUnapprovedRegionAreNotFalselySupported() {
        JsonObject node = node();
        node.remove("resourceVersion");
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.HUD_TEXT), null);
        assertEquals(
                "RESOURCE_VERSION_UNAVAILABLE",
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("blockedOperations")
                        .get("ui.text.set")
                        .getAsString());
        UiMutationSupport.describe(node, Set.of(), "SEMANTIC_INPUT_IS_NOT_A_TYPED_SETTER");
        var support = node.getAsJsonObject("debugMutation");
        assertEquals(0, support.getAsJsonArray("supportedOperations").size());
        assertEquals(
                "SEMANTIC_INPUT_IS_NOT_A_TYPED_SETTER", support.get("limitation").getAsString());
        assertFalse(support.has("readOnly"));
    }

    @Test
    void scrollUnitsDisabledRangeAndOneWayHideAreExplicit() {
        JsonObject node = node();
        node.addProperty("active", false);
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.NORMALIZED_SCROLL), null);
        var scroll =
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("operations")
                        .getAsJsonObject("ui.scroll.set");
        assertEquals("normalized", scroll.get("unit").getAsString());
        assertEquals(0, scroll.get("maximum").getAsInt());
        node.addProperty("maxScrollAmount", 123.5);
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.LIST_SCROLL), null);
        scroll =
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("operations")
                        .getAsJsonObject("ui.scroll.set");
        assertEquals("native_pixels", scroll.get("unit").getAsString());
        assertEquals(123.5, scroll.get("maximum").getAsDouble());
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.TUTORIAL_TOAST_HIDE), null);
        assertTrue(
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("operations")
                        .getAsJsonObject("ui.toast.hide")
                        .get("onlyValue")
                        .getAsBoolean());
    }

    @Test
    void productionUserTreeResolveAndActionProjectionStripCapabilityMetadata() {
        JsonObject node = node();
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.EDITOR_TEXT), null);
        JsonObject tree = JsonParser.parseString("{\"children\":[]}").getAsJsonObject();
        tree.getAsJsonArray("children").add(node);
        JsonObject user = UiDataView.tree(tree, UiDataView.Category.USER);
        assertFalse(user.toString().contains("debugMutation"));
        assertFalse(UiDataView.userNode(node).has("debugMutation"));
        JsonObject action = new JsonObject();
        action.add("resolvedNode", node);
        assertFalse(UiDataView.actionResult(action).toString().contains("debugMutation"));
        assertTrue(
                UiDataView.tree(tree, UiDataView.Category.DEBUG)
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .has("debugMutation"));
    }

    @Test
    void canvasPanDescribesNativeAbsoluteTwoAxisBoundsOnlyWhenReadyAndVersioned() {
        JsonObject node = node();
        node.addProperty("debugPanReady", false);
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.CANVAS_PAN), null);
        assertEquals(
                "UI_CONTEXT_NOT_READY",
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("blockedOperations")
                        .get("ui.canvas.pan.set")
                        .getAsString());
        node.addProperty("debugPanReady", true);
        node.add("debugPanValue", JsonParser.parseString("{\"x\":-30.5,\"y\":12.25}"));
        node.add(
                "debugPanRange",
                JsonParser.parseString(
                        "{\"unit\":\"native_gui_pixels\",\"x\":{\"enabled\":true,\"minimum\":-100,\"maximum\":0},\"y\":{\"enabled\":false,\"minimum\":12.25,\"maximum\":12.25}}"));
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.CANVAS_PAN), null);
        var spec =
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("operations")
                        .getAsJsonObject("ui.canvas.pan.set");
        assertEquals("explicit_finite_x_and_y", spec.get("requiredShape").getAsString());
        assertEquals("native_gui_pixels", spec.get("unit").getAsString());
        assertEquals(node.get("debugPanRange"), spec.get("range"));
        spec.getAsJsonObject("range").getAsJsonObject("y").addProperty("minimum", 0);
        assertEquals(
                12.25,
                node.getAsJsonObject("debugPanRange")
                        .getAsJsonObject("y")
                        .get("minimum")
                        .getAsDouble());
        node.remove("resourceVersion");
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.CANVAS_PAN), null);
        assertEquals(
                0,
                node.getAsJsonObject("debugMutation").getAsJsonArray("supportedOperations").size());
    }

    @Test
    void productionUserProjectionDoesNotLeakInternalCanvasStateOrBounds() {
        JsonObject node = node();
        node.addProperty("debugPanReady", true);
        node.add("debugPanValue", JsonParser.parseString("{\"x\":-0.5,\"y\":12.25}"));
        node.add(
                "debugPanRange",
                JsonParser.parseString(
                        "{\"x\":{\"enabled\":true,\"minimum\":-100,\"maximum\":0}}"));
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.CANVAS_PAN), null);
        var user = UiDataView.userNode(node);
        for (String field :
                Set.of(
                        "debugPanReady",
                        "debugPanValue",
                        "debugPanRange",
                        "debugMutation",
                        "resourceVersion")) assertFalse(user.has(field));
        JsonObject action = new JsonObject();
        action.add("resolvedNode", node);
        assertFalse(UiDataView.actionResult(action).toString().contains("debugPan"));
    }

    private static JsonObject node() {
        return JsonParser.parseString(
                        "{\"nodeId\":\"native\",\"role\":\"text_field\","
                                + "\"visible\":true,\"active\":true,\"value\":\"draft\","
                                + "\"resourceVersion\":{\"revision\":1}}")
                .getAsJsonObject();
    }
}
