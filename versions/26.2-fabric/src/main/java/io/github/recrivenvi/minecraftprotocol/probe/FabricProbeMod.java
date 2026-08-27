package io.github.recrivenvi.minecraftprotocol.probe;

import net.fabricmc.api.ModInitializer;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricProbeMod implements ModInitializer {
    public static final String MOD_ID = "minecraft_protocol_probe";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PeerNetworking.initializeCommon();
        LOGGER.info("Mine-Craft-Protocol Phase 8 Fabric 26.2 common V1 Runtime loaded");
    }
}
