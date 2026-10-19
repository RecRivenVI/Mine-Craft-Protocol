package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.WorldRegionPages;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorldRegionTransportTest {
    private static final String TOKEN = "world-region-http-test";
    private static final String PATH = "/v0/world/region";
    @TempDir Path artifacts;

    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
    }

    private final class Fixture implements AutoCloseable {
        final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        final AtomicInteger regionCalls = new AtomicInteger(), cells = new AtomicInteger();
        final AtomicInteger internalReads = new AtomicInteger(), inputCalls = new AtomicInteger();
        final AtomicInteger cancellationGuards = new AtomicInteger();
        final AtomicBoolean closing = new AtomicBoolean();
        final WorldRegionPages pages = new WorldRegionPages();
        final ExecutorService owner =
                Executors.newSingleThreadExecutor(
                        action -> {
                            Thread thread = new Thread(action, "region-test-native-owner");
                            thread.setDaemon(true);
                            return thread;
                        });
        final HttpClient http = HttpClient.newHttpClient();
        final ProbeTransport transport;
        final int port;
        volatile Gate gate;
        volatile String trustedOwner;
        volatile CompletableFuture<JsonObject> nativePage;
        volatile CountDownLatch nativeFinished;
        volatile WorldRegionPages.Context context =
                new WorldRegionPages.Context(
                        "native-world-player-1", "minecraft:overworld", .5, .5, .5, 42);

        Fixture(String scopes) throws Exception {
            System.setProperty("minecraft.protocol.scopes", scopes);
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
                                        if (method.getName().equals("clientWorldRegion")) {
                                            regionCalls.incrementAndGet();
                                            return read(
                                                    (String) args[0],
                                                    (JsonObject) args[1],
                                                    (String) args[2],
                                                    (Runnable) args[3]);
                                        }
                                        if (Set.of(
                                                        "blockState",
                                                        "serverBlockState",
                                                        "serverPlayerState",
                                                        "serverEntities",
                                                        "formalDeepObservation")
                                                .contains(method.getName()))
                                            internalReads.incrementAndGet();
                                        if (Set.of(
                                                        "key",
                                                        "keyGuarded",
                                                        "mouseMove",
                                                        "mouseMoveGuarded",
                                                        "mouseDelta",
                                                        "mouseButton",
                                                        "mouseButtonGuarded",
                                                        "mouseScroll",
                                                        "mouseScrollGuarded",
                                                        "character")
                                                .contains(method.getName()))
                                            inputCalls.incrementAndGet();
                                        return CompletableFuture.completedFuture(new JsonObject());
                                    });
            transport = new ProbeTransport(service, TOKEN, 0, "region-test-target");
            transport.startAsync();
            port = transport.boundPort().get(5, TimeUnit.SECONDS);
            boolean ready = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                try {
                    get("/v0/session");
                    ready = true;
                    break;
                } catch (java.io.IOException notListeningYet) {
                    Thread.sleep(10);
                }
            }
            if (!ready) {
                close();
                throw new AssertionError("Owned region HTTP transport did not start");
            }
        }

        private CompletableFuture<JsonObject> read(
                String action, JsonObject query, String principal, Runnable authorization) {
            trustedOwner = principal;
            var result = new CompletableFuture<JsonObject>();
            var finished = new CountDownLatch(1);
            nativePage = result;
            nativeFinished = finished;
            JsonObject detached = query.deepCopy();
            owner.execute(
                    () -> {
                        try {
                            if (result.isDone()) return;
                            Runnable guard =
                                    () -> {
                                        if (result.isDone() || closing.get()) {
                                            cancellationGuards.incrementAndGet();
                                            throw new java.util.concurrent.CancellationException(
                                                    "Owned page cancelled");
                                        }
                                        authorization.run();
                                    };
                            guard.run();
                            JsonObject value;
                            if (action.equals("cancel")) {
                                value = new JsonObject();
                                value.addProperty("type", "world.region.cancel");
                                value.addProperty("dataCategory", "USER");
                                value.addProperty(
                                        "cancelled",
                                        pages.cancel(
                                                detached.get("cursor").getAsString(), principal));
                            } else {
                                value =
                                        pages.read(
                                                detached,
                                                principal,
                                                new WorldRegionPages.View() {
                                                    public WorldRegionPages.Context context() {
                                                        return context;
                                                    }

                                                    public WorldRegionPages.Cell cell(
                                                            int x, int y, int z) {
                                                        cells.incrementAndGet();
                                                        Gate block = gate;
                                                        if (block != null) {
                                                            gate = null;
                                                            block.entered.countDown();
                                                            try {
                                                                if (!block.release.await(
                                                                        4, TimeUnit.SECONDS))
                                                                    throw new AssertionError(
                                                                            "Owned cell was not released");
                                                            } catch (
                                                                    InterruptedException
                                                                            interrupted) {
                                                                Thread.currentThread().interrupt();
                                                                throw new java.util.concurrent
                                                                        .CancellationException(
                                                                        "Owner interrupted");
                                                            }
                                                        }
                                                        return x == 1
                                                                ? WorldRegionPages.Cell.unloaded()
                                                                : x == 2
                                                                        ? WorldRegionPages.Cell
                                                                                .known(
                                                                                        "minecraft:air",
                                                                                        Map.of())
                                                                        : WorldRegionPages.Cell
                                                                                .known(
                                                                                        "minecraft:oak_log",
                                                                                        Map.of(
                                                                                                "axis",
                                                                                                "y"));
                                                    }
                                                },
                                                guard);
                            }
                            guard.run();
                            result.complete(value);
                        } catch (Throwable failure) {
                            result.completeExceptionally(failure);
                        } finally {
                            finished.countDown();
                        }
                    });
            return result;
        }

        HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + TOKEN)
                    .header("Content-Type", "application/json");
        }

        HttpResponse<String> get(String path) throws Exception {
            return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonObject value(String path) throws Exception {
            return response(get(path), 200);
        }

        JsonObject post(String path, JsonObject body, int status) throws Exception {
            return response(
                    http.send(
                            request(path)
                                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()),
                    status);
        }

        JsonObject post(JsonObject body, int status) throws Exception {
            return post(PATH, body, status);
        }

        @Override
        public void close() throws Exception {
            closing.set(true);
            Gate blocked = gate;
            if (blocked != null) blocked.release.countDown();
            try {
                transport.close();
            } finally {
                owner.shutdownNow();
                assertTrue(owner.awaitTermination(5, TimeUnit.SECONDS));
                pages.close();
                if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
                else System.setProperty("minecraft.protocol.scopes", oldScopes);
            }
        }
    }

    @Test
    void realHttpPagesWorkInAllThreeModesWithoutAcquiringOrReplacingInputAuthority()
            throws Exception {
        try (var fixture = new Fixture("read,control,input")) {
            JsonObject read = fixture.value("/v0/control/mode");
            assertEquals("READ", read.get("mode").getAsString());
            fixture.post(initial(0, 1), 200);
            assertEquals(
                    read.get("modeVersion"), fixture.value("/v0/control/mode").get("modeVersion"));
            assertFalse(
                    fixture.value("/v0/control/mode")
                            .getAsJsonObject("authorization")
                            .getAsJsonObject("inputLease")
                            .get("held")
                            .getAsBoolean());
            JsonObject select = json("{\"mode\":\"OPERATE\"}");
            select.add("expectedModeVersion", read.get("modeVersion"));
            fixture.post("/v0/control/mode", select, 200);
            JsonObject operate = fixture.value("/v0/control/mode");
            fixture.post(initial(0, 1), 200);
            assertEquals(
                    operate.get("modeVersion"),
                    fixture.value("/v0/control/mode").get("modeVersion"));
            assertFalse(
                    operate.getAsJsonObject("authorization")
                            .getAsJsonObject("inputLease")
                            .get("held")
                            .getAsBoolean());
            String lease =
                    fixture.post("/v0/control/acquire", json("{\"ttlMs\":15000}"), 200)
                            .get("leaseId")
                            .getAsString();
            JsonObject takeover = fixture.value("/v0/control/mode");
            JsonObject page = fixture.post(initial(0, 1), 200);
            assertEquals("USER", page.get("dataCategory").getAsString());
            assertEquals("TAKEOVER", takeover.get("mode").getAsString());
            assertEquals(
                    takeover.get("modeVersion"),
                    fixture.value("/v0/control/mode").get("modeVersion"));
            assertEquals(lease, fixture.value("/v0/control/status").get("leaseId").getAsString());
            assertEquals(0, fixture.inputCalls.get());
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void scopeAndCategoryDenialsPrecedeAnyNativeRegionOrInternalCapture() throws Exception {
        try (var fixture = new Fixture("control,input")) {
            JsonObject failure = fixture.post(json("{\"query\":null}"), 403);
            assertEquals("SCOPE_DENIED", failure.get("error").getAsString());
            assertEquals(0, fixture.regionCalls.get());
            assertEquals(0, fixture.cells.get());
        }
        try (var fixture = new Fixture("read,debug,debug.world.read")) {
            JsonObject request = initial(0, 1);
            request.addProperty("category", "DEBUG");
            assertEquals(
                    "WORLD_CATEGORY_UNSUPPORTED",
                    fixture.post(request, 409).get("error").getAsString());
            request.addProperty("category", "OTHER");
            assertEquals(
                    "INVALID_WORLD_CATEGORY",
                    fixture.post(request, 400).get("error").getAsString());
            assertEquals(0, fixture.regionCalls.get());
            assertEquals(0, fixture.internalReads.get());
            assertEquals(0, fixture.cells.get());
        }
    }

    @Test
    void paletteCoverageContinuationAndCancelUseTheRealHelperThroughHttp() throws Exception {
        try (var fixture = new Fixture("read")) {
            JsonObject page = fixture.post(initial(0, 4), 200);
            int covered = 0, pageCount = 0;
            while (true) {
                assertEquals(covered, page.get("pageStartOffset").getAsInt());
                var indices = page.getAsJsonArray("indices");
                var palette = page.getAsJsonArray("palette");
                assertTrue(indices.size() > 0);
                for (int index = 0; index < indices.size(); index++) {
                    int x = covered + index, paletteIndex = indices.get(index).getAsInt();
                    if (x == 1) assertEquals(-1, paletteIndex);
                    else {
                        JsonObject block = palette.get(paletteIndex).getAsJsonObject();
                        assertEquals(
                                x == 2 ? "minecraft:air" : "minecraft:oak_log",
                                block.get("block").getAsString());
                        if (x != 2)
                            assertEquals(
                                    "y",
                                    block.getAsJsonObject("properties").get("axis").getAsString());
                    }
                }
                covered += indices.size();
                assertEquals(covered, page.get("coveredCellCount").getAsInt());
                assertEquals(4 - covered, page.get("remainingCellCount").getAsInt());
                assertTrue(++pageCount <= 4, "Every real HTTP page must advance");
                if (page.get("complete").getAsBoolean()) break;
                page = fixture.post(continuation(page), 200);
            }
            assertEquals(4, covered);
            assertEquals(1, page.get("unloadedCellCount").getAsInt());
            assertFalse(page.get("allLoaded").getAsBoolean());
            assertFalse(page.get("chunkLoadRequested").getAsBoolean());
            assertFalse(page.get("storageAccessed").getAsBoolean());
            assertFalse(page.has("nextCursor"));
            JsonObject request = initial(0, 3);
            request.getAsJsonObject("query").addProperty("maxBlocks", 1);
            JsonObject first = fixture.post(request, 200);
            JsonObject next = continuation(first);
            JsonObject second = fixture.post(next, 200);
            assertEquals(1, second.get("pageStartOffset").getAsInt());
            assertEquals(2, second.get("coveredCellCount").getAsInt());
            assertEquals(
                    "WORLD_REGION_CURSOR_INVALID",
                    fixture.post(next, 409).get("error").getAsString());
            JsonObject cancel = continuation(second);
            cancel.addProperty("action", "cancel");
            assertTrue(fixture.post(cancel, 200).get("cancelled").getAsBoolean());
            assertFalse(fixture.post(cancel, 200).get("cancelled").getAsBoolean());
            cancel.remove("action");
            assertEquals(
                    "WORLD_REGION_CURSOR_INVALID",
                    fixture.post(cancel, 409).get("error").getAsString());
        }
    }

    @Test
    void authenticatedPrincipalOwnsCursorsAndRequestFieldsCannotOverrideIt() throws Exception {
        try (var fixture = new Fixture("read")) {
            String principal =
                    fixture.value("/v0/control/mode")
                            .getAsJsonObject("authorization")
                            .get("principalId")
                            .getAsString();
            JsonObject request = initial(0, 3);
            request.getAsJsonObject("query").addProperty("maxBlocks", 1);
            JsonObject page = fixture.post(request, 200);
            assertEquals(principal, fixture.trustedOwner);
            int calls = fixture.regionCalls.get(), reads = fixture.cells.get();
            JsonObject forged = continuation(page);
            forged.addProperty("trustedOwner", "other-token-principal");
            assertEquals(
                    "INVALID_WORLD_REGION_QUERY",
                    fixture.post(forged, 400).get("error").getAsString());
            assertEquals(calls, fixture.regionCalls.get());
            forged = continuation(page);
            forged.getAsJsonObject("query").addProperty("owner", "other-token-principal");
            assertEquals(
                    "INVALID_WORLD_REGION_QUERY",
                    fixture.post(forged, 400).get("error").getAsString());
            assertEquals(reads, fixture.cells.get());
            assertEquals(
                    2, fixture.post(continuation(page), 200).get("coveredCellCount").getAsInt());
            assertEquals(principal, fixture.trustedOwner);
        }
    }

    @Test
    void typedErrorsAndChangedNativeContextNeverReadAdditionalCells() throws Exception {
        try (var fixture = new Fixture("read")) {
            for (String body :
                    new String[] {
                        "{}",
                        "{\"query\":null}",
                        "{\"action\":3,\"query\":{}}",
                        "{\"action\":\"write\",\"query\":{}}",
                        "{\"action\":\"cancel\",\"query\":{}}",
                        "{\"query\":{\"cursor\":false}}",
                        "{\"query\":{\"cursor\":\"x\",\"maxBlocks\":1}}",
                        "{\"query\":{\"min\":{\"x\":0.5,\"y\":0,\"z\":0},\"max\":{\"x\":1,\"y\":0,\"z\":0}}}"
                    })
                assertEquals(
                        "INVALID_WORLD_REGION_QUERY",
                        fixture.post(json(body), 400).get("error").getAsString());
            assertEquals(0, fixture.cells.get());
            JsonObject request = initial(0, 3);
            request.getAsJsonObject("query").addProperty("maxBlocks", 1);
            JsonObject page = fixture.post(request, 200);
            int before = fixture.cells.get();
            fixture.context =
                    new WorldRegionPages.Context(
                            "replacement-world-player", "minecraft:overworld", .5, .5, .5, 43);
            assertEquals(
                    "WORLD_REGION_CONTEXT_CHANGED",
                    fixture.post(continuation(page), 409).get("error").getAsString());
            assertEquals(before, fixture.cells.get());
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void concurrentHttpPageIsRejectedBeforeAnotherNativeReadIsDispatched() throws Exception {
        try (var fixture = new Fixture("read")) {
            Gate gate = new Gate();
            fixture.gate = gate;
            var first =
                    fixture.http.sendAsync(
                            fixture.request(PATH)
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    initial(0, 2).toString()))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(gate.entered.await(3, TimeUnit.SECONDS));
                int calls = fixture.regionCalls.get(), cells = fixture.cells.get();
                assertEquals(
                        "WORLD_REGION_BUSY",
                        fixture.post(initial(0, 1), 429).get("error").getAsString());
                assertEquals(calls, fixture.regionCalls.get());
                assertEquals(cells, fixture.cells.get());
            } finally {
                gate.release.countDown();
            }
            response(first.get(4, TimeUnit.SECONDS), 200);
            fixture.post(initial(0, 1), 200);
        }
    }

    @Test
    void httpDeadlineCancelsTheRealPageAndNoCellArrivesAfterTheCancellationGuard()
            throws Exception {
        try (var fixture = new Fixture("read")) {
            Gate gate = new Gate();
            fixture.gate = gate;
            var pending =
                    fixture.http.sendAsync(
                            fixture.request(PATH)
                                    .header("X-MCP-Deadline-Ms", "1000")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    initial(0, 4).toString()))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(gate.entered.await(3, TimeUnit.SECONDS));
                var source = fixture.nativePage;
                var finished = fixture.nativeFinished;
                JsonObject failure = response(pending.get(3, TimeUnit.SECONDS), 408);
                assertEquals("REQUEST_DEADLINE_EXCEEDED", failure.get("error").getAsString());
                assertFalse(failure.has("palette"));
                assertTrue(source.isCancelled());
                int cells = fixture.cells.get();
                gate.release.countDown();
                assertTrue(finished.await(3, TimeUnit.SECONDS));
                assertTrue(fixture.cancellationGuards.get() > 0);
                assertEquals(cells, fixture.cells.get());
                fixture.post(initial(0, 1), 200);
            } finally {
                gate.release.countDown();
            }
        }
    }

    private static JsonObject initial(int x, int count) {
        return json(
                "{\"query\":{\"min\":{\"x\":"
                        + x
                        + ",\"y\":0,\"z\":0},\"max\":{\"x\":"
                        + (x + count - 1)
                        + ",\"y\":0,\"z\":0}}}");
    }

    private static JsonObject continuation(JsonObject page) {
        JsonObject request = new JsonObject(), query = new JsonObject();
        query.add("cursor", page.get("nextCursor").deepCopy());
        request.add("query", query);
        return request;
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject response(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        return json(response.body());
    }
}
