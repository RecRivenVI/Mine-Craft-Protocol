package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class InputReleasedConditionTest {
    private static final Set<String> SCOPES = Set.of("read", "diagnostics", "control");

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static JsonObject condition() {
        return json("{\"type\":\"input.released\"}");
    }

    private static JsonObject counts(int keys, int buttons) {
        JsonObject state = new JsonObject();
        state.addProperty("pressedKeyCount", keys);
        state.addProperty("pressedButtonCount", buttons);
        return state;
    }

    private static ConditionEngine engine(
            ProtocolState state, Supplier<CompletableFuture<JsonObject>> input) {
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    assertEquals("inputState", method.getName());
                                    return input.get();
                                });
        return new ConditionEngine(service, state, null, null, null, null);
    }

    private static ProtocolState.ProtocolException failure(CompletableFuture<?> work) {
        Throwable cause = assertThrows(CompletionException.class, work::join);
        while (cause instanceof CompletionException && cause.getCause() != null)
            cause = cause.getCause();
        return assertInstanceOf(ProtocolState.ProtocolException.class, cause);
    }

    private static void operate(ProtocolState state) {
        state.selectMode(
                        AgentControlSession.Mode.OPERATE,
                        state.modeStatus().getAsJsonObject("modeVersion"),
                        null,
                        "input-released-test",
                        "test-connection")
                .join();
    }

    @Test
    void requiresReadAndExistingDiagnosticsScopeBeforeAnySnapshot() {
        AtomicInteger reads = new AtomicInteger();
        for (Set<String> scopes :
                List.of(Set.<String>of(), Set.of("read"), Set.of("diagnostics"))) {
            try (var state = new ProtocolState(scopes, "test", ignored -> {});
                    var conditions =
                            engine(
                                    state,
                                    () -> {
                                        reads.incrementAndGet();
                                        return CompletableFuture.completedFuture(counts(0, 0));
                                    })) {
                assertEquals(
                        "SCOPE_DENIED",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () -> conditions.assertThat(condition()))
                                .code());
                assertEquals(
                        "SCOPE_DENIED",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () -> conditions.waitUntil(condition(), 1000))
                                .code());
            }
        }
        assertEquals(0, reads.get());
    }

    @Test
    void exposesOnlyHeldCountsAndDoesNotClaimQueueIdleOrHumanKeyState() {
        JsonObject source =
                json(
                        "{\"pressedKeyCount\":0,\"pressedButtonCount\":0,"
                                + "\"pressedKeys\":[87],\"pressedButtons\":[1],"
                                + "\"inputQueueDepth\":3,\"inputSequenceActive\":true,"
                                + "\"controlSessionId\":\"private-owner\",\"mouseX\":456}");
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var conditions = engine(state, () -> CompletableFuture.completedFuture(source))) {
            JsonObject result = conditions.assertThat(condition()).join();
            assertTrue(result.get("passed").getAsBoolean());
            assertEquals(condition(), result.get("condition"));
            assertEquals(counts(0, 0), result.getAsJsonObject("evidence"));
            assertEquals(8, source.size(), "Projection must not remove native snapshot fields");
            assertFalse(result.toString().contains("private-owner"));
            assertFalse(result.has("dataCategory"));
            assertFalse(result.has("domain"));
        }
    }

    @Test
    void eitherHeldKeysOrHeldButtonsPreventsReleaseAssertion() {
        for (JsonObject source : List.of(counts(1, 0), counts(0, 1), counts(2, 3))) {
            try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                    var conditions =
                            engine(state, () -> CompletableFuture.completedFuture(source))) {
                assertEquals(
                        "ASSERTION_FAILED", failure(conditions.assertThat(condition())).code());
            }
        }
    }

    @Test
    void noSelectorCategoryOrExpectedOverrideIsAccepted() {
        AtomicInteger reads = new AtomicInteger();
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var conditions =
                        engine(
                                state,
                                () -> {
                                    reads.incrementAndGet();
                                    return CompletableFuture.completedFuture(counts(0, 0));
                                })) {
            for (String name :
                    List.of("sessionId", "instanceId", "leaseId", "category", "expected")) {
                JsonObject request = condition();
                request.addProperty(name, "other-owner");
                assertEquals(
                        "INVALID_CONDITION",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () -> conditions.assertThat(request))
                                .code());
            }
        }
        assertEquals(0, reads.get());
    }

    @Test
    void missingMalformedNegativeFractionalAndOverflowCountsFailClosed() {
        List<JsonObject> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(new JsonObject());
        invalid.add(json("{\"pressedKeyCount\":0}"));
        for (String value :
                List.of("null", "\"0\"", "true", "[]", "{}", "-1", "0.5", "2147483648")) {
            invalid.add(json("{\"pressedKeyCount\":" + value + ",\"pressedButtonCount\":0}"));
            invalid.add(json("{\"pressedKeyCount\":0,\"pressedButtonCount\":" + value + "}"));
        }
        JsonObject nonFinite = counts(0, 0);
        nonFinite.add("pressedKeyCount", new JsonPrimitive(Double.NaN));
        invalid.add(nonFinite);
        for (JsonObject source : invalid) {
            try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                    var conditions =
                            engine(state, () -> CompletableFuture.completedFuture(source))) {
                assertEquals(
                        "INPUT_STATE_UNAVAILABLE",
                        failure(conditions.assertThat(condition())).code());
            }
        }
    }

    @Test
    void waitCompletesFromObservedReleaseNotFixedDelay() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var conditions =
                        engine(
                                state,
                                () ->
                                        CompletableFuture.completedFuture(
                                                counts(reads.getAndIncrement() == 0 ? 1 : 0, 0)))) {
            JsonObject result = conditions.waitUntil(condition(), 1000).get(2, TimeUnit.SECONDS);
            assertTrue(result.get("passed").getAsBoolean());
            assertTrue(result.get("waited").getAsBoolean());
            assertEquals(counts(0, 0), result.getAsJsonObject("evidence"));
            assertEquals(2, reads.get());
        }
    }

    @Test
    void heldInputReachesExistingWaitTimeoutWithoutSideEffects() throws Exception {
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var conditions =
                        engine(state, () -> CompletableFuture.completedFuture(counts(0, 1)))) {
            CompletableFuture<JsonObject> wait = conditions.waitUntil(condition(), 1);
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> wait.get(2, TimeUnit.SECONDS));
            assertEquals("WAIT_TIMEOUT", failure(wait).code());
        }
    }

    @Test
    void worksWithoutLeaseInReadOperateAndAfterManualRevocation() {
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var conditions =
                        engine(state, () -> CompletableFuture.completedFuture(counts(0, 0)))) {
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
            assertTrue(conditions.assertThat(condition()).join().get("passed").getAsBoolean());
            operate(state);
            assertTrue(conditions.assertThat(condition()).join().get("passed").getAsBoolean());
            state.acquireLease(60000);
            assertEquals("TAKEOVER", state.modeStatus().get("mode").getAsString());
            assertTrue(conditions.assertThat(condition()).join().get("passed").getAsBoolean());
            state.revokeHumanControl();
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
            assertTrue(conditions.assertThat(condition()).join().get("passed").getAsBoolean());
            operate(state);
            assertTrue(conditions.assertThat(condition()).join().get("passed").getAsBoolean());
            assertTrue(state.modeStatus().get("reconsentRequired").getAsBoolean());
        }
    }

    @Test
    void activeWaitSurvivesLeaseLossAndPublishesReleasedSnapshot() throws Exception {
        CompletableFuture<JsonObject> source = new CompletableFuture<>();
        try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                var conditions = engine(state, () -> source)) {
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            CompletableFuture<JsonObject> wait = conditions.waitUntil(condition(), 1000);
            state.releaseLease(lease, "test_lease_loss");
            source.complete(counts(0, 0));
            assertTrue(wait.get(2, TimeUnit.SECONDS).get("passed").getAsBoolean());
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
        }
    }

    @Test
    void cancellingWaitAndAssertCancelsTheNativeRead() {
        for (boolean wait : List.of(false, true)) {
            CompletableFuture<JsonObject> source = new CompletableFuture<>();
            try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                    var conditions = engine(state, () -> source)) {
                CompletableFuture<JsonObject> result =
                        wait
                                ? conditions.waitUntil(condition(), 1000)
                                : conditions.assertThat(condition());
                assertTrue(result.cancel(false));
                assertTrue(source.isCancelled());
            }
        }
    }

    @Test
    void shutdownRejectsLateSuccessAndSecretFailureBeforePublication() {
        for (boolean wait : List.of(false, true)) {
            for (boolean failed : List.of(false, true)) {
                CompletableFuture<JsonObject> source = new CompletableFuture<>();
                try (var state = new ProtocolState(SCOPES, "test", ignored -> {});
                        var conditions = engine(state, () -> source)) {
                    CompletableFuture<JsonObject> result =
                            wait
                                    ? conditions.waitUntil(condition(), 1000)
                                    : conditions.assertThat(condition());
                    state.close();
                    if (failed)
                        source.completeExceptionally(new IllegalStateException("private-key-87"));
                    else source.complete(counts(0, 0));
                    ProtocolState.ProtocolException error = failure(result);
                    assertEquals("CONTROL_SESSION_CLOSED", error.code());
                    assertFalse(error.getMessage().contains("private-key-87"));
                }
            }
        }
    }
}
