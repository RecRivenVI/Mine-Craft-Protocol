package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UiActionAliasTest {
    private static final int LEFT_SHIFT = 340;
    private static final int RIGHT_SHIFT = 344;

    @Test
    void disabledTargetRejectsBeforeAnyNativeMoveOrButtonButStillRunsFailureCleanup()
            throws Exception {
        SlotOwner owner = new SlotOwner();
        owner.disabled = true;
        try (AutomationEngine engine = new AutomationEngine(owner.service())) {
            ExecutionException failure =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.uiAction(request("click"), () -> {})
                                            .get(4, TimeUnit.SECONDS));
            assertEquals(
                    "UI_NODE_NOT_ACTIONABLE",
                    assertInstanceOf(ProtocolState.ProtocolException.class, failure.getCause())
                            .code());
            assertEquals(0, owner.moves.get());
            assertTrue(owner.events.isEmpty());
            assertEquals(1, owner.cleanups.get());
            assertEquals(8, owner.slotCount);
            owner.assertReleased();
        }
    }

    @Test
    void advertisedShiftClickUsesRealShiftAndReleasesItAfterItsOwnSlotTransfer() throws Exception {
        SlotOwner owner = new SlotOwner();
        owner.transferOnShiftClick = true;
        try (AutomationEngine engine = new AutomationEngine(owner.service())) {
            engine.uiAction(request("shift_click"), () -> {}).get(4, TimeUnit.SECONDS);

            assertEquals(
                    List.of("key:340:1", "button:0:1", "button:0:0", "key:340:0"),
                    owner.inputOrder());
            for (InputEvent event : owner.events) {
                if (event.kind().equals("button")) {
                    assertTrue(event.shiftHeld(), "A modifier flag alone is not a held Shift key");
                    assertEquals(1, event.modifiers() & 1);
                }
            }
            assertEquals(0, owner.slotCount, "The guarded native callback changed its own slot");
            owner.assertReleased();
            assertEquals(0, owner.cleanups.get(), "Successful uiAction preserves unrelated input");
        }
    }

    @Test
    void advertisedRightClickUsesTheRightButtonWithoutPressingShift() throws Exception {
        SlotOwner owner = new SlotOwner();
        try (AutomationEngine engine = new AutomationEngine(owner.service())) {
            engine.uiAction(request("right_click"), () -> {}).get(4, TimeUnit.SECONDS);

            assertEquals(List.of("button:1:1", "button:1:0"), owner.inputOrder());
            assertTrue(owner.events.stream().noneMatch(InputEvent::shiftHeld));
            owner.assertReleased();
        }
    }

    @Test
    void shiftClickPreservesEitherShiftKeyAlreadyHeldByTheControlSession() throws Exception {
        for (int heldShift : List.of(LEFT_SHIFT, RIGHT_SHIFT)) {
            SlotOwner owner = new SlotOwner();
            owner.keys.add(heldShift);
            owner.transferOnShiftClick = true;
            try (AutomationEngine engine = new AutomationEngine(owner.service())) {
                engine.uiAction(request("shift_click"), () -> {}).get(4, TimeUnit.SECONDS);

                assertEquals(List.of("button:0:1", "button:0:0"), owner.inputOrder());
                assertEquals(Set.of(heldShift), owner.keys);
                assertTrue(owner.buttons.isEmpty());
                assertEquals(0, owner.cleanups.get());
                assertEquals(0, owner.slotCount);
            }
        }
    }

    @Test
    void explicitRightButtonClickIsTheSupportedProductionDispatchControl() throws Exception {
        SlotOwner owner = new SlotOwner();
        JsonObject action = request("click");
        action.addProperty("button", 1);
        try (AutomationEngine engine = new AutomationEngine(owner.service())) {
            engine.uiAction(action, () -> {}).get(4, TimeUnit.SECONDS);

            assertEquals(List.of("button:1:1", "button:1:0"), owner.inputOrder());
            owner.assertReleased();
        }
    }

    @Test
    void failedNativeShiftClickCleansRealHeldKeysAndButtonsBeforeReportingFailure()
            throws Exception {
        SlotOwner owner = new SlotOwner();
        owner.failNativePress = true;
        try (AutomationEngine engine = new AutomationEngine(owner.service())) {
            ExecutionException failure =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    engine.uiAction(request("shift_click"), () -> {})
                                            .get(4, TimeUnit.SECONDS));

            assertEquals(
                    "NATIVE_CLICK_FAILED",
                    assertInstanceOf(ProtocolState.ProtocolException.class, failure.getCause())
                            .code());
            assertTrue(
                    owner.events.stream().anyMatch(e -> e.kind().equals("key") && e.action() == 1));
            assertTrue(
                    owner.events.stream()
                            .anyMatch(
                                    e ->
                                            e.kind().equals("button")
                                                    && e.action() == 1
                                                    && e.shiftHeld()));
            assertEquals(1, owner.cleanups.get());
            owner.assertReleased();
        }
    }

    private static JsonObject request(String action) {
        JsonObject selector = new JsonObject();
        selector.addProperty("nodeId", "slot:0");
        JsonObject request = new JsonObject();
        request.addProperty("action", action);
        request.addProperty("holdMs", 1);
        request.add("selector", selector);
        return request;
    }

    private record InputEvent(
            String kind, int code, int action, int modifiers, boolean shiftHeld) {}

    private static final class SlotOwner {
        private final Set<Integer> keys = ConcurrentHashMap.newKeySet();
        private final Set<Integer> buttons = ConcurrentHashMap.newKeySet();
        private final List<InputEvent> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger cleanups = new AtomicInteger();
        private final AtomicInteger moves = new AtomicInteger();
        private volatile int slotCount = 8;
        private volatile boolean transferOnShiftClick, failNativePress, disabled;

        private JsonObject tree() {
            JsonObject tree = new JsonObject();
            tree.addProperty("screenClass", "InventoryScreen");
            tree.addProperty("screenIdentity", 1);
            tree.addProperty("screenRevision", 1);
            tree.addProperty("overlayIdentity", 0);
            tree.addProperty("menuIdentity", 2);
            tree.addProperty("menuRevision", slotCount == 8 ? 1 : 2);
            tree.addProperty("gameCursorRevision", 1);
            tree.addProperty("guiAbsolute", true);
            tree.addProperty("width", 320);
            tree.addProperty("height", 240);
            tree.addProperty("windowWidth", 320);
            tree.addProperty("windowHeight", 240);
            tree.addProperty("guiScale", 1);
            tree.addProperty("x", 48);
            tree.addProperty("y", 48);
            tree.addProperty("pressedKeyCount", keys.size());
            tree.addProperty("pressedButtonCount", buttons.size());
            JsonObject carried = new JsonObject();
            carried.addProperty("empty", true);
            carried.addProperty("id", "minecraft:air");
            carried.addProperty("count", 0);
            carried.addProperty("contentFingerprint", "empty-carried-stack");
            carried.addProperty("contentGuardAvailable", true);
            tree.add("carriedStack", carried);
            JsonObject slot = new JsonObject();
            slot.addProperty("nodeId", "slot:0");
            slot.addProperty("elementIdentity", 3);
            slot.addProperty("class", "Slot");
            slot.addProperty("role", "slot");
            slot.addProperty("slot", 0);
            slot.addProperty("slotOwner", "player_inventory");
            slot.addProperty("active", !disabled);
            slot.addProperty("visible", true);
            slot.addProperty("boundsAvailable", true);
            slot.addProperty("x", 40);
            slot.addProperty("y", 40);
            slot.addProperty("width", 16);
            slot.addProperty("height", 16);
            slot.addProperty("item", slotCount == 0 ? "minecraft:air" : "minecraft:stone");
            slot.addProperty("count", slotCount);
            slot.addProperty("contentRevision", "slot-content:" + slotCount);
            slot.addProperty("contentFingerprint", "slot-metadata");
            slot.addProperty("contentGuardAvailable", true);
            JsonArray actions = new JsonArray();
            for (String action : List.of("click", "right_click", "shift_click"))
                actions.add(action);
            slot.add("actions", actions);
            JsonArray children = new JsonArray();
            children.add(slot);
            tree.add("children", children);
            return tree;
        }

        private ProbeService service() {
            return (ProbeService)
                    Proxy.newProxyInstance(
                            ProbeService.class.getClassLoader(),
                            new Class<?>[] {ProbeService.class},
                            (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "gestureEvent", "attachControlSession" -> {
                                        return null;
                                    }
                                    case "pointerState", "inputPacingState", "uiTree" -> {
                                        return CompletableFuture.completedFuture(tree());
                                    }
                                    case "inputState" -> {
                                        JsonObject state = new JsonObject();
                                        JsonArray heldKeys = new JsonArray();
                                        keys.stream().sorted().forEach(heldKeys::add);
                                        state.add("pressedKeys", heldKeys);
                                        state.addProperty("pressedKeyCount", keys.size());
                                        state.addProperty("pressedButtonCount", buttons.size());
                                        return CompletableFuture.completedFuture(state);
                                    }
                                    case "releaseAllInput" -> {
                                        cleanups.incrementAndGet();
                                        keys.clear();
                                        buttons.clear();
                                    }
                                    case "mouseMoveGuarded" -> {
                                        moves.incrementAndGet();
                                        beforeInput();
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[2], tree());
                                    }
                                    case "key", "keyGuarded" -> {
                                        beforeInput();
                                        if (args.length == 5 && args[4] != null)
                                            AutomationEngine.validatePointerGuard(
                                                    (JsonObject) args[4], tree());
                                        int key = (int) args[0], action = (int) args[2];
                                        if (action == 0) keys.remove(key);
                                        else if (action == 1) keys.add(key);
                                        events.add(
                                                new InputEvent(
                                                        "key",
                                                        key,
                                                        action,
                                                        (int) args[3],
                                                        shiftHeld()));
                                    }
                                    case "mouseButton", "mouseButtonGuarded" -> {
                                        beforeInput();
                                        if (args.length == 4)
                                            AutomationEngine.validatePointerGuard(
                                                    (JsonObject) args[3], tree());
                                        int button = (int) args[0], action = (int) args[1];
                                        if (action == 0) buttons.remove(button);
                                        else if (action == 1) buttons.add(button);
                                        events.add(
                                                new InputEvent(
                                                        "button",
                                                        button,
                                                        action,
                                                        (int) args[2],
                                                        shiftHeld()));
                                        if (action == 1 && failNativePress)
                                            return CompletableFuture.failedFuture(
                                                    new ProtocolState.ProtocolException(
                                                            "NATIVE_CLICK_FAILED",
                                                            409,
                                                            "Native press failed after acquiring input"));
                                        if (action == 1
                                                && button == 0
                                                && transferOnShiftClick
                                                && shiftHeld()) slotCount = 0;
                                    }
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                }
                                return CompletableFuture.completedFuture(new JsonObject());
                            });
        }

        private static void beforeInput() {
            InputDispatchContext.Permit permit = InputDispatchContext.capture();
            assertNotNull(permit, "Dispatch must use the production input permit");
            permit.beforeInput((screen, menu) -> {});
        }

        private List<String> inputOrder() {
            return events.stream().map(e -> e.kind() + ":" + e.code() + ":" + e.action()).toList();
        }

        private boolean shiftHeld() {
            return keys.contains(LEFT_SHIFT) || keys.contains(RIGHT_SHIFT);
        }

        private void assertReleased() {
            assertTrue(keys.isEmpty(), "Held keys must be zero");
            assertTrue(buttons.isEmpty(), "Held buttons must be zero");
        }
    }
}
