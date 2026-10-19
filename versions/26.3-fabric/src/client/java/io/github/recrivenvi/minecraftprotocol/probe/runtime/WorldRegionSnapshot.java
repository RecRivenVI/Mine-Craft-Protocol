package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch;
import io.github.recrivenvi.minecraftprotocol.safety.WorldRegionPages;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Native client cache only; the caller owns the client thread and the authenticated guard. */
final class WorldRegionSnapshot {
    private WorldRegionSnapshot() {}

    static WorldRegionPages.View view(
            Minecraft client, InputContextEpoch.Snapshot epoch, LongSupplier tick) {
        var level = client.level;
        var player = client.player;
        var connection = client.getConnection();
        Map<BlockState, WorldRegionPages.Cell> states = new IdentityHashMap<>();
        return new WorldRegionPages.View() {
            public WorldRegionPages.Context context() {
                if (!epoch.worldAvailable()
                        || level == null
                        || player == null
                        || connection == null
                        || level != client.level
                        || player != client.player
                        || connection != client.getConnection()
                        || !connection.getConnection().isConnected()) return null;
                return new WorldRegionPages.Context(
                        epoch.opaqueId(),
                        level.dimension().identifier().toString(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        tick.getAsLong());
            }

            public WorldRegionPages.Cell cell(int x, int y, int z) {
                var chunk =
                        level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                if (chunk == null) return WorldRegionPages.Cell.unloaded();
                BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                return states.computeIfAbsent(state, WorldRegionSnapshot::project);
            }
        };
    }

    private static WorldRegionPages.Cell project(BlockState state) {
        Map<String, String> properties = new HashMap<>();
        for (Property<?> property : state.getProperties())
            properties.put(property.getName(), propertyValue(state, property));
        return WorldRegionPages.Cell.known(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties);
    }

    private static <T extends Comparable<T>> String propertyValue(
            BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
