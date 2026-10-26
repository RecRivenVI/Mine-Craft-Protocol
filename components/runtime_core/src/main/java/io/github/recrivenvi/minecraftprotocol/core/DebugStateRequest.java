package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;

/** Closed native state values, separate from permissions and resource-version admission. */
public final class DebugStateRequest {
    private static final Set<String> PLAYER =
            Set.of(
                    "food",
                    "saturation",
                    "air",
                    "absorption",
                    "experienceLevel",
                    "experiencePoints",
                    "velocity",
                    "flying");
    private static final Set<String> ENTITY =
            Set.of(
                    "health",
                    "air",
                    "absorption",
                    "silent",
                    "noGravity",
                    "glowing",
                    "noAi",
                    "velocity");

    private DebugStateRequest() {}

    public static JsonObject parse(JsonObject request, boolean player) {
        JsonObject values = values(request.get("value"), player);
        if (values.has("experienceLevel") && values.has("experiencePoints"))
            throw invalid(
                    "Set experienceLevel and level-relative experiencePoints in separate versioned mutations");
        if (request.has("expectedValue")) values(request.get("expectedValue"), player);
        return values;
    }

    private static JsonObject values(JsonElement value, boolean player) {
        if (value == null || !value.isJsonObject() || value.getAsJsonObject().size() == 0)
            throw invalid("State value must be a nonempty typed object");
        JsonObject object = value.getAsJsonObject();
        for (var field : object.entrySet()) {
            String name = field.getKey();
            if (!(player ? PLAYER : ENTITY).contains(name))
                throw invalid("Unsupported state field: " + name);
            switch (name) {
                case "silent", "noGravity", "glowing", "noAi", "flying" -> {
                    if (!field.getValue().isJsonPrimitive()
                            || !field.getValue().getAsJsonPrimitive().isBoolean())
                        throw invalid(name + " must be a JSON boolean");
                }
                case "velocity" -> {
                    if (!field.getValue().isJsonObject()
                            || !field.getValue()
                                    .getAsJsonObject()
                                    .keySet()
                                    .equals(Set.of("x", "y", "z")))
                        throw invalid("velocity requires exactly x, y and z");
                    for (String axis : Set.of("x", "y", "z"))
                        number(field.getValue().getAsJsonObject().get(axis), axis, -10, 10);
                }
                case "food" -> integer(object, name, 0, 20);
                case "air" -> integer(object, name, 0, 32767);
                case "experienceLevel", "experiencePoints" -> integer(object, name, 0, 100000);
                case "saturation" -> number(field.getValue(), name, 0, 20);
                default -> number(field.getValue(), name, 0, 2048);
            }
        }
        return object.deepCopy();
    }

    private static void integer(JsonObject object, String name, int minimum, int maximum) {
        int result;
        try {
            JsonElement value = object.get(name);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            result = value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException error) {
            throw invalid(name + " must be an exact JSON integer");
        }
        if (result < minimum || result > maximum) throw invalid(name + " outside approved range");
    }

    private static void number(JsonElement value, String name, double minimum, double maximum) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid(name + " must be a JSON number");
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < minimum || result > maximum)
            throw invalid(name + " outside approved range");
    }

    public static void verifyValues(JsonObject request, JsonObject current) {
        if (!request.has("expectedValue")) return;
        // Normalize native floats to the same JSON representation the caller actually observed.
        JsonObject wire = JsonParser.parseString(current.toString()).getAsJsonObject();
        for (var field : request.getAsJsonObject("expectedValue").entrySet())
            if (!field.getValue().equals(wire.get(field.getKey())))
                throw new ProtocolState.ProtocolException(
                        "VALUE_PRECONDITION_FAILED",
                        409,
                        "State value precondition failed: " + field.getKey());
    }

    public static boolean unchanged(JsonObject value, JsonObject current) {
        JsonObject wire = JsonParser.parseString(current.toString()).getAsJsonObject();
        return value.entrySet().stream()
                .allMatch(field -> field.getValue().equals(wire.get(field.getKey())));
    }

    public static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_STATE", 400, message);
    }
}
