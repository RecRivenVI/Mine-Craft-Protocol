package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.WorldRegionPages;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class WorldRegionEngineTest {
    @FunctionalInterface
    private interface NativeRegion {
        CompletableFuture<JsonObject> read(
                String action, JsonObject query, String owner, Runnable authorization);
    }

    private static ProbeService service(NativeRegion read) {
        return (ProbeService)
                Proxy.newProxyInstance(
                        ProbeService.class.getClassLoader(),
                        new Class<?>[] {ProbeService.class},
                        (proxy, method, arguments) -> {
                            assertEquals("clientWorldRegion", method.getName());
                            return read.read(
                                    (String) arguments[0],
                                    (JsonObject) arguments[1],
                                    (String) arguments[2],
                                    (Runnable) arguments[3]);
                        });
    }

    private static ProtocolState state() {
        return new ProtocolState(
                Set.of("read"),
                "authenticated-region-principal",
                reason -> CompletableFuture.completedFuture(new JsonObject()),
                ignored -> {});
    }

    private static WorldRegionPages.View view(AtomicInteger reads) {
        return new WorldRegionPages.View() {
            public WorldRegionPages.Context context() {
                return new WorldRegionPages.Context(
                        "real-native-context", "minecraft:overworld", .5, .5, .5, 1);
            }

            public WorldRegionPages.Cell cell(int x, int y, int z) {
                reads.incrementAndGet();
                return WorldRegionPages.Cell.known("minecraft:stone", Map.of());
            }
        };
    }

    @Test
    void shutdownDeniesGuardAndLatePublicationOfAnActuallyCapturedPage() {
        AtomicInteger reads = new AtomicInteger();
        var captured = new AtomicReference<JsonObject>();
        var nativeGuard = new AtomicReference<Runnable>();
        var source = new CompletableFuture<JsonObject>();
        try (var state = state();
                var pages = new WorldRegionPages()) {
            var engine =
                    new WorldReadEngine(
                            service(
                                    (action, query, owner, authorization) -> {
                                        nativeGuard.set(authorization);
                                        captured.set(
                                                pages.read(
                                                        query, owner, view(reads), authorization));
                                        return source;
                                    }),
                            state);
            var pending = engine.region(request());
            assertNotNull(captured.get());
            assertTrue(captured.get().get("complete").getAsBoolean());
            assertEquals(1, reads.get());
            state.close();
            assertEquals(
                    "CONTROL_SESSION_CLOSED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> nativeGuard.get().run())
                            .code());
            source.complete(captured.get());
            var failure = assertThrows(CompletionException.class, pending::join);
            assertInstanceOf(ProtocolState.ProtocolException.class, failure.getCause());
            assertEquals(
                    "CONTROL_SESSION_CLOSED",
                    ((ProtocolState.ProtocolException) failure.getCause()).code());
            assertEquals(1, reads.get());
        }
    }

    @Test
    void cancellationPropagatesToNativeAndOnlyThenAllowsAnotherPageAdmission() {
        AtomicInteger calls = new AtomicInteger(), reads = new AtomicInteger();
        var source = new AtomicReference<>(new CompletableFuture<JsonObject>());
        var nativeGuard = new AtomicReference<Runnable>();
        var nativeQuery = new AtomicReference<JsonObject>();
        var nativeOwner = new AtomicReference<String>();
        try (var state = state();
                var pages = new WorldRegionPages()) {
            var engine =
                    new WorldReadEngine(
                            service(
                                    (action, query, owner, authorization) -> {
                                        calls.incrementAndGet();
                                        nativeGuard.set(authorization);
                                        nativeQuery.set(query);
                                        nativeOwner.set(owner);
                                        return source.get();
                                    }),
                            state);
            var pending = engine.region(request());
            var busy =
                    assertThrows(
                            ProtocolState.ProtocolException.class, () -> engine.region(request()));
            assertEquals("WORLD_REGION_BUSY", busy.code());
            assertEquals(429, busy.httpStatus());
            assertEquals(50, busy.details().get("retryAfterMillis").getAsInt());
            assertEquals(1, calls.get());
            var cancelledSource = source.get();
            assertTrue(pending.cancel(false));
            assertTrue(cancelledSource.isCancelled());
            assertEquals(0, reads.get());
            var next = new CompletableFuture<JsonObject>();
            source.set(next);
            var restarted = engine.region(request());
            assertEquals(2, calls.get());
            next.complete(
                    pages.read(
                            nativeQuery.get(), nativeOwner.get(), view(reads), nativeGuard.get()));
            assertEquals("USER", restarted.join().get("dataCategory").getAsString());
            assertEquals(1, reads.get());
        }
    }

    @Test
    void queuedQueryIsDetachedAndTheOnlyOwnerIsAuthenticatedRuntimeState() {
        var nativeQuery = new AtomicReference<JsonObject>();
        var nativeOwner = new AtomicReference<String>();
        var nativeGuard = new AtomicReference<Runnable>();
        var source = new CompletableFuture<JsonObject>();
        AtomicInteger reads = new AtomicInteger();
        try (var state = state();
                var pages = new WorldRegionPages()) {
            var engine =
                    new WorldReadEngine(
                            service(
                                    (action, query, owner, authorization) -> {
                                        assertEquals("read", action);
                                        nativeQuery.set(query);
                                        nativeOwner.set(owner);
                                        nativeGuard.set(authorization);
                                        return source;
                                    }),
                            state);
            JsonObject request = request();
            var pending = engine.region(request);
            request.getAsJsonObject("query").getAsJsonObject("min").addProperty("x", 20);
            assertEquals(0, nativeQuery.get().getAsJsonObject("min").get("x").getAsInt());
            assertEquals(state.principalId(), nativeOwner.get());
            source.complete(
                    pages.read(
                            nativeQuery.get(), nativeOwner.get(), view(reads), nativeGuard.get()));
            assertEquals(1, pending.join().get("requestedCellCount").getAsInt());
            assertEquals(1, reads.get());
            assertFalse(
                    state.modeStatus()
                            .getAsJsonObject("authorization")
                            .getAsJsonObject("inputLease")
                            .get("held")
                            .getAsBoolean());
        }
    }

    @Test
    void realNativeHelperFailureIsMappedAndReleasesThePageAdmission() {
        AtomicInteger reads = new AtomicInteger();
        try (var state = state();
                var pages = new WorldRegionPages()) {
            var engine =
                    new WorldReadEngine(
                            service(
                                    (action, query, owner, authorization) -> {
                                        try {
                                            return CompletableFuture.completedFuture(
                                                    pages.read(
                                                            query,
                                                            owner,
                                                            view(reads),
                                                            authorization));
                                        } catch (Throwable failure) {
                                            return CompletableFuture.failedFuture(failure);
                                        }
                                    }),
                            state);
            JsonObject invalid = request();
            invalid.getAsJsonObject("query").addProperty("maxBlocks", "1");
            var failure =
                    assertThrows(CompletionException.class, () -> engine.region(invalid).join());
            assertInstanceOf(ProtocolState.ProtocolException.class, failure.getCause());
            var protocol = (ProtocolState.ProtocolException) failure.getCause();
            assertEquals("INVALID_WORLD_REGION_QUERY", protocol.code());
            assertEquals(400, protocol.httpStatus());
            assertEquals(0, reads.get());
            assertTrue(engine.region(request()).join().get("complete").getAsBoolean());
            assertEquals(1, reads.get());
        }
    }

    private static JsonObject request() {
        return JsonParser.parseString(
                        "{\"query\":{\"min\":{\"x\":0,\"y\":0,\"z\":0},\"max\":{\"x\":0,\"y\":0,\"z\":0}}}")
                .getAsJsonObject();
    }
}
