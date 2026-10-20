package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the completed native calls; never reconstructs rows from PlayerInfo or scores. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabPresentationMixin
        implements UiPresentationSnapshot.PlayerListPresentationSource {
    @Unique
    private final UiPresentationSnapshot.RenderedPlayerList minecraftProtocolProbe$playerList =
            new UiPresentationSnapshot.RenderedPlayerList();

    @Inject(method = "render", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(CallbackInfo ci) {
        minecraftProtocolProbe$playerList.begin(Minecraft.getInstance(), this);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void minecraftProtocolProbe$finish(CallbackInfo ci) {
        minecraftProtocolProbe$playerList.finish(System.nanoTime());
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            ordinal = 0,
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int minecraftProtocolProbe$header(
            GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        var clip = UiNativeText.scissor(graphics);
        if (clip != null)
            minecraftProtocolProbe$playerList.header(
                    UiPresentationSnapshot.RenderedPlayerList.plain(text),
                    x,
                    y,
                    font.width(text),
                    font.lineHeight,
                    UiNativeText.opacity(color),
                    graphics.pose().last().pose(),
                    clip.rectangle());
        return result;
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            ordinal = 1,
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int minecraftProtocolProbe$footer(
            GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        var clip = UiNativeText.scissor(graphics);
        if (clip != null)
            minecraftProtocolProbe$playerList.footer(
                    UiPresentationSnapshot.RenderedPlayerList.plain(text),
                    x,
                    y,
                    font.width(text),
                    font.lineHeight,
                    UiNativeText.opacity(color),
                    graphics.pose().last().pose(),
                    clip.rectangle());
        return result;
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int minecraftProtocolProbe$name(
            GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        var clip = UiNativeText.scissor(graphics);
        if (clip != null)
            minecraftProtocolProbe$playerList.name(
                    UiPresentationSnapshot.RenderedPlayerList.plain(text.getVisualOrderText()),
                    x,
                    y,
                    font.width(text),
                    font.lineHeight,
                    UiNativeText.opacity(color),
                    graphics.pose().last().pose(),
                    clip.rectangle());
        return result;
    }

    @Redirect(
            method = {"renderTablistScore", "renderTablistHearts"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int minecraftProtocolProbe$score(
            GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        int result = graphics.drawString(font, text, x, y, color);
        var clip = UiNativeText.scissor(graphics);
        if (clip != null)
            minecraftProtocolProbe$playerList.score(
                    UiNativeText.plain(text),
                    x,
                    y,
                    font.width(text),
                    font.lineHeight,
                    UiNativeText.opacity(color),
                    graphics.pose().last().pose(),
                    clip.rectangle());
        return result;
    }

    @Redirect(
            method = {"renderPingIcon", "renderTablistHearts"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void minecraftProtocolProbe$atlas(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height) {
        graphics.blit(texture, x, y, u, v, width, height);
        var clip = UiNativeText.scissor(graphics);
        if (clip != null)
            minecraftProtocolProbe$playerList.atlas(
                    texture.toString(),
                    x,
                    y,
                    u,
                    v,
                    width,
                    height,
                    RenderSystem.getShaderColor()[3],
                    graphics.pose().last().pose(),
                    clip.rectangle());
    }

    @Override
    public UiPresentationSnapshot.RenderedPlayerList
            minecraftProtocolProbe$playerListPresentation() {
        return minecraftProtocolProbe$playerList;
    }
}
