package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

final class WorldOutlineTest {
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
