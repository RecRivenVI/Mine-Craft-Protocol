package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standard Minecraft polling and cursor mode entry points; no OS-wide hook. */
@Mixin(InputConstants.class)
abstract class InputConstantsMixin {
    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true)
    private static void mineCraftProtocol$noHostWarp(
            Window window, int mode, double x, double y, CallbackInfo ci) {
        if (NeoForgeRuntime.onGameCursorRequest(
                window.handle(),
                mode == org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED,
                mode != org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED
                        && mode != org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN,
                x,
                y)) ci.cancel();
    }

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void mineCraftProtocol$virtualPolling(
            Window window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (NeoForgeRuntime.isAgentControlActive())
            cir.setReturnValue(NeoForgeRuntime.isAgentKeyDown(key));
    }
}
