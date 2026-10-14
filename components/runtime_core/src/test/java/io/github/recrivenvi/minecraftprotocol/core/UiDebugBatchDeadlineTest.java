package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UiDebugBatchDeadlineTest {
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
