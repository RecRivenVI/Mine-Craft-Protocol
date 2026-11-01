package io.github.recrivenvi.minecraftprotocol;

import static io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;

import io.github.recrivenvi.minecraftprotocol.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeEchoReadProvider;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeV2Providers;
import io.github.recrivenvi.minecraftprotocol.peer.PeerClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricClientProtocolMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricProtocolMod.MOD_ID);

    @Override
    public void onInitializeClient() {
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider(MOD_ID));
        ProbeV2Providers.registerAll(MOD_ID);
        PeerClientNetworking.initialize();
        LOGGER.info(
                "{} {} Runtime loaded ({})",
                ProtocolIdentity.DISPLAY_NAME,
                ProtocolIdentity.VERSION,
                ProtocolIdentity.TARGET_ID);
    }
}
