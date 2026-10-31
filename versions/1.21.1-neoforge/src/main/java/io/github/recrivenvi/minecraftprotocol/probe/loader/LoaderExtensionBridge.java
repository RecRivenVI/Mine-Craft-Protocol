package io.github.recrivenvi.minecraftprotocol.probe.loader;

import io.github.recrivenvi.minecraftprotocol.ProtocolIdentity;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionEarlyBootstrap;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionMixinGate;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;

public final class LoaderExtensionBridge extends ExtensionMixinGate {
    @Override
    protected Path gameDirectory() {
        return FMLLoader.getGamePath();
    }

    @Override
    protected ExtensionPlatform.Environment environment() {
        var mods = new LinkedHashMap<String, String>();
        FMLLoader.getLoadingModList()
                .getMods()
                .forEach(mod -> mods.put(mod.getModId(), mod.getVersion().toString()));
        return new ExtensionPlatform.Environment(
                ProtocolIdentity.MOD_ID,
                ProtocolIdentity.TARGET_ID,
                FMLLoader.getDist() == Dist.CLIENT
                        ? PackageContext.Side.CLIENT
                        : PackageContext.Side.SERVER,
                Runtime.version().feature(),
                mods,
                getClass().getClassLoader());
    }

    public static ExtensionEarlyBootstrap hooks() {
        return new LoaderExtensionBridge().initializeBridge();
    }
}
