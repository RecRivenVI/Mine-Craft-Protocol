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

    private static ProtocolState state() {
        return new ProtocolState(
                Set.of("read", "input", "control", "event", "diagnostics", "command"),
                "test-principal",
                ignored -> { });
    }
}
