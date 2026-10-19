package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.safety.InteractionPacing;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

final class PointerGestureTest {
    static final class World {
        volatile int revision = 1, width = 320;
        volatile long gameCursorRevision = 1;
        volatile boolean moveTarget;
        volatile int menuRevision = 1, carriedCount, otherCount, targetCount;
        volatile int targetData, otherData, carriedData;
        volatile boolean targetDataAvailable = true;
        volatile boolean targetSlotDisabled;
        volatile long menuIdentity = 7;
        volatile Runnable afterMove, duringMove, afterPress, beforePointerRead;
        volatile Runnable beforeNativeInput;
        volatile Runnable afterPointerValidation;
        volatile boolean slots;
        volatile boolean selectionRows, selectedTarget;
        volatile Runnable afterRelease;
        final AtomicInteger pointerReads = new AtomicInteger();
        final AtomicInteger uiReads = new AtomicInteger(), debugUiReads = new AtomicInteger();
        final AtomicInteger moves = new AtomicInteger(),
                presses = new AtomicInteger(),
                releases = new AtomicInteger(),
                cleanups = new AtomicInteger();
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<Double> positions = Collections.synchronizedList(new ArrayList<>());
        final List<Long> pressTimes = Collections.synchronizedList(new ArrayList<>());
        volatile double x = 5, y = 5;
        volatile int buttons;
        volatile CompletableFuture<JsonObject> blockedCleanup;
        volatile CompletableFuture<JsonObject> blockedUiRead;

        JsonObject tree() {
            JsonObject tree = new JsonObject();
            tree.addProperty("screenRevision", revision);
            tree.addProperty("gameCursorRevision", gameCursorRevision);
            tree.addProperty("menuRevision", menuRevision);
            tree.addProperty("menuIdentity", menuIdentity);
            JsonObject carried = new JsonObject();
            carried.addProperty("empty", carriedCount == 0);
            carried.addProperty("id", carriedCount == 0 ? "minecraft:air" : "minecraft:stone");
            carried.addProperty("count", carriedCount);
            carried.addProperty("contentFingerprint", "item:" + carriedData);
            carried.addProperty("contentGuardAvailable", true);
            tree.add("carriedStack", carried);
            tree.addProperty("screenIdentity", revision);
            tree.addProperty("overlayIdentity", 0);
            tree.addProperty("screenClass", "FakeScreen");
            tree.addProperty("guiAbsolute", true);
            tree.addProperty("pressedButtonCount", buttons);
            tree.addProperty("width", width);
            tree.addProperty("height", 240);
            tree.addProperty("windowWidth", width);
            tree.addProperty("windowHeight", 240);
            tree.addProperty("guiScale", 1);
            tree.addProperty("x", x);
            tree.addProperty("y", y);
            JsonArray children = new JsonArray();
            for (int i = 0; i < 2; i++) {
                JsonObject node = new JsonObject();
                node.addProperty("nodeId", "button-" + i);
                node.addProperty("elementIdentity", i + 1);
                node.addProperty("class", slots ? "Slot" : "Button");
                node.addProperty("role", slots ? "slot" : selectionRows ? "list_item" : "button");
                if (selectionRows) node.addProperty("selected", i == 0 && selectedTarget);
                node.addProperty("label", i == 0 ? "A" : "B");
                int count = i == 0 ? targetCount : otherCount;
                node.addProperty("count", count);
                if (slots) {
                    if (i == 0) node.addProperty("slotDisabled", targetSlotDisabled);
                    int data = i == 0 ? targetData : otherData;
                    node.addProperty("contentRevision", "slot-" + i + ":" + data + ":" + count);
                    node.addProperty("contentFingerprint", "item:" + data);
                    node.addProperty("contentGuardAvailable", i != 0 || targetDataAvailable);
                }
                if (slots)
                    node.addProperty("item", count == 0 ? "minecraft:air" : "minecraft:stone");
                node.addProperty("x", i == 0 ? (moveTarget ? 80 : 40) : 150);
                node.addProperty("y", 20);
                node.addProperty("width", 30);
                node.addProperty("height", 20);
                node.addProperty("active", true);
                node.addProperty("visible", true);
                JsonArray actions = new JsonArray();
                actions.add("click");
                if (selectionRows) actions.add("double_click");
                node.add("actions", actions);
                children.add(node);
            }
            tree.add("children", children);
            return tree;
        }

