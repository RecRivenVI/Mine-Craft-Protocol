package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Resource-local snapshot-change sequences; never a global world transaction token. */
final class ObservationRevisionTracker {
    private final String sessionEpoch = UUID.randomUUID().toString();
    private final Map<String, State> states = new HashMap<>();

    synchronized JsonObject revision(String resourceType, String resourceKey, JsonElement value) {
        String key = resourceType + "|" + resourceKey;
        String fingerprint = fingerprint(value);
        State previous = this.states.get(key);
        long revision = previous == null ? 1L : previous.fingerprint().equals(fingerprint)
                ? previous.revision() : previous.revision() + 1L;
        this.states.put(key, new State(revision, fingerprint));
        JsonObject json = new JsonObject();
        json.addProperty("resourceType", resourceType);
        json.addProperty("resourceKey", resourceKey);
        json.addProperty("revision", revision);
        json.addProperty("revisionSource", "snapshot_change_sequence");
        return json;
    }

    String sessionEpoch() {
        return this.sessionEpoch;
    }

    private static String fingerprint(JsonElement value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record State(long revision, String fingerprint) {
    }
}
