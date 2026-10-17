package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandPolicy;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.junit.jupiter.api.Test;

class NativePlayerCommandsTest {
    @Test
    void opaqueNativeClickActionsAreRefusedWithoutInterpretingIdOrPayload() {
        for (String id : java.util.List.of("minecraft:validation", "unknown:validation")) {
            for (var payload :
                    java.util.List.<java.util.Optional<net.minecraft.nbt.Tag>>of(
                            java.util.Optional.empty(),
                            java.util.Optional.of(new net.minecraft.nbt.CompoundTag()))) {
                var packet =
                        new net.minecraft.network.protocol.common
                                .ServerboundCustomClickActionPacket(
                                net.minecraft.resources.Identifier.parse(id), payload);
                var configuration = NativePlayerCommands.configuration(packet);
                assertEquals(
                        PlayerCommandGate.ConfigurationKind.CUSTOM_UI_CALLBACK,
                        configuration.kind());
                assertEquals("", configuration.command());
            }
        }
    }

    @Test
    void nativeTestPacketsAreAllClassifiedAsManagementWithoutReadingTheirPayload() {
        var block =
                new net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket(
                        net.minecraft.core.BlockPos.ZERO,
                        net.minecraft.world.level.block.state.properties.TestBlockMode.LOG,
                        "not_a_player_command");
        assertEquals("TEST_BLOCK", NativePlayerCommands.configuration(block).kind().name());
        for (var action :
                net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket.Action
                        .values()) {
            var packet =
                    new net.minecraft.network.protocol.game
                            .ServerboundTestInstanceBlockActionPacket(
                            net.minecraft.core.BlockPos.ZERO,
                            action,
                            (net.minecraft.world.level.block.entity.TestInstanceBlockEntity.Data)
                                    null);
            assertEquals(
                    "TEST_INSTANCE",
                    NativePlayerCommands.configuration(packet).kind().name(),
                    action.name());
            assertEquals("", NativePlayerCommands.configuration(packet).command());
        }
    }

    @Test
    void debugTextPreflightRejectsTestRespondersButKeepsOrdinaryTextContexts() {
        assertEquals(
                "UI_DEBUG_UNSUPPORTED",
                assertThrows(
                                io.github.recrivenvi.minecraftprotocol.core.ProtocolState
                                        .ProtocolException.class,
                                () ->
                                        UiDebugEngine.requireTextMutationContext(
                                                net.minecraft.client.gui.screens.inventory
                                                        .TestInstanceBlockEditScreen.class))
                        .code());
        assertDoesNotThrow(
                () ->
                        UiDebugEngine.requireTextMutationContext(
                                net.minecraft.client.gui.screens.inventory.TestBlockEditScreen
                                        .class));
        assertDoesNotThrow(
                () ->
                        UiDebugEngine.requireTextMutationContext(
                                net.minecraft.client.gui.screens.TitleScreen.class));
    }

    @Test
    void actualStructurePacketsKeepAllFourNativeActionsDistinct() {
        for (var action :
                net.minecraft.world.level.block.entity.StructureBlockEntity.UpdateType.values()) {
            var packet =
                    new net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket(
                            new net.minecraft.core.BlockPos(2, 80, 0),
                            action,
                            net.minecraft.world.level.block.state.properties.StructureMode.SAVE,
                            "minecraft:validation_structure",
                            net.minecraft.core.BlockPos.ZERO,
                            new net.minecraft.core.Vec3i(1, 1, 1),
                            net.minecraft.world.level.block.Mirror.NONE,
                            net.minecraft.world.level.block.Rotation.NONE,
                            "metadata_not_a_command",
                            true,
                            false,
                            false,
                            true,
                            1.0F,
                            0L);
            var configuration = NativePlayerCommands.configuration(packet);
            assertEquals(
                    switch (action) {
                        case UPDATE_DATA -> "STRUCTURE_UPDATE";
                        case SCAN_AREA -> "STRUCTURE_SCAN";
                        case SAVE_AREA -> "STRUCTURE_SAVE";
                        case LOAD_AREA -> "STRUCTURE_LOAD";
                    },
                    configuration.kind().name());
            assertEquals("", configuration.command());
        }
    }

    @Test
    void actualJigsawPacketsDistinguishStoredBlockStateFromOpaqueGeneration() {
        var pos = new net.minecraft.core.BlockPos(2, 80, 0);
        var id = net.minecraft.resources.Identifier.parse("minecraft:empty");
        var configuration =
                NativePlayerCommands.configuration(
                        new net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket(
                                pos,
                                id,
                                id,
                                id,
                                "minecraft:air",
                                net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType
                                        .ROLLABLE,
                                0,
                                0));
        assertEquals(
                io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate.ConfigurationKind
                        .JIGSAW_BLOCK,
                configuration.kind());
        assertEquals("minecraft:air", configuration.command());
        var generation =
                NativePlayerCommands.configuration(
                        new net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket(
                                pos, 1, true));
        assertEquals(
                io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate.ConfigurationKind
                        .JIGSAW_GENERATE,
                generation.kind());
        assertEquals("", generation.command());
    }

