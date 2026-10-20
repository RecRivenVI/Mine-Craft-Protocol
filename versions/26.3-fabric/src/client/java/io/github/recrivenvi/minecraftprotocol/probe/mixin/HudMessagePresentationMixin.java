package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class HudMessagePresentationMixin {
    @Redirect(
            method = "extractOverlayMessage",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void minecraftProtocolProbe$overlayMessage(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        graphics.textWithBackdrop(font, text, x, y, width, color);
        UiNativeText.component("overlay_message", graphics, font, text, x, y, color);
    }

    @Redirect(
            method = "extractSavingIndicator",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void minecraftProtocolProbe$saving(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        graphics.textWithBackdrop(font, text, x, y, width, color);
        UiNativeText.component("saving_indicator", graphics, font, text, x, y, color);
    }

    @Redirect(
            method = "extractDemoOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void minecraftProtocolProbe$demo(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        graphics.textWithBackdrop(font, text, x, y, width, color);
        UiNativeText.component("demo", graphics, font, text, x, y, color);
    }
}
