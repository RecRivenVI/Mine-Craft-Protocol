package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Loader-neutral lookup of a live server owner, not a credential or permission registry. */
public final class ServerPeerReceiver {
    @FunctionalInterface
    public interface NativeAction {
        CompletableFuture<JsonObject> apply(
                Runnable authorization,
                io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession.OperateWork work);
    }

    private static final Map<Object, ProbeTransport> OWNERS = new WeakHashMap<>();

    private ServerPeerReceiver() {}

    public static synchronized void attach(Object server, ProbeTransport runtime) {
        if (OWNERS.putIfAbsent(server, runtime) != null)
            throw new IllegalStateException("Server owner Runtime already attached");
    }

    public static synchronized void detach(Object server, ProbeTransport runtime) {
        OWNERS.remove(server, runtime);
    }

    public static CompletableFuture<JsonObject> request(
            Object server,
            String playerUuid,
            String connectionId,
            BooleanSupplier operator,
            String operation,
            JsonObject params,
            NativeAction nativeRead) {
        ProbeTransport owner;
        synchronized (ServerPeerReceiver.class) {
            owner = OWNERS.get(server);
        }
        if (owner == null)
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "SERVER_PEER_OWNER_UNAVAILABLE",
                            409,
                            "No independent server owner Runtime is attached"));
        return owner.receivePeer(playerUuid, connectionId, operator, operation, params, nativeRead);
    }

    public static void requirePublication(
            Object server,
            String player,
            String connection,
            BooleanSupplier operator,
            JsonObject params) {
        ProbeTransport owner;
        synchronized (ServerPeerReceiver.class) {
            owner = OWNERS.get(server);
        }
        if (owner == null)
            throw new ProtocolState.ProtocolException(
                    "SERVER_PEER_OWNER_UNAVAILABLE", 409, "Server owner is no longer attached");
        owner.requirePeerPublication(player, connection, operator, params);
    }
}
