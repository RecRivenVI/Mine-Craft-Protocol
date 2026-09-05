package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.ForgeProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void minecraftProtocolProbe$drainBeforeLoaderClose(CallbackInfo ci) {
        ForgeProbeRuntime.beforeClientClose();
    }

    @Shadow private void handleKeybinds() { throw new AssertionError(); }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"))
    private void minecraftProtocolProbe$virtualKeymappingConsumption(Minecraft client) {
        if (ForgeProbeRuntime.isAgentControlActive()) AgentInputContext.routed(this::handleKeybinds);
        else this.handleKeybinds();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void minecraftProtocolProbe$tick(CallbackInfo ci) {
        ForgeProbeRuntime.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void minecraftProtocolProbe$beginContent(boolean advanceTime, CallbackInfo ci) {
        ForgeProbeRuntime.beginContentFrame();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V"))
    private void minecraftProtocolProbe$finalContent(boolean advanceTime, CallbackInfo ci) {
        ForgeProbeRuntime.beforePresent();
    }

    @Inject(method = "isWindowActive", at = @At("RETURN"), cancellable = true)
    private void minecraftProtocolProbe$virtualFocus(CallbackInfoReturnable<Boolean> cir) {
        if (ForgeProbeRuntime.routedWindowActive()) cir.setReturnValue(true);
    }
}
