package io.github.recrivenvi.minecraftprotocol.probe.api;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Explicit, schema-versioned observation extension contract. */
public interface AgentDataProviderV2 {
    Descriptor descriptor();

    CompletableFuture<JsonObject> capture(ReadContext context);

    /** Separate typed mutation path. Observation queries must never hide mutation commands. */
    default CompletableFuture<JsonObject> mutate(DebugContext context) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Provider does not implement typed Debug mutation"));
    }

    record Descriptor(
            String providerId,
            String schemaVersion,
            List<String> capabilities,
            String snapshotSchema,
            String querySchema,
            String threadAffinity,
            List<String> perspectives,
            String readEffects,
            String completeness,
            boolean snapshotSafe,
            boolean mayInitialize,
            boolean mayLoadData,
            boolean mayAccessStorage,
            boolean mayMutate,
            String revisionSource,
            String revisionScope,
            String revisionSchema,
            boolean revisionQueryInvariant,
            String deltaCapability,
            DebugDeclaration debugDeclaration,
            List<String> requiredScopes) {
        public Descriptor {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
            perspectives = perspectives == null ? List.of() : List.copyOf(perspectives);
            requiredScopes = requiredScopes == null ? List.of() : List.copyOf(requiredScopes);
        }
    }

    record DebugDeclaration(
            boolean supported,
            String mutationSchema,
            String resultSchema,
            String requiredScope,
            boolean requiresArm,
            boolean supportsResourceVersionPrecondition,
            String revisionBehavior,
            String synchronizationBehavior) {
        public DebugDeclaration(
                boolean supported,
                String schema,
                String requiredScope,
                boolean requiresArm) {
            this(
                    supported, schema, schema, requiredScope, requiresArm,
                    supported, supported ? "must_advance" : "none",
                    supported ? "provider_declared" : "none");
        }
    }

    record ReadContext(
            JsonObject query,
            String perspective,
            boolean allowReadEffects,
            long deadlineMillis,
            int byteBudget) {
        public ReadContext {
            query = query == null ? new JsonObject() : query.deepCopy();
        }
    }

    record DebugContext(
            JsonObject mutation,
            JsonObject expectedResourceVersion,
            String worldFingerprint,
            long deadlineMillis,
            int byteBudget,
            String requestId,
            BooleanSupplier cancellationRequested) {
        public DebugContext {
            mutation = mutation == null ? new JsonObject() : mutation.deepCopy();
            expectedResourceVersion = expectedResourceVersion == null
                    ? new JsonObject() : expectedResourceVersion.deepCopy();
            cancellationRequested = cancellationRequested == null ? () -> false : cancellationRequested;
        }
    }
}
