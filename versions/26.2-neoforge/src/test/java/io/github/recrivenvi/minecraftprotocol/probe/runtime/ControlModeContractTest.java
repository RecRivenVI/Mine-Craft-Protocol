package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ControlModeContractTest {
    private static ProtocolState state() {
        return new ProtocolState(Set.of("read", "input", "control", "fixture"), "mode-test", ignored -> { });
    }
    private static JsonObject version(ProtocolState state) { return state.modeStatus().getAsJsonObject("modeVersion").deepCopy(); }
    private static String mode(ProtocolState state) { return state.modeStatus().get("mode").getAsString(); }
    @Test void evidenceDescriptorsDoNotRequireMutationArmOrInputLease() {
        try (ProtocolState state = state()) {
            var operations = state.descriptors().getAsJsonArray("operations");
            for (var value : operations) {
                JsonObject descriptor = value.getAsJsonObject();
                String id = descriptor.get("id").getAsString();
                if (id.startsWith("debug.evidence.")) {
                    assertFalse(descriptor.get("requiresDebugArm").getAsBoolean());
                    assertFalse(descriptor.get("requiresControlLease").getAsBoolean());
                    assertEquals("READ_COMPATIBLE", descriptor.get("modeRequirement").getAsString());
                }
                if (id.startsWith("fixture.")) {
                    assertFalse(descriptor.get("requiresControlLease").getAsBoolean());
                    assertEquals("OPERATE_REQUIRED", descriptor.get("modeRequirement").getAsString());
                }
            }
        }
    }
    private static void select(ProtocolState state, AgentControlSession.Mode mode) throws Exception {
        state.selectMode(mode, version(state), null, "request", "test").get(2, TimeUnit.SECONDS);
    }

    @Test void readAndOperateCannotProduceInputOrAcquirePermissions() throws Exception {
        try (ProtocolState state = state()) {
            assertEquals("READ", mode(state));
            assertEquals("TAKEOVER_REQUIRED", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.requireTakeover(null)).code());
            select(state, AgentControlSession.Mode.OPERATE);
            assertEquals("OPERATE", mode(state));
            assertEquals("available", state.leaseStatus().get("status").getAsString());
            assertEquals("TAKEOVER_REQUIRED", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.requireTakeover(null)).code());
            assertEquals("DEBUG_SCOPE_DENIED", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.requireDebugScope("player")).code());
            assertEquals("DEBUG_NOT_ARMED", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.requireDebugCredential(null)).code());
            AtomicInteger fixture = new AtomicInteger();
            state.operate(work -> CompletableFuture.completedFuture(work.call(() -> {
                fixture.incrementAndGet(); JsonObject result = new JsonObject();
                result.addProperty("mode", "FIXTURE"); return result;
            }))).get(1, TimeUnit.SECONDS);
            assertEquals(1, fixture.get());
            assertFalse(state.hasScope("debug"));
        }
    }

    @Test void takeoverUsesOnlyExistingLeaseAndMissingLeaseStillFails() {
        try (ProtocolState state = state()) {
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            assertEquals("TAKEOVER", mode(state));
            state.requireTakeover(lease);
            assertEquals("CONTROL_LEASE_REQUIRED", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.requireTakeover(null)).code());
            assertEquals("CONTROL_LEASE_CONFLICT", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.acquireLease(60000)).code());
        }
    }

    @Test void manualLatchSurvivesOperateAndReadUntilExplicitReacquire() throws Exception {
        try (ProtocolState state = state()) {
            state.acquireLease(60000);
            state.revokeHumanControl(); // synthetic unit input; live evidence must use a human
            assertEquals("READ", mode(state));
            select(state, AgentControlSession.Mode.OPERATE);
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
            try (var work = state.beginOperate()) { assertEquals(7, work.call(() -> 7)); }
            for (int i=0;i<3;i++) assertEquals("USER_MANUALLY_ENDED_CONTROL",
                    assertThrows(ProtocolState.ProtocolException.class, () -> state.requireTakeover("old")).code());
            select(state, AgentControlSession.Mode.READ);
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
            state.acquireLease(60000);
            assertEquals("TAKEOVER", mode(state));
            assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
        }
    }

    @Test void routedEscapeCannotSatisfyTheNativeRevocationBranch() {
        try (ProtocolState state = state()) {
            state.acquireLease(60000);
            AgentInputContext.routed(() -> { if (!AgentInputContext.isAgentRouted()) state.revokeHumanControl(); });
            assertEquals("TAKEOVER", mode(state));
            assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
        }
    }

    @Test void staleQueuedInputDoesNotBecomeValidAfterAnotherTakeover() {
        try (ProtocolState state = state()) {
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            var accepted = state.controlPresence();
            AtomicInteger effects = new AtomicInteger();
            state.releaseLease(lease, "test_exit");
            assertThrows(AgentControlSession.ModeException.class,
                    () -> state.controlSession().withTakeover(accepted, effects::incrementAndGet));
            state.acquireLease(60000);
            assertThrows(AgentControlSession.ModeException.class,
                    () -> state.controlSession().withTakeover(accepted, effects::incrementAndGet));
            assertEquals(0, effects.get());
        }
    }

    @Test void exitWaitsForInputCleanupAndCancelsQueuedPipeline() throws Exception {
        CompletableFuture<JsonObject> cleanup = new CompletableFuture<>();
        try (ProtocolState state = new ProtocolState(Set.of("input","control"), "test", reason -> cleanup, ignored -> { })) {
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            CompletableFuture<JsonObject> pipeline = new CompletableFuture<>();
            state.startOperation(pipeline, true);
            CompletableFuture<JsonObject> switched = state.selectMode(AgentControlSession.Mode.OPERATE,
                    version(state), lease, "switch", "test");
            assertTrue(pipeline.isCancelled());
            assertFalse(switched.isDone());
            assertEquals("READ", mode(state));
            assertEquals("CONTROL_INPUT_CLEANUP_PENDING", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.acquireLease(60000)).code());
            cleanup.complete(new JsonObject());
            assertEquals("OPERATE", switched.get(1,TimeUnit.SECONDS).get("mode").getAsString());
            assertEquals("available", state.leaseStatus().get("status").getAsString());
        }
    }

    @Test void failedCleanupAndWrongExitLeaseCannotLeaveAnEscalatableTransition() throws Exception {
        CompletableFuture<JsonObject> cleanup = new CompletableFuture<>();
        try (ProtocolState state = new ProtocolState(Set.of("control"), "test", reason -> cleanup, ignored -> { })) {
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            assertThrows(ProtocolState.ProtocolException.class, () -> state.selectMode(
                    AgentControlSession.Mode.READ, version(state), "wrong", "request", "test"));
            assertFalse(state.modeStatus().get("modeTransitionPending").getAsBoolean());
            state.releaseLease(lease, "test");
            cleanup.completeExceptionally(new IllegalStateException("injected cleanup failure"));
            assertEquals("CONTROL_INPUT_CLEANUP_FAILED", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.acquireLease(60000)).code());
        }
    }

    @Test void compareAndSetRejectsOldSessionAndConcurrentStaleTransition() throws Exception {
        try (ProtocolState state = state(); ProtocolState other = state()) {
            JsonObject initial = version(state);
            assertEquals("STALE_CONTROL_SESSION", assertThrows(AgentControlSession.ModeException.class,
                    () -> other.requireModeVersion(initial)).code());
            select(state, AgentControlSession.Mode.OPERATE);
            assertEquals("STALE_MODE_REVISION", assertThrows(AgentControlSession.ModeException.class,
                    () -> state.selectMode(AgentControlSession.Mode.READ, initial, null, "loser", "test")).code());
            long generation=version(state).get("generation").getAsLong();
            select(state, AgentControlSession.Mode.OPERATE);
            assertEquals(generation, version(state).get("generation").getAsLong());
        }
    }

    @Test void malformedModeVersionsFailAsTypedClientErrors() {
        try (ProtocolState state = state()) {
            JsonObject invalid=version(state); invalid.addProperty("generation", 1.5);
            assertEquals("INVALID_MODE_VERSION", assertThrows(ProtocolState.ProtocolException.class,
                    () -> state.requireModeVersion(invalid)).code());
        }
    }

    @Test void operateCancellationDoesNotReleaseAnExecutingOwnerPermitEarly() throws Exception {
        try (ProtocolState state = state()) {
            select(state, AgentControlSession.Mode.OPERATE);
            AtomicReference<AgentControlSession.OperateWork> work = new AtomicReference<>();
            CompletableFuture<JsonObject> source = new CompletableFuture<>();
            CompletableFuture<JsonObject> response = state.operate(permit -> { work.set(permit); return source; });
            AgentControlSession.Guard entered=work.get().enter();
            response.cancel(false);
            assertEquals("MODE_OPERATION_IN_PROGRESS", assertThrows(AgentControlSession.ModeException.class,
                    () -> state.acquireLease(60000)).code());
            entered.close();
            assertThrows(AgentControlSession.ModeException.class, () -> work.get().call(() -> 1));
            state.acquireLease(60000);
            assertEquals("TAKEOVER", mode(state));
        }
    }

    @Test void ownerInputAndModeExitHaveARealLinearizationBoundary() throws Exception {
        try (ProtocolState state = state()) {
            String lease=state.acquireLease(60000).get("leaseId").getAsString();
            var accepted=state.controlPresence();
            CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
            AtomicInteger effects=new AtomicInteger();
            CompletableFuture<Void> input=CompletableFuture.runAsync(() -> state.controlSession().withTakeover(accepted, () -> {
                entered.countDown();
                try { assertTrue(release.await(2,TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                effects.incrementAndGet(); return null;
            }));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            CompletableFuture<Void> exit=CompletableFuture.runAsync(() -> state.releaseLease(lease,"mode_exit"));
            assertFalse(exit.isDone());
            release.countDown(); input.get(2,TimeUnit.SECONDS); exit.get(2,TimeUnit.SECONDS);
            assertEquals("READ",mode(state)); assertEquals(1,effects.get());
            assertThrows(AgentControlSession.ModeException.class, () -> state.controlSession().withTakeover(accepted,effects::incrementAndGet));
            assertEquals(1,effects.get());
        }
    }

    @Test void leaseDisconnectExpiryAndRuntimeCloseReturnToReadWithoutManualLatch() throws Exception {
        try (ProtocolState state = state()) {
            String lease=state.acquireLease(60000).get("leaseId").getAsString();
            assertFalse(state.releaseLeaseIfMatches("other-reader", "disconnect"));
            assertTrue(state.releaseLeaseIfMatches(lease, "control_channel_disconnected"));
            assertEquals("READ",mode(state));
            state.acquireLease(1000);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(mode(state).equals("TAKEOVER") && System.nanoTime()<deadline) Thread.sleep(20);
            assertEquals("READ",mode(state));
            assertFalse(state.modeStatus().get("reconsentRequired").getAsBoolean());
            select(state,AgentControlSession.Mode.OPERATE);
            var pending=state.beginOperate(); state.close();
            assertEquals("READ",mode(state));
            assertThrows(AgentControlSession.ModeException.class, () -> pending.call(() -> 1));
        }
    }
}
