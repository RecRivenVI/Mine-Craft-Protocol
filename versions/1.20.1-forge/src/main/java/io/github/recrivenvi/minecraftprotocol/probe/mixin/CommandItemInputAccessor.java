package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to native parsed item data, never a player inventory lookup. */
@Mixin(ItemInput.class)
public interface CommandItemInputAccessor {
    @Accessor("tag")
    CompoundTag minecraftProtocol$commandTag();
}
