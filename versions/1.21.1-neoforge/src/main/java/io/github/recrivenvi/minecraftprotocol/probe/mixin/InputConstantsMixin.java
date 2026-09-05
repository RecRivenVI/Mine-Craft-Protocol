package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standard Minecraft polling and cursor mode entry points; no OS-wide hook. */
@Mixin(InputConstants.class)
abstract class InputConstantsMixin {
    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true)
    private static void minecraftProtocolProbe$noHostWarp(long window, int mode, double x, double y, CallbackInfo ci) {
        if (NeoForgeProbeRuntime.isAgentControlActive()) ci.cancel();
    }
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void minecraftProtocolProbe$virtualPolling(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (NeoForgeProbeRuntime.isAgentControlActive()) cir.setReturnValue(NeoForgeProbeRuntime.isAgentKeyDown(key));
    }
}
