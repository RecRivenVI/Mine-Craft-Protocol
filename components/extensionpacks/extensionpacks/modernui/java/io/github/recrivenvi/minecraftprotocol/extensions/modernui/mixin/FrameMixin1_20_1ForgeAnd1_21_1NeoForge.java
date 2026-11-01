package io.github.recrivenvi.minecraftprotocol.extensions.modernui.mixin;

import io.github.recrivenvi.minecraftprotocol.extensions.modernui.bridge.ModernBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "icyllis.modernui.mc.UIManager", remap = false)
public abstract class FrameMixin1_20_1ForgeAnd1_21_1NeoForge {
    @Inject(method = "onRenderTick", at = @At("TAIL"), require = 1)
    private void snapshotFrame(boolean end, CallbackInfo callback) {
        ModernBridge.renderedFrame();
    }
}
