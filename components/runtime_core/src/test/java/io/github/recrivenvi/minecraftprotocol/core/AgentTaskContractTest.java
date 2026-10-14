package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AgentTaskContractTest {
    @Test
    void taskGrantsNoAuthorityAndEndWaitsForOnlyAttachedInputCleanup() throws Exception {
        var cleanup = new CompletableFuture<com.google.gson.JsonObject>();
        try (var state =
                new ProtocolState(
                        Set.of("read", "control"), "task-test", reason -> cleanup, ignored -> {})) {
            var task = state.beginTask(60000, 300000);
            String id = task.get("taskId").getAsString();
            assertTrue(state.taskPresent());
            assertFalse(task.get("grantsAuthority").getAsBoolean());
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
            assertThrows(ProtocolState.ProtocolException.class, () -> state.requireTakeover(null));
            assertThrows(
                    AgentControlSession.ModeException.class,
                    () -> state.acquireLease(60000, null, null));
            String lease = state.acquireLease(60000, null, id).get("leaseId").getAsString();
            state.requireTakeover(lease);
            var end = state.endTask(id);
            assertFalse(end.isDone());
            assertFalse(state.controlPresence().agentControlled());
            assertFalse(state.taskPresent());
            assertThrows(ProtocolState.ProtocolException.class, () -> state.requireTakeover(lease));
            cleanup.complete(new com.google.gson.JsonObject());
            assertEquals("ENDED", end.get(1, TimeUnit.SECONDS).get("status").getAsString());
            state.agentActivity();
            assertFalse(state.taskPresent());
        }
    }

    @Test
    void expiryDrainsInputAndManualOverrideCannotBeUndoneByHeartbeat() throws Exception {
        var cleanup = new AtomicInteger();
        try (var state =
                new ProtocolState(
                        Set.of("read", "control"),
                        "task-test",
                        reason -> {
                            cleanup.incrementAndGet();
                        })) {
            String id = state.beginTask(1000, 1000).get("taskId").getAsString();
            state.acquireLease(60000, null, id);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (state.controlPresence().agentControlled() && System.nanoTime() < deadline)
                Thread.sleep(25);
            assertEquals("EXPIRED", state.taskStatus().get("status").getAsString());
            assertFalse(state.controlPresence().agentControlled());
            assertEquals(1, cleanup.get());
            id = state.beginTask(60000, 300000).get("taskId").getAsString();
            state.acquireLease(60000, null, id);
            state.revokeHumanControl();
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
            assertEquals("INTERRUPTED", state.taskStatus().get("status").getAsString());
            String oldId = id;
            assertThrows(
                    AgentControlSession.ModeException.class,
                    () -> state.heartbeatTask(oldId, 60000));
            state.endTask(id).get(1, TimeUnit.SECONDS);
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
            assertEquals(2, cleanup.get());
        }
    }
}
