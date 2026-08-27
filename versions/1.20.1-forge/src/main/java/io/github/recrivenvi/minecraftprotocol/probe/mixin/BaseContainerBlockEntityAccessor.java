package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow typed Accessor for the vanilla container custom-name field. */
@Mixin(BaseContainerBlockEntity.class)
public interface BaseContainerBlockEntityAccessor {
    @Accessor("name")
    void minecraftProtocol$setCustomName(Component name);
}
