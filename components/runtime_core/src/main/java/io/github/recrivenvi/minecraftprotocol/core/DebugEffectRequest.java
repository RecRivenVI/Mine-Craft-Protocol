package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Set;

/** Bounded typed native effect chains, without arbitrary serialized entity data. */
public record DebugEffectRequest(
        String effectId, JsonObject value, boolean hasExpectedValue, JsonObject expectedValue) {
    public static final int MAX_HIDDEN_EFFECTS = 16;
    private static final Set<String> FIELDS =
            Set.of("duration", "amplifier", "ambient", "showParticles", "showIcon", "hidden");

    public static DebugEffectRequest parse(JsonObject request) {
        JsonElement id = request.get("effectId");
        if (id == null
                || !id.isJsonPrimitive()
                || !id.getAsJsonPrimitive().isString()
                || id.getAsString().isBlank()
                || id.getAsString().length() > 256)
            throw invalid("effectId must be a native identifier");
        if (!request.has("value")) throw invalid("value must be an effect object or explicit null");
        return new DebugEffectRequest(
                id.getAsString(),
                normalize(request.get("value")),
                request.has("expectedValue"),
                request.has("expectedValue") ? normalize(request.get("expectedValue")) : null);
    }

    private static JsonObject normalize(JsonElement element) {
        if (element.isJsonNull()) return null;
        JsonObject result = leaf(element, true);
        JsonArray hidden = new JsonArray();
        JsonObject input = element.getAsJsonObject();
        if (input.has("hidden")) {
            if (!input.get("hidden").isJsonArray()
                    || input.getAsJsonArray("hidden").size() > MAX_HIDDEN_EFFECTS)
                throw invalid(
                        "hidden must be a flat array of at most "
                                + MAX_HIDDEN_EFFECTS
                                + " native effects");
            for (JsonElement value : input.getAsJsonArray("hidden")) hidden.add(leaf(value, false));
        }
        result.add("hidden", hidden);
        return result;
    }

    private static JsonObject leaf(JsonElement element, boolean root) {
        if (!element.isJsonObject()) throw invalid("Effect value must be a typed object");
        JsonObject input = element.getAsJsonObject();
        for (String key : input.keySet())
            if (!FIELDS.contains(key) || (!root && key.equals("hidden")))
                throw invalid("Unsupported effect field: " + key);
        int duration = integer(input, "duration", 0, true);
        int amplifier = integer(input, "amplifier", 0, false);
        if (duration < -1 || duration > 1_200_000 || amplifier < 0 || amplifier > 255)
            throw invalid("duration must be -1 or 0..1200000 ticks; amplifier must be 0..255");
        boolean particles = bool(input, "showParticles", true);
        JsonObject result = new JsonObject();
        result.addProperty("duration", duration);
        result.addProperty("amplifier", amplifier);
        result.addProperty("ambient", bool(input, "ambient", false));
        result.addProperty("showParticles", particles);
        result.addProperty("showIcon", bool(input, "showIcon", particles));
        return result;
    }

    private static int integer(JsonObject object, String name, int defaultValue, boolean required) {
        if (!object.has(name)) {
            if (required) throw invalid(name + " is required");
            return defaultValue;
        }
        JsonElement value = object.get(name);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            return value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException error) {
            throw invalid(name + " must be an exact JSON integer");
        }
    }

    private static boolean bool(JsonObject object, String name, boolean defaultValue) {
        if (!object.has(name)) return defaultValue;
        JsonElement value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw invalid(name + " must be a JSON boolean");
        return value.getAsBoolean();
    }

    public void verifyExpected(JsonObject current) {
        if (hasExpectedValue && !json(expectedValue).equals(json(current)))
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 409, "Effect value precondition failed");
    }

    public static JsonElement json(JsonObject value) {
        return value == null ? JsonNull.INSTANCE : value;
    }

    public static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_EFFECT", 400, message);
    }
}
