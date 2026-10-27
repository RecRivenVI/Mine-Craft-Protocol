package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerMessage;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerNetworking;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class DedicatedPeerClient {
    private static final Gson GSON = new Gson();
    private static final Map<String, CompletableFuture<JsonObject>> PENDING =
            new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUTS =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "minecraft-protocol-peer-timeouts");
                        thread.setDaemon(true);
                        return thread;
                    });
    private static volatile ClientPacketListener connection;
    private static volatile boolean connected;
    private static volatile long lastHelloMillis;
    private static volatile long lastResponseMillis;
    private static volatile long lastServerTick;
    private static volatile String lastError = "";
    private static volatile String serverConnectionId = "";
    private static volatile String serverPlayerUuid = "";
    private static volatile String delegationId = "";

    private DedicatedPeerClient() {}

    public static void tick(Minecraft client) {
        ClientPacketListener current = client.getConnection();
        if (current != connection) {
            reset(current == null ? "disconnected" : "connection_changed");
            connection = current;
        }
        if (current == null) return;
        long now = System.currentTimeMillis();
        if (!connected && now - lastHelloMillis >= 2_000L) {
            lastHelloMillis = now;
            JsonObject hello = new JsonObject();
            hello.addProperty("kind", "hello");
            hello.addProperty("protocol", "peer-v0");
            hello.addProperty("clientNonce", UUID.randomUUID().toString());
            send(hello);
        }
    }

    public static synchronized JsonObject attach(JsonObject request) {
        if (request.keySet().equals(java.util.Set.of("action", "delegationId"))
                && request.get("action").isJsonPrimitive()
                && request.getAsJsonPrimitive("action").isString()
                && "detach".equals(request.get("action").getAsString())) {
            if (!request.get("delegationId").isJsonPrimitive()
                    || !request.getAsJsonPrimitive("delegationId").isString())
                throw new ProtocolState.ProtocolException(
                        "INVALID_PEER_DELEGATION", 400, "delegationId must be a string");
            if (!delegationId.equals(request.get("delegationId").getAsString()))
                throw new ProtocolState.ProtocolException(
                        "PEER_DELEGATION_MISMATCH", 409, "Current attachment changed");
            delegationId = "";
            for (var pending : PENDING.values()) pending.cancel(false);
            return status();
        }
        if (!request.keySet().equals(java.util.Set.of("delegationId", "connectionId"))
                || !request.get("delegationId").isJsonPrimitive()
                || !request.getAsJsonPrimitive("delegationId").isString()
                || !request.get("connectionId").isJsonPrimitive()
                || !request.getAsJsonPrimitive("connectionId").isString())
            throw new ProtocolState.ProtocolException(
                    "INVALID_PEER_DELEGATION",
                    400,
                    "Explicit delegationId and connectionId required");
        if (!connected || !request.get("connectionId").getAsString().equals(serverConnectionId))
            throw new ProtocolState.ProtocolException(
                    "PEER_CONNECTION_MISMATCH",
                    409,
                    "Delegation belongs to another live connection");
        try {
            delegationId =
                    java.util.UUID.fromString(request.get("delegationId").getAsString()).toString();
        } catch (IllegalArgumentException invalid) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_PEER_DELEGATION", 400, "delegationId must be a UUID");
        }
        return status();
    }

    public static CompletableFuture<JsonObject> request(
            String operation, JsonObject params, java.util.function.Consumer<String> submitted) {
        if (!connected || connection == null) {
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "SERVER_PEER_UNAVAILABLE",
                            409,
                            "Dedicated Server Peer is not connected"));
        }
        String authority = delegationId;
        if (!operation.equals("peer.status") && authority.isBlank())
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "PEER_DELEGATION_REQUIRED",
                            403,
                            "An independent server owner must issue a connection-bound delegation"));
        params = params == null ? new JsonObject() : params.deepCopy();
        if (!operation.equals("peer.status")) params.addProperty("delegationId", authority);
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        PENDING.put(requestId, future);
        var admittedConnection = connection;
        future.whenComplete(
                (value, error) -> {
                    PENDING.remove(requestId, future);
                    if (future.isCancelled()) cancelRequest(requestId, admittedConnection);
                });
        JsonObject request = new JsonObject();
        request.addProperty("kind", "request");
        request.addProperty("protocol", "peer-v0");
        request.addProperty("requestId", requestId);
        request.addProperty("operation", operation);
        request.add("params", params == null ? new JsonObject() : params.deepCopy());
        if (!send(request)) {
            PENDING.remove(requestId);
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "SERVER_PEER_UNAVAILABLE",
                            409,
                            "Unable to send Dedicated Server Peer request"));
        }
        submitted.accept(requestId);
        TIMEOUTS.schedule(
                () -> {
                    CompletableFuture<JsonObject> pending = PENDING.remove(requestId);
                    if (pending != null) {
                        cancelRequest(requestId, admittedConnection);
                        pending.completeExceptionally(
                                new ProtocolState.ProtocolException(
                                        "SERVER_PEER_TIMEOUT",
                                        408,
                                        "Dedicated Server Peer request timed out"));
                    }
                },
                5L,
                TimeUnit.SECONDS);
        return future;
    }

    public static void receive(PeerMessage payload) {
        try {
            JsonObject message = JsonParser.parseString(payload.json()).getAsJsonObject();
            String kind = message.get("kind").getAsString();
            lastResponseMillis = System.currentTimeMillis();
            if (kind.equals("hello_ack")) {
                String receivedConnection =
                        message.has("connectionId")
                                ? message.get("connectionId").getAsString()
                                : "";
                if (!receivedConnection.equals(serverConnectionId)) delegationId = "";
                serverConnectionId = receivedConnection;
                serverPlayerUuid =
                        message.has("playerUuid") ? message.get("playerUuid").getAsString() : "";
                connected = true;
                lastServerTick =
                        message.has("serverTick") ? message.get("serverTick").getAsLong() : 0L;
                lastError = "";
                return;
            }
            if (!kind.equals("response") || !message.has("requestId")) return;
            CompletableFuture<JsonObject> future =
                    PENDING.remove(message.get("requestId").getAsString());
            if (future == null) return;
            if (message.has("serverTick")) lastServerTick = message.get("serverTick").getAsLong();
            if (message.get("ok").getAsBoolean()) {
                future.complete(message.getAsJsonObject("data"));
            } else {
                String code =
                        message.has("error")
                                ? message.get("error").getAsString()
                                : "SERVER_PEER_ERROR";
                String detail =
                        message.has("message")
                                ? message.get("message").getAsString()
                                : "Peer request failed";
                future.completeExceptionally(
                        new ProtocolState.ProtocolException(
                                code,
                                message.has("httpStatus")
                                        ? message.get("httpStatus").getAsInt()
                                        : 409,
                                detail,
                                message.has("details") && message.get("details").isJsonObject()
                                        ? message.getAsJsonObject("details")
                                        : new JsonObject()));
            }
        } catch (Throwable throwable) {
            lastError =
                    throwable.getMessage() == null
                            ? throwable.getClass().getSimpleName()
                            : throwable.getMessage();
        }
    }

    public static JsonObject status() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "server.peer.status");
        json.addProperty("connected", connected && connection != null);
        json.addProperty("protocol", "peer-v0");
        json.addProperty("lastHelloMillis", lastHelloMillis);
        json.addProperty("lastResponseMillis", lastResponseMillis);
        json.addProperty("lastServerTick", lastServerTick);
        json.addProperty("pendingRequests", PENDING.size());
        json.addProperty("connectionId", serverConnectionId);
        json.addProperty("playerUuid", serverPlayerUuid);
        json.addProperty("delegationAttached", !delegationId.isBlank());
        json.addProperty("requiresServerDelegation", true);
        json.addProperty("maxResponseBytes", 30 * 1024);
        json.addProperty("lastError", lastError);
        return json;
    }

    public static void reset(String reason) {
        delegationId = "";
        serverConnectionId = "";
        serverPlayerUuid = "";
        connected = false;
        lastError = reason;
        for (CompletableFuture<JsonObject> future : PENDING.values()) {
            future.completeExceptionally(
                    new ProtocolState.ProtocolException(
                            "SERVER_PEER_DISCONNECTED", 409, "Dedicated Server Peer disconnected"));
        }
        PENDING.clear();
    }

    private static void cancelRequest(String requestId, ClientPacketListener expected) {
        if (expected == null || expected != connection || !connected) return;
        JsonObject cancel = new JsonObject();
        cancel.addProperty("kind", "cancel");
        cancel.addProperty("requestId", requestId);
        send(cancel);
    }

    private static boolean send(JsonObject message) {
        try {
            if (Minecraft.getInstance().getConnection() == null
                    || !PeerNetworking.CHANNEL.isRemotePresent(
                            Minecraft.getInstance().getConnection().getConnection())) {
                connected = false;
                return false;
            }
            PeerNetworking.CHANNEL.sendToServer(new PeerMessage(GSON.toJson(message)));
            return true;
        } catch (Throwable throwable) {
            lastError =
                    throwable.getMessage() == null
                            ? throwable.getClass().getSimpleName()
                            : throwable.getMessage();
            connected = false;
            return false;
        }
    }
}
