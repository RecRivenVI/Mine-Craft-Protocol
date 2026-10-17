package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ControlEdgeTest {
    private static int[][] render(int width, int height, float fade, float band, double guiScale) {
        int[][] alpha = new int[height][width];
        int[][] writes = new int[height][width];
        ControlChrome.edges(
                (x0, y0, x1, y1, color) -> {
                    assertTrue(x0 >= 0 && y0 >= 0 && x1 <= width && y1 <= height);
                    assertTrue(x0 < x1 && y0 < y1);
                    for (int y = y0; y < y1; y++)
                        for (int x = x0; x < x1; x++) {
                            assertEquals(
                                    1,
                                    ++writes[y][x],
                                    "No alpha accumulation at corner/edge joins");
                            alpha[y][x] = color >>> 24;
                        }
                },
                width,
                height,
                fade,
                band,
                guiScale);
        return alpha;
    }

    @Test
    void smoothCornerUnionPreservesStraightEdgesAndLeavesTheCenterUntouched() {
        int[][] a = render(96, 64, 1, 1, 6);
        assertEquals(245, a[0][0]);
        assertEquals(0, a[32][48]);
        assertEquals(Math.round(245 * (1 - 2 / 15F) * (1 - 2 / 15F)), a[2][48]);
        assertTrue(
                a[2][2] > a[2][48],
                "Blend both nearby edges, not a square minimum-distance crease");
        assertEquals(a[3][16], a[3][15], "Corner joins the horizontal profile without a step");
        assertEquals(a[16][3], a[15][3], "Corner joins the vertical profile without a step");
        assertEquals(a[2][3], a[3][2]);
        assertTrue(a[2][3] < a[2][2]);
    }

    @Test
    void allCornersMirrorAcrossScaleResizeFadeAndBreathingWidth() {
        for (double scale : new double[] {1, 3, 6})
            for (float band : new float[] {.88F, 1F})
                for (float fade : new float[] {.1F, .55F, 1F}) {
                    int[][] a = render(193, 129, fade, band, scale);
                    for (int y = 0; y < a.length; y++)
                        for (int x = 0; x < a[y].length; x++) {
                            assertEquals(a[y][x], a[y][a[y].length - x - 1]);
                            assertEquals(a[y][x], a[a.length - y - 1][x]);
                            assertTrue(a[y][x] <= Math.round(245 * fade));
                        }
                }
    }

    @Test
    void tinyOrInvisibleSurfacesCannotOverlapOrSubmitInvalidRectangles() {
        for (int width = 1; width < 5; width++)
            for (int height = 1; height < 5; height++) render(width, height, 1, 1, 1);
        for (float fade : new float[] {0, Float.NaN, Float.POSITIVE_INFINITY})
            ControlChrome.edges((a, b, c, d, e) -> fail("Invisible or invalid Fade"), 96, 64, fade);
    }
}
