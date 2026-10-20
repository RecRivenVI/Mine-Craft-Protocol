package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiSavingIndicatorFixture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class UiSavingTextProbeMixin {
    @Inject(method = "textWithBackdrop", at = @At("RETURN"))
    private void minecraftProtocolProbe$observeActualSavingText(
            Font font, Component text, int x, int y, int width, int color, CallbackInfo callback) {
        UiSavingIndicatorFixture.observeNativeText(text, color, x, y, width);
    }
}
