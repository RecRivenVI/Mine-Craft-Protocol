package io.github.recrivenvi.minecraftprotocol.safety;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Safety-only contracts for a future typed, offline Persistent Write plane. */
public final class PersistentWriteSafetyFoundation {
    public static final int CONTRACT_VERSION = 1;
    public static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;

    private PersistentWriteSafetyFoundation() {
    }

    public enum LifecycleState {
        UNKNOWN, WORLD_RUNNING, SAVING, UNLOADING, SHUTTING_DOWN, STOPPED_OFFLINE, CLOSED
    }

    public enum CommitStatus {
        NOT_COMMITTED, COMMITTED, COMMITTED_BUT_POSTVERIFY_FAILED, RECOVERY_REQUIRED
    }

    public enum FailurePoint {
        TEMP_WRITE, FLUSH, PRECOMMIT_RECHECK, BACKUP_COPY, AFTER_BACKUP_RECHECK,
        PRECOMMIT, FINAL_RECHECK, REPLACE, POSTVERIFY, CLEANUP
    }

    @FunctionalInterface
    public interface Cancellation {
        boolean requested();

        static Cancellation never() {
            return () -> false;
        }
    }

    @FunctionalInterface
    public interface FailureInjector {
        void before(FailurePoint point) throws IOException;

        static FailureInjector none() {
            return point -> {
            };
        }
    }

    public static final class SafetyFailure extends Exception {
        private final String code;

        public SafetyFailure(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return this.code;
        }
    }

