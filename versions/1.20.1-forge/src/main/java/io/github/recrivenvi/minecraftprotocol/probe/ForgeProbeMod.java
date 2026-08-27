package io.github.recrivenvi.minecraftprotocol.probe;

import com.mojang.logging.LogUtils;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.peer.PeerNetworking;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ForgeProbeMod.MOD_ID)
public final class ForgeProbeMod {
    public static final String MOD_ID = "minecraft_protocol_probe";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ForgeProbeMod() {
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider());
        PeerNetworking.initialize();
        LOGGER.info("Mine-Craft-Protocol Phase 8 Forge 1.20.1 V1 Runtime loaded");
    }
}
