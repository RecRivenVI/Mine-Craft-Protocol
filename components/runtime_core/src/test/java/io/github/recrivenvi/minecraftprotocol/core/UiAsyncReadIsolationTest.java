package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiAsyncReadIsolationTest {
    private static final String SECRET = "PRIVATE_UI_ASYNC_DRAFT";
    @TempDir Path artifacts;

    private final class Fixture implements AutoCloseable {
        final HttpClient client = HttpClient.newHttpClient();
        final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        final AtomicInteger userUiReads = new AtomicInteger();
        final CountDownLatch uiStarted = new CountDownLatch(1);
        final CountDownLatch sampleStarted = new CountDownLatch(1);
        final CompletableFuture<JsonObject> deferredUi;
        final ProbeTransport transport;
        final int port;
        String arm = "";
        String recordingId = "";

        Fixture(String uiScopes, boolean deferUi) throws Exception {
            deferredUi = deferUi ? new CompletableFuture<>() : null;
            System.setProperty(
                    "minecraft.protocol.scopes",
                    "read,control,capture,event,debug,debug.world.read,debug.write,debug.ui"
                            + uiScopes);
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
                                        JsonObject value = new JsonObject();
                                        if (method.getName().equals("uiDebugContext")) {
                                            value.addProperty(
                                                    "uiContextId", "ui-session:async-test");
                                            value.addProperty("sessionEpoch", "async-test");
                                        }
                                        if (method.getName().equals("phase9cDebugMutation")) {
                                            value.addProperty("operation", "ui.text.set");
                                            value.addProperty("namespace", "ui");
                                            value.addProperty("privateDraft", SECRET);
                                        }
                                        if (method.getName().equals("uiTree")) {
                                            userUiReads.incrementAndGet();
                                            uiStarted.countDown();
                                            if (deferredUi != null) return deferredUi;
                                            value.addProperty("label", "USER_VIEW_ONLY");
                                        }
                                        if (method.getName().equals("inputState"))
                                            sampleStarted.countDown();
                                        return CompletableFuture.completedFuture(value);
                                    });
            transport = new ProbeTransport(service, "ui-async-test", 0, "isolated-http-test");
            transport.startAsync();
            port = transport.boundPort().get(10, TimeUnit.SECONDS);
        }

        CompletableFuture<HttpResponse<String>> request(String method, String path, String body) {
            return client.sendAsync(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .header("Authorization", "Bearer ui-async-test")
                            .header("x-mcp-debug-arm", arm)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(5))
                            .method(
                                    method,
                                    body == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        JsonObject value(String method, String path, String body, int expected) throws Exception {
            var response = request(method, path, body).get(6, TimeUnit.SECONDS);
            assertEquals(expected, response.statusCode(), response.body());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }

        void mode(String mode) throws Exception {
            JsonObject body = new JsonObject();
            body.addProperty("mode", mode);
            body.add(
                    "expectedModeVersion",
                    value("GET", "/v0/control/mode", null, 200).get("modeVersion"));
            value("POST", "/v0/control/mode", body.toString(), 200);
        }

        void armUi() throws Exception {
            mode("OPERATE");
            arm =
                    value(
                                    "POST",
                                    "/v0/debug/arm",
                                    "{\"uiContextId\":\"ui-session:async-test\",\"namespaces\":[\"ui\"],\"ttlMs\":60000}",
                                    200)
                            .get("debugArmId")
                            .getAsString();
        }

        void mutateUi() throws Exception {
            armUi();
            assertTrue(
                    value("POST", "/v0/debug/mutations", "{\"operation\":\"ui.text.set\"}", 200)
                            .toString()
                            .contains(SECRET));
        }

        String mutateUiBatch() throws Exception {
            armUi();
            JsonObject receipt =
                    value(
                            "POST",
                            "/v0/debug/batches",
                            "{\"items\":[{\"operation\":\"ui.text.set\"}]}",
                            200);
            assertFalse(receipt.toString().contains(SECRET), receipt.toString());
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (value("GET", "/v0/debug/evidence", null, 200)
                            .get("lastDebugMutationSequence")
                            .getAsLong()
                    == 0) {
                assertTrue(System.nanoTime() < until, "Batch did not reach its mutation observer");
                Thread.sleep(10);
            }
            return "/v0/operations/" + receipt.get("operationId").getAsString();
        }

        void record(String category) throws Exception {
            recordingId =
                    value(
                                    "POST",
                                    "/v0/recordings",
                                    "{\"category\":\""
                                            + category
                                            + "\",\"captureFrames\":false,\"intervalMs\":60000,\"durationMs\":60000,\"stateReads\":[],\"contactSheet\":{\"enabled\":false}}",
                                    200)
                            .get("recordingId")
                            .getAsString();
            assertTrue(sampleStarted.await(2, TimeUnit.SECONDS));
        }

        String downloadFinishedRecording() throws Exception {
            String path = "/v0/recordings/" + recordingId;
            assertTrue(
                    value("GET", path + "?category=DEBUG", null, 200)
                            .get("evidenceContaminated")
                            .getAsBoolean());
            value("DELETE", path + "?category=DEBUG", null, 200);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (!value("GET", path + "?category=DEBUG", null, 200)
                    .get("artifactReady")
                    .getAsBoolean()) {
                assertTrue(System.nanoTime() < until, "Recording ZIP did not finalize");
                Thread.sleep(10);
            }
            mode("READ");
            assertTrue(
                    value("GET", "/v0/recordings", null, 200)
                            .getAsJsonArray("recordings")
                            .isEmpty());
            value("GET", path + "/artifact", null, 404);
            var response =
                    client.send(
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + path
                                                            + "/artifact?category=DEBUG"))
                                    .header("Authorization", "Bearer ui-async-test")
                                    .timeout(Duration.ofSeconds(5))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().length > 100 && response.body().length < 2 * 1024 * 1024);
            StringBuilder content = new StringBuilder();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    content.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
            return content.toString();
        }

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    private String recordedText() throws Exception {
        StringBuilder text = new StringBuilder();
        try (var paths = Files.walk(artifacts)) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".ndjson")).toList())
                text.append(Files.readString(p));
        }
        return text.toString();
    }

    @Test
    void worldDiagnosticEventAuthorityDoesNotReadUiMutationDetails() throws Exception {
        try (var f = new Fixture(",ui", false)) {
            f.mutateUi();
            assertEquals(
                    "SCOPE_DENIED",
                    f.value("GET", "/v0/ui/tree?category=DEBUG", null, 403)
                            .get("error")
                            .getAsString());
            f.value(
                    "POST",
                    "/v0/assert",
                    "{\"condition\":{\"type\":\"event\",\"category\":\"DEBUG\",\"eventType\":\"debug.operation.completed\"}}",
                    412);
            var world = new JsonObject();
            world.addProperty("type", "world.scope.test");
            world.addProperty("diagnostic", "world-remains-readable");
            f.transport.broadcast(world);
            assertTrue(
                    f.value(
                                    "POST",
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"event\",\"category\":\"DEBUG\",\"eventType\":\"world.scope.test\"}}",
                                    200)
                            .toString()
                            .contains("world-remains-readable"));
        }
    }

    @Test
    void worldDiagnosticRecordingCannotCopyUiDetailsWithoutUiReadScope() throws Exception {
        try (var f = new Fixture(",ui", false)) {
            f.record("DEBUG");
            f.mutateUi();
        }
        String text = recordedText();
        assertFalse(text.contains(SECRET), text);
        assertTrue(text.contains("evidence.contamination"), text);
    }

    @Test
    void uiDiagnosticReadPermissionPreservesEventAndContaminationDetails() throws Exception {
        try (var f = new Fixture(",ui,debug.ui.read", false)) {
            f.record("DEBUG");
            f.mutateUi();
            assertTrue(
                    f.value(
                                    "POST",
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"event\",\"category\":\"DEBUG\",\"eventType\":\"debug.operation.completed\"}}",
                                    200)
                            .toString()
                            .contains(SECRET));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!recordedText().contains(SECRET) && System.nanoTime() < until) Thread.sleep(10);
            assertTrue(recordedText().contains(SECRET));
            f.mode("READ");
            f.value(
                    "POST",
                    "/v0/assert",
                    "{\"condition\":{\"type\":\"event\",\"eventType\":\"debug.operation.completed\"}}",
                    412);
        }
        assertTrue(recordedText().contains(SECRET));
    }

    @Test
    void recordingWithoutUiScopeDoesNotCallUiOwner() throws Exception {
        try (var f = new Fixture("", false)) {
            f.record("USER");
            assertEquals(0, f.userUiReads.get());
        }
        assertFalse(recordedText().contains("USER_VIEW_ONLY"));
    }

    @Test
    void uiBatchCannotPublishEventDetailsWithOnlyWorldReadAuthority() throws Exception {
        try (var f = new Fixture(",ui", false)) {
            String operation = f.mutateUiBatch();
            f.value("GET", operation + "?category=DEBUG", null, 403);
            f.value(
                    "POST",
                    "/v0/assert",
                    "{\"condition\":{\"type\":\"event\",\"category\":\"DEBUG\",\"eventType\":\"debug.operation.completed\"}}",
                    412);
        }
    }

    @Test
    void uiBatchRecordingKeepsContaminationButNotUnreadableDetails() throws Exception {
        try (var f = new Fixture(",ui", false)) {
            f.record("DEBUG");
            f.mutateUiBatch();
        }
        String text = recordedText();
        assertTrue(text.contains("evidence.contamination"), text);
        assertFalse(text.contains(SECRET), text);
    }

    @Test
    void uiBatchReadAuthorityPreservesOperationEventAndRecordingDetails() throws Exception {
        try (var f = new Fixture(",ui,debug.ui.read", false)) {
            f.record("DEBUG");
            String operation = f.mutateUiBatch();
            assertTrue(
                    f.value("POST", operation + "/wait?category=DEBUG", "{\"timeoutMs\":2000}", 200)
                            .toString()
                            .contains(SECRET));
            assertTrue(
                    f.value(
                                    "POST",
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"event\",\"category\":\"DEBUG\",\"eventType\":\"debug.operation.completed\"}}",
                                    200)
                            .toString()
                            .contains(SECRET));
            f.mode("READ");
            f.value("GET", operation, null, 404);
        }
        assertTrue(recordedText().contains(SECRET));
    }

    @Test
    void downloadedRecordingDoesNotPromoteWorldReadToUiReadAuthority() throws Exception {
        for (boolean batch : new boolean[] {false, true}) {
            try (var f = new Fixture(",ui", false)) {
                f.record("DEBUG");
                if (batch) f.mutateUiBatch();
                else f.mutateUi();
                JsonObject world = new JsonObject();
                world.addProperty("type", "world.artifact.test");
                world.addProperty("diagnostic", "WORLD_ARTIFACT_REMAINS_READABLE");
                f.transport.broadcast(world);
                String content = f.downloadFinishedRecording();
                assertTrue(content.contains("evidence.contamination"), content);
                assertTrue(content.contains("WORLD_ARTIFACT_REMAINS_READABLE"), content);
                assertFalse(content.contains(SECRET), content);
            }
        }
    }

    @Test
    void downloadedRecordingPreservesAuthorizedUiBatchButNotItsUserLookup() throws Exception {
        try (var f = new Fixture(",ui,debug.ui.read", false)) {
            f.record("DEBUG");
            f.mutateUiBatch();
            String content = f.downloadFinishedRecording();
            assertTrue(content.contains("evidence.contamination"), content);
            assertTrue(content.contains(SECRET), content);
        }
    }

    @Test
    void diagnosticWebsocketStillRequiresIndependentUiReadPermission() throws Exception {
        for (boolean uiRead : new boolean[] {false, true}) {
            try (var f = new Fixture(uiRead ? ",ui,debug.ui.read" : ",ui", false)) {
                var messages = new LinkedBlockingQueue<JsonObject>();
                WebSocket socket =
                        f.client
                                .newWebSocketBuilder()
                                .header("Authorization", "Bearer ui-async-test")
                                .connectTimeout(Duration.ofSeconds(3))
                                .buildAsync(
                                        URI.create(
                                                "ws://127.0.0.1:"
                                                        + f.port
                                                        + "/v0/events?category=DEBUG"),
                                        new WebSocket.Listener() {
                                            private final StringBuilder text = new StringBuilder();

                                            @Override
                                            public void onOpen(WebSocket webSocket) {
                                                webSocket.request(1);
                                            }

                                            @Override
                                            public java.util.concurrent.CompletionStage<?> onText(
                                                    WebSocket webSocket,
                                                    CharSequence data,
                                                    boolean last) {
                                                text.append(data);
                                                if (last) {
                                                    messages.add(
                                                            JsonParser.parseString(text.toString())
                                                                    .getAsJsonObject());
                                                    text.setLength(0);
                                                }
                                                webSocket.request(1);
                                                return null;
                                            }
                                        })
                                .get(4, TimeUnit.SECONDS);
                try {
                    assertNotNull(messages.poll(3, TimeUnit.SECONDS), "WebSocket handshake event");
                    f.mutateUi();
                    // The synchronous mutation response follows publication; the subsequent world
                    // marker bounds the ordered stream without a sleep-based absence assertion.
                    JsonObject barrier = new JsonObject();
                    barrier.addProperty("type", "world.websocket.ui_read_barrier");
                    f.transport.broadcast(barrier);
                    StringBuilder received = new StringBuilder();
                    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                    while (true) {
                        long remaining = until - System.nanoTime();
                        assertTrue(remaining > 0, "World event barrier did not arrive");
                        JsonObject message = messages.poll(remaining, TimeUnit.NANOSECONDS);
                        assertNotNull(message, "World event barrier did not arrive");
                        assertEquals("DEBUG", message.get("dataCategory").getAsString());
                        received.append(message);
                        if ("world.websocket.ui_read_barrier"
                                .equals(message.get("type").getAsString())) break;
                    }
                    assertEquals(uiRead, received.toString().contains(SECRET), received.toString());
                } finally {
                    socket.abort();
                }
            }
        }
    }

    @Test
    void authorizedUiReadSurvivesModeRoundTrip() throws Exception {
        try (var f = new Fixture(",ui", true)) {
            var pending = f.request("GET", "/v0/ui/tree", null);
            assertTrue(f.uiStarted.await(2, TimeUnit.SECONDS));
            f.mode("OPERATE");
            f.mode("READ");
            JsonObject old = new JsonObject();
            old.addProperty("label", SECRET);
            f.deferredUi.complete(old);
            var response = pending.get(6, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains(SECRET));
        }
    }

    @Test
    void authorizedUiResolveSurvivesModeRoundTripAndStillChecksTarget() throws Exception {
        try (var f = new Fixture(",ui", true)) {
            var pending = f.request("POST", "/v0/ui/resolve", "{\"labelContains\":\"test\"}");
            assertTrue(f.uiStarted.await(2, TimeUnit.SECONDS));
            f.mode("OPERATE");
            f.mode("READ");
            f.deferredUi.complete(new JsonObject());
            var response = pending.get(6, TimeUnit.SECONDS);
            assertEquals(404, response.statusCode(), response.body());
            assertEquals(
                    "UI_NODE_NOT_FOUND",
                    JsonParser.parseString(response.body())
                            .getAsJsonObject()
                            .get("error")
                            .getAsString());
        }
    }

    @Test
    void authorizedUiWaitAndAssertSurviveModeRoundTrip() throws Exception {
        for (String path : new String[] {"/v0/assert", "/v0/wait/until"})
            try (var f = new Fixture(",ui", true)) {
                var pending =
                        f.request(
                                "POST",
                                path,
                                "{\"condition\":{\"type\":\"screen\",\"open\":true}}");
                assertTrue(f.uiStarted.await(2, TimeUnit.SECONDS));
                f.mode("OPERATE");
                f.mode("READ");
                JsonObject old = new JsonObject();
                old.addProperty("screenClass", "SecretScreen");
                old.addProperty("label", SECRET);
                f.deferredUi.complete(old);
                var response = pending.get(6, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode(), response.body());
            }
    }
}
