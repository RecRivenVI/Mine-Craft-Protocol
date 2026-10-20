package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.core.UiToastDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class ToastResidentPresentationMixin {
    @Shadow private float visiblePortion;
    @Shadow private Toast.Visibility visibility;

    @Redirect(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/toasts/Toast;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V"))
    private void minecraftProtocolProbe$residentDraw(
            Toast toast, GuiGraphicsExtractor graphics, Font font, long elapsed) {
        if (toast instanceof UiToastDraw.Source source) {
            Minecraft client = Minecraft.getInstance();
            source.minecraftProtocolProbe$toastDraw()
                    .capture(
                            graphics,
                            toast,
                            client.level,
                            client.getCameraEntity(),
                            client.screen,
                            graphics.guiWidth(),
                            graphics.guiHeight(),
                            graphics.pose().m20(),
                            graphics.pose().m21(),
                            visiblePortion,
                            visibility == Toast.Visibility.HIDE,
                            () -> toast.extractRenderState(graphics, font, elapsed));
        } else toast.extractRenderState(graphics, font, elapsed);
    }
}