        ProbeService service() {
            return (ProbeService)
                    Proxy.newProxyInstance(
                            ProbeService.class.getClassLoader(),
                            new Class<?>[] {ProbeService.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("gestureEvent")
                                        || method.getName().equals("attachControlSession"))
                                    return null;
                                if (Set.of(
                                                "mouseMoveGuarded",
                                                "mouseButtonGuarded",
                                                "mouseButton",
                                                "mouseScrollGuarded",
                                                "mouseScroll",
                                                "mouseDelta",
                                                "key",
                                                "keyGuarded",
                                                "character")
                                        .contains(method.getName())) {
                                    if (beforeNativeInput != null) beforeNativeInput.run();
                                    var permit = InputDispatchContext.capture();
                                    if (permit != null)
                                        permit.beforeInput(
                                                (screen, menu) -> {
                                                    if (screen != null && screen != revision)
                                                        throw new ProtocolState.ProtocolException(
                                                                "STALE_SCREEN_REVISION",
                                                                409,
                                                                "changed before native event");
                                                    if (menu != null && menu != menuRevision)
                                                        throw new ProtocolState.ProtocolException(
                                                                "STALE_MENU_REVISION",
                                                                409,
                                                                "changed before native event");
                                                });
                                }
                                JsonObject result = new JsonObject();
                                switch (method.getName()) {
                                    case "pointerState", "inputPacingState" -> {
                                        pointerReads.incrementAndGet();
                                        if (beforePointerRead != null) beforePointerRead.run();
                                        result = tree();
                                    }
                                    case "uiTree", "debugUiTree" -> {
                                        boolean debug = method.getName().equals("debugUiTree");
                                        (debug ? debugUiReads : uiReads).incrementAndGet();
                                        if (blockedUiRead != null) return blockedUiRead;
                                        result = tree();
                                        result.addProperty("domain", "UI");
                                        result.addProperty(
                                                "dataCategory", debug ? "DEBUG" : "USER");
                                        if (debug) result.add("debugFacts", new JsonObject());
                                    }
                                    case "inputState" ->
                                            result.addProperty("pressedButtonCount", buttons);
                                    case "mouseMoveGuarded" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[2], tree());
                                        if (afterPointerValidation != null)
                                            afterPointerValidation.run();
                                        var permit = InputDispatchContext.capture();
                                        if (permit != null) permit.check();
                                        x = (double) args[0];
                                        y = (double) args[1];
                                        moves.incrementAndGet();
                                        events.add("move");
                                        positions.add(x);
                                        if (duringMove != null) duringMove.run();
                                        AutomationEngine.advanceDragGuard(
                                                (JsonObject) args[2], tree());
                                        if (afterMove != null) afterMove.run();
                                    }
                                    case "mouseButtonGuarded" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[3], tree());
                                        if ((int) args[1] == 1) {
                                            presses.incrementAndGet();
                                            buttons++;
                                            events.add("press:" + Math.round(x));
                                            pressTimes.add(System.nanoTime());
                                            if (afterPress != null) afterPress.run();
                                        } else {
                                            releases.incrementAndGet();
                                            buttons = 0;
                                            events.add("release");
                                        }
                                        AutomationEngine.advanceDragGuard(
                                                (JsonObject) args[3], tree());
                                        if ((int) args[1] == 1)
                                            AutomationEngine.advanceClickSelectionGuard(
                                                    (JsonObject) args[3], tree());
                                    }
                                    case "mouseButton" -> {
                                        if ((int) args[1] == 1) {
                                            presses.incrementAndGet();
                                            buttons++;
                                            events.add("press:" + Math.round(x));
                                        } else {
                                            releases.incrementAndGet();
                                            buttons = 0;
                                            events.add("release");
                                            if (afterRelease != null) afterRelease.run();
                                        }
                                    }
                                    case "key" -> events.add("key");
                                    case "mouseDelta" -> events.add("delta");
                                    case "releaseAllInput" -> {
                                        cleanups.incrementAndGet();
                                        buttons = 0;
                                        if (blockedCleanup != null) return blockedCleanup;
                                    }
                                    default -> {}
                                }
                                return CompletableFuture.completedFuture(result);
                            });
        }
    }

    private static JsonObject action(String label) {
        return JsonParser.parseString("{\"selector\":{\"label\":\"" + label + "\"},\"holdMs\":10}")
                .getAsJsonObject();
    }

    private static JsonObject pipeline(String steps, int timeout) {
        return JsonParser.parseString("{\"timeoutMs\":" + timeout + ",\"steps\":" + steps + "}")
                .getAsJsonObject();
    }

    private static void until(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean());
    }

    private static int movementSteps(double dx, double dy) {
        return InteractionPacing.movementSteps(
                InteractionPacing.movementMillis(Math.hypot(dx, dy)));
    }

    @Test
    void smoothMoveDispatchesBeforeClickAndTwoGuiRequestsSerialize() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var first = engine.uiAction(action("A"), () -> {});
            var second = engine.uiAction(action("B"), () -> {});
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            int firstSteps = movementSteps(50, 25);
            assertEquals(firstSteps + movementSteps(110, 0), state.moves.get());
            assertTrue(state.positions.subList(0, firstSteps).stream().anyMatch(x -> x > 55));
            assertEquals(55, state.positions.get(firstSteps - 1));
            assertTrue(
                    TimeUnit.NANOSECONDS.toMillis(state.pressTimes.get(1) - state.pressTimes.get(0))
                            >= 700);
            assertEquals(2, state.presses.get());
            assertEquals(
                    List.of("press:55", "press:165"),
                    state.events.stream().filter(e -> e.startsWith("press:")).toList());
            assertEquals("move", state.events.get(0));
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void changedBoundsOrScreenDuringMotionNeverClicksOldTarget() throws Exception {
        for (boolean resize : List.of(false, true)) {
            World state = new World();
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var request = engine.uiAction(action("A"), () -> {});
                until(() -> state.moves.get() >= 3);
                if (resize) state.width = 640;
                else state.moveTarget = true;
                var error =
                        assertThrows(
                                ExecutionException.class, () -> request.get(2, TimeUnit.SECONDS));
                assertEquals(
                        "UI_TARGET_INVALIDATED",
                        ((ProtocolState.ProtocolException) error.getCause()).code());
                assertEquals(0, state.presses.get());
                int stopped = state.moves.get();
                Thread.sleep(80);
                assertEquals(stopped, state.moves.get());
            }
        }
    }

    @Test
    void unrelatedMenuUpdatesDuringSemanticMotionDoNotInvalidateTheTarget() throws Exception {
        World state = new World();
        state.slots = true;
        state.afterMove =
                () -> {
                    state.menuRevision++;
                    state.otherCount++;
                };
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            engine.uiAction(action("A"), () -> {}).get(3, TimeUnit.SECONDS);
            assertTrue(state.otherCount > 3, "The other node really changed during movement");
            assertEquals(1, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void crafterCellStateChangeDuringPointerMotionRejectsBeforeClick() throws Exception {
        World state = new World();
        state.slots = true;
        state.afterMove = () -> state.targetSlotDisabled = true;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var request = engine.uiAction(action("A"), () -> {});
            var error =
                    assertThrows(ExecutionException.class, () -> request.get(3, TimeUnit.SECONDS));
            assertEquals(
                    "UI_TARGET_INVALIDATED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertTrue(state.moves.get() > 0);
            assertEquals(0, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void nativeOwnedCrafterCellChangeAdvancesDragGuardWithoutLateFailure() throws Exception {
        World state = new World();
        state.slots = true;
        state.afterPress = () -> state.targetSlotDisabled = true;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var request =
                    pipeline(
                            "[{\"type\":\"ui.drag\",\"fromSelector\":{\"label\":\"A\"},\"toSelector\":{\"label\":\"B\"},\"durationMs\":100,\"segments\":4}]",
                            3000);
            engine.executePipeline(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertTrue(state.targetSlotDisabled);
            assertEquals(1, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void semanticDoubleClickAcceptsOnlyItsFirstNativePressSelection() throws Exception {
        World state = new World();
        state.selectionRows = true;
        state.afterPress = () -> state.selectedTarget = true;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            JsonObject request = action("A");
            request.addProperty("action", "double_click");
            engine.uiAction(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(2, state.presses.get());
            assertEquals(2, state.releases.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void semanticDoubleClickRejectsExternalSelectionAndAnyOtherTargetChange() throws Exception {
        for (String phase : List.of("before_press", "inside_callback", "after_release")) {
            World state = new World();
            state.selectionRows = true;
            state.afterPress = () -> state.selectedTarget = true;
            if (phase.equals("before_press")) state.afterMove = () -> state.selectedTarget = true;
            if (phase.equals("inside_callback"))
                state.afterPress =
                        () -> {
                            state.selectedTarget = true;
                            state.moveTarget = true;
                        };
            if (phase.equals("after_release"))
                state.afterRelease = () -> state.selectedTarget = false;
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                JsonObject request = action("A");
                request.addProperty("action", "double_click");
                var error =
                        assertThrows(
                                ExecutionException.class,
                                () -> engine.uiAction(request, () -> {}).get(3, TimeUnit.SECONDS));
                assertEquals(
                        "UI_TARGET_INVALIDATED",
                        ((ProtocolState.ProtocolException) error.getCause()).code());
                assertEquals(phase.equals("before_press") ? 0 : 1, state.presses.get());
                assertEquals(0, state.buttons);
            }
        }
    }

    @Test
    void menuUpdatesDuringPreResolutionDwellAreNotAnOldTarget() throws Exception {
        World state = new World();
        state.beforePointerRead =
                () -> {
                    if (state.pointerReads.get() == 2) {
                        state.menuRevision++;
                        state.otherCount++;
                    }
                };
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            engine.uiAction(action("A"), () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(1, state.otherCount);
            assertEquals(1, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void sameItemAndCountMetadataChangeInvalidatesOnlyTheActualSemanticTarget() throws Exception {
        for (boolean target : List.of(false, true)) {
            World state = new World();
            state.slots = true;
            state.targetCount = 8;
            state.otherCount = 8;
            state.afterMove =
                    () -> {
                        if (state.moves.get() == 3) {
                            if (target) state.targetData++;
                            else state.otherData++;
                            state.menuRevision++;
                        }
                    };
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var pending = engine.uiAction(action("A"), () -> {});
                if (target) {
                    var failure =
                            assertThrows(
                                    ExecutionException.class,
                                    () -> pending.get(3, TimeUnit.SECONDS));
                    assertEquals(
                            "UI_TARGET_INVALIDATED",
                            ((ProtocolState.ProtocolException) failure.getCause()).code());
                    assertEquals(0, state.presses.get());
                    int stopped = state.moves.get();
                    Thread.sleep(60);
                    assertEquals(stopped, state.moves.get());
                } else {
                    pending.get(3, TimeUnit.SECONDS);
                    assertEquals(1, state.presses.get());
                }
                assertEquals(0, state.buttons);
            }
        }
    }

    @Test
    void unguardableItemMetadataRefusesBeforeTheFirstPointerInput() throws Exception {
        World state = new World();
        state.slots = true;
        state.targetDataAvailable = false;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var failure =
                    assertThrows(
                            ExecutionException.class,
                            () -> engine.uiAction(action("A"), () -> {}).get(3, TimeUnit.SECONDS));
            assertEquals(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    ((ProtocolState.ProtocolException) failure.getCause()).code());
            assertEquals(0, state.moves.get());
            assertEquals(0, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void changedCarriedStackOrMenuOwnerStillRejectsBeforePress() throws Exception {
        for (boolean carried : List.of(false, true)) {
            World state = new World();
            state.afterMove =
                    () -> {
                        if (state.moves.get() == 3) {
                            if (carried) state.carriedCount = 1;
                            else state.menuIdentity++;
                        }
                    };
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var pending = engine.uiAction(action("A"), () -> {});
                var error =
                        assertThrows(
                                ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
                assertEquals(
                        "UI_TARGET_INVALIDATED",
                        ((ProtocolState.ProtocolException) error.getCause()).code());
                assertEquals(0, state.presses.get());
                assertEquals(0, state.buttons);
                int stopped = state.moves.get();
                Thread.sleep(100);
                assertEquals(stopped, state.moves.get());
            }
        }
    }

    @Test
    void semanticTargetContentChangesCannotHideBehindUnrelatedMenuTolerance() throws Exception {
        World state = new World();
        state.slots = true;
        state.afterMove =
                () -> {
                    if (state.moves.get() == 3) {
                        state.targetCount++;
                        state.menuRevision++;
                    }
                };
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var error =
                    assertThrows(
                            ExecutionException.class,
                            () -> engine.uiAction(action("A"), () -> {}).get(3, TimeUnit.SECONDS));
            assertEquals(
                    "UI_TARGET_INVALIDATED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(0, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void explicitGlobalMenuPinRemainsStrictDuringDwellAndMotion() throws Exception {
        for (boolean dwell : List.of(false, true)) {
            World state = new World();
            if (dwell)
                state.beforePointerRead =
                        () -> {
                            if (state.pointerReads.get() == 2) state.menuRevision++;
                        };
            else state.afterMove = () -> state.menuRevision++;
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var pending =
                        engine.uiAction(
                                action("A"), () -> {}, java.util.function.Supplier::get, true);
                var error =
                        assertThrows(
                                ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
                assertEquals(
                        "UI_TARGET_INVALIDATED",
                        ((ProtocolState.ProtocolException) error.getCause()).code());
                assertEquals(0, state.presses.get());
                assertEquals(0, state.buttons);
            }
        }
    }

    @Test
    void keyboardDwellDoesNotBindAnUnrelatedSlotButExplicitPinsRemainStrict() throws Exception {
        for (boolean strict : List.of(false, true)) {
            World state = new World();
            state.beforePointerRead =
                    () -> {
                        if (state.pointerReads.get() == 2) state.menuRevision++;
                    };
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var pending =
                        engine.rawInput(
                                JsonParser.parseString(
                                                "{\"type\":\"key\",\"key\":256,\"action\":1}")
                                        .getAsJsonObject(),
                                () -> {},
                                java.util.function.Supplier::get,
                                strict);
                if (strict) {
                    var error =
                            assertThrows(
                                    ExecutionException.class,
                                    () -> pending.get(3, TimeUnit.SECONDS));
                    assertEquals(
                            "UI_TARGET_INVALIDATED",
                            ((ProtocolState.ProtocolException) error.getCause()).code());
                    assertFalse(state.events.contains("key"));
                } else {
                    pending.get(3, TimeUnit.SECONDS);
                    assertTrue(state.events.contains("key"));
                }
                assertEquals(0, state.moves.get());
                assertEquals(0, state.presses.get());
            }
        }
    }

    @Test
    void coordinateRequestsStillPinMenuBeforePreResolutionDwell() throws Exception {
        for (boolean raw : List.of(false, true)) {
            World state = new World();
            state.beforePointerRead =
                    () -> {
                        if (state.pointerReads.get() == 2) state.menuRevision++;
                    };
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var pending =
                        raw
                                ? engine.rawInput(
                                        JsonParser.parseString(
                                                        "{\"type\":\"mouse.move\",\"x\":55,\"y\":30}")
                                                .getAsJsonObject(),
                                        () -> {},
                                        java.util.function.Supplier::get)
                                : engine.uiAction(
                                        JsonParser.parseString(
                                                        "{\"coordinates\":{\"x\":55,\"y\":30}}")
                                                .getAsJsonObject(),
                                        () -> {});
                var error =
                        assertThrows(
                                ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
                assertEquals(
                        "UI_TARGET_INVALIDATED",
                        ((ProtocolState.ProtocolException) error.getCause()).code());
                assertEquals(0, state.moves.get());
                assertEquals(0, state.presses.get());
                assertEquals(0, state.buttons);
            }
        }
    }

    @Test
    void coordinateMotionStillUsesTheGlobalMenuContext() throws Exception {
        World state = new World();
        state.afterMove = () -> state.menuRevision++;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var pending =
                    engine.uiAction(
                            JsonParser.parseString("{\"coordinates\":{\"x\":55,\"y\":30}}")
                                    .getAsJsonObject(),
                            () -> {});
            var error =
                    assertThrows(ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
            assertEquals(
                    "UI_TARGET_INVALIDATED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(0, state.presses.get());
        }
    }

    @Test
    void ownDragPickupMayChangeTheCarriedStackWithoutLosingTargetOwnership() throws Exception {
        World state = new World();
        state.slots = true;
        state.targetCount = 8;
        state.afterPress =
                () -> {
                    state.targetCount = 0;
                    state.carriedCount = 8;
                    state.menuRevision++;
                };
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var request =
                    pipeline(
                            "[{\"type\":\"ui.drag\",\"fromSelector\":{\"label\":\"A\"},\"toSelector\":{\"label\":\"B\"},\"durationMs\":100,\"segments\":4}]",
                            3000);
            engine.executePipeline(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(1, state.presses.get());
            assertEquals(0, state.buttons);
            assertEquals(8, state.carriedCount);
        }
    }

    @Test
    void cancellationDuringPointerValidationNeverReachesTheNativeMove() throws Exception {
        World state = new World();
        CountDownLatch validated = new CountDownLatch(1), resume = new CountDownLatch(1);
        state.afterPointerValidation =
                () -> {
                    validated.countDown();
                    try {
                        if (!resume.await(2, TimeUnit.SECONDS))
                            throw new AssertionError("test stalled");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                };
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var pending = engine.uiAction(action("A"), () -> {});
            try {
                assertTrue(validated.await(2, TimeUnit.SECONDS));
                assertTrue(pending.cancel(false));
            } finally {
                resume.countDown();
            }
            assertThrows(CancellationException.class, pending::join);
            Thread.sleep(120);
            assertEquals(0, state.moves.get());
            assertEquals(0, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void dragTracksOwnedAllocationButRejectsExternalTargetOrCarriedMetadata() throws Exception {
        for (String change : List.of("owned", "slot", "carried")) {
            World state = new World();
            state.slots = true;
            state.targetCount = 8;
            state.otherCount = 1;
            state.afterPress =
                    () -> {
                        state.targetCount = 0;
                        state.carriedCount = 8;
                        state.menuRevision++;
                    };
            if (change.equals("owned"))
                state.duringMove =
                        () -> {
                            if (state.buttons == 1) {
                                state.carriedCount--;
                                state.otherCount++;
                                state.menuRevision++;
                            }
                        };
            else
                state.afterMove =
                        () -> {
                            if (state.buttons == 1) {
                                if (change.equals("slot")) state.otherData++;
                                else state.carriedData++;
                                state.menuRevision++;
                            }
                        };
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var request =
                        pipeline(
                                "[{\"type\":\"ui.drag\",\"fromSelector\":{\"label\":\"A\"},\"toSelector\":{\"label\":\"B\"},\"durationMs\":100,\"segments\":4}]",
                                3000);
                var pending = engine.executePipeline(request, () -> {});
                if (change.equals("owned")) {
                    pending.get(3, TimeUnit.SECONDS);
                    assertEquals(4, state.carriedCount);
                    assertEquals(5, state.otherCount);
                } else {
                    var failure =
                            assertThrows(
                                    ExecutionException.class,
                                    () -> pending.get(3, TimeUnit.SECONDS));
                    assertEquals(
                            "UI_TARGET_INVALIDATED",
                            ((ProtocolState.ProtocolException) failure.getCause()).code());
                    int stopped = state.moves.get();
                    Thread.sleep(60);
                    assertEquals(stopped, state.moves.get());
                }
                assertEquals(1, state.presses.get());
                assertEquals(0, state.buttons);
            }
        }
    }

    @Test
    void gameCursorChangeDuringMotionCancelsWithoutLateClickOrMove() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var request = engine.uiAction(action("B"), () -> {});
            until(() -> state.moves.get() >= 3);
            state.gameCursorRevision++;
            var error =
                    assertThrows(ExecutionException.class, () -> request.get(2, TimeUnit.SECONDS));
            assertEquals(
                    "UI_TARGET_INVALIDATED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            int stopped = state.moves.get();
            Thread.sleep(100);
            assertEquals(stopped, state.moves.get());
            assertEquals(0, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void queuedCancelDoesNotReleaseActiveInputAndActiveCancelWaitsForDrain() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var active =
                    engine.executePipeline(
                            pipeline(
                                    "[{\"type\":\"mouse.button\",\"button\":0,\"action\":1},{\"type\":\"delay\",\"durationMs\":1500}]",
                                    3000),
                            () -> {});
            until(() -> state.buttons == 1);
            var queued = engine.uiAction(action("A"), () -> {});
            queued.cancel(false);
            assertEquals(0, state.cleanups.get());
            assertEquals(1, state.buttons);
            state.blockedCleanup = new CompletableFuture<>();
            active.cancel(false);
            var after = engine.uiAction(action("B"), () -> {});
            Thread.sleep(80);
            assertEquals(0, state.moves.get());
            state.blockedCleanup.complete(new JsonObject());
            after.get(2, TimeUnit.SECONDS);
            assertEquals(movementSteps(160, 25), state.moves.get());
        }
    }

    @Test
    void deadlineCancelsQueuedAndActiveRequestsWithoutLateEvents() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var active =
                    engine.executePipeline(
                            pipeline(
                                    "[{\"type\":\"delay\",\"durationMs\":500},{\"type\":\"key\",\"key\":87,\"action\":1}]",
                                    80),
                            () -> {});
            var queued =
                    engine.executePipeline(
                            pipeline("[{\"type\":\"key\",\"key\":65,\"action\":1}]", 30), () -> {});
            assertThrows(ExecutionException.class, () -> queued.get(1, TimeUnit.SECONDS));
            assertEquals(
                    0, state.cleanups.get(), "queued deadline must not clean the active owner");
            assertThrows(ExecutionException.class, () -> active.get(1, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertFalse(state.events.contains("key"));
        }
    }

    @Test
    void rawHeldButtonCannotBeStolenByGuiGesture() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            JsonObject down =
                    JsonParser.parseString("{\"type\":\"mouse.button\",\"button\":0,\"action\":1}")
                            .getAsJsonObject();
            engine.rawInput(down, () -> {}, java.util.function.Supplier::get)
                    .get(1, TimeUnit.SECONDS);
            var failed = engine.uiAction(action("A"), () -> {});
            var error =
                    assertThrows(ExecutionException.class, () -> failed.get(1, TimeUnit.SECONDS));
            assertEquals(
                    "POINTER_HELD", ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(1, state.buttons);
            assertEquals(0, state.cleanups.get());
            down.addProperty("action", 0);
            engine.rawInput(down, () -> {}, java.util.function.Supplier::get)
                    .get(1, TimeUnit.SECONDS);
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void cancellingAnOldCompletedPipelineCannotReleaseTheNextRawHeldButton() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            JsonObject request = pipeline("[{\"type\":\"delay\",\"durationMs\":1}]", 1000);
            request.addProperty("cleanupOnComplete", false);
            CompletableFuture<JsonObject> old = engine.executePipeline(request, () -> {});
            old.get(1, TimeUnit.SECONDS);
            JsonObject down =
                    JsonParser.parseString("{\"type\":\"mouse.button\",\"button\":0,\"action\":1}")
                            .getAsJsonObject();
            engine.rawInput(down, () -> {}, java.util.function.Supplier::get)
                    .get(1, TimeUnit.SECONDS);
            assertEquals(1, state.buttons);
            assertEquals(0, state.cleanups.get());
            assertFalse(old.cancel(false), "A completed operation cannot be cancelled");
            assertEquals(
                    1,
                    state.buttons,
                    "Rejected terminal cancellation must not clean a later owner");
            assertEquals(0, state.cleanups.get());
        }
    }

    @Test
    void reentrantCancellationOfACompletedPipelineCannotStartCleanup() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            JsonObject request = pipeline("[{\"type\":\"delay\",\"durationMs\":50}]", 1000);
            request.addProperty("cleanupOnComplete", false);
            CompletableFuture<JsonObject> result = engine.executePipeline(request, () -> {});
            CompletableFuture<Void> observed =
                    result.thenRun(
                            () -> {
                                assertFalse(result.cancel(false));
                                assertEquals(
                                        0,
                                        state.cleanups.get(),
                                        "Terminal observers cannot re-open cleanup");
                            });
            observed.get(1, TimeUnit.SECONDS);
            assertFalse(result.isCancelled());
            assertEquals(0, state.cleanups.get());
        }
    }

    @Test
    void staleAdmissionNeverCleansAnotherLeaseRawInput() throws Exception {
        World state = new World();
        state.buttons = 1;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var rejected =
                    engine.executePipeline(
                            pipeline("[{\"type\":\"key\",\"key\":87,\"action\":1}]", 1000),
                            () -> {
                                throw new ProtocolState.ProtocolException(
                                        "CONTROL_LEASE_REQUIRED", 409, "stale");
                            });
            assertThrows(ExecutionException.class, () -> rejected.get(1, TimeUnit.SECONDS));
            assertEquals(1, state.buttons);
            assertEquals(0, state.cleanups.get());
        }
    }

    @Test
    void publicPipelineCannotSmugglePlayerCommandPrivilege() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var result =
                    engine.executePipeline(
                            pipeline("[{\"type\":\"internal.player.command\"}]", 1000), () -> {});
            var error =
                    assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertEquals(
                    "UNSUPPORTED_PIPELINE_STEP",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
        }
    }

    @Test
    void cancelDuringScreenDwellDoesNotDeliverAnyInput() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var pending = engine.uiAction(action("A"), () -> {});
            Thread.sleep(80);
            pending.cancel(false);
            Thread.sleep(800);
            assertEquals(0, state.moves.get());
            assertEquals(0, state.presses.get());
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void hoverPauseStillValidatesBeforePressAndCancelsImmediately() throws Exception {
        for (boolean cancel : List.of(false, true)) {
            World state = new World();
            try (AutomationEngine engine = new AutomationEngine(state.service())) {
                var pending = engine.uiAction(action("A"), () -> {});
                until(() -> state.moves.get() == movementSteps(50, 25));
                if (cancel) pending.cancel(false);
                else state.revision++;
                assertThrows(Exception.class, () -> pending.get(1, TimeUnit.SECONDS));
                Thread.sleep(220);
                assertEquals(0, state.presses.get());
                assertEquals(0, state.buttons);
            }
        }
    }

    @Test
    void releaseNeverWaitsForDwellAndHeldMoveNeverOvershoots() throws Exception {
        World state = new World();
        state.buttons = 1;
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            engine.rawInput(
                            JsonParser.parseString("{\"type\":\"mouse.move\",\"x\":55,\"y\":30}")
                                    .getAsJsonObject(),
                            () -> {},
                            java.util.function.Supplier::get)
                    .get(1, TimeUnit.SECONDS);
            assertTrue(state.positions.stream().allMatch(x -> x <= 55));
            long before = System.nanoTime();
            engine.rawInput(
                            JsonParser.parseString(
                                            "{\"type\":\"mouse.button\",\"button\":0,\"action\":0}")
                                    .getAsJsonObject(),
                            () -> {},
                            java.util.function.Supplier::get)
                    .get(300, TimeUnit.MILLISECONDS);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 280);
            assertEquals(0, state.buttons);
        }
    }

    @Test
    void outOfBoundsTargetIsNotSilentlyClampedIntoAClick() throws Exception {
        World state = new World();
        try (AutomationEngine engine = new AutomationEngine(state.service())) {
            var result =
                    engine.uiAction(
                            JsonParser.parseString("{\"coordinates\":{\"x\":400,\"y\":30}}")
                                    .getAsJsonObject(),
                            () -> {});
            var error =
                    assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertEquals(
                    "POINTER_OUT_OF_BOUNDS",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertEquals(0, state.presses.get());
        }
        World edge = new World();
        try (AutomationEngine engine = new AutomationEngine(edge.service())) {
            double target = Math.nextDown(320.0);
            JsonObject coordinates = new JsonObject();
            coordinates.addProperty("x", target);
            coordinates.addProperty("y", 30);
            JsonObject request = new JsonObject();
            request.add("coordinates", coordinates);
            engine.uiAction(request, () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(target, edge.x);
            assertTrue(edge.positions.stream().allMatch(x -> x < 320));
        }
    }
}
