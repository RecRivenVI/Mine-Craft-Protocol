package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;

/** Completeness of the bounded native queued-tick projection, not the entire scheduler. */
public final class ScheduledTickRead {
    public static final int MAX_PER_CHUNK = 64;

    private ScheduledTickRead() {}

    public static JsonObject metadata(int containerCount, long queuedCount, int returnedCount) {
        JsonObject value = new JsonObject();
        value.addProperty("limit", MAX_PER_CHUNK);
        value.addProperty("returnedCount", returnedCount);
        value.addProperty("queuedCount", queuedCount);
        value.addProperty("containerCount", containerCount);
        value.addProperty("pendingUnpackedCount", containerCount - queuedCount);
        value.addProperty("truncated", queuedCount > returnedCount);
        value.addProperty("coverage", "loaded_container_queued_only");
        value.addProperty("ordering", "native_container_iteration");
        value.addProperty("includesCollectedToRun", false);
        value.addProperty("readEffects", "none");
        value.addProperty("typedWritable", false);
        return value;
    }
}
