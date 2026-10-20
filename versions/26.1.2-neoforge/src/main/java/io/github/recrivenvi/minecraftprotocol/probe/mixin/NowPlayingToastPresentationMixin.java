package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.core.UiToastDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NowPlayingToast.class)
public abstract class NowPlayingToastPresentationMixin implements UiToastDraw.Source {
    @Unique private final UiToastDraw minecraftProtocolProbe$draw = new UiToastDraw();

    @Redirect(
            method = "extractToast",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private static void minecraftProtocolProbe$background(
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
            method = "extractToast",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private static void minecraftProtocolProbe$text(
            GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
        UiToastDraw.text(graphics, text.getString(), color);
    }

    @Override
    public UiToastDraw minecraftProtocolProbe$toastDraw() {
        return minecraftProtocolProbe$draw;
    }
}
