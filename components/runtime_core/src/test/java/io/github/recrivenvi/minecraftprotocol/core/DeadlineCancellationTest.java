package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeadlineCancellationTest {
    @Test
    void timeoutIsNotPublishedBeforeNativeCancellation() throws Exception {
        try (ProtocolState state = new ProtocolState(Set.of("read"), "test", ignored -> {})) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            CompletableFuture<JsonObject> source =
                    new CompletableFuture<>() {
                        @Override
                        public boolean cancel(boolean interrupt) {
                            entered.countDown();
                            try {
                                if (!release.await(2, TimeUnit.SECONDS))
                                    throw new AssertionError("Cancellation barrier timeout");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                            return super.cancel(interrupt);
                        }
                    };
            var deadline = state.applyDeadline(source, System.currentTimeMillis() + 50);
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertFalse(deadline.isDone());
            } finally {
                release.countDown();
            }
            var error =
                    assertThrows(ExecutionException.class, () -> deadline.get(1, TimeUnit.SECONDS));
            assertEquals(
                    "REQUEST_DEADLINE_EXCEEDED",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertTrue(source.isCancelled());
        }
    }

    @Test
    void successfulCompletionAndExplicitCancelKeepTheirOutcomes() throws Exception {
        try (ProtocolState state = new ProtocolState(Set.of("read"), "test", ignored -> {})) {
            CompletableFuture<JsonObject> source = new CompletableFuture<>();
            var deadline = state.applyDeadline(source, System.currentTimeMillis() + 5000);
            var value = new JsonObject();
            source.complete(value);
            assertSame(value, deadline.get(1, TimeUnit.SECONDS));
            assertFalse(deadline.cancel(true));
            CompletableFuture<JsonObject> cancelled = new CompletableFuture<>();
            var other = state.applyDeadline(cancelled, System.currentTimeMillis() + 5000);
            assertTrue(other.cancel(true));
            assertTrue(other.isCancelled());
            assertTrue(cancelled.isCancelled());
        }
    }
}
