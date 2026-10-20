package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ForgeGui.class)
public abstract class DebugTextPresentationMixin {
    @Redirect(
            method = "renderHUDText",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
                            remap = true))
    private int minecraftProtocolProbe$text(
            GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        int result = graphics.drawString(font, text, x, y, color, shadow);
        Minecraft client = Minecraft.getInstance();
        if (client.options.renderDebug)
            UiNativeText.literal("debug_text", graphics, font, text, x, y, color);
        else if (client.isDemo()) UiNativeText.literal("demo", graphics, font, text, x, y, color);
        return result;
    }
}
