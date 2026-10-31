package io.github.recrivenvi.extensions.platformprobe.hooks.mixin;

import io.github.recrivenvi.extensions.platformprobe.hooks.state.NativeCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.MinecraftServer", remap = false)
public abstract class ServerTickHookMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void countActualExecution(CallbackInfo callback) {
        NativeCounter.hit();
    }
}
