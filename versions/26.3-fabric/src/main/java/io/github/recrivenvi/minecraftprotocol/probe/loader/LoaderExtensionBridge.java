package io.github.recrivenvi.minecraftprotocol.probe.loader;

import io.github.recrivenvi.minecraftprotocol.ProtocolIdentity;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionEarlyBootstrap;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionMixinGate;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

public final class LoaderExtensionBridge extends ExtensionMixinGate {
    @Override
    protected Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    protected void prepareLibrary(Path library) {
        FabricLauncherBase.getLauncher().addToClassPath(library);
    }

    @Override
    protected ExtensionPlatform.Environment environment() {
        var loader = FabricLoader.getInstance();
        var mods = new LinkedHashMap<String, String>();
        loader.getAllMods()
                .forEach(
                        mod ->
                                mods.put(
                                        mod.getMetadata().getId(),
                                        mod.getMetadata().getVersion().getFriendlyString()));
        return new ExtensionPlatform.Environment(
                ProtocolIdentity.MOD_ID,
                ProtocolIdentity.TARGET_ID,
                loader.getEnvironmentType() == EnvType.CLIENT
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
