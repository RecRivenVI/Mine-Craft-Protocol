package io.github.recrivenvi.minecraftprotocol;

import com.mojang.logging.LogUtils;
import io.github.recrivenvi.minecraftprotocol.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeEchoReadProvider;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeV2Providers;
import io.github.recrivenvi.minecraftprotocol.peer.PeerNetworking;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ForgeProtocolMod.MOD_ID)
public final class ForgeProtocolMod {
    public static final String MOD_ID =
            io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public ForgeProtocolMod() {
        io.github.recrivenvi.minecraftprotocol.server.DedicatedServerRuntime.initialize();
        MinecraftProtocolProviders.register(new ProbeEchoReadProvider(MOD_ID));
        ProbeV2Providers.registerAll(MOD_ID);
        PeerNetworking.initialize();
        if (net.minecraftforge.fml.loading.FMLLoader.getDist()
                == net.minecraftforge.api.distmarker.Dist.CLIENT)
            net.minecraftforge.fml.ModLoadingContext.get()
                    .registerExtensionPoint(
                            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                            () ->
                                    new net.minecraftforge.client.ConfigScreenHandler
                                            .ConfigScreenFactory(
                                            (minecraft, parent) ->
                                                    new io.github.recrivenvi.minecraftprotocol
                                                            .client.ProtocolConfigScreen(parent)));
        LOGGER.info(
                "{} {} Runtime loaded ({})",
                ProtocolIdentity.DISPLAY_NAME,
                ProtocolIdentity.VERSION,
                ProtocolIdentity.TARGET_ID);
    }
}
