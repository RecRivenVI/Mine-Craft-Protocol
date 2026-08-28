package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded resource-local state generations; never a global world transaction token. */
final class ObservationRevisionTracker {
    static final int DEFAULT_MAX_ENTRIES = 4_096;

    private final String sessionEpoch = UUID.randomUUID().toString();
    private final int maxEntries;
    private final Map<String, State> states;
    private long nextGeneration;
    private long evictionCount;

    ObservationRevisionTracker() {
        this(DEFAULT_MAX_ENTRIES);
    }

    ObservationRevisionTracker(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
        this.states = new LinkedHashMap<>(Math.min(maxEntries, 256), 0.75F, true);
    }

    synchronized JsonObject revision(String resourceType, String resourceKey, JsonElement canonicalState) {
        String key = resourceType + "|" + resourceKey;
        String fingerprint = fingerprint(canonicalState);
        State previous = this.states.get(key);
        long revision = previous != null && previous.fingerprint().equals(fingerprint)
                ? previous.revision()
                : ++this.nextGeneration;
        this.states.put(key, new State(revision, fingerprint));
        this.evictIfNeeded();
        return reference(resourceType, resourceKey, revision, "snapshot_change_sequence");
    }

    synchronized JsonObject nativeRevision(
            String resourceType, String resourceKey, long providerRevision, String revisionSource) {
        if (providerRevision < 0L) throw new IllegalArgumentException("providerRevision must be non-negative");
        return reference(resourceType, resourceKey, providerRevision, revisionSource);
    }

    synchronized JsonObject diagnostics() {
        JsonObject json = new JsonObject();
        json.addProperty("sessionEpoch", this.sessionEpoch);
        json.addProperty("entryCount", this.states.size());
        json.addProperty("entryBound", this.maxEntries);
        json.addProperty("evictionCount", this.evictionCount);
        json.addProperty("generationHighWatermark", this.nextGeneration);
        json.addProperty("evictedResourceReobservation", "allocates_new_session_unique_generation");
        return json;
    }

    String sessionEpoch() {
        return this.sessionEpoch;
    }

    private void evictIfNeeded() {
        while (this.states.size() > this.maxEntries) {
            String eldest = this.states.keySet().iterator().next();
            this.states.remove(eldest);
            this.evictionCount++;
        }
    }

    private static JsonObject reference(
            String resourceType, String resourceKey, long revision, String revisionSource) {
        JsonObject json = new JsonObject();
        json.addProperty("resourceType", resourceType);
        json.addProperty("resourceKey", resourceKey);
        json.addProperty("revision", revision);
        json.addProperty("revisionSource", revisionSource);
        return json;
    }

    static JsonElement canonicalize(JsonElement value) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
            if (primitive.isNumber()) return new JsonPrimitive(primitive.getAsNumber());
            return new JsonPrimitive(primitive.getAsString());
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted()
                    .forEach(key -> result.add(key, canonicalize(value.getAsJsonObject().get(key))));
            return result;
        }
        List<JsonElement> values = new ArrayList<>();
        value.getAsJsonArray().forEach(element -> values.add(canonicalize(element)));
        values.sort(Comparator.comparing(JsonElement::toString));
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static String fingerprint(JsonElement value) {
        try {
            String canonical = canonicalize(value).toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record State(long revision, String fingerprint) {
    }
}
