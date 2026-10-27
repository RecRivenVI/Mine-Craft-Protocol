package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerProviderContractTest {
    @TempDir Path directory;

    @Test
    void actualHeadlessDescriptorsAndNestedConditionsAgreeWithoutDebugOrLease() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty("minecraft.protocol.scopes", "read,event,server_console");
        try (var service = new ServerConsoleService(directory, "26.3-neoforge", host());
                var transport = new ProbeTransport(service, "test-token", 0, "26.3-neoforge")) {
            transport.startAsync();
            int port = transport.boundPort().get(5, TimeUnit.SECONDS);
            JsonObject descriptors = json(call(port, "GET", "/v0/providers", null));
            for (var item : descriptors.getAsJsonArray("providers")) {
                var descriptor = item.getAsJsonObject();
                if (descriptor.get("id").getAsString().equals("minecraft:server/console")) {
                    assertEquals(
                            "[\"USER\",\"DEBUG\"]", descriptor.get("readCategories").toString());
                    assertEquals("USER", descriptor.get("defaultReadCategory").getAsString());
                    assertFalse(descriptor.get("explicitCategoryRequired").getAsBoolean());
                } else {
                    assertEquals("minecraft:server/world/deep", descriptor.get("id").getAsString());
                    assertEquals("[\"DEBUG\"]", descriptor.get("readCategories").toString());
                    assertFalse(descriptor.has("defaultReadCategory"));
                    assertTrue(descriptor.get("explicitCategoryRequired").getAsBoolean());
                }
            }
            service.console().publish("INFO", "test", "test", "native fixture", null);
            var implicit =
                    call(
                            port,
                            "POST",
                            "/v0/providers/read",
                            "{\"providerId\":\"minecraft:server/console\"}");
            assertEquals(200, implicit.statusCode(), implicit.body());
            assertEquals("USER", json(implicit).get("dataCategory").getAsString());
            assertEquals(
                    409,
                    call(
                                    port,
                                    "POST",
                                    "/v0/providers/read",
                                    "{\"providerId\":\"minecraft:server/world/deep\"}")
                            .statusCode());
            JsonObject condition =
                    JsonParser.parseString(
                                    "{\"type\":\"provider\",\"category\":\"USER\",\"providerId\":\"minecraft:server/console\",\"params\":{\"limit\":1},\"expected\":{\"data\":{\"authority\":\"SERVER_CONSOLE\"}}}")
                            .getAsJsonObject();
            condition
                    .getAsJsonObject("expected")
                    .getAsJsonObject("data")
                    .add("records", service.console().read(0, 1).get("records"));
            JsonObject body = new JsonObject();
            body.add("condition", condition);
            body.addProperty("timeoutMs", 1000);
            for (String path : java.util.List.of("/v0/assert", "/v0/wait/until")) {
                var response = call(port, "POST", path, body.toString());
                assertEquals(200, response.statusCode(), response.body());
                assertTrue(json(response).get("passed").getAsBoolean());
            }
            condition
                    .getAsJsonObject("expected")
                    .getAsJsonObject("data")
                    .addProperty("authority", "wrong");
            assertEquals(412, call(port, "POST", "/v0/assert", body.toString()).statusCode());
            assertEquals(
                    409,
                    call(
                                    port,
                                    "POST",
                                    "/v0/providers/read",
                                    "{\"providerId\":\"minecraft:server/world/deep\",\"category\":\"USER\"}")
                            .statusCode());
            assertEquals(
                    403,
                    call(
                                    port,
                                    "POST",
                                    "/v0/providers/read",
                                    "{\"providerId\":\"minecraft:server/console\",\"category\":\"DEBUG\"}")
                            .statusCode());
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    @Test
    void userConsoleProviderStillRequiresConsoleScope() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty("minecraft.protocol.scopes", "read,event");
        try (var service = new ServerConsoleService(directory, "26.3-neoforge", host());
                var transport = new ProbeTransport(service, "test-token", 0, "26.3-neoforge")) {
            transport.startAsync();
            int port = transport.boundPort().get(5, TimeUnit.SECONDS);
            var response =
                    call(
                            port,
                            "POST",
                            "/v0/providers/read",
                            "{\"providerId\":\"minecraft:server/console\",\"category\":\"USER\"}");
            assertEquals(403, response.statusCode());
            assertEquals("SCOPE_DENIED", json(response).get("error").getAsString());
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    private static ServerConsole.Host host() {
        return new ServerConsole.Host() {
            public void schedule(Runnable action) {
                action.run();
            }

            public void dispatch(
                    String command,
                    java.util.function.Consumer<String> feedback,
                    java.util.function.Consumer<JsonObject> result) {
                fail("Read must not dispatch a command");
            }
        };
    }

    private static HttpResponse<String> call(int port, String method, String path, String body)
            throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                                .header("Authorization", "Bearer test-token")
                                .header("Content-Type", "application/json")
                                .method(
                                        method,
                                        body == null
                                                ? HttpRequest.BodyPublishers.noBody()
                                                : HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> value) {
        return JsonParser.parseString(value.body()).getAsJsonObject();
    }
}
