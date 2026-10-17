package io.github.recrivenvi.minecraftprotocol.safety;

/**
 * Computes relative mouse deltas; the adapter still dispatches them through Vanilla camera input.
 */
public final class WorldAim {
    private WorldAim() {}

    public static double[] delta(
            double x,
            double y,
            double z,
            double yaw,
            double pitch,
            double sensitivity,
            boolean invertX,
            boolean invertY) {
        for (double value : new double[] {x, y, z, yaw, pitch, sensitivity})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite aim");
        if (sensitivity < 0 || sensitivity > 1) throw new IllegalArgumentException("Sensitivity");
        double desiredYaw = Math.toDegrees(Math.atan2(-x, z));
        double desiredPitch = -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
        double yawError = ((desiredYaw - yaw) % 360 + 540) % 360 - 180;
        double pitchError = desiredPitch - pitch;
        double factor = Math.pow(sensitivity * 0.6 + 0.2, 3) * 8 * 0.15;
        // Bound each correction, then close the loop on the actual Vanilla result.
        return new double[] {
            Math.max(-240, Math.min(240, yawError / factor)) * (invertX ? -1 : 1),
            Math.max(-240, Math.min(240, pitchError / factor)) * (invertY ? -1 : 1)
        };
    }
}
