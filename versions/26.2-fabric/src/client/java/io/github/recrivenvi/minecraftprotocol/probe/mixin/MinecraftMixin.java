package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
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
        FabricProbeRuntime.beforeClientClose();
    }

    @Shadow private void handleKeybinds() { throw new AssertionError(); }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"))
    private void minecraftProtocolProbe$virtualKeymappingConsumption(Minecraft client) {
        if (FabricProbeRuntime.isAgentControlActive()) {
            FabricProbeRuntime.ensureExclusiveOwnerState();
            AgentInputContext.routed(this::handleKeybinds);
        }
        else this.handleKeybinds();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void minecraftProtocolProbe$tick(CallbackInfo ci) {
        FabricProbeRuntime.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void minecraftProtocolProbe$beginContent(boolean advanceTime, CallbackInfo ci) {
        FabricProbeRuntime.beginContentFrame();
    }

    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"))
    private void minecraftProtocolProbe$finalContent(boolean advanceTime, CallbackInfo ci) {
        FabricProbeRuntime.beforePresent();
    }

    @Inject(method = "isWindowActive", at = @At("RETURN"), cancellable = true)
    private void minecraftProtocolProbe$virtualFocus(CallbackInfoReturnable<Boolean> cir) {
        if (FabricProbeRuntime.routedWindowActive()) cir.setReturnValue(true);
    }
}
