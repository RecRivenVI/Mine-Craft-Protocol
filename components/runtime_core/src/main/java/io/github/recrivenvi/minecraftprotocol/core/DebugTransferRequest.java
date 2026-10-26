package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicit LIVE destinations and loaded entity identity selectors, never a load request. */
public final class DebugTransferRequest {
    public static final int MAX_ENTITY_TARGETS = 128;

    private DebugTransferRequest() {}

    public record Destination(
            String dimension, double x, double y, double z, Float yaw, Float pitch) {}

    public record Target(String dimension, UUID entityUuid) {}

    public static Destination destination(JsonObject request) {
        JsonElement value = request.get("value");
        if (value == null || !value.isJsonObject())
            throw invalid("value must be a typed LIVE destination");
        JsonObject object = value.getAsJsonObject();
        for (String key : object.keySet())
            if (!Set.of("dimension", "position", "yaw", "pitch").contains(key))
                throw invalid("Unsupported transfer field: " + key);
        JsonElement at = object.get("position");
        if (at == null
                || !at.isJsonObject()
                || !at.getAsJsonObject().keySet().equals(Set.of("x", "y", "z")))
            throw invalid("position requires exactly x, y and z");
        JsonObject position = at.getAsJsonObject();
        return new Destination(
                dimension(object, "dimension"),
                number(position, "x", -29999984, 29999983),
                number(position, "y", -19999999, 19999999),
                number(position, "z", -29999984, 29999983),
                object.has("yaw") ? (float) number(object, "yaw", -180, 180) : null,
                object.has("pitch") ? (float) number(object, "pitch", -90, 90) : null);
    }

    public static List<Target> targets(JsonElement value) {
        try {
            return selectedTargets(value);
        } catch (ProtocolState.ProtocolException error) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEEP_SELECTOR", 400, error.getMessage());
        }
    }

    private static List<Target> selectedTargets(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw invalid("entityTargets must be an array");
        JsonArray array = value.getAsJsonArray();
        if (array.isEmpty() || array.size() > MAX_ENTITY_TARGETS)
            throw invalid("entityTargets must contain 1 to " + MAX_ENTITY_TARGETS + " identities");
        List<Target> result = new ArrayList<>();
        Set<Target> unique = new HashSet<>();
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()
                    || !entry.getAsJsonObject().keySet().equals(Set.of("dimension", "entityUuid")))
                throw invalid("Each entity target requires exactly dimension and entityUuid");
            JsonObject object = entry.getAsJsonObject();
            JsonElement uuid = object.get("entityUuid");
            UUID parsed;
            try {
                if (!uuid.isJsonPrimitive() || !uuid.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException();
                parsed = UUID.fromString(uuid.getAsString());
                if (!parsed.toString().equalsIgnoreCase(uuid.getAsString()))
                    throw new IllegalArgumentException();
            } catch (RuntimeException error) {
                throw invalid("entityUuid must be a complete UUID");
            }
            Target target = new Target(dimension(object, "dimension"), parsed);
            if (!unique.add(target)) throw invalid("Duplicate entity target");
            result.add(target);
        }
        return List.copyOf(result);
    }

    public static String dimension(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > 256
                || !value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw invalid(key + " must be an explicit dimension identifier");
        return value.getAsString();
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
        return new ProtocolState.ProtocolException("INVALID_DEBUG_TRANSFER", 400, message);
    }
}
