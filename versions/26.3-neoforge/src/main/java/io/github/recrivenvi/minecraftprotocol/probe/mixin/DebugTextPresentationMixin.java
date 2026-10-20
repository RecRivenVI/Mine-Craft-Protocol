package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugTextPresentationMixin {
    @Redirect(
            method = "extractLines",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private void minecraftProtocolProbe$text(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int color,
            boolean shadow) {
        graphics.text(font, text, x, y, color, shadow);
        UiNativeText.literal("debug_text", graphics, font, text, x, y, color);
    }
}
