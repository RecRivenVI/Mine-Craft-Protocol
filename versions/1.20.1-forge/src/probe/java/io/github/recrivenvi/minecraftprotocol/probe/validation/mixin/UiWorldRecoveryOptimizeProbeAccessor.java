package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;
import net.minecraft.util.worldupdate.WorldUpgrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptimizeWorldScreen.class)
public interface UiWorldRecoveryOptimizeProbeAccessor {
    @Accessor("upgrader")
    WorldUpgrader minecraftProtocolProbe$upgrader();
}
