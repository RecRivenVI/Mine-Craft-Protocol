package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ControlAuthorizationStatusTest {
    @Test
    void shutdownDoesNotHoldTheStateMonitorWhileCancellingAnOwnerBarrier() throws Exception {
        var state = new ProtocolState(Set.of("read"), "operator", ignored -> {});
        var observed = new java.util.concurrent.atomic.AtomicBoolean();
        var source =
                new java.util.concurrent.CompletableFuture<JsonObject>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        var owner =
                                java.util.concurrent.CompletableFuture.supplyAsync(
                                        () -> {
                                            state.modeStatus();
                                            observed.set(true);
                                            return true;
                                        });
                        try {
                            owner.get(2, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception failure) {
                            throw new AssertionError(
                                    "Cancellation waited on the locked state monitor", failure);
                        }
                        return super.cancel(interrupt);
                    }
                };
        try {
            state.startOperation(source);
            java.util.concurrent.CompletableFuture.runAsync(state::close)
                    .get(4, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(observed.get());
            assertTrue(source.isCancelled());
        } finally {
            state.close();
        }
    }

    @Test
    void productionModeStatusSummarizesIndependentAuthoritiesWithoutCredentials() {
        try (var state =
                new ProtocolState(
                        Set.of("read", "control", "debug", "debug.write", "debug.world"),
                        "operator",
                        ignored -> {})) {
            JsonObject initial = state.modeStatus();
            JsonObject authorization = initial.getAsJsonObject("authorization");
            assertEquals("READ", initial.get("mode").getAsString());
            assertFalse(authorization.get("grantsAuthority").getAsBoolean());
            assertFalse(authorization.getAsJsonObject("inputLease").get("held").getAsBoolean());
            assertEquals(
                    "disarmed",
                    authorization.getAsJsonObject("debugArm").get("status").getAsString());
            assertEquals(5, authorization.getAsJsonArray("grantedScopes").size());
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            JsonObject controlled = state.modeStatus();
            assertTrue(
                    controlled
                            .getAsJsonObject("authorization")
                            .getAsJsonObject("inputLease")
                            .get("held")
                            .getAsBoolean());
            assertFalse(controlled.toString().contains(lease));
            state.revokeHumanControl();
            JsonObject revoked = state.modeStatus();
            assertEquals("READ", revoked.get("mode").getAsString());
            assertTrue(revoked.get("reconsentRequired").getAsBoolean());
            assertFalse(
                    revoked.getAsJsonObject("authorization")
                            .getAsJsonObject("inputLease")
                            .get("held")
                            .getAsBoolean());
            assertEquals(
                    0,
                    revoked.getAsJsonObject("authorization")
                            .getAsJsonObject("inputLease")
                            .get("remainingTtlMs")
                            .getAsLong());
        }
    }
}
