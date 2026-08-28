package io.github.recrivenvi.minecraftprotocol.probe.api;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Explicit, detached and schema-versioned observation extension contract. */
public interface AgentDataProviderV2 {
    Descriptor descriptor();

    CompletableFuture<JsonObject> capture(ReadContext context);

    record Descriptor(
            String providerId,
            String schemaVersion,
            List<String> capabilities,
            String snapshotSchema,
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
            String deltaCapability,
            DebugDeclaration debugDeclaration,
            List<String> requiredScopes) {
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
            query = query.deepCopy();
        }
    }
}

