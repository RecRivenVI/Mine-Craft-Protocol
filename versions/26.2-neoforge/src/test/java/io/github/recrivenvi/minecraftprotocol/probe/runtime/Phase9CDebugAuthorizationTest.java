package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class Phase9CDebugAuthorizationTest {
    @Test
    void armBindsPrincipalWorldEpochAndNamespace() {
        try (ProtocolState state = state(Set.of(
                "debug", "debug.write", "debug.player"))) {
            JsonObject armed = state.armDebug(
                    "world", "world", "epoch-a", Set.of("player"), 60_000L);
            String arm = armed.get("debugArmId").getAsString();
            state.requireDebugAuthorization(arm, "world", "epoch-a", "player", "player");

            ProtocolState.ProtocolException namespace = assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> state.requireDebugAuthorization(
                            arm, "world", "epoch-a", "player", "entity"));
            assertEquals("DEBUG_SCOPE_DENIED", namespace.code());
        }
    }

    @Test
    void scopeFailureCannotBeSuppliedByRequestData() {
        try (ProtocolState state = state(Set.of("debug", "debug.write"))) {
            JsonObject armed = state.armDebug(
                    "world", "world", "epoch-a", Set.of("player"), 60_000L);
            ProtocolState.ProtocolException failure = assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> state.requireDebugAuthorization(
                            armed.get("debugArmId").getAsString(),
                            "world", "epoch-a", "player", "player"));
            assertEquals("DEBUG_SCOPE_DENIED", failure.code());
        }
    }

    @Test
    void sessionAndWorldMismatchFailClosed() {
        try (ProtocolState state = state(Set.of(
                "debug", "debug.write", "debug.player"))) {
            JsonObject armed = state.armDebug(
                    "world", "world", "epoch-a", Set.of("player"), 60_000L);
            String arm = armed.get("debugArmId").getAsString();
            ProtocolState.ProtocolException epoch = assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> state.requireDebugAuthorization(
                            arm, "world", "epoch-b", "player", "player"));
            assertEquals("STALE_SESSION_EPOCH", epoch.code());
            assertEquals("disarmed", state.debugStatus().get("status").getAsString());
        }
        try (ProtocolState state = state(Set.of(
                "debug", "debug.write", "debug.player"))) {
            JsonObject armed = state.armDebug(
                    "world", "world", "epoch-a", Set.of("player"), 60_000L);
            ProtocolState.ProtocolException world = assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> state.requireDebugAuthorization(
                            armed.get("debugArmId").getAsString(),
                            "other", "epoch-a", "player", "player"));
            assertEquals("WORLD_FINGERPRINT_MISMATCH", world.code());
            assertEquals("disarmed", state.debugStatus().get("status").getAsString());
        }
    }

    @Test
    void gameplayActDetectsOnlyDebugWritesDuringAct() {
        try (ProtocolState state = state(Set.of("debug"))) {
            state.noteDebugMutation("arrange", "player", "player.attribute.set");
            String cleanAct = state.startGameplayAct().get("actId").getAsString();
            JsonObject clean = state.finishGameplayAct(cleanAct);
            assertFalse(clean.get("contaminated").getAsBoolean());
            assertEquals("gameplay", clean.get("gameplayEvidence").getAsString());

            String contaminatedAct = state.startGameplayAct().get("actId").getAsString();
            state.noteDebugMutation("during-act", "world", "world.block.set");
            JsonObject contaminated = state.finishGameplayAct(contaminatedAct);
            assertTrue(contaminated.get("contaminated").getAsBoolean());
            assertEquals("invalid_for_acceptance",
                    contaminated.get("gameplayEvidence").getAsString());
        }
    }

    @Test
    void cancelledOperationRetainsBatchPartialResult() {
        try (ProtocolState state = state(Set.of("read"))) {
            JsonObject partial = new JsonObject();
            partial.addProperty("type", "debug.batch.result");
            partial.addProperty("completedItems", 3);
            ProtocolState.CancellableOperationFuture future =
                    new ProtocolState.CancellableOperationFuture(() -> { }, () -> partial);
            String operationId = state.startOperation(future, false)
                    .get("operationId").getAsString();
            JsonObject cancelled = state.cancelOperation(operationId);
            assertEquals("cancelled", cancelled.get("state").getAsString());
            assertEquals(3, cancelled.getAsJsonObject("result")
                    .get("completedItems").getAsInt());
            assertTrue(future.isCancelled());
        }
    }

    private static ProtocolState state(Set<String> scopes) {
        return new ProtocolState(scopes, "principal", ignored -> { });
    }
}
