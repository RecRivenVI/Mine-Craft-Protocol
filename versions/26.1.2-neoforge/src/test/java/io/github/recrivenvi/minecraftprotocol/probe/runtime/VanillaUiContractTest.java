package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.AutomationEngine;
import io.github.recrivenvi.minecraftprotocol.core.ConditionEngine;
import io.github.recrivenvi.minecraftprotocol.core.ProbeService;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class VanillaUiContractTest {
    @Test
    void debugCategoryCannotBeDispatchedAsUserInput() throws Exception {
        Ui ui = new Ui();
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            ExecutionException failure =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.uiAction(
                                                    object(
                                                            "{\"category\":\"DEBUG\",\"action\":\"click\",\"selector\":{\"nodeId\":\"input\"}}"),
                                                    () -> {})
                                            .get(2, TimeUnit.SECONDS));
            assertEquals(
                    "USER_UI_OPERATION_REQUIRED",
                    ((ProtocolState.ProtocolException) failure.getCause()).code());
            assertEquals(0, ui.presses);
            assertTrue(ui.characters.isEmpty());
        }
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void productionCaptureTraversesNativeChildrenAndStopsCycles() {
        Button button =
                Button.builder(Component.literal("Nested"), ignored -> {})
                        .bounds(12, 20, 80, 20)
                        .build();
        var container =
                new AbstractContainerEventHandler() {
                    @Override
                    public List<? extends GuiEventListener> children() {
                        return List.of(button, this);
                    }

                    @Override
                    public ScreenRectangle getRectangle() {
                        return new ScreenRectangle(0, 0, 200, 200);
                    }
                };
        JsonObject root = new JsonObject();
        root.add(
                "children",
                new VanillaUiTree(1, System::identityHashCode)
                        .captureChildren(List.of(container), new ScreenRectangle(0, 0, 320, 240)));
        assertEquals(1, root.getAsJsonArray("children").size());
        assertEquals(
                1,
                root.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("children")
                        .size());
        JsonObject resolved =
                AutomationEngine.resolveTree(root, object("{\"label\":\"Nested\"}"))
                        .getAsJsonObject("node");
        assertEquals("button", resolved.get("role").getAsString());
        assertEquals(52, resolved.get("interactionX").getAsDouble());
    }

    @Test
    void nestedRowsResolveByStableWorldIdentityAndRespectVisibility() {
        JsonObject tree =
                object(
                        """
            {"children":[{"role":"list","children":[
            {"nodeId":"row1","role":"list_item","worldId":"a","label":"Same","visible":true,"x":10,"y":20,"width":100,"height":30},
            {"nodeId":"row2","role":"list_item","worldId":"b","label":"Same","visible":false,"x":10,"y":200,"width":100,"height":30}
            ]}]}
            """);
        assertEquals(
                "row1",
                AutomationEngine.resolveTree(tree, object("{\"worldId\":\"a\"}"))
                        .getAsJsonObject("node")
                        .get("nodeId")
                        .getAsString());
        assertTrue(ConditionEngine.findMatches(tree, object("{\"worldId\":\"b\"}")).isEmpty());
        assertEquals(
                2,
                ConditionEngine.findMatches(
                                tree, object("{\"role\":\"list_item\",\"visibleOnly\":false}"))
                        .size());
    }

    @Test
    void rowHitBoundsAreClippedAndNeverUseInvisibleCenter() {
        JsonObject node = new JsonObject();
        VanillaUiTree.bounds(
                node,
                new ScreenRectangle(10, 90, 80, 40),
                new ScreenRectangle(0, 100, 200, 100),
                true,
                true);
        assertTrue(node.get("clipped").getAsBoolean());
        assertEquals(100, node.get("y").getAsInt());
        assertEquals(30, node.get("height").getAsInt());
        assertEquals(115, node.get("interactionY").getAsDouble());
        VanillaUiTree.bounds(
                node,
                new ScreenRectangle(10, 20, 80, 40),
                new ScreenRectangle(0, 100, 200, 100),
                true,
                true);
        assertFalse(node.get("visible").getAsBoolean());
    }

    private static final class Ui {
        JsonObject tree =
                object(
                        """
            {"screenIdentity":1,"screenRevision":1,"width":640,"height":360,"children":[
            {"nodeId":"input","elementIdentity":2,"class":"EditBox","role":"text_field","focused":true,"active":true,"visible":true,
             "x":20,"y":20,"width":200,"height":20,"value":"Original","actions":["click","type_text","hover"]},
            {"nodeId":"slider","elementIdentity":3,"class":"Slider","role":"slider","active":true,"visible":true,
             "x":20,"y":60,"width":108,"height":20,"actions":["click","set_value","hover"]}]}
            """);
        List<Integer> characters = new ArrayList<>();
        int presses;
        int navigationRemaining;
        final List<Integer> keyPresses = new ArrayList<>();
        double x, y;
        boolean replaceScreen, textSelected;

        ProbeService service() {
            return (ProbeService)
                    Proxy.newProxyInstance(
                            ProbeService.class.getClassLoader(),
                            new Class<?>[] {ProbeService.class},
                            (proxy, method, args) -> {
                                if (method.getReturnType() == void.class) return null;
                                JsonObject result = new JsonObject();
                                switch (method.getName()) {
                                    case "uiTree", "debugUiTree", "pointerState" -> {
                                        result = tree.deepCopy();
                                        result.addProperty("x", x);
                                        result.addProperty("y", y);
                                    }
                                    case "mouseMoveGuarded" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[2], tree);
                                        x = (double) args[0];
                                        y = (double) args[1];
                                    }
                                    case "mouseButtonGuarded" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[3], tree);
                                        if ((int) args[1] == 1) presses++;
                                    }
                                    case "keyGuarded" -> {
                                        if ((int) args[2] != 0) {
                                            AutomationEngine.validatePointerGuard(
                                                    (JsonObject) args[4], tree);
                                            keyPresses.add((int) args[0]);
                                            JsonObject field =
                                                    tree.getAsJsonArray("children")
                                                            .get(0)
                                                            .getAsJsonObject();
                                            if ((int) args[0] == 65 && ((int) args[3] & 2) != 0)
                                                textSelected = true;
                                            if ((int) args[0] == 259 && field.has("value")) {
                                                String value = field.get("value").getAsString();
                                                field.addProperty(
                                                        "value",
                                                        textSelected || value.isEmpty()
                                                                ? ""
                                                                : value.substring(
                                                                        0,
                                                                        value.offsetByCodePoints(
                                                                                value.length(),
                                                                                -1)));
                                                textSelected = false;
                                            }
                                            if ((int) args[0] == 257 && field.has("value"))
                                                field.addProperty(
                                                        "value",
                                                        field.get("value").getAsString() + "\n");
                                            if ((int) args[0] == 264 || (int) args[0] == 265)
                                                if (--navigationRemaining == 0)
                                                    tree.getAsJsonArray("children")
                                                            .get(0)
                                                            .getAsJsonObject()
                                                            .addProperty("focused", true);
                                        }
                                    }
                                    case "character" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[1], tree);
                                        characters.add((int) args[0]);
                                        JsonObject field =
                                                tree.getAsJsonArray("children")
                                                        .get(0)
                                                        .getAsJsonObject();
                                        field.addProperty(
                                                "value",
                                                field.get("value").getAsString()
                                                        + new String(
                                                                Character.toChars((int) args[0])));
                                        if (replaceScreen) tree.addProperty("screenIdentity", 2);
                                    }
                                    default -> {}
                                }
                                return CompletableFuture.completedFuture(result);
                            });
        }
    }

    @Test
    void spectatorSelectionIsKeyboardOnlyAndRepeatedClickCannotActivate() throws Exception {
        Ui ui = new Ui();
        ui.tree =
                object(
                        """
            {"screenClass":"","uiInputContext":"spectator_menu","spectatorContextKey":"menu:1:0",
            "children":[{"nodeId":"spectator","role":"spectator_item","inputRoute":"spectator_hotbar",
            "inputKey":49,"selected":false,"activationEnabled":true,"active":true,"visible":true,
            "boundsAvailable":true,"boundsSemantics":"display_only","x":10,"y":10,"width":16,"height":16,
            "actions":["select","click"]}]}
            """);
        JsonObject item = ui.tree.getAsJsonArray("children").get(0).getAsJsonObject();
        assertFalse(
                AutomationEngine.resolveTree(ui.tree, object("{\"nodeId\":\"spectator\"}"))
                        .has("interactionPoint"));
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            JsonObject click =
                    object("{\"action\":\"click\",\"selector\":{\"nodeId\":\"spectator\"}}");
            engine.uiAction(click, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(List.of(49), ui.keyPresses);
            assertEquals(0, ui.presses);
            assertEquals(0, ui.x);
            item.addProperty("selected", true);
            item.getAsJsonArray("actions").add("activate");
            JsonObject repeated = engine.uiAction(click, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(0, repeated.get("inputDispatchCount").getAsInt());
            assertEquals(List.of(49), ui.keyPresses);
            engine.uiAction(
                            object(
                                    "{\"action\":\"activate\",\"selector\":{\"nodeId\":\"spectator\"}}"),
                            () -> {})
                    .get(3, TimeUnit.SECONDS);
            assertEquals(List.of(49, 49), ui.keyPresses);
            item.addProperty("activationEnabled", false);
            ExecutionException denied =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.uiAction(
                                                    object(
                                                            "{\"action\":\"activate\",\"selector\":{\"nodeId\":\"spectator\"}}"),
                                                    () -> {})
                                            .get(3, TimeUnit.SECONDS));
            assertEquals(
                    "UI_NODE_NOT_ACTIONABLE",
                    ((ProtocolState.ProtocolException) denied.getCause()).code());
            assertEquals(List.of(49, 49), ui.keyPresses);
        }
    }

    @Test
    void spectatorSelectionBindingAndContextChangesInvalidateTheGuard() {
        JsonObject original =
                object(
                        "{\"nodeId\":\"a\",\"inputRoute\":\"spectator_hotbar\",\"inputKey\":49,\"selected\":false,\"visible\":true,\"active\":true,\"actions\":[\"select\"]}");
        JsonObject tree = object("{\"spectatorContextKey\":\"menu:1:0\",\"children\":[]}");
        tree.getAsJsonArray("children").add(original.deepCopy());
        JsonObject guard =
                object(
                        "{\"frame\":{\"spectatorContextKey\":\"menu:1:0\"},\"selector\":{\"nodeId\":\"a\"},\"action\":\"select\"}");
        guard.add("node", original);
        for (String field : new String[] {"inputKey", "selected"}) {
            JsonObject changed = tree.deepCopy();
            if (field.equals("inputKey"))
                changed.getAsJsonArray("children").get(0).getAsJsonObject().addProperty(field, 50);
            else
                changed.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .addProperty(field, true);
            assertEquals(
                    "UI_TARGET_INVALIDATED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> AutomationEngine.validatePointerGuard(guard, changed))
                            .code());
        }
        tree.addProperty("spectatorContextKey", "menu:2:0");
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> AutomationEngine.validatePointerGuard(guard, tree));
    }

    @Test
    void unicodeTextUsesCharacterPathAndSliderUsesNativeClick() throws Exception {
        Ui ui = new Ui();
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            engine.uiAction(
                            object(
                                    "{\"action\":\"type_text\",\"selector\":{\"nodeId\":\"input\"},\"text\":\"中文😀\"}"),
                            () -> {})
                    .get(4, TimeUnit.SECONDS);
            assertEquals(List.of(0x4E2D, 0x6587, 0x1F600), ui.characters);
            assertEquals(1, ui.presses);
            engine.uiAction(
                            object(
                                    "{\"action\":\"set_value\",\"selector\":{\"nodeId\":\"slider\"},\"value\":0.25}"),
                            () -> {})
                    .get(3, TimeUnit.SECONDS);
            assertEquals(49, ui.x);
            assertEquals(2, ui.presses);
        }
    }

    @Test
    void focusedTextPreservesCaretAndNeverClicksOrReplaces() throws Exception {
        Ui ui = new Ui();
        JsonObject request =
                object(
                        """
            {"action":"type_text","selector":{"nodeId":"input"},"text":"中","replace":false,"focusOnly":true}
            """);
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            JsonObject result = engine.uiAction(request, () -> {}).get(2, TimeUnit.SECONDS);
            assertEquals(0, ui.presses);
            assertTrue(ui.keyPresses.isEmpty());
            assertEquals(List.of(0x4E2D), ui.characters);
            assertEquals("ui.action_result", result.get("type").getAsString());
            ui.tree
                    .getAsJsonArray("children")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("focused", false);
            assertThrows(
                    ExecutionException.class,
                    () -> engine.uiAction(request, () -> {}).get(2, TimeUnit.SECONDS));
            assertEquals(0, ui.presses);
            assertEquals(1, ui.characters.size());
        }
    }

    @Test
    void textCancellationAndScreenReplacementCannotDispatchLateCharacters() throws Exception {
        Ui ui = new Ui();
        ui.replaceScreen = true;
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            ExecutionException error =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.uiAction(
                                                    object(
                                                            "{\"action\":\"type_text\",\"selector\":{\"nodeId\":\"input\"},\"text\":\"abc\"}"),
                                                    () -> {})
                                            .get(4, TimeUnit.SECONDS));
            assertEquals(
                    "UI_TARGET_INVALIDATED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(List.of((int) 'a'), ui.characters);
        }
    }

    @Test
    void typedKeyboardEditorDoesNotInventMouseCoordinatesAndNavigatesNativeLines()
            throws Exception {
        Ui ui = new Ui();
        JsonObject field =
                new RenderedUiAdapter(1, System::identityHashCode)
                        .keyboardField(
                                new Object(), "sign.line.2", "Sign line 3", "", false, false);
        field.addProperty("focusOffset", 2);
        ui.tree.add("children", new JsonArray());
        ui.tree.getAsJsonArray("children").add(field);
        ui.navigationRemaining = 2;
        JsonObject selector = new JsonObject();
        selector.addProperty("controlKey", "sign.line.2");
        assertFalse(AutomationEngine.resolveTree(ui.tree, selector).has("interactionPoint"));
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            JsonObject request =
                    object("{\"action\":\"type_text\",\"text\":\"Hi\",\"replace\":false}");
            request.add("selector", selector);
            JsonObject result = engine.uiAction(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals("screen_keyboard", result.get("inputRoute").getAsString());
            assertFalse(result.has("x"));
            assertEquals(List.of(264, 264), ui.keyPresses);
            assertEquals(List.of((int) 'H', (int) 'i'), ui.characters);
            assertEquals(0, ui.presses);
            assertEquals(0, ui.x);
            assertEquals(0, ui.y);
        }
    }

    @Test
    void multilineUsesNativeEnterAndStaleSemanticChoiceRejects() throws Exception {
        Ui ui = new Ui();
        JsonObject field = ui.tree.getAsJsonArray("children").get(0).getAsJsonObject();
        field.addProperty("multiline", true);
        field.addProperty("semanticState", "recipe-a");
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            engine.uiAction(
                            object(
                                    "{\"action\":\"type_text\",\"selector\":{\"nodeId\":\"input\"},\"text\":\"a\\nb\"}"),
                            () -> {})
                    .get(4, TimeUnit.SECONDS);
            assertEquals(List.of((int) 'a', (int) 'b'), ui.characters);
            assertTrue(ui.keyPresses.contains(257));
            JsonObject guard = new JsonObject();
            guard.add("frame", new JsonObject());
            guard.add("node", field.deepCopy());
            guard.add("selector", object("{\"nodeId\":\"input\"}"));
            guard.addProperty("action", "type_text");
            field.addProperty("semanticState", "recipe-b");
            ProtocolState.ProtocolException error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> AutomationEngine.validatePointerGuard(guard, ui.tree));
            assertEquals("UI_TARGET_INVALIDATED", error.code());
        }
    }

    @Test
    void dragOffsetsStayInsideTheNativeExclusiveRightAndBottomEdges() throws Exception {
        Ui ui = new Ui();
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            engine.executePipeline(
                            object(
                                    """
                {"steps":[{"type":"ui.drag","fromSelector":{"nodeId":"input"},"toSelector":{"nodeId":"slider"},
                "toOffset":{"x":1,"y":1},"segments":4,"durationMs":100}],"cleanupOnComplete":false}
                """),
                            () -> {})
                    .get(5, TimeUnit.SECONDS);
            assertEquals(Math.nextDown(128.0), ui.x);
            assertEquals(Math.nextDown(80.0), ui.y);
            assertEquals(1, ui.presses);
        }
    }

    @Test
    void directAbsoluteMotionPreservesFractionsAndRefusesGameplay() throws Exception {
        Ui ui = new Ui();
        JsonObject move =
                object("{\"type\":\"mouse.move\",\"motion\":\"direct\",\"x\":10.25,\"y\":20.75}");
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            ui.tree.addProperty("screenClass", "InventoryScreen");
            engine.rawInput(move, () -> {}, java.util.function.Supplier::get)
                    .get(2, TimeUnit.SECONDS);
            assertEquals(10.25, ui.x);
            assertEquals(20.75, ui.y);
            ui.tree.addProperty("screenClass", "");
            move.addProperty("x", 100.5);
            ExecutionException error =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.rawInput(
                                                    move,
                                                    () -> {},
                                                    java.util.function.Supplier::get)
                                            .get(2, TimeUnit.SECONDS));
            assertEquals(
                    "GUI_POINTER_REQUIRED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(10.25, ui.x);
        }
    }

    @Test
    void nativePopupOccludesUnderlyingControlsWithoutInventingClickability() throws Exception {
        Ui ui = new Ui();
        ui.tree
                .getAsJsonArray("children")
                .get(0)
                .getAsJsonObject()
                .addProperty("interactionX", 120);
        ui.tree.getAsJsonArray("children").get(0).getAsJsonObject().addProperty("interactionY", 30);
        JsonObject popup =
                object(
                        """
            {"nodeId":"suggestions","controlKey":"chat.suggestions","interactionLayer":50,
            "x":10,"y":10,"width":220,"height":40,"visible":true}
            """);
        ui.tree.getAsJsonArray("children").add(popup);
        VanillaUiTree.markOccluded(ui.tree.getAsJsonArray("children"));
        assertTrue(
                ui.tree
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("occluded")
                        .getAsBoolean());
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            ExecutionException error =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.uiAction(
                                                    object(
                                                            "{\"action\":\"click\",\"selector\":{\"nodeId\":\"input\"}}"),
                                                    () -> {})
                                            .get(2, TimeUnit.SECONDS));
            assertEquals(
                    "UI_NODE_OCCLUDED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(0, ui.presses);
        }
    }

    @Test
    void invalidTextAndValuesDoNotPressAnything() throws Exception {
        Ui ui = new Ui();
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.uiAction(
                                            object(
                                                    "{\"action\":\"type_text\",\"selector\":{\"nodeId\":\"input\"},\"text\":\"a\\nb\"}"),
                                            () -> {})
                                    .get(4, TimeUnit.SECONDS));
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.uiAction(
                                            object(
                                                    "{\"action\":\"set_value\",\"selector\":{\"nodeId\":\"slider\"},\"value\":2}"),
                                            () -> {})
                                    .get(4, TimeUnit.SECONDS));
            assertEquals(0, ui.presses);
            assertTrue(ui.characters.isEmpty());
            assertEquals(0, ui.x);
            assertEquals(0, ui.y);
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.uiAction(
                                            object(
                                                    "{\"action\":\"type_text\",\"selector\":{\"nodeId\":\"input\"}}"),
                                            () -> {})
                                    .get(1, TimeUnit.SECONDS));
        }
    }
}
