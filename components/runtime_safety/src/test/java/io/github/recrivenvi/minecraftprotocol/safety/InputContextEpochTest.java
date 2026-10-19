package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class InputContextEpochTest {
    @Test
    void identityChangesFenceWorldPlayerAndConnectionButRepeatedObservationDoesNot() {
        var epoch = new InputContextEpoch();
        var title = epoch.snapshot();
        assertSame(title, epoch.observe(null, null, null));
        Object world = new Object(), player = new Object(), connection = new Object();
        var running = epoch.observe(world, player, connection);
        assertTrue(running.worldAvailable());
        assertEquals(title.generation() + 1, running.generation());
        assertSame(running, epoch.observe(world, player, connection));
        for (int index = 0; index < 3; index++) {
            var previous = epoch.snapshot();
            if (index == 0) world = new Object();
            if (index == 1) player = new Object();
            if (index == 2) connection = new Object();
            var current = epoch.observe(world, player, connection);
            assertEquals(previous.generation() + 1, current.generation());
            assertNotEquals(previous.opaqueId(), current.opaqueId());
        }
        var disconnected = epoch.observe(null, null, null);
        assertFalse(disconnected.worldAvailable());
        assertNotEquals(title.opaqueId(), disconnected.opaqueId());
        assertSame(disconnected, epoch.observe(null, null, null));
    }

    @Test
    void equalValuesAndReturningToTheSameObjectsDoNotResurrectAnOldEpoch() {
        var epoch = new InputContextEpoch();
        var first =
                epoch.observe(new String("world"), new String("player"), new String("connection"));
        var second =
                epoch.observe(new String("world"), new String("player"), new String("connection"));
        assertTrue(second.generation() > first.generation());
        assertNotEquals(first.opaqueId(), second.opaqueId());
        Object world = new Object(), player = new Object(), connection = new Object();
        var before = epoch.observe(world, player, connection);
        epoch.observe(null, null, null);
        var after = epoch.observe(world, player, connection);
        assertTrue(after.generation() > before.generation());
        assertNotEquals(before.opaqueId(), after.opaqueId());
    }
}
