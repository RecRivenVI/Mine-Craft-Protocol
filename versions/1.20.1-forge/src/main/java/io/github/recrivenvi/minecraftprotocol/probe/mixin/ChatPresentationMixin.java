package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatComponent.class)
public abstract class ChatPresentationMixin {
    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int minecraftProtocolProbe$message(
            GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        UiNativeText.sequence(UiNativeText.chatFamily(), graphics, font, text, x, y, color);
        return result;
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int minecraftProtocolProbe$queue(
            GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        UiNativeText.component(UiNativeText.chatFamily(), graphics, font, text, x, y, color);
        return result;
    }
}
