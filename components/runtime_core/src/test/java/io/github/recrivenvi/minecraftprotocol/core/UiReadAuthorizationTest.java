package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class UiReadAuthorizationTest {
    private static ProtocolState state(Set<String> scopes) {
        return new ProtocolState(
                scopes,
                "ui-read-test",
                reason -> CompletableFuture.completedFuture(new JsonObject()),
                ignored -> {});
    }

    @Test
    void userReadNeedsOnlyUiAndNeverAcquiresInputOrDebugAuthority() {
        try (ProtocolState state = state(Set.of("ui"))) {
            UiDataView.requireReadScopes(UiDataView.Category.USER, state::requireScope);
            assertEquals("READ", state.controlSession().snapshot().mode().name());
            assertFalse(state.hasScope("input"));
            assertFalse(state.hasScope("debug"));
            assertFalse(state.controlSession().snapshot().agentControlled());
        }
    }

    @Test
    void debugReadRequiresSeparateScopeButNoWriteArmLeaseOrOperate() {
        for (Set<String> scopes :
                Set.of(Set.of("ui"), Set.of("ui", "debug"), Set.of("ui", "debug.ui.read"))) {
            try (ProtocolState state = state(scopes)) {
                assertEquals(
                        "SCOPE_DENIED",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () ->
                                                UiDataView.requireReadScopes(
                                                        UiDataView.Category.DEBUG,
                                                        state::requireScope))
                                .code());
            }
        }
        try (ProtocolState state = state(Set.of("ui", "debug", "debug.ui.read"))) {
            UiDataView.requireReadScopes(UiDataView.Category.DEBUG, state::requireScope);
            assertEquals("READ", state.controlSession().snapshot().mode().name());
            assertFalse(state.hasScope("debug.write"));
            assertFalse(state.controlSession().snapshot().agentControlled());
        }
    }
}
