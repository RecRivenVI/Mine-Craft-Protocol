package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservationRevisionTrackerTest {
    @Test
    void objectKeyOrderIsNotSemantic() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject first = new JsonObject();
        first.addProperty("a", 1);
        first.addProperty("b", 2);
        JsonObject second = new JsonObject();
        second.addProperty("b", 2);
        second.addProperty("a", 1);
        assertEquals(revision(tracker, first), revision(tracker, second));
    }

    @Test
    void arraysAreOrderedIncludingNestedAndDuplicateSequences() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        long ordered = revision(tracker, array("a", "b"));
        long reversed = revision(tracker, array("b", "a"));
        assertNotEquals(ordered, reversed);

        JsonObject pathOne = new JsonObject();
        pathOne.add("path", array(1, 2, 3));
        JsonObject pathTwo = new JsonObject();
        pathTwo.add("path", array(3, 2, 1));
        assertNotEquals(revision(tracker, "nested", pathOne), revision(tracker, "nested", pathTwo));

        assertNotEquals(
                revision(tracker, "duplicates", array("a", "a", "b")),
                revision(tracker, "duplicates", array("a", "b", "a")));
    }

    @Test
    void nbtCompoundKeysAreUnorderedButListTagsRemainOrdered() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject compoundOne = new JsonObject();
        compoundOne.addProperty("id", "test");
        compoundOne.add("Items", array("first", "second"));
        JsonObject compoundTwo = new JsonObject();
        compoundTwo.add("Items", array("first", "second"));
        compoundTwo.addProperty("id", "test");
        assertEquals(
                revision(tracker, "nbt", compoundOne),
                revision(tracker, "nbt", compoundTwo));

        JsonObject reorderedList = new JsonObject();
        reorderedList.addProperty("id", "test");
        reorderedList.add("Items", array("second", "first"));
        assertNotEquals(
                revision(tracker, "nbt", compoundTwo),
                revision(tracker, "nbt", reorderedList));
    }

    @Test
    void domainNormalizedUnorderedAttributesAndEffectsRemainStable() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonArray first = new JsonArray();
        first.add(keyed("minecraft:generic.max_health", 20));
        first.add(keyed("minecraft:generic.movement_speed", 1));
        JsonArray reversed = new JsonArray();
        reversed.add(keyed("minecraft:generic.movement_speed", 1));
        reversed.add(keyed("minecraft:generic.max_health", 20));

        assertEquals(
                revision(tracker, "attributes", normalizeById(first)),
                revision(tracker, "attributes", normalizeById(reversed)));
        assertEquals(
                revision(tracker, "effects", normalizeById(first)),
                revision(tracker, "effects", normalizeById(reversed)));
    }

    @Test
    void inventorySlotOrderAndContentsAreSemantic() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonArray first = new JsonArray();
        first.add(slot(0, "minecraft:stone"));
        first.add(slot(1, "minecraft:dirt"));
        JsonArray swapped = new JsonArray();
        swapped.add(slot(0, "minecraft:dirt"));
        swapped.add(slot(1, "minecraft:stone"));
        assertNotEquals(
                revision(tracker, "inventory", first),
                revision(tracker, "inventory", swapped));
    }

    @Test
    void blockEntityBaseAndSerializedStateHaveSeparateOrderedSemantics() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject base = new JsonObject();
        base.addProperty("type", "minecraft:chest");
        long baseRevision = revision(tracker, "block_entity", base);
        JsonObject serialized = new JsonObject();
        serialized.add("Items", array("a", "b"));
        long serializedRevision = revision(tracker, "block_entity_serialized", serialized);
        JsonObject reordered = new JsonObject();
        reordered.add("Items", array("b", "a"));

        assertEquals(baseRevision, revision(tracker, "block_entity", base));
        assertNotEquals(
                serializedRevision,
                revision(tracker, "block_entity_serialized", reordered));
    }

    @Test
    void evictionNeverAliasesOldRevisionWithinSessionEpoch() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker(2);
        String epoch = tracker.sessionEpoch();
        long first = revision(tracker, "entity-a", value(1));
        revision(tracker, "entity-b", value(1));
        revision(tracker, "entity-c", value(1));
        long observedAgain = revision(tracker, "entity-a", value(1));

        assertEquals(epoch, tracker.sessionEpoch());
        assertNotEquals(first, observedAgain);
        assertTrue(observedAgain > first);
        assertTrue(tracker.diagnostics().get("evictionCount").getAsInt() >= 1);
    }

    @Test
    void resourceVersionIsEpochAndLifecycleBound() {
        ObservationRevisionTracker sessionA = new ObservationRevisionTracker();
        ObservationRevisionTracker sessionB = new ObservationRevisionTracker();
        JsonObject state = value(1);
        JsonObject a = sessionA.revision("entity", "uuid", "entity:uuid@1", state);
        JsonObject b = sessionB.revision("entity", "uuid", "entity:uuid@1", state);
        assertEquals(a.get("revision").getAsLong(), b.get("revision").getAsLong());
        assertNotEquals(a.get("sessionEpoch").getAsString(), b.get("sessionEpoch").getAsString());
        assertCode("STALE_SESSION_EPOCH", () ->
                ResourceVersionVerifier.verify(ResourceVersionVerifier.token(a), b));

        ObservationLifecycleTracker lifecycles = new ObservationLifecycleTracker(8);
        String firstLifecycle = lifecycles.lifecycleId("entity", "uuid", new Object());
        String secondLifecycle = lifecycles.lifecycleId("entity", "uuid", new Object());
        JsonObject first = sessionA.revision("entity", "uuid", firstLifecycle, state);
        JsonObject recreated = sessionA.revision("entity", "uuid", secondLifecycle, state);
        assertNotEquals(first.get("revision").getAsLong(), recreated.get("revision").getAsLong());
        assertCode("RESOURCE_MISMATCH", () ->
                ResourceVersionVerifier.verify(ResourceVersionVerifier.token(first), recreated));
    }

    @Test
    void menuBlockEntityAndChunkLifecycleReuseInvalidateOldTokens() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        ObservationLifecycleTracker lifecycles = new ObservationLifecycleTracker(8);
        for (String type : List.of("menu", "block_entity", "chunk")) {
            String key = type + "-key";
            JsonObject first = tracker.revision(
                    type, key, lifecycles.lifecycleId(type, key, new Object()), value(1));
            JsonObject replacement = tracker.revision(
                    type, key, lifecycles.lifecycleId(type, key, new Object()), value(1));
            assertCode("RESOURCE_MISMATCH", () ->
                    ResourceVersionVerifier.verify(ResourceVersionVerifier.token(first), replacement));
        }
    }

    @Test
    void verifierRejectsWrongIdentityAndStaleRevisionAndAcceptsCurrent() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject current = tracker.revision("block", "dim@1,2,3", "block:lifecycle", value(1));
        JsonObject correct = ResourceVersionVerifier.token(current);
        assertEquals("passed", ResourceVersionVerifier.verify(correct, current).get("status").getAsString());

        JsonObject wrongKey = correct.deepCopy();
        wrongKey.addProperty("resourceKey", "dim@4,5,6");
        assertCode("RESOURCE_MISMATCH", () -> ResourceVersionVerifier.verify(wrongKey, current));

        JsonObject wrongType = correct.deepCopy();
        wrongType.addProperty("resourceType", "entity");
        assertCode("RESOURCE_MISMATCH", () -> ResourceVersionVerifier.verify(wrongType, current));

        JsonObject stale = correct.deepCopy();
        stale.addProperty("revision", correct.get("revision").getAsLong() - 1L);
        assertCode("STALE_RESOURCE_REVISION", () -> ResourceVersionVerifier.verify(stale, current));

        JsonObject queryView = tracker.queryViewRevision(
                "provider", "test:provider", "provider:lifecycle", "query", value(1));
        assertCode("RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE", () ->
                ResourceVersionVerifier.verify(ResourceVersionVerifier.token(queryView), queryView));
    }

    private static JsonArray normalizeById(JsonArray input) {
        List<JsonElement> values = new ArrayList<>();
        input.forEach(value -> values.add(value.deepCopy()));
        values.sort(Comparator.comparing(value ->
                value.getAsJsonObject().get("id").getAsString()));
        JsonArray normalized = new JsonArray();
        values.forEach(normalized::add);
        return normalized;
    }

    private static JsonObject keyed(String id, Number value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("value", value);
        return json;
    }

    private static JsonObject slot(int slot, String item) {
        JsonObject json = new JsonObject();
        json.addProperty("slot", slot);
        json.addProperty("id", item);
        return json;
    }

    private static JsonObject value(Number value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    private static JsonArray array(Object... values) {
        JsonArray json = new JsonArray();
        for (Object value : values) {
            if (value instanceof Number number) json.add(number);
            else json.add(String.valueOf(value));
        }
        return json;
    }

    private static long revision(ObservationRevisionTracker tracker, JsonElement state) {
        return revision(tracker, "resource", state);
    }

    private static long revision(
            ObservationRevisionTracker tracker, String key, JsonElement state) {
        return tracker.revision("test", key, "test:" + key + "@lifecycle", state)
                .get("revision").getAsLong();
    }

    private static void assertCode(String code, Runnable action) {
        ProtocolState.ProtocolException failure = assertThrows(
                ProtocolState.ProtocolException.class, action::run);
        assertEquals(code, failure.code());
    }
}
