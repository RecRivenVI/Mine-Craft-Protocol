package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import org.lwjgl.sdl.SDL_Surface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$title(String title, CallbackInfo ci) {
        FabricProbeRuntime.onVanillaWindowTitle(title);
        if (FabricProbeRuntime.isAgentControlActive()) ci.cancel();
    }

    @Redirect(
            method = "setIcon(Ljava/util/List;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/lwjgl/sdl/SDLVideo;SDL_SetWindowIcon(JLorg/lwjgl/sdl/SDL_Surface;)Z",
                            remap = false))
    private boolean minecraftProtocolProbe$rememberActualIcons(long window, SDL_Surface icon) {
        return FabricProbeRuntime.onVanillaWindowIcon(window, icon);
    }

    @Inject(method = "onFocus", at = @At("HEAD"))
    private void minecraftProtocolProbe$focus(boolean focused, CallbackInfo ci) {
        FabricProbeRuntime.onHostFocus(focused);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void minecraftProtocolProbe$restoreBeforeDestroy(CallbackInfo ci) {
        FabricProbeRuntime.beforeWindowClose();
    }
}
