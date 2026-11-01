package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Redirect(
            method = "setup",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/platform/InputConstants;setupMouseCallbacks(Lcom/mojang/blaze3d/platform/Window;Lorg/lwjgl/glfw/GLFWCursorPosCallbackI;Lorg/lwjgl/glfw/GLFWMouseButtonCallbackI;Lorg/lwjgl/glfw/GLFWScrollCallbackI;Lorg/lwjgl/glfw/GLFWDropCallbackI;)V"))
    private void mineCraftProtocol$nativeIngress(
            Window window,
            GLFWCursorPosCallbackI move,
            GLFWMouseButtonCallbackI button,
            GLFWScrollCallbackI scroll,
            GLFWDropCallbackI drop) {
        MouseHandlerInvoker self = (MouseHandlerInvoker) (Object) this;
        InputConstants.setupMouseCallbacks(
                window,
                (w, x, y) ->
                        this.minecraft.execute(
                                AgentInputContext.nativeTask(
                                        () -> self.mineCraftProtocol$onMove(w, x, y))),
                (w, b, a, m) ->
                        this.minecraft.execute(
                                AgentInputContext.nativeTask(
                                        () ->
                                                self.mineCraftProtocol$onButton(
                                                        w, new MouseButtonInfo(b, m), a))),
                (w, x, y) ->
                        this.minecraft.execute(
                                AgentInputContext.nativeTask(
                                        () -> self.mineCraftProtocol$onScroll(w, x, y))),
                (w, count, names) -> {
                    if (!NeoForgeRuntime.suppressNative(AgentInputContext.Kind.DROP))
                        drop.invoke(w, count, names);
                });
    }

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
    private void mineCraftProtocol$nativeMove(long window, double x, double y, CallbackInfo ci) {
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

    @Inject(method = "isMouseGrabbed", at = @At("RETURN"), cancellable = true)
    private void mineCraftProtocol$virtualGrab(CallbackInfoReturnable<Boolean> cir) {
        if (NeoForgeRuntime.routedMouseGrabbed()) cir.setReturnValue(true);
    }
}
