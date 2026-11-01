package io.github.recrivenvi.minecraftprotocol.extensions.littletiles;

import static io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge.NativeAccess.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.api.extension.*;
import io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge.ReadProvider;
import java.util.*;

/** Static cached micro geometry is USER; structure state remains independently authorized DEBUG. */
public final class LittleTilesExtension implements Extension, WorldAdapter {
    private String runtime;

    public void initialize(ExtensionContext context) {
        var scope = context.processResources();
        runtime = scope.owner().runtimeId();
        io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge.NativeHooks.open(
                runtime);
        scope.own(
                "passive-structure-counter",
                new ExtensionResources.ThreadOwner("detached_cleanup", Runnable::run, () -> true),
                () ->
                        io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge
                                .NativeHooks.close(runtime));
        if (context.side() == ExtensionContext.Side.CLIENT) context.worldAdapter(this);
        new io.github.recrivenvi.minecraftprotocol.extensions.littletiles.fixture.ExampleFixture(
                context);
        ReadProvider.register(
                context,
                "machine",
                "server_thread",
                io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge.NativeAccess
                        ::blockQuery,
                query -> machine(context, query));
    }

    public Descriptor descriptor() {
        return new Descriptor("littletiles:parts", 64, 250);
    }

    public boolean matches(Frame frame) {
        return frame.parentKind().equals("block");
    }

    public Projection capture(Context context) {
        Object entity = context.nativeParent();
        if (!entity.getClass()
                .getName()
                .startsWith("team.creative.littletiles.common.block.entity."))
            return new Projection(context.frame(), System.nanoTime(), List.of());
        JsonObject snapshot = context.nativeSnapshot();
        double x = snapshot.get("x").getAsDouble(),
                y = snapshot.get("y").getAsDouble(),
                z = snapshot.get("z").getAsDouble();
        int grid = ((Number) field(call(entity, "getGrid"), "count")).intValue();
        if (grid <= 0 || grid > 4096) throw new IllegalStateException("Invalid native grid");
        var parts = new ArrayList<Part>();
        outer:
        for (Object pair : (Iterable<?>) call(entity, "allTiles")) {
            Object tile = call(pair, "getValue");
            String material = Objects.toString(call(tile, "getBlockName"));
            for (Object box : (Iterable<?>) tile) {
                if (parts.size() == 64) break outer;
                double minX = x + number(box, "minX") / grid,
                        minY = y + number(box, "minY") / grid,
                        minZ = z + number(box, "minZ") / grid;
                double maxX = x + number(box, "maxX") / grid,
                        maxY = y + number(box, "maxY") / grid,
                        maxZ = z + number(box, "maxZ") / grid;
                String key =
                        "group:"
                                + context.identify(call(pair, "getKey"))
                                + ":tile:"
                                + context.identify(tile)
                                + ":box:"
                                + context.identify(box);
                String revision =
                        grid
                                + ":"
                                + number(box, "minX")
                                + ":"
                                + number(box, "minY")
                                + ":"
                                + number(box, "minZ")
                                + ":"
                                + number(box, "maxX")
                                + ":"
                                + number(box, "maxY")
                                + ":"
                                + number(box, "maxZ")
                                + ":"
                                + material
                                + ":"
                                + field(tile, "color")
                                + ":"
                                + call(tile, "getState");
                try {
                    revision =
                            java.util.HexFormat.of()
                                    .formatHex(
                                            java.security.MessageDigest.getInstance("SHA-256")
                                                    .digest(
                                                            revision.getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8)));
                } catch (java.security.NoSuchAlgorithmException failure) {
                    throw new IllegalStateException(failure);
                }
                parts.add(
                        new Part(
                                key,
                                revision,
                                material,
                                new Bounds(minX, minY, minZ, maxX, maxY, maxZ),
                                new Point((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2),
                                0,
                                Map.of(
                                        "material",
                                        material,
                                        "coverage",
                                        "bounded_first_64_static_boxes",
                                        "grid",
                                        Integer.toString(grid))));
            }
        }
        return new Projection(context.frame(), System.nanoTime(), parts);
    }

