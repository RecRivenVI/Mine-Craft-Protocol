package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.ForgeProbeRuntime;
import org.lwjgl.glfw.GLFWImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
abstract class WindowMixin {
    @Redirect(
            method = "setTitle",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/lwjgl/glfw/GLFW;glfwSetWindowTitle(JLjava/lang/CharSequence;)V",
                            remap = false))
    private void minecraftProtocolProbe$title(long window, CharSequence title) {
        ForgeProbeRuntime.onVanillaWindowTitle(window, title);
    }

    @Redirect(
            method = "setIcon",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/lwjgl/glfw/GLFW;glfwSetWindowIcon(JLorg/lwjgl/glfw/GLFWImage$Buffer;)V",
                            remap = false))
    private void minecraftProtocolProbe$rememberActualIcons(long window, GLFWImage.Buffer icons) {
        ForgeProbeRuntime.onVanillaWindowIcon(window, icons);
    }

    @Inject(method = "onFocus", at = @At("HEAD"))
    private void minecraftProtocolProbe$focus(long window, boolean focused, CallbackInfo ci) {
        ForgeProbeRuntime.onHostFocus(focused);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void minecraftProtocolProbe$restoreBeforeDestroy(CallbackInfo ci) {
        ForgeProbeRuntime.beforeWindowClose();
    }
}
