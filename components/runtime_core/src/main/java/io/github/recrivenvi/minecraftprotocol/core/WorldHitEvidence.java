package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.util.Set;

/** Only bounded client pick facts may leave a failed USER gesture; never its private guard. */
public final class WorldHitEvidence {
    private WorldHitEvidence() {}

    public static JsonObject details(JsonObject hit) {
        JsonObject result = new JsonObject();
        if (hit != null) result.add("actualHit", bounded(hit));
        return result;
    }

    public static JsonObject bounded(JsonObject source) {
        JsonObject result = new JsonObject();
        result.addProperty("dataCategory", "USER");
        result.addProperty("authority", "CLIENT_KNOWN");
        result.addProperty("kind", "unknown");
        result.addProperty("source", "unknown");
        if (source == null) return result;
        String origin = text(source, "source");
        if (Set.of("native_interaction_pick", "bounded_client_vanilla_ray", "candidate_outline_ray")
                .contains(origin)) result.addProperty("source", origin);
        String kind = text(source, "kind");
        if (!Set.of("block", "entity", "miss").contains(kind)) return result;
        JsonObject position = vector(source, "position", false);
        double distance = number(source, "distance");
        if (position == null || !Double.isFinite(distance) || distance < 0 || distance > 32.001)
            return result;
        if (kind.equals("block")) {
            JsonObject block = vector(source, "block", true);
            String face = text(source, "face");
            if (block == null
                    || !Set.of("up", "down", "north", "south", "east", "west").contains(face))
                return result;
            result.add("block", block);
            result.addProperty("face", face);
        }
        if (kind.equals("entity")) {
            String type = text(source, "entityType");
            if (type.length() > 256 || !type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return result;
            result.addProperty("entityType", type);
        }
        result.addProperty("kind", kind);
        result.addProperty("distance", distance);
        result.add("position", position);
        return result;
    }

    private static JsonObject vector(JsonObject source, String key, boolean integral) {
        if (!source.has(key) || !source.get(key).isJsonObject()) return null;
        JsonObject result = new JsonObject();
        for (String axis : new String[] {"x", "y", "z"}) {
            double value = number(source.getAsJsonObject(key), axis);
            if (!Double.isFinite(value)
                    || Math.abs(value) > Integer.MAX_VALUE
                    || integral && value != Math.rint(value)) return null;
            if (integral) result.addProperty(axis, (int) value);
            else result.addProperty(axis, value);
        }
        return result;
    }

    private static double number(JsonObject source, String key) {
        var value = source.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : Double.NaN;
    }

    private static String text(JsonObject source, String key) {
        var value = source.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : "";
    }
}
