package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SubtitleOverlay.class)
public abstract class SubtitlePresentationMixin {
    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int mineCraftProtocol$direction(
            GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        UiNativeText.literal("subtitle", graphics, font, text, x, y, color);
        return result;
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int mineCraftProtocol$text(
            GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        UiNativeText.component("subtitle", graphics, font, text, x, y, color);
        return result;
    }
}
