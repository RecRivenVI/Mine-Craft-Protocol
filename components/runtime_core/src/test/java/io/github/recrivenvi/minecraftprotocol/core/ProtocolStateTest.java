package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ProtocolStateTest {
    @Test
    void waitObservesCommittedTerminalState() throws Exception {
        try (ProtocolState state = state()) {
            CompletableFuture<JsonObject> nativeFuture = new CompletableFuture<>();
            JsonObject started = state.startOperation(nativeFuture, true);
            CompletableFuture<JsonObject> waited =
                    state.waitOperation(started.get("operationId").getAsString(), 1_000L);
            JsonObject value = new JsonObject();
            value.addProperty("answer", 42);
            nativeFuture.complete(value);
            JsonObject terminal = waited.get(1L, TimeUnit.SECONDS);
            assertEquals("completed", terminal.get("state").getAsString());
            assertEquals(42, terminal.getAsJsonObject("result").get("answer").getAsInt());
        }
    }

    @Test
    void cancelPropagatesToNativeFuture() {
        try (ProtocolState state = state()) {
            CompletableFuture<JsonObject> nativeFuture = new CompletableFuture<>();
            String id = state.startOperation(nativeFuture, true).get("operationId").getAsString();
            JsonObject cancelled = state.cancelOperation(id);
            assertTrue(nativeFuture.isCancelled());
            assertEquals("cancelled", cancelled.get("state").getAsString());
            assertTrue(cancelled.get("cancelled").getAsBoolean());
        }
    }

    @Test
    void deadlineCancelsUnderlyingWork() {
        try (ProtocolState state = state()) {
            CompletableFuture<JsonObject> nativeFuture = new CompletableFuture<>();
            CompletableFuture<JsonObject> deadline =
                    state.applyDeadline(nativeFuture, System.currentTimeMillis() + 25L);
            assertThrows(ExecutionException.class, () -> deadline.get(1L, TimeUnit.SECONDS));
            assertTrue(nativeFuture.isCancelled());
        }
    }

    @Test
    void deadlineWaitsForCancellationBeforePublishingTimeout() throws Exception {
        try (ProtocolState state = state()) {
            CountDownLatch cancellationEntered = new CountDownLatch(1);
            CountDownLatch allowCancellation = new CountDownLatch(1);
            CompletableFuture<JsonObject> nativeFuture =
                    new CompletableFuture<>() {
                        @Override
                        public boolean cancel(boolean interrupt) {
                            cancellationEntered.countDown();
                            try {
                                if (!allowCancellation.await(2, TimeUnit.SECONDS))
                                    throw new AssertionError("Test cancellation barrier timed out");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                            return super.cancel(interrupt);
                        }
                    };
            CompletableFuture<JsonObject> deadline =
                    state.applyDeadline(nativeFuture, System.currentTimeMillis() + 50L);
            try {
                assertTrue(cancellationEntered.await(1, TimeUnit.SECONDS));
                assertTrue(!deadline.isDone(), "Timeout cannot be published before cancellation");
            } finally {
                allowCancellation.countDown();
            }
            ExecutionException failure =
                    assertThrows(ExecutionException.class, () -> deadline.get(1, TimeUnit.SECONDS));
            assertTrue(nativeFuture.isCancelled());
            assertEquals(
                    "REQUEST_DEADLINE_EXCEEDED",
                    ((ProtocolState.ProtocolException) failure.getCause()).code());
        }
    }

    @Test
    void deadlineWrapperPreservesSuccessfulCompletionAndExplicitCancellation() throws Exception {
        try (ProtocolState state = state()) {
            CompletableFuture<JsonObject> source = new CompletableFuture<>();
            var deadline = state.applyDeadline(source, System.currentTimeMillis() + 5000);
            var value = new JsonObject();
            source.complete(value);
            assertEquals(value, deadline.get(1, TimeUnit.SECONDS));
            assertTrue(!deadline.cancel(true));
            CompletableFuture<JsonObject> cancelled = new CompletableFuture<>();
            var other = state.applyDeadline(cancelled, System.currentTimeMillis() + 5000);
            assertTrue(other.cancel(true));
            assertTrue(other.isCancelled());
            assertTrue(cancelled.isCancelled());
        }
    }

    @Test
    void activeOperationRegistryIsBounded() {
        try (ProtocolState state = state()) {
            for (int index = 0; index < 16; index++) {
                state.startOperation(new CompletableFuture<JsonObject>(), false);
            }
            ProtocolState.ProtocolException failure =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> state.startOperation(new CompletableFuture<JsonObject>(), false));
            assertEquals("TOO_MANY_OPERATIONS", failure.code());
            assertEquals(429, failure.httpStatus());
        }
    }

    @Test
    void auditCarriesPrincipalAndConnectionIdentity() {
        try (ProtocolState state = state()) {
            state.audit("request", "connection", "/v0/session", "completed");
            JsonObject entry =
                    state.auditSnapshot(1).getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("test-principal", entry.get("principalId").getAsString());
            assertEquals("connection", entry.get("connectionId").getAsString());
        }
    }

    @Test
    void explicitRequestCancellationRetiresDeepObservationWork() {
        try (ProtocolState state = state()) {
            DeepObservationRequestContext context =
                    new DeepObservationRequestContext(
                            Set.of("read"),
                            "test-principal",
                            "deep-request",
                            "connection",
                            System.currentTimeMillis() + 5_000L,
                            ignored -> {});
            CompletableFuture<JsonObject> providerWork = context.track(new CompletableFuture<>());
            state.registerDeepObservation("deep-request", context);
            JsonObject cancelled = state.cancelDeepObservation("deep-request");
            assertEquals("cancelled", cancelled.get("status").getAsString());
            assertTrue(providerWork.isCancelled());
            assertEquals(
                    "already_terminal_or_unknown",
                    state.cancelDeepObservation("deep-request").get("status").getAsString());
        }
    }

    @Test
    void activeDeepObservationRegistryIsBounded() {
        try (ProtocolState state = state()) {
            for (int index = 0; index < ProtocolState.MAX_ACTIVE_DEEP_OBSERVATIONS; index++) {
                state.registerDeepObservation(
                        "request-" + index,
                        new DeepObservationRequestContext(
                                Set.of("read"),
                                "principal",
                                "request-" + index,
                                "connection",
                                0L,
                                ignored -> {}));
            }
            ProtocolState.ProtocolException failure =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () ->
                                    state.registerDeepObservation(
                                            "overflow",
                                            new DeepObservationRequestContext(
                                                    Set.of("read"),
                                                    "principal",
                                                    "overflow",
                                                    "connection",
                                                    0L,
                                                    ignored -> {})));
            assertEquals("TOO_MANY_DEEP_OBSERVATIONS", failure.code());
            assertEquals(429, failure.httpStatus());
        }
    }

    @Test
    void manualRevocationIsVisibleUntilExplicitReacquire() {
        try (ProtocolState state = state()) {
            JsonObject acquired = state.acquireLease(60_000L);
            assertEquals("AGENT_CONTROLLED", acquired.get("controlState").getAsString());

            JsonObject revoked = state.revokeHumanControl();
            assertEquals("manually_revoked", revoked.get("status").getAsString());
            assertEquals("MANUALLY_REVOKED", revoked.get("controlState").getAsString());
            assertTrue(revoked.get("reconsentRequired").getAsBoolean());
            assertEquals("用户手动结束控制", revoked.get("message").getAsString());

            JsonObject status = state.leaseStatus();
            assertEquals("MANUALLY_REVOKED", status.get("controlState").getAsString());
            assertTrue(status.get("reconsentRequired").getAsBoolean());
            ProtocolState.ProtocolException blocked =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> state.requireLease("old-lease"));
            assertEquals("USER_MANUALLY_ENDED_CONTROL", blocked.code());

            JsonObject reacquired = state.acquireLease(60_000L);
            assertEquals("AGENT_CONTROLLED", reacquired.get("controlState").getAsString());
            assertTrue(!reacquired.get("reconsentRequired").getAsBoolean());

            String audit = state.auditSnapshot(16).toString();
            assertTrue(audit.contains("agent_control_acquired"));
            assertTrue(audit.contains("human_manual_revocation"));
            assertTrue(audit.contains("agent_control_reacquired"));
        }
    }

    @Test
    void bulkCancellationCommitsEveryInputTerminalBeforeQueueHandoff() {
        for (String reason :
                java.util.List.of(
                        "human_manual_revocation",
                        "client_release",
                        "control_channel_disconnected",
                        "transport_close")) {
            try (ProtocolState state = state();
                    var queue =
                            new io.github.recrivenvi.minecraftprotocol.safety
                                    .InputSequenceQueue()) {
                String lease = state.acquireLease(60_000L).get("leaseId").getAsString();
                var guard = state.captureInputGuard(lease);
                var futures = new java.util.ArrayList<CompletableFuture<JsonObject>>();
                var drains = new java.util.ArrayList<CompletableFuture<Void>>();
                var ids = new java.util.ArrayList<String>();
                var nativeDispatches = new java.util.concurrent.atomic.AtomicInteger();
                for (int index = 0; index < 3; index++) {
                    var future = new CompletableFuture<JsonObject>();
                    var drain = new CompletableFuture<Void>();
                    futures.add(future);
                    drains.add(drain);
                    ids.add(state.startOperation(future, true).get("operationId").getAsString());
                    future.whenComplete((value, error) -> drain.complete(null));
                }
                for (int index = 0; index < 3; index++) {
                    int current = index;
                    queue.submit(
                            () -> {
                                if (current == 0) return;
                                try {
                                    guard.run();
                                    nativeDispatches.incrementAndGet();
                                    futures.get(current).complete(new JsonObject());
                                } catch (Throwable error) {
                                    futures.get(current).completeExceptionally(error);
                                }
                            },
                            () -> futures.get(current).cancel(false),
                            drains.get(current));
                }
                var independent = new CompletableFuture<JsonObject>();
                String independentId =
                        state.startOperation(independent, false).get("operationId").getAsString();
                JsonObject value = new JsonObject();
                value.addProperty("answer", 42);
                String completedId =
                        state.startOperation(CompletableFuture.completedFuture(value), true)
                                .get("operationId")
                                .getAsString();
                if (reason.equals("human_manual_revocation")) state.revokeHumanControl();
                else if (reason.equals("control_channel_disconnected"))
                    state.releaseLeaseIfMatches(lease, reason);
                else if (reason.equals("transport_close")) state.close();
                else state.releaseLease(lease, reason);
                for (String id : ids) {
                    JsonObject terminal = state.operationStatus(id);
                    assertEquals(
                            "cancelled",
                            terminal.get("state").getAsString(),
                            reason + ": " + terminal);
                    assertEquals(reason, terminal.get("cancellationReason").getAsString());
                    assertTrue(!terminal.has("errorCode"));
                }
                assertEquals(0, nativeDispatches.get());
                assertEquals(
                        "completed", state.operationStatus(completedId).get("state").getAsString());
                assertEquals(
                        42,
                        state.operationStatus(completedId)
                                .getAsJsonObject("result")
                                .get("answer")
                                .getAsInt());
                assertEquals(
                        reason.equals("transport_close") ? "cancelled" : "executing",
                        state.operationStatus(independentId).get("state").getAsString());
                if (!reason.equals("transport_close")) assertTrue(!independent.isDone());
            }
        }
    }

    @Test
    void terminalObserverSeesCommittedFailureInsteadOfFutureCallbackRegistrationOrder()
            throws Exception {
        try (var state = state()) {
            var input = new CompletableFuture<JsonObject>();
            String id = state.startOperation(input, true).get("operationId").getAsString();
            var observed = new CompletableFuture<JsonObject>();
            state.onOperationTerminal(id, observed::complete);
            input.completeExceptionally(
                    new ProtocolState.ProtocolException("TEST_FAILED", 409, "failed"));
            var terminal = observed.get(1, TimeUnit.SECONDS);
            assertEquals("failed", terminal.get("state").getAsString());
            assertEquals("TEST_FAILED", terminal.get("errorCode").getAsString());
        }
    }

    @Test
    void terminalObserverDoesNotKeepTheOperationMonitorWhileWaitingForSessionLocks()
            throws Exception {
        for (boolean cancel : new boolean[] {false, true})
            try (var state = state()) {
                var input = new CompletableFuture<JsonObject>();
                String id = state.startOperation(input, true).get("operationId").getAsString();
                Object sessionLock = new Object();
                var entered = new CountDownLatch(1);
                state.onOperationTerminal(
                        id,
                        snapshot -> {
                            entered.countDown();
                            synchronized (sessionLock) {
                            }
                        });
                var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
                try {
                    java.util.concurrent.Future<?> completion;
                    synchronized (sessionLock) {
                        completion =
                                executor.submit(
                                        () -> {
                                            if (cancel) state.cancelOperation(id);
                                            else input.complete(new JsonObject());
                                        });
                        assertTrue(entered.await(1, TimeUnit.SECONDS));
                        var read = executor.submit(() -> state.operationStatus(id));
                        assertEquals(
                                cancel ? "cancelled" : "completed",
                                read.get(1, TimeUnit.SECONDS).get("state").getAsString());
                    }
                    completion.get(1, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                }
            }
    }

    @Test
    void cancellationDoesNotFreezeASubmissionObservationStillBeingPublished() {
        class EvidenceFuture extends CompletableFuture<JsonObject>
                implements PlayerCommandEvidence.Source {
            final java.util.List<JsonObject> receipts =
                    new java.util.concurrent.CopyOnWriteArrayList<>();
            boolean complete;

            public java.util.List<JsonObject> commandSubmissions() {
                return receipts;
            }

            public boolean commandSubmissionsComplete() {
                return complete;
            }
        }
        try (var state = state()) {
            var future = new EvidenceFuture();
            String id = state.startOperation(future, true).get("operationId").getAsString();
            var cancelled = state.cancelOperation(id);
            assertEquals("cancelled", cancelled.get("state").getAsString());
            assertTrue(!cancelled.has("result"));
            assertTrue(!cancelled.get("commandSubmissionsComplete").getAsBoolean());
            // Already-admitted observation before cleanup, not late input.
            future.receipts.add(
                    PlayerCommandEvidence.classified(
                            new JsonObject(),
                            PlayerCommandPolicy.Effect.STATE_MUTATION,
                            "GAME_ROUTED_RAW"));
            for (int repeat = 0; repeat < 3; repeat++) {
                var current = state.operationStatus(id);
                assertEquals("cancelled", current.get("state").getAsString());
                assertEquals(
                        1,
                        current.getAsJsonObject("result")
                                .getAsJsonArray("commandSubmissions")
                                .size());
                assertTrue(
                        current.getAsJsonObject("result")
                                .get("evidenceContaminated")
                                .getAsBoolean());
            }
            future.complete = true;
            assertTrue(state.operationStatus(id).get("commandSubmissionsComplete").getAsBoolean());
        }
    }

    private static ProtocolState state() {
        return new ProtocolState(
                Set.of("read", "input", "control", "event", "diagnostics", "command"),
                "test-principal",
                ignored -> {});
    }
}
