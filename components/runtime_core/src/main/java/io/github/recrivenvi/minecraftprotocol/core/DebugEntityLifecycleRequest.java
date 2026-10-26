package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Typed entity factory coordinates and explicit loaded-chunk membership selection. */
public final class DebugEntityLifecycleRequest {
    public static final int MAX_CHUNK_MEMBERS = 4096;

    private DebugEntityLifecycleRequest() {}

    public record Chunk(int x, int z) {}

    public record Spawn(String entityType, double x, double y, double z, float yaw, float pitch) {
        public Chunk chunk() {
            return new Chunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        }
    }

    public static Chunk chunk(JsonObject value) {
        if (value == null || !value.keySet().equals(Set.of("x", "z")))
            throw invalid("entityChunk requires exactly integer x and z");
        return new Chunk(
                integer(value, "x", -1875000, 1874999), integer(value, "z", -1875000, 1874999));
    }

    public static Spawn spawn(JsonObject request) {
        JsonElement value = request.get("value");
        if (value == null || !value.isJsonObject())
            throw invalid("value must be a typed entity factory request");
        JsonObject data = value.getAsJsonObject();
        for (String key : data.keySet())
            if (!Set.of("entityType", "position", "yaw", "pitch").contains(key))
                throw invalid("Unsupported entity factory field: " + key);
        JsonElement type = data.get("entityType");
        if (type == null
                || !type.isJsonPrimitive()
                || !type.getAsJsonPrimitive().isString()
                || type.getAsString().isBlank()
                || type.getAsString().length() > 256)
            throw invalid("entityType must be a native registry identifier");
        JsonElement position = data.get("position");
        if (position == null
                || !position.isJsonObject()
                || !position.getAsJsonObject().keySet().equals(Set.of("x", "y", "z")))
            throw invalid("position requires exactly x, y and z");
        JsonObject at = position.getAsJsonObject();
        return new Spawn(
                type.getAsString(),
                number(at, "x", -29999984, 29999983),
                number(at, "y", -19999999, 19999999),
                number(at, "z", -29999984, 29999983),
                data.has("yaw") ? (float) number(data, "yaw", -180, 180) : 0,
                data.has("pitch") ? (float) number(data, "pitch", -90, 90) : 0);
    }

    public static int integer(JsonObject object, String name, int minimum, int maximum) {
        JsonElement value = object.get(name);
        int result;
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            result = value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException failure) {
            throw invalid(name + " must be an exact JSON integer");
        }
        if (result < minimum || result > maximum)
            throw invalid(name + " is outside the native coordinate domain");
        return result;
    }

    private static double number(JsonObject object, String name, double minimum, double maximum) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid(name + " must be a finite JSON number");
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < minimum || result > maximum)
            throw invalid(name + " is outside the native coordinate domain");
        return result;
    }

    public static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_ENTITY_LIFECYCLE", 400, message);
    }
}
