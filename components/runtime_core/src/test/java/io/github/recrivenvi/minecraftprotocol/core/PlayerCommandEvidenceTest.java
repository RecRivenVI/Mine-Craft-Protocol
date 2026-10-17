package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlayerCommandEvidenceTest {
    private static final class ReceiptFixture implements AutoCloseable {
        final ProtocolState state =
                new ProtocolState(java.util.Set.of("read"), "receipt-test", ignored -> {});
        final CountDownLatch submitted = new CountDownLatch(1);
        final AtomicInteger submissions = new AtomicInteger();
        volatile boolean failRelease;
        volatile PlayerCommandPolicy.Effect effect = PlayerCommandPolicy.Effect.STATE_MUTATION;
        volatile CompletableFuture<JsonObject> cleanup =
                CompletableFuture.completedFuture(new JsonObject());
        final AutomationEngine engine;

        ReceiptFixture() {
            var reference = new java.util.concurrent.atomic.AtomicReference<AutomationEngine>();
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("releaseAllInput"))
                                            return cleanup;
                                        JsonObject result = new JsonObject();
                                        if (method.getName().equals("key")) {
                                            if ((int) args[2] == 1) {
                                                var permit = InputDispatchContext.capture();
                                                permit.check();
                                                var receipt =
                                                        PlayerCommandEvidence.classified(
                                                                new JsonObject(),
                                                                effect,
                                                                "GAME_ROUTED_RAW");
                                                receipt.addProperty(
                                                        "gestureId", permit.gestureId());
                                                reference.get().noteCommandSubmission(receipt);
                                                submissions.incrementAndGet();
                                                submitted.countDown();
                                                result.addProperty(
                                                        "privateTestData",
                                                        "MUST_NOT_LEAK_IN_PARTIAL_PROVENANCE");
                                            } else if (failRelease)
                                                return CompletableFuture.failedFuture(
                                                        new ProtocolState.ProtocolException(
                                                                "TEST_INPUT_FAILED",
                                                                409,
                                                                "native release failed"));
                                        }
                                        return CompletableFuture.completedFuture(result);
                                    });
            engine = new AutomationEngine(service);
            reference.set(engine);
        }

        String start(String steps, long timeout) {
            var request =
                    com.google.gson.JsonParser.parseString(
                                    "{\"steps\":[" + steps + "],\"timeoutMs\":" + timeout + "}")
                            .getAsJsonObject();
            return state.startOperation(engine.executePipeline(request, () -> {}), true)
                    .get("operationId")
                    .getAsString();
        }

        JsonObject terminal(String id) throws Exception {
            return state.waitOperation(id, 5000).get(6, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            cleanup.complete(new JsonObject());
            engine.close();
            state.close();
        }
    }

    private static final String DOWN = "{\"type\":\"key\",\"key\":257,\"action\":1}";
    private static final String UP = "{\"type\":\"key\",\"key\":257,\"action\":0}";

    private static void assertPartialCommand(JsonObject operation) {
        assertTrue(
                operation.has("result"),
                "Terminal input failure/cancellation must retain known submission provenance");
        var result = operation.getAsJsonObject("result");
        assertTrue(result.get("partial").getAsBoolean());
        assertEquals(1, result.getAsJsonArray("commandSubmissions").size());
        assertTrue(result.get("evidenceContaminated").getAsBoolean());
        assertEquals("DEBUG_PRIVILEGED", result.get("evidenceAuthority").getAsString());
        assertFalse(result.get("commandEffectsConfirmed").getAsBoolean());
        assertFalse(
                result.toString().contains("MUST_NOT_LEAK"),
                "Only provenance, not arbitrary partial step data");
    }

    @Test
    void failedPipelineRetainsAlreadySubmittedCommands() throws Exception {
        try (var fixture = new ReceiptFixture()) {
            fixture.failRelease = true;
            var terminal = fixture.terminal(fixture.start(DOWN + "," + UP, 4000));
            assertEquals("failed", terminal.get("state").getAsString());
            assertEquals("TEST_INPUT_FAILED", terminal.get("errorCode").getAsString());
            assertPartialCommand(terminal);
            assertTrue(terminal.get("commandSubmissionsComplete").getAsBoolean());
        }
    }

    @Test
    void cancellationKeepsLiveProvenanceWithoutPretendingCleanupHasDrained() throws Exception {
        try (var fixture = new ReceiptFixture()) {
            fixture.cleanup = new CompletableFuture<>();
            String id =
                    fixture.start(
                            DOWN + ",{\"type\":\"delay\",\"durationMs\":10000}," + DOWN, 20000);
            assertTrue(fixture.submitted.await(3, TimeUnit.SECONDS));
            var cancelled = fixture.state.cancelOperation(id);
            assertEquals("cancelled", cancelled.get("state").getAsString());
            assertPartialCommand(cancelled);
            assertFalse(cancelled.get("commandSubmissionsComplete").getAsBoolean());
            cancelled.getAsJsonObject("result").getAsJsonArray("commandSubmissions").remove(0);
            assertPartialCommand(fixture.state.operationStatus(id));
            fixture.cleanup.complete(new JsonObject());
            assertTrue(
                    fixture.state
                            .operationStatus(id)
                            .get("commandSubmissionsComplete")
                            .getAsBoolean());
            Thread.sleep(100);
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test
    void failedQueryDoesNotInventMutationAuthorityOrCleanGameplayAcceptance() throws Exception {
        try (var fixture = new ReceiptFixture()) {
            fixture.effect = PlayerCommandPolicy.Effect.INFORMATION;
            fixture.failRelease = true;
            var terminal = fixture.terminal(fixture.start(DOWN + "," + UP, 4000));
            assertEquals("failed", terminal.get("state").getAsString());
            var result = terminal.getAsJsonObject("result");
            assertFalse(result.get("evidenceContaminated").getAsBoolean());
            assertEquals("PLAYTEST", result.get("evidenceAuthority").getAsString());
            var receipt = result.getAsJsonArray("commandSubmissions").get(0).getAsJsonObject();
            assertEquals("INFORMATION", receipt.get("effectClassification").getAsString());
            assertFalse(receipt.get("effectConfirmed").getAsBoolean());
            assertFalse(receipt.get("gameplayAcceptanceEligible").getAsBoolean());
        }
    }

    @Test
    void deadlineAndCleanupFailureRetainProvenanceWithAccurateLifecycle() throws Exception {
        for (boolean cleanupFailure : new boolean[] {false, true})
            try (var fixture = new ReceiptFixture()) {
                if (cleanupFailure)
                    fixture.cleanup =
                            CompletableFuture.failedFuture(
                                    new ProtocolState.ProtocolException(
                                            "TEST_CLEANUP_FAILED", 409, "cleanup failed"));
                String steps =
                        cleanupFailure
                                ? DOWN + "," + UP
                                : DOWN + ",{\"type\":\"delay\",\"durationMs\":10000}";
                var terminal = fixture.terminal(fixture.start(steps, cleanupFailure ? 4000 : 1500));
                assertEquals(
                        cleanupFailure ? "failed" : "timed_out",
                        terminal.get("state").getAsString());
                assertPartialCommand(terminal);
                assertEquals(
                        !cleanupFailure, terminal.get("commandSubmissionsComplete").getAsBoolean());
            }
    }

    @Test
    void queuedCancellationDoesNotInventCommandSubmission() {
        try (var fixture = new ReceiptFixture()) {
            fixture.start("{\"type\":\"delay\",\"durationMs\":10000}", 20000);
            var cancelled = fixture.state.cancelOperation(fixture.start(DOWN, 20000));
            assertEquals("cancelled", cancelled.get("state").getAsString());
            assertFalse(cancelled.has("result"));
            assertEquals(0, fixture.submissions.get());
            assertTrue(cancelled.get("commandSubmissionsComplete").getAsBoolean());
        }
    }

    @Test
    void rawKeyReleaseAndUiCleanupDoNotEraseAnEarlierCommandReceipt() throws Exception {
        var engineRef = new java.util.concurrent.atomic.AtomicReference<AutomationEngine>();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("key") && (int) args[2] == 1) {
                                        var permit = InputDispatchContext.capture();
                                        assertNotNull(permit);
                                        permit.check();
                                        var receipt =
                                                PlayerCommandEvidence.classified(
                                                        new JsonObject(),
                                                        PlayerCommandPolicy.Effect.STATE_MUTATION,
                                                        "GAME_ROUTED_RAW");
                                        receipt.addProperty("gestureId", permit.gestureId());
                                        engineRef.get().noteCommandSubmission(receipt);
                                    }
                                    return CompletableFuture.completedFuture(new JsonObject());
                                });
        try (var engine = new AutomationEngine(service)) {
            engineRef.set(engine);
            var request =
                    com.google.gson.JsonParser.parseString(
                                    "{\"steps\":[{\"type\":\"key.tap\",\"key\":257,\"holdMs\":0}],\"cleanupOnComplete\":true}")
                            .getAsJsonObject();
            JsonObject result = engine.executePipeline(request, () -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(1, result.getAsJsonArray("commandSubmissions").size());
            assertTrue(result.get("evidenceContaminated").getAsBoolean());
            assertEquals("DEBUG_PRIVILEGED", result.get("evidenceAuthority").getAsString());
            var step =
                    result.getAsJsonArray("steps")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("result");
            assertEquals(1, step.getAsJsonArray("commandSubmissions").size());
            assertEquals(
                    "STATE_MUTATION",
                    step.getAsJsonArray("commandSubmissions")
                            .get(0)
                            .getAsJsonObject()
                            .get("effectClassification")
                            .getAsString());
        }
    }

    @Test
    void queuedCancelledCommandNeverReachesSubmissionObserver() throws Exception {
        var submitted = new AtomicInteger();
        var firstSubmitted = new CountDownLatch(1);
        var firstResult = new CompletableFuture<JsonObject>();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("playerCommand")) {
                                        assertNotNull(InputDispatchContext.capture());
                                        InputDispatchContext.capture().check();
                                        if (!args[1].equals("submit")) {
                                            JsonObject phase = new JsonObject();
                                            phase.addProperty("ready", true);
                                            phase.addProperty("typed", true);
                                            phase.addProperty("complete", true);
                                            return CompletableFuture.completedFuture(phase);
                                        }
                                        submitted.incrementAndGet();
                                        firstSubmitted.countDown();
                                        return firstResult;
                                    }
                                    return CompletableFuture.completedFuture(new JsonObject());
                                });
        try (var engine = new AutomationEngine(service)) {
            var first = engine.playerCommand("help", () -> {}, java.util.function.Supplier::get);
            assertTrue(firstSubmitted.await(3, TimeUnit.SECONDS));
            var queued =
                    engine.playerCommand(
                            "give @s stone", () -> {}, java.util.function.Supplier::get);
            queued.cancel(false);
            firstResult.complete(
                    PlayerCommandEvidence.classified(
                            new JsonObject(),
                            PlayerCommandPolicy.Effect.COMMUNICATION,
                            "GAME_ROUTED_RAW"));
            assertFalse(first.get(3, TimeUnit.SECONDS).get("effectConfirmed").getAsBoolean());
            assertTrue(queued.isCancelled());
        }
        assertEquals(1, submitted.get());
    }

    @Test
    void failureBeforeSubmissionDoesNotPretendThereWasAPacket() throws Exception {
        var submitted = new AtomicInteger();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("playerCommand"))
                                        return CompletableFuture.failedFuture(
                                                new ProtocolState.ProtocolException(
                                                        "INVALID_PLAYER_COMMAND",
                                                        400,
                                                        "Invalid line"));
                                    return CompletableFuture.completedFuture(new JsonObject());
                                });
        try (var engine = new AutomationEngine(service)) {
            var result =
                    engine.playerCommand(
                            "bad\ncommand", () -> {}, java.util.function.Supplier::get);
            var failure =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> result.get(3, TimeUnit.SECONDS));
            assertEquals(
                    "INVALID_PLAYER_COMMAND",
                    assertInstanceOf(ProtocolState.ProtocolException.class, failure.getCause())
                            .code());
        }
        assertEquals(0, submitted.get());
    }
}
