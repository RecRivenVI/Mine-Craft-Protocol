package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.mojang.brigadier.suggestion.Suggestions;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandSuggestionRequests;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.CommandCompletionProviderAccessor;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

/** Native suggestion traffic shares the player's connection, not the human Chat future. */
public final class NativeCommandSuggestions {
    public interface ConnectionAccess {
        PlayerCommandSuggestionRequests<Suggestions> minecraftProtocolProbe$suggestionRequests();
    }

    private static final AtomicReference<WeakReference<ClientPacketListener>> tracked =
            new AtomicReference<>(new WeakReference<>(null));
    private static final AtomicBoolean runtimeClosed = new AtomicBoolean();

    private NativeCommandSuggestions() {}

    static CompletableFuture<Suggestions> request(
            Minecraft client, String input, Runnable authorization) {
        if (runtimeClosed.get())
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_CONTEXT_CHANGED", 409, "Completion Runtime is closed");
        if (!client.isSameThread())
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                    409,
                    "Native completion requires the client owner thread");
        authorization.run();
        var connection = client.getConnection();
        if (!(connection instanceof ConnectionAccess access)
                || !(connection.getSuggestionsProvider()
                        instanceof CommandCompletionProviderAccessor provider))
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE",
                    409,
                    "Native completion ownership hooks are unavailable");
        track(connection);
        requireHumanIdle(provider.minecraftProtocolProbe$pendingSuggestions());
        var registry = access.minecraftProtocolProbe$suggestionRequests();
        var ticket = registry.begin(now(), authorization);
        registry.dispatch(
                ticket,
                () -> requireHumanIdle(provider.minecraftProtocolProbe$pendingSuggestions()),
                id -> connection.send(new ServerboundCommandSuggestionPacket(id, input)));
        return ticket.future();
    }

    static void requireHumanIdle(CompletableFuture<?> pending) {
        if (pending != null && !pending.isDone())
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_COMPLETION_BUSY",
                    409,
                    "The player's native completion is pending; no Agent request was sent");
    }

    public static void tick(Minecraft client) {
        if (runtimeClosed.get()) return;
        ClientPacketListener connection = client == null ? null : client.getConnection();
        track(connection);
        if (connection instanceof ConnectionAccess access)
            access.minecraftProtocolProbe$suggestionRequests().tick(now());
    }

    public static void close(Minecraft ignored) {
        runtimeClosed.set(true);
        ClientPacketListener previous = tracked.getAndSet(new WeakReference<>(null)).get();
        closeConnection(previous);
    }

    private static void track(ClientPacketListener connection) {
        if (runtimeClosed.get()) {
            closeConnection(connection);
            return;
        }
        ClientPacketListener previous = tracked.get().get();
        if (previous != connection) {
            tracked.set(new WeakReference<>(connection));
            closeConnection(previous);
        }
    }

    private static void closeConnection(ClientPacketListener connection) {
        if (connection instanceof ConnectionAccess access)
            access.minecraftProtocolProbe$suggestionRequests().close();
    }

    private static long now() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}
