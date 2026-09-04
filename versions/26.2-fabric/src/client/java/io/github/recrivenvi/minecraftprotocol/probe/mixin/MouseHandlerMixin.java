package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$handleNativeButton(
            long window, MouseButtonInfo buttonInfo, int action, CallbackInfo callbackInfo) {
        if (!AgentInputContext.isAgentRouted()
                && FabricProbeRuntime.onNativeMouseButton(window, buttonInfo.button(), action)) callbackInfo.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void minecraftProtocolProbe$markAgentMove(long window, double x, double y, CallbackInfo callbackInfo) {
        if (AgentInputContext.isAgentRouted()) FabricProbeRuntime.onAgentMouseMove(window);
    }
}
