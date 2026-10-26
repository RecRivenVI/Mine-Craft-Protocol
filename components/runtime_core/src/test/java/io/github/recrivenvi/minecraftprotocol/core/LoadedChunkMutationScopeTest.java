package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class LoadedChunkMutationScopeTest {
    @Test
    void nativeReentryAndWrappedFailureDoNotLeakTheScopeIntoLaterWork() {
        assertNull(LoadedChunkMutationScope.current());
        try (var outer = LoadedChunkMutationScope.open()) {
            assertSame(outer, LoadedChunkMutationScope.current());
            assertNull(CompletableFuture.supplyAsync(LoadedChunkMutationScope::current).join());
            assertThrows(
                    IllegalStateException.class,
                    () -> {
                        try (var nested = LoadedChunkMutationScope.open()) {
                            try {
                                nested.reject("minecraft:overworld", -2, 3);
                            } catch (IllegalStateException nativeWrapped) {
                                assertEquals(-2, nested.blockedAccess().get("chunkX").getAsInt());
                                throw new IllegalStateException(
                                        "native neighbor wrapper", nativeWrapped);
                            }
                        }
                    });
            assertSame(outer, LoadedChunkMutationScope.current());
            assertNull(outer.blockedAccess());
        }
        assertNull(LoadedChunkMutationScope.current());
    }
}
