package io.github.recrivenvi.minecraftprotocol.loader;

import io.github.recrivenvi.minecraftprotocol.ProtocolIdentity;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionEarlyBootstrap;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionMixinGate;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

public final class LoaderExtensionBridge extends ExtensionMixinGate {
    private static boolean installationPrepared;

    @Override
    protected synchronized void prepareInstallation() {
        synchronized (LoaderExtensionBridge.class) {
            if (installationPrepared) return;
            try {
                io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionEarlyStage.refresh(
                        gameDirectory(), environment());
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot prepare approved extensionpacks", failure);
            }
            installationPrepared = true;
        }
    }

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
                        ? ExtensionContext.Side.CLIENT
                        : ExtensionContext.Side.SERVER,
                Runtime.version().feature(),
                mods,
                getClass().getClassLoader());
    }

    public static ExtensionPlatform.Environment snapshotEnvironment() {
        return new LoaderExtensionBridge().environment();
    }

    public static ExtensionEarlyBootstrap hooks() {
        return new LoaderExtensionBridge().initializeBridge();
    }
}
