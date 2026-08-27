package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.safety.InteractionPacing;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

final class PointerGestureTest {
    private static final class World {
        volatile int revision = 1, width = 320;
        volatile long gameCursorRevision = 1;
        volatile boolean moveTarget;
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

        JsonObject tree() {
            JsonObject tree = new JsonObject();
            tree.addProperty("screenRevision", revision);
            tree.addProperty("gameCursorRevision", gameCursorRevision);
            tree.addProperty("menuRevision", 1);
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
                node.addProperty("class", "Button");
                node.addProperty("role", "button");
                node.addProperty("label", i == 0 ? "A" : "B");
                node.addProperty("x", i == 0 ? (moveTarget ? 80 : 40) : 150);
                node.addProperty("y", 20);
                node.addProperty("width", 30);
                node.addProperty("height", 20);
                node.addProperty("active", true);
                node.addProperty("visible", true);
                JsonArray actions = new JsonArray();
                actions.add("click");
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
                                JsonObject result = new JsonObject();
                                switch (method.getName()) {
                                    case "pointerState", "uiTree" -> result = tree();
                                    case "inputState" ->
                                            result.addProperty("pressedButtonCount", buttons);
                                    case "mouseMoveGuarded" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[2], tree());
                                        x = (double) args[0];
                                        y = (double) args[1];
                                        moves.incrementAndGet();
                                        events.add("move");
                                        positions.add(x);
                                    }
                                    case "mouseButtonGuarded" -> {
                                        AutomationEngine.validatePointerGuard(
                                                (JsonObject) args[3], tree());
                                        if ((int) args[1] == 1) {
                                            presses.incrementAndGet();
                                            buttons++;
                                            events.add("press:" + Math.round(x));
                                            pressTimes.add(System.nanoTime());
                                        } else {
                                            releases.incrementAndGet();
                                            buttons = 0;
                                            events.add("release");
                                        }
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
                                        }
                                    }
                                    case "key" -> events.add("key");
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
