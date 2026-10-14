package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiRenderedIconsTest {
    @Test
    void onlyCompletedCurrentDrawsExposeTheNativeOpacity() {
        var cache = new UiRenderedIcons();
        Object owner = new Object(), screen = new Object();
        cache.begin(owner, screen, 640, 360);
        cache.observe("speed", 618, 4, 18, 18, 0.125F);
        assertNull(cache.sample("speed", 618, 4, owner, screen, 640, 360, 10));
        cache.finish(10);
        assertEquals(0.125F, cache.sample("speed", 618, 4, owner, screen, 640, 360, 10).opacity());
        assertNull(cache.sample("speed", 618, 4, new Object(), screen, 640, 360, 10));
        assertNull(cache.sample("speed", 618, 4, owner, null, 640, 360, 10));
        assertNull(cache.sample("speed", 618, 4, owner, new Object(), 640, 360, 10));
        assertNull(cache.sample("speed", 618, 4, owner, screen, 1280, 720, 10));
        assertNull(cache.sample("speed", 619, 4, owner, screen, 640, 360, 10));
        assertNull(cache.sample("slowness", 618, 4, owner, screen, 640, 360, 10));
        assertNull(cache.sample("speed", 618, 4, owner, screen, 640, 360, 9));
        assertNull(cache.sample("speed", 618, 4, owner, screen, 640, 360, 250_000_011L));
        cache.begin(owner, screen, 640, 360);
        cache.finish(20);
        assertNull(cache.sample("speed", 618, 4, owner, screen, 640, 360, 20));
    }

    @Test
    void transparentNativeDrawsRemainDistinguishableAndBounded() {
        var cache = new UiRenderedIcons();
        Object owner = new Object();
        cache.begin(owner, null, 640, 360);
        cache.observe("invalid", 0, 0, 18, 18, Float.NaN);
        cache.observe("empty", 0, 0, 0, 18, 1);
        for (int i = 0; i < 65; i++) cache.observe("icon" + i, i, 0, 18, 18, -0.1F);
        cache.finish(10);
        assertEquals(0, cache.sample("icon0", 0, 0, owner, null, 640, 360, 10).opacity());
        assertNotNull(cache.sample("icon63", 63, 0, owner, null, 640, 360, 10));
        assertNull(cache.sample("icon64", 64, 0, owner, null, 640, 360, 10));
        assertNull(cache.sample("invalid", 0, 0, owner, null, 640, 360, 10));
        cache.observe("late", 0, 0, 18, 18, 1);
        assertNull(cache.sample("late", 0, 0, owner, null, 640, 360, 10));
    }
}
