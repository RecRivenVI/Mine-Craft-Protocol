package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.github.recrivenvi.minecraftprotocol.safety.PersistentWriteSafetyFoundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.jpountz.lz4.LZ4BlockInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Target-local persistent read boundary. It intentionally exposes no write
 * operation and never accepts a caller supplied filesystem path.
 */
final class PersistentStorageAdapter implements AutoCloseable {
    static final long MAX_SOURCE_BYTES = 16L * 1024L * 1024L;
    static final long MAX_NBT_BYTES = 2L * 1024L * 1024L;
    static final int QUEUE_CAPACITY = 8;
    static final int MAX_IN_FLIGHT = 1;
    private static final long CLOSE_TIMEOUT_MILLIS = 2_000L;

    private final String target;
    private final long testDelayMillis;
    private final ThreadPoolExecutor worker;
    private final Set<CompletableFuture<JsonObject>> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean worldAvailable = new AtomicBoolean(false);
    private final AtomicLong lifecycleEpoch = new AtomicLong();
    private final AtomicReference<Object> observedWorld = new AtomicReference<>();
    private final AtomicReference<String> anchoredWorldIdentity = new AtomicReference<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicReference<Phase9ASpikeEngine.StorageRequest> retainedContext = new AtomicReference<>();
    private final AtomicBoolean contextCapturePending = new AtomicBoolean();
    private volatile boolean fullyStopped;
    private volatile boolean saving;

    PersistentStorageAdapter(String target) {
        this(target, 0L);
    }

