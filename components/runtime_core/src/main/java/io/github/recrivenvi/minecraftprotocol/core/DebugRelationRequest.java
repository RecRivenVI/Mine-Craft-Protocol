package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;

/** Closed relationship values; references never grant either participant's authority. */
public final class DebugRelationRequest {
    private DebugRelationRequest() {}

    public static String uuid(JsonElement value) {
        if (value == null) throw invalid("An explicit UUID or null value is required");
        if (value.isJsonNull()) return null;
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException();
            String text = value.getAsString();
            String canonical = UUID.fromString(text).toString();
            if (!canonical.equalsIgnoreCase(text)) throw new IllegalArgumentException();
            return canonical;
        } catch (RuntimeException failure) {
            throw invalid("Expected a complete entity UUID or explicit null");
        }
    }

    public static JsonObject owner(JsonElement value) {
        if (value == null
                || !value.isJsonObject()
                || !value.getAsJsonObject().keySet().equals(Set.of("ownerUuid", "tamed")))
            throw invalid("Owner value requires exactly ownerUuid and tamed");
        JsonObject result = value.getAsJsonObject();
        String owner = uuid(result.get("ownerUuid"));
        if (!result.get("tamed").isJsonPrimitive()
                || !result.getAsJsonPrimitive("tamed").isBoolean())
            throw invalid("tamed must be a JSON boolean");
        if (owner == null && result.get("tamed").getAsBoolean())
            throw invalid("A tamed ownership relationship requires an owner");
        return result.deepCopy();
    }

    public static void expected(JsonObject request, JsonElement current) {
        if (request.has("expectedValue") && !request.get("expectedValue").equals(current))
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 409, "Native relationship no longer matches");
    }

    public static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_RELATION", 400, message);
    }
}
