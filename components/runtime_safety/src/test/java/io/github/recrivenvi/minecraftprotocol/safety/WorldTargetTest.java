package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class WorldTargetTest {
    @Test
    void referencesExpireAndNeverResurrect() {
        AtomicLong clock = new AtomicLong();
        WorldTargetRegistry<String> targets = new WorldTargetRegistry<>(clock::get);
        String token = targets.add("first");
        assertEquals("first", targets.get(token));
        assertNull(targets.get("ui:foreign"));
        clock.set(WorldTargetRegistry.TTL_NANOS);
        assertNull(targets.get(token));
        assertNotEquals(token, targets.add("first"));
        targets.clear();
        assertNull(targets.get(token));
    }

    @Test
    void registryIsBoundedAndClearInvalidatesCurrentReferences() {
        WorldTargetRegistry<Integer> targets = new WorldTargetRegistry<>();
        String first = targets.add(0), last = first;
        for (int i = 1; i <= WorldTargetRegistry.CAPACITY; i++) last = targets.add(i);
        assertNull(targets.get(first));
        assertEquals(WorldTargetRegistry.CAPACITY, targets.get(last));
        targets.clear();
        assertNull(targets.get(last));
    }

    @Test
    void aimUsesShortestWrappedRotationAndVanillaSensitivity() {
        assertArrayEquals(
                new double[] {0, 0}, WorldAim.delta(0, 0, 4, 0, 0, .5, false, false), 1e-8);
        double[] a = WorldAim.delta(4, 0, 0, 0, 0, .5, false, false);
        assertEquals(-240, a[0]);
        assertEquals(0, a[1], 1e-8);
        assertTrue(WorldAim.delta(0, 0, -4, 179, 0, 1, false, false)[0] > 0);
        assertTrue(WorldAim.delta(0, 1, 4, 0, 0, .5, false, false)[1] < 0);
    }

    @Test
    void inversionAndFiniteBudgetsAreExplicit() {
        double[] a = WorldAim.delta(1, 1, 5, 0, 0, 1, false, false);
        double[] b = WorldAim.delta(1, 1, 5, 0, 0, 1, true, true);
        assertEquals(-a[0], b[0]);
        assertEquals(-a[1], b[1]);
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldAim.delta(Double.NaN, 0, 1, 0, 0, .5, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldAim.delta(1, 0, 1, 0, 0, 2, false, false));
    }
}
