package io.github.recrivenvi.minecraftprotocol.probe.extension;

import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSchemaRegistry;

/** The public SDK is Minecraft-independent; registering code never grants remote authority. */
public interface PackageContext {
    int SDK_API = 1;

    String packageId();

    String contentIdentity();

    String target();

    Side side();

    ExtensionResources.Scope processResources();

    java.util.Map<String, ExtensionResources.ThreadOwner> threadOwners();

    Registration schema(String id, ProviderSchemaRegistry.Validator validator);

    Registration provider(AgentDataProviderV2 provider);

    enum Side {
        CLIENT,
        SERVER
    }
}
