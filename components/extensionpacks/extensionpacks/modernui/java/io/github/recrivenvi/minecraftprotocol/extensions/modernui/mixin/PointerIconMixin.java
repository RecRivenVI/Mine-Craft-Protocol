package io.github.recrivenvi.minecraftprotocol.extensions.modernui.mixin;

import io.github.recrivenvi.minecraftprotocol.extensions.modernui.bridge.ModernBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Framework-selected system cursor images remain default while the shared input is owned. */
@Pseudo
@Mixin(targets = "icyllis.modernui.view.PointerIcon", remap = false)
public abstract class PointerIconMixin {
    @Inject(method = "getHandle", at = @At("RETURN"), cancellable = true)
    private void minecraftProtocol$hostCursor(CallbackInfoReturnable<Long> result) {
        result.setReturnValue(ModernBridge.hostCursorHandle(result.getReturnValue()));
    }
}
