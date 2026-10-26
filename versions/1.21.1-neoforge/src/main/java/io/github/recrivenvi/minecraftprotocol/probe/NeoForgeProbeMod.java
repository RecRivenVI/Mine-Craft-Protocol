package io.github.recrivenvi.minecraftprotocol.probe;

import com.mojang.logging.LogUtils;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeEchoReadProvider;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeV2Providers;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NeoForgeProbeMod.MOD_ID)
public final class NeoForgeProbeMod {
    public static final String MOD_ID =
            io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeProbeMod(IEventBus modBus) {
        io.github.recrivenvi.minecraftprotocol.probe.server.DedicatedServerRuntime.initialize();
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider(MOD_ID));
        ProbeV2Providers.registerAll(MOD_ID);
        modBus.addListener(PeerNetworking::register);
        LOGGER.info("Mine-Craft-Protocol Phase 8 NeoForge 1.21.1 V1 Runtime loaded");
    }
}
