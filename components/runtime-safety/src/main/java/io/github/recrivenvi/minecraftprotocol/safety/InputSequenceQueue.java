package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/** One input sequence owns callbacks and cleanup. Queued cancellation never cleans another owner. */
public final class InputSequenceQueue implements AutoCloseable {
    public static final int CAPACITY = 16;
    private record Entry(Runnable start, Runnable cancel, CompletableFuture<?> drained) { }
    private final ArrayDeque<Entry> waiting = new ArrayDeque<>();
    private Entry active;
    private boolean closed;
    private boolean cancelling;
    private boolean poisoned;

    public void submit(Runnable start, Runnable cancel, CompletableFuture<?> drained) {
        Entry entry = new Entry(start, cancel, drained);
        synchronized (this) {
            if (poisoned) throw new RejectedExecutionException("INPUT_CLEANUP_FAILED");
            if (closed || waiting.size() + (active == null ? 0 : 1) >= CAPACITY)
                throw new RejectedExecutionException(closed ? "INPUT_QUEUE_CLOSED" : "INPUT_QUEUE_FULL");
            waiting.addLast(entry);
        }
        drained.whenComplete((value, error) -> finished(entry, error));
        pump();
    }
    private void finished(Entry entry, Throwable error) {
        synchronized (this) {
            if (error != null) poisoned = true;
            waiting.remove(entry);
            if (active == entry) active = null;
        }
        if (error != null) cancelAll(); else pump();
    }
    private void pump() {
        Entry next;
        synchronized (this) {
            if (closed || poisoned || cancelling || active != null) return;
            do { next = waiting.pollFirst(); } while (next != null && next.drained.isDone());
            if (next == null) return;
            active = next;
        }
        next.start.run();
    }
    public void cancelAll() {
        ArrayList<Entry> entries;
        synchronized (this) {
            cancelling = true;
            entries = new ArrayList<>(waiting);
            if (active != null) entries.add(active);
        }
        try { for (Entry entry : entries) entry.cancel.run(); }
        finally { synchronized (this) { cancelling = false; } pump(); }
    }
    public synchronized int pendingCount() { return waiting.size(); }
    public synchronized boolean busy() { return active != null; }
    @Override public void close() { synchronized (this) { closed = true; } cancelAll(); }
}
