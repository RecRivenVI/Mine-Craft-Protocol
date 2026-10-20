package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class HudMessagePresentationMixin {
    @Redirect(
            method = "renderOverlayMessage",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
    private int minecraftProtocolProbe$overlayMessage(
            GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        int result = graphics.drawStringWithBackdrop(font, text, x, y, width, color);
        UiNativeText.component("overlay_message", graphics, font, text, x, y, color);
        return result;
    }

    @Redirect(
            method = "renderSavingIndicator",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
    private int minecraftProtocolProbe$saving(
            GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        int result = graphics.drawStringWithBackdrop(font, text, x, y, width, color);
        UiNativeText.component("saving_indicator", graphics, font, text, x, y, color);
        return result;
    }

    @Redirect(
            method = "renderDemoOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
    private int minecraftProtocolProbe$demo(
            GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        int result = graphics.drawStringWithBackdrop(font, text, x, y, width, color);
        UiNativeText.component("demo", graphics, font, text, x, y, color);
        return result;
    }
}
