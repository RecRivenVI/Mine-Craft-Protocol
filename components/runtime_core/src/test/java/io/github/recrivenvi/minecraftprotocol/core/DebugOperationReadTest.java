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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugOperationReadTest {
    @TempDir Path artifacts;

    private final class Fixture implements AutoCloseable {
        final HttpClient client = HttpClient.newHttpClient();
        final ProbeTransport transport;
        final int port;
        final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        final CompletableFuture<JsonObject> dispatched = new CompletableFuture<>();
        final CompletableFuture<JsonObject> mutation;

        Fixture(String readScopes, CompletableFuture<JsonObject> mutation) throws Exception {
            this.mutation = mutation;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            System.setProperty(
                    "minecraft.protocol.scopes",
                    "read,ui,control,debug,debug.write,debug.ui,debug.world" + readScopes);
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
                                        if (method.getName().equals("debugUiTree")
                                                || method.getName().equals("uiDebugContext")) {
                                            value.addProperty("uiContextId", "ui-session:test");
                                            value.addProperty("sessionEpoch", "test");
                                        }
                                        if (method.getName().equals("phase9cDebugMutation")) {
                                            dispatched.complete(((JsonObject) args[0]).deepCopy());
                                            return mutation;
                                        }
                                        return CompletableFuture.completedFuture(value);
                                    });
            transport = new ProbeTransport(service, "debug-operation-test", port, "test-target");
            transport.startAsync();
            boolean ready = false;
            for (int i = 0; i < 100; i++) {
                try {
                    request("GET", "/v0/control/mode", null).get(1, TimeUnit.SECONDS);
                    ready = true;
                    break;
                } catch (Exception e) {
                    Thread.sleep(25);
                }
            }
            if (!ready) {
                close();
                throw new AssertionError("Transport failed to start");
            }
        }

        CompletableFuture<HttpResponse<String>> request(String method, String path, String body) {
            return client.sendAsync(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .header("Authorization", "Bearer debug-operation-test")
                            .header("x-mcp-debug-arm", arm == null ? "" : arm)
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

        String arm;

        void mode(String mode) throws Exception {
            JsonObject body = new JsonObject();
            body.addProperty("mode", mode);
            body.add(
                    "expectedModeVersion",
                    value("GET", "/v0/control/mode", null, 200).get("modeVersion"));
            value("POST", "/v0/control/mode", body.toString(), 200);
        }

        JsonObject value(String method, String path, String body, int status) throws Exception {
            var response = request(method, path, body).get(6, TimeUnit.SECONDS);
            assertEquals(status, response.statusCode(), response.body());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }

        String start(boolean mixed) throws Exception {
            mode("OPERATE");
            arm =
                    value(
                                    "POST",
                                    "/v0/debug/arm",
                                    "{\"uiContextId\":\"ui-session:test\",\"namespaces\":[\"ui\"],\"ttlMs\":60000}",
                                    200)
                            .get("debugArmId")
                            .getAsString();
            return value(
                            "POST",
                            "/v0/debug/batches",
                            "{\"items\":[{\"operation\":\"ui.text.set\"}"
                                    + (mixed ? ",{\"operation\":\"world.block.set\"}" : "")
                                    + "]}",
                            200)
                    .get("operationId")
                    .getAsString();
        }

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    private JsonObject secret() {
        JsonObject secret = new JsonObject();
        secret.addProperty("evidenceAuthority", "DEBUG_PRIVILEGED");
        secret.addProperty("privateDraft", "PRIVATE_EDITOR_DRAFT");
        return secret;
    }

    @Test
    void uiDebugOperationCannotBeReadOrWaitedAsUserAndCancelDoesNotPublishItsResult()
            throws Exception {
        try (Fixture f =
                new Fixture(",debug.ui.read", CompletableFuture.completedFuture(secret()))) {
            String path = "/v0/operations/" + f.start(false);
            assertTrue(
                    f.value("POST", path + "/wait?category=DEBUG", "{\"timeoutMs\":2000}", 200)
                            .toString()
                            .contains("PRIVATE_EDITOR_DRAFT"));
            f.mode("READ");
            for (int i = 0; i < 2; i++) {
                assertEquals(
                        "OPERATION_NOT_FOUND",
                        f.value("GET", path, null, 404).get("error").getAsString());
                assertEquals(
                        "OPERATION_NOT_FOUND",
                        f.value("POST", path + "/wait", "{}", 404).get("error").getAsString());
                String condition =
                        "{\"condition\":{\"type\":\"operation\",\"operationId\":\""
                                + path.substring("/v0/operations/".length())
                                + "\",\"expected\":{\"state\":\"completed\"}}}";
                assertEquals(
                        "OPERATION_NOT_FOUND",
                        f.value("POST", "/v0/assert", condition, 404).get("error").getAsString());
                assertEquals(
                        "OPERATION_NOT_FOUND",
                        f.value("POST", "/v0/wait/until", condition, 404)
                                .get("error")
                                .getAsString());
                assertTrue(
                        f.value(
                                        "POST",
                                        "/v0/assert",
                                        condition.replace(
                                                "\"type\":\"operation\"",
                                                "\"type\":\"operation\",\"category\":\"DEBUG\""),
                                        200)
                                .toString()
                                .contains("PRIVATE_EDITOR_DRAFT"));
            }
            assertTrue(
                    f.value("GET", path + "?category=DEBUG", null, 200)
                            .toString()
                            .contains("PRIVATE_EDITOR_DRAFT"));
            assertFalse(
                    f.value("DELETE", path, null, 200).toString().contains("PRIVATE_EDITOR_DRAFT"));
        }
    }

    @Test
    void failedDiagnosticCancellationRedactsPrivatePartialBatchDetails() throws Exception {
        var error =
                new ProtocolState.ProtocolException(
                        "PRIVATE_FAILURE", 409, "PRIVATE_EDITOR_DRAFT", secret());
        try (Fixture f = new Fixture(",debug.ui.read", CompletableFuture.failedFuture(error))) {
            String path = "/v0/operations/" + f.start(false);
            assertTrue(
                    f.value("POST", path + "/wait?category=DEBUG", "{\"timeoutMs\":2000}", 200)
                            .toString()
                            .contains("PRIVATE_EDITOR_DRAFT"));
            var lifecycle = f.value("DELETE", path, null, 200);
            assertFalse(lifecycle.has("result"));
            assertFalse(lifecycle.has("error"));
            assertFalse(lifecycle.has("errorCode"));
            assertFalse(lifecycle.has("errorDetails"));
            assertFalse(lifecycle.toString().contains("PRIVATE_EDITOR_DRAFT"));
        }
    }

    @Test
    void diagnosticReadPublicationPinsModeGenerationWithoutWorldAuthorityForUi() {
        try (var state =
                new ProtocolState(
                        java.util.Set.of("ui", "debug", "debug.ui.read"), "test", ignored -> {})) {
            var domains = new java.util.HashSet<>(java.util.Set.of("UI"));
            JsonObject started =
                    state.startOperation(
                            ignored -> CompletableFuture.completedFuture(secret()), false, domains);
            assertFalse(
                    started.has("result"),
                    "Submission grants no implicit diagnostic read even when native work already completed");
            String id = started.get("operationId").getAsString();
            assertTrue(state.operationStatus(id).has("result"));
            domains.clear();
            assertEquals(java.util.Set.of("UI"), state.operationDiagnosticDomains(id));
            var guard =
                    state.operationReadGuard(
                            id, "DEBUG", category -> fail("UI cannot demand world authority"));
            guard.run();
            state.controlSession()
                    .select(
                            io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession.Mode
                                    .OPERATE,
                            "test");
            assertEquals(
                    "DEBUG_READ_CONTEXT_CHANGED",
                    assertThrows(ProtocolState.ProtocolException.class, guard::run).code());
            assertFalse(state.controlSession().snapshot().agentControlled());
        }
    }

    @Test
    void uiWriteAuthorityDoesNotGrantReadAndMixedOperationsRequireBothReadDomains()
            throws Exception {
        for (String scopes : new String[] {"", ",debug.world.read", ",debug.ui.read"}) {
            try (Fixture f = new Fixture(scopes, CompletableFuture.completedFuture(secret()))) {
                String path = "/v0/operations/" + f.start(!scopes.isEmpty());
                assertEquals(
                        "SCOPE_DENIED",
                        f.value("GET", path + "?category=DEBUG", null, 403)
                                .get("error")
                                .getAsString());
            }
        }
        try (Fixture f =
                new Fixture(
                        ",debug.ui.read,debug.world.read",
                        CompletableFuture.completedFuture(secret()))) {
            String path = "/v0/operations/" + f.start(true);
            assertEquals(
                    "completed",
                    f.value("POST", path + "/wait?category=DEBUG", "{\"timeoutMs\":2000}", 200)
                            .get("state")
                            .getAsString());
        }
    }

    @Test
    void cancelledUiWaitDoesNotPublishLateNativeResultsAfterReturningToRead() throws Exception {
        try (Fixture f = new Fixture(",debug.ui.read", new CompletableFuture<>())) {
            String path = "/v0/operations/" + f.start(false);
            f.dispatched.get(2, TimeUnit.SECONDS);
            var waiting = f.request("POST", path + "/wait?category=DEBUG", "{\"timeoutMs\":2000}");
            // HTTP request order is not a scheduling barrier; the owner is deliberately delayed.
            Thread.sleep(100);
            assertFalse(waiting.isDone());
            assertEquals(
                    "MODE_OPERATION_IN_PROGRESS",
                    f.value(
                                    "POST",
                                    "/v0/control/mode",
                                    "{\"mode\":\"READ\",\"expectedModeVersion\":"
                                            + f.value("GET", "/v0/control/mode", null, 200)
                                                    .get("modeVersion")
                                            + "}",
                                    409)
                            .get("error")
                            .getAsString());
            var cancelled = f.value("DELETE", path, null, 200);
            assertFalse(cancelled.has("result"));
            assertFalse(cancelled.has("errorDetails"));
            var response = waiting.get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), response.body());
            assertFalse(response.body().contains("PRIVATE_EDITOR_DRAFT"));
            f.mode("READ");
            f.mutation.complete(secret());
            assertEquals(
                    "OPERATION_NOT_FOUND",
                    f.value("GET", path, null, 404).get("error").getAsString());
            assertFalse(
                    f.value("GET", path + "?category=DEBUG", null, 200)
                            .toString()
                            .contains("PRIVATE_EDITOR_DRAFT"));
        }
    }
}
