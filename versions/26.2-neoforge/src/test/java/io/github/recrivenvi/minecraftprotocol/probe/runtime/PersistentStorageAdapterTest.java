package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

final class PersistentStorageAdapterTest {
    @Test
    void boundedWorldReadReturnsPersistedMetadataAndIdentity() throws Exception {
        Path root = Files.createTempDirectory("mcp-storage-read-");
        try {
            Files.writeString(root.resolve("session.lock"), "session-a");
            writeNbt(root.resolve("level.dat"), "WorldName", "phase9d0");
            PersistentStorageAdapter adapter = new PersistentStorageAdapter("26.2-neoforge");
            adapter.observeWorldLifecycle(new Object());
            try {
                JsonObject result = adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS);
                assertEquals("PERSISTED", result.get("dataSource").getAsString());
                assertEquals("9D-0", result.get("phase").getAsString());
                assertEquals("storage.read", result.get("persistentReadScope").getAsString());
                assertTrue(result.get("formalRead").getAsBoolean());
                assertNotNull(result.get("storageWorldIdentity").getAsString());
                assertTrue(result.get("fileRevision").getAsString().matches("[0-9a-f]{64}"));
                assertEquals("read_only_file_channel", result.get("sideEffects").getAsString());
            } finally {
                adapter.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void worldReplacementInvalidatesAnchoredStorageIdentity() throws Exception {
        Path root = Files.createTempDirectory("mcp-storage-identity-");
        try {
            Files.writeString(root.resolve("session.lock"), "session-a");
            writeNbt(root.resolve("level.dat"), "WorldName", "phase9d0");
            PersistentStorageAdapter adapter = new PersistentStorageAdapter("26.2-neoforge");
            adapter.observeWorldLifecycle(new Object());
            try {
                adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS);
                Files.writeString(root.resolve("session.lock"), "session-b");
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS));
                assertEquals("PERSISTED_STORAGE_WORLD_IDENTITY_CHANGED", protocolCode(failure));
            } finally {
                adapter.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void lifecycleSaveAndCloseFailClosed() throws Exception {
        Path root = Files.createTempDirectory("mcp-storage-lifecycle-");
        try {
            Files.writeString(root.resolve("session.lock"), "session-a");
            writeNbt(root.resolve("level.dat"), "WorldName", "phase9d0");
            PersistentStorageAdapter adapter = new PersistentStorageAdapter("26.2-neoforge");
            try {
                adapter.observeWorldLifecycle(new Object());
                ExecutionException saving = assertThrows(
                        ExecutionException.class,
                        () -> adapter.read(request(root, "world", true)).get(5, TimeUnit.SECONDS));
                assertEquals("PERSISTED_STORAGE_SAVE_IN_PROGRESS", protocolCode(saving));
                adapter.observeWorldLifecycle(null);
                ExecutionException unloaded = assertThrows(
                        ExecutionException.class,
                        () -> adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS));
                assertEquals("PERSISTED_STORAGE_WORLD_UNAVAILABLE", protocolCode(unloaded));
                adapter.close();
                ExecutionException closed = assertThrows(
                        ExecutionException.class,
                        () -> adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS));
                assertEquals("PERSISTED_STORAGE_CLOSED", protocolCode(closed));
            } finally {
                adapter.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void oversizedSourceIsRejectedBeforeNbtDecode() throws Exception {
        Path root = Files.createTempDirectory("mcp-storage-budget-");
        try {
            Files.writeString(root.resolve("session.lock"), "session-a");
            Files.write(root.resolve("level.dat"), new byte[Math.toIntExact(PersistentStorageAdapter.MAX_SOURCE_BYTES + 1L)]);
            PersistentStorageAdapter adapter = new PersistentStorageAdapter("26.2-neoforge");
            adapter.observeWorldLifecycle(new Object());
            try {
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS));
                assertEquals("PERSISTED_STORAGE_BUDGET_EXCEEDED", protocolCode(failure));
            } finally {
                adapter.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void corruptCompressedSourceReturnsControlledFailure() throws Exception {
        Path root = Files.createTempDirectory("mcp-storage-corrupt-");
        try {
            Files.writeString(root.resolve("session.lock"), "session-a");
            Files.write(root.resolve("level.dat"), new byte[] { 1, 2, 3, 4, 5 });
            PersistentStorageAdapter adapter = new PersistentStorageAdapter("26.2-neoforge");
            adapter.observeWorldLifecycle(new Object());
            try {
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> adapter.read(request(root, "world", false)).get(5, TimeUnit.SECONDS));
                assertEquals("PERSISTED_STORAGE_CORRUPT", protocolCode(failure));
            } finally {
                adapter.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void storageBoundsAreFiniteAndWritesAreNotImplemented() {
        assertEquals(8, PersistentStorageAdapter.QUEUE_CAPACITY);
        assertEquals(1, PersistentStorageAdapter.MAX_IN_FLIGHT);
        assertTrue(PersistentStorageAdapter.MAX_SOURCE_BYTES > PersistentStorageAdapter.MAX_NBT_BYTES);
    }

    @Test
    void queueOverflowAndCancellationAreBounded() throws Exception {
        Path root = Files.createTempDirectory("mcp-storage-queue-");
        try {
            Files.writeString(root.resolve("session.lock"), "session-a");
            writeNbt(root.resolve("level.dat"), "WorldName", "phase9d0");
            PersistentStorageAdapter adapter = new PersistentStorageAdapter("26.2-neoforge", 250L);
            adapter.observeWorldLifecycle(new Object());
            try {
                List<java.util.concurrent.CompletableFuture<JsonObject>> requests = new ArrayList<>();
                for (int i = 0; i < 12; i++) requests.add(adapter.read(request(root, "world", false)));
                long queueFull = 0L;
                for (var request : requests) {
                    try { request.get(8, TimeUnit.SECONDS); }
                    catch (ExecutionException failure) {
                        if ("PERSISTED_STORAGE_QUEUE_FULL".equals(protocolCode(failure))) queueFull++;
                    }
                }
                assertTrue(queueFull > 0L, "bounded queue must reject excess reads");
            } finally {
                adapter.close();
            }

            PersistentStorageAdapter cancelledAdapter = new PersistentStorageAdapter("26.2-neoforge", 1_000L);
            cancelledAdapter.observeWorldLifecycle(new Object());
            try {
                var cancelled = cancelledAdapter.read(request(root, "world", false));
                cancelled.cancel(true);
                Thread.sleep(250L);
                assertEquals(0, cancelledAdapter.inFlightCount());
            } finally {
                cancelledAdapter.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    private static Phase9ASpikeEngine.StorageRequest request(Path root, String domain, boolean saveInProgress) {
        return new Phase9ASpikeEngine.StorageRequest(
                domain,
                root,
                root.resolve("players").resolve("data"),
                "phase9d0",
                null,
                UUID.randomUUID().toString(),
                null,
                true,
                true,
                saveInProgress,
                "session-epoch-a",
                1L,
                "legacy-world-fingerprint");
    }

    private static void writeNbt(Path path, String key, String value) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        NbtIo.writeCompressed(tag, path);
    }

    private static String protocolCode(ExecutionException failure) {
        Throwable cause = failure.getCause();
        while (cause != null && !(cause instanceof ProtocolState.ProtocolException)) cause = cause.getCause();
        assertNotNull(cause);
        return ((ProtocolState.ProtocolException) cause).code();
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException exception) { throw new RuntimeException(exception); }
            });
        }
    }
}
