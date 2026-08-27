package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block both SDL capture/warp entry points before they can affect the host pointer. */
@Mixin(InputConstants.class)
abstract class InputConstantsMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private static void minecraftProtocolProbe$logicalCapture(
            Window window, double x, double y, CallbackInfo ci) {
        if (FabricProbeRuntime.onGameCursorRequest(window.handle(), true, false, x, y)) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private static void minecraftProtocolProbe$logicalRelease(
            Window window, double x, double y, CallbackInfo ci) {
        if (FabricProbeRuntime.onGameCursorRequest(window.handle(), false, true, x, y)) ci.cancel();
    }

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void minecraftProtocolProbe$virtualPolling(
            int key, CallbackInfoReturnable<Boolean> cir) {
        if (FabricProbeRuntime.isAgentControlActive())
            cir.setReturnValue(FabricProbeRuntime.isAgentKeyDown(key));
    }
}
