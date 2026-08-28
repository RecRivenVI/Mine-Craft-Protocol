package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Small bounded executor with explicit overload and shutdown accounting. */
final class BoundedTaskExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    BoundedTaskExecutor(String threadName, int threads, int queueCapacity) {
        if (threads < 1 || queueCapacity < 1) throw new IllegalArgumentException("invalid executor bounds");
        this.queueCapacity = queueCapacity;
        AtomicLong threadIds = new AtomicLong();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadName + "-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Task<T> task = new Task<>(supplier, future, this.completed);
        try {
            this.executor.execute(task);
            this.submitted.incrementAndGet();
        } catch (RejectedExecutionException exception) {
            this.rejected.incrementAndGet();
            future.completeExceptionally(exception);
        }
        return future;
    }

    JsonObject diagnostics() {
        JsonObject json = new JsonObject();
        json.addProperty("threads", this.executor.getCorePoolSize());
        json.addProperty("active", this.executor.getActiveCount());
        json.addProperty("queueDepth", this.executor.getQueue().size());
        json.addProperty("queueCapacity", this.queueCapacity);
        json.addProperty("submitted", this.submitted.get());
        json.addProperty("completed", this.completed.get());
        json.addProperty("rejected", this.rejected.get());
        json.addProperty("overloadPolicy", "reject");
        return json;
    }

    int queueDepth() {
        return this.executor.getQueue().size();
    }

    int queueCapacity() {
        return this.queueCapacity;
    }

    @Override
    public void close() {
        List<Runnable> queued = this.executor.shutdownNow();
        for (Runnable runnable : queued) {
            if (runnable instanceof Task<?> task) task.cancel();
        }
    }

    private static final class Task<T> implements Runnable {
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future;
        private final AtomicLong completed;

        private Task(Supplier<T> supplier, CompletableFuture<T> future, AtomicLong completed) {
            this.supplier = supplier;
            this.future = future;
            this.completed = completed;
        }

        @Override
        public void run() {
            if (this.future.isDone()) return;
            try {
                this.future.complete(this.supplier.get());
            } catch (Throwable throwable) {
                this.future.completeExceptionally(throwable);
            } finally {
                this.completed.incrementAndGet();
            }
        }

        private void cancel() {
            this.future.cancel(true);
        }
    }
}

