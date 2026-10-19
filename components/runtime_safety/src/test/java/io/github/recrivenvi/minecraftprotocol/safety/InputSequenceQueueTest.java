package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InputSequenceQueueTest {
    @Test
    void stopWaitsForEveryCapturedOwnerWithoutStartingQueuedInput() {
        try (InputSequenceQueue queue = new InputSequenceQueue()) {
            AtomicInteger starts = new AtomicInteger();
            AtomicInteger cancellations = new AtomicInteger();
            CompletableFuture<Void> active = new CompletableFuture<>();
            CompletableFuture<Void> waiting = new CompletableFuture<>();
            queue.submit(starts::incrementAndGet, cancellations::incrementAndGet, active);
            queue.submit(starts::incrementAndGet, cancellations::incrementAndGet, waiting);
            CompletableFuture<Void> stopped = queue.cancelAllAndWait();
            assertEquals(2, cancellations.get());
            assertEquals(1, starts.get());
            assertFalse(stopped.isDone());
            waiting.complete(null);
            assertFalse(stopped.isDone());
            active.complete(null);
            stopped.join();
            assertFalse(queue.busy());
            assertEquals(0, queue.pendingCount());
            assertEquals(1, starts.get());
        }
    }

    @Test
    void failedCleanupPoisonsTheQueueAndCannotBeReportedAsReleased() {
        try (InputSequenceQueue queue = new InputSequenceQueue()) {
            CompletableFuture<Void> active = new CompletableFuture<>();
            queue.submit(() -> {}, () -> {}, active);
            CompletableFuture<Void> stopped = queue.cancelAllAndWait();
            active.completeExceptionally(new IllegalStateException("cleanup failed"));
            assertTrue(stopped.isCompletedExceptionally());
            assertThrows(
                    java.util.concurrent.RejectedExecutionException.class,
                    () -> queue.submit(() -> {}, () -> {}, new CompletableFuture<>()));
        }
    }

    @Test
    void emptyQueueHasAnAlreadyCompletedDrainBarrier() {
        try (InputSequenceQueue queue = new InputSequenceQueue()) {
            assertTrue(queue.cancelAllAndWait().isDone());
        }
    }
}