    public record FileSnapshot(
            boolean exists,
            long size,
            long modifiedMillis,
            long createdMillis,
            String fileKey,
            String sha256) {
        public static FileSnapshot capture(Path path) throws IOException, SafetyFailure {
            return capture(path, MAX_FILE_BYTES);
        }

        public static FileSnapshot capture(Path path, long maxBytes) throws IOException, SafetyFailure {
            if (path == null || maxBytes < 1L) throw new SafetyFailure("INVALID_FILE_SNAPSHOT", "Invalid file snapshot request");
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)) throw new SafetyFailure("SYMLINK_UNSUPPORTED", "Storage symlinks are not accepted");
            if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) return new FileSnapshot(false, 0L, 0L, 0L, "", "");
            BasicFileAttributes attributes = Files.readAttributes(normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.size() > maxBytes) throw new SafetyFailure("FILE_BUDGET_EXCEEDED", "File exceeds the bounded safety budget");
            MessageDigest digest = digest();
            long total = 0L;
            byte[] buffer = new byte[8_192];
            try (InputStream input = Files.newInputStream(normalized, StandardOpenOption.READ)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total = Math.addExact(total, read);
                    if (total > maxBytes) throw new SafetyFailure("FILE_BUDGET_EXCEEDED", "File changed beyond the bounded safety budget");
                    digest.update(buffer, 0, read);
                }
            }
            return new FileSnapshot(true, attributes.size(), attributes.lastModifiedTime().toMillis(),
                    attributes.creationTime().toMillis(), String.valueOf(attributes.fileKey()),
                    HexFormat.of().formatHex(digest.digest()));
        }

        public boolean same(FileSnapshot other) {
            return other != null && this.exists == other.exists && this.size == other.size
                    && this.modifiedMillis == other.modifiedMillis && this.createdMillis == other.createdMillis
                    && this.fileKey.equals(other.fileKey) && this.sha256.equals(other.sha256);
        }
    }

    public record StorageIdentity(
            String identity,
            String target,
            String rootPath,
            String rootFileKey,
            String levelFileKey,
            String lockFileKey,
            long rootCreatedMillis,
            long levelCreatedMillis,
            long lockCreatedMillis,
            String identityEvidence,
            boolean durableEvidence) {
        public static StorageIdentity capture(Path worldRoot, String target) throws IOException, SafetyFailure {
            if (worldRoot == null || target == null || target.isBlank()) throw new SafetyFailure("STORAGE_IDENTITY_INVALID", "World root and target are required");
            Path root = worldRoot.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new SafetyFailure("STORAGE_IDENTITY_INVALID", "World root must be a real directory");
            BasicFileAttributes rootAttrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            FileSnapshot level = FileSnapshot.capture(root.resolve("level.dat"));
            FileSnapshot lock = FileSnapshot.capture(root.resolve("session.lock"));
            if (!level.exists() || !lock.exists()) throw new SafetyFailure("STORAGE_IDENTITY_UNAVAILABLE", "level.dat and session.lock are required");
            String rootKey = String.valueOf(rootAttrs.fileKey());
            long rootCreated = rootAttrs.creationTime().toMillis();
            // The world directory is the stable lineage anchor. level.dat and session.lock
            // are intentionally excluded from the identity because both may be replaced or
            // rewritten during a normal save/runtime session. Their FileSnapshot values are
            // still used as mutable write preconditions.
            boolean durable = !"null".equals(rootKey) || rootCreated > 0L;
            String evidence = "rootFileKey=" + rootKey + ";levelFileKey=" + level.fileKey()
                    + ";lockFileKey=" + lock.fileKey() + ";rootCreatedMillis=" + rootCreated
                    + ";levelCreatedMillis=" + level.createdMillis() + ";lockCreatedMillis=" + lock.createdMillis()
                    + ";identityBasis=root_directory_lineage";
            String material = "persistent-storage-v" + CONTRACT_VERSION + "|target=" + target + "|root=" + root
                    + "|rootFileKey=" + rootKey + "|rootCreatedMillis=" + rootCreated
                    + "|identityBasis=root_directory_lineage";
            return new StorageIdentity(sha256(material), target, root.toString(), rootKey, level.fileKey(), lock.fileKey(),
                    rootCreated, level.createdMillis(), lock.createdMillis(), evidence, durable);
        }

        public boolean same(StorageIdentity other) {
            return other != null && this.identity.equals(other.identity);
        }

        public void requireDurable() throws SafetyFailure {
            if (!this.durableEvidence) throw new SafetyFailure("STORAGE_IDENTITY_NOT_DURABLE", "Stable filesystem identity evidence is unavailable");
        }
    }

    public record StorageVersion(
            String runtimeSessionEpoch,
            StorageIdentity storageIdentity,
            String resourceType,
            String resourceKey,
            long revision,
            String contentSha256) {
        public StorageVersion {
            if (runtimeSessionEpoch == null || runtimeSessionEpoch.isBlank() || storageIdentity == null
                    || resourceType == null || resourceType.isBlank() || resourceKey == null || resourceKey.isBlank()
                    || revision < 0L || contentSha256 == null || contentSha256.isBlank()) throw new IllegalArgumentException("Incomplete storage version");
        }

        public String identityKey() {
            return runtimeSessionEpoch + "|" + storageIdentity.identity() + "|" + resourceType + "|" + resourceKey + "|" + revision;
        }
    }

    public static final class LifecycleBarrier implements AutoCloseable {
        private static final ConcurrentHashMap<String, Ownership> GLOBAL_OWNERS = new ConcurrentHashMap<>();
        private final String runtimeSessionEpoch;
        private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.UNKNOWN);
        private final AtomicLong generation = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ReentrantLock operationLock = new ReentrantLock(true);
        private Ownership localOwnership;

        public LifecycleBarrier(String runtimeSessionEpoch) {
            if (runtimeSessionEpoch == null || runtimeSessionEpoch.isBlank()) throw new IllegalArgumentException("runtimeSessionEpoch is required");
            this.runtimeSessionEpoch = runtimeSessionEpoch;
        }

        public synchronized void markRunning() { transition(LifecycleState.WORLD_RUNNING); }
        public synchronized void markSaving() { transition(LifecycleState.SAVING); }
        public synchronized void markUnloading() { transition(LifecycleState.UNLOADING); }
        public synchronized void markShuttingDown() { transition(LifecycleState.SHUTTING_DOWN); }
        public synchronized void markOffline() {
            LifecycleState current = this.state.get();
            if (current == LifecycleState.WORLD_RUNNING || current == LifecycleState.SAVING) throw new IllegalStateException("Running world must unload before offline ownership");
            transition(LifecycleState.STOPPED_OFFLINE);
        }

        public synchronized LifecycleSnapshot snapshot(StorageIdentity identity) {
            return new LifecycleSnapshot(this.runtimeSessionEpoch, this.state.get(), this.generation.get(), identity, this.localOwnership != null && this.localOwnership.isActive());
        }

        public synchronized Ownership acquireOffline(StorageIdentity identity) throws SafetyFailure {
            if (this.closed.get() || this.state.get() != LifecycleState.STOPPED_OFFLINE) throw new SafetyFailure("STORAGE_NOT_OFFLINE", "Storage is not fully stopped");
            if (identity == null) throw new SafetyFailure("STORAGE_IDENTITY_REQUIRED", "Storage identity is required");
            identity.requireDurable();
            if (this.localOwnership != null && this.localOwnership.isActive()) throw new SafetyFailure("STORAGE_OWNERSHIP_DUPLICATE", "Runtime already owns storage");
            Ownership candidate = new Ownership(this, identity, this.runtimeSessionEpoch, this.generation.get());
            Ownership previous = GLOBAL_OWNERS.putIfAbsent(identity.identity(), candidate);
            if (previous != null) {
                if (previous.isActive()) throw new SafetyFailure("STORAGE_OWNERSHIP_CONFLICT", "Another Runtime owns storage");
                GLOBAL_OWNERS.remove(identity.identity(), previous);
                if (GLOBAL_OWNERS.putIfAbsent(identity.identity(), candidate) != null) throw new SafetyFailure("STORAGE_OWNERSHIP_CONFLICT", "Another Runtime owns storage");
            }
            try { candidate.acquireFileLock(); }
            catch (Exception exception) {
                GLOBAL_OWNERS.remove(identity.identity(), candidate);
                throw new SafetyFailure("STORAGE_LOCK_UNAVAILABLE", "Persistent storage lock could not be acquired");
            }
            this.localOwnership = candidate;
            return candidate;
        }

        public WritePermit enterWrite(Ownership ownership) throws SafetyFailure {
            this.operationLock.lock();
            try {
                if (ownership == null) throw new SafetyFailure("STORAGE_OWNERSHIP_REQUIRED", "Active ownership is required");
                ownership.requireActive();
                return new WritePermit(this);
            } catch (SafetyFailure failure) { this.operationLock.unlock(); throw failure; }
        }

        public LifecycleState state() { return this.state.get(); }
        public long generation() { return this.generation.get(); }

        private void transition(LifecycleState next) {
            this.operationLock.lock();
            try {
                if (this.closed.get()) return;
                if (this.state.getAndSet(next) != next) this.generation.incrementAndGet();
                if (next != LifecycleState.STOPPED_OFFLINE) revokeLocalOwnership();
            } finally { this.operationLock.unlock(); }
        }

        private void revokeLocalOwnership() {
            Ownership ownership = this.localOwnership;
            this.localOwnership = null;
            if (ownership != null) {
                GLOBAL_OWNERS.remove(ownership.storageIdentity().identity(), ownership);
                ownership.releaseFileLock();
                ownership.revoked.set(true);
            }
        }

        private synchronized void release(Ownership ownership) {
            this.operationLock.lock();
            try {
                if (this.localOwnership == ownership) this.localOwnership = null;
                GLOBAL_OWNERS.remove(ownership.storageIdentity().identity(), ownership);
                ownership.releaseFileLock();
                ownership.revoked.set(true);
            } finally { this.operationLock.unlock(); }
        }

        @Override public synchronized void close() {
            this.operationLock.lock();
            try {
                if (!this.closed.compareAndSet(false, true)) return;
                this.state.set(LifecycleState.CLOSED);
                this.generation.incrementAndGet();
                revokeLocalOwnership();
            } finally { this.operationLock.unlock(); }
        }
    }

    public record LifecycleSnapshot(String runtimeSessionEpoch, LifecycleState state, long generation, StorageIdentity storageIdentity, boolean ownershipActive) { }

    public static final class WritePermit implements AutoCloseable {
        private final LifecycleBarrier barrier;
        private final AtomicBoolean closed = new AtomicBoolean();
        private WritePermit(LifecycleBarrier barrier) { this.barrier = barrier; }
        @Override public void close() { if (this.closed.compareAndSet(false, true)) this.barrier.operationLock.unlock(); }
    }

    public static final class Ownership implements AutoCloseable {
        private final LifecycleBarrier barrier;
        private final StorageIdentity storageIdentity;
        private final String runtimeSessionEpoch;
        private final long generation;
        private final AtomicBoolean revoked = new AtomicBoolean();
        private FileChannel lockChannel;
        private FileLock fileLock;
        private Ownership(LifecycleBarrier barrier, StorageIdentity identity, String epoch, long generation) { this.barrier = barrier; this.storageIdentity = identity; this.runtimeSessionEpoch = epoch; this.generation = generation; }
        public StorageIdentity storageIdentity() { return this.storageIdentity; }
        public boolean isActive() { return !this.revoked.get() && this.barrier.state() == LifecycleState.STOPPED_OFFLINE && this.barrier.generation() == this.generation && this.fileLock != null && this.fileLock.isValid(); }
        public void requireActive() throws SafetyFailure { if (!isActive()) throw new SafetyFailure("STORAGE_OWNERSHIP_INVALID", "Offline ownership is no longer active"); }
        public String runtimeSessionEpoch() { return this.runtimeSessionEpoch; }
        public FileLock fileLock() { return this.fileLock; }
        private synchronized void acquireFileLock() throws IOException {
            Path path = Path.of(this.storageIdentity.rootPath()).resolve("session.lock");
            this.lockChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
            try { this.fileLock = this.lockChannel.tryLock(); if (this.fileLock == null) throw new IOException("lock held"); }
            catch (java.nio.channels.OverlappingFileLockException exception) { throw new IOException("lock held", exception); }
            catch (IOException exception) { releaseFileLock(); throw exception; }
        }
        private synchronized void releaseFileLock() {
            try { if (this.fileLock != null) this.fileLock.release(); } catch (IOException ignored) { }
            finally { this.fileLock = null; try { if (this.lockChannel != null) this.lockChannel.close(); } catch (IOException ignored) { } this.lockChannel = null; }
        }
        @Override public void close() { this.barrier.release(this); }
    }

    public record WritePrecondition(StorageIdentity expectedStorageIdentity, String runtimeSessionEpoch, String target, int expectedDataVersion, FileSnapshot expectedFile, String expectedContentRevision, String resourceType, String resourceKey, String principalId, Set<String> scopes, String debugArmId, long deadlineEpochMillis, String auditCorrelation, String expectedValueDigest) {
        public WritePrecondition { scopes = scopes == null ? Set.of() : Set.copyOf(scopes); }
    }

    public record WriteContext(StorageIdentity storageIdentity, String runtimeSessionEpoch, String target, int dataVersion, FileSnapshot file, String contentRevision, String resourceType, String resourceKey, String principalId, Set<String> scopes, String debugArmId, boolean debugArmValid, LifecycleState lifecycleState, boolean ownershipActive, String valueDigest, String auditCorrelation) {
        public WriteContext { scopes = scopes == null ? Set.of() : Set.copyOf(scopes); }
    }

    public static void validateWritePrecondition(WritePrecondition expected, WriteContext actual, Ownership ownership, long nowEpochMillis) throws SafetyFailure {
        if (expected == null || actual == null) throw new SafetyFailure("WRITE_PRECONDITION_INVALID", "Precondition and current context are required");
        if (expected.expectedStorageIdentity() == null || actual.storageIdentity() == null || !expected.expectedStorageIdentity().same(actual.storageIdentity())) throw new SafetyFailure("STORAGE_IDENTITY_MISMATCH", "Storage identity mismatch");
        expected.expectedStorageIdentity().requireDurable();
        if (!same(expected.runtimeSessionEpoch(), actual.runtimeSessionEpoch())) throw new SafetyFailure("STALE_RUNTIME_SESSION", "Runtime session mismatch");
        if (!same(expected.target(), actual.target())) throw new SafetyFailure("TARGET_MISMATCH", "Target mismatch");
        if (expected.expectedDataVersion() < 0 || expected.expectedDataVersion() != actual.dataVersion()) throw new SafetyFailure("DATAVERSION_MISMATCH", "Unsupported or stale DataVersion");
        if (expected.expectedFile() == null || !expected.expectedFile().same(actual.file())) throw new SafetyFailure("STALE_STORAGE_FILE", "Storage file changed");
        if (!same(expected.expectedContentRevision(), actual.contentRevision())) throw new SafetyFailure("STALE_CONTENT_REVISION", "Content revision mismatch");
        if (!same(expected.resourceType(), actual.resourceType()) || !same(expected.resourceKey(), actual.resourceKey())) throw new SafetyFailure("RESOURCE_MISMATCH", "Persisted resource mismatch");
        if (expected.principalId() == null || expected.principalId().isBlank() || !same(expected.principalId(), actual.principalId())) throw new SafetyFailure("PRINCIPAL_MISMATCH", "Principal mismatch");
        if (!actual.scopes().contains("storage.write")) throw new SafetyFailure("WRITE_SCOPE_DENIED", "storage.read does not grant storage.write");
        if (!actual.scopes().contains("debug.storage")) throw new SafetyFailure("DEBUG_STORAGE_SCOPE_DENIED", "debug.storage is required");
        if (expected.debugArmId() == null || expected.debugArmId().isBlank() || !same(expected.debugArmId(), actual.debugArmId()) || !actual.debugArmValid()) throw new SafetyFailure("DEBUG_NOT_ARMED", "Storage Debug Arm is required");
        if (expected.auditCorrelation() == null || expected.auditCorrelation().isBlank() || !same(expected.auditCorrelation(), actual.auditCorrelation())) throw new SafetyFailure("AUDIT_CORRELATION_REQUIRED", "Audit correlation is required");
        if (expected.deadlineEpochMillis() <= 0L || nowEpochMillis > expected.deadlineEpochMillis()) throw new SafetyFailure("WRITE_DEADLINE_EXPIRED", "Write deadline expired");
        if (actual.lifecycleState() != LifecycleState.STOPPED_OFFLINE || !actual.ownershipActive()) throw new SafetyFailure("STORAGE_NOT_OFFLINE", "Storage is not offline");
        if (ownership == null) throw new SafetyFailure("STORAGE_OWNERSHIP_REQUIRED", "Active ownership is required");
        ownership.requireActive();
        if (expected.expectedValueDigest() != null && !same(expected.expectedValueDigest(), actual.valueDigest())) throw new SafetyFailure("VALUE_PRECONDITION_FAILED", "Value precondition mismatch");
    }

    public record AtomicWriteRequest(Path target, byte[] content, FileSnapshot expectedBefore, String expectedContentSha256, long maxBytes, boolean requireAtomicMove, boolean requireDirectoryForce, Cancellation cancellation, FailureInjector failureInjector, String operationId, Path ownershipLockPath, FileLock heldOwnershipLock, boolean requireOwnershipLock) {
        public AtomicWriteRequest(Path target, byte[] content, FileSnapshot expectedBefore, String expectedContentSha256, long maxBytes, boolean requireAtomicMove, boolean requireDirectoryForce, Cancellation cancellation, FailureInjector failureInjector, String operationId) {
            this(target, content, expectedBefore, expectedContentSha256, maxBytes, requireAtomicMove, requireDirectoryForce,
                    cancellation, failureInjector, operationId, null, null, true);
        }

        public AtomicWriteRequest { content = content == null ? new byte[0] : content.clone(); maxBytes = maxBytes < 1L ? MAX_FILE_BYTES : Math.min(maxBytes, MAX_FILE_BYTES); cancellation = cancellation == null ? Cancellation.never() : cancellation; failureInjector = failureInjector == null ? FailureInjector.none() : failureInjector; operationId = operationId == null || operationId.isBlank() ? UUID.randomUUID().toString() : operationId; if (expectedContentSha256 == null || expectedContentSha256.isBlank()) expectedContentSha256 = sha256(content); }
        @Override public byte[] content() { return this.content.clone(); }
    }

    public record AtomicWriteResult(CommitStatus status, FileSnapshot before, FileSnapshot after, Path temporaryPath, Path backupPath, boolean commitPointReached, boolean backupRetained, boolean cleanupFailed, String code, String message) { }

    public static AtomicWriteResult replace(AtomicWriteRequest request) {
        if (request == null || request.target() == null || request.expectedBefore() == null) {
            return result(CommitStatus.NOT_COMMITTED, null, null, null, null, false, false, false,
                    "WRITE_REQUEST_INVALID", "Target and expected snapshot are required");
        }
        Path target = request.target().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            return result(CommitStatus.NOT_COMMITTED, null, null, null, null, false, false, false,
                    "WRITE_PARENT_INVALID", "Target parent is invalid");
        }
        if (!request.requireAtomicMove()) {
            return result(CommitStatus.NOT_COMMITTED, null, null, null, null, false, false, false,
                    "ATOMICITY_POLICY_REQUIRED", "Non-atomic replacement is prohibited");
        }
        LockHandle ownershipLock;
        try {
            ownershipLock = acquireOwnershipLock(request, target);
        } catch (IOException exception) {
            return result(CommitStatus.NOT_COMMITTED, null, null, null, null, false, false, false,
                    "STORAGE_OWNERSHIP_LOCK_UNAVAILABLE", exception.getMessage());
        }
        if (request.requireOwnershipLock() && ownershipLock == null) {
            return result(CommitStatus.NOT_COMMITTED, null, null, null, null, false, false, false,
                    "STORAGE_OWNERSHIP_LOCK_REQUIRED", "An exclusive storage ownership lock is required");
        }
        try (LockHandle ignored = ownershipLock) {
            return replaceLocked(request, target, parent);
        }
    }

    private static AtomicWriteResult replaceLocked(AtomicWriteRequest request, Path target, Path parent) {
        FileSnapshot before;
        try {
            before = FileSnapshot.capture(target, request.maxBytes());
        } catch (Exception exception) {
            return result(CommitStatus.NOT_COMMITTED, null, null, null, null, false, false, false,
                    code(exception, "WRITE_SNAPSHOT_FAILED"), exception.getMessage());
        }
        if (!before.same(request.expectedBefore())) return result(CommitStatus.NOT_COMMITTED, before, before, null, null, false, false, false, "STALE_STORAGE_FILE", "Target changed before write");
        if (request.content().length > request.maxBytes()) return result(CommitStatus.NOT_COMMITTED, before, before, null, null, false, false, false, "WRITE_BUDGET_EXCEEDED", "Replacement exceeds budget");
        if (cancelled(request)) return result(CommitStatus.NOT_COMMITTED, before, before, null, null, false, false, false, "CANCELLED_BEFORE_COMMIT", "Cancelled before commit");
        Path temporary = null;
        Path backup = null;
        try {
            temporary = Files.createTempFile(parent, "." + target.getFileName() + ".mcp-", ".tmp");
            request.failureInjector().before(FailurePoint.TEMP_WRITE);
            writeDurably(temporary, request.content(), request.maxBytes(), request.failureInjector());
            if (cancelled(request)) return cleanupBeforeCommit(before, temporary, backup, "CANCELLED_BEFORE_COMMIT", "Cancelled before recheck");
            request.failureInjector().before(FailurePoint.PRECOMMIT_RECHECK);
            FileSnapshot rechecked = FileSnapshot.capture(target, request.maxBytes());
            if (!before.same(rechecked)) return cleanupBeforeCommit(before, temporary, backup, "STALE_STORAGE_FILE", "Target changed before backup");
            if (before.exists()) {
                backup = parent.resolve(target.getFileName() + ".mcp-backup-" + request.operationId());
                request.failureInjector().before(FailurePoint.BACKUP_COPY);
                copyDurably(target, backup, request.maxBytes());
            }
            request.failureInjector().before(FailurePoint.AFTER_BACKUP_RECHECK);
            FileSnapshot afterBackup = FileSnapshot.capture(target, request.maxBytes());
            if (!before.same(afterBackup)) return cleanupBeforeCommit(before, temporary, backup, "STALE_STORAGE_FILE", "Target changed during backup");
            if (cancelled(request)) return cleanupBeforeCommit(before, temporary, backup, "CANCELLED_BEFORE_COMMIT", "Cancelled before final check");
            request.failureInjector().before(FailurePoint.PRECOMMIT);
            if (cancelled(request)) return cleanupBeforeCommit(before, temporary, backup, "CANCELLED_BEFORE_COMMIT", "Cancelled at commit boundary");
            request.failureInjector().before(FailurePoint.REPLACE);
            request.failureInjector().before(FailurePoint.FINAL_RECHECK);
            FileSnapshot finalCheck = FileSnapshot.capture(target, request.maxBytes());
            if (!before.same(finalCheck)) return cleanupBeforeCommit(before, temporary, backup, "STALE_STORAGE_FILE", "Target changed before final commit");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                boolean cleanup = !delete(temporary) | !delete(backup);
                return result(CommitStatus.NOT_COMMITTED, before, before, temporary, backup, false, false, cleanup, "ATOMIC_REPLACE_UNAVAILABLE", exception.getMessage());
            } catch (IOException exception) {
                FileSnapshot observed = safeSnapshot(target, request.maxBytes());
                boolean unchanged = observed != null && before.same(observed);
                return result(unchanged ? CommitStatus.NOT_COMMITTED : CommitStatus.RECOVERY_REQUIRED, before, observed, temporary, backup, false, backup != null, !delete(temporary), unchanged ? "REPLACE_FAILED" : "REPLACE_OUTCOME_UNKNOWN", exception.getMessage());
            }
            FileSnapshot after = safeSnapshot(target, request.maxBytes());
            try {
                request.failureInjector().before(FailurePoint.POSTVERIFY);
                if (after == null || !after.exists() || !request.expectedContentSha256().equals(after.sha256())) return result(CommitStatus.COMMITTED_BUT_POSTVERIFY_FAILED, before, after, temporary, backup, true, backup != null, false, "POSTVERIFY_FAILED", "Committed bytes did not verify");
                if (request.requireDirectoryForce() && !forceDirectory(parent)) return result(CommitStatus.COMMITTED_BUT_POSTVERIFY_FAILED, before, after, temporary, backup, true, backup != null, false, "DIRECTORY_DURABILITY_UNVERIFIED", "Directory durability could not be proven");
            } catch (Exception exception) {
                return result(CommitStatus.COMMITTED_BUT_POSTVERIFY_FAILED, before, after, temporary, backup, true, backup != null, false, code(exception, "POSTVERIFY_FAILED"), exception.getMessage());
            }
            boolean cleanupFailed;
            try { request.failureInjector().before(FailurePoint.CLEANUP); cleanupFailed = !delete(backup); } catch (IOException exception) { cleanupFailed = true; }
            boolean retained = backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
            return result(CommitStatus.COMMITTED, before, after, temporary, backup, true, retained, cleanupFailed, cleanupFailed ? "CLEANUP_FAILED" : "", cleanupFailed ? "Backup cleanup failed" : "");
        } catch (Exception exception) {
            return result(CommitStatus.NOT_COMMITTED, before, safeSnapshot(target, request.maxBytes()), temporary, backup, false, backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS), !delete(temporary) | !delete(backup), code(exception, "WRITE_PRECOMMIT_FAILED"), exception.getMessage());
        }
    }

    private static LockHandle acquireOwnershipLock(AtomicWriteRequest request, Path target) throws IOException {
        if (!request.requireOwnershipLock()) return null;
        if (request.heldOwnershipLock() != null) {
            if (!request.heldOwnershipLock().isValid()) throw new IOException("ownership lock is invalid");
            return LockHandle.held(request.heldOwnershipLock());
        }
        Path lockPath = request.ownershipLockPath() != null ? request.ownershipLockPath().toAbsolutePath().normalize() : target.getParent().resolve("session.lock");
        if (Files.isSymbolicLink(lockPath) || !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) throw new IOException("ownership lock file is unavailable");
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("ownership lock is held");
            return LockHandle.owned(channel, lock);
        } catch (java.nio.channels.OverlappingFileLockException exception) {
            try { channel.close(); } catch (IOException ignored) { }
            throw new IOException("ownership lock is held", exception);
        } catch (IOException exception) {
            try { channel.close(); } catch (IOException ignored) { }
            throw exception;
        }
    }

    private static final class LockHandle implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private LockHandle(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        private static LockHandle owned(FileChannel channel, FileLock lock) { return new LockHandle(channel, lock); }
        private static LockHandle held(FileLock lock) { return new LockHandle(null, lock); }
        @Override public void close() {
            if (this.channel == null) return;
            try { this.lock.release(); } catch (IOException ignored) { }
            try { this.channel.close(); } catch (IOException ignored) { }
        }
    }

    public record RecoveryResult(String status, FileSnapshot restored, String code, String message) { }

    public static RecoveryResult recoverBackup(Path target, Path backup, FileSnapshot expectedCurrent, long maxBytes) {
        try {
            if (target == null || backup == null || !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) return new RecoveryResult("NO_BACKUP", null, "NO_BACKUP", "No recovery backup exists");
            FileSnapshot current = FileSnapshot.capture(target, maxBytes);
            if (expectedCurrent != null && !expectedCurrent.same(current)) return new RecoveryResult("REFUSED_STALE", current, "RECOVERY_TARGET_CHANGED", "Target changed after commit");
            Files.move(backup.toAbsolutePath().normalize(), target.toAbsolutePath().normalize(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            FileSnapshot restored = FileSnapshot.capture(target, maxBytes);
            return new RecoveryResult("RESTORED", restored, "", "Backup restored and verified");
        } catch (Exception exception) { return new RecoveryResult("RECOVERY_REQUIRED", null, code(exception, "RECOVERY_FAILED"), exception.getMessage()); }
    }

    public static int cleanupStaleArtifacts(Path directory, String targetFileName) throws IOException {
        if (directory == null || targetFileName == null || targetFileName.isBlank()) return 0;
        int deleted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "." + targetFileName + ".mcp-*.tmp")) { for (Path entry : entries) if (delete(entry)) deleted++; }
        return deleted;
    }

    private static AtomicWriteResult cleanupBeforeCommit(FileSnapshot before, Path temporary, Path backup, String code, String message) { boolean cleanup = !delete(temporary) | !delete(backup); return result(CommitStatus.NOT_COMMITTED, before, before, temporary, backup, false, false, cleanup, code, message); }
    private static void writeDurably(Path path, byte[] content, long maxBytes, FailureInjector injector) throws IOException, SafetyFailure { if (content.length > maxBytes) throw new SafetyFailure("WRITE_BUDGET_EXCEEDED", "Replacement exceeds budget"); try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(content); while (buffer.hasRemaining()) channel.write(buffer); injector.before(FailurePoint.FLUSH); channel.force(true); } }
    private static void copyDurably(Path source, Path destination, long maxBytes) throws IOException, SafetyFailure { FileSnapshot snapshot = FileSnapshot.capture(source, maxBytes); try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ); FileChannel output = FileChannel.open(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { long position = 0L; while (position < snapshot.size()) { long moved = input.transferTo(position, snapshot.size() - position, output); if (moved <= 0L) throw new IOException("backup copy made no progress"); position += moved; } output.force(true); } }
    private static boolean forceDirectory(Path directory) { try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); return true; } catch (Exception exception) { return false; } }
    private static boolean delete(Path path) { if (path == null) return true; try { return Files.deleteIfExists(path) || !Files.exists(path, LinkOption.NOFOLLOW_LINKS); } catch (IOException exception) { return false; } }
    private static FileSnapshot safeSnapshot(Path path, long maxBytes) { try { return FileSnapshot.capture(path, maxBytes); } catch (Exception exception) { return null; } }
    private static boolean cancelled(AtomicWriteRequest request) { return request.cancellation().requested() || Thread.currentThread().isInterrupted(); }
    private static AtomicWriteResult result(CommitStatus status, FileSnapshot before, FileSnapshot after, Path temporary, Path backup, boolean commit, boolean retained, boolean cleanup, String code, String message) { return new AtomicWriteResult(status, before, after, temporary, backup, commit, retained, cleanup, code == null ? "" : code, message == null ? "" : message); }
    private static String code(Exception exception, String fallback) { return exception instanceof SafetyFailure failure ? failure.code() : fallback; }
    private static boolean same(String left, String right) { return left == null ? right == null : left.equals(right); }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    public static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static String sha256(String value) { return HexFormat.of().formatHex(digest().digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
}
