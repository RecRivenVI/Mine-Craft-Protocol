package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("smoothTurnX")
    net.minecraft.util.SmoothDouble mineCraftProtocol$smoothTurnX();

    @Accessor("smoothTurnY")
    net.minecraft.util.SmoothDouble mineCraftProtocol$smoothTurnY();

    @Accessor("isLeftPressed")
    void mineCraftProtocol$setLeftPressed(boolean value);

    @Accessor("isRightPressed")
    void mineCraftProtocol$setRightPressed(boolean value);

    @Accessor("isMiddlePressed")
    void mineCraftProtocol$setMiddlePressed(boolean value);

    @Accessor("activeButton")
    void mineCraftProtocol$setActiveButton(net.minecraft.client.input.MouseButtonInfo value);

    @Accessor("mousePressedTime")
    void mineCraftProtocol$setMousePressedTime(double value);

    @Accessor("mouseGrabbed")
    void mineCraftProtocol$setMouseGrabbed(boolean value);

    @Accessor("ignoreFirstMove")
    void mineCraftProtocol$setIgnoreFirstMove(boolean value);

    @Accessor("xpos")
    void mineCraftProtocol$setXpos(double value);

    @Accessor("ypos")
    void mineCraftProtocol$setYpos(double value);

    @Accessor("accumulatedDX")
    void mineCraftProtocol$setAccumulatedDX(double value);

    @Accessor("accumulatedDY")
    void mineCraftProtocol$setAccumulatedDY(double value);
}
