package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native parsed command data only, not a world or storage lookup. */
@Mixin(BlockInput.class)
public interface CommandBlockInputAccessor {
    @Accessor("tag")
    CompoundTag minecraftProtocol$commandTag();
}
