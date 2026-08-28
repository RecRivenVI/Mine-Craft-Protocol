package io.github.recrivenvi.minecraftprotocol.probe.api;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Explicit, schema-versioned observation extension contract. */
public interface AgentDataProviderV2 {
    Descriptor descriptor();

    CompletableFuture<JsonObject> capture(ReadContext context);

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
            String schema,
            String requiredScope,
            boolean requiresArm) {
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
}
