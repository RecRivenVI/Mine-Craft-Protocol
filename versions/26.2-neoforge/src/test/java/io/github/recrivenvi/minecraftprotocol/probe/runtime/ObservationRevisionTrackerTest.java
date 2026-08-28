package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class ObservationRevisionTrackerTest {
    @Test
    void canonicalSemanticStateIgnoresJsonAndSetOrdering() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker(8);
        JsonObject first = new JsonObject();
        first.addProperty("uuid", "resource");
        JsonArray attributes = new JsonArray();
        JsonObject speed = new JsonObject();
        speed.addProperty("id", "speed");
        speed.addProperty("value", 1);
        JsonObject health = new JsonObject();
        health.addProperty("value", 20);
        health.addProperty("id", "health");
        attributes.add(speed);
        attributes.add(health);
        first.add("attributes", attributes);

        JsonObject reordered = new JsonObject();
        JsonArray reversed = new JsonArray();
        JsonObject reorderedHealth = new JsonObject();
        reorderedHealth.addProperty("id", "health");
        reorderedHealth.addProperty("value", 20);
        reversed.add(reorderedHealth);
        reversed.add(speed.deepCopy());
        reordered.add("attributes", reversed);
        reordered.addProperty("uuid", "resource");

        long one = revision(tracker, "entity", "resource", first);
        long two = revision(tracker, "entity", "resource", reordered);
        assertEquals(one, two);
    }

    @Test
    void resourceChangesAreLocalAndSerializedStateIsSeparate() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker(8);
        JsonObject player = state("value", 1);
        JsonObject entity = state("health", 20);
        JsonObject blockEntityBase = state("type", "minecraft:chest");
        long playerOne = revision(tracker, "player", "player", player);
        long entityOne = revision(tracker, "entity", "entity", entity);
        long baseOne = revision(tracker, "block_entity", "0,64,0", blockEntityBase);
        long serializedOne = revision(
                tracker, "block_entity_serialized", "0,64,0", state("Items", 0));

        long entityTwo = revision(tracker, "entity", "entity", state("health", 19));
        long playerTwo = revision(tracker, "player", "player", player);
        long baseTwo = revision(tracker, "block_entity", "0,64,0", blockEntityBase);
        long serializedTwo = revision(
                tracker, "block_entity_serialized", "0,64,0", state("Items", 1));

        assertNotEquals(entityOne, entityTwo);
        assertEquals(playerOne, playerTwo);
        assertEquals(baseOne, baseTwo);
        assertNotEquals(serializedOne, serializedTwo);
    }

    @Test
    void evictionNeverAliasesAnOldRevisionWithinSessionEpoch() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker(2);
        String epoch = tracker.sessionEpoch();
        long first = revision(tracker, "entity", "a", state("value", 1));
        revision(tracker, "entity", "b", state("value", 1));
        revision(tracker, "entity", "c", state("value", 1));
        long observedAgain = revision(tracker, "entity", "a", state("value", 1));

        assertEquals(epoch, tracker.sessionEpoch());
        assertNotEquals(first, observedAgain);
        assertTrue(observedAgain > first);
        JsonObject diagnostics = tracker.diagnostics();
        assertEquals(2, diagnostics.get("entryBound").getAsInt());
        assertTrue(diagnostics.get("evictionCount").getAsInt() >= 1);
    }

    private static JsonObject state(String name, Number value) {
        JsonObject json = new JsonObject();
        json.addProperty(name, value);
        return json;
    }

    private static JsonObject state(String name, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(name, value);
        return json;
    }

    private static long revision(
            ObservationRevisionTracker tracker, String type, String key, JsonObject state) {
        return tracker.revision(type, key, state).get("revision").getAsLong();
    }
}
