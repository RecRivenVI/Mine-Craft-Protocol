package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("mouseGrabbed") void minecraftProtocolProbe$setMouseGrabbed(boolean value);
    @Accessor("ignoreFirstMove") void minecraftProtocolProbe$setIgnoreFirstMove(boolean value);
    @Accessor("xpos") void minecraftProtocolProbe$setXpos(double value);
    @Accessor("ypos") void minecraftProtocolProbe$setYpos(double value);
    @Accessor("accumulatedDX") void minecraftProtocolProbe$setAccumulatedDX(double value);
    @Accessor("accumulatedDY") void minecraftProtocolProbe$setAccumulatedDY(double value);
}
