package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiSavingIndicatorFixture;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class UiSavingHudScopeProbeMixin {
    @Inject(method = "renderSavingIndicator", at = @At("HEAD"))
    private void minecraftProtocolProbe$beginNativeSavingDraw(CallbackInfo callback) {
        UiSavingIndicatorFixture.beginSavingExtraction();
    }

    @Inject(method = "renderSavingIndicator", at = @At("RETURN"))
    private void minecraftProtocolProbe$endNativeSavingDraw(CallbackInfo callback) {
        UiSavingIndicatorFixture.endSavingExtraction();
    }
}
