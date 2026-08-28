package io.github.recrivenvi.minecraftprotocol.probe;

import net.fabricmc.api.ClientModInitializer;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerClientNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricClientProbeMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricProbeMod.MOD_ID);

    @Override
    public void onInitializeClient() {
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider());
        ProbeV2Providers.registerAll();
        PeerClientNetworking.initialize();
        LOGGER.info("Mine-Craft-Protocol Phase 8 Fabric 26.2 client V1 Runtime loaded");
    }
}
