package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class PersistentWriteSafetyFoundationTest {
    @Test
    void storageIdentityIsNotPathOrSessionOnly() throws Exception {
        Path root = fixtureRoot();
        Path replacedRoot = root.resolveSibling(root.getFileName() + "-replacement");
        try {
            PersistentWriteSafetyFoundation.StorageIdentity first =
                    PersistentWriteSafetyFoundation.StorageIdentity.capture(root, "26.2-neoforge");
            PersistentWriteSafetyFoundation.FileSnapshot beforeLegalUpdate =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(root.resolve("level.dat"));
            assertTrue(first.durableEvidence(), first.identityEvidence());

            Files.write(root.resolve("level.dat"), bytes("legal-level-update"));
            PersistentWriteSafetyFoundation.StorageIdentity legalUpdate =
                    PersistentWriteSafetyFoundation.StorageIdentity.capture(root, "26.2-neoforge");
            PersistentWriteSafetyFoundation.FileSnapshot afterLegalUpdate =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(root.resolve("level.dat"));
            assertEquals(first.identity(), legalUpdate.identity());
            assertNotEquals(beforeLegalUpdate.sha256(), afterLegalUpdate.sha256());
            Files.delete(root.resolve("level.dat"));
            Files.write(root.resolve("level.dat"), bytes("legal-level-replacement"));
            PersistentWriteSafetyFoundation.StorageIdentity legalReplacement =
                    PersistentWriteSafetyFoundation.StorageIdentity.capture(root, "26.2-neoforge");
            assertEquals(first.identity(), legalReplacement.identity());

            Files.move(root, replacedRoot);
            Files.createDirectory(root);
            Files.writeString(root.resolve("session.lock"), "replacement-lock");
            Files.write(root.resolve("level.dat"), bytes("replacement-world"));
            PersistentWriteSafetyFoundation.StorageIdentity replacement =
                    PersistentWriteSafetyFoundation.StorageIdentity.capture(root, "26.2-neoforge");

            assertNotEquals(first.identity(), replacement.identity());
            assertNotEquals(
                    new PersistentWriteSafetyFoundation.StorageVersion(
                            "session-a", first, "world", "level.dat", 1L, first.identity()).identityKey(),
                    new PersistentWriteSafetyFoundation.StorageVersion(
                            "session-b", first, "world", "level.dat", 1L, first.identity()).identityKey());
        } finally {
            deleteTree(root);
            deleteTree(replacedRoot);
        }
    }

    @Test
    void offlineOwnershipIsExclusiveAndRevokedByLifecycle() throws Exception {
        Path root = fixtureRoot();
        try {
            PersistentWriteSafetyFoundation.StorageIdentity identity =
                    PersistentWriteSafetyFoundation.StorageIdentity.capture(root, "26.2-neoforge");
            PersistentWriteSafetyFoundation.LifecycleBarrier first =
                    new PersistentWriteSafetyFoundation.LifecycleBarrier("runtime-a");
            first.markRunning();
            assertCode(
                    "STORAGE_NOT_OFFLINE",
                    () -> first.acquireOffline(identity));
            first.markShuttingDown();
            first.markOffline();
            PersistentWriteSafetyFoundation.Ownership ownership = first.acquireOffline(identity);
            assertTrue(ownership.isActive());
            try (PersistentWriteSafetyFoundation.WritePermit ignored = first.enterWrite(ownership)) {
                assertTrue(ownership.isActive());
            }

            PersistentWriteSafetyFoundation.LifecycleBarrier second =
                    new PersistentWriteSafetyFoundation.LifecycleBarrier("runtime-b");
            second.markOffline();
            assertCode(
                    "STORAGE_OWNERSHIP_CONFLICT",
                    () -> second.acquireOffline(identity));

            first.markSaving();
            assertFalse(ownership.isActive());
            assertCode("STORAGE_OWNERSHIP_INVALID", ownership::requireActive);
            first.markRunning();
            assertFalse(ownership.isActive());
            assertCode("STORAGE_OWNERSHIP_INVALID", ownership::requireActive);
            ownership.close();
            second.close();
            first.close();
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void writePreconditionsRequireStorageWriteAndOfflineOwnership() throws Exception {
        Path root = fixtureRoot();
        try {
            PersistentWriteSafetyFoundation.StorageIdentity identity =
                    PersistentWriteSafetyFoundation.StorageIdentity.capture(root, "26.2-neoforge");
            PersistentWriteSafetyFoundation.FileSnapshot file =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(root.resolve("level.dat"));
            PersistentWriteSafetyFoundation.LifecycleBarrier barrier =
                    new PersistentWriteSafetyFoundation.LifecycleBarrier("runtime-a");
            barrier.markOffline();
            PersistentWriteSafetyFoundation.Ownership ownership = barrier.acquireOffline(identity);
            long deadline = System.currentTimeMillis() + 30_000L;
            PersistentWriteSafetyFoundation.WritePrecondition expected = new PersistentWriteSafetyFoundation.WritePrecondition(
                    identity, "runtime-a", "26.2-neoforge", 3955, file, file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.write", "debug.storage"),
                    "arm-a", deadline, "audit-a", "value-a");
            PersistentWriteSafetyFoundation.WriteContext actual = new PersistentWriteSafetyFoundation.WriteContext(
                    identity, "runtime-a", "26.2-neoforge", 3955, file, file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.write", "debug.storage"),
                    "arm-a", true, PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE,
                    true, "value-a", "audit-a");
            PersistentWriteSafetyFoundation.validateWritePrecondition(expected, actual, ownership, System.currentTimeMillis());

            PersistentWriteSafetyFoundation.WriteContext readOnly = new PersistentWriteSafetyFoundation.WriteContext(
                    identity, "runtime-a", "26.2-neoforge", 3955, file, file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.read"),
                    "arm-a", true, PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE,
                    true, "value-a", "audit-a");
            assertCode(
                    "WRITE_SCOPE_DENIED",
                    () -> PersistentWriteSafetyFoundation.validateWritePrecondition(expected, readOnly, ownership, System.currentTimeMillis()));

            PersistentWriteSafetyFoundation.WriteContext wrongDataVersion = new PersistentWriteSafetyFoundation.WriteContext(
                    identity, "runtime-a", "26.2-neoforge", 3954, file, file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.write", "debug.storage"),
                    "arm-a", true, PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE,
                    true, "value-a", "audit-a");
            assertCode(
                    "DATAVERSION_MISMATCH",
                    () -> PersistentWriteSafetyFoundation.validateWritePrecondition(expected, wrongDataVersion, ownership, System.currentTimeMillis()));

            PersistentWriteSafetyFoundation.WriteContext unarmed = new PersistentWriteSafetyFoundation.WriteContext(
                    identity, "runtime-a", "26.2-neoforge", 3955, file, file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.write", "debug.storage"),
                    "arm-a", false, PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE,
                    true, "value-a", "audit-a");
            assertCode(
                    "DEBUG_NOT_ARMED",
                    () -> PersistentWriteSafetyFoundation.validateWritePrecondition(expected, unarmed, ownership, System.currentTimeMillis()));

            PersistentWriteSafetyFoundation.WriteContext wrongTarget = new PersistentWriteSafetyFoundation.WriteContext(
                    identity, "runtime-a", "1.20.1-forge", 3955, file, file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.write", "debug.storage"),
                    "arm-a", true, PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE,
                    true, "value-a", "audit-a");
            assertCode(
                    "TARGET_MISMATCH",
                    () -> PersistentWriteSafetyFoundation.validateWritePrecondition(expected, wrongTarget, ownership, System.currentTimeMillis()));

            Files.write(root.resolve("level.dat"), bytes("changed-after-read"));
            PersistentWriteSafetyFoundation.WriteContext staleFile = new PersistentWriteSafetyFoundation.WriteContext(
                    identity, "runtime-a", "26.2-neoforge", 3955,
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(root.resolve("level.dat")), file.sha256(),
                    "world", "level.dat", "principal-a", Set.of("storage.write", "debug.storage"),
                    "arm-a", true, PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE,
                    true, "value-a", "audit-a");
            assertCode(
                    "STALE_STORAGE_FILE",
                    () -> PersistentWriteSafetyFoundation.validateWritePrecondition(expected, staleFile, ownership, System.currentTimeMillis()));

            barrier.markShuttingDown();
            assertCode(
                    "STORAGE_OWNERSHIP_INVALID",
                    () -> PersistentWriteSafetyFoundation.validateWritePrecondition(expected, actual, ownership, System.currentTimeMillis()));
            ownership.close();
            barrier.close();
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void atomicReplacementCommitsSyntheticFileAndIsIdempotent() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            byte[] original = bytes("original");
            byte[] replacement = bytes("replacement");
            Files.write(target, original);
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult result =
                    PersistentWriteSafetyFoundation.replace(request(target, replacement, expected, "atomic-success"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.COMMITTED, result.status());
            assertTrue(result.commitPointReached());
            assertFalse(result.backupRetained());
            assertArrayEquals(replacement, Files.readAllBytes(target));

            PersistentWriteSafetyFoundation.AtomicWriteResult stale =
                    PersistentWriteSafetyFoundation.replace(request(target, bytes("second"), expected, "atomic-repeat"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, stale.status());
            assertEquals("STALE_STORAGE_FILE", stale.code());
            assertArrayEquals(replacement, Files.readAllBytes(target));

            PersistentWriteSafetyFoundation.AtomicWriteResult nonAtomic =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("unsafe"),
                            PersistentWriteSafetyFoundation.FileSnapshot.capture(target), null, 1_024L, false, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            PersistentWriteSafetyFoundation.FailureInjector.none(), "non-atomic"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, nonAtomic.status());
            assertEquals("ATOMICITY_POLICY_REQUIRED", nonAtomic.code());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void failuresBeforeCommitLeaveOriginalAndCleanTemporaryFiles() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult failed =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), expected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.TEMP_WRITE) throw new IOException("injected disk full"); },
                            "precommit-failure"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, failed.status());
            assertFalse(failed.commitPointReached());
            assertArrayEquals(bytes("original"), Files.readAllBytes(target));

            PersistentWriteSafetyFoundation.FileSnapshot flushExpected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult flushFailure =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), flushExpected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.FLUSH) throw new IOException("injected fsync failure"); },
                            "flush-failure"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, flushFailure.status());
            assertEquals("WRITE_PRECOMMIT_FAILED", flushFailure.code());
            assertArrayEquals(bytes("original"), Files.readAllBytes(target));

            AtomicBoolean cancelled = new AtomicBoolean(true);
            PersistentWriteSafetyFoundation.AtomicWriteResult cancelledResult =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("cancelled"), expected, null, 1_024L, true, false,
                            cancelled::get, PersistentWriteSafetyFoundation.FailureInjector.none(), "cancelled"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, cancelledResult.status());
            assertEquals("CANCELLED_BEFORE_COMMIT", cancelledResult.code());
            assertArrayEquals(bytes("original"), Files.readAllBytes(target));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void staleRecheckAndReplaceFailureNeverSilentlyCommit() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult stale =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), expected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.PRECOMMIT_RECHECK) Files.write(target, bytes("external")); },
                            "stale-recheck"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, stale.status());
            assertEquals("STALE_STORAGE_FILE", stale.code());
            assertArrayEquals(bytes("external"), Files.readAllBytes(target));

            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot backupExpected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult duringBackup =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), backupExpected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.BACKUP_COPY) Files.write(target, bytes("during-backup")); },
                            "backup-window"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, duringBackup.status());
            assertEquals("STALE_STORAGE_FILE", duringBackup.code());
            assertArrayEquals(bytes("during-backup"), Files.readAllBytes(target));

            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot finalExpected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult beforeFinalCommit =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), finalExpected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.PRECOMMIT) Files.write(target, bytes("before-final-check")); },
                            "final-window"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, beforeFinalCommit.status());
            assertEquals("STALE_STORAGE_FILE", beforeFinalCommit.code());
            assertArrayEquals(bytes("before-final-check"), Files.readAllBytes(target));

            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot commitRaceExpected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult commitRace =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), commitRaceExpected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.REPLACE) Files.write(target, bytes("after-replace-hook")); },
                            "commit-race"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, commitRace.status());
            assertEquals("STALE_STORAGE_FILE", commitRace.code());
            assertArrayEquals(bytes("after-replace-hook"), Files.readAllBytes(target));

            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot reset =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult replaceFailure =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), reset, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.REPLACE) throw new IOException("injected replace failure"); },
                            "replace-failure"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, replaceFailure.status());
            assertArrayEquals(bytes("original"), Files.readAllBytes(target));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void postCommitFailureIsExplicitAndBackupCanRecover() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult result =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), expected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.POSTVERIFY) throw new IOException("injected verify failure"); },
                            "postverify-failure"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.COMMITTED_BUT_POSTVERIFY_FAILED, result.status());
            assertTrue(result.commitPointReached());
            assertTrue(result.backupRetained());
            assertNotNull(result.backupPath());

            PersistentWriteSafetyFoundation.RecoveryResult recovery =
                    PersistentWriteSafetyFoundation.recoverBackup(target, result.backupPath(), result.after(), 1_024L);
            assertEquals("RESTORED", recovery.status());
            assertArrayEquals(bytes("original"), Files.readAllBytes(target));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void cancellationAfterCommitRemainsCommittedAndStaleArtifactsAreBounded() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            AtomicBoolean cancelled = new AtomicBoolean();
            PersistentWriteSafetyFoundation.AtomicWriteResult result =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("replacement"), expected, null, 1_024L, true, false,
                            cancelled::get,
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.POSTVERIFY) cancelled.set(true); },
                            "cancel-after-commit"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.COMMITTED, result.status());
            assertTrue(cancelled.get());

            Path stale = root.resolve("." + target.getFileName() + ".mcp-stale.tmp");
            Files.write(stale, bytes("stale"));
            assertEquals(1, PersistentWriteSafetyFoundation.cleanupStaleArtifacts(root, target.getFileName().toString()));
            assertFalse(Files.exists(stale));

            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot cleanupExpected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            PersistentWriteSafetyFoundation.AtomicWriteResult cleanupFailure =
                    PersistentWriteSafetyFoundation.replace(new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                            target, bytes("cleanup"), cleanupExpected, null, 1_024L, true, false,
                            PersistentWriteSafetyFoundation.Cancellation.never(),
                            point -> { if (point == PersistentWriteSafetyFoundation.FailurePoint.CLEANUP) throw new IOException("injected cleanup failure"); },
                            "cleanup-failure"));
            assertEquals(PersistentWriteSafetyFoundation.CommitStatus.COMMITTED, cleanupFailure.status());
            assertTrue(cleanupFailure.cleanupFailed());
            assertTrue(cleanupFailure.backupRetained());
            assertTrue(Files.exists(cleanupFailure.backupPath()));
            assertEquals("RESTORED", PersistentWriteSafetyFoundation.recoverBackup(
                    target, cleanupFailure.backupPath(), cleanupFailure.after(), 1_024L).status());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void openHandleProducesOnlyAnExplicitOutcome() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            try (FileChannel ignored = FileChannel.open(target, StandardOpenOption.READ)) {
                PersistentWriteSafetyFoundation.AtomicWriteResult result =
                        PersistentWriteSafetyFoundation.replace(request(target, bytes("replacement"), expected, "open-handle"));
                assertTrue(result.status() == PersistentWriteSafetyFoundation.CommitStatus.COMMITTED
                        || result.status() == PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED
                        || result.status() == PersistentWriteSafetyFoundation.CommitStatus.RECOVERY_REQUIRED
                        || result.status() == PersistentWriteSafetyFoundation.CommitStatus.COMMITTED_BUT_POSTVERIFY_FAILED);
                if (!result.commitPointReached()) assertArrayEquals(bytes("original"), Files.readAllBytes(target));
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void externalSessionLockCompetitionIsRejectedBeforeWrite() throws Exception {
        Path root = fixtureRoot();
        try {
            Path target = root.resolve("level.dat");
            Files.write(target, bytes("original"));
            PersistentWriteSafetyFoundation.FileSnapshot expected =
                    PersistentWriteSafetyFoundation.FileSnapshot.capture(target);
            try (FileChannel channel = FileChannel.open(root.resolve("session.lock"), StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                PersistentWriteSafetyFoundation.AtomicWriteResult result =
                        PersistentWriteSafetyFoundation.replace(request(target, bytes("replacement"), expected, "external-lock"));
                assertEquals(PersistentWriteSafetyFoundation.CommitStatus.NOT_COMMITTED, result.status());
                assertEquals("STORAGE_OWNERSHIP_LOCK_UNAVAILABLE", result.code());
                assertArrayEquals(bytes("original"), Files.readAllBytes(target));
            }
        } finally {
            deleteTree(root);
        }
    }

    private static PersistentWriteSafetyFoundation.AtomicWriteRequest request(
            Path target,
            byte[] content,
            PersistentWriteSafetyFoundation.FileSnapshot expected,
            String operationId) {
        return new PersistentWriteSafetyFoundation.AtomicWriteRequest(
                target, content, expected, null, 1_024L, true, false,
                PersistentWriteSafetyFoundation.Cancellation.never(),
                PersistentWriteSafetyFoundation.FailureInjector.none(), operationId);
    }

    private static Path fixtureRoot() throws IOException {
        Path root = Files.createTempDirectory("mcp-write-safety-");
        Files.writeString(root.resolve("session.lock"), "runtime-lock");
        Files.write(root.resolve("level.dat"), bytes("world"));
        return root;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void assertCode(String expected, ThrowingAction action) {
        PersistentWriteSafetyFoundation.SafetyFailure failure = assertThrows(
                PersistentWriteSafetyFoundation.SafetyFailure.class, action::run);
        assertEquals(expected, failure.code());
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }
}
