package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorldConsumerLifecycleTest {
    private static final Set<String> SCOPES =
            Set.of("read", "debug", "debug.world.read", "control");

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static ProbeService service(
            java.util.function.Supplier<CompletableFuture<JsonObject>> read) {
        return (ProbeService)
                Proxy.newProxyInstance(
                        ProbeService.class.getClassLoader(),
                        new Class<?>[] {ProbeService.class},
                        (proxy, method, args) -> read.get());
    }

    @Test
    void framePreflightsAllSourcesAndMixedCategoriesBeforeAnyCapture() {
        AtomicInteger captures = new AtomicInteger();
        ProbeService service =
                service(
                        () -> {
                            captures.incrementAndGet();
                            return CompletableFuture.completedFuture(new JsonObject());
                        });
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {})) {
            var observation = new ObservationEngine(service, new WorldReadEngine(service, state));
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () ->
                                    observation.stateFrame(
                                            json(
                                                    "{\"reads\":[{\"providerId\":\"minecraft:client/player\"},{\"providerId\":\"minecraft:server/player\"}]}")));
            assertEquals("WORLD_USER_PERCEPTION_UNAVAILABLE", error.code());
            var mixed =
                    observation.stateFrame(
                            json(
                                    "{\"reads\":[{\"providerId\":\"minecraft:client/player\"},{\"providerId\":\"minecraft:client/player\",\"category\":\"DEBUG\"}]}"));
            assertTrue(mixed.isCompletedExceptionally());
            assertEquals(0, captures.get());
        }
    }

    @Test
    void cancellingFrameCancelsEveryNativeChildNotOnlyTheJoinStage() {
        var children = new java.util.ArrayList<CompletableFuture<JsonObject>>();
        ProbeService service =
                service(
                        () -> {
                            var future = new CompletableFuture<JsonObject>();
                            children.add(future);
                            return future;
                        });
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {})) {
            var observation = new ObservationEngine(service, new WorldReadEngine(service, state));
            var frame =
                    observation.stateFrame(
                            json(
                                    "{\"reads\":[{\"providerId\":\"minecraft:client/player\"},{\"providerId\":\"minecraft:client/player\"}]}"));
            assertEquals(2, children.size());
            assertTrue(frame.cancel(false));
            assertTrue(children.stream().allMatch(CompletableFuture::isCancelled));
        }
    }

    @Test
    void completedInternalProviderCannotPublishAfterTakeoverAndReturnToRead() {
        var source = new CompletableFuture<JsonObject>();
        ProbeService service = service(() -> source);
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {})) {
            var observation = new ObservationEngine(service, new WorldReadEngine(service, state));
            var result =
                    observation.read(
                            json(
                                    "{\"providerId\":\"minecraft:client/player\",\"category\":\"DEBUG\"}"));
            var lease = state.acquireLease(60000);
            state.releaseLease(lease.get("leaseId").getAsString(), "test_release");
            source.complete(json("{\"x\":12345}"));
            assertTrue(result.isCompletedExceptionally());
            assertEquals(
                    "WORLD_READ_CONTEXT_CHANGED",
                    ((ProtocolState.ProtocolException)
                                    assertThrows(
                                                    java.util.concurrent.CompletionException.class,
                                                    result::join)
                                            .getCause())
                            .code());
        }
    }

    @Test
    void publicationTicketRejectsModeRoundTripAndShutdown() {
        ProbeService service = service(() -> CompletableFuture.completedFuture(new JsonObject()));
        var state = new ProtocolState(SCOPES, "test", ignored -> {});
        var reads = new WorldReadEngine(service, state);
        Runnable ticket = reads.publicationGuard("DEBUG");
        var lease = state.acquireLease(60000);
        state.releaseLease(lease.get("leaseId").getAsString(), "test_release");
        assertEquals(
                "WORLD_READ_CONTEXT_CHANGED",
                assertThrows(ProtocolState.ProtocolException.class, ticket::run).code());
        Runnable closing = reads.publicationGuard("DEBUG");
        state.close();
        assertThrows(ProtocolState.ProtocolException.class, closing::run);
    }

    @Test
    void internalFailureMessageCannotLeakAfterAuthorityWasLost() {
        var source = new CompletableFuture<JsonObject>();
        ProbeService service = service(() -> source);
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {})) {
            var reads = new WorldReadEngine(service, state);
            var result = reads.read("DEBUG", "client/player", new JsonObject(), () -> source);
            state.acquireLease(60000);
            source.completeExceptionally(
                    new IllegalStateException("hidden world coordinate 12345"));
            var cause =
                    assertThrows(java.util.concurrent.CompletionException.class, result::join)
                            .getCause();
            assertEquals(
                    "WORLD_CATEGORY_MODE_MISMATCH",
                    ((ProtocolState.ProtocolException) cause).code());
            assertFalse(cause.getMessage().contains("12345"));
        }
    }

    @Test
    void failedChildCancelsOtherJoinedWorldReads() {
        var failed = new CompletableFuture<JsonObject>();
        var pending = new CompletableFuture<JsonObject>();
        var joined =
                io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.all(
                        java.util.List.of(failed, pending));
        failed.completeExceptionally(new IllegalStateException("fixture failure"));
        assertTrue(joined.isCompletedExceptionally());
        assertTrue(pending.isCancelled());
    }

    @Test
    void waitKeepsItsOriginalModeGenerationAcrossPolling() throws Exception {
        var source = new CompletableFuture<JsonObject>();
        ProbeService service = service(() -> source);
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var userEvents = new EventHub("test", () -> source);
                var debugEvents = new EventHub("test", () -> source);
                var conditions =
                        new ConditionEngine(
                                service,
                                state,
                                userEvents,
                                debugEvents,
                                null,
                                new ObservationEngine(
                                        service, new WorldReadEngine(service, state)))) {
            var wait =
                    conditions.waitUntil(
                            json(
                                    "{\"type\":\"player\",\"category\":\"DEBUG\",\"expected\":{\"available\":true}}"),
                            2000);
            source.complete(json("{\"available\":false}"));
            var lease = state.acquireLease(60000);
            state.releaseLease(lease.get("leaseId").getAsString(), "test_release");
            var error =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> wait.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(
                    "WORLD_READ_CONTEXT_CHANGED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
        }
    }

    @Test
    void cancellingSceneWaitCancelsItsNativeRead() {
        var source = new CompletableFuture<JsonObject>();
        ProbeService service = service(() -> source);
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var userEvents = new EventHub("test", () -> source);
                var debugEvents = new EventHub("test", () -> source);
                var conditions =
                        new ConditionEngine(
                                service,
                                state,
                                userEvents,
                                debugEvents,
                                null,
                                new ObservationEngine(
                                        service, new WorldReadEngine(service, state)))) {
            var wait =
                    conditions.waitUntil(
                            json("{\"type\":\"scene\",\"expected\":{\"available\":true}}"), 2000);
            assertTrue(wait.cancel(false));
            assertTrue(source.isCancelled());
        }
    }
}
