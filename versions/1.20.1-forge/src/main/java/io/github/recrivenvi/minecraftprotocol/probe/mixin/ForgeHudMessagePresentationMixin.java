package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ForgeGui.class)
public abstract class ForgeHudMessagePresentationMixin {
    @Redirect(
            method = "renderRecordOverlay",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I",
                            remap = true))
    private int minecraftProtocolProbe$overlayMessage(
            GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        UiNativeText.sequence("overlay_message", graphics, font, text, x, y, color);
        return result;
    }
}
