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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;

final class NativePlayerCommands {
    private NativePlayerCommands() {}

    record Configuration(PlayerCommandGate.ConfigurationKind kind, String command) {}

    static Configuration configuration(Packet<?> packet) {
        if (packet
                instanceof net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket)
            return new Configuration(PlayerCommandGate.ConfigurationKind.CUSTOM_UI_CALLBACK, "");
        if (packet instanceof ServerboundSetTestBlockPacket)
            return new Configuration(PlayerCommandGate.ConfigurationKind.TEST_BLOCK, "");
        if (packet instanceof ServerboundTestInstanceBlockActionPacket)
            return new Configuration(PlayerCommandGate.ConfigurationKind.TEST_INSTANCE, "");
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
            com.mojang.brigadier.ParseResults<ClientSuggestionProvider> parsed) {
        return route(parsed, null);
    }

    static PlayerCommandPolicy.Parsed route(
            com.mojang.brigadier.ParseResults<ClientSuggestionProvider> parsed,
            com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> dispatcher) {
        return route(parsed, dispatcher, new int[3], 0);
    }

    private static PlayerCommandPolicy.Parsed route(
            com.mojang.brigadier.ParseResults<ClientSuggestionProvider> parsed,
            com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> dispatcher,
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
                                CommandContextBuilder<ClientSuggestionProvider>, Boolean>());
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
                CommandNode<ClientSuggestionProvider> root =
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
