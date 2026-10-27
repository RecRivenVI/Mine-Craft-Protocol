package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PeerDelegationsTest {
    @TempDir Path directory;
    private final HttpClient http = HttpClient.newHttpClient();
    private int port;
    private static final String PLAYER = "11111111-1111-4111-8111-111111111111";
    private static final String CONNECTION = "22222222-2222-4222-8222-222222222222";

    @Test
    void serverIssuerConnectionPermissionArmAndCancellationAreIndependentFromClientClaims()
            throws Exception {
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty(
                "minecraft.protocol.scopes",
                "read,event,control,server_peer,debug,debug.world.read,debug.write,debug.world");
        var world = new ServerWorldDebugTest.World();
        try (var service =
                        new ServerConsoleService(
                                directory,
                                "26.3-neoforge",
                                new ServerConsole.Host() {
                                    public void schedule(Runnable action) {
                                        fail("No console authority requested");
                                    }

                                    public void dispatch(
                                            String command,
                                            java.util.function.Consumer<String> feedback,
                                            java.util.function.Consumer<JsonObject> result) {
                                        fail("No command authority requested");
                                    }
                                },
                                world);
                var transport = new ProbeTransport(service, "server-token", 0, "26.3-neoforge")) {
            transport.startAsync();
            port = transport.boundPort().get(5, TimeUnit.SECONDS);
            JsonObject grantRequest =
                    ServerWorldDebugTest.json(
                            "{\"playerUuid\":\""
                                    + PLAYER
                                    + "\",\"connectionId\":\""
                                    + CONNECTION
                                    + "\",\"scopes\":[\"read\",\"debug\",\"debug.world.read\"],\"ttlMs\":60000}");
            JsonObject grant = call("POST", "/v0/server/peer/delegations", grantRequest);
            JsonObject params = new JsonObject();
            params.add("delegationId", grant.get("delegationId"));
            params.add("request", ServerWorldDebugTest.query());
            ServerPeerReceiver.NativeAction never =
                    (guard, work) -> {
                        fail("Formal observation must not invoke a legacy native callback");
                        return null;
                    };
            assertEquals(
                    "DEBUG",
                    transport
                            .receivePeer(
                                    PLAYER, CONNECTION, () -> true, "observe.deep", params, never)
                            .join()
                            .get("dataCategory")
                            .getAsString());
            assertEquals(
                    "PEER_AUTHORITY_REVOKED",
                    ServerWorldDebugTest.failure(
                                    () ->
                                            transport
                                                    .receivePeer(
                                                            PLAYER,
                                                            "other",
                                                            () -> true,
                                                            "observe.deep",
                                                            params,
                                                            never)
                                                    .join())
                            .code());
            assertEquals(
                    "PEER_AUTHORITY_REVOKED",
                    ServerWorldDebugTest.failure(
                                    () ->
                                            transport
                                                    .receivePeer(
                                                            PLAYER,
                                                            CONNECTION,
                                                            () -> false,
                                                            "observe.deep",
                                                            params,
                                                            never)
                                                    .join())
                            .code());
            var nativeOperator = new java.util.concurrent.atomic.AtomicBoolean(true);
            world.immediate = false;
            var queuedRead =
                    transport.receivePeer(
                            PLAYER, CONNECTION, nativeOperator::get, "observe.deep", params, never);
            assertFalse(world.queue.isEmpty());
            nativeOperator.set(false);
            world.queue.remove().run();
            assertEquals(
                    "PEER_AUTHORITY_REVOKED",
                    ServerWorldDebugTest.failure(queuedRead::join).code());
            world.immediate = true;
            params.add(
                    "request",
                    ServerWorldDebugTest.mutation(
                            new ServerWorldDebug(world),
                            world.observe(
                                            ServerWorldDebugTest.query(),
                                            ServerWorldDebugTest.context())
                                    .join()));
            assertEquals(
                    "DEBUG_SCOPE_DENIED",
                    ServerWorldDebugTest.failure(
                                    () ->
                                            transport
                                                    .receivePeer(
                                                            PLAYER,
                                                            CONNECTION,
                                                            () -> true,
                                                            "debug.mutate",
                                                            params,
                                                            never)
                                                    .join())
                            .code());
            JsonObject armRequest =
                    ServerWorldDebugTest.json("{\"namespaces\":[\"world\"],\"ttlMs\":60000}");
            armRequest.add("worldFingerprint", world.fingerprint().get("worldFingerprint"));
            JsonObject arm = call("POST", "/v0/debug/arm", armRequest);
            JsonObject mode = call("GET", "/v0/control/mode", null);
            JsonObject operate = new JsonObject();
            operate.addProperty("mode", "OPERATE");
            operate.add("expectedModeVersion", mode.get("modeVersion"));
            call("POST", "/v0/control/mode", operate);
            grantRequest.add("debugArmId", arm.get("debugArmId"));
            grantRequest.getAsJsonArray("scopes").add("debug.write");
            grantRequest.getAsJsonArray("scopes").add("debug.world");
            JsonObject write = call("POST", "/v0/server/peer/delegations", grantRequest);
            params.add("delegationId", write.get("delegationId"));
            JsonObject applied =
                    transport
                            .receivePeer(
                                    PLAYER, CONNECTION, () -> true, "debug.mutate", params, never)
                            .join();
            assertEquals("DEBUG_PRIVILEGED", applied.get("evidenceAuthority").getAsString());
            assertEquals(1, world.mutations);
            JsonObject mutation = params.getAsJsonObject("request");
            mutation.addProperty("blockId", "minecraft:glass");
            mutation.add("expectedResourceVersion", applied.get("afterResourceVersion"));
            world.immediate = false;
            CompletableFuture<JsonObject> pending =
                    transport.receivePeer(
                            PLAYER, CONNECTION, () -> true, "debug.mutate", params, never);
            assertFalse(world.queue.isEmpty());
            call(
                    "DELETE",
                    "/v0/server/peer/delegations/" + write.get("delegationId").getAsString(),
                    null);
            world.queue.remove().run();
            assertTrue(pending.isCompletedExceptionally());
            assertEquals(1, world.mutations);
            assertEquals(
                    "PEER_DELEGATION_REQUIRED",
                    ServerWorldDebugTest.failure(
                                    () ->
                                            transport
                                                    .receivePeer(
                                                            PLAYER,
                                                            CONNECTION,
                                                            () -> true,
                                                            "debug.mutate",
                                                            params,
                                                            never)
                                                    .join())
                            .code());
            JsonObject status = call("GET", "/v0/control/status", null);
            assertFalse(status.get("takeoverActive").getAsBoolean());
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }

    private JsonObject call(String method, String path, JsonObject body) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer server-token")
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return ServerWorldDebugTest.json(response.body());
    }
}
