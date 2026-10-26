package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Native environment scalar values; no command parsing, arbitrary data or simulation controls. */
public final class DebugEnvironmentRequest {
    public static final long MAX_SAFE_TICKS = 9_007_199_254_740_991L;
    private static final Set<String> WEATHER =
            Set.of("clearWeatherTime", "rainTime", "thunderTime", "raining", "thundering");

    private DebugEnvironmentRequest() {}

    public static long ticks(JsonElement value) {
        return integer(value, -MAX_SAFE_TICKS, MAX_SAFE_TICKS);
    }

    public static long integer(JsonElement value, long minimum, long maximum) {
        long parsed;
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            parsed = value.getAsBigDecimal().longValueExact();
        } catch (RuntimeException failure) {
            throw invalid("Expected an exact JSON integer");
        }
        if (parsed < minimum || parsed > maximum)
            throw invalid("Integer is outside the typed native/transport range");
        return parsed;
    }

    public static JsonObject weather(JsonElement value) {
        if (value == null
                || !value.isJsonObject()
                || !value.getAsJsonObject().keySet().equals(WEATHER))
            throw invalid(
                    "Weather requires exactly clearWeatherTime, rainTime, thunderTime, raining and thundering");
        JsonObject data = value.getAsJsonObject();
        for (String key : WEATHER) {
            if (key.equals("raining") || key.equals("thundering")) {
                if (!data.get(key).isJsonPrimitive() || !data.getAsJsonPrimitive(key).isBoolean())
                    throw invalid(key + " must be a JSON boolean");
            } else integer(data.get(key), 0, Integer.MAX_VALUE);
        }
        return data.deepCopy();
    }

    public static JsonElement rule(JsonElement value) {
        if (value == null || !value.isJsonPrimitive())
            throw invalid("Game rule requires a JSON boolean or integer");
        if (!value.getAsJsonPrimitive().isBoolean())
            integer(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return value.deepCopy();
    }

    public static void expected(JsonObject request, JsonElement actual) {
        if (request.has("expectedValue") && !request.get("expectedValue").equals(actual))
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 409, "Native environment value no longer matches");
    }

    public static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_ENVIRONMENT", 400, message);
    }
}
