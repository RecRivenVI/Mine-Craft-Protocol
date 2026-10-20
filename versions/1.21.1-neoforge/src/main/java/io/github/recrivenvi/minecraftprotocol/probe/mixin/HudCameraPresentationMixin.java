package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class HudCameraPresentationMixin {
    @Redirect(
            method = {"renderCrosshair", "renderItemHotbar"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void minecraftProtocolProbe$crosshair(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        graphics.blitSprite(sprite, x, y, width, height);
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame != null) frame.crosshairSprite(sprite.toString(), x, y, width, height, 0, 0);
    }

    @Redirect(
            method = {"renderCrosshair", "renderItemHotbar"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"))
    private void minecraftProtocolProbe$attackProgress(
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
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame != null)
            frame.crosshairSprite(
                    sprite.toString(), x, y, width, height, textureWidth, textureHeight);
    }

    @Redirect(
            method = {"renderTextureOverlay", "renderVignette"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V"))
    private void minecraftProtocolProbe$textureOverlay(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int depth,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight) {
        graphics.blit(texture, x, y, depth, u, v, width, height, textureWidth, textureHeight);
        minecraftProtocolProbe$overlay(
                graphics,
                "texture",
                texture.toString(),
                x,
                y,
                width,
                height,
                depth,
                minecraftProtocolProbe$shaderColor());
    }

    @Redirect(
            method = "renderSpyglassOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V"))
    private void minecraftProtocolProbe$spyglass(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int depth,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight) {
        graphics.blit(texture, x, y, depth, u, v, width, height, textureWidth, textureHeight);
        minecraftProtocolProbe$overlay(
                graphics,
                "spyglass",
                texture.toString(),
                x,
                y,
                width,
                height,
                depth,
                minecraftProtocolProbe$shaderColor());
    }

    @Redirect(
            method = "renderSpyglassOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V"))
    private void minecraftProtocolProbe$spyglassMask(
            GuiGraphics graphics,
            RenderType renderType,
            int left,
            int top,
            int right,
            int bottom,
            int depth,
            int color) {
        graphics.fill(renderType, left, top, right, bottom, depth, color);
        minecraftProtocolProbe$overlay(
                graphics, "spyglass_mask", "", left, top, right - left, bottom - top, depth, color);
    }

    @Redirect(
            method = "renderPortalOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void minecraftProtocolProbe$portal(
            GuiGraphics graphics,
            int x,
            int y,
            int depth,
            int width,
            int height,
            TextureAtlasSprite sprite) {
        graphics.blit(x, y, depth, width, height, sprite);
        minecraftProtocolProbe$overlay(
                graphics,
                "portal",
                sprite.contents().name().toString(),
                x,
                y,
                width,
                height,
                depth,
                minecraftProtocolProbe$shaderColor());
    }

    @Redirect(
            method = "renderSleepOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void minecraftProtocolProbe$sleep(
            GuiGraphics graphics,
            RenderType renderType,
            int left,
            int top,
            int right,
            int bottom,
            int color) {
        graphics.fill(renderType, left, top, right, bottom, color);
        minecraftProtocolProbe$overlay(
                graphics, "sleep", "", left, top, right - left, bottom - top, 0, color);
    }

    private static int minecraftProtocolProbe$shaderColor() {
        float alpha = RenderSystem.getShaderColor()[3];
        if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1) return 0;
        return (Math.round(alpha * 255) << 24) | 0xFFFFFF;
    }

    private static void minecraftProtocolProbe$overlay(
            GuiGraphics graphics,
            String family,
            String resource,
            int x,
            int y,
            int width,
            int height,
            int depth,
            int color) {
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame == null) return;
        var pose = graphics.pose().last().pose();
        if (pose.m03() != 0 || pose.m13() != 0 || pose.m23() != 0 || pose.m33() != 1) return;
        frame.cameraOverlay(
                family,
                resource,
                x,
                y,
                width,
                height,
                color,
                pose.m00(),
                pose.m01(),
                pose.m10(),
                pose.m11(),
                pose.m20() * depth + pose.m30(),
                pose.m21() * depth + pose.m31());
    }
}
