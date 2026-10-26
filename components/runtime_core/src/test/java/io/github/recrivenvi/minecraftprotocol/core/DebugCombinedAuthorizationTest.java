package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.StampedLock;
import org.junit.jupiter.api.Test;

class DebugCombinedAuthorizationTest {
    @Test
    void combinedNamespacesUseOneCancellationPermitAndCannotCommitAfterCancel() throws Exception {
        for (boolean both : new boolean[] {false, true}) {
            CompletableFuture<DebugMutationAuthorization> dispatched = new CompletableFuture<>();
            CompletableFuture<JsonObject> child = new CompletableFuture<>();
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
                                    Set.of("debug", "debug.write", "debug.player", "debug.entity"),
                                    "test",
                                    ignored -> CompletableFuture.completedFuture(new JsonObject()),
                                    ignored -> {});
                    var batch = new DebugBatchEngine(service, state, ignored -> {})) {
                state.controlSession().select(AgentControlSession.Mode.OPERATE, "test");
                String arm =
                        state.armDebug(
                                        "world",
                                        "world",
                                        "epoch",
                                        both ? Set.of("player", "entity") : Set.of("player"),
                                        60000)
                                .get("debugArmId")
                                .getAsString();
                var metadata =
                        new ProtocolState.RequestMetadata(
                                "test", "v0", 0L, null, null, null, null, arm);
                var request =
                        JsonParser.parseString(
                                        "{\"items\":[{\"operation\":\"player.vehicle.set\"}],\"maxTotalDurationMs\":30000}")
                                .getAsJsonObject();
                var pending = state.operate(work -> batch.start("test", request, metadata, work));
                var authorization = dispatched.get(2, TimeUnit.SECONDS);
                var domains = new LinkedHashMap<String, String>();
                domains.put("player", "player");
                domains.put("entity", "entity");
                if (!both) {
                    assertEquals(
                            "DEBUG_SCOPE_DENIED",
                            assertThrows(
                                            ProtocolState.ProtocolException.class,
                                            () ->
                                                    authorization.authorizeAll(
                                                            "world", "epoch", domains))
                                    .code());
                    pending.cancel(false);
                    continue;
                }
                var field = authorization.getClass().getDeclaredField("cancellationBarrier");
                field.setAccessible(true);
                var barrier = (StampedLock) field.get(authorization);
                var permit = authorization.authorizeAll("world", "epoch", domains);
                assertEquals(1, barrier.getReadLockCount());
                CompletableFuture<Void> started = new CompletableFuture<>();
                var cancel =
                        CompletableFuture.runAsync(
                                () -> {
                                    started.complete(null);
                                    pending.cancel(false);
                                });
                started.get(2, TimeUnit.SECONDS);
                try {
                    assertThrows(
                            TimeoutException.class, () -> cancel.get(100, TimeUnit.MILLISECONDS));
                } finally {
                    permit.close();
                }
                cancel.get(2, TimeUnit.SECONDS);
                assertEquals(0, barrier.getReadLockCount());
                assertTrue(authorization.isCancelled());
                assertThrows(
                        java.util.concurrent.CancellationException.class,
                        () -> authorization.authorizeAll("world", "epoch", domains));
                assertFalse(state.controlSession().snapshot().agentControlled());
            }
        }
    }
}
