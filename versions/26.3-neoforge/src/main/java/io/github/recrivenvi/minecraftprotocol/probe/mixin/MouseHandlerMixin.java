package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeButton(
            long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.button(
                                window, info.button(), action, info.modifiers()));
        if (!agent && NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.BUTTON))
            ci.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeMove(
            long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.point(AgentInputContext.Kind.MOVE, window, x, y));
        if (!agent && NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.MOVE)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeScroll(
            long window, double x, double y, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.point(AgentInputContext.Kind.SCROLL, window, x, y));
        if (!agent && NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.SCROLL))
            ci.cancel();
    }

    @Inject(method = "onDrop", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeDrop(CallbackInfo ci) {
        if (NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.DROP)) ci.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$hostGrab(CallbackInfo ci) {
        if (!NeoForgeProbeRuntime.allowHostMouseGrab()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$hostRelease(CallbackInfo ci) {
        if (NeoForgeProbeRuntime.handleMouseRelease()) ci.cancel();
    }

    @Inject(method = "resyncMousePosition", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$logicalResync(CallbackInfo ci) {
        if (NeoForgeProbeRuntime.handleMouseResync()) ci.cancel();
    }

    @Inject(method = "isMouseGrabbed", at = @At("RETURN"), cancellable = true)
    private void minecraftProtocolProbe$virtualGrab(CallbackInfoReturnable<Boolean> cir) {
        if (NeoForgeProbeRuntime.routedMouseGrabbed()) cir.setReturnValue(true);
    }
}
