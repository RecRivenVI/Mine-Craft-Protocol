package io.github.recrivenvi.minecraftprotocol.probe.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class MinecraftProtocolProviders {
    private static final Map<String, ReadProvider> PROVIDERS = new ConcurrentHashMap<>();

    private MinecraftProtocolProviders() {
    }

    public static void register(ReadProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = Objects.requireNonNull(provider.id(), "provider.id");
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Provider ID must be namespaced and must not use minecraft: " + id);
        }
        ReadProvider previous = PROVIDERS.putIfAbsent(id, provider);
        if (previous != null) throw new IllegalStateException("Provider is already registered: " + id);
    }

    public static boolean unregister(String id) {
        return PROVIDERS.remove(id) != null;
    }

    public static List<ReadProvider> snapshot() {
        return PROVIDERS.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }
}