    private static double number(Object object, String field) {
        return ((Number) field(object, field)).doubleValue();
    }

    private JsonObject machine(ExtensionContext context, JsonObject query) {
        Object entity = loadedBlockEntity(context, query);
        var result = new JsonObject();
        result.addProperty("available", entity != null);
        result.addProperty("chunkLoadRequested", false);
        result.add(
                "interactionTrace",
                io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge.NativeHooks
                        .snapshot(runtime));
        var animations = new JsonArray();
        Object level = call(context.nativeContext("server_thread"), "overworld");
        int scanned = 0;
        for (Object animation : (Iterable<?>) call(level, "getAllEntities")) {
            if (scanned++ == 512) {
                result.addProperty("animationsScanTruncated", true);
                break;
            }
            if (!animation
                    .getClass()
                    .getName()
                    .equals(
                            "team.creative.littletiles.common.entity.animation.LittleAnimationEntity"))
                continue;
            double dx =
                    ((Number) call(animation, "getX")).doubleValue() - query.get("x").getAsDouble();
            double dy =
                    ((Number) call(animation, "getY")).doubleValue() - query.get("y").getAsDouble();
            double dz =
                    ((Number) call(animation, "getZ")).doubleValue() - query.get("z").getAsDouble();
            if (dx * dx + dy * dy + dz * dz > 256) continue;
            var item = new JsonObject();
            item.addProperty("uuid", Objects.toString(call(animation, "getUUID")));
            Object connection = field(animation, "structure");
            Object cached = connection == null ? null : field(connection, "cachedBE");
            if (cached != null) item.add("structures", structures(cached));
            else item.addProperty("stateUnavailable", "native_structure_not_cached");
            animations.add(item);
            if (animations.size() == 16) break;
        }
        result.add("animations", animations);
        if (entity == null) return result;
        result.addProperty("nativeType", entity.getClass().getName());
        if (!entity.getClass()
                .getName()
                .startsWith("team.creative.littletiles.common.block.entity.")) {
            result.addProperty("supported", false);
            return result;
        }
        result.addProperty("supported", true);
        result.addProperty("grid", ((Number) field(call(entity, "getGrid"), "count")).intValue());
        result.addProperty("tileCount", ((Number) call(entity, "tilesCount")).intValue());
        result.addProperty("boxCount", ((Number) call(entity, "boxesCount")).intValue());
        result.add("structures", structures(entity));
        return result;
    }

    private static JsonArray structures(Object entity) {
        var structures = new JsonArray();
        for (Object structure : (Iterable<?>) call(entity, "loadedStructures")) {
            if (structures.size() >= 32) {
                break;
            }
            var item = new JsonObject();
            item.addProperty("nativeType", structure.getClass().getName());
            item.addProperty("identity", System.identityHashCode(structure));
            item.addProperty("stateCount", ((List<?>) field(structure, "states")).size());
            item.addProperty("changing", Boolean.TRUE.equals(call(structure, "isChanging")));
            item.addProperty(
                    "outputState",
                    ((Number) call(call(call(structure, "getOutput", 0), "getState"), "number"))
                            .intValue());
            for (String name :
                    List.of(
                            "currentState",
                            "duration",
                            "rightClick",
                            "disableRightClick",
                            "activateParent")) {
                try {
                    Object value = field(structure, name);
                    if (value instanceof Number n) item.addProperty(name, n);
                    else if (value instanceof Boolean b) item.addProperty(name, b);
                } catch (IllegalStateException missing) {
                    if (!missing.getMessage().startsWith("Native field unavailable:"))
                        throw missing;
                }
            }
            structures.add(item);
        }
        return structures;
    }
}
