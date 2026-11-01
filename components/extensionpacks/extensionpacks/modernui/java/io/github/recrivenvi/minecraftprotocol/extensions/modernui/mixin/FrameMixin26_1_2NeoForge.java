package io.github.recrivenvi.minecraftprotocol.extensions.modernui.mixin;

import io.github.recrivenvi.minecraftprotocol.extensions.modernui.bridge.ModernBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "icyllis.modernui.mc.UIManager", remap = false)
public abstract class FrameMixin26_1_2NeoForge {
    @Inject(method = "onRenderFrame", at = @At("TAIL"), require = 1)
    private void snapshotFrame(long frameTime, int stage, CallbackInfo callback) {
        ModernBridge.renderedFrame();
    }
}
