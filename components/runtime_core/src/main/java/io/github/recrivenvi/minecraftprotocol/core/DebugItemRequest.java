package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Closed, typed replacement data; native adapters check the item's stack and damage domains. */
public record DebugItemRequest(String itemId, int count, String customName, Integer damage) {
    public static void requireResolvedContainer(JsonObject serialized) {
        if (serialized.has("LootTable"))
            throw new ProtocolState.ProtocolException(
                    "DEBUG_CONTAINER_LOOT_UNRESOLVED",
                    409,
                    "Native slot access would unpack the pending loot table; open it through normal gameplay before typed slot mutation");
    }

    public static DebugItemRequest parse(JsonObject request) {
        int count = integer(request, "count");
        if (count < 0 || count > 64) throw invalid("count must be 0 to 64");
        String id = null;
        if (count > 0) {
            JsonElement item = request.get("itemId");
            if (item == null
                    || !item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isString()
                    || item.getAsString().isBlank()
                    || item.getAsString().length() > 256)
                throw invalid("itemId must be a nonempty native item identifier");
            id = item.getAsString();
        }
        String name = null;
        Integer damage = null;
        if (request.has("itemData")) {
            JsonElement element = request.get("itemData");
            if (!element.isJsonObject() || count == 0)
                throw invalid("itemData requires a nonempty replacement");
            JsonObject data = element.getAsJsonObject();
            for (String field : data.keySet())
                if (!Set.of("customName", "damage").contains(field))
                    throw invalid("Unsupported itemData field: " + field);
            if (data.has("customName")) {
                JsonElement value = data.get("customName");
                if (!value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive().isString()
                        || value.getAsString().length() > 128)
                    throw invalid(
                            "itemData.customName must be a literal string of at most 128 characters");
                name = value.getAsString();
            }
            if (data.has("damage")) {
                damage = integer(data, "damage");
                if (damage < 0) throw invalid("itemData.damage must be nonnegative");
            }
        }
        return new DebugItemRequest(id, count, name, damage);
    }

    public static int integer(JsonObject request, String field) {
        JsonElement value = request.get(field);
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            return value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException invalid) {
            throw invalid(field + " must be an exact JSON integer");
        }
    }

    private static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_ITEM", 400, message);
    }
}
