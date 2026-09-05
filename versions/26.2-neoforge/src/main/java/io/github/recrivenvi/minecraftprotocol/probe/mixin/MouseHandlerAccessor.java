package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("isLeftPressed") void minecraftProtocolProbe$setLeftPressed(boolean value);
    @Accessor("isRightPressed") void minecraftProtocolProbe$setRightPressed(boolean value);
    @Accessor("isMiddlePressed") void minecraftProtocolProbe$setMiddlePressed(boolean value);
    @Accessor("activeButton") void minecraftProtocolProbe$setActiveButton(net.minecraft.client.input.MouseButtonInfo value);
    @Accessor("mousePressedTime") void minecraftProtocolProbe$setMousePressedTime(double value);
    @Accessor("mouseGrabbed") void minecraftProtocolProbe$setMouseGrabbed(boolean value);
    @Accessor("ignoreFirstMove") void minecraftProtocolProbe$setIgnoreFirstMove(boolean value);
    @Accessor("xpos") void minecraftProtocolProbe$setXpos(double value);
    @Accessor("ypos") void minecraftProtocolProbe$setYpos(double value);
    @Accessor("accumulatedDX") void minecraftProtocolProbe$setAccumulatedDX(double value);
    @Accessor("accumulatedDY") void minecraftProtocolProbe$setAccumulatedDY(double value);
}
