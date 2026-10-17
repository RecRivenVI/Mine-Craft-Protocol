package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded typed property maps; native adapters still validate each property's actual domain. */
public final class DebugBlockStateRequest {
    private DebugBlockStateRequest() {}

    public static Map<String, String> properties(JsonObject request, String field) {
        if (!request.has(field)) return Map.of();
        JsonElement element = request.get(field);
        if (!element.isJsonObject() || element.getAsJsonObject().size() > 64) {
            throw invalid(field);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!identifier(entry.getKey())
                    || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()
                    || !identifier(value.getAsString())) {
                throw invalid(field);
            }
            result.put(entry.getKey(), value.getAsString());
        }
        return Map.copyOf(result);
    }

    public static void verifyExpected(JsonObject request, JsonObject actual) {
        for (var entry : properties(request, "expectedProperties").entrySet()) {
            if (!actual.has(entry.getKey())
                    || !entry.getValue().equals(actual.get(entry.getKey()).getAsString())) {
                throw new ProtocolState.ProtocolException(
                        "VALUE_PRECONDITION_FAILED",
                        409,
                        "Block property precondition failed: " + entry.getKey());
            }
        }
    }

    private static boolean identifier(String value) {
        return !value.isEmpty() && value.length() <= 64 && value.matches("[a-z0-9_]+");
    }

    private static ProtocolState.ProtocolException invalid(String field) {
        return new ProtocolState.ProtocolException(
                "INVALID_DEBUG_BLOCK_STATE",
                400,
                field
                        + " requires at most 64 native property names and string values (1-64 lowercase letters, digits or underscores)");
    }
}
