package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class UiDebugAuthorizationTest {
    private ProtocolState state(Set<String> scopes) {
        return new ProtocolState(
                scopes,
                "ui-debug-test",
                reason -> CompletableFuture.completedFuture(new JsonObject()),
                ignored -> {});
    }

    @Test
    void uiArmIsSessionBoundAndNeverGrantsWorldScopeOrLease() {
        try (var state = state(Set.of("debug", "debug.write", "debug.ui"))) {
            var arm = state.armDebug("ui-session:A", "ui-session:A", "A", Set.of("ui"), 10000);
            assertEquals("CLIENT_UI", arm.get("authority").getAsString());
            assertFalse(arm.has("worldFingerprint"));
            String id = arm.get("debugArmId").getAsString();
            state.requireDebugAuthorization(id, "ui-session:A", "A", "ui", "ui");
            assertFalse(state.controlSession().snapshot().agentControlled());
            assertThrows(ProtocolState.ProtocolException.class, () -> state.requireOperateIntent());
            assertEquals(
                    "DEBUG_SCOPE_DENIED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            state.requireDebugAuthorization(
                                                    id, "ui-session:A", "A", "world", "world"))
                            .code());
            assertEquals(
                    "STALE_SESSION_EPOCH",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            state.requireDebugAuthorization(
                                                    id, "ui-session:A", "B", "ui", "ui"))
                            .code());
        }
    }

    @Test
    void debugReadDoesNotAuthorizeUiWriteAndContextsCannotMix() {
        try (var state = state(Set.of("ui", "debug", "debug.ui.read"))) {
            assertThrows(
                    ProtocolState.ProtocolException.class, () -> state.requireDebugScope("ui"));
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> state.armDebug("world", "world", "A", Set.of("ui"), 10000));
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () ->
                            state.armDebug(
                                    "ui-session:A",
                                    "ui-session:A",
                                    "A",
                                    Set.of("ui", "world"),
                                    10000));
        }
    }
}
