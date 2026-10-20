package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive observations after the original native draw, without reading PlayerInfo or scores. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabPresentationMixin
        implements UiPresentationSnapshot.PlayerListPresentationSource {
    @Unique
    private final UiPresentationSnapshot.RenderedPlayerList minecraftProtocolProbe$playerList =
            new UiPresentationSnapshot.RenderedPlayerList();

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(CallbackInfo ci) {
        minecraftProtocolProbe$playerList.begin(Minecraft.getInstance(), this);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void minecraftProtocolProbe$finish(CallbackInfo ci) {
        minecraftProtocolProbe$playerList.finish(System.nanoTime());
    }

    @Redirect(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            ordinal = 0,
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
    private void minecraftProtocolProbe$header(
            GuiGraphicsExtractor graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color) {
        graphics.text(font, text, x, y, color);
        minecraftProtocolProbe$playerList.header(
                UiPresentationSnapshot.RenderedPlayerList.plain(text),
                x,
                y,
                font.width(text),
                font.lineHeight,
                (color >>> 24) / 255.0F,
                graphics.pose(),
                graphics.peekScissorStack());
    }

    @Redirect(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            ordinal = 1,
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
    private void minecraftProtocolProbe$footer(
            GuiGraphicsExtractor graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color) {
        graphics.text(font, text, x, y, color);
        minecraftProtocolProbe$playerList.footer(
                UiPresentationSnapshot.RenderedPlayerList.plain(text),
                x,
                y,
                font.width(text),
                font.lineHeight,
                (color >>> 24) / 255.0F,
                graphics.pose(),
                graphics.peekScissorStack());
    }

    @Redirect(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void minecraftProtocolProbe$name(
            GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
        minecraftProtocolProbe$playerList.name(
                UiPresentationSnapshot.RenderedPlayerList.plain(text.getVisualOrderText()),
                x,
                y,
                font.width(text),
                font.lineHeight,
                (color >>> 24) / 255.0F,
                graphics.pose(),
                graphics.peekScissorStack());
    }

    @Redirect(
            method = {"extractTablistScore", "extractTablistHearts"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void minecraftProtocolProbe$score(
            GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
        minecraftProtocolProbe$playerList.score(
                UiPresentationSnapshot.RenderedPlayerList.plain(text.getVisualOrderText()),
                x,
                y,
                font.width(text),
                font.lineHeight,
                (color >>> 24) / 255.0F,
                graphics.pose(),
                graphics.peekScissorStack());
    }

    @Redirect(
            method = {"extractPingIcon", "extractTablistHearts"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void minecraftProtocolProbe$sprite(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height) {
        graphics.blitSprite(pipeline, sprite, x, y, width, height);
        minecraftProtocolProbe$playerList.sprite(
                sprite.toString(),
                x,
                y,
                width,
                height,
                graphics.pose(),
                graphics.peekScissorStack());
    }

    @Override
    public UiPresentationSnapshot.RenderedPlayerList
            minecraftProtocolProbe$playerListPresentation() {
        return minecraftProtocolProbe$playerList;
    }
}
