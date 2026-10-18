package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlayerCommandSuggestionRequestsTest {
    @Test
    void singleFlightRateAndIdentityAreIndependentOfHumanRequests() {
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            var first = requests.begin(1000, () -> {});
            assertEquals(-2, first.requestId());
            assertEquals(
                    "PLAYER_COMMAND_COMPLETION_BUSY",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> requests.begin(1100, () -> {}))
                            .code());
            assertFalse(requests.receive(0, "human", 1001));
            assertFalse(requests.receive(-1, "native sentinel", 1001));
            assertFalse(requests.receive(-99, "unknown", 1001));
            assertFalse(first.future().isDone());
            assertTrue(requests.receive(first.requestId(), "native server result", 1001));
            assertEquals("native server result", first.future().join());
            var limited =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> requests.begin(1100, () -> {}));
            assertEquals("PLAYER_COMMAND_COMPLETION_RATE_LIMITED", limited.code());
            assertEquals(150, limited.details().get("retryAfterMillis").getAsInt());
            var second = requests.begin(1250, () -> {});
            assertEquals(-3, second.requestId());
            assertTrue(requests.receive(first.requestId(), "late old response", 1250));
            assertFalse(second.future().isDone());
        }
    }

    @Test
    void authorityAndHumanPriorityAreCheckedBeforeActualSend() {
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            List<String> sequence = new ArrayList<>();
            var request = requests.begin(1000, () -> sequence.add("authority"));
            requests.dispatch(
                    request, () -> sequence.add("human"), id -> sequence.add("send:" + id));
            assertEquals(List.of("authority", "human", "send:-2"), sequence);
        }
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            var sends = new AtomicInteger();
            var request = requests.begin(1000, () -> {});
            requests.dispatch(
                    request,
                    () -> {
                        throw new ProtocolState.ProtocolException(
                                "PLAYER_COMMAND_COMPLETION_BUSY", 409, "human pending");
                    },
                    id -> sends.incrementAndGet());
            assertEquals(0, sends.get());
            assertEquals("PLAYER_COMMAND_COMPLETION_BUSY", error(request.future()).code());
        }
    }

    @Test
    void cancellationAndTimeoutRetireOnlyOwnedRequestsAndNoLatePublish() {
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            var sends = new AtomicInteger();
            var first = requests.begin(1000, () -> {});
            first.future().cancel(false);
            requests.dispatch(first, () -> {}, id -> sends.incrementAndGet());
            assertEquals(0, sends.get());
            assertTrue(requests.receive(first.requestId(), "late cancelled", 1001));
            assertTrue(first.future().isCancelled());
            var timed = requests.begin(1250, () -> {});
            requests.tick(2750);
            assertEquals("PLAYER_COMMAND_COMPLETION_TIMEOUT", error(timed.future()).code());
            assertTrue(requests.receive(timed.requestId(), "late timed-out", 2751));
            assertFalse(requests.owns(0));
        }
    }

    @Test
    void actorConnectionWorldOrScopeChangeInvalidatesPublication() {
        var authorized = new AtomicBoolean(true);
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            var request =
                    requests.begin(
                            1000,
                            () -> {
                                if (!authorized.get())
                                    throw new ProtocolState.ProtocolException(
                                            "PLAYER_COMMAND_CONTEXT_CHANGED", 409, "owner changed");
                            });
            requests.dispatch(request, () -> {}, id -> {});
            authorized.set(false);
            requests.receive(request.requestId(), "must not publish", 1001);
            assertEquals("PLAYER_COMMAND_CONTEXT_CHANGED", error(request.future()).code());
        }
    }

    @Test
    void closeDoesNotRunGameAuthorizationAndRejectsReopening() {
        var requests = new PlayerCommandSuggestionRequests<String>();
        var checks = new AtomicInteger();
        var request = requests.begin(1000, checks::incrementAndGet);
        requests.close();
        assertEquals(0, checks.get());
        assertEquals("PLAYER_COMMAND_CONTEXT_CHANGED", error(request.future()).code());
        assertTrue(requests.receive(request.requestId(), "late after close", 1001));
        assertEquals(
                "PLAYER_COMMAND_CONTEXT_CHANGED",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () -> requests.begin(2000, () -> {}))
                        .code());
    }

    @Test
    void humanArrivingAfterAgentIsTheOnlySurvivingLatestWinsRequest() {
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            var human = new CompletableFuture<String>();
            var agent = requests.begin(1000, () -> {});
            var serverQueue = new ArrayList<Integer>();
            requests.dispatch(agent, () -> {}, serverQueue::add);
            serverQueue.add(0);
            assertEquals(0, serverQueue.get(serverQueue.size() - 1).intValue());
            assertFalse(requests.receive(0, "human reply", 1001));
            human.complete("human reply");
            assertEquals("human reply", human.join());
            requests.tick(2500);
            assertEquals("PLAYER_COMMAND_COMPLETION_TIMEOUT", error(agent.future()).code());
            assertFalse(human.isCancelled());
        }
    }

    @Test
    void receivedResponseEnforcesExactTimeoutWithoutWaitingForTick() {
        for (long elapsed : List.of(1499L, 1500L, 1501L)) {
            try (var requests = new PlayerCommandSuggestionRequests<String>()) {
                var checks = new AtomicInteger();
                var request = requests.begin(1000, checks::incrementAndGet);
                assertTrue(requests.receive(request.requestId(), "server result", 1000 + elapsed));
                if (elapsed < PlayerCommandSuggestionRequests.TIMEOUT_MILLIS) {
                    assertEquals("server result", request.future().join());
                    assertEquals(1, checks.get());
                } else {
                    assertEquals(
                            "PLAYER_COMMAND_COMPLETION_TIMEOUT", error(request.future()).code());
                    assertEquals(408, error(request.future()).httpStatus());
                    assertEquals(0, checks.get());
                    assertTrue(requests.receive(request.requestId(), "late retry", 4000));
                    assertEquals(
                            "PLAYER_COMMAND_COMPLETION_TIMEOUT", error(request.future()).code());
                }
            }
        }
    }

    @Test
    void cancellationDuringPriorityCheckCannotLeakASendAndCallbacksHoldNoRegistryLock() {
        try (var requests = new PlayerCommandSuggestionRequests<String>()) {
            var sends = new AtomicInteger();
            var request = requests.begin(1000, () -> assertFalse(Thread.holdsLock(requests)));
            requests.dispatch(
                    request,
                    () -> {
                        assertFalse(Thread.holdsLock(requests));
                        request.future().cancel(false);
                    },
                    id -> sends.incrementAndGet());
            assertEquals(0, sends.get());
            assertTrue(request.future().isCancelled());
        }
    }

    private static ProtocolState.ProtocolException error(CompletableFuture<?> future) {
        return (ProtocolState.ProtocolException)
                assertThrows(CompletionException.class, future::join).getCause();
    }
}
