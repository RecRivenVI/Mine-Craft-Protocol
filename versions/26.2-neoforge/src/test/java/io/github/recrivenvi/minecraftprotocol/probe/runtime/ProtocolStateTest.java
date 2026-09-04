package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ProtocolStateTest {
    @Test
    void waitObservesCommittedTerminalState() throws Exception {
        try (ProtocolState state = state()) {
            CompletableFuture<JsonObject> nativeFuture = new CompletableFuture<>();
            JsonObject started = state.startOperation(nativeFuture, true);
            CompletableFuture<JsonObject> waited = state.waitOperation(
                    started.get("operationId").getAsString(), 1_000L);
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
            CompletableFuture<JsonObject> deadline = state.applyDeadline(
                    nativeFuture, System.currentTimeMillis() + 25L);
            assertThrows(ExecutionException.class, () -> deadline.get(1L, TimeUnit.SECONDS));
            assertTrue(nativeFuture.isCancelled());
        }
    }

    @Test
    void activeOperationRegistryIsBounded() {
        try (ProtocolState state = state()) {
            for (int index = 0; index < 16; index++) {
                state.startOperation(new CompletableFuture<JsonObject>(), false);
            }
            ProtocolState.ProtocolException failure = assertThrows(
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
            JsonObject entry = state.auditSnapshot(1).getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("test-principal", entry.get("principalId").getAsString());
            assertEquals("connection", entry.get("connectionId").getAsString());
        }
    }

    @Test
    void explicitRequestCancellationRetiresDeepObservationWork() {
        try (ProtocolState state = state()) {
            DeepObservationRequestContext context = new DeepObservationRequestContext(
                    Set.of("read"),
                    "test-principal",
                    "deep-request",
                    "connection",
                    System.currentTimeMillis() + 5_000L,
                    ignored -> { });
            CompletableFuture<JsonObject> providerWork = context.track(new CompletableFuture<>());
            state.registerDeepObservation("deep-request", context);
            JsonObject cancelled = state.cancelDeepObservation("deep-request");
            assertEquals("cancelled", cancelled.get("status").getAsString());
            assertTrue(providerWork.isCancelled());
            assertEquals("already_terminal_or_unknown",
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
                                Set.of("read"), "principal", "request-" + index,
                                "connection", 0L, ignored -> { }));
            }
            ProtocolState.ProtocolException failure = assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> state.registerDeepObservation(
                            "overflow",
                            new DeepObservationRequestContext(
                                    Set.of("read"), "principal", "overflow",
                                    "connection", 0L, ignored -> { })));
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
            ProtocolState.ProtocolException blocked = assertThrows(
                    ProtocolState.ProtocolException.class, () -> state.requireLease("old-lease"));
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

    private static ProtocolState state() {
        return new ProtocolState(
                Set.of("read", "input", "control", "event", "diagnostics", "command"),
                "test-principal",
                ignored -> { });
    }
}
