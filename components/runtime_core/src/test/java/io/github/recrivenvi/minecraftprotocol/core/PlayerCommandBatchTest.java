package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlayerCommandBatchTest {
    @Test
    void wholeBatchTextValidationHappensBeforeAnySubmission() {
        AtomicInteger calls = new AtomicInteger();
        for (var invalid :
                List.of(
                        List.of("help", "stop\nhelp"),
                        List.of("help", ""),
                        java.util.Collections.nCopies(33, "help"),
                        java.util.Collections.nCopies(32, "x".repeat(2048)))) {
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () ->
                            PlayerCommandBatch.execute(
                                    invalid,
                                    "request",
                                    () -> {},
                                    (index, command, guard) -> {
                                        calls.incrementAndGet();
                                        return CompletableFuture.completedFuture(new JsonObject());
                                    }));
        }
        assertEquals(0, calls.get());
        assertEquals(
                List.of("help", "time query daytime"),
                PlayerCommandBatch.normalize(List.of(" /help ", "time query daytime")));
    }

    @Test
    void waitsForEachSubmissionAndReturnsStableIndexNotServerFeedback() {
        List<CompletableFuture<JsonObject>> pending = new ArrayList<>();
        List<String> submitted = new ArrayList<>();
        var result =
                PlayerCommandBatch.execute(
                        List.of("help", "time query daytime", "list"),
                        "batch",
                        () -> {},
                        (index, command, guard) -> {
                            guard.run();
                            submitted.add(command);
                            var next = new CompletableFuture<JsonObject>();
                            pending.add(next);
                            return next;
                        });
        assertEquals(List.of("help"), submitted);
        pending.get(0).complete(receipt());
        assertEquals(List.of("help", "time query daytime"), submitted);
        pending.get(1).complete(receipt());
        pending.get(2).complete(receipt());
        var value = result.join();
        assertEquals(3, value.get("submittedCount").getAsInt());
        assertFalse(value.get("effectConfirmed").getAsBoolean());
        assertFalse(value.get("serverFeedbackCorrelated").getAsBoolean());
        assertFalse(value.get("atomic").getAsBoolean());
        assertFalse(value.get("retrySafe").getAsBoolean());
        for (int index = 0; index < 3; index++) {
            var item = value.getAsJsonArray("commands").get(index).getAsJsonObject();
            assertEquals(index, item.get("commandIndex").getAsInt());
            assertEquals("batch:" + index, item.get("commandRequestId").getAsString());
            assertEquals("submitted_unconfirmed", item.get("status").getAsString());
            assertEquals("USER", item.get("dataCategory").getAsString());
        }
    }

    @Test
    void permissionOrDeadlineLossStopsRemainingCommandsWithoutRollingBackSubmittedOnes() {
        var authorized = new AtomicBoolean(true);
        var calls = new AtomicInteger();
        var value =
                PlayerCommandBatch.execute(
                                List.of("help", "list", "time query daytime"),
                                "batch",
                                () -> {
                                    if (!authorized.get())
                                        throw new ProtocolState.ProtocolException(
                                                "DEADLINE_EXCEEDED", 408, "expired");
                                },
                                (index, command, guard) -> {
                                    guard.run();
                                    calls.incrementAndGet();
                                    authorized.set(false);
                                    return CompletableFuture.completedFuture(receipt());
                                })
                        .join();
        assertEquals(1, calls.get());
        assertEquals(1, value.get("submittedCount").getAsInt());
        assertEquals("partial_submission", value.get("submissionStatus").getAsString());
        assertEquals(
                "not_submitted",
                value.getAsJsonArray("commands")
                        .get(1)
                        .getAsJsonObject()
                        .get("status")
                        .getAsString());
        assertEquals(
                "not_attempted",
                value.getAsJsonArray("commands")
                        .get(2)
                        .getAsJsonObject()
                        .get("status")
                        .getAsString());
        assertFalse(value.get("rollbackAttempted").getAsBoolean());
    }

    @Test
    void submissionFailureIsConservativeAndNeverRetries() {
        var calls = new AtomicInteger();
        var value =
                PlayerCommandBatch.execute(
                                List.of("help", "list", "help"),
                                "batch",
                                () -> {},
                                (index, command, guard) -> {
                                    calls.incrementAndGet();
                                    return index == 0
                                            ? CompletableFuture.completedFuture(receipt())
                                            : CompletableFuture.failedFuture(
                                                    new ProtocolState.ProtocolException(
                                                            "PLAYER_COMMAND_CONTEXT_CHANGED",
                                                            409,
                                                            "connection changed"));
                                })
                        .join();
        assertEquals(2, calls.get());
        assertEquals("not_confirmed", value.get("submissionStatus").getAsString());
        var uncertain = value.getAsJsonArray("commands").get(1).getAsJsonObject();
        assertEquals("submission_unconfirmed", uncertain.get("status").getAsString());
        assertTrue(uncertain.get("submissionMayHaveOccurred").getAsBoolean());
        assertEquals(
                "PLAYER_COMMAND_CONTEXT_CHANGED",
                uncertain.getAsJsonObject("error").get("code").getAsString());
        assertEquals(
                "not_attempted",
                value.getAsJsonArray("commands")
                        .get(2)
                        .getAsJsonObject()
                        .get("status")
                        .getAsString());
    }

    @Test
    void cancelledBatchInvalidatesQueuedDispatchGuardAndHasNoLateNextItem() {
        var pending = new CompletableFuture<JsonObject>();
        var guard = new AtomicReference<Runnable>();
        var calls = new AtomicInteger();
        var result =
                PlayerCommandBatch.execute(
                        List.of("help", "list"),
                        "batch",
                        () -> {},
                        (index, command, authorization) -> {
                            calls.incrementAndGet();
                            guard.set(authorization);
                            return pending;
                        });
        assertTrue(result.cancel(false));
        assertTrue(pending.isCancelled());
        assertEquals(
                "PLAYER_COMMAND_BATCH_CANCELLED",
                assertThrows(ProtocolState.ProtocolException.class, () -> guard.get().run())
                        .code());
        pending.complete(receipt());
        assertEquals(1, calls.get());
    }

    @Test
    void nativeContextGuardIsRecheckedByEachSenderAtItsActualCommitPoint() {
        var connection = new AtomicBoolean(true);
        var pending = new CompletableFuture<JsonObject>();
        var commitGuard = new AtomicReference<Runnable>();
        var result =
                PlayerCommandBatch.execute(
                        List.of("help", "list"),
                        "batch",
                        () -> {
                            if (!connection.get())
                                throw new ProtocolState.ProtocolException(
                                        "PLAYER_COMMAND_CONTEXT_CHANGED",
                                        409,
                                        "connection changed");
                        },
                        (index, command, guard) -> {
                            commitGuard.set(guard);
                            return pending;
                        });
        connection.set(false);
        assertEquals(
                "PLAYER_COMMAND_CONTEXT_CHANGED",
                assertThrows(ProtocolState.ProtocolException.class, () -> commitGuard.get().run())
                        .code());
        pending.completeExceptionally(
                new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_CONTEXT_CHANGED", 409, "blocked before packet"));
        assertEquals(
                "not_attempted",
                result.join()
                        .getAsJsonArray("commands")
                        .get(1)
                        .getAsJsonObject()
                        .get("status")
                        .getAsString());
    }

    @Test
    void cancellationAfterOneReceiptCannotSendTheQueuedSecondOrThirdCommand() {
        var first = new AtomicInteger();
        var second = new CompletableFuture<JsonObject>();
        var queuedGuard = new AtomicReference<Runnable>();
        var result =
                PlayerCommandBatch.execute(
                        List.of("help", "list", "help"),
                        "partial",
                        () -> {},
                        (index, command, guard) -> {
                            if (index == 0) {
                                guard.run();
                                first.incrementAndGet();
                                return CompletableFuture.completedFuture(receipt());
                            }
                            assertEquals(1, index, "No third item before the second submission");
                            queuedGuard.set(guard);
                            return second;
                        });
        assertEquals(1, first.get(), "A completed native submission is not rolled back");
        assertTrue(result.cancel(false));
        assertTrue(second.isCancelled());
        assertThrows(ProtocolState.ProtocolException.class, () -> queuedGuard.get().run());
        second.complete(receipt());
        assertEquals(1, first.get());
        assertTrue(result.isCancelled(), "A cancelled caller receives no fabricated batch success");
    }

    private static JsonObject receipt() {
        JsonObject value = new JsonObject();
        value.addProperty("accepted", true);
        value.addProperty("route", "NATIVE");
        value.addProperty("dataCategory", "USER");
        value.addProperty("effectConfirmed", false);
        return value;
    }
}
