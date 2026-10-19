package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PipelineTimingTest {
    @Test
    void monotonicOffsetsAndStagesNeverPretendToMeasureServerEffects() {
        AtomicLong clock = new AtomicLong(10_000_000);
        PipelineTiming timing = new PipelineTiming(clock::get);
        clock.addAndGet(2_000_000);
        timing.started();
        long stage = timing.now();
        clock.addAndGet(3_000_000);
        timing.inputCallback();
        timing.record("nativeCall", stage);
        timing.requestedDelay(40);
        clock.addAndGet(1_000_000);
        timing.inputCallback();
        JsonObject value = timing.snapshot();
        assertEquals(2, value.get("queueMillis").getAsDouble());
        assertEquals(6, value.get("totalMillis").getAsDouble());
        assertEquals(5, value.get("firstInputCallbackOffsetMillis").getAsDouble());
        assertEquals(6, value.get("lastInputCallbackOffsetMillis").getAsDouble());
        assertEquals(2, value.get("inputCallbackCount").getAsInt());
        assertEquals(40, value.get("requestedDelayMillis").getAsInt());
        assertEquals(
                3,
                value.getAsJsonObject("stages")
                        .getAsJsonObject("nativeCall")
                        .get("elapsedMillis")
                        .getAsDouble());
        assertEquals(
                "automation_pipeline_not_http_or_server_effect", value.get("scope").getAsString());
        assertFalse(value.has("effectConfirmed"));
        assertFalse(value.has("serverTick"));
    }

    @Test
    void productionEngineReportsDelayWithoutInventingInputCallbacks() throws Exception {
        AtomicInteger cleanups = new AtomicInteger();
        try (AutomationEngine engine = new AutomationEngine(service(cleanups))) {
            JsonObject value =
                    engine.executePipeline(
                                    request("[{\"type\":\"delay\",\"durationMs\":10}]"), () -> {})
                            .get(3, TimeUnit.SECONDS);
            JsonObject timing = value.getAsJsonObject("timing");
            assertEquals(0, timing.get("inputCallbackCount").getAsInt());
            assertFalse(timing.has("firstInputCallbackOffsetMillis"));
            assertFalse(timing.has("lastInputCallbackOffsetMillis"));
            JsonObject wire =
                    new com.google.gson.Gson()
                            .fromJson(new com.google.gson.Gson().toJson(timing), JsonObject.class);
            assertEquals(timing, wire);
            assertEquals(10, timing.get("requestedDelayMillis").getAsInt());
            assertEquals(
                    1,
                    timing.getAsJsonObject("stages")
                            .getAsJsonObject("delay")
                            .get("calls")
                            .getAsInt());
            assertEquals(1, cleanups.get());
            assertTrue(
                    value.getAsJsonArray("steps")
                                    .get(0)
                                    .getAsJsonObject()
                                    .get("elapsedMillis")
                                    .getAsDouble()
                            >= 0);
        }
    }

    @Test
    void productionEngineMeasuresGuardedCallbacksAndPreservesAutomaticCleanup() throws Exception {
        AtomicInteger cleanups = new AtomicInteger();
        try (AutomationEngine engine = new AutomationEngine(service(cleanups))) {
            JsonObject value =
                    engine.executePipeline(
                                    request("[{\"type\":\"key.tap\",\"key\":87,\"holdMs\":1}]"),
                                    () -> {})
                            .get(3, TimeUnit.SECONDS);
            JsonObject timing = value.getAsJsonObject("timing");
            assertEquals(2, timing.get("inputCallbackCount").getAsInt());
            assertTrue(
                    timing.get("firstInputCallbackOffsetMillis").getAsDouble()
                            <= timing.get("lastInputCallbackOffsetMillis").getAsDouble());
            assertEquals(
                    2,
                    timing.getAsJsonObject("stages")
                            .getAsJsonObject("pointerState")
                            .get("calls")
                            .getAsInt());
            assertEquals(
                    1,
                    timing.getAsJsonObject("stages")
                            .getAsJsonObject("cleanup")
                            .get("calls")
                            .getAsInt());
            assertEquals(1, cleanups.get());
            assertTrue(value.get("cleanupOnComplete").getAsBoolean());
            assertNull(InputDispatchContext.capture());
        }
    }

    @Test
    void gameplayPacingUsesLightweightStateUnlessTheCallerPinsTheWholeMenu() throws Exception {
        for (boolean pinned : new boolean[] {false, true}) {
            AtomicInteger full = new AtomicInteger(),
                    lightweight = new AtomicInteger(),
                    callbacks = new AtomicInteger(),
                    cleanups = new AtomicInteger();
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("gestureEvent")) return null;
                                        JsonObject state = new JsonObject();
                                        if (method.getName().equals("pointerState")
                                                || method.getName().equals("inputPacingState")) {
                                            (method.getName().equals("pointerState")
                                                            ? full
                                                            : lightweight)
                                                    .incrementAndGet();
                                            state.addProperty("screenClass", "");
                                            state.addProperty("guiAbsolute", false);
                                        }
                                        if (method.getName().equals("mouseButton")) {
                                            InputDispatchContext.capture()
                                                    .beforeInput((screen, menu) -> {});
                                            callbacks.incrementAndGet();
                                        }
                                        if (method.getName().equals("releaseAllInput"))
                                            cleanups.incrementAndGet();
                                        return CompletableFuture.completedFuture(state);
                                    });
            try (AutomationEngine engine = new AutomationEngine(service)) {
                JsonObject value =
                        engine.executePipeline(
                                        request(
                                                "["
                                                        + String.join(
                                                                ",",
                                                                java.util.Collections.nCopies(
                                                                        3,
                                                                        "{\"type\":\"mouse.button\",\"button\":0,\"action\":1},{\"type\":\"mouse.button\",\"button\":0,\"action\":0}"))
                                                        + "]"),
                                        () -> {},
                                        java.util.function.Supplier::get,
                                        pinned,
                                        null)
                                .get(3, TimeUnit.SECONDS);
                assertEquals(6, callbacks.get());
                assertEquals(1, cleanups.get());
                assertEquals(6, value.get("stepCount").getAsInt());
                assertEquals(
                        1,
                        value.getAsJsonObject("timing")
                                .getAsJsonObject("stages")
                                .getAsJsonObject("cleanup")
                                .get("calls")
                                .getAsInt());
                assertEquals(pinned ? 9 : 0, full.get());
                assertEquals(pinned ? 0 : 9, lightweight.get());
            }
        }
    }

    @Test
    void measurementCannotAuthorizeExpiredOrRejectedCallbacks() {
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<InputDispatchContext.Permit> captured = new AtomicReference<>();
        CompletableFuture<JsonObject> nativeWork = new CompletableFuture<>();
        InputDispatchContext.run(
                "timing",
                () -> {},
                null,
                null,
                false,
                callbacks::incrementAndGet,
                () -> {
                    captured.set(InputDispatchContext.capture());
                    return nativeWork;
                });
        assertNull(InputDispatchContext.capture());
        captured.get().beforeInput((screen, menu) -> {});
        assertEquals(1, callbacks.get());
        nativeWork.cancel(false);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> captured.get().beforeInput((screen, menu) -> {}));
        assertEquals(1, callbacks.get());
        assertThrows(
                ProtocolState.ProtocolException.class,
                () ->
                        InputDispatchContext.run(
                                "denied",
                                () -> {
                                    throw new ProtocolState.ProtocolException(
                                            "DENIED", 403, "denied");
                                },
                                null,
                                null,
                                false,
                                callbacks::incrementAndGet,
                                () -> CompletableFuture.completedFuture(new JsonObject())));
        assertEquals(1, callbacks.get());
    }

    private static JsonObject request(String steps) {
        return JsonParser.parseString("{\"steps\":" + steps + ",\"timeoutMs\":2000}")
                .getAsJsonObject();
    }

    private static ProbeService service(AtomicInteger cleanups) {
        return (ProbeService)
                Proxy.newProxyInstance(
                        ProbeService.class.getClassLoader(),
                        new Class<?>[] {ProbeService.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("gestureEvent")) return null;
                            JsonObject result = new JsonObject();
                            if (method.getName().equals("pointerState")
                                    || method.getName().equals("inputPacingState")) {
                                result.addProperty("guiAbsolute", false);
                                result.addProperty("screenClass", "");
                            }
                            if (method.getName().equals("key"))
                                InputDispatchContext.capture().beforeInput((screen, menu) -> {});
                            if (method.getName().equals("releaseAllInput"))
                                cleanups.incrementAndGet();
                            return CompletableFuture.completedFuture(result);
                        });
    }
}
