package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;

/**
 * Native server receipt counters and bounded fixture readback, not an execution acknowledgement.
 */
public final class CommandConfigurationProbe {
    private static final String PREFIX = "say Mine-Craft-Protocol_command_configuration_";
    private static int blocks, minecarts, jigsaws, generations, nativePlayerCommands;
    private static int structureUpdates, structureScans, structureSaves, structureLoads;
    private static String blockHash, minecartHash;
    private static WeakReference<BaseCommandBlock> minecart = new WeakReference<>(null);

    private CommandConfigurationProbe() {}

    private static boolean enabled() {
        return "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"));
    }

    public static void block(ServerPlayer player, ServerboundSetCommandBlockPacket packet) {
        if (!enabled() || !player.level().getServer().isSameThread()) return;
        blocks++;
        blockHash = hash(packet.getCommand());
    }

    public static void minecart(ServerPlayer player, ServerboundSetCommandMinecartPacket packet) {
        if (!enabled() || !player.level().getServer().isSameThread()) return;
        minecarts++;
        minecartHash = hash(packet.getCommand());
        minecart = new WeakReference<>(packet.getCommandBlock(player.level()));
    }

    public static void command(CommandSourceStack source, String command) {
        if (!enabled() || !source.getServer().isSameThread() || source.getPlayer() == null) return;
        nativePlayerCommands++;
        if (!source.getServer().isSingleplayer() || !command.startsWith(PREFIX)) return;
        String attempt = command.substring(PREFIX.length());
        if (!attempt.matches("[0-9a-f]{32}")) return;
        JsonObject result = new JsonObject();
        result.addProperty("attemptId", attempt);
        result.addProperty("probeOnly", true);
        result.addProperty("nativePlayerCommands", nativePlayerCommands);
        result.addProperty("commandBlockPackets", blocks);
        result.addProperty("commandMinecartPackets", minecarts);
        result.addProperty("jigsawConfigurationPackets", jigsaws);
        result.addProperty("jigsawGenerationPackets", generations);
        result.addProperty("structureUpdatePackets", structureUpdates);
        result.addProperty("structureScanPackets", structureScans);
        result.addProperty("structureSavePackets", structureSaves);
        result.addProperty("structureLoadPackets", structureLoads);
        result.addProperty("lastCommandBlockHash", blockHash);
        result.addProperty("lastCommandMinecartHash", minecartHash);
        var chunk = source.getLevel().getChunkSource().getChunkNow(0, 0);
        if (chunk != null
                && chunk.getBlockEntity(new BlockPos(2, 80, 0)) instanceof CommandBlockEntity block)
            result.addProperty(
                    "storedBlockCommandHash", hash(block.getCommandBlock().getCommand()));
        var cart = minecart.get();
        if (chunk != null
                && chunk.getBlockEntity(new BlockPos(2, 80, 0)) instanceof JigsawBlockEntity jigsaw)
            result.addProperty("storedJigsawFinalStateHash", hash(jigsaw.getFinalState()));
        if (cart != null) result.addProperty("storedMinecartCommandHash", hash(cart.getCommand()));
        if (chunk != null
                && chunk.getBlockEntity(new BlockPos(2, 80, 0))
                        instanceof StructureBlockEntity structure) {
            result.addProperty("storedStructureNameHash", hash(structure.getStructureName()));
            result.addProperty("storedStructureMode", structure.getMode().name());
            var offset = structure.getStructurePos();
            var size = structure.getStructureSize();
            result.addProperty(
                    "storedStructureOffset",
                    offset.getX() + "," + offset.getY() + "," + offset.getZ());
            result.addProperty(
                    "storedStructureSize", size.getX() + "," + size.getY() + "," + size.getZ());
        }
        result.addProperty("chunkLoadRequested", false);
        result.addProperty("worldMutationUsedByProbe", false);
        try {
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve(
                            System.getProperty("validation.target")
                                    + "-command-configuration-probe.json"),
                    result + "\n",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static void jigsaw(ServerPlayer player, ServerboundSetJigsawBlockPacket packet) {
        if (enabled() && player.level().getServer().isSameThread()) jigsaws++;
    }

    public static void generate(ServerPlayer player, ServerboundJigsawGeneratePacket packet) {
        if (enabled() && player.level().getServer().isSameThread()) generations++;
    }

    public static void structure(ServerPlayer player, ServerboundSetStructureBlockPacket packet) {
        if (!enabled() || !player.level().getServer().isSameThread()) return;
        switch (packet.getUpdateType()) {
            case UPDATE_DATA -> structureUpdates++;
            case SCAN_AREA -> structureScans++;
            case SAVE_AREA -> structureSaves++;
            case LOAD_AREA -> structureLoads++;
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
