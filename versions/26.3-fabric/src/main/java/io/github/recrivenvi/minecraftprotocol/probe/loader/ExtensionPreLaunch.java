package io.github.recrivenvi.minecraftprotocol.probe.loader;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.Mixins;

public final class ExtensionPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        LoaderExtensionBridge.hooks().submitConfigurations(Mixins::addConfiguration);
    }
}
