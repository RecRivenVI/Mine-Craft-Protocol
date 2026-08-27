package io.github.recrivenvi.minecraftprotocol.probe;

import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricProbeMod implements ModInitializer {
    public static final String MOD_ID =
            io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PeerNetworking.initializeCommon();
        LOGGER.info("Mine-Craft-Protocol Phase 8 Fabric 26.3 common V1 Runtime loaded");
    }
}
