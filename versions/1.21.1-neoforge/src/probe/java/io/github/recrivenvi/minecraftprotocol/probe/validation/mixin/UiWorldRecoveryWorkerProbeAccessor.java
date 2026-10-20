package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.util.worldupdate.WorldUpgrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldUpgrader.class)
public interface UiWorldRecoveryWorkerProbeAccessor {
    @Accessor("thread")
    Thread minecraftProtocolProbe$thread();
}
