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

class WorldReadTransportTest {
    @TempDir Path artifacts;

    private final class Fixture implements AutoCloseable {
        private final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        private final ProbeTransport transport;
        private final AtomicInteger internalReads = new AtomicInteger();
        private final AtomicInteger commandSubmissions = new AtomicInteger();
        private boolean failCommandSubmission;
        private PlayerCommandGate.ConfigurationKind configurationKind;
        private volatile boolean failInputAfterCommand;
        private PlayerCommandGate commandGate;
        private io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession commandControl;
        private final java.util.concurrent.atomic.AtomicBoolean manyEntities =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final HttpClient http = HttpClient.newHttpClient();
        private final int port;

        Fixture(String scopes) throws Exception {
            this(scopes, null);
        }

        Fixture(String scopes, CompletableFuture<JsonObject> scene) throws Exception {
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            System.setProperty("minecraft.protocol.scopes", scopes);
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("attachPlayerCommandGate"))
                                            commandGate = (PlayerCommandGate) args[0];
                                        if (method.getName().equals("attachControlSession"))
                                            commandControl =
                                                    (io.github.recrivenvi.minecraftprotocol.safety
                                                                    .AgentControlSession)
                                                            args[0];
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
                                        if (method.getName().equals("key")
                                                && (int) args[0] == 257) {
                                            if ((int) args[2] == 1) {
                                                try (var dispatch =
                                                        commandGate.enter(
                                                                commandControl.snapshot(),
                                                                InputDispatchContext.capture())) {
                                                    if (configurationKind != null)
                                                        commandGate.allowConfiguration(
                                                                configurationKind.fixedEffect
                                                                                != null
                                                                        ? ""
                                                                        : configurationKind
                                                                                        == PlayerCommandGate
                                                                                                .ConfigurationKind
                                                                                                .JIGSAW_BLOCK
                                                                                ? "minecraft:air"
                                                                                : "seed",
                                                                true,
                                                                configurationKind,
                                                                () ->
                                                                        PlayerCommandPolicyTest
                                                                                .parsed(
                                                                                        PlayerCommandPolicyTest
                                                                                                .frame(
                                                                                                        configurationKind
                                                                                                                        == PlayerCommandGate
                                                                                                                                .ConfigurationKind
                                                                                                                                .JIGSAW_BLOCK
                                                                                                                ? "setblock"
                                                                                                                : "seed")));
                                                    else
                                                        commandGate.allow(
                                                                "give @s stone",
                                                                true,
                                                                true,
                                                                () ->
                                                                        PlayerCommandPolicyTest
                                                                                .parsed(
                                                                                        PlayerCommandPolicyTest
                                                                                                .frame(
                                                                                                        "give")));
                                                    dispatch.check();
                                                    commandSubmissions.incrementAndGet();
                                                }
                                                var result = new JsonObject();
                                                result.addProperty(
                                                        "privateTestData", "PARTIAL_STEP_SECRET");
                                                return CompletableFuture.completedFuture(result);
                                            }
                                            if (failInputAfterCommand)
                                                return CompletableFuture.failedFuture(
                                                        new ProtocolState.ProtocolException(
                                                                "TEST_INPUT_FAILED",
                                                                409,
                                                                "release failed"));
                                        }
                                        if (method.getName().equals("playerCommand")) {
                                            if (!args[1].equals("submit")) {
                                                var phase = new JsonObject();
                                                phase.addProperty("ready", true);
                                                phase.addProperty("typed", true);
                                                phase.addProperty("complete", true);
                                                return CompletableFuture.completedFuture(phase);
                                            }
                                            try (var dispatch =
                                                    commandGate.enter(
                                                            commandControl.snapshot(),
                                                            InputDispatchContext.capture())) {
                                                String command =
                                                        PlayerCommandPolicy.normalize(
                                                                (String) args[0]);
                                                commandGate.allow(
                                                        command,
                                                        true,
                                                        true,
                                                        () ->
                                                                new PlayerCommandPolicy.Parsed(
                                                                        true,
                                                                        java.util.List.of(
                                                                                new PlayerCommandPolicy
                                                                                        .Frame(
                                                                                        command.split(
                                                                                                        " ")[
                                                                                                0],
                                                                                        java.util
                                                                                                .List
                                                                                                .of(
                                                                                                        new PlayerCommandPolicy
                                                                                                                .Node(
                                                                                                                command.split(
                                                                                                                                " ")[
                                                                                                                        0],
                                                                                                                true))))));
                                                dispatch.check();
                                                commandSubmissions.incrementAndGet();
                                                if (failCommandSubmission)
                                                    return CompletableFuture.failedFuture(
                                                            new IllegalStateException(
                                                                    "injected send failure"));
                                                JsonObject result = dispatch.receipt();
                                                result.addProperty(
                                                        "type", "command.player.execute");
                                                return CompletableFuture.completedFuture(result);
                                            }
                                        }
                                        if (method.getName().equals("clientWorldRead")
                                                && args[0].equals("scene")
                                                && scene != null) return scene;
                                        if (method.getName().equals("clientWorldRead"))
                                            return CompletableFuture.completedFuture(
                                                    io.github.recrivenvi.minecraftprotocol.safety
                                                            .WorldClientModel.capture(
                                                            (String) args[0],
                                                            (JsonObject) args[1],
                                                            new io.github.recrivenvi
                                                                    .minecraftprotocol.safety
                                                                    .WorldClientModel.View() {
                                                                public boolean available() {
                                                                    return true;
                                                                }

                                                                public JsonObject player() {
                                                                    return JsonParser.parseString(
                                                                                    "{\"x\":7.25,\"y\":64,\"z\":0,\"health\":20}")
                                                                            .getAsJsonObject();
                                                                }

                                                                public JsonObject block(
                                                                        int x, int y, int z) {
                                                                    return JsonParser.parseString(
                                                                                    "{\"available\":true,\"block\":\"minecraft:stone\"}")
                                                                            .getAsJsonObject();
                                                                }

                                                                public java.util.Iterator<
                                                                                JsonObject>
                                                                        entities() {
                                                                    if (manyEntities.get())
                                                                        return java.util.Collections
                                                                                .nCopies(
                                                                                        129,
                                                                                        JsonParser
                                                                                                .parseString(
                                                                                                        "{\"type\":\"minecraft:pig\",\"x\":1,\"y\":64,\"z\":0}")
                                                                                                .getAsJsonObject())
                                                                                .iterator();
                                                                    return java.util.Collections
                                                                            .emptyIterator();
                                                                }
                                                            }));
                                        if (method.getName().equals("uiTree"))
                                            return CompletableFuture.completedFuture(
                                                    new JsonObject());
                                        if (java.util.Set.of(
                                                        "playerState",
                                                        "serverPlayerState",
                                                        "blockState",
                                                        "serverBlockState",
                                                        "entities",
                                                        "serverEntities")
                                                .contains(method.getName()))
                                            internalReads.incrementAndGet();
                                        JsonObject raw = new JsonObject();
                                        raw.addProperty("type", "test");
                                        raw.addProperty("available", true);
                                        raw.addProperty("x", 1.23456789);
                                        return CompletableFuture.completedFuture(raw);
                                    });
            transport = new ProbeTransport(service, "world-read-http-test", port, "test-target");
            transport.startAsync();
            boolean ready = false;
            for (int i = 0; i < 200; i++) {
                try {
                    get("/v0/player", "world-read-http-test");
                    ready = true;
                    break;
                } catch (java.io.IOException ignored) {
                    Thread.sleep(25);
                }
            }
            if (!ready) {
                close();
                throw new AssertionError("Owned test transport did not start");
            }
        }

        HttpResponse<String> get(String path, String token) throws Exception {
            return http.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(3))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        JsonObject value(String path, int status) throws Exception {
            var response = get(path, "world-read-http-test");
            assertEquals(status, response.statusCode(), response.body());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }

        JsonObject post(String path, String body, int status) throws Exception {
            return post(path, body, status, null);
        }

        JsonObject post(String path, String body, int status, String lease) throws Exception {
            var builder =
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(4))
                            .header("Authorization", "Bearer world-read-http-test")
                            .header("Content-Type", "application/json");
            if (lease != null) builder.header("X-MCP-Control-Lease", lease);
            var response =
                    http.send(
                            builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode(), response.body());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    @Test
    void configurationHttpPipelineNeedsCommandScopeAndPreservesTypedProvenance() throws Exception {
        for (var kind :
                java.util.List.of(
                        PlayerCommandGate.ConfigurationKind.COMMAND_BLOCK,
                        PlayerCommandGate.ConfigurationKind.COMMAND_MINECART,
                        PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK,
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_UPDATE,
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_SCAN))
            for (boolean authorized : new boolean[] {false, true})
                try (Fixture fixture =
                        new Fixture("read,input,control" + (authorized ? ",command" : ""))) {
                    fixture.configurationKind = kind;
                    String lease =
                            fixture.post("/v0/control/acquire", "{\"ttlMs\":15000}", 200)
                                    .get("leaseId")
                                    .getAsString();
                    var started =
                            fixture.post(
                                    "/v0/pipelines",
                                    "{\"steps\":[{\"type\":\"key\",\"key\":257,\"action\":1},{\"type\":\"key\",\"key\":257,\"action\":0}],\"timeoutMs\":5000}",
                                    200,
                                    lease);
                    var result =
                            fixture.post(
                                    "/v0/operations/"
                                            + started.get("operationId").getAsString()
                                            + "/wait",
                                    "{\"timeoutMs\":7000}",
                                    200);
                    assertEquals(
                            authorized ? "completed" : "failed",
                            result.get("state").getAsString(),
                            result.toString());
                    assertEquals(authorized ? 1 : 0, fixture.commandSubmissions.get());
                    if (authorized) {
                        var receipt =
                                result.getAsJsonObject("result")
                                        .getAsJsonArray("commandSubmissions")
                                        .get(0)
                                        .getAsJsonObject();
                        assertEquals(kind.name(), receipt.get("configurationKind").getAsString());
                        assertEquals(
                                kind == PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK
                                                || kind.fixedEffect != null
                                        ? "STATE_MUTATION"
                                        : "INFORMATION",
                                receipt.get("configuredCommandEffect").getAsString());
                        assertEquals(
                                kind.payloadType,
                                receipt.get("configurationPayloadType").getAsString());
                        assertEquals(
                                "STATE_MUTATION",
                                receipt.get("effectClassification").getAsString());
                        assertFalse(receipt.get("effectConfirmed").getAsBoolean());
                    } else assertEquals("SCOPE_DENIED", result.get("errorCode").getAsString());
                    fixture.post("/v0/control/release", "{}", 200, lease);
                }
    }

    @Test
    void failedAndCancelledHttpPipelinePreserveOnlyCommandProvenanceInReadMode() throws Exception {
        for (boolean fail : new boolean[] {false, true})
            try (Fixture fixture = new Fixture("read,input,control,command")) {
                fixture.failInputAfterCommand = fail;
                String lease =
                        fixture.post("/v0/control/acquire", "{\"ttlMs\":15000}", 200)
                                .get("leaseId")
                                .getAsString();
                String steps =
                        "{\"type\":\"key\",\"key\":257,\"action\":1},"
                                + (fail
                                        ? "{\"type\":\"key\",\"key\":257,\"action\":0}"
                                        : "{\"type\":\"delay\",\"durationMs\":10000}");
                var started =
                        fixture.post(
                                "/v0/pipelines",
                                "{\"steps\":[" + steps + "],\"timeoutMs\":15000}",
                                200,
                                lease);
                String path = "/v0/operations/" + started.get("operationId").getAsString();
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (fixture.commandSubmissions.get() == 0 && System.nanoTime() < until)
                    Thread.sleep(5);
                assertEquals(1, fixture.commandSubmissions.get());
                if (!fail) {
                    var response =
                            fixture.http.send(
                                    HttpRequest.newBuilder(
                                                    URI.create(
                                                            "http://127.0.0.1:"
                                                                    + fixture.port
                                                                    + path))
                                            .header("Authorization", "Bearer world-read-http-test")
                                            .timeout(Duration.ofSeconds(3))
                                            .DELETE()
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode(), response.body());
                }
                var terminal = fixture.post(path + "/wait", "{\"timeoutMs\":4000}", 200);
                assertEquals(fail ? "failed" : "cancelled", terminal.get("state").getAsString());
                fixture.post("/v0/control/release", "{}", 200, lease);
                assertEquals(
                        "READ", fixture.value("/v0/control/mode", 200).get("mode").getAsString());
                for (int repeat = 0; repeat < 3; repeat++) {
                    var read = fixture.value(path, 200);
                    var result = read.getAsJsonObject("result");
                    assertTrue(read.get("commandSubmissionsComplete").getAsBoolean());
                    assertTrue(result.get("partial").getAsBoolean());
                    assertEquals(1, result.getAsJsonArray("commandSubmissions").size());
                    assertEquals("DEBUG_PRIVILEGED", result.get("evidenceAuthority").getAsString());
                    assertTrue(result.get("evidenceContaminated").getAsBoolean());
                    assertFalse(result.get("commandEffectsConfirmed").getAsBoolean());
                    assertFalse(read.toString().contains("PARTIAL_STEP_SECRET"));
                    assertFalse(read.toString().contains("give @s stone"));
                }
            }
    }

    @Test
    void replayedPipelineTerminalEventContainsCommittedFailureAndCommandProvenance()
            throws Exception {
        try (Fixture fixture =
                new Fixture("read,input,control,command,event,debug,debug.world.read")) {
            fixture.failInputAfterCommand = true;
            String lease =
                    fixture.post("/v0/control/acquire", "{\"ttlMs\":15000}", 200)
                            .get("leaseId")
                            .getAsString();
            var started =
                    fixture.post(
                            "/v0/pipelines",
                            "{\"steps\":[{\"type\":\"key\",\"key\":257,\"action\":1},"
                                    + "{\"type\":\"delay\",\"durationMs\":100},"
                                    + "{\"type\":\"key\",\"key\":257,\"action\":0}],\"timeoutMs\":5000}",
                            200,
                            lease);
            String id = started.get("operationId").getAsString();
            assertEquals(
                    "failed",
                    fixture.post("/v0/operations/" + id + "/wait", "{\"timeoutMs\":4000}", 200)
                            .get("state")
                            .getAsString());
            fixture.post("/v0/control/release", "{}", 200, lease);
            var messages = new java.util.concurrent.LinkedBlockingQueue<JsonObject>();
            var socket =
                    fixture.http
                            .newWebSocketBuilder()
                            .header("Authorization", "Bearer world-read-http-test")
                            .connectTimeout(Duration.ofSeconds(3))
                            .buildAsync(
                                    URI.create(
                                            "ws://127.0.0.1:"
                                                    + fixture.port
                                                    + "/v0/events?category=DEBUG&resumeFromSequence=0"
                                                    + "&type=event.pipeline.terminal"),
                                    new java.net.http.WebSocket.Listener() {
                                        private final StringBuilder text = new StringBuilder();

                                        @Override
                                        public void onOpen(java.net.http.WebSocket socket) {
                                            socket.request(1);
                                        }

                                        @Override
                                        public java.util.concurrent.CompletionStage<?> onText(
                                                java.net.http.WebSocket socket,
                                                CharSequence data,
                                                boolean last) {
                                            text.append(data);
                                            if (last) {
                                                messages.add(
                                                        JsonParser.parseString(text.toString())
                                                                .getAsJsonObject());
                                                text.setLength(0);
                                            }
                                            socket.request(1);
                                            return null;
                                        }
                                    })
                            .get(4, TimeUnit.SECONDS);
            try {
                var hello = messages.poll(3, TimeUnit.SECONDS);
                assertNotNull(hello);
                assertEquals("event.hello", hello.get("type").getAsString());
                var terminal = messages.poll(3, TimeUnit.SECONDS);
                assertNotNull(terminal);
                assertEquals("event.pipeline.terminal", terminal.get("type").getAsString());
                assertEquals(id, terminal.get("operationId").getAsString());
                assertEquals("failed", terminal.get("state").getAsString());
                assertFalse(terminal.get("executing").getAsBoolean());
                assertTrue(terminal.get("commandSubmissionsComplete").getAsBoolean());
                var result = terminal.getAsJsonObject("result");
                assertTrue(result.get("partial").getAsBoolean());
                assertEquals(1, result.getAsJsonArray("commandSubmissions").size());
                assertTrue(result.get("evidenceContaminated").getAsBoolean());
                assertFalse(result.get("commandEffectsConfirmed").getAsBoolean());
                assertFalse(terminal.toString().contains("PARTIAL_STEP_SECRET"));
                assertNull(messages.poll(100, TimeUnit.MILLISECONDS));
            } finally {
                socket.abort();
            }
        }
    }

    @Test
    void nativeChatHelperDoesNotClaimServerExecutionOrGameplayAcceptance() throws Exception {
        try (Fixture fixture = new Fixture("read,capture,control,command,input")) {
            String id =
                    fixture.post(
                                    "/v0/recordings",
                                    "{\"captureFrames\":false,\"intervalMs\":60000,\"stateReads\":[],\"contactSheet\":{\"enabled\":false}}",
                                    200)
                            .get("recordingId")
                            .getAsString();
            assertEquals(
                    "TAKEOVER_REQUIRED",
                    fixture.post("/v0/command/player", "{\"command\":\"help\"}", 409)
                            .get("error")
                            .getAsString());
            assertFalse(
                    fixture.value("/v0/recordings/" + id, 200)
                            .get("evidenceContaminated")
                            .getAsBoolean());
            assertEquals(0, fixture.commandSubmissions.get());
            String lease =
                    fixture.post("/v0/control/acquire", "{\"ttlMs\":60000}", 200)
                            .get("leaseId")
                            .getAsString();
            JsonObject receipt =
                    fixture.post("/v0/command/player", "{\"command\":\"help\"}", 200, lease);
            assertTrue(receipt.get("accepted").getAsBoolean());
            assertEquals("COMMUNICATION", receipt.get("effectClassification").getAsString());
            assertFalse(receipt.get("effectConfirmed").getAsBoolean());
            assertFalse(receipt.get("serverValidationObserved").getAsBoolean());
            assertFalse(receipt.get("gameplayAcceptanceEligible").getAsBoolean());
            assertEquals("GAME_ROUTED_RAW", receipt.get("mechanism").getAsString());
            assertFalse(receipt.get("evidenceContaminated").getAsBoolean());
            assertFalse(
                    fixture.value("/v0/recordings/" + id, 200)
                            .get("evidenceContaminated")
                            .getAsBoolean());
            fixture.post("/v0/control/release", "{}", 200, lease);
        }
    }

    @Test
    void nativeCommandHelperNeedsItsOwnInputScopeBeforeAnySubmission() throws Exception {
        try (Fixture fixture = new Fixture("read,control,command")) {
            String lease =
                    fixture.post("/v0/control/acquire", "{\"ttlMs\":60000}", 200)
                            .get("leaseId")
                            .getAsString();
            assertEquals(
                    "SCOPE_DENIED",
                    fixture.post("/v0/command/player", "{\"command\":\"help\"}", 403, lease)
                            .get("error")
                            .getAsString());
            assertEquals(0, fixture.commandSubmissions.get());
            fixture.post("/v0/control/release", "{}", 200, lease);
        }
    }

    @Test
    void failedSubmissionDoesNotErasePreSubmissionContamination() throws Exception {
        try (Fixture fixture = new Fixture("read,capture,control,command,input")) {
            String id =
                    fixture.post(
                                    "/v0/recordings",
                                    "{\"captureFrames\":false,\"intervalMs\":60000,\"stateReads\":[],\"contactSheet\":{\"enabled\":false}}",
                                    200)
                            .get("recordingId")
                            .getAsString();
            String lease =
                    fixture.post("/v0/control/acquire", "{\"ttlMs\":60000}", 200)
                            .get("leaseId")
                            .getAsString();
            fixture.failCommandSubmission = true;
            fixture.post("/v0/command/player", "{\"command\":\"give @s stone\"}", 500, lease);
            assertEquals(1, fixture.commandSubmissions.get());
            assertTrue(
                    fixture.value("/v0/recordings/" + id, 200)
                            .get("evidenceContaminated")
                            .getAsBoolean());
            fixture.post("/v0/control/release", "{}", 200, lease);
        }
        try (var files = java.nio.file.Files.walk(artifacts)) {
            var manifest =
                    files.filter(file -> file.getFileName().toString().equals("manifest.json"))
                            .findFirst()
                            .orElseThrow();
            assertTrue(
                    JsonParser.parseString(java.nio.file.Files.readString(manifest))
                            .getAsJsonObject()
                            .get("evidenceContaminated")
                            .getAsBoolean());
        }
    }

    @Test
    void semanticWorldStepDoesNotBypassHttpScopeOrMode() throws Exception {
        String body =
                "{\"steps\":[{\"type\":\"world.action\",\"targetRef\":\"world:test\",\"action\":\"use\"}]}";
        try (Fixture fixture = new Fixture("read")) {
            assertEquals(
                    "SCOPE_DENIED",
                    fixture.post("/v0/pipelines", body, 403).get("error").getAsString());
            assertEquals(0, fixture.internalReads.get());
        }
        try (Fixture fixture = new Fixture("read,input")) {
            assertEquals(
                    "TAKEOVER_REQUIRED",
                    fixture.post("/v0/pipelines", body, 409).get("error").getAsString());
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void sceneHttpDeadlineCancelsTheNativePresentationRead() throws Exception {
        var pending = new CompletableFuture<JsonObject>();
        try (Fixture fixture = new Fixture("read", pending)) {
            var response =
                    fixture.http.send(
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + fixture.port
                                                            + "/v0/world/scene"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Authorization", "Bearer world-read-http-test")
                                    .header(ProtocolState.DEADLINE_HEADER, "25")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(408, response.statusCode(), response.body());
            assertEquals(
                    "REQUEST_DEADLINE_EXCEEDED",
                    JsonParser.parseString(response.body())
                            .getAsJsonObject()
                            .get("error")
                            .getAsString());
            assertThrows(
                    java.util.concurrent.CancellationException.class,
                    () -> pending.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(pending.isCancelled());
        }
    }

    @Test
    void actualHttpRoutesRejectUnperceivedDataBeforeServiceCapture() throws Exception {
        try (Fixture fixture = new Fixture("read")) {
            assertEquals(401, fixture.get("/v0/player", "wrong-token").statusCode());
            JsonObject user = fixture.value("/v0/player", 200);
            assertEquals("USER", user.get("dataCategory").getAsString());
            assertEquals(7.25, user.get("x").getAsDouble());
            for (String path :
                    new String[] {
                        "/v0/server/player",
                        "/v0/server/world/block?x=0&y=64&z=0",
                        "/v0/server/world/entities"
                    })
                assertEquals(
                        "WORLD_USER_PERCEPTION_UNAVAILABLE",
                        fixture.value(path, 409).get("error").getAsString());
            assertEquals(
                    "SCOPE_DENIED",
                    fixture.value("/v0/player?category=DEBUG", 403).get("error").getAsString());
            assertEquals(
                    "INVALID_WORLD_CATEGORY",
                    fixture.value("/v0/player?category=CLIENT_KNOWN", 400)
                            .get("error")
                            .getAsString());
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void allSixActualHttpBindingsPreserveExplicitInternalData() throws Exception {
        try (Fixture fixture = new Fixture("read,debug,debug.world.read")) {
            for (String path :
                    new String[] {
                        "/v0/player",
                        "/v0/server/player",
                        "/v0/world/block",
                        "/v0/server/world/block",
                        "/v0/world/entities",
                        "/v0/server/world/entities"
                    }) {
                JsonObject value = fixture.value(path + "?category=DEBUG&x=0&y=64&z=0", 200);
                assertEquals("WORLD", value.get("domain").getAsString());
                assertEquals("DEBUG", value.get("dataCategory").getAsString());
                assertEquals(1.23456789, value.get("x").getAsDouble());
            }
            assertEquals(6, fixture.internalReads.get());
        }
    }

    @Test
    void providerFrameConditionAndResyncCannotBypassUserProjection() throws Exception {
        try (Fixture fixture = new Fixture("read,event")) {
            JsonObject player =
                    fixture.post(
                            "/v0/providers/read",
                            "{\"providerId\":\"minecraft:client/player\"}",
                            200);
            assertEquals("USER", player.get("dataCategory").getAsString());
            assertEquals(7.25, player.getAsJsonObject("data").get("x").getAsDouble());
            for (String body :
                    new String[] {
                        "{\"providerId\":\"minecraft:server/player\"}",
                        "{\"providerId\":\"unknown:hidden-provider\"}"
                    }) {
                assertEquals(
                        "WORLD_USER_PERCEPTION_UNAVAILABLE",
                        fixture.post("/v0/providers/read", body, 409).get("error").getAsString());
            }
            assertEquals(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    fixture.post(
                                    "/v0/state/frames",
                                    "{\"reads\":[{\"providerId\":\"minecraft:client/player\"},{\"providerId\":\"minecraft:server/player\"}]}",
                                    409)
                            .get("error")
                            .getAsString());
            fixture.post(
                    "/v0/assert",
                    "{\"condition\":{\"type\":\"block\",\"x\":0,\"y\":64,\"z\":0,\"blockId\":\"minecraft:stone\"}}",
                    200);
            fixture.post(
                    "/v0/assert", "{\"condition\":{\"type\":\"entity\",\"exists\":false}}", 200);
            fixture.post(
                    "/v0/observe/deep",
                    "{\"domains\":[\"player\"],\"perspective\":\"client_known\"}",
                    409);
            fixture.value("/v0/world/fingerprint", 409);
            fixture.post(
                    "/v0/assert", "{\"condition\":{\"type\":\"player\",\"healthMin\":1}}", 200);
            fixture.post(
                    "/v0/wait/until",
                    "{\"condition\":{\"type\":\"player\",\"expected\":{\"x\":7.25}}}",
                    200);
            fixture.value("/v0/world/entities?radius=33", 400);
            fixture.value("/v0/world/scene?radius=9", 400);
            fixture.value("/v0/world/block?x=0.5&y=64&z=0", 400);
            var resync = fixture.value("/v0/events/resync", 200);
            assertEquals("USER", resync.get("dataCategory").getAsString());
            assertEquals(7.25, resync.getAsJsonObject("player").get("x").getAsDouble());
            assertFalse(resync.has("operations"));
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void userConditionsValidateOriginalCoordinatesAndBudgetsBeforeLegacyCoercion()
            throws Exception {
        try (Fixture fixture = new Fixture("read")) {
            for (String condition :
                    new String[] {
                        "{\"type\":\"block\",\"x\":0.5,\"y\":64,\"z\":0}",
                        "{\"type\":\"block\",\"x\":2147483648,\"y\":64,\"z\":0}",
                        "{\"type\":\"entity\",\"radius\":-1}",
                        "{\"type\":\"entity\",\"radius\":33}"
                    }) {
                for (String route : new String[] {"/v0/assert", "/v0/wait/until"}) {
                    assertEquals(
                            "INVALID_WORLD_USER_QUERY",
                            fixture.post(route, "{\"condition\":" + condition + "}", 400)
                                    .get("error")
                                    .getAsString());
                }
            }
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void truncatedClientEntitiesCannotProveThatAnUnmatchedTypeIsAbsent() throws Exception {
        try (Fixture fixture = new Fixture("read")) {
            fixture.manyEntities.set(true);
            assertEquals(
                    "WORLD_USER_QUERY_INCOMPLETE",
                    fixture.post(
                                    "/v0/assert",
                                    "{\"condition\":{\"type\":\"entity\",\"entityType\":\"minecraft:cow\",\"exists\":false}}",
                                    409)
                            .get("error")
                            .getAsString());
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void explicitDebugMustBeAuthorizedBeforeEveryConsumerReads() throws Exception {
        try (Fixture fixture = new Fixture("read,event,capture")) {
            fixture.post(
                    "/v0/providers/read",
                    "{\"providerId\":\"minecraft:client/player\",\"category\":\"DEBUG\"}",
                    403);
            fixture.post(
                    "/v0/state/frames",
                    "{\"category\":\"DEBUG\",\"reads\":[{\"providerId\":\"minecraft:client/player\"}]}",
                    403);
            fixture.post(
                    "/v0/assert",
                    "{\"condition\":{\"type\":\"player\",\"category\":\"DEBUG\",\"healthMin\":1}}",
                    403);
            fixture.post(
                    "/v0/observe/deep", "{\"category\":\"DEBUG\",\"domains\":[\"player\"]}", 403);
            fixture.value("/v0/events/resync?category=DEBUG", 403);
            fixture.post("/v0/recordings", "{\"category\":\"DEBUG\",\"captureFrames\":false}", 403);
            fixture.value("/v0/recordings/unknown/artifact?category=DEBUG", 403);
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void diagnosticReadsDoNotAdvanceUserProviderOrFrameSequences() throws Exception {
        try (Fixture fixture = new Fixture("read,debug,debug.world.read")) {
            String userRequest = "{\"reads\":[{\"providerId\":\"minecraft:client/player\"}]}";
            JsonObject first = fixture.post("/v0/state/frames", userRequest, 200);
            String diagnostic =
                    "{\"category\":\"DEBUG\",\"reads\":[{\"providerId\":\"minecraft:client/player\"}]}";
            for (int i = 0; i < 5; i++) fixture.post("/v0/state/frames", diagnostic, 200);
            JsonObject next = fixture.post("/v0/state/frames", userRequest, 200);
            assertEquals(
                    first.get("stateFrameSequence").getAsLong() + 1,
                    next.get("stateFrameSequence").getAsLong());
            assertEquals(
                    first.getAsJsonArray("reads")
                                    .get(0)
                                    .getAsJsonObject()
                                    .get("providerRevision")
                                    .getAsLong()
                            + 1,
                    next.getAsJsonArray("reads")
                            .get(0)
                            .getAsJsonObject()
                            .get("providerRevision")
                            .getAsLong());
            assertFalse(next.toString().contains("1.23456789"));
        }
    }

    @Test
    void recordingPreflightRejectsInternalUserSourcesBeforeCreatingArtifacts() throws Exception {
        try (Fixture fixture = new Fixture("read,capture")) {
            fixture.post(
                    "/v0/recordings",
                    "{\"captureFrames\":false,\"stateReads\":[{\"providerId\":\"minecraft:server/player\"}]}",
                    409);
            try (var files = java.nio.file.Files.list(artifacts)) {
                assertEquals(0, files.count());
            }
            assertEquals(0, fixture.internalReads.get());
        }
    }

    @Test
    void hiddenEventsDoNotChangeUserResyncCursorOrLeakThroughUserWebsocket() throws Exception {
        try (Fixture fixture = new Fixture("read,event,control,debug,debug.world.read")) {
            java.util.concurrent.BlockingQueue<JsonObject> messages =
                    new java.util.concurrent.LinkedBlockingQueue<>();
            var socket =
                    fixture.http
                            .newWebSocketBuilder()
                            .header("Authorization", "Bearer world-read-http-test")
                            .buildAsync(
                                    URI.create(
                                            "ws://127.0.0.1:"
                                                    + fixture.port
                                                    + "/v0/events?category=USER"),
                                    new java.net.http.WebSocket.Listener() {
                                        private final StringBuilder text = new StringBuilder();

                                        @Override
                                        public void onOpen(java.net.http.WebSocket socket) {
                                            socket.request(1);
                                        }

                                        @Override
                                        public java.util.concurrent.CompletionStage<?> onText(
                                                java.net.http.WebSocket socket,
                                                CharSequence data,
                                                boolean last) {
                                            text.append(data);
                                            if (last) {
                                                messages.add(
                                                        JsonParser.parseString(text.toString())
                                                                .getAsJsonObject());
                                                text.setLength(0);
                                            }
                                            socket.request(1);
                                            return null;
                                        }
                                    })
                            .join();
            try {
                JsonObject hello = messages.poll(3, java.util.concurrent.TimeUnit.SECONDS);
                assertNotNull(hello);
                long cursor =
                        fixture.value("/v0/events/resync", 200).get("resumeCursor").getAsLong();
                JsonObject secret =
                        JsonParser.parseString(
                                        "{\"type\":\"entity.hidden.changed\",\"uuid\":\"secret\",\"x\":12345}")
                                .getAsJsonObject();
                fixture.transport.broadcast(secret);
                assertEquals(
                        cursor,
                        fixture.value("/v0/events/resync", 200).get("resumeCursor").getAsLong());
                assertTrue(
                        fixture.value("/v0/events/resync?category=DEBUG", 200)
                                        .get("resumeCursor")
                                        .getAsLong()
                                > cursor);
                secret.addProperty("type", "event.screen.changed");
                secret.addProperty("screenClass", "VisibleScreen");
                fixture.transport.broadcast(secret);
                JsonObject visible = messages.poll(3, java.util.concurrent.TimeUnit.SECONDS);
                assertNotNull(visible);
                assertEquals("event.screen.changed", visible.get("type").getAsString());
                assertEquals(cursor + 1, visible.get("sequence").getAsLong());
                assertFalse(visible.has("uuid"));
                assertFalse(visible.has("x"));
                assertEquals("USER", visible.get("dataCategory").getAsString());
            } finally {
                socket.abort();
            }
        }
    }

    @Test
    void debugRecordingCannotBeListedReadOrDownloadedAsUser() throws Exception {
        try (Fixture fixture = new Fixture("read,capture,debug,debug.world.read")) {
            JsonObject recording =
                    fixture.post(
                            "/v0/recordings",
                            "{\"category\":\"DEBUG\",\"captureFrames\":false,\"maxSamples\":1,\"intervalMs\":50,\"stateReads\":[{\"providerId\":\"minecraft:client/player\"}],\"contactSheet\":{\"enabled\":false}}",
                            200);
            String id = recording.get("recordingId").getAsString();
            assertEquals("DEBUG", recording.get("dataCategory").getAsString());
            assertTrue(fixture.value("/v0/recordings", 200).getAsJsonArray("recordings").isEmpty());
            fixture.value("/v0/recordings/" + id, 404);
            fixture.value("/v0/recordings/" + id + "/artifact", 404);
            fixture.post(
                    "/v0/assert",
                    "{\"condition\":{\"type\":\"recording\",\"recordingId\":\"" + id + "\"}}",
                    404);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
            JsonObject status;
            do {
                status = fixture.value("/v0/recordings/" + id + "?category=DEBUG", 200);
                if (status.get("artifactReady").getAsBoolean()) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertTrue(status.get("artifactReady").getAsBoolean(), status.toString());
            var response =
                    fixture.http.send(
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + fixture.port
                                                            + "/v0/recordings/"
                                                            + id
                                                            + "/artifact?category=DEBUG"))
                                    .header("Authorization", "Bearer world-read-http-test")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals(80, Byte.toUnsignedInt(response.body()[0]));
            try (var zip =
                    new java.util.zip.ZipInputStream(
                            new java.io.ByteArrayInputStream(response.body()))) {
                boolean secretFound = false;
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    String content =
                            new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    secretFound |= content.contains("1.23456789");
                }
                assertTrue(
                        secretFound,
                        "DEBUG artifact must preserve internally authorized precision");
            }
        }
    }
}
