package io.github.recrivenvi.minecraftprotocol;

import com.mojang.logging.LogUtils;
import io.github.recrivenvi.minecraftprotocol.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeEchoReadProvider;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeV2Providers;
import io.github.recrivenvi.minecraftprotocol.peer.PeerNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NeoForgeProtocolMod.MOD_ID)
public final class NeoForgeProtocolMod {
    public static final String MOD_ID =
            io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeProtocolMod(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        io.github.recrivenvi.minecraftprotocol.server.DedicatedServerRuntime.initialize();
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider(MOD_ID));
        ProbeV2Providers.registerAll(MOD_ID);
        modBus.addListener(PeerNetworking::register);
        if (net.neoforged.fml.loading.FMLLoader.getCurrent().getDist()
                == net.neoforged.api.distmarker.Dist.CLIENT)
            container.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (ignored, parent) ->
                            new io.github.recrivenvi.minecraftprotocol.client.ProtocolConfigScreen(
                                    parent));
        LOGGER.info(
                "{} {} Runtime loaded ({})",
                ProtocolIdentity.DISPLAY_NAME,
                ProtocolIdentity.VERSION,
                ProtocolIdentity.TARGET_ID);
    }
}
