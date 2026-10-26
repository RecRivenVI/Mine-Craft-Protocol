package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read cached links without invoking lazy resolution or save hooks. */
@Mixin(Mob.class)
public interface MobLeashAccessor {
    @Accessor("leashHolder")
    Entity minecraftProtocol$getLeashHolder();

    @Accessor("leashInfoTag")
    CompoundTag minecraftProtocol$getLeashInfoTag();
}
