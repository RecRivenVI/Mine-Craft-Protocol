package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.ForgeRuntime;
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
        ForgeRuntime.beforeClientInput((Minecraft) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mineCraftProtocol$drainBeforeLoaderClose(CallbackInfo ci) {
        ForgeRuntime.beforeClientClose();
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
        if (ForgeRuntime.isAgentControlActive()) {
            ForgeRuntime.ensureExclusiveOwnerState();
            AgentInputContext.routed(this::handleKeybinds);
        } else this.handleKeybinds();
    }

    @Redirect(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer()V"))
    private void mineCraftProtocol$virtualMouseFrameConsumption(MouseHandler mouse) {
        if (ForgeRuntime.isAgentControlActive()) {
            ForgeRuntime.ensureExclusiveOwnerState();
            AgentInputContext.routed(mouse::turnPlayer);
        } else mouse.turnPlayer();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mineCraftProtocol$tick(CallbackInfo ci) {
        ForgeRuntime.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void mineCraftProtocol$beginContent(boolean advanceTime, CallbackInfo ci) {
        ForgeRuntime.beginContentFrame();
    }

    @Inject(
            method = "runTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V"))
    private void mineCraftProtocol$finalContent(boolean advanceTime, CallbackInfo ci) {
        ForgeRuntime.beforePresent();
    }

    @Inject(method = "isWindowActive", at = @At("RETURN"), cancellable = true)
    private void mineCraftProtocol$virtualFocus(CallbackInfoReturnable<Boolean> cir) {
        if (ForgeRuntime.routedWindowActive()) cir.setReturnValue(true);
    }
}
