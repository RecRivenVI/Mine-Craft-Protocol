package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onMove")
    void minecraftProtocolProbe$onMove(long window, double x, double y);

    @Invoker("onPress")
    void minecraftProtocolProbe$onPress(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void minecraftProtocolProbe$onScroll(long window, double xOffset, double yOffset);

    @Invoker("grabMouse")
    void minecraftProtocolProbe$grabMouse();

    @Invoker("releaseMouse")
    void minecraftProtocolProbe$releaseMouse();
}

