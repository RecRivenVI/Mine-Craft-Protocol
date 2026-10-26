package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceVersionInspectionTest {
    @Test
    void productionTrackerTokensSupportInspectMutateReinspectWithoutGuessingRevision() {
        for (String type :
                List.of("player", "entity", "block", "menu", "block_entity_serialized")) {
            ObservationRevisionTracker tracker = new ObservationRevisionTracker();
            JsonObject inspected = tracker.revision(type, "resource", "instance-a", state(1));
            JsonObject expected = ResourceVersionVerifier.token(inspected);
            assertEquals(
                    "passed",
                    ResourceVersionVerifier.verify(expected, inspected)
                            .get("status")
                            .getAsString());
            JsonObject changed = tracker.revision(type, "resource", "instance-a", state(2));
            ProtocolState.ProtocolException stale =
                    error(
                            "STALE_RESOURCE_REVISION",
                            () -> ResourceVersionVerifier.verifyAuthorized(expected, changed));
            JsonObject metadata =
                    ResourceVersionVerifier.staleMetadata(stale.code(), stale.details());
            assertEquals(changed.get("revision"), metadata.get("actualRevision"));
            assertEquals(expected.get("revision"), metadata.get("expectedRevision"));
            assertTrue(metadata.get("reinspectRequired").getAsBoolean());
            assertFalse(metadata.get("retrySafe").getAsBoolean());
            assertEquals(
                    Set.of("actualRevision", "expectedRevision", "reinspectRequired", "retrySafe"),
                    metadata.keySet());
            JsonObject reinspected = tracker.revision(type, "resource", "instance-a", state(2));
            assertEquals(
                    "passed",
                    ResourceVersionVerifier.verify(
                                    ResourceVersionVerifier.token(reinspected), changed)
                            .get("status")
                            .getAsString());
            assertNotEquals(expected.get("revision"), reinspected.get("revision"));
        }
    }

    @Test
    void revisionMetadataIsNeverExposedBeforeEpochAndIdentityMatch() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject current = tracker.revision("block", "world@1,2,3", "block-instance", state(1));
        JsonObject stale = ResourceVersionVerifier.token(current);
        stale.addProperty("revision", 0);
        for (String field :
                List.of(
                        "sessionEpoch",
                        "resourceType",
                        "resourceKey",
                        "lifecycleId",
                        "revisionSource",
                        "revisionScope")) {
            JsonObject mismatch = stale.deepCopy();
            mismatch.addProperty(field, "other");
            String code =
                    field.equals("sessionEpoch")
                            ? "STALE_SESSION_EPOCH"
                            : field.equals("revisionScope")
                                    ? "RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE"
                                    : "RESOURCE_MISMATCH";
            ProtocolState.ProtocolException denied =
                    error(code, () -> ResourceVersionVerifier.verifyAuthorized(mismatch, current));
            assertEquals(0, denied.details().size());
            assertEquals(
                    0,
                    ResourceVersionVerifier.staleMetadata(denied.code(), denied.details()).size());
        }
    }

    @Test
    void ordinaryPreauthorizationVerifierDoesNotPublishRevisionMetadata() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject previous = tracker.revision("provider", "owner", "life", state(1));
        JsonObject current = tracker.revision("provider", "owner", "life", state(2));
        ProtocolState.ProtocolException stale =
                error(
                        "STALE_RESOURCE_REVISION",
                        () -> ResourceVersionVerifier.verify(previous, current));
        assertEquals(0, stale.details().size());
    }

    @Test
    void currentReferenceMustBeMutationEligibleEvenWhenClientClaimsItIs() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject query =
                tracker.queryViewRevision("provider", "owner", "life", "projection", state(1));
        JsonObject forged = ResourceVersionVerifier.token(query);
        forged.addProperty("mutationPreconditionEligible", true);
        forged.addProperty("revisionScope", "resource");
        ProtocolState.ProtocolException denied =
                error(
                        "RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE",
                        () -> ResourceVersionVerifier.verify(forged, query));
        assertEquals(0, denied.details().size());
    }

    @Test
    void malformedTokenScalarsCannotBeCoercedIntoValidConditions() {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject current = tracker.revision("block", "world@1,2,3", "block-instance", state(1));
        for (var malformed :
                List.of(
                        new JsonPrimitive("1"),
                        new JsonPrimitive(1.5),
                        new JsonPrimitive(-1),
                        new JsonPrimitive("NaN"),
                        new JsonPrimitive(true),
                        new JsonPrimitive(new java.math.BigInteger("9223372036854775808")))) {
            JsonObject expected = current.deepCopy();
            expected.add("revision", malformed);
            error(
                    "INVALID_RESOURCE_VERSION",
                    () -> ResourceVersionVerifier.verify(expected, current));
            error("INVALID_RESOURCE_VERSION", () -> ResourceVersionVerifier.token(expected));
        }
        JsonObject coercedBoolean = current.deepCopy();
        coercedBoolean.addProperty("mutationPreconditionEligible", "true");
        error(
                "INVALID_RESOURCE_VERSION",
                () -> ResourceVersionVerifier.verify(coercedBoolean, current));
        JsonObject numericKey = current.deepCopy();
        numericKey.addProperty("resourceKey", 1);
        error(
                "INVALID_RESOURCE_VERSION",
                () -> ResourceVersionVerifier.verify(numericKey, current));
        error("INVALID_RESOURCE_VERSION", () -> ResourceVersionVerifier.verify(current, null));
    }

    @Test
    void staleMetadataProjectionRejectsOtherErrorsAndPrivateFields() {
        JsonObject details = new JsonObject();
        details.addProperty("expectedRevision", 1);
        details.addProperty("actualRevision", 2);
        details.addProperty("rawNbt", "secret");
        details.addProperty("retrySafe", true);
        assertFalse(
                ResourceVersionVerifier.staleMetadata("STALE_RESOURCE_REVISION", details)
                        .has("rawNbt"));
        assertFalse(
                ResourceVersionVerifier.staleMetadata("STALE_RESOURCE_REVISION", details)
                        .get("retrySafe")
                        .getAsBoolean());
        for (String code :
                List.of(
                        "SCOPE_REQUIRED",
                        "RESOURCE_MISMATCH",
                        "STALE_SESSION_EPOCH",
                        "VALUE_PRECONDITION_FAILED"))
            assertEquals(0, ResourceVersionVerifier.staleMetadata(code, details).size());
        details.addProperty("actualRevision", "2");
        assertEquals(
                0,
                ResourceVersionVerifier.staleMetadata("STALE_RESOURCE_REVISION", details).size());
        details.addProperty("actualRevision", 1);
        assertEquals(
                0,
                ResourceVersionVerifier.staleMetadata("STALE_RESOURCE_REVISION", details).size());
    }

    @Test
    void publishedInspectionContractUsesExistingReadSurfaceAndIndependentMutationAuthority() {
        JsonObject contract = WorldDebugInspection.contract();
        assertEquals("/v0/observe/deep", contract.get("path").getAsString());
        assertEquals("resourceRevisionRefs", contract.get("resourceVersionsField").getAsString());
        assertFalse(contract.get("readWriteAtomic").getAsBoolean());
        assertFalse(contract.get("automaticRetry").getAsBoolean());
        assertFalse(contract.get("grantsMutationPermission").getAsBoolean());
        assertTrue(contract.get("requiresMutationPreconditionEligible").getAsBoolean());
        assertEquals(27, contract.getAsJsonArray("operations").size());
        for (var entry : contract.getAsJsonArray("operations")) {
            JsonObject descriptor = entry.getAsJsonObject();
            JsonObject query = descriptor.getAsJsonObject("readRequest");
            assertEquals("DEBUG", query.get("category").getAsString());
            assertEquals("server_authoritative", query.get("perspective").getAsString());
            assertEquals(
                    descriptor.get("resourceType").getAsString().equals("entity_relationship")
                            ? 2
                            : 1,
                    query.getAsJsonArray("domains").size());
            assertTrue(descriptor.get("requiredMutationScope").getAsString().startsWith("debug."));
            if (descriptor.get("operation").getAsString().startsWith("block_entity.")) {
                assertTrue(query.get("includeSerializedBlockEntities").getAsBoolean());
                assertEquals(
                        "block_entity_serialized", descriptor.get("resourceType").getAsString());
            }
        }
        contract.remove("operations");
        assertEquals(27, WorldDebugInspection.contract().getAsJsonArray("operations").size());
    }

    private static JsonObject state(int value) {
        JsonObject result = new JsonObject();
        result.addProperty("value", value);
        return result;
    }

    private static ProtocolState.ProtocolException error(String code, Runnable call) {
        ProtocolState.ProtocolException failure =
                assertThrows(ProtocolState.ProtocolException.class, call::run);
        assertEquals(code, failure.code());
        return failure;
    }
}
