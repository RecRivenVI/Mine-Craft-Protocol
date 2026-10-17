package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class WorldBreakTraceTest {
    @Test
    void fluidReplacementAndNeighborChainAreOwnedButUnobservedRevisionIsNot() {
        Object chunk = new Object(),
                other = new Object(),
                original = new Object(),
                water = new Object();
        var trace = new WorldBreakTrace(chunk, "target", original, water, 12);
        trace.changed(chunk, "neighbor", original, water);
        trace.changed(other, "neighbor", original, water);
        trace.changed(chunk, "target", original, water);
        assertTrue(trace.completed(true, chunk, water, 14));
        assertEquals(3, trace.changes());
        assertFalse(trace.completed(true, chunk, water, 15));
        assertFalse(trace.completed(false, chunk, water, 14));
        assertFalse(trace.completed(true, other, water, 14));
        assertFalse(trace.completed(true, chunk, original, 14));
    }

    @Test
    void changedBackTargetUnrelatedChangesAndOverflowFailClosed() {
        Object chunk = new Object(), original = new Object(), air = new Object();
        var trace = new WorldBreakTrace(chunk, 1, original, air, 0);
        trace.changed(chunk, 2, original, air);
        assertFalse(trace.completed(true, chunk, air, 1));
        trace.changed(chunk, 1, original, air);
        trace.changed(chunk, 1, air, original);
        trace.changed(chunk, 1, original, air);
        assertFalse(trace.completed(true, chunk, air, 4));
        var overflow = new WorldBreakTrace(chunk, 1, original, air, 0);
        overflow.changed(chunk, 1, original, air);
        for (int i = 0; i < WorldBreakTrace.MAX_CHANGES; i++)
            overflow.changed(chunk, 2, original, air);
        assertFalse(overflow.completed(true, chunk, air, WorldBreakTrace.MAX_CHANGES + 1));
    }
}
