package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

final class WorldActionTest {
    private static final class Game {
        final AtomicInteger corrections = new AtomicInteger(),
                presses = new AtomicInteger(),
                releases = new AtomicInteger(),
                cleanups = new AtomicInteger();
        volatile boolean stale, deny, neverAlign;
        volatile double aimError = 90;
        volatile JsonObject actualHit;
        volatile CompletableFuture<JsonObject> blocked;
        final AtomicInteger progressCalls = new AtomicInteger(), ends = new AtomicInteger();
        volatile int doneAfter = Integer.MAX_VALUE;
        volatile JsonObject options;

        ProbeService service() {
            return (ProbeService)
                    Proxy.newProxyInstance(
                            ProbeService.class.getClassLoader(),
                            new Class<?>[] {ProbeService.class},
                            (p, m, a) -> {
                                if (m.getName().equals("gestureEvent")) return null;
                                JsonObject result = new JsonObject();
                                switch (m.getName()) {
                                    case "worldActionFrame" -> {
                                        if (a.length == 5) options = ((JsonObject) a[4]).deepCopy();
                                        if (stale)
                                            return CompletableFuture.failedFuture(
                                                    new ProtocolState.ProtocolException(
                                                            "WORLD_TARGET_STALE", 409, "Changed"));
                                        if (blocked != null) return blocked;
                                        result.addProperty(
                                                "onTarget", !neverAlign && corrections.get() > 0);
                                        result.addProperty("aimError", aimError);
                                        if (actualHit != null)
                                            result.add("actualHit", actualHit.deepCopy());
                                        result.addProperty("dx", 10);
                                        result.addProperty("dy", 1);
                                        result.add("guard", new JsonObject());
                                        if ((boolean) a[3]) {
                                            presses.incrementAndGet();
                                            result.add(
                                                    "binding",
                                                    JsonParser.parseString(
                                                            "{\"kind\":\"key\",\"code\":80,\"scanCode\":0}"));
                                        }
                                    }
                                    case "worldAimDelta" -> corrections.incrementAndGet();
                                    case "key" -> {
                                        assertEquals(80, a[0]);
                                        assertEquals(0, a[2]);
                                        releases.incrementAndGet();
                                    }
                                    case "releaseAllInput" -> cleanups.incrementAndGet();
                                    case "worldActionProgress" -> {
                                        int calls = progressCalls.incrementAndGet();
                                        result.addProperty("done", calls >= doneAfter);
                                        result.addProperty("nativeUseStarted", true);
                                        result.addProperty(
                                                "nativeUseReleaseObserved", (boolean) a[2]);
                                        result.addProperty("released", (boolean) a[2]);
                                        result.addProperty(
                                                "clientBreakPredicted", calls >= doneAfter);
                                    }
                                    case "worldActionEnd" -> ends.incrementAndGet();
                                    default -> {}
                                }
                                return CompletableFuture.completedFuture(result);
                            });
        }

        void check() {
            if (deny) throw new ProtocolState.ProtocolException("TAKEOVER_REQUIRED", 409, "READ");
        }
    }

    private static JsonObject request(String action) {
        return JsonParser.parseString(
                        "{\"steps\":[{\"type\":\"world.action\",\"targetRef\":\"world:test\",\"action\":\""
                                + action
                                + "\"}],\"timeoutMs\":8000}")
                .getAsJsonObject();
    }

    @Test
    void failedAimPreservesOnlyBoundedPickFactsThroughTheProductionOperation() throws Exception {
        Game game = new Game();
        game.neverAlign = true;
        game.aimError = 0;
        game.actualHit =
                JsonParser.parseString(
                                """
                {"kind":"block","source":"native_interaction_pick","distance":2.5,
                 "position":{"x":0.5,"y":81.5,"z":2,"private":"secret"},
                 "block":{"x":0,"y":81,"z":2},"face":"north","guard":"secret"}
                """)
                        .getAsJsonObject();
        try (var state = new ProtocolState(java.util.Set.of("read"), "test", ignored -> {});
                var engine = new AutomationEngine(game.service())) {
            var future = engine.executePipeline(request("use"), game::check);
            String id = state.startOperation(future).get("operationId").getAsString();
            var operation = state.waitOperation(id, 3000).get(4, TimeUnit.SECONDS);
            assertEquals("WORLD_TARGET_NOT_HIT", operation.get("errorCode").getAsString());
            assertEquals("failed", operation.get("state").getAsString());
            var hit = operation.getAsJsonObject("errorDetails").getAsJsonObject("actualHit");
            assertEquals("block", hit.get("kind").getAsString());
            assertEquals("north", hit.get("face").getAsString());
            assertFalse(operation.toString().contains("secret"));
            assertEquals(0, game.presses.get());
            assertTrue(game.cleanups.get() > 0);
        }
    }

