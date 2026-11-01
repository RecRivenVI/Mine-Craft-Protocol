package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.FabricRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void mineCraftProtocol$inputContext(CallbackInfo ci) {
        FabricRuntime.beforeClientInput((Minecraft) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mineCraftProtocol$drainBeforeLoaderClose(CallbackInfo ci) {
        FabricRuntime.beforeClientClose();
    }

    @Shadow
    private void handleKeybinds() {
        throw new AssertionError();
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"))
    private void mineCraftProtocol$virtualKeymappingConsumption(Minecraft client) {
        if (FabricRuntime.isAgentControlActive()) {
            FabricRuntime.ensureExclusiveOwnerState();
            AgentInputContext.routed(this::handleKeybinds);
        } else this.handleKeybinds();
    }

    @Redirect(
            method = "runTick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/MouseHandler;handleAccumulatedMovement()V"))
    private void mineCraftProtocol$virtualMouseFrameConsumption(MouseHandler mouse) {
        if (FabricRuntime.isAgentControlActive()) {
            FabricRuntime.ensureExclusiveOwnerState();
            AgentInputContext.routed(mouse::handleAccumulatedMovement);
        } else mouse.handleAccumulatedMovement();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mineCraftProtocol$tick(CallbackInfo ci) {
        FabricRuntime.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void mineCraftProtocol$beginContent(boolean advanceTime, CallbackInfo ci) {
        FabricRuntime.beginContentFrame();
    }

    @Inject(
            method = "renderFrame",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void mineCraftProtocol$finalContent(boolean advanceTime, CallbackInfo ci) {
        FabricRuntime.beforePresent();
    }

    @Inject(method = "isWindowActive", at = @At("RETURN"), cancellable = true)
    private void mineCraftProtocol$virtualFocus(CallbackInfoReturnable<Boolean> cir) {
        if (FabricRuntime.routedWindowActive()) cir.setReturnValue(true);
    }
}
