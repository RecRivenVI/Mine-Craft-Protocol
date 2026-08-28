package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class BoundedTaskExecutorTest {
    @Test
    void revisionWorkerRejectsBeyondOneActiveAndEightQueued() throws Exception {
        assertBounded(1, 8);
    }

    @Test
    void providerWorkerRejectsBeyondTwoActiveAndSixteenQueued() throws Exception {
        assertBounded(2, 16);
    }

    @Test
    void shutdownCancelsQueuedWork() throws Exception {
        BoundedTaskExecutor executor = new BoundedTaskExecutor("shutdown-test", 1, 2);
        CountDownLatch blocker = new CountDownLatch(1);
        CompletableFuture<Integer> active = executor.submit(() -> await(blocker));
        CompletableFuture<Integer> queued = executor.submit(() -> 2);
        executor.close();
        blocker.countDown();
        assertTrue(queued.isCancelled());
        assertThrows(Exception.class, () -> active.get(1, TimeUnit.SECONDS));
    }

    private static void assertBounded(int threads, int queueCapacity) throws Exception {
        try (BoundedTaskExecutor executor =
                     new BoundedTaskExecutor("bounded-test", threads, queueCapacity)) {
            CountDownLatch started = new CountDownLatch(threads);
            CountDownLatch release = new CountDownLatch(1);
            List<CompletableFuture<Integer>> accepted = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                accepted.add(executor.submit(() -> {
                    started.countDown();
                    return await(release);
                }));
            }
            assertTrue(started.await(2, TimeUnit.SECONDS));
            for (int index = 0; index < queueCapacity; index++) {
                int value = index;
                accepted.add(executor.submit(() -> value));
            }
            CompletableFuture<Integer> rejected = executor.submit(() -> 99);
            ExecutionException failure = assertThrows(
                    ExecutionException.class, () -> rejected.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof java.util.concurrent.RejectedExecutionException);
            assertEquals(queueCapacity, executor.diagnostics().get("queueCapacity").getAsInt());
            assertEquals(1, executor.diagnostics().get("rejected").getAsInt());
            release.countDown();
            for (CompletableFuture<Integer> future : accepted) {
                future.get(2, TimeUnit.SECONDS);
            }
            assertEquals(0, executor.queueDepth());
        }
    }

    private static int await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
            return 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
