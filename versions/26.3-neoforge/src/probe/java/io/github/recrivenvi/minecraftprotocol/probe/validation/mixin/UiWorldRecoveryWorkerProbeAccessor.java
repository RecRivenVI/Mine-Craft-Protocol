package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldUpgrader.class)
public interface UiWorldRecoveryWorkerProbeAccessor {
    @Accessor("thread")
    Thread minecraftProtocolProbe$thread();

    @Accessor("overworldSavedDataStorage")
    SavedDataStorage minecraftProtocolProbe$savedData();
}
