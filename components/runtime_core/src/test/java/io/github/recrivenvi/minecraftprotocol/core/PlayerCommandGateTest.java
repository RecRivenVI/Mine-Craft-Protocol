package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PlayerCommandGateTest {
    static final class World implements AutoCloseable {
        final AgentControlSession control = new AgentControlSession();
        final AtomicInteger observed = new AtomicInteger();
        final AtomicInteger parsed = new AtomicInteger();
        boolean denied;
        final PlayerCommandGate gate =
                new PlayerCommandGate(
                        control,
                        (id, routed) -> {
                            if (denied)
                                throw new ProtocolState.ProtocolException(
                                        "SCOPE_DENIED", 403, "command scope missing");
                        },
                        receipt -> observed.incrementAndGet());

        PlayerCommandPolicy.Parsed parse(String root) {
            parsed.incrementAndGet();
            return PlayerCommandPolicyTest.parsed(PlayerCommandPolicyTest.frame(root));
        }

        void dispatch(Consumer<PlayerCommandGate.Dispatch> body) {
            var pending = new CompletableFuture<JsonObject>();
            try {
                InputDispatchContext.run(
                        "test-gesture",
                        () -> {},
                        () -> {
                            try (var dispatch =
                                    gate.enter(
                                            control.snapshot(), InputDispatchContext.capture())) {
                                body.accept(dispatch);
                            }
                            return pending;
                        });
            } finally {
                pending.cancel(false);
            }
        }

        @Override
        public void close() {
            gate.close();
            control.close();
        }
    }

    @Test
    void humanOutsideTakeoverAndAfterRuntimeCloseIsNotIntercepted() {
        try (var world = new World()) {
            assertTrue(world.gate.allow("stop", false, true, () -> world.parse("stop")));
            world.gate.close();
            assertTrue(world.gate.allow("stop", false, true, () -> world.parse("stop")));
            assertEquals(0, world.parsed.get());
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void testManagementIsDeniedBeforeParseOrSubmissionButDoesNotInterceptHumansInRead() {
        for (var kind :
                java.util.List.of(
                        PlayerCommandGate.ConfigurationKind.TEST_BLOCK,
                        PlayerCommandGate.ConfigurationKind.TEST_INSTANCE)) {
            try (var world = new World()) {
                assertTrue(
                        world.gate.allowConfiguration("", false, kind, () -> world.parse("help")));
                world.control.acquire();
                world.dispatch(
                        dispatch -> {
                            assertFalse(
                                    world.gate.allowConfiguration(
                                            "", true, kind, () -> world.parse("help")));
                            assertEquals(
                                    "PLAYER_COMMAND_OUT_OF_SCOPE",
                                    assertThrows(
                                                    ProtocolState.ProtocolException.class,
                                                    dispatch::check)
                                            .code());
                        });
                assertEquals(0, world.parsed.get());
                assertEquals(0, world.observed.get());
            }
        }
    }

    @Test
    void originHintDoesNotGrantAnInputPermitOrScopes() {
        try (var world = new World()) {
            world.control.acquire();
            assertFalse(world.gate.allow("help", true, true, () -> world.parse("help")));
            world.denied = true;
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allow("help", true, true, () -> world.parse("help")));
                        assertEquals(
                                "SCOPE_DENIED",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.parsed.get());
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void denialIsDeferredUntilTheNativeCallbackReturns() {
        try (var world = new World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allow("stop", true, false, () -> world.parse("stop")));
                        assertEquals(
                                "PLAYER_COMMAND_OUT_OF_SCOPE",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void entryAndPacketAreRecheckedButOnlyPacketProducesEvidence() {
        try (var world = new World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertTrue(
                                world.gate.allow("help", true, false, () -> world.parse("help")));
                        assertEquals(0, world.observed.get());
                        assertTrue(world.gate.allow("help", true, true, () -> world.parse("help")));
                        assertEquals(
                                "GAME_ROUTED_RAW",
                                dispatch.receipt().get("mechanism").getAsString());
                        assertFalse(dispatch.receipt().get("evidenceContaminated").getAsBoolean());
                        assertFalse(dispatch.receipt().get("effectConfirmed").getAsBoolean());
                    });
            assertEquals(2, world.parsed.get());
            assertEquals(1, world.observed.get());
        }
    }

    @Test
    void changedEffectAtFinalPacketCannotUseEntryClassification() {
        try (var world = new World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertTrue(
                                world.gate.allow("help", true, false, () -> world.parse("help")));
                        assertFalse(
                                world.gate.allow("help", true, true, () -> world.parse("stop")));
                        assertThrows(ProtocolState.ProtocolException.class, dispatch::check);
                    });
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void unknownOriginIsRejectedAndRoutedStateMutationIsDiagnostic() {
        try (var world = new World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allow("help", false, true, () -> world.parse("help")));
                        assertEquals(
                                "PLAYER_COMMAND_ORIGIN_UNAVAILABLE",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            world.dispatch(
                    dispatch -> {
                        assertTrue(
                                world.gate.allow(
                                        "give @s stone", true, true, () -> world.parse("give")));
                        assertEquals(
                                "DEBUG_PRIVILEGED", dispatch.receipt().get("mode").getAsString());
                    });
        }
    }

    @Test
    void finishedEffectPermitCannotBeReplayedEvenWithAnActiveLease() {
        try (var world = new World()) {
            world.control.acquire();
            var permit = new AtomicReference<InputDispatchContext.Permit>();
            InputDispatchContext.run(
                    "finished",
                    () -> {},
                    () -> {
                        permit.set(InputDispatchContext.capture());
                        return CompletableFuture.completedFuture(new JsonObject());
                    });
            try (var dispatch = world.gate.enter(world.control.snapshot(), permit.get())) {
                assertEquals(
                        "INPUT_DISPATCH_EXPIRED",
                        assertThrows(ProtocolState.ProtocolException.class, dispatch::checkEffect)
                                .code());
                assertFalse(world.gate.allow("help", true, true, () -> world.parse("help")));
                assertEquals(
                        "INPUT_DISPATCH_EXPIRED",
                        assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                .code());
            }
            assertEquals(0, world.observed.get());
            assertNull(InputDispatchContext.capture());
            var checks = new AtomicInteger();
            assertEquals(
                    "INPUT_DISPATCH_EXPIRED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            permit.get()
                                                    .beforeInput(
                                                            (screen, menu) ->
                                                                    checks.incrementAndGet()))
                            .code());
            assertEquals(
                    0, checks.get(), "An expired effect must not reach a new owner-thread check");
        }
    }

    @Test
    void nestedScopeAndExceptionalFactoryNeverLeakOrigin() {
        try (var world = new World()) {
            world.control.acquire();
            world.dispatch(
                    outer -> {
                        world.dispatch(inner -> assertNotSame(outer, inner));
                        assertSame(outer, world.gate.dispatch());
                    });
            assertNull(world.gate.dispatch());
            assertNull(InputDispatchContext.capture());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            InputDispatchContext.run(
                                    "failed",
                                    () -> {},
                                    () -> {
                                        throw new IllegalStateException();
                                    }));
            assertNull(InputDispatchContext.capture());
        }
    }

    @Test
    void cancellationRevocationAndRuntimeCloseDenyBeforePacket() {
        try (var world = new World()) {
            world.control.acquire();
            var permit = new AtomicReference<InputDispatchContext.Permit>();
            var pending = new CompletableFuture<JsonObject>();
            InputDispatchContext.run(
                    "cancelled",
                    () -> {},
                    () -> {
                        permit.set(InputDispatchContext.capture());
                        return pending;
                    });
            pending.cancel(false);
            try (var dispatch = world.gate.enter(world.control.snapshot(), permit.get())) {
                assertEquals(
                        "INPUT_DISPATCH_EXPIRED",
                        assertThrows(ProtocolState.ProtocolException.class, dispatch::checkEffect)
                                .code());
                assertFalse(world.gate.allow("help", true, true, () -> world.parse("help")));
                assertEquals(
                        "INPUT_DISPATCH_EXPIRED",
                        assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                .code());
            }
            world.dispatch(
                    dispatch -> {
                        world.control.manuallyRevoke();
                        assertFalse(
                                world.gate.allow("help", true, true, () -> world.parse("help")));
                        assertTrue(world.control.snapshot().reconsentRequired());
                    });
            world.gate.close();
            assertFalse(world.gate.allow("help", true, true, () -> world.parse("help")));
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void nativeHelperCannotSubmitDuringTypingOrReplaceTheExpectedCommand() {
        try (var world = new World()) {
            world.control.acquire();
            for (boolean submit : new boolean[] {false, true}) {
                InputDispatchContext.run(
                        "helper",
                        () -> {},
                        null,
                        "help",
                        submit,
                        () -> {
                            try (var dispatch =
                                    world.gate.enter(
                                            world.control.snapshot(),
                                            InputDispatchContext.capture())) {
                                assertTrue(
                                        world.gate.allow(
                                                "help", true, false, () -> world.parse("help")));
                                assertEquals(
                                        submit,
                                        world.gate.allow(
                                                "help", true, true, () -> world.parse("help")));
                                if (!submit)
                                    assertEquals(
                                            "PLAYER_COMMAND_CONTEXT_CHANGED",
                                            assertThrows(
                                                            ProtocolState.ProtocolException.class,
                                                            dispatch::check)
                                                    .code());
                            }
                            return CompletableFuture.completedFuture(new JsonObject());
                        });
            }
            InputDispatchContext.run(
                    "helper",
                    () -> {},
                    null,
                    "help",
                    true,
                    () -> {
                        try (var dispatch =
                                world.gate.enter(
                                        world.control.snapshot(), InputDispatchContext.capture())) {
                            assertFalse(
                                    world.gate.allow(
                                            "give @s stone",
                                            true,
                                            true,
                                            () -> world.parse("give")));
                            assertEquals(
                                    "PLAYER_COMMAND_CONTEXT_CHANGED",
                                    assertThrows(
                                                    ProtocolState.ProtocolException.class,
                                                    dispatch::check)
                                            .code());
                        }
                        return CompletableFuture.completedFuture(new JsonObject());
                    });
            assertEquals(1, world.observed.get());
        }
    }
}
