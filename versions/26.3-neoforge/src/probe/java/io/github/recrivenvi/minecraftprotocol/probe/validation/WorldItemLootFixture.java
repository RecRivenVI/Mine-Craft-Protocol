package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Opt-in pending-loot Arrange on the runner's existing empty barrel, not a product operation. */
public final class WorldItemLootFixture {
    private static final String TARGET = "26.3-neoforge";

    private WorldItemLootFixture() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.worldItemLootFixture")
                && "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"));
    }

    public static void change(boolean pending, Button button) {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null)
            throw new IllegalStateException("Loot fixture requires its integrated player");
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!root.endsWith(
                Path.of("validations", "conformance-world_read", "instance", TARGET, "client")))
            throw new IllegalStateException(
                    "Loot fixture is outside its owned validation directory");
        try {
            if (!root.toRealPath().equals(root)
                    || !server.getWorldPath(LevelResource.ROOT)
                            .toRealPath()
                            .startsWith(root.resolve("saves")))
                throw new IllegalStateException(
                        "Loot fixture world is outside its validation saves");
            var owner =
                    JsonParser.parseString(
                                    Files.readString(
                                            root.resolve("world-read-owner.json"),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (!TARGET.equals(owner.get("target").getAsString())
                    || !"world-read-classification".equals(owner.get("owner").getAsString()))
                throw new IllegalStateException("Loot fixture owner mismatch");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Loot fixture ownership could not be verified", error);
        }
        UUID playerId = client.player.getUUID();
        button.active = false;
        server.execute(
                () -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player == null
                            || !server.isSameThread()
                            || server.getPlayerList().getPlayerCount() != 1
                            || !server.getWorldData().getLevelName().equals("World-Read-Alpha"))
                        throw new IllegalStateException(
                                "Loot fixture requires the fixed solo world");
                    ServerLevel level = player.level();
                    BlockPos position = player.blockPosition().offset(4, 3, 4);
                    LevelChunk chunk =
                            level.getChunkSource()
                                    .getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                    if (chunk == null
                            || !(chunk.getBlockEntities().get(position)
                                    instanceof BarrelBlockEntity barrel))
                        throw new IllegalStateException(
                                "Loot fixture requires its already loaded native barrel");
                    var loot =
                            ResourceKey.create(
                                    Registries.LOOT_TABLE,
                                    Identifier.tryParse("minecraft:chests/simple_dungeon"));
                    if (pending) {
                        if (barrel.getLootTable() != null)
                            throw new IllegalStateException(
                                    "Unknown pending loot must not be overwritten");
                        barrel.setLootTable(loot);
                        barrel.setLootTableSeed(0);
                    } else {
                        if (!loot.equals(barrel.getLootTable()) || barrel.getLootTableSeed() != 0)
                            throw new IllegalStateException(
                                    "Unknown changed loot fixture must be preserved");
                        barrel.setLootTable(null);
                        barrel.setLootTableSeed(0);
                    }
                    barrel.setChanged();
                    client.execute(
                            () -> {
                                button.setMessage(
                                        Component.literal(
                                                pending
                                                        ? "Pending Loot Prepared (Fixture)"
                                                        : "Pending Loot Cleared (Fixture)"));
                                button.active = true;
                            });
                });
    }
}