    @Test
    void semanticActionUsesGuardedDeltaAndActualBindingWithoutClaimingEffect() throws Exception {
        Game game = new Game();
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            JsonObject result =
                    engine.executePipeline(request("use"), game::check)
                            .get(4, TimeUnit.SECONDS)
                            .getAsJsonArray("steps")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("result");
            assertEquals(1, game.corrections.get());
            assertEquals(1, game.presses.get());
            assertEquals(1, game.releases.get());
            assertFalse(result.get("effectConfirmed").getAsBoolean());
            assertFalse(result.has("guard"));
            assertEquals("PLAYTEST", result.get("evidenceAuthority").getAsString());
            assertFalse(result.get("directMutationUsed").getAsBoolean());
        }
    }

    @Test
    void aimDoesNotPressAndStaleTargetFailsClosed() throws Exception {
        Game game = new Game();
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            engine.executePipeline(request("aim"), game::check).get(4, TimeUnit.SECONDS);
            assertEquals(0, game.presses.get());
            int corrections = game.corrections.get(), cleanups = game.cleanups.get();
            game.stale = true;
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.executePipeline(request("attack"), game::check)
                                    .get(4, TimeUnit.SECONDS));
            assertEquals(0, game.presses.get());
            assertEquals(corrections, game.corrections.get());
            assertEquals(0, game.releases.get());
            assertEquals(cleanups + 1, game.cleanups.get());
        }
    }

    @Test
    void modeLossAndCancellationPreventLatePress() throws Exception {
        Game game = new Game();
        game.neverAlign = true;
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            var work = engine.executePipeline(request("attack"), game::check);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (game.corrections.get() == 0 && System.nanoTime() < end) Thread.sleep(2);
            game.deny = true;
            assertThrows(ExecutionException.class, () -> work.get(3, TimeUnit.SECONDS));
            assertEquals(0, game.presses.get());
            game.deny = false;
            game.blocked = new CompletableFuture<>();
            var cancel = engine.executePipeline(request("use"), game::check);
            Thread.sleep(40);
            cancel.cancel(true);
            game.blocked.complete(new JsonObject());
            Thread.sleep(80);
            assertEquals(0, game.presses.get());
            assertTrue(game.cleanups.get() > 0);
        }
    }

    @Test
    void deadlineAndQueuedCancellationCannotDispatchLateInput() throws Exception {
        Game game = new Game();
        game.neverAlign = true;
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            JsonObject shortDeadline = request("attack");
            shortDeadline.addProperty("timeoutMs", 30);
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.executePipeline(shortDeadline, game::check)
                                    .get(2, TimeUnit.SECONDS));
            game.blocked = new CompletableFuture<>();
            var first = engine.executePipeline(request("aim"), game::check);
            var queued = engine.executePipeline(request("use"), game::check);
            engine.cancelControlWork();
            game.blocked.complete(new JsonObject());
            Thread.sleep(80);
            assertTrue(first.isCancelled());
            assertTrue(queued.isCancelled());
            assertEquals(0, game.presses.get());
        }
    }

    @Test
    void heldMiningCompletesOnNativePredictionAndUseReleasesBeforeReporting() throws Exception {
        Game game = new Game();
        game.doneAfter = 3;
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            var mine =
                    engine.executePipeline(request("mine"), game::check).get(4, TimeUnit.SECONDS);
            assertTrue(
                    mine.getAsJsonArray("steps")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("result")
                            .get("clientBreakPredicted")
                            .getAsBoolean());
            assertEquals(1, game.ends.get());
            assertEquals(1, game.releases.get());
            game.doneAfter = Integer.MAX_VALUE;
            var held = request("use_hold");
            held.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("holdMs", 100);
            var used =
                    engine.executePipeline(held, game::check)
                            .get(4, TimeUnit.SECONDS)
                            .getAsJsonArray("steps")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("result");
            assertTrue(used.get("nativeUseReleaseObserved").getAsBoolean());
            assertFalse(used.get("effectConfirmed").getAsBoolean());
            assertEquals(2, game.releases.get());
            assertEquals(2, game.ends.get());
        }
    }

    @Test
    void heldTimeoutCancellationAndModeLossCleanUpWithoutLatePress() throws Exception {
        Game game = new Game();
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            var mine = request("mine");
            mine.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("holdMs", 50);
            assertThrows(
                    ExecutionException.class,
                    () -> engine.executePipeline(mine, game::check).get(3, TimeUnit.SECONDS));
            assertTrue(game.cleanups.get() > 0);
            var held = engine.executePipeline(request("use_hold"), game::check);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (game.presses.get() < 2 && System.nanoTime() < end) Thread.sleep(2);
            held.cancel(true);
            Thread.sleep(120);
            int calls = game.progressCalls.get(), presses = game.presses.get();
            Thread.sleep(100);
            assertEquals(calls, game.progressCalls.get());
            assertEquals(presses, game.presses.get());
            assertTrue(game.cleanups.get() > 1);
            var lost = engine.executePipeline(request("use_hold"), game::check);
            while (game.presses.get() < 3 && System.nanoTime() < end) Thread.sleep(2);
            game.deny = true;
            assertThrows(ExecutionException.class, () -> lost.get(3, TimeUnit.SECONDS));
            assertTrue(game.cleanups.get() > 2);
        }
    }

    @Test
    void exactPlacementOptionsReachProductionServiceAndSerializeWithOtherInput() throws Exception {
        Game game = new Game();
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            var place = request("place");
            var step = place.getAsJsonArray("steps").get(0).getAsJsonObject();
            step.addProperty("face", "east");
            step.add("hit", JsonParser.parseString("{\"u\":0.25,\"v\":0.75}"));
            step.addProperty("expectedDestinationBlockId", "minecraft:air");
            engine.executePipeline(place, game::check).get(3, TimeUnit.SECONDS);
            assertEquals("east", game.options.get("face").getAsString());
            assertEquals(.75, game.options.get("v").getAsDouble());
            assertEquals(1, game.presses.get());
            assertEquals(1, game.releases.get());
            assertEquals(1, game.ends.get());
        }
    }

    @Test
    void offhandIntentReachesTheSameSerializedNativePipeline() throws Exception {
        Game game = new Game();
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            var request = request("use_hold");
            var step = request.getAsJsonArray("steps").get(0).getAsJsonObject();
            step.addProperty("hand", "off_hand");
            step.addProperty("holdMs", 100);
            var result = engine.executePipeline(request, game::check).get(4, TimeUnit.SECONDS);
            assertEquals("off_hand", game.options.get("hand").getAsString());
            assertEquals(1, game.presses.get());
            assertEquals(1, game.releases.get());
            assertEquals(1, game.ends.get());
            assertFalse(result.toString().contains("DEBUG_PRIVILEGED"));
        }
    }

    @Test
    void invalidActionAndForeignTokenNeverDispatch() {
        Game game = new Game();
        try (AutomationEngine engine = new AutomationEngine(game.service())) {
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.executePipeline(request("teleport"), game::check)
                                    .get(2, TimeUnit.SECONDS));
            var bad = request("aim");
            bad.getAsJsonArray("steps")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("targetRef", "ui:node");
            assertThrows(
                    ExecutionException.class,
                    () -> engine.executePipeline(bad, game::check).get(2, TimeUnit.SECONDS));
            assertEquals(0, game.corrections.get());
            assertEquals(0, game.presses.get());
        }
    }
}
