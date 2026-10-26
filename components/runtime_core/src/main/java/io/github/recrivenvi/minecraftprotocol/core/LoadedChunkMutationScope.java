package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;

/** Synchronous native DEBUG work may use loaded chunks, never create a dependency chunk. */
public final class LoadedChunkMutationScope implements AutoCloseable {
    private static final ThreadLocal<LoadedChunkMutationScope> CURRENT = new ThreadLocal<>();
    private final LoadedChunkMutationScope previous;
    private JsonObject blocked;

    private LoadedChunkMutationScope() {
        this.previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static LoadedChunkMutationScope open() {
        return new LoadedChunkMutationScope();
    }

    public static LoadedChunkMutationScope current() {
        return CURRENT.get();
    }

    public void reject(String dimension, int chunkX, int chunkZ) {
        this.blocked = new JsonObject();
        this.blocked.addProperty("dimension", dimension);
        this.blocked.addProperty("chunkX", chunkX);
        this.blocked.addProperty("chunkZ", chunkZ);
        throw new IllegalStateException("DEBUG native dependency chunk is not loaded");
    }

    public JsonObject blockedAccess() {
        return this.blocked == null ? null : this.blocked.deepCopy();
    }

    @Override
    public void close() {
        if (this.previous == null) CURRENT.remove();
        else CURRENT.set(this.previous);
    }
}
