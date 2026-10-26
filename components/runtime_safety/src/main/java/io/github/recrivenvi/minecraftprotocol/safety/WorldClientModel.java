package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Bounded client-cache projection. A View must never consult a server, disk or loading API. */
public final class WorldClientModel {
    public static final int MAX_RADIUS = 32;
    public static final int MAX_SCENE_RADIUS = 8;
    public static final int MAX_BLOCKS = 512;
    public static final int MAX_ENTITIES = 128;
    public static final int MAX_BLOCK_SCAN = 4096;
    public static final int MAX_ENTITY_SCAN = 2048;
    public static final int MAX_BYTES = 524288;

    public interface View {
        boolean available();

        JsonObject player();

        JsonObject block(int x, int y, int z);

        Iterator<JsonObject> entities();
    }

    public static final class InvalidQuery extends IllegalArgumentException {
        public InvalidQuery(String message) {
            super(message);
        }
    }

    private WorldClientModel() {}

    public static void validate(String kind, JsonObject query) {
        Set<String> fields =
                switch (kind) {
                    case "player" -> Set.of();
                    case "block" -> Set.of("x", "y", "z");
                    case "entities" -> Set.of("radius", "limit");
                    case "scene" -> Set.of("radius", "blockLimit", "entityLimit");
                    default -> throw new InvalidQuery("Unknown USER client source");
                };
        if (!fields.containsAll(query.keySet()))
            throw new InvalidQuery("Unsupported USER query field");
        if (kind.equals("block"))
            for (String axis : Set.of("x", "y", "z"))
                integer(query, axis, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        if (kind.equals("entities")) {
            number(query, "radius", 16, 0, MAX_RADIUS);
            integer(query, "limit", 64, 1, MAX_ENTITIES, false);
        }
        if (kind.equals("scene")) {
            integer(query, "radius", 4, 0, MAX_SCENE_RADIUS, false);
            integer(query, "blockLimit", 256, 1, MAX_BLOCKS, false);
            integer(query, "entityLimit", 64, 1, MAX_ENTITIES, false);
        }
    }

    public static JsonObject capture(String kind, JsonObject query, View view) {
        validate(kind, query);
        JsonObject result = new JsonObject();
        result.addProperty("type", kind.equals("player") ? "player.state" : "world." + kind);
        result.addProperty("domain", "WORLD");
        result.addProperty("dataCategory", "USER");
        result.addProperty("authority", "CLIENT_KNOWN");
        result.addProperty("source", "bounded_client_world_model");
        result.addProperty("dataSource", "LIVE");
        result.addProperty("stateSemantics", "client_known_not_visibility_filtered");
        result.addProperty("visibility", "not_evaluated");
        result.addProperty("perceptionFiltered", false);
        result.addProperty("completeGameData", false);
        result.addProperty("storageAccessed", false);
        result.addProperty("chunkLoadRequested", false);
        result.addProperty("sampledAtMillis", System.currentTimeMillis());
        result.addProperty("precision", "client_state_not_server_truth");
        result.addProperty("available", view.available());
        if (kind.equals("player")) {
            result.addProperty("gameMode", "unknown");
            result.addProperty("onGround", "unknown");
            result.addProperty("flying", "unknown");
        }
        if (!view.available()) {
            result.addProperty("reason", "client_world_unavailable");
            return result;
        }
        JsonObject knownPlayer = view.player();
        JsonObject player =
                select(
                        knownPlayer,
                        Set.of(
                                "x",
                                "y",
                                "z",
                                "yaw",
                                "pitch",
                                "health",
                                "maxHealth",
                                "absorption",
                                "food",
                                "air",
                                "selectedSlot",
                                "dimension",
                                "usingItem",
                                "usingHand",
                                "riding",
                                "vehicleType",
                                "vehicleControllerIsPlayer",
                                "pose",
                                "crouching",
                                "sprinting",
                                "swimming",
                                "fallFlying",
                                "inWater",
                                "underWater",
                                "inLava",
                                "onFire",
                                "sleeping",
                                "deadOrDying",
                                "eyeHeight",
                                "maxAir",
                                "experienceLevel",
                                "experienceProgress",
                                "cameraType",
                                "cameraIsPlayer",
                                "useTicks"));
        for (String field : Set.of("gameMode", "onGround", "flying")) {
            var value = knownPlayer.get(field);
            boolean valid =
                    value != null
                            && value.isJsonPrimitive()
                            && (field.equals("gameMode")
                                    ? value.getAsJsonPrimitive().isString()
                                            && Set.of(
                                                            "survival",
                                                            "creative",
                                                            "adventure",
                                                            "spectator")
                                                    .contains(value.getAsString())
                                    : value.getAsJsonPrimitive().isBoolean());
            if (valid) player.add(field, value.deepCopy());
        }
        for (String field : Set.of("velocityX", "velocityY", "velocityZ")) {
            var value = knownPlayer.get(field);
            if (value != null
                    && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isNumber()
                    && Double.isFinite(value.getAsDouble())) player.add(field, value.deepCopy());
        }
        double px = player.get("x").getAsDouble(),
                py = player.get("y").getAsDouble(),
                pz = player.get("z").getAsDouble();
        JsonObject anchor = select(player, Set.of("x", "y", "z", "dimension"));
        result.add("anchor", anchor);
        result.addProperty("maxRadius", MAX_RADIUS);
        long deadline = System.nanoTime() + 25_000_000L;
        switch (kind) {
            case "player" -> player.entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
            case "block" -> {
                int x = query.get("x").getAsInt(),
                        y = query.get("y").getAsInt(),
                        z = query.get("z").getAsInt();
                result.addProperty("x", x);
                result.addProperty("y", y);
                result.addProperty("z", z);
                if (distance(x + 0.5, y + 0.5, z + 0.5, px, py, pz) > MAX_RADIUS * MAX_RADIUS) {
                    result.addProperty("available", false);
                    result.addProperty("reason", "outside_user_radius");
                } else {
                    JsonObject block = view.block(x, y, z);
                    select(
                                    block,
                                    Set.of(
                                            "available",
                                            "reason",
                                            "block",
                                            "state",
                                            "targetRef",
                                            "targetTtlMs"))
                            .entrySet()
                            .forEach(e -> result.add(e.getKey(), e.getValue()));
                }
            }
            case "entities" -> collectEntities(result, query, view, px, py, pz, deadline, false);
            case "scene" -> {
                int radius = integer(query, "radius", 4, 0, MAX_SCENE_RADIUS, false);
                int limit = integer(query, "blockLimit", 256, 1, MAX_BLOCKS, false);
                JsonArray blocks = new JsonArray();
                int scanned = 0, unavailable = 0;
                boolean truncated = false;
                int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
                outer:
                for (int shell = 0; shell <= radius; shell++)
                    for (int y = -shell; y <= shell; y++)
                        for (int z = -shell; z <= shell; z++)
                            for (int x = -shell; x <= shell; x++) {
                                if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)))
                                        != shell) continue;
                                cancelled();
                                if (scanned >= MAX_BLOCK_SCAN
                                        || blocks.size() >= limit
                                        || System.nanoTime() >= deadline) {
                                    truncated = true;
                                    break outer;
                                }
                                JsonObject block = view.block(cx + x, cy + y, cz + z);
                                scanned++;
                                if (!block.get("available").getAsBoolean()) {
                                    unavailable++;
                                    continue;
                                }
                                JsonObject node =
                                        select(
                                                block,
                                                Set.of(
                                                        "block",
                                                        "state",
                                                        "targetRef",
                                                        "targetTtlMs"));
                                node.addProperty("x", cx + x);
                                node.addProperty("y", cy + y);
                                node.addProperty("z", cz + z);
                                blocks.add(node);
                            }
                result.addProperty("coverage", "bounded_client_cache");
                result.addProperty("radius", radius);
                result.addProperty("blockLimit", limit);
                result.addProperty("blocksScanned", scanned);
                result.addProperty("unavailableBlockSamples", unavailable);
                result.addProperty("blocksTruncated", truncated);
                result.add("blocks", blocks);
                collectEntities(result, query, view, px, py, pz, deadline, true);
            }
            default -> throw new InvalidQuery("Unknown USER client source");
        }
        cancelled();
        if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new InvalidQuery("USER response byte budget exceeded");
        return result;
    }

    private static void collectEntities(
            JsonObject result,
            JsonObject query,
            View view,
            double px,
            double py,
            double pz,
            long deadline,
            boolean scene) {
        double radius =
                number(query, "radius", scene ? 4 : 16, 0, scene ? MAX_SCENE_RADIUS : MAX_RADIUS);
        int limit = integer(query, scene ? "entityLimit" : "limit", 64, 1, MAX_ENTITIES, false);
        Iterator<JsonObject> cursor = view.entities();
        JsonArray values = new JsonArray();
        int scanned = 0;
        while (scanned < MAX_ENTITY_SCAN
                && values.size() < limit
                && System.nanoTime() < deadline
                && cursor.hasNext()) {
            cancelled();
            JsonObject entity = cursor.next();
            scanned++;
            if (distance(
                            entity.get("x").getAsDouble(),
                            entity.get("y").getAsDouble(),
                            entity.get("z").getAsDouble(),
                            px,
                            py,
                            pz)
                    > radius * radius) continue;
            values.add(
                    select(
                            entity,
                            Set.of(
                                    "type",
                                    "name",
                                    "x",
                                    "y",
                                    "z",
                                    "yaw",
                                    "pitch",
                                    "invisible",
                                    "onGround",
                                    "hurtAnimation",
                                    "baby",
                                    "tamed",
                                    "sitting",
                                    "leashed",
                                    "leashHolderKnown",
                                    "leashHeldByPlayer",
                                    "itemId",
                                    "itemCount",
                                    "displayedItemId",
                                    "displayedItemCount",
                                    "displayedRotation",
                                    "partName",
                                    "targetRef",
                                    "targetTtlMs")));
        }
        result.add("entities", values);
        result.addProperty("radius", radius);
        result.addProperty("entityLimit", limit);
        result.addProperty("entitiesScanned", scanned);
        result.addProperty(
                "entitiesTruncated",
                scanned >= MAX_ENTITY_SCAN
                        || values.size() >= limit
                        || System.nanoTime() >= deadline);
    }

    private static JsonObject select(JsonObject source, Set<String> fields) {
        JsonObject result = new JsonObject();
        for (String key : fields)
            if (source.has(key) && source.get(key).isJsonPrimitive()) {
                var value = source.getAsJsonPrimitive(key);
                if (value.isString()) {
                    result.addProperty(
                            key,
                            value.getAsString()
                                    .substring(0, Math.min(512, value.getAsString().length())));
                    if (value.getAsString().length() > 512)
                        result.addProperty("textTruncated", true);
                } else if (value.isBoolean()
                        || (value.isNumber() && Double.isFinite(value.getAsDouble())))
                    result.add(key, value.deepCopy());
            }
        return result;
    }

    private static double distance(double x, double y, double z, double px, double py, double pz) {
        return (x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz);
    }

    private static void cancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("USER read cancelled");
    }

    private static double number(
            JsonObject query, String key, double fallback, double min, double max) {
        if (!query.has(key)) return fallback;
        try {
            if (!query.get(key).isJsonPrimitive() || !query.getAsJsonPrimitive(key).isNumber())
                throw new IllegalArgumentException();
            double value = query.get(key).getAsDouble();
            if (!Double.isFinite(value) || value < min || value > max)
                throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) {
            throw new InvalidQuery("Invalid USER " + key + " budget/value");
        }
    }

    private static int integer(
            JsonObject query, String key, int fallback, int min, int max, boolean required) {
        if (!query.has(key) && !required) return fallback;
        try {
            if (!query.has(key)
                    || !query.get(key).isJsonPrimitive()
                    || !query.getAsJsonPrimitive(key).isNumber())
                throw new IllegalArgumentException();
            int value = query.get(key).getAsBigDecimal().intValueExact();
            if (value < min || value > max) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) {
            throw new InvalidQuery("Invalid USER " + key + " budget/value");
        }
    }
}
