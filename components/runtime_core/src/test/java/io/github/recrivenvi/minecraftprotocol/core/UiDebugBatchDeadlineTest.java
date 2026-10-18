package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UiDebugBatchDeadlineTest {
    @Test
    void queuedUiMutationRechecksCancelAndArmAtTheOwnerCommitBarrier() throws Exception {
        for (String scenario :
                Set.of(
                        "cancel:ui.text.set",
                        "disarm:ui.text.set",
                        "cancel:ui.canvas.pan.set",
                        "disarm:ui.canvas.pan.set")) {
            String reason = scenario.substring(0, scenario.indexOf(':'));
            String operation = scenario.substring(scenario.indexOf(':') + 1);
            CompletableFuture<DebugMutationAuthorization> dispatched = new CompletableFuture<>();
            CompletableFuture<JsonObject> child = new CompletableFuture<>();
            AtomicInteger mutations = new AtomicInteger();
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("phase9cDebugMutation")) {
                                            dispatched.complete(
                                                    (DebugMutationAuthorization) args[1]);
                                            return child;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    });
            try (var state =
                            new ProtocolState(
                                    Set.of("debug", "debug.write", "debug.ui"),
                                    "test",
                                    ignored -> CompletableFuture.completedFuture(new JsonObject()),
                                    ignored -> {});
                    var batch = new DebugBatchEngine(service, state, ignored -> {})) {
                state.controlSession().select(AgentControlSession.Mode.OPERATE, "test");
                String arm =
                        state.armDebug("ui-session:A", "ui-session:A", "A", Set.of("ui"), 60000)
                                .get("debugArmId")
                                .getAsString();
                var metadata =
                        new ProtocolState.RequestMetadata(
                                "test", "v0", 0L, null, null, null, null, arm);
                var request =
                        JsonParser.parseString(
                                        "{\"items\":[{\"operation\":\"%s\"}],\"maxTotalDurationMs\":30000}"
                                                .formatted(operation))
                                .getAsJsonObject();
                var pending = state.operate(work -> batch.start("test", request, metadata, work));
                var authorization = dispatched.get(2, TimeUnit.SECONDS);
                if (reason.equals("cancel")) pending.cancel(false);
                else state.disarmDebug("test");
                org.junit.jupiter.api.function.Executable commit =
                        () -> {
                            try (var permit =
                                    authorization.authorize("ui-session:A", "A", "ui", "ui")) {
                                mutations.incrementAndGet();
                            }
                        };
                if (reason.equals("cancel")) {
                    assertTrue(authorization.isCancelled());
                    assertThrows(CancellationException.class, commit);
                } else {
                    assertEquals(
                            "DEBUG_NOT_ARMED",
                            assertThrows(ProtocolState.ProtocolException.class, commit).code());
                }
                assertEquals(0, mutations.get());
                assertFalse(state.controlSession().snapshot().agentControlled());
                pending.cancel(false);
            }
        }
    }

    @Test
    void queuedClientMutationCannotEnterAfterBatchDeadline() throws Exception {
        CompletableFuture<DebugMutationAuthorization> dispatched = new CompletableFuture<>();
        CompletableFuture<JsonObject> child = new CompletableFuture<>();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("phase9cDebugMutation")) {
                                        dispatched.complete((DebugMutationAuthorization) args[1]);
                                        return child;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        try (var state =
                        new ProtocolState(
                                Set.of("debug", "debug.write", "debug.ui"),
                                "test",
                                reason -> CompletableFuture.completedFuture(new JsonObject()),
                                ignored -> {});
                var batch = new DebugBatchEngine(service, state, ignored -> {})) {
            state.controlSession().select(AgentControlSession.Mode.OPERATE, "test");
            String arm =
                    state.armDebug("ui-session:A", "ui-session:A", "A", Set.of("ui"), 60000)
                            .get("debugArmId")
                            .getAsString();
            long deadline = System.currentTimeMillis() + 500;
            var metadata =
                    new ProtocolState.RequestMetadata(
                            "test", "v0", deadline, null, null, null, null, arm);
            JsonObject request =
                    JsonParser.parseString(
                                    """
                {"items":[{"operation":"ui.text.set"}],"maxTotalDurationMs":30000}
                """)
                            .getAsJsonObject();
            var pending = state.operate(work -> batch.start("test", request, metadata, work));
            DebugMutationAuthorization authorization = dispatched.get(2, TimeUnit.SECONDS);
            while (System.currentTimeMillis() <= deadline) Thread.sleep(5);
            assertEquals(
                    "DEADLINE_EXCEEDED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> authorization.authorize("ui-session:A", "A", "ui", "ui"))
                            .code());
            pending.cancel(false);
            assertTrue(authorization.isCancelled());
        }
    }
}
