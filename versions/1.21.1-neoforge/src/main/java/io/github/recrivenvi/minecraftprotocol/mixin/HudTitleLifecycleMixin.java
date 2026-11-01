package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.core.UiTitleLifecycleSource;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Invalidates title receipts on native setters, never on ordinary lifetime ticks. */
@Mixin(Gui.class)
public abstract class HudTitleLifecycleMixin implements UiTitleLifecycleSource {
    @Unique private long mineCraftProtocol$titleLifecycleGeneration;

    @Inject(
            method = {
                "setTitle(Lnet/minecraft/network/chat/Component;)V",
                "setSubtitle(Lnet/minecraft/network/chat/Component;)V",
                "setTimes(III)V",
                "resetTitleTimes()V",
                "clear()V"
            },
            at = @At("HEAD"))
    private void mineCraftProtocol$titleChanged(CallbackInfo callback) {
        mineCraftProtocol$titleLifecycleGeneration++;
    }

    @Override
    public long mineCraftProtocol$titleLifecycleGeneration() {
        return mineCraftProtocol$titleLifecycleGeneration;
    }
}
