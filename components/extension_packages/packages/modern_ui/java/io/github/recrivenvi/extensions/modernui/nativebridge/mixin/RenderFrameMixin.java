package io.github.recrivenvi.extensions.modernui.nativebridge.mixin;

import io.github.recrivenvi.extensions.modernui.nativebridge.ModernBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "icyllis.modernui.mc.UIManager", remap = false)
public abstract class RenderFrameMixin {
    @Inject(method = "onRenderFrame", at = @At("TAIL"), require = 1)
    private void snapshotFrame(long frameTime, int stage, CallbackInfo callback) {
        ModernBridge.renderedFrame();
    }
}
