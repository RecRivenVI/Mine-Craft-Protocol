package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
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
    private void mineCraftProtocol$nativeButton(
            long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.button(
                                window, info.button(), action, info.modifiers()));
        if (!agent && NeoForgeRuntime.suppressNative(AgentInputContext.Kind.BUTTON)) ci.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$nativeMove(
            long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.point(AgentInputContext.Kind.MOVE, window, x, y));
        if (!agent && NeoForgeRuntime.suppressNative(AgentInputContext.Kind.MOVE)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$nativeScroll(long window, double x, double y, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.point(AgentInputContext.Kind.SCROLL, window, x, y));
        if (!agent && NeoForgeRuntime.suppressNative(AgentInputContext.Kind.SCROLL)) ci.cancel();
    }

    @Inject(method = "onDrop", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$nativeDrop(CallbackInfo ci) {
        if (NeoForgeRuntime.suppressNative(AgentInputContext.Kind.DROP)) ci.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$hostGrab(CallbackInfo ci) {
        if (!NeoForgeRuntime.allowHostMouseGrab()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$hostRelease(CallbackInfo ci) {
        if (NeoForgeRuntime.handleMouseRelease()) ci.cancel();
    }

    @Inject(method = "resyncMousePosition", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$logicalResync(CallbackInfo ci) {
        if (NeoForgeRuntime.handleMouseResync()) ci.cancel();
    }

    @Inject(method = "isMouseGrabbed", at = @At("RETURN"), cancellable = true)
    private void mineCraftProtocol$virtualGrab(CallbackInfoReturnable<Boolean> cir) {
        if (NeoForgeRuntime.routedMouseGrabbed()) cir.setReturnValue(true);
    }
}
