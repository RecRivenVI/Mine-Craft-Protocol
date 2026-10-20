package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SavedDataStorage.class)
public interface UiWorldRecoverySavedDataProbeAccessor {
    @Accessor("closed")
    boolean minecraftProtocolProbe$closed();
}
