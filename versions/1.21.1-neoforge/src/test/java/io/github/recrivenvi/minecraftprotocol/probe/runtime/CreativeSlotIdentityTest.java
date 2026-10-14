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
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;

final class CreativeSlotIdentityTest {
    private static JsonObject node(int viewIndex, int nativeIndex, long menu, long element) {
        // Only Slot identity/geometry is inspected here; no registry-dependent item container.
        Slot slot = new Slot(null, 0, 40 + viewIndex * 18, 20);
        slot.index = nativeIndex;
        JsonObject node = new JsonObject();
        NeoForgeProbeRuntime.addSlotIdentity(node, slot, viewIndex, 7, menu, element);
        node.addProperty("role", "slot");
        node.addProperty("class", "CreativeSlotWrapper");
        NeoForgeProbeRuntime.addSlotBounds(node, slot.x, slot.y);
        NeoForgeProbeRuntime.addInteractionMetadata(node, "slot", true);
        return node;
    }

    private static JsonObject tree(JsonObject... nodes) {
        JsonObject tree =
                JsonParser.parseString(
                                "{\"screenRevision\":7,\"menuRevision\":3,\"menuIdentity\":11,\"screenIdentity\":5,\"screenClass\":\"CreativeModeInventoryScreen\",\"width\":320,\"height\":240,\"guiAbsolute\":true,\"pressedButtonCount\":0,\"x\":48,\"y\":28}")
                        .getAsJsonObject();
        JsonArray children = new JsonArray();
        for (JsonObject node : nodes) children.add(node);
        tree.add("children", children);
        return tree;
    }

    private static JsonObject selector(int slot) {
        JsonObject selector = new JsonObject();
        selector.addProperty("slot", slot);
        return selector;
    }

    @Test
    void repeatedNativeIndicesStillResolveEachViewSlotAndNodeExactlyOnce() {
        JsonObject[] nodes = new JsonObject[46];
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = node(i, 0, 11, 100 + i);
            assertTrue(ids.add(nodes[i].get("nodeId").getAsString()));
            assertEquals(0, nodes[i].get("nativeSlotIndex").getAsInt());
        }
        JsonObject tree = tree(nodes);
        for (int i = 0; i < nodes.length; i++) {
            assertEquals(nodes[i], AutomationEngine.resolveTree(tree, selector(i)).get("node"));
            JsonObject byId = new JsonObject();
            byId.add("nodeId", nodes[i].get("nodeId"));
            assertEquals(1, ConditionEngine.findMatches(tree, byId).size());
        }
    }

    @Test
    void hiddenCreativeCraftingSlotsAreNotSelectableByDefault() {
        JsonObject hidden = node(0, 0, 11, 100);
        NeoForgeProbeRuntime.addSlotBounds(hidden, -2000, -2000);
        boolean visible = NeoForgeProbeRuntime.addSlotVisibility(hidden, 320, 240);
        NeoForgeProbeRuntime.addInteractionMetadata(hidden, "slot", visible);
        assertFalse(visible);
        assertTrue(hidden.getAsJsonArray("actions").isEmpty());
        assertTrue(ConditionEngine.findMatches(tree(hidden), selector(0)).isEmpty());
        JsonObject shown = node(9, 0, 11, 109);
        assertTrue(NeoForgeProbeRuntime.addSlotVisibility(shown, 320, 240));
        assertEquals(1, ConditionEngine.findMatches(tree(hidden, shown), selector(9)).size());
    }

    @Test
    void normalMenuNumbersRemainUnchangedAndNodeIdsAreStableWithinTheSameView() {
        for (int i = 0; i < 54; i++) {
            assertEquals(i, node(i, i, 11, 100 + i).get("slot").getAsInt());
            assertEquals(node(i, i, 11, 100 + i), node(i, i, 11, 100 + i));
        }
    }

    @Test
    void replacedWrapperReorderedViewAndReusedMenuCannotAliasOldNode() {
        JsonObject old = node(1, 0, 11, 101);
        JsonObject byId = new JsonObject();
        byId.add("nodeId", old.get("nodeId"));
        JsonObject guard = new JsonObject();
        JsonObject frame = new JsonObject();
        frame.addProperty("menuIdentity", 11);
        guard.add("frame", frame);
        guard.add("selector", selector(1));
        guard.add("node", old);
        for (JsonObject changed :
                List.of(node(1, 0, 11, 201), node(0, 0, 11, 101), node(1, 0, 12, 101))) {
            JsonObject current = tree(changed);
            if (changed.get("nodeId").getAsString().contains(":12:"))
                current.addProperty("menuIdentity", 12);
            assertTrue(ConditionEngine.findMatches(current, byId).isEmpty());
            ProtocolState.ProtocolException error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> AutomationEngine.validatePointerGuard(guard, current));
            assertEquals("UI_TARGET_INVALIDATED", error.code());
        }
    }

    @Test
    void semanticHoverClickAndDragDispatchToDistinctSlotCentersWithoutCoordinates()
            throws Exception {
        JsonObject tree = tree(node(0, 0, 11, 100), node(1, 0, 11, 101), node(2, 0, 11, 102));
        double[] position = {48, 28};
        List<Double> presses = new ArrayList<>();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    JsonObject result = new JsonObject();
                                    switch (method.getName()) {
                                        case "uiTree", "debugUiTree", "pointerState" -> {
                                            result = tree.deepCopy();
                                            result.addProperty("x", position[0]);
                                            result.addProperty("y", position[1]);
                                        }
                                        case "mouseMoveGuarded" -> {
                                            AutomationEngine.validatePointerGuard(
                                                    (JsonObject) args[2], tree);
                                            position[0] = (double) args[0];
                                            position[1] = (double) args[1];
                                        }
                                        case "mouseButtonGuarded" -> {
                                            AutomationEngine.validatePointerGuard(
                                                    (JsonObject) args[3], tree);
                                            if ((int) args[1] == 1) presses.add(position[0]);
                                        }
                                        default -> {}
                                    }
                                    return CompletableFuture.completedFuture(result);
                                });
        try (AutomationEngine engine = new AutomationEngine(service)) {
            JsonObject request = new JsonObject();
            request.add("selector", selector(1));
            request.addProperty("action", "hover");
            engine.uiAction(request, () -> {}).get(4, TimeUnit.SECONDS);
            assertEquals(66, position[0]);
            assertTrue(presses.isEmpty());
            request.addProperty("action", "click");
            engine.uiAction(request, () -> {}).get(4, TimeUnit.SECONDS);
            JsonObject drag = new JsonObject();
            drag.addProperty("type", "ui.drag");
            drag.add("fromSelector", selector(0));
            drag.add("toSelector", selector(2));
            drag.addProperty("durationMs", 100);
            drag.addProperty("segments", 4);
            JsonArray steps = new JsonArray();
            steps.add(drag);
            JsonObject pipeline = new JsonObject();
            pipeline.add("steps", steps);
            pipeline.addProperty("timeoutMs", 5000);
            engine.executePipeline(pipeline, () -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(List.of(66.0, 48.0), presses);
            assertEquals(84, position[0]);
        }
    }
}
