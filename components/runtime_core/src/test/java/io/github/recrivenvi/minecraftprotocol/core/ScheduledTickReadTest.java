package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScheduledTickReadTest {
    @Test
    void pendingNativeEntriesAreNotInventedAsReadableQueuedTicks() {
        var value = ScheduledTickRead.metadata(70, 65, 64);
        assertEquals(5, value.get("pendingUnpackedCount").getAsLong());
        assertEquals(65, value.get("queuedCount").getAsLong());
        assertTrue(value.get("truncated").getAsBoolean());
        assertFalse(value.get("includesCollectedToRun").getAsBoolean());
        assertFalse(value.get("typedWritable").getAsBoolean());
        assertEquals("none", value.get("readEffects").getAsString());
        assertFalse(ScheduledTickRead.metadata(65, 64, 64).get("truncated").getAsBoolean());
        assertEquals(
                1, ScheduledTickRead.metadata(65, 64, 64).get("pendingUnpackedCount").getAsLong());
        assertFalse(ScheduledTickRead.metadata(0, 0, 0).get("truncated").getAsBoolean());
    }
}
