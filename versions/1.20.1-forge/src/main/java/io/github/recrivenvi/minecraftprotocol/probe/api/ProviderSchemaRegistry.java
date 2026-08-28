package io.github.recrivenvi.minecraftprotocol.probe.api;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit registry of executable Provider V2 payload/query validators. */
public final class ProviderSchemaRegistry {
    @FunctionalInterface
    public interface Validator {
        ValidationResult validate(JsonObject value);
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult pass() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, Objects.requireNonNull(reason));
        }
    }

    private static final Map<String, Validator> VALIDATORS = new LinkedHashMap<>();

    private ProviderSchemaRegistry() {
    }

    public static synchronized void register(String schemaId, Validator validator) {
        if (schemaId == null || schemaId.isBlank() || !schemaId.contains("://")) {
            throw new IllegalArgumentException("Provider schema identity must be an absolute namespaced URI");
        }
        if (VALIDATORS.putIfAbsent(schemaId, Objects.requireNonNull(validator)) != null) {
            throw new IllegalStateException("Duplicate Provider schema: " + schemaId);
        }
    }

    public static synchronized boolean contains(String schemaId) {
        return schemaId != null && VALIDATORS.containsKey(schemaId);
    }

    public static synchronized ValidationResult validate(String schemaId, JsonObject value) {
        Validator validator = VALIDATORS.get(schemaId);
        if (validator == null) return ValidationResult.fail("unknown_schema");
        try {
            ValidationResult result = validator.validate(value == null ? new JsonObject() : value.deepCopy());
            return result == null ? ValidationResult.fail("validator_returned_null") : result;
        } catch (Throwable throwable) {
            return ValidationResult.fail("validator_exception:" + throwable.getClass().getSimpleName());
        }
    }
}

