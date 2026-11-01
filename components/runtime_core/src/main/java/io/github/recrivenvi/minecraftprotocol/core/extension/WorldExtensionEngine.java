package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.api.extension.WorldAdapter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/** Observed parts retain their native parent; an adapter cannot dispatch or authorize input. */
public final class WorldExtensionEngine {
    private final String runtimeId;
    private final Supplier<List<WorldAdapter>> adapters;
    private final ThreadLocal<Budget> readBudget = new ThreadLocal<>();

    private static final class Budget {
        int parts, bytes;
    }

    public record Binding(String capabilityId, WorldAdapter.Frame frame, WorldAdapter.Part part) {}

    public record Discovery(List<Binding> parts, JsonArray limitations) {
        public Discovery {
            parts = List.copyOf(parts);
            limitations = limitations.deepCopy();
        }

        @Override
        public JsonArray limitations() {
            return limitations.deepCopy();
        }
    }

    public final class ReadScope implements AutoCloseable {
        private final Budget previous = readBudget.get();
        private boolean closed;

        private ReadScope() {
            readBudget.set(new Budget());
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (previous == null) readBudget.remove();
                else readBudget.set(previous);
            }
        }
    }

    public WorldExtensionEngine(String runtimeId, Supplier<List<WorldAdapter>> adapters) {
        this.runtimeId = java.util.Objects.requireNonNull(runtimeId);
        this.adapters = java.util.Objects.requireNonNull(adapters);
    }

    public ReadScope readScope() {
        return new ReadScope();
    }

    public String runtimeId() {
        return runtimeId;
    }

    public Discovery discover(
            WorldAdapter.Frame frame,
            Object parent,
            JsonObject snapshot,
            ToLongFunction<Object> identity,
            BooleanSupplier owner) {
        if (!owner.getAsBoolean() || !runtimeId.equals(frame.runtimeId()))
            throw new IllegalStateException("World projection is native-client-owner only");
        Budget budget = readBudget.get();
        if (budget == null) budget = new Budget();
        var parts = new ArrayList<Binding>();
        var issues = new JsonArray();
        for (WorldAdapter adapter : adapters.get()) {
            String id = "unknown";
            var active = new AtomicBoolean(true);
            try {
                var descriptor = adapter.descriptor();
                id = descriptor.id();
                var context =
                        new WorldAdapter.Context() {
                            private void check() {
                                if (!active.get() || !owner.getAsBoolean())
                                    throw new IllegalStateException("World capture context ended");
                            }

                            public WorldAdapter.Frame frame() {
                                return frame;
                            }

                            public Object nativeParent() {
                                check();
                                return parent;
                            }

                            public JsonObject nativeSnapshot() {
                                check();
                                return snapshot.deepCopy();
                            }

                            public long identify(Object value) {
                                check();
                                return identity.applyAsLong(
                                        java.util.Objects.requireNonNull(value));
                            }
                        };
                long started = System.nanoTime();
                if (!adapter.matches(frame)) continue;
                var projection = adapter.capture(context);
                long now = System.nanoTime();
                if (now - started > 10_000_000L)
                    throw new ExtensionFailure(
                            "EXTENSION_WORLD_ENTRY_BUDGET", "Return ready parts without waiting");
                if (projection == null) {
                    issue(issues, id, "snapshot_pending");
                    continue;
                }
                if (!projection.frame().equals(frame)
                        || projection.capturedAtNanos() > now
                        || now - projection.capturedAtNanos()
                                > descriptor.maxAgeMillis() * 1_000_000L)
                    throw new ExtensionFailure(
                            "EXTENSION_WORLD_SNAPSHOT_STALE", "World/parent/snapshot changed");
                if (projection.parts().size() > descriptor.maxParts())
                    throw new ExtensionFailure(
                            "EXTENSION_WORLD_PART_BUDGET", "Declared part limit exceeded");
                var keys = new HashSet<String>();
                var pending = new ArrayList<Binding>();
                int bytes = 0;
                for (var part : projection.parts()) {
                    if (!keys.add(part.key()))
                        throw new ExtensionFailure(
                                "EXTENSION_WORLD_DUPLICATE_PART", "Part keys must be unique");
                    var binding = new Binding(id, frame, part);
                    bytes += describe(binding).toString().getBytes(StandardCharsets.UTF_8).length;
                    pending.add(binding);
                }
                if (budget.parts + pending.size() > 256 || budget.bytes + bytes > 131072)
                    throw new ExtensionFailure(
                            "EXTENSION_WORLD_READ_BUDGET", "Read part/byte budget exceeded");
                budget.parts += pending.size();
                budget.bytes += bytes;
                parts.addAll(pending);
            } catch (RuntimeException failure) {
                issue(
                        issues,
                        id,
                        failure instanceof ExtensionFailure known
                                ? known.code()
                                : "EXTENSION_WORLD_CAPTURE_FAILED");
            } finally {
                active.set(false);
            }
        }
        return new Discovery(parts, issues);
    }

    public WorldAdapter.Part resolve(
            Binding expected,
            Object parent,
            JsonObject snapshot,
            ToLongFunction<Object> identity,
            BooleanSupplier owner) {
        var result = discover(expected.frame(), parent, snapshot, identity, owner);
        var current =
                result.parts().stream()
                        .filter(
                                p ->
                                        p.capabilityId().equals(expected.capabilityId())
                                                && p.part().key().equals(expected.part().key()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        stale(
                                                "Part missing or capture unavailable: "
                                                        + result.limitations()));
        var before = expected.part();
        var after = current.part();
        double tolerance = before.transformTolerance();
        if (!before.revision().equals(after.revision())
                || tolerance != after.transformTolerance()
                || !before.bounds().near(after.bounds(), tolerance)
                || Math.abs(before.aimPoint().x() - after.aimPoint().x()) > tolerance
                || Math.abs(before.aimPoint().y() - after.aimPoint().y()) > tolerance
                || Math.abs(before.aimPoint().z() - after.aimPoint().z()) > tolerance)
            throw stale("Part topology or transform changed; rediscover");
        return after;
    }

    private static ExtensionFailure stale(String detail) {
        return new ExtensionFailure("WORLD_PART_STALE", detail);
    }

    private static void issue(JsonArray issues, String id, String code) {
        var issue = new JsonObject();
        issue.addProperty("capabilityId", id);
        issue.addProperty("reason", code);
        issues.add(issue);
    }

    public static JsonObject describe(Binding binding) {
        var part = binding.part();
        var result = new JsonObject();
        result.addProperty("key", part.key());
        result.addProperty("revision", part.revision());
        result.addProperty("label", part.label());
        result.addProperty("capabilitySource", binding.capabilityId());
        result.addProperty("targetKind", "part");
        result.addProperty("category", "USER");
        result.addProperty("perspective", "CLIENT_KNOWN");
        var b = part.bounds();
        var bounds = new JsonObject();
        bounds.addProperty("minX", b.minX());
        bounds.addProperty("minY", b.minY());
        bounds.addProperty("minZ", b.minZ());
        bounds.addProperty("maxX", b.maxX());
        bounds.addProperty("maxY", b.maxY());
        bounds.addProperty("maxZ", b.maxZ());
        result.add("bounds", bounds);
        var point = new JsonObject();
        point.addProperty("x", part.aimPoint().x());
        point.addProperty("y", part.aimPoint().y());
        point.addProperty("z", part.aimPoint().z());
        result.add("aimPoint", point);
        var attributes = new JsonObject();
        part.attributes().forEach(attributes::addProperty);
        result.add("attributes", attributes);
        return result;
    }
}
