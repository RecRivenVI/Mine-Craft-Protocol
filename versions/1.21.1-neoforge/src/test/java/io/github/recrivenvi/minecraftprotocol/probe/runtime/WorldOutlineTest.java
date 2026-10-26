package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

final class WorldOutlineTest {
    @Test
    void entityBodyPointKeepsRequestedHeadAndFeetInsteadOfAcceptingAnyEntityHit() {
        var box = new net.minecraft.world.phys.AABB(.25, 80, 2.25, .75, 81.975, 2.75);
        var eye = new Vec3(.5, 81.62, .5);
        Vec3 head = WorldTargeting.entityPoint(box, eye, .5, .9, .05),
                feet = WorldTargeting.entityPoint(box, eye, .5, .15, .05);
        assertEquals(2.25, head.z, .000001);
        assertEquals(2.25, feet.z, .000001);
        assertTrue(head.y > 81.6 && head.y < box.maxY);
        assertTrue(feet.y > 80.1 && feet.y < 80.55);
        assertEquals(.5, head.x, .000001);
        assertNotEquals(box.getCenter(), head);
    }

    @Test
    void explicitFaceUsesNativeFractionalHeightInsteadOfTheUnitCube() {
        BlockPos at = new BlockPos(-3, 80, 2);
        assertEquals(
                new Vec3(-2.5, 80.5, 2.5),
                WorldTargeting.facePoint(at, Shapes.box(0, 0, 0, 1, .5, 1), "up", .5, .5));
        assertEquals(
                new Vec3(-2.5, 80.875, 2.5),
                WorldTargeting.facePoint(
                        at, Shapes.box(.0625, 0, .0625, .9375, .875, .9375), "up", .5, .5));
        assertEquals(
                new Vec3(-2.9375, 80.25, 2.75),
                WorldTargeting.facePoint(
                        at, Shapes.box(.0625, 0, .0625, .9375, .875, .9375), "west", .75, .25));
    }

    @Test
    void explicitFaceDoesNotInventAnIntersectionAcrossAHollowShape() {
        var hollow = Shapes.or(Shapes.box(0, 0, 0, .25, 1, 1), Shapes.box(.75, 0, 0, 1, 1, 1));
        assertThrows(
                io.github.recrivenvi.minecraftprotocol.core.ProtocolState.ProtocolException.class,
                () -> WorldTargeting.facePoint(BlockPos.ZERO, hollow, "up", .5, .5));
        assertThrows(
                io.github.recrivenvi.minecraftprotocol.core.ProtocolState.ProtocolException.class,
                () -> WorldTargeting.facePoint(BlockPos.ZERO, Shapes.empty(), "up", .5, .5));
        assertEquals(
                new Vec3(.125, 1, .75),
                WorldTargeting.facePoint(BlockPos.ZERO, hollow, "up", .125, .75));
    }

    @Test
    void fullCubeKeepsTheExistingSixFaceUvConvention() {
        var cube = Shapes.block();
        for (var entry :
                java.util.Map.of(
                                "up",
                                new Vec3(.25, 1, .75),
                                "down",
                                new Vec3(.25, 0, .75),
                                "north",
                                new Vec3(.25, .75, 0),
                                "south",
                                new Vec3(.25, .75, 1),
                                "west",
                                new Vec3(0, .75, .25),
                                "east",
                                new Vec3(1, .75, .25))
                        .entrySet())
            assertEquals(
                    entry.getValue(),
                    WorldTargeting.facePoint(BlockPos.ZERO, cube, entry.getKey(), .25, .75));
    }

    @Test
    void thinAndFractionalOutlinesDoNotUseTheBlockCenter() {
        var points =
                WorldTargeting.outlinePoints(
                        new BlockPos(-3, 70, 4), Shapes.box(.375, 0, .25, .625, .125, .75));
        assertEquals(7, points.size());
        assertEquals(new Vec3(-2.5, 70.0625, 4.5), points.get(0));
        assertTrue(points.stream().anyMatch(p -> p.y > 70.12 && p.y < 70.125));
    }

    @Test
    void disjointShapesKeepRealBoxesAndEmptyShapesHaveNoTarget() {
        var shape = Shapes.or(Shapes.box(0, 0, 0, .25, 1, 1), Shapes.box(.75, 0, 0, 1, 1, 1));
        var points = WorldTargeting.outlinePoints(BlockPos.ZERO, shape);
        assertEquals(14, points.size());
        assertTrue(points.stream().allMatch(p -> p.x < .25 || p.x > .75));
        assertTrue(WorldTargeting.outlinePoints(BlockPos.ZERO, Shapes.empty()).isEmpty());
    }

    @Test
    void complexOutlinesHaveABoundedCandidateCount() {
        var shape = Shapes.empty();
        for (int i = 0; i < 100; i++)
            shape = Shapes.or(shape, Shapes.box(i / 200.0, 0, 0, (i + .5) / 200.0, 1, 1));
        assertEquals(64, WorldTargeting.outlinePoints(BlockPos.ZERO, shape).size());
    }
}
