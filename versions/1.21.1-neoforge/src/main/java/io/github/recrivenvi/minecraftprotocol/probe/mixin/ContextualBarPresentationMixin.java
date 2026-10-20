package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class ContextualBarPresentationMixin {
    @Redirect(
            method = {"renderExperienceBar", "renderJumpMeter"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void minecraftProtocolProbe$background(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        graphics.blitSprite(sprite, x, y, width, height);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.contextualSprite(
                    sprite.getPath().startsWith("hud/experience") ? "experience" : "jump",
                    sprite.toString(),
                    x,
                    y,
                    width,
                    height,
                    0,
                    0xFFFFFFFF);
    }

    @Redirect(
            method = {"renderExperienceBar", "renderJumpMeter"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"))
    private void minecraftProtocolProbe$progress(
            GuiGraphics graphics,
            ResourceLocation sprite,
            int textureWidth,
            int textureHeight,
            int u,
            int v,
            int x,
            int y,
            int width,
            int height) {
        graphics.blitSprite(sprite, textureWidth, textureHeight, u, v, x, y, width, height);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.contextualSprite(
                    sprite.getPath().startsWith("hud/experience") ? "experience" : "jump",
                    sprite.toString(),
                    x,
                    y,
                    width,
                    height,
                    textureWidth,
                    0xFFFFFFFF);
    }
}