    PersistentStorageAdapter(String target, long testDelayMillis) {
        this.target = target;
        this.testDelayMillis = Math.max(0L, Math.min(testDelayMillis, 5_000L));
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-phase9d-storage");
            thread.setDaemon(true);
            return thread;
        };
        this.worker = new ThreadPoolExecutor(
                MAX_IN_FLIGHT,
                MAX_IN_FLIGHT,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    void observeWorldLifecycle(Object world) {
        Object previous = this.observedWorld.getAndSet(world);
        if (previous != world) {
            this.lifecycleEpoch.incrementAndGet();
            if (world != null) {
                this.anchoredWorldIdentity.set(null);
                this.retainedContext.set(null);
            }
            if (world == null) {
                cancelPending("world_unload");
            }
        }
        this.worldAvailable.set(world != null);
    }


    /** Only detached path/identity metadata is retained, never a Server/Level/Player. */
    void rememberContext(Phase9ASpikeEngine.StorageRequest request) {
        if (!this.accepting.get() || !this.worldAvailable.get()
                || !this.contextCapturePending.compareAndSet(false, true)) return;
        long epoch = this.lifecycleEpoch.get();
        try {
            this.worker.execute(() -> {
                try {
                    String identity = PersistentWriteSafetyFoundation.StorageIdentity.readIdentity(request.root(), this.target);
                    if (this.accepting.get() && this.worldAvailable.get() && this.lifecycleEpoch.get() == epoch) {
                        String anchored = this.anchoredWorldIdentity.get();
                        if (anchored != null && !anchored.equals(identity)) return;
                        this.anchoredWorldIdentity.compareAndSet(null, identity);
                        this.retainedContext.set(request);
                    }
                } catch (Exception ignored) {
                    // No usable identity means no offline context, never a guessed path.
                } finally { this.contextCapturePending.set(false); }
            });
        } catch (RejectedExecutionException ignored) { this.contextCapturePending.set(false); }
    }

    void observeStorageLifecycle(Object world, boolean saving, boolean fullyStopped) {
        observeWorldLifecycle(world);
        if (this.saving != saving || this.fullyStopped != fullyStopped) {
            this.lifecycleEpoch.incrementAndGet();
            cancelPending("storage_lifecycle_transition");
        }
        this.saving = saving;
        this.fullyStopped = fullyStopped && world == null;
    }

    CompletableFuture<JsonObject> readSaved(JsonObject query) {
        Phase9ASpikeEngine.StorageRequest base = this.retainedContext.get();
        if (!this.accepting.get()) return failed("PERSISTED_STORAGE_CLOSED", 409, "Storage adapter is closed");
        if (!this.fullyStopped || this.worldAvailable.get()) {
            return failed("PERSISTED_STORAGE_BUSY", 409, "World has not finished Save & Quit");
        }
        if (base == null) return failed("PERSISTED_STORAGE_CONTEXT_UNAVAILABLE", 409, "No safely retained storage context");
        String domain = query.has("domain") ? query.get("domain").getAsString() : "";
        if (!Set.of("world", "player", "chunk").contains(domain)) {
            return failed("INVALID_STORAGE_DOMAIN", 400, "domain must be world, player, or chunk");
        }
        int x = query.has("chunkX") ? query.get("chunkX").getAsInt() : (base.chunkPos() == null ? 0 : base.chunkPos().getMinBlockX() >> 4);
        int z = query.has("chunkZ") ? query.get("chunkZ").getAsInt() : (base.chunkPos() == null ? 0 : base.chunkPos().getMinBlockZ() >> 4);
        return read(new Phase9ASpikeEngine.StorageRequest(domain, base.root(), base.playerDataRoot(),
                base.levelName(), base.dimension(), base.playerUuid(), new ChunkPos(x, z),
                false, false, false, base.sessionEpoch(), this.lifecycleEpoch.get(), base.worldFingerprint()));
    }

    static ProtocolState.ProtocolException classifyIo(IOException error) {
        if (error instanceof java.nio.file.NoSuchFileException) {
            return new ProtocolState.ProtocolException("PERSISTED_STORAGE_NOT_FOUND", 404, "Persisted file is not found");
        }
        if (error instanceof java.nio.file.AccessDeniedException) {
            return new ProtocolState.ProtocolException("PERSISTED_STORAGE_UNAVAILABLE", 403, "Storage access denied or unavailable");
        }
        if (error instanceof java.nio.file.FileSystemException) {
            return new ProtocolState.ProtocolException("PERSISTED_STORAGE_BUSY", 409, "Storage is busy/locked; retry after Save & Quit");
        }
        if (error instanceof java.io.EOFException || error instanceof java.util.zip.ZipException
                || error instanceof java.io.UTFDataFormatException) {
            return new ProtocolState.ProtocolException("PERSISTED_STORAGE_CORRUPT", 422, "Persisted data is corrupt or truncated");
        }
        return new ProtocolState.ProtocolException("PERSISTED_STORAGE_READ_FAILED", 503, "Storage IO is unavailable; corruption is not established");
    }

    long lifecycleEpoch() {
        return this.lifecycleEpoch.get();
    }

    int inFlightCount() {
        return this.inFlight.get();
    }

    CompletableFuture<JsonObject> read(Phase9ASpikeEngine.StorageRequest request) {
        if (!this.accepting.get()) return failed("PERSISTED_STORAGE_CLOSED", 409, "Persistent storage adapter is closed");
        if (request.liveWorldExists() != this.worldAvailable.get()
                || (!request.liveWorldExists() && !this.fullyStopped)) {
            return failed("PERSISTED_STORAGE_WORLD_UNAVAILABLE", 409, "Live world lifecycle is not available");
        }
        if (request.saveInProgress() || this.saving) {
            return failed("PERSISTED_STORAGE_SAVE_IN_PROGRESS", 409, "Minecraft is currently saving the world");
        }

        long requestEpoch = this.lifecycleEpoch.get();
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        this.pending.add(result);
        Runnable task = () -> {
            this.inFlight.incrementAndGet();
            try {
                if (!active(requestEpoch, result)) return;
                JsonObject response = readNow(request, requestEpoch, result);
                if (active(requestEpoch, result)) result.complete(response);
            } catch (ProtocolState.ProtocolException exception) {
                result.completeExceptionally(exception);
            } catch (PersistentWriteSafetyFoundation.SafetyFailure exception) {
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        exception.code(), 409, exception.getMessage()));
            } catch (CancellationException exception) {
                result.cancel(false);
            } catch (IOException exception) {
                result.completeExceptionally(classifyIo(exception));
            } catch (RuntimeException exception) {
                String code = exception.getMessage() != null && exception.getMessage().contains("too big")
                        ? "PERSISTED_STORAGE_BUDGET_EXCEEDED" : "PERSISTED_STORAGE_READ_FAILED";
                result.completeExceptionally(new ProtocolState.ProtocolException(code, code.endsWith("EXCEEDED") ? 413 : 422, exception.getClass().getSimpleName()));
            } catch (Throwable exception) {
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "PERSISTED_STORAGE_READ_FAILED", 500,
                        "Bounded persisted read failed: " + exception.getClass().getSimpleName()));
            } finally {
                this.inFlight.decrementAndGet();
                this.pending.remove(result);
            }
        };
        try {
            this.worker.execute(task);
        } catch (RejectedExecutionException exception) {
            this.pending.remove(result);
            return failed("PERSISTED_STORAGE_QUEUE_FULL", 429, "Persistent storage read queue is full");
        }
        result.whenComplete((ignored, error) -> {
            if (result.isCancelled()) {
                // A queued task will observe the cancelled future before touching the filesystem;
                // a running task also checks the token between every bounded operation.
                this.worker.getQueue().remove(task);
            }
        });
        return result;
    }

    private JsonObject readNow(
            Phase9ASpikeEngine.StorageRequest request, long epoch, CompletableFuture<JsonObject> result) throws Exception {
        if (request.liveWorldExists()) return readSnapshot(request, epoch, result);
        // A shared read-only OS lock prevents another Minecraft process taking ownership
        // during the offline snapshot. No lock file is created or modified.
        try (FileChannel channel = FileChannel.open(request.root().resolve("session.lock"), StandardOpenOption.READ);
             java.nio.channels.FileLock guard = channel.tryLock(0L, Long.MAX_VALUE, true)) {
            if (guard == null) throw new ProtocolState.ProtocolException(
                    "PERSISTED_STORAGE_BUSY", 409, "Another Minecraft process owns this save");
            return readSnapshot(request, epoch, result);
        } catch (java.nio.channels.OverlappingFileLockException busy) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_BUSY", 409, "Storage is owned by an active Runtime");
        }
    }

    private JsonObject readSnapshot(
            Phase9ASpikeEngine.StorageRequest request,
            long requestEpoch,
            CompletableFuture<JsonObject> result) throws Exception {
        checkActive(requestEpoch, result);
        if (this.testDelayMillis > 0L) {
            try {
                Thread.sleep(this.testDelayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("persistent storage test delay interrupted");
            }
            checkActive(requestEpoch, result);
        }
        Path root = request.root().toAbsolutePath().normalize();
        Path levelData = root.resolve("level.dat");
        String worldIdentity = anchorWorldIdentity(request, root, null);
        FileStamp lockBefore = metadataOnly(root.resolve("session.lock"));
        FileStamp worldBefore = snapshot(levelData, requestEpoch, result);
        if (!worldBefore.exists()) {
            throw new ProtocolState.ProtocolException(
                    "PERSISTED_STORAGE_WORLD_IDENTITY_UNAVAILABLE", 409,
                    "level.dat is unavailable; storage identity cannot be established");
        }
        Path file = resolveFile(request, root);
        FileStamp before = snapshot(file, requestEpoch, result);
        if (!before.exists()) {
            FileStamp worldAfter = snapshot(levelData, requestEpoch, result);
            ensureStable(worldBefore, worldAfter, "level.dat");
            return persistedResult(request, file, null, worldIdentity, before, before, "not_found");
        }

        CompoundTag tag;
        try {
            if ("chunk".equals(request.domain())) {
                tag = readChunk(file, request, requestEpoch, result);
            } else {
                tag = readCompressed(file, requestEpoch, result);
            }
        } catch (IOException | ProtocolState.ProtocolException failure) {
            // A concurrent save must not be diagnosed as malformed stable data.
            ensureStable(before, snapshot(file, requestEpoch, result), file.getFileName().toString());
            ensureStable(worldBefore, snapshot(levelData, requestEpoch, result), "level.dat");
            throw failure;
        }
        checkActive(requestEpoch, result);
        FileStamp after = snapshot(file, requestEpoch, result);
        FileStamp worldAfter = snapshot(levelData, requestEpoch, result);
        ensureStable(lockBefore, metadataOnly(root.resolve("session.lock")), "session.lock");
        anchorWorldIdentity(request, root, null);
        ensureStable(before, after, file.getFileName().toString());
        ensureStable(worldBefore, worldAfter, "level.dat");
        return persistedResult(request, file, tag, worldIdentity, before, after, "ok");
    }

    private Path resolveFile(Phase9ASpikeEngine.StorageRequest request, Path root) {
        if ("world".equals(request.domain())) return root.resolve("level.dat");
        if ("player".equals(request.domain())) {
            Path playerRoot = request.playerDataRoot().toAbsolutePath().normalize();
            if (!playerRoot.startsWith(root)) throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_PATH_INVALID", 422, "Player storage path is outside the world root");
            return playerRoot.resolve(request.playerUuid() + ".dat");
        }
        Path dimensionRoot = DimensionType.getStorageFolder(request.dimension(), root);
        Path normalized = dimensionRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_PATH_INVALID", 422, "Dimension storage path is outside the world root");
        return normalized.resolve("region")
                .resolve("r." + request.chunkPos().getRegionX() + "." + request.chunkPos().getRegionZ() + ".mca");
    }

    private CompoundTag readCompressed(
            Path file,
            long requestEpoch,
            CompletableFuture<JsonObject> result) throws Exception {
        try (InputStream raw = Files.newInputStream(file, StandardOpenOption.READ);
             InputStream gzip = new GZIPInputStream(raw);
             BoundedInputStream bounded = new BoundedInputStream(gzip, MAX_SOURCE_BYTES);
             DataInputStream input = new DataInputStream(bounded)) {
            checkActive(requestEpoch, result);
            return NbtIo.read(input, NbtAccounter.create(MAX_NBT_BYTES));
        }
    }

    // Package-visible for synthetic byte-stream safety tests, without a live world bootstrap.
    CompoundTag readChunk(
            Path file,
            Phase9ASpikeEngine.StorageRequest request,
            long requestEpoch,
            CompletableFuture<JsonObject> result) throws Exception {
        FileStamp stamp = snapshot(file, requestEpoch, result);
        int locationIndex = (request.chunkPos().getRegionLocalZ() * 32 + request.chunkPos().getRegionLocalX()) * 4;
        ByteBuffer header = ByteBuffer.allocate(8_192);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            readFully(channel, header, 0L);
        }
        header.flip();
        int location = header.getInt(locationIndex);
        if (location == 0) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_CHUNK_NOT_FOUND", 404, "Chunk is not persisted in this region");
        }
        int sectors = location & 0xFF;
        int firstSector = (location >>> 8) & 0xFFFFFF;
        long offset = (long) firstSector * 4_096L;
        if (firstSector < 2 || sectors <= 0 || offset + 5L > stamp.size()) {
            ensureStable(stamp, snapshot(file, requestEpoch, result), file.getFileName().toString());
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_CORRUPT", 422, "Region sector entry is invalid");
        }
        long allocatedBytes = Math.multiplyExact((long) sectors, 4_096L);
        if (allocatedBytes > MAX_SOURCE_BYTES) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_BUDGET_EXCEEDED", 413, "Region chunk allocation exceeds the read budget");
        }
        // Anvil sector allocation is not a promise that the last sector is padded.
        // Read only the bytes actually present, then validate the declared payload.
        long readableBytes = Math.min(allocatedBytes, stamp.size() - offset);
        ByteBuffer chunk = ByteBuffer.allocate(Math.toIntExact(readableBytes));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            readFully(channel, chunk, (long) firstSector * 4_096L);
        }
        chunk.flip();
        if (chunk.remaining() < 5) throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_CORRUPT", 422, "Region chunk header is truncated");
        int length = chunk.getInt();
        int compression = Byte.toUnsignedInt(chunk.get());
        if ((compression & 0x80) != 0) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_UNSUPPORTED_EXTERNAL_CHUNK", 422,
                    "External .mcc chunks are not part of this bounded read contract");
        }
        int payloadLength = length - 1;
        if (length <= 1 || payloadLength > chunk.remaining()) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_CORRUPT", 422, "Region chunk payload is truncated");
        }
        byte[] payload = new byte[payloadLength];
        chunk.get(payload);
        InputStream compressed = new ByteArrayInputStream(payload);
        InputStream decoded = switch (compression) {
            case 1 -> new GZIPInputStream(compressed);
            case 2 -> new InflaterInputStream(compressed);
            case 3 -> compressed;
            case 4 -> new LZ4BlockInputStream(compressed);
            default -> throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_UNSUPPORTED_COMPRESSION", 422, "Unsupported region compression id: " + compression);
        };
        try (InputStream stream = decoded; DataInputStream input = new DataInputStream(stream)) {
            checkActive(requestEpoch, result);
            return NbtIo.read(input, NbtAccounter.create(MAX_NBT_BYTES));
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) throw new java.io.EOFException("unexpected end of persisted file");
            position += read;
        }
    }

    private JsonObject persistedResult(
            Phase9ASpikeEngine.StorageRequest request,
            Path file,
            CompoundTag tag,
            String worldIdentity,
            FileStamp before,
            FileStamp after,
            String readStatus) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "phase9a.storage.read");
        json.addProperty("target", this.target);
        json.addProperty("phase", "9D-0");
        json.addProperty("experimental", false);
        json.addProperty("formalRead", true);
        json.addProperty("persistentReadScope", "storage.read");
        json.addProperty("wireProtocolFrozen", false);
        json.addProperty("domain", request.domain());
        json.addProperty("source", "persistent_storage");
        json.addProperty("dataSource", "PERSISTED");
        json.addProperty("perspective", "persistent_storage");
        json.addProperty("acquisition", "bounded_minecraft_storage_api");
        json.addProperty("storageApi", "NbtIo+read_only_region_channel");
        json.addProperty("worldFingerprint", request.worldFingerprint());
        json.addProperty("storageWorldIdentity", worldIdentity);
        json.addProperty("sessionEpoch", request.sessionEpoch());
        json.addProperty("storageLifecycleEpoch", request.lifecycleEpoch());
        json.addProperty("lifecycleState", request.liveWorldExists() ? "active_file_snapshot" : "offline_file_snapshot");
        json.addProperty("contextSource", request.liveWorldExists() ? "integrated_server" : "retained_detached_storage_context");
        json.addProperty("dimension", request.dimension() == null ? "" : request.dimension().identifier().toString());
        json.addProperty("playerUuid", request.playerUuid());
        json.addProperty("chunkX", request.chunkPos() == null ? 0 : request.chunkPos().x());
        json.addProperty("chunkZ", request.chunkPos() == null ? 0 : request.chunkPos().z());
        json.addProperty("liveWorldExists", request.liveWorldExists());
        json.addProperty("targetLoaded", request.targetLoaded());
        json.addProperty("saveInProgressAtCapture", request.saveInProgress());
        json.addProperty("saveState", request.saveInProgress() ? "saving_at_capture" : "not_saving_at_capture_file_stable");
        json.addProperty("consistency", "last_saved_state");
        json.addProperty("stalePossibility", request.liveWorldExists());
        json.addProperty("storageAccessOccurred", true);
        json.addProperty("sideEffects", "read_only_file_channel");
        json.addProperty("ownershipGuard", request.liveWorldExists() ? "runtime_lifecycle" : "shared_read_only_session_lock");
        json.addProperty("writeImplemented", false);
        json.addProperty("readStatus", readStatus);
        json.addProperty("fileExists", before.exists());
        json.addProperty("saveMarker", after.modifiedMillis());
        json.addProperty("fileRevision", after.sha256());
        json.addProperty("fileKey", after.fileKey());
        json.addProperty("serializedBytes", after.size());
        json.addProperty("maxSourceBytes", MAX_SOURCE_BYTES);
        json.addProperty("maxNbtBytes", MAX_NBT_BYTES);
        json.addProperty("queueCapacity", QUEUE_CAPACITY);
        json.addProperty("maxInFlight", MAX_IN_FLIGHT);
        json.addProperty("queueDepth", this.worker.getQueue().size());
        json.addProperty("inFlight", this.inFlight.get());
        json.addProperty("available", tag != null);
        if (tag != null) {
            JsonArray keys = new JsonArray();
            tag.keySet().stream().sorted().forEach(keys::add);
            json.add("rootKeys", keys);
            json.addProperty("decodedCharacters", tag.toString().length());
            json.addProperty("decodedSha256", sha256(tag.toString()));
        }
        return json;
    }

    private String anchorWorldIdentity(
            Phase9ASpikeEngine.StorageRequest request,
            Path root,
            FileStamp levelData) throws IOException, PersistentWriteSafetyFoundation.SafetyFailure {
        String identity = PersistentWriteSafetyFoundation.StorageIdentity.readIdentity(root, this.target);
        String previous = this.anchoredWorldIdentity.get();
        if (previous == null && this.anchoredWorldIdentity.compareAndSet(null, identity)) return identity;
        if (previous == null) previous = this.anchoredWorldIdentity.get();
        if (!identity.equals(previous)) {
            throw new ProtocolState.ProtocolException(
                    "PERSISTED_STORAGE_WORLD_IDENTITY_CHANGED", 409,
                    "The storage identity changed during this Runtime world lifecycle");
        }
        return identity;
    }

    private FileStamp snapshot(Path path, long requestEpoch, CompletableFuture<JsonObject> result) throws IOException {
        checkActive(requestEpoch, result);
        return snapshotUnchecked(path);
    }

    private FileStamp snapshotUnchecked(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_SYMLINK_UNSUPPORTED", 422, "Storage symlinks are not accepted");
        }
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (java.nio.file.NoSuchFileException missing) { return FileStamp.missing(); }
        if (!attributes.isRegularFile()) throw new ProtocolState.ProtocolException(
                "PERSISTED_STORAGE_UNAVAILABLE", 409, "Storage target is not a regular file");
        if (attributes.size() > MAX_SOURCE_BYTES) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_BUDGET_EXCEEDED", 413, "Persistent source file exceeds the read budget");
        }
        return new FileStamp(
                true,
                attributes.size(),
                attributes.lastModifiedTime().toMillis(),
                String.valueOf(attributes.fileKey()),
                sha256File(path));
    }

    private FileStamp metadataOnly(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_SYMLINK_UNSUPPORTED", 422, "Storage symlinks are not accepted");
        }
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (java.nio.file.NoSuchFileException missing) { return FileStamp.missing(); }
        if (!attributes.isRegularFile()) throw new ProtocolState.ProtocolException(
                "PERSISTED_STORAGE_UNAVAILABLE", 409, "Storage target is not a regular file");
        if (attributes.size() > MAX_SOURCE_BYTES) {
            throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_BUDGET_EXCEEDED", 413, "Persistent identity file exceeds the read budget");
        }
        return new FileStamp(true, attributes.size(), attributes.lastModifiedTime().toMillis(), String.valueOf(attributes.fileKey()), "");
    }

    private static void ensureStable(FileStamp before, FileStamp after, String name) {
        if (!before.same(after)) {
            throw new ProtocolState.ProtocolException(
                    "PERSISTED_STORAGE_CHANGED_DURING_READ", 409,
                    "Persistent file changed during read: " + name);
        }
    }

    private boolean active(long requestEpoch, CompletableFuture<JsonObject> result) {
        return this.accepting.get()
                && (this.worldAvailable.get() || this.fullyStopped)
                && !this.saving
                && this.lifecycleEpoch.get() == requestEpoch
                && !result.isDone()
                && !Thread.currentThread().isInterrupted();
    }

    private void checkActive(long requestEpoch, CompletableFuture<JsonObject> result) {
        if (!active(requestEpoch, result)) {
            throw new CancellationException("persistent storage lifecycle changed");
        }
    }

    private void cancelPending(String reason) {
        for (CompletableFuture<JsonObject> future : this.pending) future.cancel(false);
        this.worker.getQueue().clear();
        this.contextCapturePending.set(false);
    }

    private static CompletableFuture<JsonObject> failed(String code, int status, String message) {
        return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(code, status, message));
    }

    private static String sha256File(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            MessageDigest digest = digest();
            byte[] buffer = new byte[8_192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SOURCE_BYTES) {
                    throw new ProtocolState.ProtocolException("PERSISTED_STORAGE_BUDGET_EXCEEDED", 413, "Persistent source file exceeds the read budget");
                }
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static String sha256(String value) {
        MessageDigest digest = digest();
        return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() {
        if (!this.accepting.compareAndSet(true, false)) return;
        this.worldAvailable.set(false);
        this.fullyStopped = false;
        this.retainedContext.set(null);
        this.lifecycleEpoch.incrementAndGet();
        this.anchoredWorldIdentity.set(null);
        cancelPending("runtime_shutdown");
        this.worker.shutdownNow();
        try {
            if (!this.worker.awaitTermination(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                this.worker.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.worker.shutdownNow();
        }
    }

    record FileStamp(boolean exists, long size, long modifiedMillis, String fileKey, String sha256) {
        static FileStamp missing() {
            return new FileStamp(false, 0L, 0L, "", "");
        }

        boolean same(FileStamp other) {
            return this.exists == other.exists
                    && this.size == other.size
                    && this.modifiedMillis == other.modifiedMillis
                    && this.fileKey.equals(other.fileKey)
                    && this.sha256.equals(other.sha256);
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maximum;
        private long consumed;

        private BoundedInputStream(InputStream delegate, long maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = this.delegate.read();
            if (value >= 0) account(1L);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int value = this.delegate.read(bytes, offset, length);
            if (value > 0) account(value);
            return value;
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }

        private void account(long amount) throws IOException {
            this.consumed += amount;
            if (this.consumed > this.maximum) throw new ProtocolState.ProtocolException(
                    "PERSISTED_STORAGE_BUDGET_EXCEEDED", 413, "Decoded persisted source exceeded budget");
        }
    }
}
