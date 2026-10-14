package io.github.recrivenvi.minecraftprotocol.probe;

import static io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;

import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeEchoReadProvider;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeV2Providers;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricClientProbeMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricProbeMod.MOD_ID);

    @Override
    public void onInitializeClient() {
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider(MOD_ID));
        ProbeV2Providers.registerAll(MOD_ID);
        PeerClientNetworking.initialize();
        LOGGER.info("Mine-Craft-Protocol Phase 8 Fabric 26.3 client V1 Runtime loaded");
    }
}
