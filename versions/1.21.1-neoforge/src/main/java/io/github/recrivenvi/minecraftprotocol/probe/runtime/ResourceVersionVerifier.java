package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import java.util.List;

/** Common verifier for future typed mutation preconditions. It performs no mutation. */
final class ResourceVersionVerifier {
    private static final List<String> TOKEN_FIELDS = List.of(
            "sessionEpoch", "resourceType", "resourceKey", "lifecycleId",
            "revision", "revisionSource", "revisionScope", "mutationPreconditionEligible");

    private ResourceVersionVerifier() {
    }

    static JsonObject token(JsonObject revisionRef) {
        JsonObject token = new JsonObject();
        for (String field : TOKEN_FIELDS) {
            if (!revisionRef.has(field)) {
                throw new ProtocolState.ProtocolException(
                        "INVALID_RESOURCE_VERSION", 400, "Missing resource version field: " + field);
            }
            token.add(field, revisionRef.get(field).deepCopy());
        }
        return token;
    }

    static JsonObject verify(JsonObject expected, JsonObject current) {
        requireTokenFields(expected);
        requireTokenFields(current);
        if (!expected.get("mutationPreconditionEligible").getAsBoolean()) {
            throw new ProtocolState.ProtocolException(
                    "RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE", 409,
                    "The supplied resource version is scoped to a query view");
        }
        if (!string(expected, "sessionEpoch").equals(string(current, "sessionEpoch"))) {
            throw new ProtocolState.ProtocolException(
                    "STALE_SESSION_EPOCH", 409, "Resource version belongs to another Runtime session");
        }
        for (String field : List.of(
                "resourceType", "resourceKey", "lifecycleId", "revisionSource", "revisionScope")) {
            if (!string(expected, field).equals(string(current, field))) {
                throw new ProtocolState.ProtocolException(
                        "RESOURCE_MISMATCH", 409, "Resource version mismatch: " + field);
            }
        }
        if (expected.get("revision").getAsLong() != current.get("revision").getAsLong()) {
            throw new ProtocolState.ProtocolException(
                    "STALE_RESOURCE_REVISION", 409, "Resource revision no longer matches");
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "resource.version.precondition");
        result.addProperty("status", "passed");
        result.add("resourceVersion", token(current));
        return result;
    }

    private static String string(JsonObject value, String field) {
        if (!value.has(field) || !value.get(field).isJsonPrimitive()) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_RESOURCE_VERSION", 400, "Missing resource version field: " + field);
        }
        return value.get(field).getAsString();
    }

    private static void requireTokenFields(JsonObject value) {
        for (String field : TOKEN_FIELDS) {
            if (!value.has(field) || !value.get(field).isJsonPrimitive()) {
                throw new ProtocolState.ProtocolException(
                        "INVALID_RESOURCE_VERSION", 400,
                        "Missing resource version field: " + field);
            }
        }
    }
}
