package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ServerWorldDebugTest {
    static final class World implements ServerWorldDebug.Host {
        final java.util.concurrent.ConcurrentLinkedDeque<Runnable> queue =
                new java.util.concurrent.ConcurrentLinkedDeque<>();
        String blockId = "minecraft:air", lifecycle = "chunk1";
        int mutations;
        boolean immediate = true;
        final ObservationRevisionTracker revisions = new ObservationRevisionTracker();

        public void schedule(Runnable work) {
            if (immediate) work.run();
            else queue.add(work);
        }

        public JsonObject world() {
            return json("{\"serverTick\":0}");
        }

        public JsonObject block(int x, int y, int z) {
            JsonObject value =
                    json(
                            "{\"key\":\"8,100,8\",\"x\":8,\"y\":100,\"z\":8,\"available\":true,\"loadRequested\":false,\"properties\":{}}");
            value.addProperty("blockId", blockId);
            value.addProperty("lifecycleId", lifecycle);
            return value;
        }

        public void setBlock(int x, int y, int z, String id, JsonObject request) {
            blockId = id;
            mutations++;
        }

        public JsonObject fingerprint() {
            JsonObject value = new JsonObject();
            value.addProperty("worldFingerprint", "a".repeat(64));
            value.addProperty("sessionEpoch", revisions.sessionEpoch());
            return value;
        }

        public JsonObject capabilities() {
            return fingerprint();
        }

        public JsonObject observationCapabilities() {
            return fingerprint();
        }

        private JsonObject reference() {
            return revisions.revision("block", "8,100,8", lifecycle, block(8, 100, 8));
        }

        public java.util.concurrent.CompletableFuture<JsonObject> observe(
                JsonObject request, DeepObservationRequestContext context) {
            // The fake resource owner validates its native selector, not the transport wrapper.
            try {
                request.getAsJsonObject("selector")
                        .getAsJsonArray("blocks")
                        .get(0)
                        .getAsJsonObject()
                        .get("x")
                        .getAsBigDecimal()
                        .intValueExact();
            } catch (RuntimeException invalid) {
                throw new ProtocolState.ProtocolException(
                        "INVALID_DEBUG_REQUEST", 400, "Invalid native coordinate");
            }
            return queued(
                    () -> {
                        if (context.isCancelled())
                            throw new java.util.concurrent.CancellationException();
                        JsonObject value = new JsonObject();
                        value.addProperty("dataCategory", "DEBUG");
                        com.google.gson.JsonArray refs = new com.google.gson.JsonArray();
                        refs.add(reference());
                        value.add("resourceRevisionRefs", refs);
                        return value;
                    });
        }

        public java.util.concurrent.CompletableFuture<JsonObject> mutate(
                JsonObject request, DebugMutationAuthorization authority) {
            return queued(
                    () -> {
                        if (authority.isCancelled())
                            throw new java.util.concurrent.CancellationException();
                        try (var permit =
                                authority.authorize(
                                        "a".repeat(64),
                                        revisions.sessionEpoch(),
                                        "world",
                                        "world")) {
                            if (!"a"
                                    .repeat(64)
                                    .equals(request.get("worldFingerprint").getAsString()))
                                throw new ProtocolState.ProtocolException(
                                        "WORLD_FINGERPRINT_MISMATCH", 409, "Wrong world");
                            ResourceVersionVerifier.verifyAuthorized(
                                    request.getAsJsonObject("expectedResourceVersion"),
                                    reference());
                            JsonObject before = block(8, 100, 8), beforeRef = reference();
                            setBlock(8, 100, 8, request.get("blockId").getAsString(), request);
                            JsonObject value = new JsonObject();
                            value.addProperty("evidenceAuthority", "DEBUG_PRIVILEGED");
                            value.addProperty("gameplayEvidence", false);
                            value.addProperty("operation", "world.block.set");
                            value.add("before", before);
                            value.add("after", block(8, 100, 8));
                            value.add("beforeResourceVersion", beforeRef);
                            value.add("afterResourceVersion", reference());
                            return value;
                        }
                    });
        }

        public java.util.concurrent.CompletableFuture<JsonObject> storageRead(JsonObject request) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new UnsupportedOperationException("No fake disk"));
        }

        private java.util.concurrent.CompletableFuture<JsonObject> queued(
                java.util.function.Supplier<JsonObject> action) {
            var future = new java.util.concurrent.CompletableFuture<JsonObject>();
            schedule(
                    () -> {
                        if (future.isDone()) return;
                        try {
                            future.complete(action.get());
                        } catch (Throwable failure) {
                            future.completeExceptionally(failure);
                        }
                    });
            return future;
        }

        public void close() {}
    }

    static JsonObject query() {
        return json(
                "{\"category\":\"DEBUG\",\"perspective\":\"server_authoritative\",\"domains\":[\"blocks\"],\"selector\":{\"blocks\":[{\"x\":8,\"y\":100,\"z\":8}]}}");
    }

    static DeepObservationRequestContext context() {
        return new DeepObservationRequestContext(
                Set.of("read", "debug", "debug.world.read"),
                "test",
                "request",
                "connection",
                0,
                event -> {});
    }

    static JsonObject mutation(ServerWorldDebug engine, JsonObject before) {
        JsonObject value =
                json(
                        "{\"operation\":\"world.block.set\",\"x\":8,\"y\":100,\"z\":8,\"blockId\":\"minecraft:stone\"}");
        value.add("worldFingerprint", engine.fingerprint().get("worldFingerprint"));
        value.add("expectedResourceVersion", before.getAsJsonArray("resourceRevisionRefs").get(0));
        return value;
    }

    static DebugMutationAuthorization authorization(ServerWorldDebug engine) {
        return new DebugMutationAuthorization() {
            public Permit authorize(
                    String fingerprint, String epoch, String domain, String namespace) {
                assertEquals(
                        engine.fingerprint().get("worldFingerprint").getAsString(), fingerprint);
                assertEquals(engine.fingerprint().get("sessionEpoch").getAsString(), epoch);
                assertEquals("world", domain);
                assertEquals("world", namespace);
                return () -> {};
            }

            public boolean isCancelled() {
                return false;
            }

            public boolean hasScope(String scope) {
                return true;
            }

            public String principalId() {
                return "test";
            }

            public String debugArmId() {
                return "test-arm";
            }
        };
    }

    @Test
    void versionsLifecycleAndWorldIdentityRejectBeforeNativeEffects() {
        World host = new World();
        try (var engine = new ServerWorldDebug(host)) {
            JsonObject before = engine.observe(query(), context()).join(),
                    mutation = mutation(engine, before);
            JsonObject applied = engine.mutate(mutation, authorization(engine)).join();
            assertEquals("DEBUG_PRIVILEGED", applied.get("evidenceAuthority").getAsString());
            assertFalse(applied.get("gameplayEvidence").getAsBoolean());
            assertEquals(
                    "STALE_RESOURCE_REVISION",
                    failure(() -> engine.mutate(mutation, authorization(engine)).join()).code());
            assertEquals(1, host.mutations);
            var fresh = mutation(engine, engine.observe(query(), context()).join());
            host.lifecycle = "chunk2";
            assertEquals(
                    "RESOURCE_MISMATCH",
                    failure(() -> engine.mutate(fresh, authorization(engine)).join()).code());
            assertEquals(1, host.mutations);
            fresh.addProperty("worldFingerprint", "0".repeat(64));
            assertEquals(
                    "WORLD_FINGERPRINT_MISMATCH",
                    failure(() -> engine.mutate(fresh, authorization(engine)).join()).code());
            assertEquals(1, host.mutations);
        }
    }

    @Test
    void closeCancellationAndCancelledObservationCannotReachNativeWorld() {
        World host = new World();
        try (var engine = new ServerWorldDebug(host)) {
            JsonObject mutation = mutation(engine, engine.observe(query(), context()).join());
            host.immediate = false;
            var cancelled = engine.mutate(mutation, authorization(engine));
            cancelled.cancel(false);
            host.queue.remove().run();
            assertEquals(0, host.mutations);
            var closing = engine.mutate(mutation, authorization(engine));
            engine.close();
            assertTrue(closing.isCancelled());
            host.queue.remove().run();
            assertEquals(0, host.mutations);
        }
        try (var engine = new ServerWorldDebug(host)) {
            var context = context();
            var pending = engine.observe(query(), context);
            context.cancel("request_cancelled");
            host.queue.remove().run();
            assertTrue(pending.isCompletedExceptionally());
        }
    }

    @Test
    void unsupportedPerspectiveAndFractionalCoordinatesAreNotSilentlyIgnored() {
        World host = new World();
        try (var engine = new ServerWorldDebug(host)) {
            JsonObject perspective = query();
            perspective.addProperty("perspective", "both");
            assertEquals(
                    "CAPABILITY_UNAVAILABLE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> engine.observe(perspective, context()))
                            .code());
            JsonObject invalid = query();
            invalid.getAsJsonObject("selector")
                    .getAsJsonArray("blocks")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("x", 8.5);
            assertEquals(
                    "INVALID_DEBUG_REQUEST",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> engine.observe(invalid, context()))
                            .code());
            JsonObject unsupported = query();
            unsupported.getAsJsonObject("selector").addProperty("chunkRadius", 2);
            assertNotNull(engine.observe(unsupported, context()).join());
            assertEquals(0, host.mutations);
        }
    }

    static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    static ProtocolState.ProtocolException failure(Runnable action) {
        Throwable cause = assertThrows(java.util.concurrent.CompletionException.class, action::run);
        while (cause.getCause() != null) cause = cause.getCause();
        return assertInstanceOf(ProtocolState.ProtocolException.class, cause);
    }
}