    @Test
    void recursiveNativeCommandPayloadsHaveAnAggregateBudget() {
        var dispatcher = new CommandDispatcher<ClientSuggestionProvider>();
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("help")
                        .executes(context -> 1));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("summon")
                        .then(
                                RequiredArgumentBuilder
                                        .<ClientSuggestionProvider, net.minecraft.nbt.CompoundTag>
                                                argument(
                                                        "nbt",
                                                        net.minecraft.commands.arguments
                                                                .CompoundTagArgument.compoundTag())
                                        .executes(context -> 1)));
        String command = "help";
        for (int i = 0; i < 5; i++) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("Command", command);
            command = "summon " + tag;
        }
        String excessive = command;
        var error =
                assertThrows(
                        io.github.recrivenvi.minecraftprotocol.core.ProtocolState.ProtocolException
                                .class,
                        () ->
                                NativePlayerCommands.route(
                                        dispatcher.parse(excessive, null), dispatcher));
        assertEquals("PLAYER_COMMAND_BUDGET_EXCEEDED", error.code());
    }

    @Test
    void nativeDecodedPayloadsUseTheSameDispatcherForNestedPrograms() {
        var dispatcher = new CommandDispatcher<ClientSuggestionProvider>();
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("reload")
                        .executes(context -> 1));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("help")
                        .executes(context -> 1));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("summon")
                        .then(
                                RequiredArgumentBuilder
                                        .<ClientSuggestionProvider, net.minecraft.nbt.CompoundTag>
                                                argument(
                                                        "nbt",
                                                        net.minecraft.commands.arguments
                                                                .CompoundTagArgument.compoundTag())
                                        .executes(context -> 1)));
        for (String command :
                java.util.List.of(
                        "summon {Command:'reload'}", "summon {Passengers:[{Command:'reload'}]}")) {
            var parsed = NativePlayerCommands.route(dispatcher.parse(command, null), dispatcher);
            assertTrue(parsed.complete(), command);
            assertEquals(
                    PlayerCommandPolicy.Effect.CONTROL_PLANE,
                    PlayerCommandPolicy.classify(parsed),
                    command);
        }
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(
                                dispatcher.parse(
                                        "summon {Command:'help',CustomName:'reload'}", null),
                                dispatcher)));
        assertEquals(
                PlayerCommandPolicy.Effect.UNRESOLVED,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(
                                dispatcher.parse("summon {Command:'unknown_internal'}", null),
                                dispatcher)));
    }

    @Test
    void nativeLiteralTagAndPathCannotHideACommandInScalarOrNestedFields() {
        var dispatcher = new CommandDispatcher<ClientSuggestionProvider>();
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("reload")
                        .executes(context -> 1));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("data")
                        .then(
                                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("modify")
                                        .then(
                                                RequiredArgumentBuilder
                                                        .<ClientSuggestionProvider,
                                                                net.minecraft.commands.arguments
                                                                        .NbtPathArgument.NbtPath>
                                                                argument(
                                                                        "targetPath",
                                                                        net.minecraft.commands
                                                                                .arguments
                                                                                .NbtPathArgument
                                                                                .nbtPath())
                                                        .then(
                                                                LiteralArgumentBuilder
                                                                        .<ClientSuggestionProvider>
                                                                                literal("value")
                                                                        .then(
                                                                                RequiredArgumentBuilder
                                                                                        .<ClientSuggestionProvider,
                                                                                                net
                                                                                                        .minecraft
                                                                                                        .nbt
                                                                                                        .Tag>
                                                                                                argument(
                                                                                                        "value",
                                                                                                        net
                                                                                                                .minecraft
                                                                                                                .commands
                                                                                                                .arguments
                                                                                                                .NbtTagArgument
                                                                                                                .nbtTag())
                                                                                        .executes(
                                                                                                context ->
                                                                                                        1))))));
        for (String path : java.util.List.of("Command", "\"Command\"", "Passengers[0].Command"))
            assertEquals(
                    PlayerCommandPolicy.Effect.CONTROL_PLANE,
                    PlayerCommandPolicy.classify(
                            NativePlayerCommands.route(
                                    dispatcher.parse(
                                            "data modify " + path + " value 'reload'", null),
                                    dispatcher)),
                    path);
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(
                                dispatcher.parse(
                                        "data modify \"foo.Command\" value 'reload'", null),
                                dispatcher)));
    }

    @Test
    void actualNativeConfigurationPacketsAreTypedWithoutResolvingWorldObjects() {
        var block =
                NativePlayerCommands.configuration(
                        new net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket(
                                new net.minecraft.core.BlockPos(2, 80, 0),
                                "/time query daytime",
                                net.minecraft.world.level.block.entity.CommandBlockEntity.Mode
                                        .REDSTONE,
                                true,
                                false,
                                false));
        assertEquals(
                io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate.ConfigurationKind
                        .COMMAND_BLOCK,
                block.kind());
        assertEquals("/time query daytime", block.command());
        var minecart =
                NativePlayerCommands.configuration(
                        new net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket(
                                7, "", true));
        assertEquals(
                io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate.ConfigurationKind
                        .COMMAND_MINECART,
                minecart.kind());
        assertEquals("", minecart.command());
        assertNull(
                NativePlayerCommands.configuration(
                        new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(
                                0)));
    }

    @Test
    void nativeEndOfInputAgreesWithBrigadierWhenAnArgumentAdvancesPastTheEnd() throws Exception {
        var dispatcher = new CommandDispatcher<ClientSuggestionProvider>();
        com.mojang.brigadier.arguments.ArgumentType<String> nativeArgument =
                reader -> {
                    reader.setCursor(reader.getTotalLength() + 1);
                    return "native parsed value";
                };
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("title")
                        .then(
                                RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument(
                                                "text", nativeArgument)
                                        .executes(context -> 1)));
        var parsed = dispatcher.parse("title content", null);
        assertFalse(parsed.getReader().canRead());
        assertEquals(1, dispatcher.execute(parsed));
        var route = NativePlayerCommands.route(parsed);
        assertTrue(route.complete());
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION, PlayerCommandPolicy.classify(route));
    }

    @Test
    void aliasArgumentsCannotTurnASetterIntoAQuery() {
        var dispatcher = new CommandDispatcher<ClientSuggestionProvider>();
        var difficulty =
                dispatcher.register(
                        LiteralArgumentBuilder.<ClientSuggestionProvider>literal("difficulty")
                                .executes(context -> 1)
                                .then(
                                        RequiredArgumentBuilder
                                                .<ClientSuggestionProvider, String>argument(
                                                        "level", StringArgumentType.word())
                                                .executes(context -> 1)));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("diff")
                        .redirect(difficulty));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(dispatcher.parse("diff easy", null))));
        var gamerule =
                dispatcher.register(
                        LiteralArgumentBuilder.<ClientSuggestionProvider>literal("gamerule")
                                .then(
                                        RequiredArgumentBuilder
                                                .<ClientSuggestionProvider, String>argument(
                                                        "rule", StringArgumentType.word())
                                                .executes(context -> 1)
                                                .then(
                                                        RequiredArgumentBuilder
                                                                .<ClientSuggestionProvider, String>
                                                                        argument(
                                                                                "value",
                                                                                StringArgumentType
                                                                                        .word())
                                                                .executes(context -> 1))));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("gr").redirect(gamerule));
        assertEquals(
                PlayerCommandPolicy.Effect.INFORMATION,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(dispatcher.parse("gr test", null))));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(dispatcher.parse("gr test true", null))));
    }

    @Test
    void actualBrigadierAliasAndNestedRedirectKeepTheirNativeCommandRoot() {
        var dispatcher = new CommandDispatcher<ClientSuggestionProvider>();
        var teleport =
                dispatcher.register(
                        LiteralArgumentBuilder.<ClientSuggestionProvider>literal("teleport")
                                .then(
                                        RequiredArgumentBuilder
                                                .<ClientSuggestionProvider, String>argument(
                                                        "targets", StringArgumentType.word())
                                                .then(
                                                        RequiredArgumentBuilder
                                                                .<ClientSuggestionProvider, String>
                                                                        argument(
                                                                                "location",
                                                                                StringArgumentType
                                                                                        .word())
                                                                .executes(context -> 1))));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("tp").redirect(teleport));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("execute")
                        .then(
                                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("run")
                                        .redirect(dispatcher.getRoot())));
        dispatcher.register(
                LiteralArgumentBuilder.<ClientSuggestionProvider>literal("reload")
                        .executes(context -> 1));
        for (String command :
                java.util.List.of(
                        "teleport self here", "tp self here", "execute run tp self here")) {
            var route = NativePlayerCommands.route(dispatcher.parse(command, null));
            assertTrue(route.complete(), command);
            assertEquals(
                    PlayerCommandPolicy.Effect.STATE_MUTATION,
                    PlayerCommandPolicy.classify(route),
                    command);
        }
        assertEquals(
                PlayerCommandPolicy.Effect.CONTROL_PLANE,
                PlayerCommandPolicy.classify(
                        NativePlayerCommands.route(dispatcher.parse("execute run reload", null))));
    }
}
