package io.github.recrivenvi.minecraftprotocol.probe;

import com.mojang.logging.LogUtils;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NeoForgeProbeMod.MOD_ID)
public final class NeoForgeProbeMod {
    public static final String MOD_ID = "minecraft_protocol_probe";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeProbeMod(IEventBus modBus) {
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider());
        ProbeV2Providers.registerAll();
        modBus.addListener(PeerNetworking::register);
        LOGGER.info("Mine-Craft-Protocol Phase 8 NeoForge 1.21.1 V1 Runtime loaded");
    }
}
