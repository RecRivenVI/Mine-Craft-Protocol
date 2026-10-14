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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldReadTransportTest {
    @TempDir Path artifacts;

    private final class Fixture implements AutoCloseable {
        private final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        private final ProbeTransport transport;
        private final AtomicInteger internalReads = new AtomicInteger();
        private final HttpClient http = HttpClient.newHttpClient();
        private final int port;

        Fixture(String scopes) throws Exception {
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
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
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

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    @Test
    void actualHttpRoutesRejectUnperceivedDataBeforeServiceCapture() throws Exception {
        try (Fixture fixture = new Fixture("read")) {
            assertEquals(401, fixture.get("/v0/player", "wrong-token").statusCode());
            JsonObject user = fixture.value("/v0/player", 200);
            assertEquals("USER", user.get("dataCategory").getAsString());
            assertFalse(user.has("x"));
            for (String path :
                    new String[] {
                        "/v0/server/player",
                        "/v0/world/block?x=0&y=64&z=0",
                        "/v0/server/world/block?x=0&y=64&z=0",
                        "/v0/world/entities",
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
}
