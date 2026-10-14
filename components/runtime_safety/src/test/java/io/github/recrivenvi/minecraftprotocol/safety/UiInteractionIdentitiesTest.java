package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiInteractionIdentitiesTest {
    @Test
    void currentNodeSurvivesTheOld2048BoundaryAndRepeatedNavigation() {
        var registry = new UiInteractionIdentities();
        var current = new Object();
        long id = registry.id(current);
        for (int i = 0; i < 20_000; i++) {
            registry.id(new Object());
            assertEquals(id, registry.id(current));
        }
        assertEquals(8192, registry.size());
    }

    @Test
    void aFullCurrentTreeStaysStableWhileOlderScreensAreEvicted() {
        var registry = new UiInteractionIdentities();
        for (int i = 0; i < 8192; i++) registry.id(new Object());
        Object[] nodes = new Object[2052];
        long[] ids = new long[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Object();
            ids[i] = registry.id(nodes[i]);
        }
        for (int pass = 0; pass < 4; pass++)
            for (int i = 0; i < nodes.length; i++) assertEquals(ids[i], registry.id(nodes[i]));
        assertEquals(8192, registry.size());
    }

    @Test
    void equalsDoesNotAliasNativeObjectsAndEvictedIdsAreNeverReused() {
        var registry = new UiInteractionIdentities();
        Object first = new String("same"), second = new String("same");
        long old = registry.id(first);
        assertNotEquals(old, registry.id(second));
        for (int i = 0; i < 8192; i++) registry.id(new Object());
        assertTrue(registry.id(first) > old);
        assertThrows(NullPointerException.class, () -> registry.id(null));
    }
}
