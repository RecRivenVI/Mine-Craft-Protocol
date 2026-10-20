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

    @Test
    void titleLifecycleRequiresItsOwnCompleteGuardAndDoesNotGrantInput() {
        JsonObject node = node();
        node.addProperty("debugTitleGuardAvailable", false);
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.HUD_TITLE_LIFECYCLE), null);
        assertEquals(
                0,
                node.getAsJsonObject("debugMutation").getAsJsonArray("supportedOperations").size());
        assertEquals(
                "UI_CONTENT_GUARD_UNAVAILABLE",
                node.getAsJsonObject("debugMutation")
                        .getAsJsonObject("blockedOperations")
                        .get("ui.hud.title.clear")
                        .getAsString());
        node.addProperty("debugTitleGuardAvailable", true);
        node.add(
                "debugTitleTimes",
                JsonParser.parseString(
                        "{\"fadeInTicks\":10,\"stayTicks\":70,\"fadeOutTicks\":20}"));
        node.addProperty("debugTitleCleared", false);
        node.addProperty("debugTitleRemainingTicks", 87);
        UiMutationSupport.describe(node, Set.of(UiMutationSupport.Facet.HUD_TITLE_LIFECYCLE), null);
        var descriptor = node.getAsJsonObject("debugMutation");
        var operations = descriptor.getAsJsonObject("operations");
        assertEquals(2, operations.size());
        assertEquals(
                "native_hud_title_timing_and_remaining_reset",
                operations
                        .getAsJsonObject("ui.hud.title.times.set")
                        .get("sideEffects")
                        .getAsString());
        assertEquals(
                12000,
                operations
                        .getAsJsonObject("ui.hud.title.times.set")
                        .get("maximumFieldTicks")
                        .getAsInt());
        assertTrue(
                operations.getAsJsonObject("ui.hud.title.clear").get("onlyValue").getAsBoolean());
        assertFalse(descriptor.get("requiresInputLease").getAsBoolean());
        assertFalse(descriptor.get("grantsPermission").getAsBoolean());
        var user = UiDataView.userNode(node);
        for (String field :
                Set.of(
                        "debugTitleGuardAvailable",
                        "debugTitleTimes",
                        "debugTitleCleared",
                        "debugTitleRemainingTicks",
                        "resourceVersion",
                        "debugMutation")) assertFalse(user.has(field), field);
    }

    @Test
    void localSelectionDescribesItsActualNativeOwnerWithoutClaimingActivation() {
        for (var facet :
                Set.of(
                        UiMutationSupport.Facet.LIST_SELECTION,
                        UiMutationSupport.Facet.SPECTATOR_SELECTION)) {
            JsonObject node = node();
            node.addProperty("debugSelectionGuardAvailable", true);
            node.addProperty("debugSelectionIndex", -1);
            node.add(
                    "debugSelectionRange", JsonParser.parseString("{\"minimum\":0,\"maximum\":7}"));
            UiMutationSupport.describe(node, Set.of(facet), null);
            var operation =
                    node.getAsJsonObject("debugMutation")
                            .getAsJsonObject("operations")
                            .getAsJsonObject("ui.list.selection.set");
            assertEquals("integer", operation.get("valueType").getAsString());
            assertEquals("native_entry_index", operation.get("unit").getAsString());
            assertEquals(0, operation.get("minimum").getAsInt());
            assertEquals(7, operation.get("maximum").getAsInt());
            assertEquals(
                    facet == UiMutationSupport.Facet.SPECTATOR_SELECTION
                            ? "native_local_spectator_highlight_not_activation"
                            : "native_local_suggestion_selection_not_use_or_submit",
                    operation.get("sideEffects").getAsString());
            assertFalse(UiDataView.userNode(node).has("debugSelectionIndex"));
            node.addProperty("debugSelectionGuardAvailable", false);
            UiMutationSupport.describe(node, Set.of(facet), null);
            assertEquals(
                    0,
                    node.getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .size());
        }
    }

    private static JsonObject node() {
        return JsonParser.parseString(
                        "{\"nodeId\":\"native\",\"role\":\"text_field\","
                                + "\"visible\":true,\"active\":true,\"value\":\"draft\","
                                + "\"resourceVersion\":{\"revision\":1}}")
                .getAsJsonObject();
    }
}
