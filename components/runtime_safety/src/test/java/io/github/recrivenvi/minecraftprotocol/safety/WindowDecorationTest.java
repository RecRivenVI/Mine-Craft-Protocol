package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class WindowDecorationTest {
    @Test
    void latestGameTitleIsPreservedInEveryModeIncludingSuffixLikeText() {
        String original = "Minecraft · Demo - 智能体读取中";
        for (var mode : AgentControlSession.Mode.values()) {
            assertEquals(original, WindowDecoration.title(original, mode, false));
            assertTrue(WindowDecoration.title(original, mode, true).startsWith(original));
        }
        assertEquals(
                "New world - 由智能体接管",
                WindowDecoration.title("New world", AgentControlSession.Mode.TAKEOVER, true));
    }

    @Test
    void repeatedTransitionsAlwaysDecorateTheBaseOnce() {
        for (int cycle = 0; cycle < 20; cycle++) {
            assertEquals(
                    "Minecraft - 智能体读取中",
                    WindowDecoration.title("Minecraft", AgentControlSession.Mode.READ, true));
            assertEquals(
                    "Minecraft - 智能体操控中",
                    WindowDecoration.title("Minecraft", AgentControlSession.Mode.OPERATE, true));
            assertEquals(
                    "Minecraft - 由智能体接管",
                    WindowDecoration.title("Minecraft", AgentControlSession.Mode.TAKEOVER, true));
            assertEquals(
                    "Minecraft",
                    WindowDecoration.title("Minecraft", AgentControlSession.Mode.READ, false));
        }
    }

    @Test
    void pixelBadgeOnlyChangesTheBottomRightQuarterOfEveryResolution() {
        for (int size : new int[] {1, 16, 17, 32, 48, 64, 128, 256, 512}) {
            byte[] original = new byte[size * size * 4];
            Arrays.fill(original, (byte) 255);
            byte[] saved = original.clone();
            byte[] decorated = WindowDecoration.icon(size, size, original);
            assertArrayEquals(saved, original);
            assertArrayEquals(decorated, WindowDecoration.icon(size, size, original));
            int diameter = Math.max(1, size / 2);
            assertFalse(Arrays.equals(original, decorated));
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++) {
                    if (x >= size - diameter && y >= size - diameter) continue;
                    int at = (y * size + x) * 4;
                    assertArrayEquals(
                            Arrays.copyOfRange(original, at, at + 4),
                            Arrays.copyOfRange(decorated, at, at + 4));
                }
        }
    }

    @Test
    void enlargedBadgeRepeatsNativePointerPixelsInsteadOfRecomputingASmoothGradient() {
        int sourceSize = ControlChrome.POINTER_DIAMETER;
        int[] pixels = new int[sourceSize * sourceSize];
        ControlChrome.pointer(
                (x0, y0, x1, y1, color) -> pixels[y0 * sourceSize + x0] = color,
                sourceSize / 2,
                sourceSize / 2,
                1F);
        // 48 has exact 1:1 source pixels; 256 requires uneven nearest-neighbor pixel blocks.
        for (int size : new int[] {16, 48, 96, 128, 256, 512}) {
            byte[] decorated = WindowDecoration.icon(size, size, new byte[size * size * 4]);
            int diameter = size / 2;
            for (int y = 0; y < diameter; y++)
                for (int x = 0; x < diameter; x++) {
                    int sourceX = (int) Math.floor((x + 0.5) * sourceSize / diameter);
                    int sourceY = (int) Math.floor((y + 0.5) * sourceSize / diameter);
                    int color = pixels[sourceY * sourceSize + sourceX];
                    int offset = ((size - diameter + y) * size + size - diameter + x) * 4;
                    assertEquals((color >>> 16) & 255, decorated[offset] & 255);
                    assertEquals((color >>> 8) & 255, decorated[offset + 1] & 255);
                    assertEquals(color & 255, decorated[offset + 2] & 255);
                    assertEquals(color >>> 24, decorated[offset + 3] & 255);
                }
        }
    }

    @Test
    void pixelBadgePreservesStraightAlphaCompositionAndRectangularSources() {
        int width = 80, height = 48;
        byte[] original = new byte[width * height * 4];
        for (int i = 0; i < original.length; i += 4) {
            original[i] = 27;
            original[i + 1] = 43;
            original[i + 2] = 61;
            original[i + 3] = (byte) 128;
        }
        byte[] decorated = WindowDecoration.icon(width, height, original);
        byte[] overlay = WindowDecoration.icon(width, height, new byte[original.length]);
        for (int i = 0; i < original.length; i += 4) {
            double a = (overlay[i + 3] & 255) / 255.0;
            if (a == 0) {
                assertArrayEquals(
                        Arrays.copyOfRange(original, i, i + 4),
                        Arrays.copyOfRange(decorated, i, i + 4));
                continue;
            }
            int pixel = i / 4;
            assertTrue(pixel % width >= width - height / 2 && pixel / width >= height / 2);
            double backgroundAlpha = 128 / 255.0, alpha = a + backgroundAlpha * (1 - a);
            for (int channel = 0; channel < 3; channel++) {
                assertEquals(
                        (int)
                                Math.round(
                                        ((overlay[i + channel] & 255) * a
                                                        + (original[i + channel] & 255)
                                                                * backgroundAlpha
                                                                * (1 - a))
                                                / alpha),
                        decorated[i + channel] & 255);
            }
            assertEquals((int) Math.round(alpha * 255), decorated[i + 3] & 255);
        }
    }

    @Test
    void transparentBackgroundDoesNotDarkenOrbColors() {
        byte[] output = WindowDecoration.icon(32, 32, new byte[32 * 32 * 4]);
        int at = (24 * 32 + 24) * 4;
        assertEquals(255, output[at + 2] & 255);
        assertTrue((output[at + 3] & 255) > 200);
        assertEquals(0, output[3]);
    }

    @Test
    void replacementSourceDoesNotRetainOldPixelsOrMutateEitherSource() {
        byte[] first = new byte[32 * 16 * 4], second = first.clone();
        Arrays.fill(second, (byte) 255);
        byte[] a = WindowDecoration.icon(32, 16, first);
        byte[] b = WindowDecoration.icon(32, 16, second);
        assertNotEquals(a[0], b[0]);
        assertEquals(0, first[0]);
        assertEquals((byte) 255, second[0]);
    }

    @Test
    void invalidAndOversizedIconsAreRejectedBeforeAllocation() {
        assertThrows(
                IllegalArgumentException.class, () -> WindowDecoration.icon(0, 1, new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> WindowDecoration.icon(513, 1, new byte[2052]));
        assertThrows(
                IllegalArgumentException.class, () -> WindowDecoration.icon(16, 16, new byte[3]));
        assertDoesNotThrow(() -> WindowDecoration.icon(1, 1, new byte[4]));
    }
}
