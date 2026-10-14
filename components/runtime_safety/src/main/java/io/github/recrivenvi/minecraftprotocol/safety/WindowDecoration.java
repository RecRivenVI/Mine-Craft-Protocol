package io.github.recrivenvi.minecraftprotocol.safety;

/** Decorations are derived from the latest game output, never from a decorated native window. */
public final class WindowDecoration {
    private static final int[] POINTER_PIXELS = pointerPixels();

    private WindowDecoration() {}

    private static int[] pointerPixels() {
        int size = ControlChrome.POINTER_DIAMETER;
        int[] pixels = new int[size * size];
        ControlChrome.pointer(
                (x0, y0, x1, y1, color) -> pixels[y0 * size + x0] = color, size / 2, size / 2, 1F);
        return pixels;
    }

    public static String title(String original, AgentControlSession.Mode mode, boolean present) {
        return original
                + (!present
                        ? ""
                        : switch (mode) {
                            case READ -> " - 智能体读取中";
                            case OPERATE -> " - 智能体操控中";
                            case TAKEOVER -> " - 由智能体接管";
                        });
    }

    /** Straight-alpha RGBA; nearest-neighbor orb pixels confined to the bottom-right quadrant. */
    public static byte[] icon(int width, int height, byte[] original) {
        if (width < 1
                || height < 1
                || width > 512
                || height > 512
                || original.length != width * height * 4)
            throw new IllegalArgumentException("Invalid window icon dimensions");
        byte[] result = original.clone();
        int diameter = Math.max(1, Math.min(width, height) / 2);
        int size = ControlChrome.POINTER_DIAMETER;
        for (int y = 0; y < diameter; y++) {
            int sourceY = (2 * y + 1) * size / (2 * diameter);
            for (int x = 0; x < diameter; x++) {
                int sourceX = (2 * x + 1) * size / (2 * diameter);
                int color = POINTER_PIXELS[sourceY * size + sourceX];
                if ((color >>> 24) == 0) continue;
                int offset = ((height - diameter + y) * width + width - diameter + x) * 4;
                double sourceAlpha = (color >>> 24) / 255.0;
                double backgroundAlpha = (original[offset + 3] & 255) / 255.0;
                double alpha = sourceAlpha + backgroundAlpha * (1 - sourceAlpha);
                for (int channel = 0; channel < 3; channel++) {
                    int source = (color >>> (16 - channel * 8)) & 255;
                    int background = original[offset + channel] & 255;
                    result[offset + channel] =
                            (byte)
                                    Math.round(
                                            (source * sourceAlpha
                                                            + background
                                                                    * backgroundAlpha
                                                                    * (1 - sourceAlpha))
                                                    / alpha);
                }
                result[offset + 3] = (byte) Math.round(alpha * 255);
            }
        }
        return result;
    }
}
