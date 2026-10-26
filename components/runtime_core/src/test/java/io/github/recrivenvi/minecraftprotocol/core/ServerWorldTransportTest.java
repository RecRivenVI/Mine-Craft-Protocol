package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerWorldTransportTest {
    @TempDir Path directory;
    private final HttpClient client = HttpClient.newHttpClient();
    private int port;

    @Test
    void realHttpUsesDebugAuthorityWithoutConsoleScopeOrPlayerLease() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty(
                "minecraft.protocol.scopes",
                "read,event,control,debug,debug.world.read,debug.write,debug.world");
        var host = new ServerWorldDebugTest.World();
        try (var service = service(host);
                var transport = new ProbeTransport(service, "test-token", 0, "26.3-neoforge")) {
            transport.startAsync();
            port = transport.boundPort().get(5, TimeUnit.SECONDS);
            assertEquals(403, call("GET", "/v0/server/console", null, null).statusCode());
            JsonObject before = read();
            assertEquals("DEBUG", before.get("dataCategory").getAsString());
            JsonObject mutation =
                    ServerWorldDebugTest.json(
                            "{\"operation\":\"world.block.set\",\"x\":8,\"y\":100,\"z\":8,\"blockId\":\"minecraft:stone\"}");
            mutation.add(
                    "worldFingerprint",
                    json(call("GET", "/v0/world/fingerprint?category=DEBUG", null, null))
                            .get("worldFingerprint"));
            mutation.add(
                    "expectedResourceVersion",
                    before.getAsJsonArray("resourceRevisionRefs").get(0));
            assertEquals(409, call("POST", "/v0/debug/mutations", mutation, null).statusCode());
            JsonObject arm =
                    ServerWorldDebugTest.json("{\"namespaces\":[\"world\"],\"ttlMs\":60000}");
            arm.add("worldFingerprint", mutation.get("worldFingerprint"));
            String id =
                    json(call("POST", "/v0/debug/arm", arm, null)).get("debugArmId").getAsString();
            assertEquals(409, call("POST", "/v0/debug/mutations", mutation, id).statusCode());
            assertEquals(0, host.mutations);
            mode("OPERATE");
            var receipt = json(call("POST", "/v0/debug/mutations", mutation, id));
            assertEquals(
                    "minecraft:stone",
                    receipt.getAsJsonObject("after").get("blockId").getAsString());
            assertEquals(1, host.mutations);
            var batch = ServerWorldDebugTest.json("{\"items\":[]}");
            mutation.add("expectedResourceVersion", receipt.get("afterResourceVersion"));
            mutation.addProperty("blockId", "minecraft:air");
            batch.getAsJsonArray("items").add(mutation);
            String operation =
                    json(call("POST", "/v0/debug/batches", batch, id))
                            .get("operationId")
                            .getAsString();
            JsonObject result = null;
            for (int i = 0; i < 100; i++) {
                result =
                        json(
                                call(
                                        "GET",
                                        "/v0/operations/" + operation + "?category=DEBUG",
                                        null,
                                        null));
                if (result.get("state").getAsString().equals("completed")) break;
                Thread.sleep(10);
            }
            assertEquals("completed", result.get("state").getAsString());
            assertFalse(result.has("worldEffectConfirmed"));
            assertEquals(2, host.mutations);
            assertEquals(404, call("GET", "/v0/operations/" + operation, null, null).statusCode());
            assertEquals(
                    409, call("POST", "/v0/control/acquire", new JsonObject(), null).statusCode());
            // Explicit cancellation/disarm stop queued DEBUG; input handback alone is not
            // revocation.
            mutation.add(
                    "expectedResourceVersion",
                    read().getAsJsonArray("resourceRevisionRefs").get(0));
            mutation.addProperty("blockId", "minecraft:stone");
            host.immediate = false;
            batch.getAsJsonArray("items").set(0, mutation.deepCopy());
            String queuedId =
                    json(call("POST", "/v0/debug/batches", batch, id))
                            .get("operationId")
                            .getAsString();
            long queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (host.queue.isEmpty() && System.nanoTime() < queueDeadline) Thread.sleep(5);
            assertFalse(host.queue.isEmpty());
            assertEquals(
                    "cancelled",
                    json(call("DELETE", "/v0/operations/" + queuedId, null, null))
                            .get("state")
                            .getAsString());
            host.queue.remove().run();
            assertEquals(2, host.mutations);
            var pending =
                    client.sendAsync(
                            request("POST", "/v0/debug/mutations", mutation, id),
                            HttpResponse.BodyHandlers.ofString());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (host.queue.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
            assertFalse(host.queue.isEmpty());
            assertEquals(200, call("POST", "/v0/debug/disarm", null, null).statusCode());
            host.queue.remove().run();
            assertEquals(409, pending.get(5, TimeUnit.SECONDS).statusCode());
            assertEquals(2, host.mutations);
            mode("READ");
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    @Test
    void defaultServerScopesCannotReadWorldOrMintDebugArm() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.clearProperty("minecraft.protocol.scopes");
        try (var service = service(new ServerWorldDebugTest.World());
                var transport = new ProbeTransport(service, "test-token", 0, "26.3-neoforge")) {
            transport.startAsync();
            port = transport.boundPort().get(5, TimeUnit.SECONDS);
            assertEquals(
                    403,
                    call("POST", "/v0/observe/deep", ServerWorldDebugTest.query(), null)
                            .statusCode());
            assertEquals(403, call("POST", "/v0/debug/arm", new JsonObject(), null).statusCode());
            assertEquals(
                    403,
                    call("GET", "/v0/world/fingerprint?category=DEBUG", null, null).statusCode());
        } finally {
            if (previous != null) System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    @Test
    void writeOnlyPrincipalIsRejectedBeforeChangingStateItCannotRead() throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty(
                "minecraft.protocol.scopes", "read,control,debug,debug.write,debug.world");
        var host = new ServerWorldDebugTest.World();
        try (var service = service(host);
                var transport = new ProbeTransport(service, "test-token", 0, "26.3-neoforge")) {
            transport.startAsync();
            port = transport.boundPort().get(5, TimeUnit.SECONDS);
            JsonObject arm =
                    ServerWorldDebugTest.json("{\"namespaces\":[\"world\"],\"ttlMs\":60000}");
            arm.add("worldFingerprint", service.worldFingerprint().join().get("worldFingerprint"));
            String id =
                    json(call("POST", "/v0/debug/arm", arm, null)).get("debugArmId").getAsString();
            mode("OPERATE");
            JsonObject mutation =
                    ServerWorldDebugTest.json(
                            "{\"operation\":\"world.block.set\",\"x\":8,\"y\":100,\"z\":8,\"blockId\":\"minecraft:stone\",\"expectedResourceVersion\":{}}");
            mutation.add("worldFingerprint", arm.get("worldFingerprint"));
            assertEquals(403, call("POST", "/v0/debug/mutations", mutation, id).statusCode());
            assertEquals(0, host.mutations);
            assertTrue(host.queue.isEmpty());
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    private ServerConsoleService service(ServerWorldDebugTest.World world) {
        return new ServerConsoleService(
                directory,
                "26.3-neoforge",
                new ServerConsole.Host() {
                    public void schedule(Runnable work) {
                        fail("No console work permitted");
                    }

                    public void dispatch(
                            String command,
                            java.util.function.Consumer<String> output,
                            java.util.function.Consumer<JsonObject> result) {
                        fail("No command permitted");
                    }
                },
                world);
    }

    private JsonObject read() throws Exception {
        var response = call("POST", "/v0/observe/deep", ServerWorldDebugTest.query(), null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response);
    }

    private void mode(String value) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("mode", value);
        body.add(
                "expectedModeVersion",
                json(call("GET", "/v0/control/mode", null, null)).get("modeVersion"));
        assertEquals(200, call("POST", "/v0/control/mode", body, null).statusCode());
    }

    private HttpRequest request(String method, String path, JsonObject body, String arm) {
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer test-token")
                        .header("Content-Type", "application/json");
        if (arm != null) builder.header("x-mcp-debug-arm", arm);
        return builder.method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private HttpResponse<String> call(String method, String path, JsonObject body, String arm)
            throws Exception {
        return client.send(request(method, path, body, arm), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> response) {
        return ServerWorldDebugTest.json(response.body());
    }
}
