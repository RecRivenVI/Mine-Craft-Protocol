package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerConsoleTransportTest {
    @TempDir Path directory;

    @Test
    void productionTransportRequiresExplicitConsoleScopeModeAndNoInputLease() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty(
                "minecraft.protocol.scopes", "read,event,control,command,server_console");
        ArrayDeque<Runnable> queued = new ArrayDeque<>();
        try (var service =
                        new ServerConsoleService(
                                directory,
                                "26.3-neoforge",
                                new ServerConsole.Host() {
                                    public void schedule(Runnable action) {
                                        synchronized (queued) {
                                            queued.add(action);
                                        }
                                    }

                                    public void dispatch(
                                            String command,
                                            java.util.function.Consumer<String> feedback,
                                            java.util.function.Consumer<JsonObject> result) {
                                        feedback.accept("native " + command);
                                    }
                                });
                var transport = new ProbeTransport(service, "test-token", 0, "test")) {
            transport.startAsync();
            int port = transport.boundPort().get(5, TimeUnit.SECONDS);
            var client = HttpClient.newHttpClient();
            assertEquals(401, call(client, port, "GET", "/v0/session", null, "wrong").statusCode());
            var session = json(call(client, port, "GET", "/v0/session", null, "test-token"));
            assertEquals("DEDICATED_SERVER", session.get("deploymentRole").getAsString());
            assertEquals(
                    409,
                    call(client, port, "POST", "/v0/control/acquire", "{}", "test-token")
                            .statusCode());
            var rejected =
                    json(
                            call(
                                    client,
                                    port,
                                    "POST",
                                    "/v0/server/console/commands",
                                    "{\"command\":\"list\"}",
                                    "test-token"));
            assertTrue(rejected.toString().contains("OPERATE_REQUIRED"));
            assertTrue(queued.isEmpty());
            var mode = json(call(client, port, "GET", "/v0/control/mode", null, "test-token"));
            JsonObject change = new JsonObject();
            change.addProperty("mode", "OPERATE");
            change.add("expectedModeVersion", mode.get("modeVersion"));
            assertEquals(
                    200,
                    call(client, port, "POST", "/v0/control/mode", change.toString(), "test-token")
                            .statusCode());
            var operation =
                    json(
                            call(
                                    client,
                                    port,
                                    "POST",
                                    "/v0/server/console/commands",
                                    "{\"command\":\"list\"}",
                                    "test-token"));
            String id = operation.get("operationId").getAsString();
            assertEquals(
                    200,
                    call(client, port, "DELETE", "/v0/operations/" + id, "{}", "test-token")
                            .statusCode());
            synchronized (queued) {
                queued.remove().run();
            }
            assertTrue(service.console().read(0, 64).getAsJsonArray("records").isEmpty());
            assertEquals(
                    409, call(client, port, "GET", "/v0/capture", null, "test-token").statusCode());
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    @Test
    void defaultServerScopesDoNotGrantConsoleOwnerRights() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.clearProperty("minecraft.protocol.scopes");
        try {
            assertFalse(ProtocolState.configuredScopes(true).contains("server_console"));
            try (var service =
                            new ServerConsoleService(
                                    directory,
                                    "26.3-neoforge",
                                    new ServerConsole.Host() {
                                        public void schedule(Runnable action) {
                                            fail(
                                                    "unauthorized requests must never reach owner queue");
                                        }

                                        public void dispatch(
                                                String command,
                                                java.util.function.Consumer<String> output,
                                                java.util.function.Consumer<JsonObject> result) {
                                            fail("unauthorized dispatch");
                                        }
                                    });
                    var transport = new ProbeTransport(service, "test-token", 0, "26.3-neoforge")) {
                transport.startAsync();
                int port = transport.boundPort().get(5, TimeUnit.SECONDS);
                service.console().publish("INFO", "fixture", "server", "owner-only-text", null);
                var denied =
                        call(
                                HttpClient.newHttpClient(),
                                port,
                                "GET",
                                "/v0/server/console",
                                null,
                                "test-token");
                assertEquals(403, denied.statusCode());
                assertFalse(denied.body().contains("owner-only-text"));
                assertEquals(
                        403,
                        call(
                                        HttpClient.newHttpClient(),
                                        port,
                                        "POST",
                                        "/v0/server/console/commands",
                                        "{\"command\":\"list\"}",
                                        "test-token")
                                .statusCode());
            }
        } finally {
            if (previous != null) System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    private static HttpResponse<String> call(
            HttpClient client, int port, String method, String path, String body, String token)
            throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
