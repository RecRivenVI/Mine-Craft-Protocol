package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiHudIconsTest {
    @Test
    void completedNativeLayersAreDetachedAndNeverBecomeRawHealth() {
        var samples = new UiHudIcons();
        Object world = new Object(), player = new Object();
        samples.begin(world, player, null, 640, 360);
        samples.observe(
                UiHudIcons.Kind.HEALTH,
                "minecraft:hud/heart/container",
                "container",
                230,
                320,
                9,
                9);
        samples.observe(UiHudIcons.Kind.HEALTH, "minecraft:hud/heart/half", "half", 230, 320, 9, 9);
        assertEquals("UNOBSERVED", samples.sample(world, player, null, 640, 360, 10).status());
        samples.finish(10);
        var frame = samples.sample(world, player, null, 640, 360, 10);
        assertEquals("CURRENT", frame.status());
        assertEquals(2, frame.icons().size());
        assertEquals("half", frame.icons().get(1).label());
        assertThrows(UnsupportedOperationException.class, () -> frame.icons().clear());
        samples.observe(UiHudIcons.Kind.HEALTH, "late", "late", 1, 1, 9, 9);
        assertEquals(2, samples.sample(world, player, null, 640, 360, 10).icons().size());
        samples.begin(world, player, null, 640, 360);
        assertEquals("UNOBSERVED", samples.sample(world, player, null, 640, 360, 11).status());
        assertEquals(2, frame.icons().size());
        samples.finish(12);
        assertTrue(samples.sample(world, player, null, 640, 360, 12).icons().isEmpty());
    }

    @Test
    void contextAndTimeAreFailClosedIncludingSamePlayerInNewWorld() {
        var samples = new UiHudIcons();
        Object world = new Object(), player = new Object(), screen = new Object();
        samples.begin(world, player, screen, 640, 360);
        samples.observe(UiHudIcons.Kind.ARMOR, "armor", "full", 1, 1, 9, 9);
        samples.finish(10);
        assertEquals(
                "CONTEXT_CHANGED",
                samples.sample(new Object(), player, screen, 640, 360, 10).status());
        assertEquals(
                "CONTEXT_CHANGED",
                samples.sample(world, new Object(), screen, 640, 360, 10).status());
        assertEquals("CONTEXT_CHANGED", samples.sample(world, player, null, 640, 360, 10).status());
        assertEquals(
                "CONTEXT_CHANGED",
                samples.sample(world, player, new Object(), 640, 360, 10).status());
        assertEquals(
                "CONTEXT_CHANGED", samples.sample(world, player, screen, 1280, 720, 10).status());
        assertEquals("STALE", samples.sample(world, player, screen, 640, 360, 9).status());
        assertEquals(
                "STALE", samples.sample(world, player, screen, 640, 360, 250_000_011L).status());
        assertEquals(
                "CURRENT", samples.sample(world, player, screen, 640, 360, 250_000_010L).status());
    }

    @Test
    void overBudgetIsExplicitNotACompletePartialHealthBar() {
        var samples = new UiHudIcons();
        Object world = new Object(), player = new Object();
        samples.begin(world, player, null, 640, 360);
        for (int i = 0; i < 257; i++)
            samples.observe(UiHudIcons.Kind.FOOD, "food", "full", 1, 1, 9, 9);
        samples.finish(10);
        var frame = samples.sample(world, player, null, 640, 360, 10);
        assertEquals("BUDGET_EXCEEDED", frame.status());
        assertTrue(frame.icons().isEmpty());
    }

    @Test
    void invalidGeometryAndUnownedFramesNeverPublishIcons() {
        var samples = new UiHudIcons();
        Object world = new Object(), player = new Object();
        samples.begin(world, null, null, 640, 360);
        samples.observe(UiHudIcons.Kind.AIR, "air", "full", 1, 1, 9, 9);
        samples.finish(10);
        assertEquals("UNOBSERVED", samples.sample(world, player, null, 640, 360, 10).status());
        samples.begin(world, player, null, 640, 360);
        for (int[] box :
                new int[][] {
                    {-1, 1, 9, 9},
                    {1, -1, 9, 9},
                    {639, 1, 9, 9},
                    {1, 359, 9, 9},
                    {1, 1, 0, 9},
                    {Integer.MAX_VALUE, 1, 9, 9}
                })
            samples.observe(UiHudIcons.Kind.AIR, "air", "full", box[0], box[1], box[2], box[3]);
        samples.finish(10);
        assertTrue(samples.sample(world, player, null, 640, 360, 10).icons().isEmpty());
    }
}
