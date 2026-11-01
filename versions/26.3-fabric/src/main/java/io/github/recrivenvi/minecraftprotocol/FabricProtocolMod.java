package io.github.recrivenvi.minecraftprotocol;

import io.github.recrivenvi.minecraftprotocol.peer.PeerNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricProtocolMod implements ModInitializer {
    public static final String MOD_ID =
            io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        io.github.recrivenvi.minecraftprotocol.server.DedicatedServerRuntime.initialize();
        PeerNetworking.initializeCommon();
        LOGGER.info(
                "{} {} Runtime loaded ({})",
                ProtocolIdentity.DISPLAY_NAME,
                ProtocolIdentity.VERSION,
                ProtocolIdentity.TARGET_ID);
    }
}
