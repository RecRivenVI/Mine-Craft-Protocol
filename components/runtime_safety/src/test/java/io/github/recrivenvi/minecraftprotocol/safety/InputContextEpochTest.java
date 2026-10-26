package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class InputContextEpochTest {
    @Test
    void identityChangesFenceWorldPlayerAndConnectionButRepeatedObservationDoesNot() {
        var epoch = new InputContextEpoch();
        var title = epoch.snapshot();
        assertSame(title, epoch.observe(null, null, null, false));
        Object world = new Object(), player = new Object(), connection = new Object();
        var running = epoch.observe(world, player, connection, true);
        assertTrue(running.worldAvailable());
        assertEquals(title.generation() + 1, running.generation());
        assertSame(running, epoch.observe(world, player, connection, true));
        for (int index = 0; index < 3; index++) {
            var previous = epoch.snapshot();
            if (index == 0) world = new Object();
            if (index == 1) player = new Object();
            if (index == 2) connection = new Object();
            var current = epoch.observe(world, player, connection, true);
            assertEquals(previous.generation() + 1, current.generation());
            assertNotEquals(previous.opaqueId(), current.opaqueId());
        }
        var disconnected = epoch.observe(null, null, null, false);
        assertFalse(disconnected.worldAvailable());
        assertNotEquals(title.opaqueId(), disconnected.opaqueId());
        assertSame(disconnected, epoch.observe(null, null, null, false));
    }

    @Test
    void sameObjectDeathAndRevivalFenceOldInputWithoutMakingDeathUiUnavailable() {
        var epoch = new InputContextEpoch();
        Object world = new Object(), player = new Object(), connection = new Object();
        var alive = epoch.observe(world, player, connection, true);
        var dead = epoch.observe(world, player, connection, false);
        assertEquals(alive.generation() + 1, dead.generation());
        assertNotEquals(alive.opaqueId(), dead.opaqueId());
        assertTrue(dead.worldAvailable());
        assertSame(dead, epoch.observe(world, player, connection, false));
        var revived = epoch.observe(world, player, connection, true);
        assertEquals(dead.generation() + 1, revived.generation());
        assertNotEquals(alive.opaqueId(), revived.opaqueId());
    }

    @Test
    void equalValuesAndReturningToTheSameObjectsDoNotResurrectAnOldEpoch() {
        var epoch = new InputContextEpoch();
        var first =
                epoch.observe(
                        new String("world"), new String("player"), new String("connection"), true);
        var second =
                epoch.observe(
                        new String("world"), new String("player"), new String("connection"), true);
        assertTrue(second.generation() > first.generation());
        assertNotEquals(first.opaqueId(), second.opaqueId());
        Object world = new Object(), player = new Object(), connection = new Object();
        var before = epoch.observe(world, player, connection, true);
        epoch.observe(null, null, null, false);
        var after = epoch.observe(world, player, connection, true);
        assertTrue(after.generation() > before.generation());
        assertNotEquals(before.opaqueId(), after.opaqueId());
    }
}
