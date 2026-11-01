package io.github.recrivenvi.minecraftprotocol.api.extension;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/** Client-cache parts only. The host keeps the original native pick and input authority. */
public interface WorldAdapter {
    record Descriptor(String id, int maxParts, int maxAgeMillis) {
        public Descriptor {
            java.util.Objects.requireNonNull(id);
            if (maxParts < 1 || maxParts > 64 || maxAgeMillis < 1 || maxAgeMillis > 1000)
                throw new IllegalArgumentException("Bounded world descriptor required");
        }
    }

    record Frame(
            String runtimeId,
            long worldIdentity,
            String dimension,
            String parentKind,
            String parentKey,
            long parentIdentity,
            String parentTargetRef) {
        public Frame {
            java.util.Objects.requireNonNull(runtimeId);
            java.util.Objects.requireNonNull(dimension);
            java.util.Objects.requireNonNull(parentKind);
            java.util.Objects.requireNonNull(parentKey);
            java.util.Objects.requireNonNull(parentTargetRef);
            if (worldIdentity <= 0 || parentIdentity <= 0)
                throw new IllegalArgumentException("Native world/parent identity required");
        }
    }

    record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Finite world point required");
        }
    }

    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Bounds {
            if (!Double.isFinite(minX)
                    || !Double.isFinite(minY)
                    || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX)
                    || !Double.isFinite(maxY)
                    || !Double.isFinite(maxZ)
                    || maxX <= minX
                    || maxY <= minY
                    || maxZ <= minZ
                    || maxX - minX > 64
                    || maxY - minY > 64
                    || maxZ - minZ > 64)
                throw new IllegalArgumentException("Finite positive bounded world box required");
        }

        public boolean contains(Point point, double tolerance) {
            return point.x >= minX - tolerance
                    && point.x <= maxX + tolerance
                    && point.y >= minY - tolerance
                    && point.y <= maxY + tolerance
                    && point.z >= minZ - tolerance
                    && point.z <= maxZ + tolerance;
        }

        public boolean near(Bounds other, double tolerance) {
            return Math.abs(minX - other.minX) <= tolerance
                    && Math.abs(minY - other.minY) <= tolerance
                    && Math.abs(minZ - other.minZ) <= tolerance
                    && Math.abs(maxX - other.maxX) <= tolerance
                    && Math.abs(maxY - other.maxY) <= tolerance
                    && Math.abs(maxZ - other.maxZ) <= tolerance;
        }
    }

    record Part(
            String key,
            String revision,
            String label,
            Bounds bounds,
            Point aimPoint,
            double transformTolerance,
            Map<String, String> attributes) {
        public Part {
            if (key == null
                    || !key.matches("[A-Za-z0-9_.:-]{1,128}")
                    || revision == null
                    || revision.length() > 256
                    || label == null
                    || label.length() > 512
                    || !Double.isFinite(transformTolerance)
                    || transformTolerance < 0
                    || transformTolerance > 1.0 / 32)
                throw new IllegalArgumentException("Stable bounded part required");
            java.util.Objects.requireNonNull(bounds);
            java.util.Objects.requireNonNull(aimPoint);
            if (!bounds.contains(aimPoint, 0))
                throw new IllegalArgumentException("Aim point must belong to the part");
            attributes = Map.copyOf(attributes);
            if (attributes.size() > 16
                    || attributes.entrySet().stream()
                            .anyMatch(
                                    e ->
                                            !e.getKey().matches("[A-Za-z0-9_.:-]{1,64}")
                                                    || e.getValue().length() > 512))
                throw new IllegalArgumentException("Bounded detached attributes required");
        }
    }

    interface Context {
        Frame frame();

        Object nativeParent();

        JsonObject nativeSnapshot();

        long identify(Object nativePart);
    }

    record Projection(Frame frame, long capturedAtNanos, List<Part> parts) {
        public Projection {
            java.util.Objects.requireNonNull(frame);
            parts = List.copyOf(parts);
        }
    }

    Descriptor descriptor();

    boolean matches(Frame frame);

    /** Return a ready detached snapshot, or null. Never load chunks or wait on another owner. */
    Projection capture(Context context);
}
