package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onMove")
    void minecraftProtocolProbe$onMove(long window, double x, double y);

    @Invoker("onButton")
    void minecraftProtocolProbe$onButton(long window, MouseButtonInfo buttonInfo, int action);

    @Invoker("onScroll")
    void minecraftProtocolProbe$onScroll(long window, double xOffset, double yOffset);

    @Invoker("grabMouse")
    void minecraftProtocolProbe$grabMouse();

    @Invoker("releaseMouse")
    void minecraftProtocolProbe$releaseMouse();
}
