package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeButton(
            long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (!AgentInputContext.isAgentRouted()
                && FabricProbeRuntime.onNativeMouseButton(window, info.button(), action)) ci.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$hostGrab(CallbackInfo ci) {
        if (!FabricProbeRuntime.allowHostMouseGrab()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$hostRelease(CallbackInfo ci) {
        if (FabricProbeRuntime.handleMouseRelease()) ci.cancel();
    }

    @Inject(method = "isMouseGrabbed", at = @At("RETURN"), cancellable = true)
    private void minecraftProtocolProbe$virtualGrab(CallbackInfoReturnable<Boolean> cir) {
        if (FabricProbeRuntime.routedMouseGrabbed()) cir.setReturnValue(true);
    }
}
