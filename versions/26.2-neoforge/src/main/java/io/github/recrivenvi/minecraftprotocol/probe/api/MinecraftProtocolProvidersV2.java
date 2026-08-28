package io.github.recrivenvi.minecraftprotocol.probe.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class MinecraftProtocolProvidersV2 {
    public static final int MAX_REGISTERED_PROVIDERS = 256;
    private static final Set<String> AFFINITIES = Set.of(
            "detached_provider_worker", "client_thread", "server_thread", "render_thread");
    private static final Set<String> PERSPECTIVES = Set.of("client_known", "server_authoritative");
    private static final Set<String> READ_EFFECTS = Set.of("none", "lazy_initialization");
    private static final Set<String> REVISION_SOURCES = Set.of("provider_revision", "snapshot_change_sequence");
    private static final Set<String> REVISION_SCOPES = Set.of("resource", "query_view");
    private static final Set<String> DELTA_CAPABILITIES = Set.of("none", "snapshot_diff_delta", "native_typed_delta");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SCOPE = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final Map<String, AgentDataProviderV2> PROVIDERS = new LinkedHashMap<>();

    private MinecraftProtocolProvidersV2() {
    }

    public static synchronized void register(AgentDataProviderV2 provider) {
        if (PROVIDERS.size() >= MAX_REGISTERED_PROVIDERS) {
            throw new IllegalStateException(
                    "Provider V2 registry is full: " + MAX_REGISTERED_PROVIDERS);
        }
        Objects.requireNonNull(provider, "provider");
        AgentDataProviderV2.Descriptor descriptor = Objects.requireNonNull(provider.descriptor(), "descriptor");
        String id = descriptor.providerId();
        if (id == null || !id.contains(":") || id.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Provider V2 ID must be namespaced and cannot use minecraft: " + id);
        }
        if (descriptor.schemaVersion() == null || !VERSION.matcher(descriptor.schemaVersion()).matches()) {
            throw new IllegalArgumentException("Provider V2 schemaVersion is invalid: " + id);
        }
        requireSchema(id, "snapshotSchema", descriptor.snapshotSchema());
        requireSchema(id, "querySchema", descriptor.querySchema());
        if (!AFFINITIES.contains(descriptor.threadAffinity())) {
            throw new IllegalArgumentException("Unsupported Provider V2 threadAffinity: " + descriptor.threadAffinity());
        }
        if (descriptor.perspectives().isEmpty()
                || descriptor.perspectives().stream().anyMatch(value -> !PERSPECTIVES.contains(value))) {
            throw new IllegalArgumentException("Provider V2 perspectives are empty or invalid: " + id);
        }
        if (!READ_EFFECTS.contains(descriptor.readEffects())) {
            throw new IllegalArgumentException("Provider V2 readEffects is invalid: " + id);
        }
        if (descriptor.requiredScopes().stream().anyMatch(scope -> scope == null || !SCOPE.matcher(scope).matches())) {
            throw new IllegalArgumentException("Provider V2 requiredScopes are invalid: " + id);
        }
        if (!REVISION_SOURCES.contains(descriptor.revisionSource())) {
            throw new IllegalArgumentException("Provider V2 revisionSource is invalid: " + id);
        }
        if (!REVISION_SCOPES.contains(descriptor.revisionScope())) {
            throw new IllegalArgumentException("Provider V2 revisionScope is invalid: " + id);
        }
        if ("resource".equals(descriptor.revisionScope())) {
            requireSchema(id, "revisionSchema", descriptor.revisionSchema());
            if (!descriptor.revisionQueryInvariant()) {
                throw new IllegalArgumentException(
                        "Resource-scoped Provider V2 revision must be query invariant: " + id);
            }
        } else if (descriptor.revisionQueryInvariant()) {
            throw new IllegalArgumentException(
                    "query_view Provider V2 revision cannot claim query invariance: " + id);
        }
        if ("provider_revision".equals(descriptor.revisionSource())
                && !"resource".equals(descriptor.revisionScope())) {
            throw new IllegalArgumentException(
                    "Native Provider V2 revision must be resource scoped: " + id);
        }
        if (!DELTA_CAPABILITIES.contains(descriptor.deltaCapability())) {
            throw new IllegalArgumentException("Provider V2 deltaCapability is invalid: " + id);
        }
        validateEffectContract(descriptor);
        validateDebugContract(descriptor);
        if (PROVIDERS.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("Duplicate Provider V2: " + id);
        }
    }

    public static synchronized List<AgentDataProviderV2> snapshot() {
        return new ArrayList<>(PROVIDERS.values());
    }

    private static void requireSchema(String id, String field, String schema) {
        if (schema == null || schema.isBlank() || !ProviderSchemaRegistry.contains(schema)) {
            throw new IllegalArgumentException("Provider V2 " + field + " is not registered: " + id);
        }
    }

    private static void validateEffectContract(AgentDataProviderV2.Descriptor descriptor) {
        boolean hasEffects = descriptor.mayInitialize() || descriptor.mayLoadData()
                || descriptor.mayAccessStorage() || descriptor.mayMutate();
        if (descriptor.snapshotSafe() && (hasEffects || !"none".equals(descriptor.readEffects()))) {
            throw new IllegalArgumentException("snapshotSafe Provider V2 cannot declare side effects: " + descriptor.providerId());
        }
        if (descriptor.mayInitialize() && !"lazy_initialization".equals(descriptor.readEffects())) {
            throw new IllegalArgumentException("mayInitialize requires lazy_initialization readEffects: " + descriptor.providerId());
        }
    }

    private static void validateDebugContract(AgentDataProviderV2.Descriptor descriptor) {
        AgentDataProviderV2.DebugDeclaration debug = Objects.requireNonNull(
                descriptor.debugDeclaration(), "debugDeclaration");
        if (debug.supported()) {
            if (debug.mutationSchema() == null || debug.mutationSchema().isBlank()
                    || !ProviderSchemaRegistry.contains(debug.mutationSchema())
                    || debug.resultSchema() == null || debug.resultSchema().isBlank()
                    || !ProviderSchemaRegistry.contains(debug.resultSchema())
                    || debug.requiredScope() == null || !SCOPE.matcher(debug.requiredScope()).matches()
                    || !debug.requiresArm()
                    || !debug.supportsResourceVersionPrecondition()
                    || !"must_advance".equals(debug.revisionBehavior())
                    || debug.synchronizationBehavior() == null
                    || debug.synchronizationBehavior().isBlank()) {
                throw new IllegalArgumentException("Provider V2 debug declaration is inconsistent: "
                        + descriptor.providerId());
            }
        } else if (descriptor.mayMutate()) {
            throw new IllegalArgumentException("mayMutate Provider V2 must declare gated debug support: "
                    + descriptor.providerId());
        }
    }
}
