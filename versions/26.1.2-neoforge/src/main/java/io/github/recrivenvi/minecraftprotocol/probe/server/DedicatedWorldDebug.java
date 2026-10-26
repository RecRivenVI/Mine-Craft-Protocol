package io.github.recrivenvi.minecraftprotocol.probe.server;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.DebugBlockStateRequest;
import io.github.recrivenvi.minecraftprotocol.core.ObservationLifecycleTracker;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ServerWorldDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Native server-only boundary; no Minecraft client singleton, player proxy or command dispatch. */
final class DedicatedWorldDebug implements ServerWorldDebug.Host {
    private final DedicatedServer server;
    private final ObservationLifecycleTracker lifecycles = new ObservationLifecycleTracker();
    // Identity tokens must not keep unloaded chunks alive.
    private final java.util.Map<
                    net.minecraft.world.level.chunk.LevelChunk,
                    java.lang.ref.WeakReference<net.minecraft.world.level.chunk.LevelChunk>>
            chunkIdentities = new java.util.WeakHashMap<>();

    DedicatedWorldDebug(DedicatedServer server) {
        this.server = server;
    }

    @Override
    public void schedule(Runnable work) {
        server.execute(work);
    }

    @Override
    public JsonObject world() {
        var level = server.overworld();
        JsonObject value = new JsonObject();
        value.addProperty("dimension", level.dimension().identifier().toString());
        value.addProperty("gameTime", level.getGameTime());
        value.addProperty("raining", level.isRaining());
        value.addProperty("thundering", level.isThundering());
        value.addProperty("blockScheduledTickCount", level.getBlockTicks().count());
        value.addProperty("fluidScheduledTickCount", level.getFluidTicks().count());
        value.addProperty("serverTick", server.getTickCount());
        value.addProperty("loadedChunkCount", level.getChunkSource().getLoadedChunksCount());
        value.addProperty("onlinePlayerCount", server.getPlayerList().getPlayerCount());
        return value;
    }

    @Override
    public JsonObject block(int x, int y, int z) {
        var level = server.overworld();
        if (y < level.getMinY() || y >= level.getMaxY())
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEBUG_REQUEST", 400, "Block y is outside this dimension");
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        JsonObject value = new JsonObject();
        String key = x + "," + y + "," + z;
        value.addProperty("key", key);
        value.addProperty("x", x);
        value.addProperty("y", y);
        value.addProperty("z", z);
        value.addProperty(
                "lifecycleId",
                lifecycles.lifecycleId(
                                "chunk",
                                "minecraft:overworld@" + (x >> 4) + "," + (z >> 4),
                                chunk == null
                                        ? null
                                        : chunkIdentities.computeIfAbsent(
                                                chunk, java.lang.ref.WeakReference::new))
                        + "@block:"
                        + key);
        value.addProperty("available", chunk != null);
        value.addProperty("loadRequested", false);
        if (chunk == null) {
            value.addProperty("reason", "NOT_LOADED");
            return value;
        }
        var state = chunk.getBlockState(new BlockPos(x, y, z));
        value.addProperty("blockId", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        JsonObject properties = new JsonObject();
        state.getProperties().stream()
                .sorted(java.util.Comparator.comparing(Property::getName))
                .forEach(
                        property ->
                                properties.addProperty(
                                        property.getName(), propertyName(state, property)));
        value.add("properties", properties);
        return value;
    }

    @Override
    public void setBlock(int x, int y, int z, String blockId, JsonObject request) {
        var level = server.overworld();
        var position = new BlockPos(x, y, z);
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) throw notLoaded();
        var id = Identifier.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
            throw new ProtocolState.ProtocolException(
                    "UNKNOWN_BLOCK", 400, "Unknown block: " + blockId);
        var block = BuiltInRegistries.BLOCK.getValue(id);
        var current = chunk.getBlockState(position);
        var requested = current.is(block) ? current : block.defaultBlockState();
        for (var entry : DebugBlockStateRequest.properties(request, "properties").entrySet()) {
            var property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null)
                throw new ProtocolState.ProtocolException(
                        "UNKNOWN_BLOCK_PROPERTY", 400, "Unknown property: " + entry.getKey());
            requested = setProperty(requested, property, entry.getValue());
        }
        if (requested == current)
            throw new ProtocolState.ProtocolException(
                    "NO_STATE_CHANGE", 409, "Requested block state is unchanged");
        for (Direction direction : Direction.values()) {
            var neighbor = position.relative(direction);
            if (level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                    == null) throw notLoaded();
        }
        if (!level.setBlockAndUpdate(position, requested))
            throw new ProtocolState.ProtocolException(
                    "DEBUG_MUTATION_REJECTED",
                    409,
                    "Native setBlockAndUpdate rejected the mutation");
    }

    private static ProtocolState.ProtocolException notLoaded() {
        return new ProtocolState.ProtocolException(
                "CHUNK_NOT_LOADED",
                409,
                "Target or direct native neighbor is not loaded; no load requested");
    }

    private static <T extends Comparable<T>> String propertyName(
            BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> property, String value) {
        T parsed =
                property.getValue(value)
                        .orElseThrow(
                                () ->
                                        new ProtocolState.ProtocolException(
                                                "INVALID_BLOCK_PROPERTY_VALUE",
                                                400,
                                                "Invalid native property value: "
                                                        + property.getName()));
        return state.setValue(property, parsed);
    }
}
