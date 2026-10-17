package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldPresentationTest {
    private final Object world = new Object();
    private final Object camera = new Object();

    private WorldPresentation.Context context(Object target, boolean active, String view) {
        return new WorldPresentation.Context(world, camera, null, target, 641, 361, active, view);
    }

    @Test
    void onlyCompletedMatchingNativeSubmissionsCreateOpaqueFractionalHotspots() {
        var sample = new WorldPresentation();
        var context = context("private-block-position", true, "private-camera-pose");
        sample.begin(context);
        sample.outlineSubmitted(context.target());
        assertFalse(sample.read(context, 100).get("available").getAsBoolean());
        sample.finish(context, 100);
        var result = sample.read(context, 101);
        assertTrue(result.get("available").getAsBoolean());
        var node = result.getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals(320.5, node.getAsJsonObject("hotspot").get("x").getAsDouble());
        assertEquals(180.5, node.getAsJsonObject("hotspot").get("y").getAsDouble());
        assertEquals(
                node.get("targetRef"),
                sample.read(context, 102)
                        .getAsJsonArray("nodes")
                        .get(0)
                        .getAsJsonObject()
                        .get("targetRef"));
        assertFalse(result.toString().contains("private-"));
        for (String key : new String[] {"blockId", "uuid", "x", "y", "z", "resourceVersion"})
            assertFalse(node.has(key));
        assertTrue(node.getAsJsonArray("actions").isEmpty());
        assertFalse(result.get("fullScenePerception").getAsBoolean());
    }

    @Test
    void screenResizeViewTargetAgeAndIncompleteFramesFailClosed() {
        var sample = new WorldPresentation();
        var context = context("a", true, "view");
        sample.begin(context);
        sample.outlineSubmitted(context.target());
        sample.finish(context, 100);
        assertFalse(sample.read(context, 250_000_101L).get("available").getAsBoolean());
        assertFalse(sample.read(context("b", true, "view"), 110).get("available").getAsBoolean());
        assertFalse(sample.read(context("a", true, "turned"), 110).get("available").getAsBoolean());
        assertFalse(sample.read(context("a", false, "view"), 110).get("available").getAsBoolean());
        assertFalse(
                sample.read(
                                new WorldPresentation.Context(
                                        world, camera, new Object(), "a", 641, 361, true, "view"),
                                110)
                        .get("available")
                        .getAsBoolean());
        assertFalse(
                sample.read(
                                new WorldPresentation.Context(
                                        world, camera, null, "a", 642, 361, true, "view"),
                                110)
                        .get("available")
                        .getAsBoolean());
        sample.begin(context);
        sample.outlineSubmitted(context.target());
        sample.finish(context("b", true, "view"), 200);
        assertFalse(sample.read(context("b", true, "view"), 201).get("available").getAsBoolean());
    }

    @Test
    void newFrameWithoutOutlineCannotReuseOldTarget() {
        var sample = new WorldPresentation();
        var context = context("a", true, "view");
        sample.begin(context);
        sample.outlineSubmitted(context.target());
        sample.finish(context, 100);
        String ref =
                sample.read(context, 101)
                        .getAsJsonArray("nodes")
                        .get(0)
                        .getAsJsonObject()
                        .get("targetRef")
                        .getAsString();
        sample.begin(context);
        sample.finish(context, 200);
        assertTrue(sample.read(context, 201).getAsJsonArray("nodes").isEmpty());
        sample.begin(context);
        sample.outlineSubmitted(context.target());
        sample.finish(context, 300);
        assertNotEquals(
                ref,
                sample.read(context, 301)
                        .getAsJsonArray("nodes")
                        .get(0)
                        .getAsJsonObject()
                        .get("targetRef")
                        .getAsString());
    }
}
