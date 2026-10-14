package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.AutomationEngine;
import io.github.recrivenvi.minecraftprotocol.core.ProbeService;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

final class UiTargetGeometryTest {
    private static JsonObject node(String id, double x, double y, double width, double height) {
        JsonObject node = new JsonObject();
        node.addProperty("nodeId", id);
        node.addProperty("role", "slot");
        node.addProperty("class", "Slot");
        node.addProperty("elementIdentity", id.hashCode());
        node.addProperty("x", x);
        node.addProperty("y", y);
        node.addProperty("width", width);
        node.addProperty("height", height);
        NeoForgeProbeRuntime.addInteractionMetadata(node, "slot", true);
        return node;
    }

    private static JsonObject slot(String id, int x, int y) {
        JsonObject node = node(id, 0, 0, 16, 16);
        NeoForgeProbeRuntime.addSlotBounds(node, x, y);
        NeoForgeProbeRuntime.addInteractionMetadata(node, "slot", true);
        return node;
    }

    private static JsonObject selector(String id) {
        JsonObject selector = new JsonObject();
        selector.addProperty("nodeId", id);
        return selector;
    }

    private static final class Ui {
        final JsonObject tree = new JsonObject();
        final List<double[]> presses = new ArrayList<>();
        double x, y;

        Ui(JsonObject... nodes) {
            tree.addProperty("screenClass", "InventoryScreen");
            tree.addProperty("screenIdentity", 1);
            tree.addProperty("screenRevision", 1);
            tree.addProperty("menuRevision", 1);
            tree.addProperty("width", 641);
            tree.addProperty("height", 361);
            tree.addProperty("windowWidth", 2561);
            tree.addProperty("windowHeight", 1441);
            tree.addProperty("guiScale", 4);
            JsonArray children = new JsonArray();
            for (JsonObject node : nodes) children.add(node);
            tree.add("children", children);
        }

        ProbeService service() {
            return (ProbeService)
                    Proxy.newProxyInstance(
                            ProbeService.class.getClassLoader(),
                            new Class<?>[] {ProbeService.class},
                            (proxy, method, args) -> {
                                if (method.getReturnType() == void.class) return null;
                                JsonObject result = new JsonObject();
                                switch (method.getName()) {
                                    case "pointerState", "uiTree" -> {
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
                                        if ((int) args[1] == 1) presses.add(new double[] {x, y});
                                    }
                                    default -> {}
                                }
                                return CompletableFuture.completedFuture(result);
                            });
        }
    }

    @Test
    void capturedSlotBoundsMetadataAndResolveUseTheVanillaItemCenter() {
        for (int x : new int[] {0, 8, 100, 160}) {
            JsonObject slot = slot("slot", x, 40);
            assertEquals(x - 1, slot.get("x").getAsInt());
            assertEquals(39, slot.get("y").getAsInt());
            assertEquals(18, slot.get("width").getAsInt());
            assertEquals(18, slot.get("height").getAsInt());
            JsonObject point =
                    AutomationEngine.resolveTree(new Ui(slot).tree, selector("slot"))
                            .getAsJsonObject("interactionPoint");
            assertEquals(x + 8, point.get("x").getAsDouble());
            assertEquals(48, point.get("y").getAsDouble());
            assertEquals(slot.get("interactionX"), point.get("x"));
            assertEquals(slot.get("interactionY"), point.get("y"));
        }
    }

    @Test
    void hoverClickAndDragReuseResolveWithoutRoundingOddSizedWidgets() throws Exception {
        Ui ui = new Ui(slot("slot", 100, 40), node("widget", 201, 31, 31, 21));
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            JsonObject request = new JsonObject();
            request.add("selector", selector("slot"));
            request.addProperty("action", "hover");
            JsonObject hover = engine.uiAction(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(108, ui.x);
            assertEquals(48, ui.y);
            assertEquals(0, ui.presses.size());
            assertEquals(108, hover.get("x").getAsDouble());
            request.addProperty("action", "click");
            engine.uiAction(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertArrayEquals(new double[] {108, 48}, ui.presses.get(0));

            JsonObject step = new JsonObject();
            step.addProperty("type", "ui.drag");
            step.add("fromSelector", selector("widget"));
            step.add("toSelector", selector("slot"));
            step.addProperty("durationMs", 20);
            step.addProperty("segments", 4);
            JsonArray steps = new JsonArray();
            steps.add(step);
            JsonObject pipeline = new JsonObject();
            pipeline.add("steps", steps);
            pipeline.addProperty("timeoutMs", 3000);
            engine.executePipeline(pipeline, () -> {}).get(3, TimeUnit.SECONDS);
            assertArrayEquals(new double[] {216.5, 41.5}, ui.presses.get(1));
            assertEquals(108, ui.x);
            assertEquals(48, ui.y);
            JsonObject point =
                    engine.resolve(selector("widget"))
                            .get(1, TimeUnit.SECONDS)
                            .getAsJsonObject("interactionPoint");
            assertEquals(216.5, point.get("x").getAsDouble());
            assertEquals(41.5, point.get("y").getAsDouble());
        }
    }

    @Test
    void invalidGeometryAndAmbiguousTargetingNeverDispatchPresses() throws Exception {
        for (double width : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Ui ui = new Ui(node("bad", 10, 10, width, 16));
            assertFalse(
                    ui.tree
                            .getAsJsonArray("children")
                            .get(0)
                            .getAsJsonObject()
                            .has("interactionX"));
            try (AutomationEngine engine = new AutomationEngine(ui.service())) {
                JsonObject request = new JsonObject();
                request.add("selector", selector("bad"));
                ExecutionException error =
                        assertThrows(
                                ExecutionException.class,
                                () -> engine.uiAction(request, () -> {}).get(1, TimeUnit.SECONDS));
                assertEquals(
                        "UI_NODE_NOT_ACTIONABLE",
                        ((ProtocolState.ProtocolException) error.getCause()).code());
                assertEquals(0, ui.presses.size());
            }
        }
        Ui ui = new Ui(slot("slot", 100, 40));
        try (AutomationEngine engine = new AutomationEngine(ui.service())) {
            JsonObject request = new JsonObject();
            request.add("selector", selector("slot"));
            request.add("coordinates", JsonParser.parseString("{\"x\":5,\"y\":5}"));
            ExecutionException error =
                    assertThrows(
                            ExecutionException.class,
                            () -> engine.uiAction(request, () -> {}).get(1, TimeUnit.SECONDS));
            assertEquals(
                    "INVALID_UI_ACTION",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(0, ui.presses.size());
        }
    }
}
