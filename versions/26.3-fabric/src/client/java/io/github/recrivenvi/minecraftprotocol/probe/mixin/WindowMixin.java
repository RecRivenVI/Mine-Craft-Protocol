package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import org.lwjgl.sdl.SDL_Surface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
abstract class WindowMixin {
    @ModifyVariable(
            method = "selectCursor(Lcom/mojang/blaze3d/platform/cursor/CursorType;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private CursorType minecraftProtocolProbe$hostCursor(CursorType requested) {
        return minecraftProtocolProbe$hostCursorShape(
                requested, FabricProbeRuntime.isAgentControlActive());
    }

    @Unique
    private static CursorType minecraftProtocolProbe$hostCursorShape(
            CursorType requested, boolean takeover) {
        return takeover ? CursorType.DEFAULT : requested;
    }

    @Redirect(
            method = "setTitle",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/lwjgl/sdl/SDLVideo;SDL_SetWindowTitle(JLjava/lang/CharSequence;)Z",
                            remap = false))
    private boolean minecraftProtocolProbe$title(long window, CharSequence title) {
        return FabricProbeRuntime.onVanillaWindowTitle(window, title);
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
