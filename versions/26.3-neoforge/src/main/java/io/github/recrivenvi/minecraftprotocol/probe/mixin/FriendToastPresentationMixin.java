package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.core.UiToastDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.FriendToast;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FriendToast.class)
public abstract class FriendToastPresentationMixin implements UiToastDraw.Source {
    @Unique private final UiToastDraw minecraftProtocolProbe$draw = new UiToastDraw();

    @Redirect(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void minecraftProtocolProbe$background(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height) {
        graphics.blitSprite(pipeline, sprite, x, y, width, height);
        UiToastDraw.background(graphics, x, y, width, height);
    }

    @Redirect(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"))
    private void minecraftProtocolProbe$text(
            GuiGraphicsExtractor graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color,
            boolean shadow) {
        graphics.text(font, text, x, y, color, shadow);
        StringBuilder label = new StringBuilder();
        text.accept(
                (index, style, codepoint) -> {
                    if (label.length() >= 2048) return false;
                    label.appendCodePoint(codepoint);
                    return true;
                });
        UiToastDraw.text(graphics, label.toString(), color);
    }

    @Override
    public UiToastDraw minecraftProtocolProbe$toastDraw() {
        return minecraftProtocolProbe$draw;
    }
}
