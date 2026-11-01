package io.github.recrivenvi.minecraftprotocol.api.extension;

import java.util.Map;

/** Opaque native lifetime identities. Presence is not permission or a native object handle. */
public record LifetimeContexts(Map<ExtensionResources.Lifetime, String> identities) {
    public LifetimeContexts {
        identities = Map.copyOf(identities);
        if (identities.entrySet().stream().anyMatch(entry -> entry.getValue().isBlank()))
            throw new IllegalArgumentException("Nonempty lifetime identity required");
    }

    public static LifetimeContexts empty() {
        return new LifetimeContexts(Map.of());
    }

    public String identity(ExtensionResources.Lifetime lifetime) {
        return identities.get(lifetime);
    }
}
