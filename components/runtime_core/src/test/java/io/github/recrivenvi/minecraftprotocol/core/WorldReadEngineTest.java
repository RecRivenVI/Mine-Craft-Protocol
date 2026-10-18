package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorldReadEngineTest {
    private static final Set<String> DEBUG_SCOPES =
            Set.of("read", "debug", "debug.world.read", "control");

    private static ProtocolState state(Set<String> scopes) {
        return new ProtocolState(
                scopes,
                "world-read-test",
                reason -> CompletableFuture.completedFuture(new JsonObject()),
                ignored -> {});
    }

    private static ProbeService service(CompletableFuture<JsonObject> ui) {
        return (ProbeService)
                Proxy.newProxyInstance(
                        ProbeService.class.getClassLoader(),
                        new Class<?>[] {ProbeService.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("clientWorldRead")) {
                                return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork
                                        .map(
                                                ui,
                                                value -> {
                                                    JsonObject result = value.deepCopy();
                                                    result.addProperty("dataCategory", "USER");
                                                    return result;
                                                });
                            }
                            throw new AssertionError(
                                    "Unexpected service call: " + method.getName());
                        });
    }

    @Test
    void userNeverReadsInternalPlayerAndUnavailableSceneDoesNotProbe() {
        try (ProtocolState state = state(Set.of("read"))) {
            WorldReadEngine engine =
                    new WorldReadEngine(
                            service(CompletableFuture.completedFuture(new JsonObject())), state);
            AtomicInteger calls = new AtomicInteger();
            var raw =
                    (java.util.function.Supplier<CompletableFuture<JsonObject>>)
                            () -> {
                                calls.incrementAndGet();
                                return CompletableFuture.completedFuture(new JsonObject());
                            };
            assertEquals(
                    "USER",
                    engine.read(null, "client/player", new JsonObject(), raw)
                            .join()
                            .get("dataCategory")
                            .getAsString());
            assertEquals(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            engine.read(
                                                    "USER", "server/player", new JsonObject(), raw))
                            .code());
            assertEquals(0, calls.get());
            assertFalse(state.controlSession().snapshot().agentControlled());
        }
    }

    @Test
    void debugNeedsIndependentReadScopesNotWriteArmOrLease() {
        for (Set<String> scopes :
                Set.of(
                        Set.of("read"),
                        Set.of("read", "debug"),
                        Set.of("read", "debug.world.read"))) {
            try (ProtocolState state = state(scopes)) {
                WorldReadEngine engine = new WorldReadEngine(service(null), state);
                assertEquals(
                        "SCOPE_DENIED",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () ->
                                                engine.read(
                                                        "DEBUG",
                                                        "client/player",
                                                        new JsonObject(),
                                                        () -> {
                                                            throw new AssertionError(
                                                                    "must not dispatch");
                                                        }))
                                .code());
            }
        }
        try (ProtocolState state = state(DEBUG_SCOPES)) {
            WorldReadEngine engine = new WorldReadEngine(service(null), state);
            JsonObject raw = new JsonObject();
            raw.addProperty("x", 1.23456789);
            assertEquals(
                    1.23456789,
                    engine.read(
                                    "DEBUG",
                                    "client/player",
                                    new JsonObject(),
                                    () -> CompletableFuture.completedFuture(raw))
                            .join()
                            .get("x")
                            .getAsDouble());
            assertFalse(state.hasScope("debug.write"));
            state.acquireLease(60000);
            assertEquals(
                    1.23456789,
                    engine.read(
                                    "DEBUG",
                                    "client/player",
                                    new JsonObject(),
                                    () -> CompletableFuture.completedFuture(raw))
                            .join()
                            .get("x")
                            .getAsDouble());
        }
    }

    @Test
    void operateRetainsUserReadAndInvalidCategoryDoesNotDispatch() {
        try (ProtocolState state = state(DEBUG_SCOPES)) {
            WorldReadEngine engine =
                    new WorldReadEngine(
                            service(CompletableFuture.completedFuture(new JsonObject())), state);
            state.controlSession().select(AgentControlSession.Mode.OPERATE, "test");
            assertEquals(
                    "USER",
                    engine.read(
                                    "USER",
                                    "client/player",
                                    new JsonObject(),
                                    () -> {
                                        throw new AssertionError("internal fallback");
                                    })
                            .join()
                            .get("dataCategory")
                            .getAsString());
            assertEquals(
                    "INVALID_WORLD_CATEGORY",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            engine.read(
                                                    "CLIENT_KNOWN",
                                                    "client/player",
                                                    new JsonObject(),
                                                    () -> null))
                            .code());
        }
    }

    @Test
    void authorizedReadSurvivesReadTakeoverRead() {
        try (ProtocolState state = state(DEBUG_SCOPES)) {
            CompletableFuture<JsonObject> source = new CompletableFuture<>();
            WorldReadEngine engine = new WorldReadEngine(service(null), state);
            CompletableFuture<JsonObject> result =
                    engine.read("DEBUG", "client/player", new JsonObject(), () -> source);
            JsonObject lease = state.acquireLease(60000);
            state.releaseLease(lease.get("leaseId").getAsString(), "test_release");
            source.complete(new JsonObject());
            assertEquals("DEBUG", result.join().get("dataCategory").getAsString());
        }
    }

    @Test
    void cancellationPropagatesAndShutdownRejectsLatePublication() {
        ProtocolState state = state(DEBUG_SCOPES);
        CompletableFuture<JsonObject> source = new CompletableFuture<>();
        WorldReadEngine engine = new WorldReadEngine(service(null), state);
        CompletableFuture<JsonObject> result =
                engine.read("DEBUG", "client/player", new JsonObject(), () -> source);
        assertTrue(result.cancel(false));
        assertTrue(source.isCancelled());
        CompletableFuture<JsonObject> lateSource = new CompletableFuture<>();
        CompletableFuture<JsonObject> late =
                engine.read("DEBUG", "client/player", new JsonObject(), () -> lateSource);
        state.close();
        lateSource.complete(new JsonObject());
        assertTrue(late.isCompletedExceptionally());
    }

    @Test
    void userModelPropagatesCancellationButDoesNotConfuseModeWithReadAuthority() {
        try (ProtocolState state = state(DEBUG_SCOPES)) {
            var nativeRead = new CompletableFuture<JsonObject>();
            WorldReadEngine reads = new WorldReadEngine(service(nativeRead), state);
            var pending =
                    reads.read(
                            "USER",
                            "client/player",
                            new JsonObject(),
                            () -> {
                                throw new AssertionError("internal fallback");
                            });
            assertTrue(pending.cancel(false));
            assertTrue(nativeRead.isCancelled());
            var lateNative = new CompletableFuture<JsonObject>();
            var late = new WorldReadEngine(service(lateNative), state).scene("USER");
            state.controlSession().select(AgentControlSession.Mode.OPERATE, "test");
            lateNative.complete(new JsonObject());
            assertEquals("USER", late.join().get("dataCategory").getAsString());
        }
    }
}
