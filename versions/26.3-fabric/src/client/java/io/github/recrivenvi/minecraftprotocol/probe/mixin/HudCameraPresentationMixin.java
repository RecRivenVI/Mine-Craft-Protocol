package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class HudCameraPresentationMixin {
    @Redirect(
            method = {"extractCrosshair", "extractItemHotbar"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void minecraftProtocolProbe$crosshair(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height) {
        graphics.blitSprite(pipeline, sprite, x, y, width, height);
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame != null) frame.crosshairSprite(sprite.toString(), x, y, width, height, 0, 0);
    }

    @Redirect(
            method = {"extractCrosshair", "extractItemHotbar"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
    private void minecraftProtocolProbe$attackProgress(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int textureWidth,
            int textureHeight,
            int u,
            int v,
            int x,
            int y,
            int width,
            int height) {
        graphics.blitSprite(
                pipeline, sprite, textureWidth, textureHeight, u, v, x, y, width, height);
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame != null)
            frame.crosshairSprite(
                    sprite.toString(), x, y, width, height, textureWidth, textureHeight);
    }

    @Redirect(
            method = {"extractTextureOverlay", "extractConfusionOverlay", "extractVignette"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIII)V"))
    private void minecraftProtocolProbe$textureOverlay(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier texture,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int color) {
        graphics.blit(
                pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
        minecraftProtocolProbe$overlay(
                graphics, "texture", texture.toString(), x, y, width, height, color);
    }

    @Redirect(
            method = "extractSpyglassOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void minecraftProtocolProbe$spyglass(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier texture,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight) {
        graphics.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        minecraftProtocolProbe$overlay(
                graphics, "spyglass", texture.toString(), x, y, width, height, 0xFFFFFFFF);
    }

    @Redirect(
            method = "extractSpyglassOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;IIIII)V"))
    private void minecraftProtocolProbe$spyglassMask(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            int left,
            int top,
            int right,
            int bottom,
            int color) {
        graphics.fill(pipeline, left, top, right, bottom, color);
        minecraftProtocolProbe$overlay(
                graphics, "spyglass_mask", "", left, top, right - left, bottom - top, color);
    }

    @Redirect(
            method = "extractPortalOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIIII)V"))
    private void minecraftProtocolProbe$portal(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            TextureAtlasSprite sprite,
            int x,
            int y,
            int width,
            int height,
            int color) {
        graphics.blitSprite(pipeline, sprite, x, y, width, height, color);
        minecraftProtocolProbe$overlay(
                graphics,
                "portal",
                sprite.contents().name().toString(),
                x,
                y,
                width,
                height,
                color);
    }

    @Redirect(
            method = "extractSleepOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void minecraftProtocolProbe$sleep(
            GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
        minecraftProtocolProbe$overlay(
                graphics, "sleep", "", left, top, right - left, bottom - top, color);
    }

    private static void minecraftProtocolProbe$overlay(
            GuiGraphicsExtractor graphics,
            String family,
            String resource,
            int x,
            int y,
            int width,
            int height,
            int color) {
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame == null) return;
        var pose = graphics.pose();
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
                pose.m20(),
                pose.m21());
    }
}
