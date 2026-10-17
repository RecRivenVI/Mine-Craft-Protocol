package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/** Validation-only replay of one already loaded native Chunk; never shipped in a product JAR. */
public final class ChunkPacketReplay {
    private static final String PREFIX = "say Mine-Craft-Protocol_chunk_snapshot_";
    private static final BlockPos POSITION = new BlockPos(2, 80, 0);
    private static volatile Request request;

    private static final class Request {
        final String attempt;
        LevelChunk chunk;
        BlockState before;
        boolean reusedCachedChunk;
        int positionUpdates;

        Request(String attempt) {
            this.attempt = attempt;
        }
    }

    private ChunkPacketReplay() {}

    public static void command(CommandSourceStack source, String command) {
        if (!"conformance-world_read".equals(System.getProperty("validation.name"))
                || !"client".equals(System.getProperty("validation.role"))
                || !command.startsWith(PREFIX)) return;
        String attempt = command.substring(PREFIX.length());
        if (!attempt.matches("[0-9a-f]{32}")
                || !source.getServer().isSingleplayer()
                || source.getPlayer() == null
                || request != null) return;
        var chunks = source.getLevel().getChunkSource();
        LevelChunk chunk = chunks.getChunkNow(0, 0);
        if (chunk == null) throw new IllegalStateException("Replay fixture Chunk is not loaded");
        request = new Request(attempt);
        source.getPlayer()
                .connection
                .send(
                        new ClientboundLevelChunkWithLightPacket(
                                chunk, chunks.getLightEngine(), null, null));
    }

    public static void before(LevelChunk chunk) {
        Request pending = request;
        if (pending == null
                || pending.chunk != null
                || chunk.getPos().x != 0
                || chunk.getPos().z != 0) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null)
            throw new IllegalStateException("Replay is not on the client owner thread");
        pending.reusedCachedChunk =
                client.level.getChunkSource().getChunk(0, 0, ChunkStatus.FULL, false) == chunk;
        pending.chunk = chunk;
        pending.before = chunk.getBlockState(POSITION);
    }

    public static void positionUpdate(LevelChunk chunk) {
        Request pending = request;
        if (pending != null && pending.chunk == chunk) pending.positionUpdates++;
    }

    public static void after(LevelChunk chunk) {
        Request pending = request;
        if (pending == null || pending.chunk != chunk) return;
        JsonObject result = new JsonObject();
        result.addProperty("attemptId", pending.attempt);
        result.addProperty("probeOnly", true);
        result.addProperty("sourceChunkAlreadyLoaded", true);
        result.addProperty("sameNativeCachedChunk", pending.reusedCachedChunk);
        result.addProperty("sameBlockState", pending.before == chunk.getBlockState(POSITION));
        result.addProperty("nativePositionUpdates", pending.positionUpdates);
        result.addProperty("nativeSnapshotCompleted", true);
        result.addProperty("worldMutationUsed", false);
        result.addProperty("chunkLoadRequested", false);
        String target = System.getProperty("validation.target");
        if (target == null || !target.matches("[0-9.]+-(?:neoforge|forge|fabric)"))
            throw new IllegalStateException("Invalid validation Target");
        try {
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve(target + "-chunk-snapshot.json"),
                    result + "\n",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot record native replay evidence", failure);
        } finally {
            request = null;
        }
    }
}
