package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Formal deep-read selector bounds; invalid values must never become a different query. */
public final class DeepObservationSelector {
    private DeepObservationSelector() {}

    public static int chunkRadius(JsonObject request) {
        if (!request.has("selector")) return 0;
        JsonElement selector = request.get("selector");
        if (!selector.isJsonObject())
            throw new IllegalArgumentException("selector must be an object");
        JsonElement radius = selector.getAsJsonObject().get("chunkRadius");
        if (radius == null) return 0;
        try {
            if (!radius.isJsonPrimitive() || !radius.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            int value = radius.getAsBigDecimal().intValueExact();
            if (value < 0 || value > 2) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "selector.chunkRadius must be an integer from 0 to 2");
        }
    }
}
