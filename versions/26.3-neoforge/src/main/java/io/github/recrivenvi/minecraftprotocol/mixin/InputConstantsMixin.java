package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block both SDL capture/warp entry points before they can affect the host pointer. */
@Mixin(InputConstants.class)
abstract class InputConstantsMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private static void mineCraftProtocol$logicalCapture(
            Window window, double x, double y, CallbackInfo ci) {
        if (NeoForgeRuntime.onGameCursorRequest(window.handle(), true, false, x, y)) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private static void mineCraftProtocol$logicalRelease(
            Window window, double x, double y, CallbackInfo ci) {
        if (NeoForgeRuntime.onGameCursorRequest(window.handle(), false, true, x, y)) ci.cancel();
    }

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void mineCraftProtocol$virtualPolling(
            int key, CallbackInfoReturnable<Boolean> cir) {
        if (NeoForgeRuntime.isAgentControlActive())
            cir.setReturnValue(NeoForgeRuntime.isAgentKeyDown(key));
    }
}
