package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.List;

/** Epoch/lifecycle-bound typed mutation preconditions; no mutation or automatic retry. */
public final class ResourceVersionVerifier {
    private static final List<String> TOKEN_FIELDS =
            List.of(
                    "sessionEpoch",
                    "resourceType",
                    "resourceKey",
                    "lifecycleId",
                    "revision",
                    "revisionSource",
                    "revisionScope",
                    "mutationPreconditionEligible");

    private ResourceVersionVerifier() {}

    public static JsonObject token(JsonObject revisionRef) {
        requireTokenFields(revisionRef);
        JsonObject token = new JsonObject();
        for (String field : TOKEN_FIELDS) {
            token.add(field, revisionRef.get(field).deepCopy());
        }
        return token;
    }

    public static JsonObject verify(JsonObject expected, JsonObject current) {
        return verify(expected, current, false);
    }

    /** Only after complete principal/scope/Arm/world authorization on the resource owner thread. */
    public static JsonObject verifyAuthorized(JsonObject expected, JsonObject current) {
        return verify(expected, current, true);
    }

    private static JsonObject verify(JsonObject expected, JsonObject current, boolean authorized) {
        requireTokenFields(expected);
        requireTokenFields(current);
        if (!eligible(expected) || !eligible(current)) {
            throw new ProtocolState.ProtocolException(
                    "RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE",
                    409,
                    "Both supplied and current resource versions must be mutation eligible");
        }
        if (!string(expected, "sessionEpoch").equals(string(current, "sessionEpoch"))) {
            throw new ProtocolState.ProtocolException(
                    "STALE_SESSION_EPOCH",
                    409,
                    "Resource version belongs to another Runtime session");
        }
        for (String field :
                List.of(
                        "resourceType",
                        "resourceKey",
                        "lifecycleId",
                        "revisionSource",
                        "revisionScope")) {
            if (!string(expected, field).equals(string(current, field))) {
                throw new ProtocolState.ProtocolException(
                        "RESOURCE_MISMATCH", 409, "Resource version mismatch: " + field);
            }
        }
        if (expected.get("revision").getAsLong() != current.get("revision").getAsLong()) {
            JsonObject details = new JsonObject();
            if (authorized) {
                details.addProperty("expectedRevision", expected.get("revision").getAsLong());
                details.addProperty("actualRevision", current.get("revision").getAsLong());
                details.addProperty("reinspectRequired", true);
                details.addProperty("retrySafe", false);
            }
            throw new ProtocolState.ProtocolException(
                    "STALE_RESOURCE_REVISION", 409, "Resource revision no longer matches", details);
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "resource.version.precondition");
        result.addProperty("status", "passed");
        result.add("resourceVersion", token(current));
        return result;
    }

    /** Publish only counters after the authorized verifier has matched resource identity. */
    public static JsonObject staleMetadata(String code, JsonObject details) {
        JsonObject result = new JsonObject();
        if (!"STALE_RESOURCE_REVISION".equals(code) || details == null) return result;
        Long expected = counter(details, "expectedRevision");
        Long actual = counter(details, "actualRevision");
        if (expected == null || actual == null || expected.equals(actual)) return result;
        result.addProperty("expectedRevision", expected);
        result.addProperty("actualRevision", actual);
        result.addProperty("reinspectRequired", true);
        result.addProperty("retrySafe", false);
        return result;
    }

    private static boolean eligible(JsonObject value) {
        return value.get("mutationPreconditionEligible").getAsBoolean()
                && "resource".equals(string(value, "revisionScope"));
    }

    private static String string(JsonObject value, String field) {
        return value.get(field).getAsString();
    }

    private static void requireTokenFields(JsonObject value) {
        if (value == null) throw invalid("resourceVersion");
        for (String field : TOKEN_FIELDS) {
            if (!value.has(field) || !value.get(field).isJsonPrimitive()) {
                throw invalid(field);
            }
            if (field.equals("revision")) {
                if (counter(value, field) == null) throw invalid(field);
            } else if (field.equals("mutationPreconditionEligible")) {
                if (!value.getAsJsonPrimitive(field).isBoolean()) throw invalid(field);
            } else if (!value.getAsJsonPrimitive(field).isString()
                    || value.get(field).getAsString().isBlank()) throw invalid(field);
        }
    }

    private static Long counter(JsonObject value, String field) {
        if (!value.has(field)
                || !value.get(field).isJsonPrimitive()
                || !value.getAsJsonPrimitive(field).isNumber()) return null;
        try {
            long result = new BigDecimal(value.get(field).getAsString()).longValueExact();
            return result >= 0L ? result : null;
        } catch (ArithmeticException | NumberFormatException invalid) {
            return null;
        }
    }

    private static ProtocolState.ProtocolException invalid(String field) {
        return new ProtocolState.ProtocolException(
                "INVALID_RESOURCE_VERSION", 400, "Invalid resource version field: " + field);
    }
}
