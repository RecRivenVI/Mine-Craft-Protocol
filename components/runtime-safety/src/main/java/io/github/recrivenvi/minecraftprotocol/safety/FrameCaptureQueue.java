package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** All evidence readbacks start at the final content boundary, before Operator Chrome. */
public final class FrameCaptureQueue implements AutoCloseable {
    public static final int CAPACITY = 16;
    public static final int MAX_IN_FLIGHT = 2;
    private final ArrayDeque<CompletableFuture<byte[]>> pending = new ArrayDeque<>();
    private final Set<CompletableFuture<byte[]>> requests = new HashSet<>();
    private int inFlight;
    private boolean closed;
    private long frameSequence;
    private long readbacks;

    public synchronized CompletableFuture<byte[]> request() {
        if (closed || requests.size() >= CAPACITY) return CompletableFuture.failedFuture(
                new RejectedExecutionException(closed ? "Capture queue is closed" : "Capture queue is full"));
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        pending.add(result);
        requests.add(result);
        result.orTimeout(10, TimeUnit.SECONDS).whenComplete((ignored, error) -> {
            synchronized (this) { pending.remove(result); requests.remove(result); }
        });
        return result;
    }

    /** readback must copy/submit its GPU copy synchronously before returning its Future. */
    public void beforeOperatorChrome(Supplier<CompletableFuture<byte[]>> readback) {
        synchronized (this) { frameSequence++; }
        while (true) {
            CompletableFuture<byte[]> result;
            synchronized (this) {
                if (closed || inFlight >= MAX_IN_FLIGHT || pending.isEmpty()) return;
                result = pending.removeFirst();
                if (result.isDone()) continue;
                inFlight++;
                readbacks++;
            }
            try {
                readback.get().whenComplete((bytes, error) -> {
                    synchronized (this) { inFlight--; }
                    if (error != null) result.completeExceptionally(error);
                    else result.complete(bytes);
                });
            } catch (Throwable error) {
                synchronized (this) { inFlight--; }
                result.completeExceptionally(error);
            }
        }
    }

    public synchronized int pendingCount() { return pending.size(); }
    public synchronized int inFlightCount() { return inFlight; }
    public synchronized long frameSequence() { return frameSequence; }
    public synchronized long readbacks() { return readbacks; }

    @Override public synchronized void close() {
        closed = true;
        for (CompletableFuture<byte[]> result : Set.copyOf(requests)) result.cancel(false);
        pending.clear();
    }
}
