package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiSavingIndicatorFixture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphics.class)
public abstract class UiSavingTextProbeMixin {
    @Inject(
            method =
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            at = @At("RETURN"))
    private void minecraftProtocolProbe$observeActualSavingText(
            Font font,
            Component text,
            int x,
            int y,
            int color,
            boolean shadow,
            CallbackInfoReturnable<Integer> callback) {
        UiSavingIndicatorFixture.observeNativeText(text, color, x, y, font.width(text));
    }
}
