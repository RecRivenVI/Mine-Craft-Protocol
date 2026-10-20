package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Starts the isolated test window without requesting host focus; never changes an existing window.
 */
@Mixin(Window.class)
public abstract class BackgroundWindowProbeMixin {
    @Shadow @org.spongepowered.asm.mixin.Final private long handle;

    @ModifyArg(
            method = "createWindow",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"),
            index = 3)
    private long minecraftProtocolProbe$backgroundCreation(long flags) {
        return "true".equals(System.getenv("MCP_VALIDATION_BACKGROUND_WINDOW"))
                ? flags | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE
                : flags;
    }

    @Inject(method = "isFocused", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeFocus(CallbackInfoReturnable<Boolean> callback) {
        if (!"true".equals(System.getenv("MCP_VALIDATION_BACKGROUND_WINDOW"))) return;
        // A never-focused SDL window need not emit FOCUS_LOST. Read native flags
        // rather than pretending the game's initially-true cache is an OS event.
        callback.setReturnValue(
                (SDLVideo.SDL_GetWindowFlags(this.handle) & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0);
    }
}
