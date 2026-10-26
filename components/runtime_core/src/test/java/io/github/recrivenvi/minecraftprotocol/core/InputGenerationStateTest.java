package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class InputGenerationStateTest {
    @Test
    void nativeConsumptionAttributionNeverGrantsControlAndRejectsOldContextOrCleanup() {
        var epoch = new InputContextEpoch();
        var cleanup = new CompletableFuture<JsonObject>();
        try (var state = state(cleanup)) {
            var world = epoch.observe(new Object(), new Object(), new Object(), true);
            state.nativeInputContextChanged(world);
            assertFalse(state.nativeInputLeaseActive(world));
            String lease = acquire(state);
            assertTrue(state.nativeInputLeaseActive(world));
            assertFalse(state.nativeInputLeaseActive(null));
            state.releaseAllInputAndWait(lease);
            assertFalse(state.nativeInputLeaseActive(world));
            cleanup.complete(released(0, 0));
            assertTrue(state.nativeInputLeaseActive(world));
            var next = epoch.observe(new Object(), new Object(), new Object(), true);
            state.nativeInputContextChanged(next);
            assertFalse(state.nativeInputLeaseActive(world));
            assertTrue(state.nativeInputLeaseActive(next));
            state.releaseLease(lease, "test");
            assertFalse(state.nativeInputLeaseActive(next));
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
        }
    }

    @Test
    void nativeReplacementFencesOldAdmissionAndPreservesLeaseModeAndIndependentWork()
            throws Exception {
        var nativeEpoch = new InputContextEpoch();
        var cleanup = new CompletableFuture<JsonObject>();
        AtomicInteger cleanups = new AtomicInteger();
        try (var state =
                new ProtocolState(
                        Set.of("read", "input", "control"),
                        "test",
                        reason -> {
                            assertEquals("native_input_context_changed", reason);
                            cleanups.incrementAndGet();
                            return cleanup;
                        },
                        ignored -> {})) {
            state.nativeInputContextChanged(nativeEpoch.snapshot());
            String lease = acquire(state);
            var old = state.captureInputGuard(lease);
            try (var independent = state.beginOperate()) {
                state.nativeInputContextChanged(
                        nativeEpoch.observe(new Object(), new Object(), new Object(), true));
                assertEquals("CONTROL_INPUT_GENERATION_CHANGED", code(old));
                assertEquals(
                        "CONTROL_INPUT_CLEANUP_PENDING",
                        code(() -> state.captureInputGuard(lease)));
                assertEquals(42, independent.call(() -> 42));
                assertEquals(1, cleanups.get());
                state.nativeInputContextChanged(nativeEpoch.snapshot());
                assertEquals(1, cleanups.get());
                cleanup.complete(released(1, 1));
                state.captureInputGuard(lease).run();
                assertEquals("TAKEOVER", state.modeStatus().get("mode").getAsString());
                assertEquals(lease, state.leaseStatus().get("leaseId").getAsString());
                assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
                var metadata = state.inputStateMetadata(new JsonObject());
                assertEquals(
                        nativeEpoch.snapshot().opaqueId(),
                        metadata.get("inputContextId").getAsString());
                assertTrue(metadata.get("inputContextAvailable").getAsBoolean());
            }
        }
    }

    @Test
    void nativeDeathDrainsOldInputButRetainsTheLeaseForNewDeathScreenInput() {
        var epoch = new InputContextEpoch();
        Object world = new Object(), player = new Object(), connection = new Object();
        var cleanup = new CompletableFuture<JsonObject>();
        try (var state = state(cleanup)) {
            state.nativeInputContextChanged(epoch.observe(world, player, connection, true));
            String lease = acquire(state);
            var old = state.captureInputGuard(lease);
            var version = state.modeStatus().get("modeVersion").deepCopy();
            state.nativeInputContextChanged(epoch.observe(world, player, connection, false));
            assertEquals("CONTROL_INPUT_GENERATION_CHANGED", code(old));
            assertEquals(
                    "CONTROL_INPUT_CLEANUP_PENDING", code(() -> state.captureInputGuard(lease)));
            assertTrue(
                    state.inputStateMetadata(new JsonObject())
                            .get("inputContextAvailable")
                            .getAsBoolean());
            cleanup.complete(released(1, 0));
            state.captureInputGuard(lease).run();
            assertEquals("TAKEOVER", state.modeStatus().get("mode").getAsString());
            assertEquals(lease, state.leaseStatus().get("leaseId").getAsString());
            assertEquals(version, state.modeStatus().get("modeVersion"));
            assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
        }
    }

    @Test
    void replacementsDuringCleanupJoinEveryOldDrainBeforeNewInputCanStart() {
        var nativeEpoch = new InputContextEpoch();
        var first = new CompletableFuture<JsonObject>();
        var second = new CompletableFuture<JsonObject>();
        AtomicInteger cleanups = new AtomicInteger();
        try (var state =
                new ProtocolState(
                        Set.of("input", "control"),
                        "test",
                        reason -> cleanups.getAndIncrement() == 0 ? first : second,
                        ignored -> {})) {
            state.nativeInputContextChanged(nativeEpoch.snapshot());
            String lease = acquire(state);
            var old = state.captureInputGuard(lease);
            state.nativeInputContextChanged(
                    nativeEpoch.observe(new Object(), new Object(), new Object(), true));
            state.nativeInputContextChanged(nativeEpoch.observe(null, null, null, false));
            assertEquals(2, cleanups.get());
            second.complete(released(0, 0));
            assertEquals(
                    "CONTROL_INPUT_CLEANUP_PENDING", code(() -> state.captureInputGuard(lease)));
            first.complete(released(1, 0));
            state.captureInputGuard(lease).run();
            assertEquals("CONTROL_INPUT_GENERATION_CHANGED", code(old));
            assertFalse(
                    state.inputStateMetadata(new JsonObject())
                            .get("inputContextAvailable")
                            .getAsBoolean());
        }
    }

    @Test
    void oldContextCannotBeReinstalledAndFailedNativeCleanupFailsClosed() {
        var nativeEpoch = new InputContextEpoch();
        var title = nativeEpoch.snapshot();
        var cleanup = new CompletableFuture<JsonObject>();
        try (var state = state(cleanup)) {
            state.nativeInputContextChanged(title);
            String lease = acquire(state);
            state.nativeInputContextChanged(
                    nativeEpoch.observe(new Object(), new Object(), new Object(), true));
            assertThrows(
                    IllegalArgumentException.class, () -> state.nativeInputContextChanged(title));
            cleanup.completeExceptionally(new IllegalStateException("native cleanup failure"));
            assertEquals(
                    "CONTROL_INPUT_CLEANUP_FAILED", code(() -> state.captureInputGuard(lease)));
        }
    }

    private static ProtocolState state(CompletableFuture<JsonObject> cleanup) {
        return new ProtocolState(
                Set.of("read", "input", "control"),
                "generation-test",
                ignored -> cleanup,
                ignored -> {});
    }

    private static JsonObject released(int keys, int buttons) {
        JsonObject result = new JsonObject();
        result.addProperty("releasedKeys", keys);
        result.addProperty("releasedButtons", buttons);
        return result;
    }

    private static String acquire(ProtocolState state) {
        return state.acquireLease(ProtocolState.MAX_CONTROL_LEASE_MILLIS)
                .get("leaseId")
                .getAsString();
    }

    private static String code(Runnable action) {
        return assertThrows(ProtocolState.ProtocolException.class, action::run).code();
    }

    @Test
    void strongStopInvalidatesOldAdmissionAndBlocksNewInputUntilCleanupFinishes() throws Exception {
        CompletableFuture<JsonObject> cleanup = new CompletableFuture<>();
        try (ProtocolState state = state(cleanup)) {
            String lease = acquire(state);
            ProtocolState.InputGuard old = state.captureInputGuard(lease);
            var response = state.releaseAllInputAndWait(lease);
            assertFalse(response.isDone());
            assertEquals("CONTROL_INPUT_GENERATION_CHANGED", code(old));
            assertEquals(
                    "CONTROL_INPUT_CLEANUP_PENDING", code(() -> state.captureInputGuard(lease)));
            AtomicInteger dispatches = new AtomicInteger();
            assertEquals(
                    "CONTROL_INPUT_GENERATION_CHANGED",
                    code(
                            () ->
                                    state.admitInput(
                                            old,
                                            () -> {
                                                dispatches.incrementAndGet();
                                                return CompletableFuture.completedFuture(
                                                        new JsonObject());
                                            })));
            assertEquals(0, dispatches.get());
            assertTrue(
                    state.inputStateMetadata(new JsonObject())
                            .get("inputCleanupPending")
                            .getAsBoolean());
            cleanup.complete(released(2, 1));
            JsonObject result = response.get(1, TimeUnit.SECONDS);
            assertEquals("input.release_all", result.get("type").getAsString());
            assertEquals(old.generation() + 1, result.get("inputGeneration").getAsLong());
            assertEquals(2, result.get("releasedKeys").getAsInt());
            assertEquals(1, result.get("releasedButtons").getAsInt());
            state.captureInputGuard(lease).run();
            assertEquals("CONTROL_INPUT_GENERATION_CHANGED", code(old));
            assertEquals("TAKEOVER", state.modeStatus().get("mode").getAsString());
            assertEquals(lease, state.leaseStatus().get("leaseId").getAsString());
            assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
        }
    }

    @Test
    void responseCancellationAndDeadlineCannotCancelTheSafetyCleanup() throws Exception {
        for (boolean deadline : new boolean[] {false, true}) {
            CompletableFuture<JsonObject> cleanup = new CompletableFuture<>();
            try (ProtocolState state = state(cleanup)) {
                String lease = acquire(state);
                CompletableFuture<JsonObject> response = state.releaseAllInputAndWait(lease);
                if (deadline) {
                    var expired = state.applyDeadline(response, System.currentTimeMillis() - 1);
                    ExecutionException failure =
                            assertThrows(
                                    ExecutionException.class,
                                    () -> expired.get(1, TimeUnit.SECONDS));
                    assertEquals(
                            "REQUEST_DEADLINE_EXCEEDED",
                            ((ProtocolState.ProtocolException) failure.getCause()).code());
                } else {
                    assertTrue(response.cancel(false));
                }
                assertFalse(cleanup.isCancelled());
                assertFalse(cleanup.isDone());
                assertEquals(
                        "CONTROL_INPUT_CLEANUP_PENDING",
                        code(() -> state.captureInputGuard(lease)));
                cleanup.complete(released(0, 1));
                state.captureInputGuard(lease).run();
            }
        }
    }

    @Test
    void concurrentStopsShareOneCleanupWaveWithoutASecondLateRelease() throws Exception {
        AtomicInteger cleanups = new AtomicInteger();
        CompletableFuture<JsonObject> cleanup = new CompletableFuture<>();
        try (ProtocolState state =
                new ProtocolState(
                        Set.of("input", "control"),
                        "test",
                        reason -> {
                            cleanups.incrementAndGet();
                            return cleanup;
                        },
                        ignored -> {})) {
            String lease = acquire(state);
            var first = state.releaseAllInputAndWait(lease);
            var second = state.releaseAllInputAndWait(lease);
            assertEquals(1, cleanups.get());
            cleanup.complete(released(1, 0));
            assertEquals(first.get(1, TimeUnit.SECONDS), second.get(1, TimeUnit.SECONDS));
            assertEquals(1, cleanups.get());
            state.captureInputGuard(lease).run();
        }
    }

    @Test
    void failedCleanupCannotAdmitSameLeaseInputOrPretendToHaveReleasedAnything() {
        CompletableFuture<JsonObject> cleanup = new CompletableFuture<>();
        try (ProtocolState state = state(cleanup)) {
            String lease = acquire(state);
            var response = state.releaseAllInputAndWait(lease);
            cleanup.completeExceptionally(new IllegalStateException("native release failed"));
            assertTrue(response.isCompletedExceptionally());
            assertEquals(
                    "CONTROL_INPUT_CLEANUP_FAILED", code(() -> state.captureInputGuard(lease)));
            JsonObject metadata = state.inputStateMetadata(new JsonObject());
            assertFalse(metadata.get("inputCleanupPending").getAsBoolean());
            assertTrue(metadata.get("inputCleanupFailed").getAsBoolean());
        }
    }

    @Test
    void stopLeavesIndependentOperateAuthorityAliveAndDoesNotSetTheHumanLatch() throws Exception {
        try (ProtocolState state = state(CompletableFuture.completedFuture(released(0, 0)))) {
            String lease = acquire(state);
            try (var work = state.beginOperate()) {
                state.releaseAllInputAndWait(lease).get(1, TimeUnit.SECONDS);
                assertEquals(42, work.call(() -> 42));
                assertEquals("TAKEOVER", state.modeStatus().get("mode").getAsString());
                assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
            }
        }
    }

    @Test
    void inputGenerationIsNotAnotherLeaseAndCannotResurrectAfterReacquire() throws Exception {
        try (ProtocolState state = state(CompletableFuture.completedFuture(released(0, 0)))) {
            String first = acquire(state);
            var old = state.captureInputGuard(first);
            assertTrue(state.releaseLeaseIfMatches(first, "control_channel_disconnected"));
            String second = acquire(state);
            assertNotEquals(first, second);
            assertEquals("CONTROL_LEASE_REQUIRED", code(old));
            assertEquals("CONTROL_LEASE_REQUIRED", code(() -> state.releaseAllInputAndWait(first)));
            state.captureInputGuard(second).run();
            state.revokeHumanControl();
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
            assertEquals(
                    "USER_MANUALLY_ENDED_CONTROL",
                    code(() -> state.releaseAllInputAndWait(second)));
        }
    }

    @Test
    void guardDoesNotTakeProtocolStateMonitorFromTheNativeOwnerCallback() throws Exception {
        try (ProtocolState state = state(CompletableFuture.completedFuture(released(0, 0)))) {
            var guard = state.captureInputGuard(acquire(state));
            synchronized (state) {
                CompletableFuture.runAsync(guard).get(1, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void releaseAllRequiresBothScopesAndNeverUpgradesReadOrOperate() throws Exception {
        for (Set<String> scopes : List.of(Set.of("input"), Set.of("control"))) {
            try (ProtocolState state = new ProtocolState(scopes, "test", ignored -> {})) {
                String lease = acquire(state);
                assertEquals("SCOPE_DENIED", code(() -> state.releaseAllInputAndWait(lease)));
            }
        }
        try (ProtocolState state = state(CompletableFuture.completedFuture(released(0, 0)))) {
            assertEquals("TAKEOVER_REQUIRED", code(() -> state.releaseAllInputAndWait(null)));
            state.selectMode(
                            AgentControlSession.Mode.OPERATE,
                            state.modeStatus().getAsJsonObject("modeVersion"),
                            null,
                            "test",
                            "test")
                    .get(1, TimeUnit.SECONDS);
            assertEquals("TAKEOVER_REQUIRED", code(() -> state.releaseAllInputAndWait(null)));
        }
    }
}
