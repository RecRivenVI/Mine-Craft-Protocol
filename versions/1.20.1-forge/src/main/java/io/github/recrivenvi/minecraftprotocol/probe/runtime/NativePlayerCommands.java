package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandPolicy;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;

final class NativePlayerCommands {
    private NativePlayerCommands() {}

    record Configuration(PlayerCommandGate.ConfigurationKind kind, String command) {}

    static Runnable contextGuard(Minecraft client, Runnable authorization) {
        authorization.run();
        var player = client.player;
        var level = client.level;
        var connection = client.getConnection();
        if (player == null
                || level == null
                || connection == null
                || !connection.getConnection().isConnected())
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                    409,
                    "A connected current player is required");
        var dispatcher = connection.getCommands();
        return () -> {
            authorization.run();
            if (client.player != player
                    || client.level != level
                    || client.getConnection() != connection
                    || !connection.getConnection().isConnected()
                    || connection.getCommands() != dispatcher)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_CONTEXT_CHANGED",
                        409,
                        "Player, world, connection or native command tree changed");
        };
    }

    static java.util.concurrent.CompletableFuture<com.google.gson.JsonObject> discover(
            Minecraft client,
            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery.Query query,
            Runnable authorization) {
        var guard = contextGuard(client, authorization);
        var connection = client.getConnection();
        var dispatcher = connection.getCommands();
        var serverRequired = new java.util.concurrent.atomic.AtomicBoolean();
        var source =
                localSuggestionSource(
                        connection, client, connection.getSuggestionsProvider(), serverRequired);
        return discover(dispatcher, source, query, guard, client::execute, serverRequired::get);
    }

    static ClientSuggestionProvider localSuggestionSource(
            net.minecraft.client.multiplayer.ClientPacketListener connection,
            Minecraft client,
            ClientSuggestionProvider original,
            java.util.concurrent.atomic.AtomicBoolean serverRequired) {
        return new LocalSuggestionSource(connection, client, original, serverRequired);
    }

    // Explicit constructors avoid duplicated parameter names in Forge's mapped superclass metadata.
    private static final class LocalSuggestionSource extends ClientSuggestionProvider {
        private final ClientSuggestionProvider original;
        private final java.util.concurrent.atomic.AtomicBoolean serverRequired;

        LocalSuggestionSource(
                net.minecraft.client.multiplayer.ClientPacketListener connection,
                Minecraft client,
                ClientSuggestionProvider original,
                java.util.concurrent.atomic.AtomicBoolean serverRequired) {
            super(connection, client);
            this.original = original;
            this.serverRequired = serverRequired;
        }

        @Override
        public java.util.Collection<String> getCustomTabSugggestions() {
            return original.getCustomTabSugggestions();
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
                customSuggestion(com.mojang.brigadier.context.CommandContext<?> context) {
            serverRequired.set(true);
            return com.mojang.brigadier.suggestion.Suggestions.empty();
        }
    }

    static java.util.concurrent.CompletableFuture<com.google.gson.JsonObject> complete(
            Minecraft client,
            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery.Query query,
            Runnable authorization) {
        Runnable guard = contextGuard(client, authorization);
        var connection = client.getConnection();
        var dispatcher = connection.getCommands();
        var source = connection.getSuggestionsProvider();
        var route = route(dispatcher.parse(query.commandInput(), source), dispatcher);
        var effect = route.complete() ? PlayerCommandPolicy.classify(route) : null;
        var roots =
                dispatcher.getRoot().getChildren().stream()
                        .filter(node -> node.canUse(source))
                        .map(CommandNode::getName)
                        .sorted()
                        .toList();
        String prefix = query.commandInput().substring(0, query.commandCursor());
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.map(
                NativeCommandSuggestions.request(client, prefix, guard),
                values -> {
                    guard.run();
                    if (values.getRange().getStart() < 0
                            || values.getRange().getEnd() > prefix.length())
                        throw new ProtocolState.ProtocolException(
                                "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                                409,
                                "Server suggestion range is outside the queried prefix");
                    var suggestions =
                            values.getList().stream()
                                    .limit(query.maxResults())
                                    .map(
                                            value ->
                                                    new io.github.recrivenvi.minecraftprotocol.core
                                                            .PlayerCommandDiscovery.Suggestion(
                                                            value.getRange().getStart()
                                                                    + query.inputOffset(),
                                                            value.getRange().getEnd()
                                                                    + query.inputOffset(),
                                                            value.getText(),
                                                            value.getTooltip() == null
                                                                    ? null
                                                                    : value.getTooltip()
                                                                            .getString()))
                                    .toList();
                    var response =
                            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery
                                    .result(
                                            query,
                                            roots,
                                            roots.size(),
                                            suggestions,
                                            values.getList().size(),
                                            route.complete(),
                                            effect);
                    response.addProperty("source", "current_player_server_command_suggestions");
                    response.addProperty(
                            "availability", "client_received_tree_and_native_server_suggestions");
                    response.addProperty("completionSource", "SERVER");
                    response.addProperty("completionScope", "server_current_player");
                    response.addProperty("serverCompletionSupported", true);
                    response.addProperty("serverCompletionRequested", true);
                    response.addProperty("serverCompletionRequired", false);
                    response.addProperty(
                            "completionComplete", values.getList().size() <= query.maxResults());
                    response.addProperty("humanFutureModified", false);
                    response.addProperty("humanRequestsHavePriority", true);
                    response.addProperty("serverBudgetShared", true);
                    response.addProperty("maxAgentCompletionsInFlight", 1);
                    response.addProperty(
                            "completionMinIntervalMillis",
                            io.github.recrivenvi.minecraftprotocol.core
                                    .PlayerCommandSuggestionRequests.MIN_INTERVAL_MILLIS);
                    response.addProperty(
                            "completionTimeoutMillis",
                            io.github.recrivenvi.minecraftprotocol.core
                                    .PlayerCommandSuggestionRequests.TIMEOUT_MILLIS);
                    response.addProperty("nativeServerMayLimitSuggestions", true);
                    return response;
                });
    }

    static java.util.concurrent.CompletableFuture<com.google.gson.JsonObject> discover(
            com.mojang.brigadier.CommandDispatcher<SharedSuggestionProvider> dispatcher,
            SharedSuggestionProvider source,
            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery.Query query,
            Runnable guard,
            java.util.function.Consumer<Runnable> ownerThread) {
        return discover(dispatcher, source, query, guard, ownerThread, () -> false);
    }

    static java.util.concurrent.CompletableFuture<com.google.gson.JsonObject> discover(
            com.mojang.brigadier.CommandDispatcher<SharedSuggestionProvider> dispatcher,
            SharedSuggestionProvider source,
            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery.Query query,
            Runnable guard,
            java.util.function.Consumer<Runnable> ownerThread,
            java.util.function.BooleanSupplier serverRequired) {
        guard.run();
        var parsed = dispatcher.parse(query.commandInput(), source);
        var route = route(parsed, dispatcher);
        var effect = route.complete() ? PlayerCommandPolicy.classify(route) : null;
        var roots =
                dispatcher.getRoot().getChildren().stream()
                        .filter(node -> node.canUse(source))
                        .map(CommandNode::getName)
                        .sorted()
                        .toList();
        var result = new java.util.concurrent.CompletableFuture<com.google.gson.JsonObject>();
        var pending = dispatcher.getCompletionSuggestions(parsed, query.commandCursor());
        result.whenComplete(
                (value, error) -> {
                    if (result.isCancelled()) pending.cancel(false);
                });
        pending.whenComplete(
                (values, error) ->
                        ownerThread.accept(
                                () -> {
                                    if (result.isDone()) return;
                                    try {
                                        guard.run();
                                        if (error != null) {
                                            result.completeExceptionally(error);
                                            return;
                                        }
                                        var suggestions =
                                                values.getList().stream()
                                                        .limit(query.maxResults())
                                                        .map(
                                                                value ->
                                                                        new io.github.recrivenvi
                                                                                .minecraftprotocol
                                                                                .core
                                                                                .PlayerCommandDiscovery
                                                                                .Suggestion(
                                                                                value.getRange()
                                                                                                .getStart()
                                                                                        + query
                                                                                                .inputOffset(),
                                                                                value.getRange()
                                                                                                .getEnd()
                                                                                        + query
                                                                                                .inputOffset(),
                                                                                value.getText(),
                                                                                value.getTooltip()
                                                                                                == null
                                                                                        ? null
                                                                                        : value.getTooltip()
                                                                                                .getString()))
                                                        .toList();
                                        var response =
                                                io.github.recrivenvi.minecraftprotocol.core
                                                        .PlayerCommandDiscovery.result(
                                                        query,
                                                        roots,
                                                        roots.size(),
                                                        suggestions,
                                                        values.getList().size(),
                                                        route.complete(),
                                                        effect);
                                        response.addProperty(
                                                "completionScope", "client_local_only");
                                        response.addProperty("completionSource", "CLIENT_LOCAL");
                                        response.addProperty("serverCompletionSupported", true);
                                        response.addProperty("serverCompletionRequested", false);
                                        response.addProperty(
                                                "serverCompletionRequired",
                                                serverRequired.getAsBoolean());
                                        response.addProperty(
                                                "completionComplete",
                                                !serverRequired.getAsBoolean());
                                        if (serverRequired.getAsBoolean())
                                            response.addProperty(
                                                    "completionUnavailableReason",
                                                    "server_completion_requires_independent_request_ownership");
                                        result.complete(response);
                                    } catch (Throwable failure) {
                                        result.completeExceptionally(failure);
                                    }
                                }));
        return result;
    }

    static Configuration configuration(Packet<?> packet) {
        if (packet instanceof ServerboundSetCommandBlockPacket block)
            return new Configuration(
                    PlayerCommandGate.ConfigurationKind.COMMAND_BLOCK, block.getCommand());
        if (packet instanceof ServerboundSetCommandMinecartPacket minecart)
            return new Configuration(
                    PlayerCommandGate.ConfigurationKind.COMMAND_MINECART, minecart.getCommand());
        if (packet instanceof ServerboundSetJigsawBlockPacket jigsaw)
            return new Configuration(
                    PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK, jigsaw.getFinalState());
        if (packet instanceof ServerboundJigsawGeneratePacket)
            return new Configuration(PlayerCommandGate.ConfigurationKind.JIGSAW_GENERATE, "");
        if (packet instanceof ServerboundSetStructureBlockPacket structure)
            return new Configuration(
                    switch (structure.getUpdateType()) {
                        case UPDATE_DATA -> PlayerCommandGate.ConfigurationKind.STRUCTURE_UPDATE;
                        case SCAN_AREA -> PlayerCommandGate.ConfigurationKind.STRUCTURE_SCAN;
                        case SAVE_AREA -> PlayerCommandGate.ConfigurationKind.STRUCTURE_SAVE;
                        case LOAD_AREA -> PlayerCommandGate.ConfigurationKind.STRUCTURE_LOAD;
                    },
                    "");
        return null;
    }

    static PlayerCommandPolicy.Parsed parseConfiguration(
            Minecraft client, PlayerCommandGate.ConfigurationKind kind, String text) {
        String command =
                kind == PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK
                        ? "setblock ~ ~ ~ " + text
                        : PlayerCommandPolicy.normalize(text);
        PlayerCommandPolicy.validateText(command);
        return parse(client, command);
    }

    static PlayerCommandPolicy.Parsed parse(Minecraft client, String command) {
        var connection = client.getConnection();
        if (connection == null || client.player == null)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_UNAVAILABLE", 409, "A connected current player is required");
        var dispatcher = connection.getCommands();
        return route(dispatcher.parse(command, connection.getSuggestionsProvider()), dispatcher);
    }

    static PlayerCommandPolicy.Parsed route(
            com.mojang.brigadier.ParseResults<SharedSuggestionProvider> parsed) {
        return route(parsed, null);
    }

    static PlayerCommandPolicy.Parsed route(
            com.mojang.brigadier.ParseResults<SharedSuggestionProvider> parsed,
            com.mojang.brigadier.CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        return route(parsed, dispatcher, new int[3], 0);
    }

    private static PlayerCommandPolicy.Parsed route(
            com.mojang.brigadier.ParseResults<SharedSuggestionProvider> parsed,
            com.mojang.brigadier.CommandDispatcher<SharedSuggestionProvider> dispatcher,
            int[] budget,
            int depth) {
        if (depth > 4 || ++budget[0] > 17)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BUDGET_EXCEEDED",
                    413,
                    "Embedded native program budget exceeded");
        var payloads = new NativeCommandPayloads();
        var frames = new ArrayList<PlayerCommandPolicy.Frame>();
        var seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<
                                CommandContextBuilder<SharedSuggestionProvider>, Boolean>());
        var context = parsed.getContext();
        boolean executable = false;
        int nodes = 0, contexts = 0;
        while (context != null) {
            if (!seen.add(context) || ++contexts > 16)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                        413,
                        "Native command context budget exceeded");
            var route = new ArrayList<PlayerCommandPolicy.Node>();
            for (var node : context.getNodes()) {
                if (++nodes > 128)
                    throw new ProtocolState.ProtocolException(
                            "PLAYER_COMMAND_BUDGET_EXCEEDED",
                            413,
                            "Native command node budget exceeded");
                route.add(
                        new PlayerCommandPolicy.Node(
                                node.getNode().getName(),
                                node.getNode() instanceof LiteralCommandNode<?>));
            }
            if (!route.isEmpty()) {
                // Aliases redirect into a command's argument subtree, not a new command root.
                CommandNode<SharedSuggestionProvider> root =
                        context.getRootNode() instanceof RootCommandNode<?>
                                ? context.getNodes().get(0).getNode()
                                : context.getRootNode();
                int redirects = 0;
                while (root.getRedirect() != null
                        && !(root.getRedirect() instanceof RootCommandNode<?>)) {
                    if (++redirects > 16)
                        throw new ProtocolState.ProtocolException(
                                "PLAYER_COMMAND_BUDGET_EXCEEDED",
                                413,
                                "Native command redirect budget exceeded");
                    root = root.getRedirect();
                }
                boolean freshRoot = context.getRootNode() instanceof RootCommandNode<?>;
                if (freshRoot)
                    route.set(
                            0,
                            new PlayerCommandPolicy.Node(root.getName(), route.get(0).literal()));
                if (!freshRoot
                        && !frames.isEmpty()
                        && frames.get(frames.size() - 1).root().equals(root.getName())) {
                    // An alias continuation must retain root+arguments for query/set arity.
                    var previous = frames.get(frames.size() - 1);
                    var combined = new ArrayList<>(previous.nodes());
                    combined.addAll(route);
                    frames.set(
                            frames.size() - 1,
                            new PlayerCommandPolicy.Frame(root.getName(), combined));
                } else frames.add(new PlayerCommandPolicy.Frame(root.getName(), route));
                payloads.capture(context, root.getName());
            }
            executable = context.getCommand() != null;
            context = context.getChild();
        }
        // Match Brigadier: a native argument reader can finish just past the input boundary.
        var effects = new ArrayList<PlayerCommandPolicy.Effect>();
        if (payloads.unresolved()) effects.add(PlayerCommandPolicy.Effect.UNRESOLVED);
        budget[1] += payloads.output().nodes();
        if (budget[1] > 1024)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BUDGET_EXCEEDED",
                    413,
                    "Aggregate native payload budget exceeded");
        for (var program : payloads.output().programs()) {
            if (dispatcher == null) {
                effects.add(PlayerCommandPolicy.Effect.UNRESOLVED);
                continue;
            }
            String command =
                    program.kind()
                                    == io.github.recrivenvi.minecraftprotocol.core
                                            .PlayerCommandPayloads.Kind.COMMAND
                            ? PlayerCommandPolicy.normalize(program.text())
                            : "setblock ~ ~ ~ " + program.text();
            budget[2] += command.length();
            if (budget[2] > 32768)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                        413,
                        "Aggregate native program text budget exceeded");
            PlayerCommandPolicy.validateText(command);
            effects.add(
                    PlayerCommandPolicy.classify(
                            route(
                                    dispatcher.parse(command, parsed.getContext().getSource()),
                                    dispatcher,
                                    budget,
                                    depth + 1)));
        }
        return new PlayerCommandPolicy.Parsed(
                executable
                        && (!parsed.getReader().canRead()
                                || parsed.getReader().getRemaining().isBlank()),
                frames,
                effects);
    }
}
