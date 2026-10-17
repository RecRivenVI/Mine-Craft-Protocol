package io.github.recrivenvi.minecraftprotocol.safety;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Objects;

/** Bounded client-block history. A state returning to its old value cannot revive a reference. */
public final class WorldBlockChanges {
    public static final int CAPACITY = 4096;

    private record Change(
            WeakReference<Object> chunk, long position, boolean wholeChunk, long sequence) {}

    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private final int capacity;
    private long sequence;
    private boolean exhausted;

    public WorldBlockChanges() {
        this(CAPACITY);
    }

    WorldBlockChanges(int capacity) {
        if (capacity < 1 || capacity > CAPACITY)
            throw new IllegalArgumentException("Invalid change budget");
        this.capacity = capacity;
    }

    public synchronized long snapshot() {
        return sequence;
    }

    public synchronized void changed(Object chunk, long position) {
        record(chunk, position, false);
    }

    public synchronized void replaced(Object chunk) {
        record(chunk, 0, true);
    }

    private void record(Object chunk, long position, boolean wholeChunk) {
        Objects.requireNonNull(chunk);
        if (exhausted) return;
        // Never wrap and let a sufficiently old reference become current again.
        if (sequence == Long.MAX_VALUE) {
            exhausted = true;
            changes.clear();
            return;
        }
        if (changes.size() == capacity) changes.removeFirst();
        changes.addLast(new Change(new WeakReference<>(chunk), position, wholeChunk, ++sequence));
    }

    public synchronized boolean unchanged(Object chunk, long position, long observed) {
        if (chunk == null
                || exhausted
                || observed < 0
                || observed > sequence
                || sequence - observed > changes.size()) return false;
        for (Change change : changes) {
            if (change.sequence() > observed
                    && change.chunk().get() == chunk
                    && (change.wholeChunk() || change.position() == position)) return false;
        }
        return true;
    }
}
