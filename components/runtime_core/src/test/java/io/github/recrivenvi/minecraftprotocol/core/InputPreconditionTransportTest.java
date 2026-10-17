package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputPreconditionTransportTest {
    @TempDir Path artifacts;

    private final class Fixture implements AutoCloseable {
        final PointerGestureTest.World world = new PointerGestureTest.World();
        final AtomicInteger checks = new AtomicInteger();
        volatile CompletableFuture<JsonObject> blockedPreflight;
        volatile JsonObject uiReadyTree;
        final HttpClient client = HttpClient.newHttpClient();
        final ProbeTransport transport;
        final int port;
        final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        String lease;

        Fixture() throws Exception {
            this("read,ui,input,control");
        }

        Fixture(String scopes) throws Exception {
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            System.setProperty("minecraft.protocol.scopes", scopes);
            ProbeService delegate = world.service();
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
                                        if (uiReadyTree != null
                                                && java.util.Set.of("uiTree", "debugUiTree")
                                                        .contains(method.getName()))
                                            return CompletableFuture.completedFuture(
                                                    uiReadyTree.deepCopy());
                                        if (method.getName().equals("validatePreconditions")) {
                                            checks.incrementAndGet();
                                            if (blockedPreflight != null) return blockedPreflight;
                                            Long screen = (Long) args[0], menu = (Long) args[1];
                                            String stale =
                                                    screen != null && screen != world.revision
                                                            ? "SCREEN"
                                                            : menu != null
                                                                            && menu
                                                                                    != world.menuRevision
                                                                    ? "MENU"
                                                                    : null;
                                            if (stale != null)
                                                return CompletableFuture.failedFuture(
                                                        new ProtocolState.ProtocolException(
                                                                "STALE_" + stale + "_REVISION",
                                                                409,
                                                                "changed while queued"));
                                            return CompletableFuture.completedFuture(
                                                    new JsonObject());
                                        }
                                        try {
                                            return method.invoke(delegate, args);
                                        } catch (
                                                java.lang.reflect.InvocationTargetException error) {
                                            throw error.getCause();
                                        }
                                    });
            transport = new ProbeTransport(service, "input-precondition-http", port, "test-target");
            transport.startAsync();
            boolean ready = false;
            for (int attempt = 0; attempt < 200; attempt++) {
                try {
                    request("GET", "/v0/control/mode", null, false).get(1, TimeUnit.SECONDS);
                    ready = true;
                    break;
                } catch (java.util.concurrent.ExecutionException failure) {
                    Thread.sleep(25);
                }
            }
            if (!ready) {
                close();
                throw new AssertionError("Owned HTTP transport did not start");
            }
            lease =
                    value(
                                    request(
                                                    "POST",
                                                    "/v0/control/acquire",
                                                    "{\"ttlMs\":60000}",
                                                    false)
                                            .get(3, TimeUnit.SECONDS))
                            .get("leaseId")
                            .getAsString();
        }

        CompletableFuture<HttpResponse<String>> request(
                String method, String path, String body, boolean pin) {
            var builder =
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(6))
                            .header("Authorization", "Bearer input-precondition-http");
            if (lease != null) builder.header("X-MCP-Control-Lease", lease);
            if (pin)
                builder.header("X-MCP-Expected-Screen-Revision", "1")
                        .header("X-MCP-Expected-Menu-Revision", "1");
            if (body != null) builder.header("Content-Type", "application/json");
            return client.sendAsync(
                    builder.method(
                                    method,
                                    body == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        String blockQueue() throws Exception {
            var response =
                    request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"delay\",\"durationMs\":800}],\"cleanupOnComplete\":false}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            return value(response).get("operationId").getAsString();
        }

        JsonObject waitOperation(String id) throws Exception {
            return value(
                    request("POST", "/v0/operations/" + id + "/wait", "{\"timeoutMs\":5000}", false)
                            .get(6, TimeUnit.SECONDS));
        }

        void mode(String desired) throws Exception {
            var current = request("GET", "/v0/control/mode", null, false).get(3, TimeUnit.SECONDS);
            assertEquals(200, current.statusCode(), current.body());
            JsonObject body = new JsonObject();
            body.addProperty("mode", desired);
            body.add("expectedModeVersion", value(current).get("modeVersion").deepCopy());
            var response =
                    request("POST", "/v0/control/mode", body.toString(), false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(desired, value(response).get("mode").getAsString());
        }

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    private static JsonObject value(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @Test
    void pipelineRateLimitReportsItsActualBudgetWithoutDispatchingPlayerInput() throws Exception {
        try (Fixture fixture = new Fixture()) {
            HttpResponse<String> rejected = null;
            for (int attempt = 0; attempt < 64; attempt++) {
                var response =
                        fixture.request(
                                        "POST",
                                        "/v0/pipelines",
                                        "{\"steps\":[{\"type\":\"delay\",\"durationMs\":1}],\"cleanupOnComplete\":false}",
                                        false)
                                .get(3, TimeUnit.SECONDS);
                if (response.statusCode() == 429) {
                    rejected = response;
                    break;
                }
                assertEquals(200, response.statusCode(), response.body());
            }
            assertNotNull(rejected, "Bounded burst must reach the production pipeline quota");
            JsonObject error = value(rejected);
            assertEquals("EXPENSIVE_OPERATION_RATE_LIMITED", error.get("error").getAsString());
            assertTrue(error.has("retryAfterMillis"), rejected.body());
            assertTrue(error.get("retryAfterMillis").getAsLong() > 0);
            assertTrue(error.get("retryAfterMillis").getAsLong() <= 500);
            JsonObject budget = error.getAsJsonObject("rateLimit");
            assertEquals("pipeline", budget.get("category").getAsString());
            assertEquals("pipeline", budget.get("operationCategory").getAsString());
            assertEquals(8, budget.get("capacity").getAsInt());
            assertEquals(0, budget.get("remaining").getAsInt());
            assertEquals(2.0, budget.get("refillPerSecond").getAsDouble());
            var unauthenticated =
                    fixture.client.send(
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + fixture.port
                                                            + "/v0/pipelines"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Authorization", "Bearer wrong-token")
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString("{\"steps\":[]}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthenticated.statusCode());
            assertEquals("UNAUTHORIZED", value(unauthenticated).get("error").getAsString());
            assertFalse(value(unauthenticated).has("rateLimit"));
            assertFalse(value(unauthenticated).has("retryAfterMillis"));
            assertEquals(0, fixture.world.presses.get());
            assertEquals(0, fixture.world.moves.get());
            assertEquals(0, fixture.world.buttons);
            assertFalse(fixture.world.events.contains("key"));
        }
    }

    @Test
    void queuedUiAndRawInputsRecheckExactInitialValuesBeforeDispatch() throws Exception {
        for (boolean raw : new boolean[] {false, true})
            for (boolean screen : new boolean[] {false, true}) {
                try (Fixture fixture = new Fixture()) {
                    fixture.blockQueue();
                    var pending =
                            fixture.request(
                                    "POST",
                                    raw ? "/v0/input/key" : "/v0/ui/action",
                                    raw
                                            ? "{\"key\":87,\"action\":1}"
                                            : "{\"selector\":{\"label\":\"A\"},\"action\":\"click\"}",
                                    true);
                    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (fixture.checks.get() == 0 && System.nanoTime() < end) Thread.sleep(5);
                    assertEquals(
                            1,
                            fixture.checks.get(),
                            "Initial precondition really passed before changing state");
                    assertFalse(
                            pending.isDone(),
                            "Request must really be queued, not rejected at admission");
                    if (screen) fixture.world.revision++;
                    else fixture.world.menuRevision++;
                    var response = pending.get(6, TimeUnit.SECONDS);
                    assertEquals(409, response.statusCode(), response.body());
                    assertEquals(
                            "STALE_" + (screen ? "SCREEN" : "MENU") + "_REVISION",
                            value(response).get("error").getAsString());
                    assertEquals(0, fixture.world.moves.get());
                    assertEquals(0, fixture.world.presses.get());
                    assertFalse(fixture.world.events.contains("key"));
                    assertEquals(0, fixture.world.buttons);
                    assertEquals(
                            0,
                            fixture.world.cleanups.get(),
                            "Rejected preflight must not release another raw input stream");
                    Thread.sleep(80);
                    assertEquals(0, fixture.world.moves.get(), "No late rejected request motion");
                    assertFalse(fixture.world.events.contains("key"));
                }
            }
    }

    @Test
    void pipelinesHonorTheSameQueuedRevisionHeaders() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.blockQueue();
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key.tap\",\"key\":87,\"holdMs\":20}]}",
                                    true)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            fixture.world.menuRevision++;
            var operation = fixture.waitOperation(value(response).get("operationId").getAsString());
            assertEquals("failed", operation.get("state").getAsString(), operation.toString());
            assertEquals("STALE_MENU_REVISION", operation.get("errorCode").getAsString());
            assertFalse(fixture.world.events.contains("key"));
            assertEquals(0, fixture.world.cleanups.get());
        }
    }

    @Test
    void productionDescriptorsExposeTheSameConditionalScopesEnforcedByPipelines() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var response =
                    fixture.request("GET", "/v0/operations", null, false).get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            JsonObject pipeline = null;
            for (var entry : value(response).getAsJsonArray("operations")) {
                var descriptor = entry.getAsJsonObject();
                if (descriptor.get("id").getAsString().equals("ui.action"))
                    assertEquals(
                            "ui",
                            descriptor.getAsJsonArray("additionalScopes").get(0).getAsString());
                if (descriptor.get("id").getAsString().equals("pipeline.execute"))
                    pipeline = descriptor;
            }
            assertNotNull(pipeline);
            var scopes = pipeline.getAsJsonObject("additionalScopesByStep");
            assertEquals(15, scopes.size());
            for (String type : new String[] {"ui.action", "ui.drag"})
                assertEquals("ui", scopes.getAsJsonArray(type).get(0).getAsString());
            for (String type : new String[] {"wait.until", "assert.that"})
                assertEquals("read", scopes.getAsJsonArray(type).get(0).getAsString());
            assertTrue(scopes.getAsJsonArray("key.tap").isEmpty());
            assertFalse(scopes.has("internal.player.command"));
        }
    }

    @Test
    void unknownOrInternalTypedStepsFailBeforeAnyEarlierInput() throws Exception {
        for (String type : new String[] {"unknown", "internal.player.command"})
            try (Fixture fixture = new Fixture()) {
                var response =
                        fixture.request(
                                        "POST",
                                        "/v0/pipelines",
                                        "{\"steps\":[{\"type\":\"key\",\"key\":87,\"action\":1},{\"type\":\""
                                                + type
                                                + "\"}]}",
                                        false)
                                .get(3, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode(), response.body());
                var operation =
                        fixture.waitOperation(value(response).get("operationId").getAsString());
                assertEquals("failed", operation.get("state").getAsString());
                assertEquals("UNSUPPORTED_PIPELINE_STEP", operation.get("errorCode").getAsString());
                assertFalse(fixture.world.events.contains("key"));
                assertEquals(0, fixture.world.cleanups.get());
            }
    }

    @Test
    void eventConditionsRequireTheirOwnScopeBeforeEarlierPipelineInput() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key\",\"key\":87,\"action\":1},{\"type\":\"assert.that\",\"condition\":{\"type\":\"event\"}}]}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(403, response.statusCode(), response.body());
            assertFalse(fixture.world.events.contains("key"));
            response =
                    fixture.request(
                                    "POST",
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"event\"}}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(403, response.statusCode(), response.body());
        }
    }

    @Test
    void readyScreenRequiresAnUncoveredFreshInteractiveTargetInTheSameSnapshot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.request("POST", "/v0/control/release", null, false).get(3, TimeUnit.SECONDS);
            fixture.uiReadyTree =
                    JsonParser.parseString(
                                    """
                    {"screenClass":"TitleScreen","screenIdentity":5,"screenRevision":7,
                     "overlayIdentity":99,"windowWidth":1920,"windowHeight":1080,"width":480,"height":270,
                     "children":[{"nodeId":"button:1","role":"button","label":"Play","x":10,"y":20,
                     "width":100,"height":20,"active":true,"visible":true,"actions":["click"]}]}
                    """)
                            .getAsJsonObject();
            String simple =
                    "{\"condition\":{\"type\":\"screen\",\"classContains\":\"TitleScreen\"}}";
            assertEquals(
                    200,
                    fixture.request("POST", "/v0/assert", simple, false)
                            .get(3, TimeUnit.SECONDS)
                            .statusCode());
            String ready =
                    "{\"condition\":{\"type\":\"screen\",\"classContains\":\"TitleScreen\",\"ready\":true,\"selector\":{\"nodeId\":\"button:1\"}}}";
            var response =
                    fixture.request("POST", "/v0/assert", ready, false).get(3, TimeUnit.SECONDS);
            assertEquals(412, response.statusCode(), response.body());
            fixture.uiReadyTree.addProperty("overlayIdentity", 0);
            assertEquals(
                    200,
                    fixture.request("POST", "/v0/assert", ready, false)
                            .get(3, TimeUnit.SECONDS)
                            .statusCode());
            for (String field : new String[] {"active", "visible"}) {
                fixture.uiReadyTree
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .addProperty(field, false);
                assertEquals(
                        412,
                        fixture.request("POST", "/v0/assert", ready, false)
                                .get(3, TimeUnit.SECONDS)
                                .statusCode());
                fixture.uiReadyTree
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .addProperty(field, true);
            }
            fixture.uiReadyTree.addProperty("windowWidth", 0);
            assertEquals(
                    412,
                    fixture.request("POST", "/v0/assert", ready, false)
                            .get(3, TimeUnit.SECONDS)
                            .statusCode());
            fixture.uiReadyTree.addProperty("windowWidth", 1920);
            fixture.uiReadyTree
                    .getAsJsonArray("children")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("nodeId", "replacement:2");
            assertEquals(
                    412,
                    fixture.request("POST", "/v0/assert", ready, false)
                            .get(3, TimeUnit.SECONDS)
                            .statusCode());
            assertEquals(0, fixture.world.moves.get());
            assertEquals(0, fixture.world.presses.get());
        }
    }

    @Test
    void uiConditionsUseTheAuthorizedProjectionAndModeWithoutAnInputLease() throws Exception {
        try (Fixture fixture = new Fixture("read,ui,input,control,debug,debug.ui.read")) {
            fixture.request("POST", "/v0/control/release", null, false).get(3, TimeUnit.SECONDS);
            for (String category : new String[] {"USER", "DEBUG"}) {
                for (String type : new String[] {"screen", "ui.exists", "menu", "inventory"}) {
                    var response =
                            fixture.request(
                                            "POST",
                                            "/v0/assert",
                                            "{\"condition\":{\"type\":\""
                                                    + type
                                                    + "\",\"category\":\""
                                                    + category
                                                    + "\",\"selector\":{\"label\":\"A\"}}}",
                                            false)
                                    .get(3, TimeUnit.SECONDS);
                    assertEquals(200, response.statusCode(), response.body());
                    assertEquals("UI", value(response).get("domain").getAsString());
                    assertEquals(category, value(response).get("dataCategory").getAsString());
                    assertEquals(
                            category.equals("DEBUG"),
                            value(response).getAsJsonObject("evidence").has("debugFacts"));
                }
            }
            assertEquals(4, fixture.world.uiReads.get());
            assertEquals(4, fixture.world.debugUiReads.get());
            fixture.mode("OPERATE");
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"screen\"}}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(409, response.statusCode(), response.body());
            assertTrue(response.body().contains("UI_CATEGORY_MODE_MISMATCH"));
            response =
                    fixture.request(
                                    "POST",
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"screen\",\"category\":\"DEBUG\"}}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(0, fixture.world.moves.get());
        }
    }

    @Test
    void asyncUiConditionsRecheckModeAndCancellationReachesTheNativeRead() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.request("POST", "/v0/control/release", null, false).get(3, TimeUnit.SECONDS);
            fixture.world.blockedUiRead = new CompletableFuture<>();
            var pending =
                    fixture.request(
                            "POST",
                            "/v0/wait/until",
                            "{\"condition\":{\"type\":\"screen\"},\"timeoutMs\":3000}",
                            false);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (fixture.world.uiReads.get() == 0 && System.nanoTime() < end) Thread.sleep(5);
            assertEquals(1, fixture.world.uiReads.get());
            fixture.mode("OPERATE");
            fixture.world.blockedUiRead.complete(fixture.world.tree());
            var response = pending.get(3, TimeUnit.SECONDS);
            assertEquals(409, response.statusCode(), response.body());
            assertTrue(response.body().contains("UI_CATEGORY_MODE_MISMATCH"));
        }
        try (Fixture fixture = new Fixture()) {
            fixture.world.blockedUiRead = new CompletableFuture<>();
            var accepted =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"wait.until\",\"condition\":{\"type\":\"screen\"}}]}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            String id = value(accepted).get("operationId").getAsString();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (fixture.world.uiReads.get() == 0 && System.nanoTime() < end) Thread.sleep(5);
            assertEquals(1, fixture.world.uiReads.get());
            assertEquals(
                    200,
                    fixture.request("DELETE", "/v0/operations/" + id, null, false)
                            .get(3, TimeUnit.SECONDS)
                            .statusCode());
            assertEquals("cancelled", fixture.waitOperation(id).get("state").getAsString());
            assertTrue(fixture.world.blockedUiRead.isCancelled());
            assertFalse(fixture.world.events.contains("key"));
        }
    }

    @Test
    void missingUiDoesNotBlockRawInputOrGrantUiDuringAnAuthorizedPipeline() throws Exception {
        try (Fixture fixture = new Fixture("read,input,control")) {
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key.tap\",\"key\":87,\"holdMs\":10}]}",
                                    false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(
                    "completed",
                    fixture.waitOperation(value(response).get("operationId").getAsString())
                            .get("state")
                            .getAsString());
            assertTrue(fixture.world.events.contains("key"));
            assertEquals(
                    403,
                    fixture.request("GET", "/v0/ui/tree", null, false)
                            .get(3, TimeUnit.SECONDS)
                            .statusCode());
        }
    }

    @Test
    void typedUiStepsCannotBorrowThePipelineInputScope() throws Exception {
        for (String type : new String[] {"ui.action", "ui.drag"}) {
            try (Fixture fixture = new Fixture("read,input,control")) {
                String step =
                        type.equals("ui.action")
                                ? "{\"type\":\"ui.action\",\"selector\":{\"label\":\"A\"}}"
                                : "{\"type\":\"ui.drag\",\"from\":{\"label\":\"A\"},\"to\":{\"label\":\"B\"}}";
                var response =
                        fixture.request(
                                        "POST",
                                        "/v0/pipelines",
                                        "{\"steps\":[{\"type\":\"key\",\"key\":87,\"action\":1},"
                                                + step
                                                + "]}",
                                        false)
                                .get(3, TimeUnit.SECONDS);
                assertEquals(403, response.statusCode(), response.body());
                assertTrue(response.body().contains("SCOPE_DENIED"), response.body());
                assertFalse(
                        fixture.world.events.contains("key"),
                        "Reject the whole plan before its earlier input");
                assertEquals(0, fixture.world.moves.get());
                assertEquals(0, fixture.world.cleanups.get());
            }
        }
    }

    @Test
    void typedConditionsCannotBorrowThePipelineInputScope() throws Exception {
        for (String type : new String[] {"wait.until", "assert.that"}) {
            try (Fixture fixture = new Fixture("ui,input,control")) {
                var response =
                        fixture.request(
                                        "POST",
                                        "/v0/pipelines",
                                        "{\"steps\":[{\"type\":\"key\",\"key\":87,\"action\":1},{\"type\":\""
                                                + type
                                                + "\",\"condition\":{\"type\":\"screen\"}}]}",
                                        false)
                                .get(3, TimeUnit.SECONDS);
                assertEquals(403, response.statusCode(), response.body());
                assertTrue(response.body().contains("SCOPE_DENIED"), response.body());
                assertFalse(fixture.world.events.contains("key"));
                assertEquals(0, fixture.world.cleanups.get());
            }
        }
    }

    @Test
    void standaloneUiConditionsRequireUiScopeAndRespectExplicitDebugCategory() throws Exception {
        for (String category : new String[] {"USER", "DEBUG"}) {
            try (Fixture fixture =
                    new Fixture(
                            category.equals("USER")
                                    ? "read,input,control"
                                    : "read,ui,input,control")) {
                fixture.request("POST", "/v0/control/release", null, false)
                        .get(3, TimeUnit.SECONDS);
                for (String type : new String[] {"screen", "ui.exists", "menu", "inventory"}) {
                    var response =
                            fixture.request(
                                            "POST",
                                            "/v0/assert",
                                            "{\"condition\":{\"type\":\""
                                                    + type
                                                    + "\",\"category\":\""
                                                    + category
                                                    + "\",\"selector\":{\"label\":\"A\"}}}",
                                            false)
                                    .get(3, TimeUnit.SECONDS);
                    assertEquals(403, response.statusCode(), response.body());
                    assertTrue(response.body().contains("SCOPE_DENIED"), response.body());
                }
            }
        }
    }

    @Test
    void finalOwnerCheckCoversRawEventsAndPipelineInputWithoutAGuiGuard() throws Exception {
        for (String route : new String[] {"key", "delta", "pipeline"}) {
            try (Fixture fixture = new Fixture()) {
                fixture.world.beforeNativeInput =
                        () -> {
                            fixture.world.beforeNativeInput = null;
                            fixture.world.menuRevision++;
                        };
                String path = route.equals("pipeline") ? "/v0/pipelines" : "/v0/input/" + route;
                if (route.equals("delta")) path = "/v0/input/mouse/delta";
                String body =
                        switch (route) {
                            case "pipeline" -> "{\"steps\":[{\"type\":\"key.tap\",\"key\":87}]}";
                            case "delta" -> "{\"dx\":10,\"dy\":5}";
                            default -> "{\"key\":87,\"action\":1}";
                        };
                var response = fixture.request("POST", path, body, true).get(6, TimeUnit.SECONDS);
                if (route.equals("pipeline")) {
                    assertEquals(200, response.statusCode(), response.body());
                    var operation =
                            fixture.waitOperation(value(response).get("operationId").getAsString());
                    assertEquals(
                            "failed", operation.get("state").getAsString(), operation.toString());
                    assertEquals("STALE_MENU_REVISION", operation.get("errorCode").getAsString());
                } else {
                    assertEquals(409, response.statusCode(), response.body());
                    assertTrue(response.body().contains("STALE_MENU_REVISION"), response.body());
                }
                assertFalse(fixture.world.events.contains("key"));
                assertFalse(fixture.world.events.contains("delta"));
                assertEquals(0, fixture.world.moves.get());
                assertEquals(0, fixture.world.presses.get());
                assertEquals(0, fixture.world.buttons);
                assertEquals(
                        0,
                        fixture.world.cleanups.get(),
                        "Rejected first input must not release a preceding raw stream");
            }
        }
    }

    @Test
    void originalValuesRemainPinnedAfterPreflightUntilTheFirstNativeEvent() throws Exception {
        for (boolean screen : new boolean[] {true, false}) {
            try (Fixture fixture = new Fixture()) {
                fixture.world.beforePointerRead =
                        () -> {
                            fixture.world.beforePointerRead = null;
                            if (screen) fixture.world.revision++;
                            else fixture.world.menuRevision++;
                        };
                var response =
                        fixture.request(
                                        "POST",
                                        "/v0/ui/action",
                                        "{\"selector\":{\"label\":\"A\"}}",
                                        true)
                                .get(6, TimeUnit.SECONDS);
                assertEquals(409, response.statusCode(), response.body());
                assertTrue(
                        response.body()
                                .contains(screen ? "STALE_SCREEN_REVISION" : "STALE_MENU_REVISION"),
                        response.body());
                assertEquals(0, fixture.world.moves.get());
                assertEquals(0, fixture.world.presses.get());
                assertEquals(0, fixture.world.buttons);
                assertEquals(
                        0,
                        fixture.world.cleanups.get(),
                        "Rejected first input must not release a preceding raw stream");
            }
        }
    }

    @Test
    void unchangedPreconditionsAllowInputAndDoNotPinSelfCausedPipelineChangesForever()
            throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.blockQueue();
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/ui/action",
                                    "{\"selector\":{\"label\":\"A\"}}",
                                    true)
                            .get(6, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(
                    2,
                    fixture.checks.get(),
                    "Admission and queue-turn checks use the real transport");
            assertEquals(1, fixture.world.presses.get());
            fixture.world.afterPress =
                    () -> {
                        fixture.world.menuRevision++;
                        fixture.world.revision++;
                    };
            var started =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"ui.action\",\"selector\":{\"label\":\"A\"}},{\"type\":\"ui.action\",\"selector\":{\"label\":\"B\"}}]}",
                                    true)
                            .get(3, TimeUnit.SECONDS);
            var operation = fixture.waitOperation(value(started).get("operationId").getAsString());
            assertEquals("completed", operation.get("state").getAsString(), operation.toString());
            assertEquals(3, fixture.world.presses.get());
            assertEquals(0, fixture.world.buttons);
        }
    }

    @Test
    void queuedCancellationDoesNotRunPreflightOrDispatchLateInput() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String blocker = fixture.blockQueue();
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key.tap\",\"key\":87}]}",
                                    true)
                            .get(3, TimeUnit.SECONDS);
            String id = value(response).get("operationId").getAsString();
            var cancelled =
                    fixture.request("DELETE", "/v0/operations/" + id, null, false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, cancelled.statusCode(), cancelled.body());
            assertEquals("cancelled", fixture.waitOperation(id).get("state").getAsString());
            assertEquals("completed", fixture.waitOperation(blocker).get("state").getAsString());
            assertEquals(0, fixture.checks.get());
            assertFalse(fixture.world.events.contains("key"));
            assertEquals(0, fixture.world.cleanups.get());
        }
    }

    @Test
    void deadlineWhilePreflightIsPendingPreservesTimeoutAndReleasesTheQueue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.blockedPreflight = new CompletableFuture<>();
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key.tap\",\"key\":87}],\"timeoutMs\":1000}",
                                    true)
                            .get(3, TimeUnit.SECONDS);
            var operation = fixture.waitOperation(value(response).get("operationId").getAsString());
            assertEquals(1, fixture.checks.get(), "Owner-thread preflight was really pending");
            assertEquals("timed_out", operation.get("state").getAsString(), operation.toString());
            assertEquals("PIPELINE_TIMEOUT", operation.get("errorCode").getAsString());
            assertTrue(fixture.blockedPreflight.isCancelled());
            assertFalse(fixture.world.events.contains("key"));
            assertEquals(0, fixture.world.cleanups.get());
            fixture.blockedPreflight = null;
            var fresh =
                    fixture.request("POST", "/v0/input/key", "{\"key\":87,\"action\":0}", true)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, fresh.statusCode(), fresh.body());
        }
    }

    @Test
    void runtimeShutdownDuringNativeClickReportsUnconfirmedCancellationNotInternalError()
            throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.world.afterPress = fixture.transport::close;
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/ui/action",
                                    "{\"selector\":{\"label\":\"A\"},\"action\":\"click\"}",
                                    false)
                            .get(5, TimeUnit.SECONDS);
            assertEquals(503, response.statusCode(), response.body());
            JsonObject error = value(response);
            assertEquals("RUNTIME_SHUTTING_DOWN", error.get("error").getAsString());
            assertEquals("not_confirmed", error.get("executionOutcome").getAsString());
            assertFalse(error.get("retrySafe").getAsBoolean());
            assertEquals(1, fixture.world.presses.get(), "The native effect really began");
            assertEquals(0, fixture.world.buttons);
            int dispatched = fixture.world.moves.get();
            Thread.sleep(100);
            assertEquals(dispatched, fixture.world.moves.get());
            assertEquals(1, fixture.world.presses.get(), "No automatic retry after shutdown");
        }
    }

    @Test
    void modeExitCancelsInFlightHttpClickWithoutClaimingNoEffectOrServerFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var pending =
                    fixture.request(
                            "POST",
                            "/v0/ui/action",
                            "{\"selector\":{\"label\":\"A\"},\"action\":\"click\",\"holdMs\":2000}",
                            false);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (fixture.world.presses.get() == 0 && System.nanoTime() < end) Thread.sleep(5);
            assertEquals(1, fixture.world.presses.get());
            fixture.mode("READ");
            var response = pending.get(3, TimeUnit.SECONDS);
            assertEquals(409, response.statusCode(), response.body());
            assertEquals("REQUEST_CANCELLED", value(response).get("error").getAsString());
            assertEquals("not_confirmed", value(response).get("executionOutcome").getAsString());
            assertFalse(value(response).get("retrySafe").getAsBoolean());
            assertEquals(0, fixture.world.buttons);
        }
    }

    @Test
    void missingScopeWinsBeforeAnyRevisionRead() throws Exception {
        try (Fixture fixture = new Fixture("read,control")) {
            fixture.world.menuRevision++;
            for (String path : new String[] {"/v0/ui/action", "/v0/input/key", "/v0/pipelines"}) {
                var response = fixture.request("POST", path, "{}", true).get(3, TimeUnit.SECONDS);
                assertEquals(403, response.statusCode(), response.body());
                assertEquals("SCOPE_DENIED", value(response).get("error").getAsString());
            }
            assertEquals(0, fixture.checks.get());
            assertEquals(0, fixture.world.moves.get());
            assertEquals(0, fixture.world.presses.get());
            assertFalse(fixture.world.events.contains("key"));
        }
    }

    @Test
    void leaseLossDuringPreflightCancelsItAndReturnsReadWithoutLateInput() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.blockedPreflight = new CompletableFuture<>();
            var response =
                    fixture.request(
                                    "POST",
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key.tap\",\"key\":87}]}",
                                    true)
                            .get(3, TimeUnit.SECONDS);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (fixture.checks.get() == 0 && System.nanoTime() < end) Thread.sleep(5);
            assertEquals(1, fixture.checks.get());
            var released =
                    fixture.request("POST", "/v0/control/release", "{}", false)
                            .get(3, TimeUnit.SECONDS);
            assertEquals(200, released.statusCode(), released.body());
            assertEquals(
                    "cancelled",
                    fixture.waitOperation(value(response).get("operationId").getAsString())
                            .get("state")
                            .getAsString());
            assertTrue(fixture.blockedPreflight.isCancelled());
            JsonObject mode =
                    value(
                            fixture.request("GET", "/v0/control/mode", null, false)
                                    .get(3, TimeUnit.SECONDS));
            assertEquals("READ", mode.get("mode").getAsString());
            assertFalse(mode.get("reconsentRequired").getAsBoolean());
            assertEquals(0, fixture.world.moves.get());
            assertEquals(0, fixture.world.presses.get());
            assertFalse(fixture.world.events.contains("key"));
            Thread.sleep(80);
            assertFalse(fixture.world.events.contains("key"));
        }
    }
}
