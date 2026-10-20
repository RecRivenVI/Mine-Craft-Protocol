package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectsInventoryPresentationMixin {
    @Redirect(
            method = "renderLabels",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int minecraftProtocolProbe$text(
            GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        UiNativeText.component("inventory_effect", graphics, font, text, x, y, color);
        return result;
    }
}
