package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugTextPresentationMixin {
    @Redirect(
            method = "renderLines",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int minecraftProtocolProbe$text(
            GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        int result = graphics.drawString(font, text, x, y, color, shadow);
        UiNativeText.literal("debug_text", graphics, font, text, x, y, color);
        return result;
    }
}
