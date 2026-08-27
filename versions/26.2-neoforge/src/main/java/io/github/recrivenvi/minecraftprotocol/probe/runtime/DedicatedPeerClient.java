package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerPayload;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class DedicatedPeerClient {
    private static final Gson GSON = new Gson();
    private static final Map<String, CompletableFuture<JsonObject>> PENDING = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(runnable -> {
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

    private DedicatedPeerClient() {
    }

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

    public static CompletableFuture<JsonObject> request(String operation, JsonObject params) {
        if (!connected || connection == null) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "SERVER_PEER_UNAVAILABLE", 409, "Dedicated Server Peer is not connected"));
        }
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        PENDING.put(requestId, future);
        JsonObject request = new JsonObject();
        request.addProperty("kind", "request");
        request.addProperty("protocol", "peer-v0");
        request.addProperty("requestId", requestId);
        request.addProperty("operation", operation);
        request.add("params", params == null ? new JsonObject() : params.deepCopy());
        if (!send(request)) {
            PENDING.remove(requestId);
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "SERVER_PEER_UNAVAILABLE", 409, "Unable to send Dedicated Server Peer request"));
        }
        TIMEOUTS.schedule(() -> {
            CompletableFuture<JsonObject> pending = PENDING.remove(requestId);
            if (pending != null) pending.completeExceptionally(new ProtocolState.ProtocolException(
                    "SERVER_PEER_TIMEOUT", 408, "Dedicated Server Peer request timed out"));
        }, 5L, TimeUnit.SECONDS);
        return future;
    }

    public static void receive(PeerPayload payload) {
        try {
            JsonObject message = JsonParser.parseString(payload.json()).getAsJsonObject();
            String kind = message.get("kind").getAsString();
            lastResponseMillis = System.currentTimeMillis();
            if (kind.equals("hello_ack")) {
                connected = true;
                lastServerTick = message.has("serverTick") ? message.get("serverTick").getAsLong() : 0L;
                lastError = "";
                return;
            }
            if (!kind.equals("response") || !message.has("requestId")) return;
            CompletableFuture<JsonObject> future = PENDING.remove(message.get("requestId").getAsString());
            if (future == null) return;
            if (message.has("serverTick")) lastServerTick = message.get("serverTick").getAsLong();
            if (message.get("ok").getAsBoolean()) {
                future.complete(message.getAsJsonObject("data"));
            } else {
                String code = message.has("error") ? message.get("error").getAsString() : "SERVER_PEER_ERROR";
                String detail = message.has("message") ? message.get("message").getAsString() : "Peer request failed";
                future.completeExceptionally(new ProtocolState.ProtocolException(code, 409, detail));
            }
        } catch (Throwable throwable) {
            lastError = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
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
        json.addProperty("lastError", lastError);
        return json;
    }

    public static void reset(String reason) {
        connected = false;
        lastError = reason;
        for (CompletableFuture<JsonObject> future : PENDING.values()) {
            future.completeExceptionally(new ProtocolState.ProtocolException(
                    "SERVER_PEER_DISCONNECTED", 409, "Dedicated Server Peer disconnected"));
        }
        PENDING.clear();
    }

    private static boolean send(JsonObject message) {
        try {
            ClientPacketDistributor.sendToServer(new PeerPayload(GSON.toJson(message)));
            return true;
        } catch (Throwable throwable) {
            lastError = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            connected = false;
            return false;
        }
    }
}
