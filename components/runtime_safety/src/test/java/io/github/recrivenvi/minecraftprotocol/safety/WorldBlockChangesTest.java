package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

final class WorldBlockChangesTest {
    @Test
    void unrelatedPositionsAndChunksDoNotInvalidateTheObservedBlock() {
        var history = new WorldBlockChanges();
        Object chunk = new Object(), other = new Object();
        long observed = history.snapshot();
        history.changed(chunk, 2);
        history.changed(other, 1);
        assertTrue(history.unchanged(chunk, 1, observed));
        assertFalse(history.unchanged(chunk, 2, observed));
        assertFalse(history.unchanged(other, 1, observed));
    }

    @Test
    void anyObservedTargetTransitionIncludingABACannotReviveOldReferences() {
        var history = new WorldBlockChanges();
        Object chunk = new Object();
        long first = history.snapshot();
        history.changed(chunk, 1);
        long middle = history.snapshot();
        history.changed(chunk, 1);
        assertFalse(history.unchanged(chunk, 1, first));
        assertFalse(history.unchanged(chunk, 1, middle));
        assertTrue(history.unchanged(chunk, 1, history.snapshot()));
    }

    @Test
    void fullSnapshotInvalidatesEveryPositionButNotAnotherChunk() {
        var history = new WorldBlockChanges();
        Object chunk = new Object(), other = new Object();
        long old = history.snapshot();
        history.replaced(chunk);
        for (long position : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertFalse(history.unchanged(chunk, position, old));
            assertTrue(history.unchanged(other, position, old));
            assertTrue(history.unchanged(chunk, position, history.snapshot()));
        }
    }

    @Test
    void replacementInvalidatesBeforeAnInterruptedReadAndCannotBeUndoneByEqualData() {
        var history = new WorldBlockChanges();
        Object chunk = new Object();
        long old = history.snapshot();
        assertThrows(
                IllegalStateException.class,
                () -> {
                    history.replaced(chunk);
                    throw new IllegalStateException("Synthetic packet decoding interruption");
                });
        assertFalse(history.unchanged(chunk, 42, old));
        long next = history.snapshot();
        history.changed(chunk, 43);
        assertFalse(history.unchanged(chunk, 42, old));
        assertTrue(history.unchanged(chunk, 42, next));
        history.replaced(chunk);
        assertFalse(history.unchanged(chunk, 42, next));
    }

    @Test
    void fullSnapshotsShareTheSameBoundedHistoryAndFailClosedOnEvictionAndExhaustion()
            throws Exception {
        var history = new WorldBlockChanges(2);
        Object chunk = new Object(), other = new Object();
        long old = history.snapshot();
        history.replaced(chunk);
        history.changed(other, 1);
        history.replaced(other);
        assertFalse(history.unchanged(chunk, 2, old));
        assertTrue(history.unchanged(chunk, 2, history.snapshot()));
        var field = WorldBlockChanges.class.getDeclaredField("sequence");
        field.setAccessible(true);
        field.setLong(history, Long.MAX_VALUE);
        history.replaced(chunk);
        assertFalse(history.unchanged(chunk, 2, history.snapshot()));
    }

    @Test
    void boundedHistoryEvictionFailsClosedButFreshReferencesStillWork() {
        var history = new WorldBlockChanges(2);
        Object chunk = new Object();
        long old = history.snapshot();
        history.changed(chunk, 2);
        history.changed(chunk, 3);
        assertTrue(history.unchanged(chunk, 1, old));
        history.changed(chunk, 4);
        assertFalse(history.unchanged(chunk, 1, old));
        assertTrue(history.unchanged(chunk, 1, history.snapshot()));
    }

    @Test
    void chunkIdentityIsNotItsEqualsMethodAndInvalidSnapshotsFailClosed() {
        class EqualChunk {
            @Override
            public boolean equals(Object other) {
                return other instanceof EqualChunk;
            }

            @Override
            public int hashCode() {
                return 1;
            }
        }
        Object chunk = new EqualChunk(), replacement = new EqualChunk();
        var history = new WorldBlockChanges();
        long observed = history.snapshot();
        history.changed(replacement, 1);
        assertTrue(history.unchanged(chunk, 1, observed));
        assertFalse(history.unchanged(replacement, 1, observed));
        assertFalse(history.unchanged(chunk, 1, -1));
        assertFalse(history.unchanged(chunk, 1, history.snapshot() + 1));
        assertFalse(history.unchanged(null, 1, observed));
        assertThrows(IllegalArgumentException.class, () -> new WorldBlockChanges(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldBlockChanges(WorldBlockChanges.CAPACITY + 1));
    }

    @Test
    void storageIsBoundedAndDoesNotHoldChunksStrongly() throws Exception {
        var history = new WorldBlockChanges(2);
        Object chunk = new Object();
        for (int i = 0; i < 100; i++) history.changed(chunk, i);
        var field = WorldBlockChanges.class.getDeclaredField("changes");
        field.setAccessible(true);
        var entries = (ArrayDeque<?>) field.get(history);
        assertEquals(2, entries.size());
        var reference = entries.getFirst().getClass().getDeclaredMethod("chunk");
        reference.setAccessible(true);
        assertInstanceOf(WeakReference.class, reference.invoke(entries.getFirst()));
    }

    @Test
    void sequenceExhaustionNeverReusesAnOldIdentity() throws Exception {
        var history = new WorldBlockChanges();
        var field = WorldBlockChanges.class.getDeclaredField("sequence");
        field.setAccessible(true);
        field.setLong(history, Long.MAX_VALUE);
        Object chunk = new Object();
        history.changed(chunk, 1);
        assertEquals(Long.MAX_VALUE, history.snapshot());
        assertFalse(history.unchanged(chunk, 1, history.snapshot()));
        history.changed(chunk, 2);
        assertFalse(history.unchanged(chunk, 2, history.snapshot()));
    }
}
