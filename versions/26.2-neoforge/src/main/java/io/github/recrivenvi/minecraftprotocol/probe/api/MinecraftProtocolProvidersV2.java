package io.github.recrivenvi.minecraftprotocol.probe.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinecraftProtocolProvidersV2 {
    private static final Map<String, AgentDataProviderV2> PROVIDERS = new LinkedHashMap<>();

    private MinecraftProtocolProvidersV2() {
    }

    public static synchronized void register(AgentDataProviderV2 provider) {
        AgentDataProviderV2.Descriptor descriptor = provider.descriptor();
        String id = descriptor.providerId();
        if (id == null || !id.contains(":") || id.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Provider V2 ID must be namespaced and cannot use minecraft: " + id);
        }
        if (descriptor.schemaVersion() == null || descriptor.schemaVersion().isBlank()
                || descriptor.snapshotSchema() == null || descriptor.snapshotSchema().isBlank()) {
            throw new IllegalArgumentException("Provider V2 requires schemaVersion and snapshotSchema: " + id);
        }
        if (PROVIDERS.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("Duplicate Provider V2: " + id);
        }
    }

    public static synchronized List<AgentDataProviderV2> snapshot() {
        return new ArrayList<>(PROVIDERS.values());
    }
}
