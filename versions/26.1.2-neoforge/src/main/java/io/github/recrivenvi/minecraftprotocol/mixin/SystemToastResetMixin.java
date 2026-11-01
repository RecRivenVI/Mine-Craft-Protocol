package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.core.UiToastResetSource;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts native reset attempts without changing the Toast or its lifetime processing. */
@Mixin(SystemToast.class)
public abstract class SystemToastResetMixin implements UiToastResetSource {
    @Unique private long mineCraftProtocol$resetGeneration;

    @Inject(
            method =
                    "reset(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"))
    private void mineCraftProtocol$reset(
            Component title, Component message, CallbackInfo callback) {
        mineCraftProtocol$resetGeneration++;
    }

    @Override
    public long mineCraftProtocol$resetGeneration() {
        return mineCraftProtocol$resetGeneration;
    }
}
