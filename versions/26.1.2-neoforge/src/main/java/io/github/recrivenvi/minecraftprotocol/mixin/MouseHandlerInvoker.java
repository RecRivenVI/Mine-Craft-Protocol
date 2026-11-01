package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onMove")
    void mineCraftProtocol$onMove(long window, double x, double y);

    @Invoker("onButton")
    void mineCraftProtocol$onButton(long window, MouseButtonInfo buttonInfo, int action);

    @Invoker("onScroll")
    void mineCraftProtocol$onScroll(long window, double xOffset, double yOffset);

    @Invoker("grabMouse")
    void mineCraftProtocol$grabMouse();

    @Invoker("releaseMouse")
    void mineCraftProtocol$releaseMouse();
}
