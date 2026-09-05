package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.ForgeProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeButton(
            long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (!AgentInputContext.isAgentRouted()
                && ForgeProbeRuntime.onNativeMouseButton(window, button, action)) ci.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$hostGrab(CallbackInfo ci) {
        if (!ForgeProbeRuntime.allowHostMouseGrab()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$hostRelease(CallbackInfo ci) {
        if (ForgeProbeRuntime.handleMouseRelease()) ci.cancel();
    }

    @Inject(method = "isMouseGrabbed", at = @At("RETURN"), cancellable = true)
    private void minecraftProtocolProbe$virtualGrab(CallbackInfoReturnable<Boolean> cir) {
        if (ForgeProbeRuntime.routedMouseGrabbed()) cir.setReturnValue(true);
    }
}
