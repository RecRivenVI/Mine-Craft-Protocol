package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PointerRenderingTest {
    record Pixel(int x, int y, int color) {}

    static final class Surface implements ControlChrome.PointerTransform, ControlChrome.Rectangles {
        double x, y;
        int pushes, pops;
        final List<Pixel> pixels = new ArrayList<>();

        public void push(double x, double y) {
            this.x = x;
            this.y = y;
            pushes++;
        }

        public void pop() {
            pops++;
        }

        public void fill(int x0, int y0, int x1, int y1, int color) {
            assertEquals(x0 + 1, x1);
            assertEquals(y0 + 1, y1);
            pixels.add(new Pixel(x0, y0, color));
        }
    }

    @Test
    void fractionalHotspotIsTheAbsoluteOrbCenterAtEveryClickScale() {
        var pointer = new AgentPointer();
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120.25);
        long revision = pointer.revision();
        for (float scale : new float[] {1F, 0.94F, 0.85F, 0.81F, 0.7F}) {
            var surface = new Surface();
            ControlChrome.pointer(
                    surface, surface, pointer.x(), pointer.y(), 427, 240, 427, 240, 1F, scale);
            assertEquals(213.5, surface.x);
            assertEquals(120.25, surface.y);
            assertEquals(1, surface.pushes);
            assertEquals(1, surface.pops);
            for (Pixel pixel : surface.pixels) {
                assertTrue(surface.pixels.contains(new Pixel(-pixel.x - 1, pixel.y, pixel.color)));
                assertTrue(surface.pixels.contains(new Pixel(pixel.x, -pixel.y - 1, pixel.color)));
            }
            int minX = surface.pixels.stream().mapToInt(Pixel::x).min().orElseThrow();
            int maxX = surface.pixels.stream().mapToInt(Pixel::x).max().orElseThrow();
            assertEquals(surface.x, surface.x + (minX + maxX + 1) / 2.0);
        }
        assertEquals(213.5, pointer.x());
        assertEquals(120.25, pointer.y());
        assertEquals(revision, pointer.revision());
    }

    @Test
    void ceilGuiDimensionsDoNotDisplaceThePhysicalInputHotspot() {
        // Legacy chrome projects with integer GUI extents; modern native GUI uses width / scale.
        for (int[] viewport :
                new int[][] {
                    {2560, 1440, 6},
                    {2559, 1439, 6},
                    {1920, 1080, 3},
                    {3841, 2161, 7},
                    {800, 600, 2}
                }) {
            int width = viewport[0], height = viewport[1], scale = viewport[2];
            int guiWidth = (int) Math.ceil(width / (double) scale);
            int guiHeight = (int) Math.ceil(height / (double) scale);
            for (boolean modern : new boolean[] {false, true}) {
                double projectionWidth = modern ? width / (float) scale : guiWidth;
                double projectionHeight = modern ? height / (float) scale : guiHeight;
                for (double[] point :
                        new double[][] {
                            {guiWidth / 2.0, guiHeight / 2.0},
                            {99.125, 87.875},
                            {guiWidth - 0.25, guiHeight - 0.125}
                        }) {
                    var surface = new Surface();
                    ControlChrome.pointer(
                            surface,
                            surface,
                            point[0],
                            point[1],
                            guiWidth,
                            guiHeight,
                            projectionWidth,
                            projectionHeight,
                            1F,
                            1F);
                    double deliveredX = point[0] * width / guiWidth;
                    double deliveredY = point[1] * height / guiHeight;
                    assertEquals(deliveredX, surface.x * width / projectionWidth, 1e-9);
                    assertEquals(deliveredY, surface.y * height / projectionHeight, 1e-9);
                    // The unavoidable final GPU float conversion stays far below a display pixel.
                    assertEquals(deliveredX, (float) surface.x * width / projectionWidth, 0.001);
                    assertEquals(deliveredY, (float) surface.y * height / projectionHeight, 0.001);
                }
            }
        }
    }

    @Test
    void renderFailureStillRestoresTheNativePose() {
        var surface = new Surface();
        assertThrows(
                IllegalStateException.class,
                () ->
                        ControlChrome.pointer(
                                (x0, y0, x1, y1, color) -> {
                                    throw new IllegalStateException("synthetic");
                                },
                                surface,
                                12.5,
                                24.75,
                                427,
                                240,
                                2560 / 6F,
                                240,
                                1F,
                                1F));
        assertEquals(1, surface.pushes);
        assertEquals(1, surface.pops);
    }

    @Test
    void invalidViewportNeverSubmitsAPoseOrPixels() {
        var surface = new Surface();
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, -1}) {
            ControlChrome.pointer(surface, surface, 12.5, 24.75, 427, 240, invalid, 240, 1F, 1F);
        }
        ControlChrome.pointer(surface, surface, Double.NaN, 24.75, 427, 240, 427, 240, 1F, 1F);
        ControlChrome.pointer(surface, surface, 12.5, 24.75, 0, 240, 427, 240, 1F, 1F);
        assertEquals(0, surface.pushes);
        assertEquals(0, surface.pops);
        assertTrue(surface.pixels.isEmpty());
    }
}
