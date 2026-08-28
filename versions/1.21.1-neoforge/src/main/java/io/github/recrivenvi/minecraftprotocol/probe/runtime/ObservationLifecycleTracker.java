package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bounded session-local identity generations for live Minecraft object lifecycles. */
final class ObservationLifecycleTracker {
    static final int DEFAULT_MAX_ENTRIES = 4_096;

    private final int maxEntries;
    private final Map<Object, Long> identities = new IdentityHashMap<>();
    private long nextGeneration;
    private long resetCount;

    ObservationLifecycleTracker() {
        this(DEFAULT_MAX_ENTRIES);
    }

    ObservationLifecycleTracker(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    synchronized String lifecycleId(String resourceType, String semanticKey, Object liveObject) {
        if (liveObject == null) return resourceType + ":" + semanticKey + "@absent";
        Long generation = this.identities.get(liveObject);
        if (generation == null) {
            if (this.identities.size() >= this.maxEntries) {
                this.identities.clear();
                this.resetCount++;
            }
            generation = ++this.nextGeneration;
            this.identities.put(liveObject, generation);
        }
        return resourceType + ":" + semanticKey + "@" + generation;
    }

    synchronized JsonObject diagnostics() {
        JsonObject json = new JsonObject();
        json.addProperty("entryCount", this.identities.size());
        json.addProperty("entryBound", this.maxEntries);
        json.addProperty("generationHighWatermark", this.nextGeneration);
        json.addProperty("boundedMapResets", this.resetCount);
        json.addProperty("resetBehavior", "allocates_new_monotonic_lifecycle_generation");
        return json;
    }
}

