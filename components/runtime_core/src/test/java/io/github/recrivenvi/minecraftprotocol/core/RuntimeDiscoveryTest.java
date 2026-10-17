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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeDiscoveryTest {
    @TempDir Path root;

    private ProbeService service(AtomicInteger calls) {
        return (ProbeService)
                Proxy.newProxyInstance(
                        ProbeService.class.getClassLoader(),
                        new Class<?>[] {ProbeService.class},
                        (proxy, method, args) -> {
                            if (method.getReturnType() == void.class) return null;
                            if (method.getName().equals("artifactRoot"))
                                return root.resolve("artifacts");
                            if (method.getName().equals("session")) calls.incrementAndGet();
                            JsonObject value = new JsonObject();
                            value.addProperty("target", "test-target");
                            return CompletableFuture.completedFuture(value);
                        });
    }

    private JsonObject record(Path directory) throws Exception {
        return JsonParser.parseString(
                        Files.readString(directory.resolve("minecraft-protocol/endpoint.json")))
                .getAsJsonObject();
    }

    private HttpResponse<String> request(
            int port, String token, String instance, String path, String method) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                                .timeout(Duration.ofSeconds(3))
                                .header("Authorization", "Bearer " + token)
                                .header(RuntimeEndpoint.INSTANCE_HEADER, instance)
                                .method(method, HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fiveConcurrentInstancesUseOsAssignedPortsAndSeparateIdentities() throws Exception {
        var transports = new ArrayList<ProbeTransport>();
        var ports = new HashSet<Integer>();
        var identities = new HashSet<String>();
        try {
            for (int i = 0; i < 5; i++) {
                Path directory = Files.createDirectory(root.resolve("instance" + i));
                ProbeTransport transport =
                        ProbeTransport.forInstance(
                                service(new AtomicInteger()), directory, "test-target");
                transports.add(transport);
                transport.startAsync();
            }
            for (int i = 0; i < 5; i++) {
                int port = transports.get(i).boundPort().get(5, TimeUnit.SECONDS);
                JsonObject record = record(root.resolve("instance" + i));
                String id = record.get("runtimeInstanceId").getAsString();
                assertTrue(ports.add(port));
                assertTrue(identities.add(id));
                assertEquals(port, record.get("port").getAsInt());
                assertFalse(record.has("token"));
                String token =
                        Files.readString(
                                root.resolve("instance" + i + "/minecraft-protocol/token"));
                var response = request(port, token, id, "/v0/session", "GET");
                assertEquals(200, response.statusCode());
                assertEquals(
                        id,
                        response.headers()
                                .firstValue(RuntimeEndpoint.INSTANCE_HEADER)
                                .orElseThrow());
                assertEquals(
                        id,
                        JsonParser.parseString(response.body())
                                .getAsJsonObject()
                                .getAsJsonObject("runtimeConnection")
                                .get("runtimeInstanceId")
                                .getAsString());
            }
        } finally {
            transports.forEach(ProbeTransport::close);
        }
    }

    @Test
    void sameDirectoryRefusesSecondOwnerBeforeTokenIsOverwritten() throws Exception {
        try (var first =
                ProbeTransport.forInstance(service(new AtomicInteger()), root, "test-target")) {
            String token = Files.readString(root.resolve("minecraft-protocol/token"));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            ProbeTransport.forInstance(
                                    service(new AtomicInteger()), root, "other-target"));
            assertEquals(token, Files.readString(root.resolve("minecraft-protocol/token")));
            first.startAsync();
            first.boundPort().get(5, TimeUnit.SECONDS);
            assertEquals("test-target", record(root).get("target").getAsString());
        }
    }

    @Test
    void staleIdentityFailsBeforeDispatchEvenWithCorrectTokenIncludingWebSocket() throws Exception {
        var calls = new AtomicInteger();
        try (var transport = ProbeTransport.forInstance(service(calls), root, "test-target")) {
            transport.startAsync();
            int port = transport.boundPort().get(5, TimeUnit.SECONDS);
            String token = Files.readString(root.resolve("minecraft-protocol/token"));
            for (String path :
                    new String[] {
                        "/v0/session", "/v0/events", "/v0/capture/composite", "/v0/control/acquire"
                    }) {
                var response =
                        request(
                                port,
                                token,
                                "stale-id",
                                path,
                                path.equals("/v0/control/acquire") ? "POST" : "GET");
                assertEquals(409, response.statusCode(), response.body());
                assertTrue(response.body().contains("RUNTIME_INSTANCE_MISMATCH"));
            }
            assertEquals(0, calls.get());
            assertEquals(
                    401,
                    request(port, "wrong-token", "stale-id", "/v0/session", "GET").statusCode());
            assertEquals(
                    200,
                    request(
                                    port,
                                    token,
                                    record(root).get("runtimeInstanceId").getAsString(),
                                    "/v0/session",
                                    "GET")
                            .statusCode());
        }
    }

    @Test
    void startupCloseRaceNeverPublishesLateEndpointAndReleasesDirectory() throws Exception {
        for (int i = 0; i < 12; i++) {
            Path directory = Files.createDirectory(root.resolve("race" + i));
            var transport =
                    ProbeTransport.forInstance(
                            service(new AtomicInteger()), directory, "test-target");
            transport.startAsync();
            transport.close();
            awaitNoEndpoint(directory);
            try (var next = RuntimeEndpoint.acquire(directory, "test-target")) {
                assertNotNull(next.instanceId());
            }
        }
    }

    @Test
    void crashResidueIsReplacedWithFreshIdentityAndShutdownRemovesOnlyOwnRecord() throws Exception {
        Files.createDirectories(root.resolve("minecraft-protocol"));
        Files.writeString(
                root.resolve("minecraft-protocol/endpoint.json"),
                "{\"runtimeInstanceId\":\"crashed\"}");
        String firstId;
        try (var transport =
                ProbeTransport.forInstance(service(new AtomicInteger()), root, "test-target")) {
            assertFalse(Files.exists(root.resolve("minecraft-protocol/endpoint.json")));
            transport.startAsync();
            transport.boundPort().get(5, TimeUnit.SECONDS);
            firstId = record(root).get("runtimeInstanceId").getAsString();
            assertNotEquals("crashed", firstId);
        }
        awaitNoEndpoint(root);
        try (var next =
                ProbeTransport.forInstance(service(new AtomicInteger()), root, "test-target")) {
            next.startAsync();
            next.boundPort().get(5, TimeUnit.SECONDS);
            assertNotEquals(firstId, record(root).get("runtimeInstanceId").getAsString());
        }
    }

    @Test
    void explicitOccupiedPortFailsWithoutFallbackOrPublishedRecord() throws Exception {
        try (var socket = new ServerSocket(0)) {
            System.setProperty(
                    "minecraft.protocol.probe.port", Integer.toString(socket.getLocalPort()));
            try (var transport =
                    ProbeTransport.forInstance(service(new AtomicInteger()), root, "test-target")) {
                transport.startAsync();
                assertThrows(
                        java.util.concurrent.ExecutionException.class,
                        () -> transport.boundPort().get(5, TimeUnit.SECONDS));
            } finally {
                System.clearProperty("minecraft.protocol.probe.port");
            }
            awaitNoEndpoint(root);
        }
    }

    @Test
    void configuredPortRejectsInvalidValuesAndHonorsExplicitPort() {
        try {
            assertEquals(0, RuntimeEndpoint.configuredPort());
            for (String invalid : new String[] {"bad", "-1", "65536", ""}) {
                System.setProperty("minecraft.protocol.probe.port", invalid);
                assertThrows(IllegalArgumentException.class, RuntimeEndpoint::configuredPort);
            }
            System.setProperty("minecraft.protocol.probe.port", "25581");
            assertEquals(25581, RuntimeEndpoint.configuredPort());
        } finally {
            System.clearProperty("minecraft.protocol.probe.port");
        }
    }

    private void awaitNoEndpoint(Path directory) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Files.exists(directory.resolve("minecraft-protocol/endpoint.json"))
                && System.nanoTime() < until) Thread.sleep(10);
        assertFalse(Files.exists(directory.resolve("minecraft-protocol/endpoint.json")));
    }

    @Test
    void externalProcessDirectoryLockRejectsStartupAndReleasesAfterProcessCrash() throws Exception {
        Path directory = Files.createDirectories(root.resolve("external/minecraft-protocol"));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes =
                Path.of(
                                RuntimeDiscoveryTest.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toString();
        Process child =
                new ProcessBuilder(
                                java,
                                "-cp",
                                classes,
                                LockHolder.class.getName(),
                                directory.resolve("instance.lock").toString())
                        .redirectErrorStream(true)
                        .start();
        try {
            var line =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return child.inputReader().readLine();
                                } catch (java.io.IOException exception) {
                                    throw new RuntimeException(exception);
                                }
                            });
            assertEquals("LOCKED", line.get(5, TimeUnit.SECONDS));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            ProbeTransport.forInstance(
                                    service(new AtomicInteger()),
                                    directory.getParent(),
                                    "test-target"));
            assertFalse(Files.exists(directory.resolve("token")));
        } finally {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
        }
        try (var endpoint = RuntimeEndpoint.acquire(directory.getParent(), "test-target")) {
            assertNotNull(endpoint.instanceId());
        }
    }

    public static final class LockHolder {
        public static void main(String[] args) throws Exception {
            try (var channel =
                            java.nio.channels.FileChannel.open(
                                    Path.of(args[0]),
                                    java.nio.file.StandardOpenOption.CREATE,
                                    java.nio.file.StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                System.out.println("LOCKED");
                System.out.flush();
                while (System.in.read() != -1) {}
            }
        }
    }

    @Test
    void realWebSocketUpgradeUsesTheSameIdentityBoundary() throws Exception {
        try (var transport =
                ProbeTransport.forInstance(service(new AtomicInteger()), root, "test-target")) {
            transport.startAsync();
            int port = transport.boundPort().get(5, TimeUnit.SECONDS);
            String token = Files.readString(root.resolve("minecraft-protocol/token"));
            String id = record(root).get("runtimeInstanceId").getAsString();
            var client = HttpClient.newHttpClient();
            URI uri = URI.create("ws://127.0.0.1:" + port + "/v0/events");
            var listener = new java.net.http.WebSocket.Listener() {};
            var failure =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () ->
                                    client.newWebSocketBuilder()
                                            .header("Authorization", "Bearer " + token)
                                            .header(
                                                    RuntimeEndpoint.INSTANCE_HEADER,
                                                    "stale-instance")
                                            .buildAsync(uri, listener)
                                            .get(5, TimeUnit.SECONDS));
            assertInstanceOf(java.net.http.WebSocketHandshakeException.class, failure.getCause());
            assertEquals(
                    409,
                    ((java.net.http.WebSocketHandshakeException) failure.getCause())
                            .getResponse()
                            .statusCode());
            var socket =
                    client.newWebSocketBuilder()
                            .header("Authorization", "Bearer " + token)
                            .header(RuntimeEndpoint.INSTANCE_HEADER, id)
                            .buildAsync(uri, listener)
                            .get(5, TimeUnit.SECONDS);
            socket.abort();
        }
    }
}
