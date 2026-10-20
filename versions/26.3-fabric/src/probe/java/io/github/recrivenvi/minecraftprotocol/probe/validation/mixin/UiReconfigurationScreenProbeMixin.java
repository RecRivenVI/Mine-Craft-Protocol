package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiReconfigurationFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerReconfigScreen.class)
public abstract class UiReconfigurationScreenProbeMixin {
    @Inject(
            method = {"init", "tick"},
            at = @At("TAIL"))
    private void minecraftProtocolProbe$observeNativeDisconnectDelay(CallbackInfo callback) {
        UiReconfigurationFixture.observeNativeScreen((ServerReconfigScreen) (Object) this);
    }

    @Inject(method = "lambda$init$0", at = @At("TAIL"))
    private void minecraftProtocolProbe$observeNativeDisconnect(
            Button button, CallbackInfo callback) {
        UiReconfigurationFixture.observeNativeDisconnect((ServerReconfigScreen) (Object) this);
    }
}
