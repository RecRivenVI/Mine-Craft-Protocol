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

    record ControlState(
            String mode, String controlSessionId, long generation, boolean reconsentRequired) {
        public boolean takeoverActive() {
            return mode.equals("TAKEOVER");
        }
    }

    /** Presence only; neither permission nor an input dispatch permit. */
    ControlState controlState();

    ExtensionResources.Scope processResources();

    /** Unavailable contexts are absent; acquiring a resource scope grants no game authority. */
    LifetimeContexts lifetimeContexts();

    ExtensionResources.Scope contextResources(ExtensionResources.Lifetime lifetime);

    java.util.Map<String, ExtensionResources.ThreadOwner> threadOwners();

    /**
     * Installed native code only: access the current native client/server on its verified owner.
     * Never serialized or exposed as a remote object API; callers must not retain this context.
     */
    default Object nativeContext(String ownerId) {
        throw new UnsupportedOperationException("No native owner context");
    }

    Registration schema(String id, ProviderSchemaRegistry.Validator validator);

    Registration provider(AgentDataProviderV2 provider);

    Registration uiAdapter(UiAdapter adapter);

    Registration inputAdapter(LogicalInputAdapter adapter);

    Registration worldAdapter(WorldAdapter adapter);

    enum Side {
        CLIENT,
        SERVER
    }
}
