package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Owned, opt-in Arrange only. Product Act still uses the formal typed mutation API. */
public final class WorldDebugBoundaryFixture {
    private static final String TARGET = "1.20.1-forge";
    private static Fixture fixture;

    private WorldDebugBoundaryFixture() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.worldDebugBoundaryFixture")
                && "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"));
    }

    public static void change(boolean prepare, Button button) {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null)
            throw new IllegalStateException("Boundary fixture requires its integrated player");
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (!root.endsWith(
                            Path.of(
                                    "validations",
                                    "conformance-world_read",
                                    "instance",
                                    TARGET,
                                    "client"))
                    || !root.toRealPath().equals(root)
                    || !server.getWorldPath(LevelResource.ROOT)
                            .toRealPath()
                            .startsWith(root.resolve("saves")))
                throw new IllegalStateException("Boundary fixture is outside its validation world");
            var owner =
                    JsonParser.parseString(
                                    Files.readString(
                                            root.resolve("world-read-owner.json"),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (!TARGET.equals(owner.get("target").getAsString())
                    || !"world-read-classification".equals(owner.get("owner").getAsString()))
                throw new IllegalStateException("Boundary fixture owner mismatch");
        } catch (java.io.IOException error) {
            throw new IllegalStateException(
                    "Boundary fixture ownership could not be verified", error);
        }
        UUID playerId = client.player.getUUID();
        button.active = false;
        server.execute(
                () -> {
                    JsonObject report = new JsonObject();
                    report.addProperty("target", TARGET);
                    report.addProperty("authority", "FIXTURE");
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player == null
                                || !server.isSameThread()
                                || server.getPlayerList().getPlayerCount() != 1
                                || !server.getWorldData().getLevelName().equals("World-Read-Alpha"))
                            throw new IllegalStateException(
                                    "Boundary fixture requires the fixed solo world");
                        ServerLevel level = player.serverLevel();
                        if (prepare) prepare(level, player, report);
                        else restore(level, report);
                        report.addProperty("status", prepare ? "prepared" : "restored");
                    } catch (Throwable error) {
                        report.addProperty("status", "failed");
                        report.addProperty("error", error.toString());
                    }
                    try {
                        Files.writeString(
                                root.resolve("world-debug-boundary.json"),
                                report.toString() + "\n",
                                StandardCharsets.UTF_8);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException(
                                "Boundary fixture result could not be written", error);
                    }
                    client.execute(
                            () -> {
                                button.setMessage(
                                        Component.literal(
                                                report.get("status").getAsString().equals("failed")
                                                        ? "Boundary Fixture Failed"
                                                        : prepare
                                                                ? "Boundary Ready (Fixture)"
                                                                : "Boundary Restored (Fixture)"));
                                button.active = true;
                            });
                });
    }

    private static void prepare(ServerLevel level, ServerPlayer player, JsonObject report) {
        if (fixture != null)
            throw new IllegalStateException("Existing fixture must be restored first");
        int cx = player.chunkPosition().x + 64, cz = player.chunkPosition().z + 64;
        if (level.getChunkSource().getChunkNow(cx, cz) != null)
            throw new IllegalStateException("Unknown preloaded destination must be preserved");
        Fixture created = new Fixture(cx, cz);
        fixture = created;
        level.getChunk(cx, cz);
        int edge = cx;
        while (edge < cx + 8 && level.getChunkSource().getChunkNow(edge + 1, cz) != null) edge++;
        if (edge == cx + 8)
            throw new IllegalStateException("Expected a bounded loaded island edge");
        created.source =
                new BlockPos((edge << 4) + 15, player.blockPosition().getY() + 30, (cz << 4) + 8);
        created.outside = created.source.east();
        created.propagationSources =
                List.of(created.source.offset(-1, 0, 2), created.source.offset(-1, 0, 4));
        LevelChunk chunk = level.getChunkSource().getChunkNow(edge, cz);
        if (chunk == null || !chunk.getBlockState(created.source).isAir())
            throw new IllegalStateException("Only owned air is an admissible fixture source");
        for (BlockPos p : created.propagationCells())
            if (!chunk.getBlockState(p).isAir())
                throw new IllegalStateException(
                        "Only owned air may be used for the propagation fixture");
        // Arrange a native wire without running its placement callbacks into the unknown chunk.
        // Only the formal product mutation below is allowed to trigger neighbor propagation.
        for (BlockPos p : created.propagationSources) {
            fixtureState(chunk, p.below(), Blocks.STONE.defaultBlockState());
            fixtureState(chunk, p.east().below(), Blocks.STONE.defaultBlockState());
            fixtureState(chunk, p.east(), Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        report.add("source", position(created.source));
        report.add("outside", position(created.outside));
        JsonArray propagation = new JsonArray();
        created.propagationSources.forEach(p -> propagation.add(position(p)));
        report.add("propagationSources", propagation);
        report.addProperty("sourceLoaded", true);
        report.addProperty("outsideLoaded", false);
        report.addProperty("arrangeLoadRequested", true);
        report.addProperty("forcedTicketsCreated", 0);
        report.addProperty("generation", UUID.randomUUID().toString());
    }

    private static void restore(ServerLevel level, JsonObject report) {
        Fixture current = fixture;
        if (current == null) throw new IllegalStateException("No owned boundary fixture");
        if (current.source != null) {
            LevelChunk chunk =
                    level.getChunkSource()
                            .getChunkNow(current.source.getX() >> 4, current.source.getZ() >> 4);
            if (chunk == null)
                throw new IllegalStateException("Owned source chunk unexpectedly unloaded");
            var state = chunk.getBlockState(current.source);
            if (!state.isAir() && !state.is(Blocks.STONE))
                throw new IllegalStateException("Unknown external block change must be preserved");
            if (!state.isAir()) {
                level.getChunk(current.outside.getX() >> 4, current.outside.getZ() >> 4);
                level.setBlockAndUpdate(current.source, Blocks.AIR.defaultBlockState());
            }
            report.addProperty("sourceRestored", chunk.getBlockState(current.source).isAir());
            for (BlockPos p : current.propagationCells()) {
                var existing = chunk.getBlockState(p);
                if (!existing.isAir()
                        && !existing.is(Blocks.REDSTONE_BLOCK)
                        && !existing.is(Blocks.REDSTONE_WIRE)
                        && !existing.is(Blocks.STONE))
                    throw new IllegalStateException(
                            "Unknown propagation fixture change must be preserved");
            }
            for (BlockPos p : current.propagationCells())
                fixtureState(chunk, p, Blocks.AIR.defaultBlockState());
            report.addProperty("propagationRestored", true);
        }
        report.addProperty("forcedTicketsCreated", 0);
        fixture = null;
    }

    private static void fixtureState(
            LevelChunk chunk, BlockPos p, net.minecraft.world.level.block.state.BlockState state) {
        chunk.getSection(chunk.getSectionIndex(p.getY()))
                .setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, state);
    }

    private static JsonObject position(BlockPos p) {
        JsonObject value = new JsonObject();
        value.addProperty("x", p.getX());
        value.addProperty("y", p.getY());
        value.addProperty("z", p.getZ());
        return value;
    }

    private static final class Fixture {
        final int cx, cz;
        BlockPos source, outside;
        List<BlockPos> propagationSources = List.of();

        List<BlockPos> propagationCells() {
            List<BlockPos> cells = new ArrayList<>();
            for (BlockPos p : propagationSources) {
                cells.add(p);
                cells.add(p.east());
                cells.add(p.below());
                cells.add(p.east().below());
            }
            return cells;
        }

        Fixture(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }
}
