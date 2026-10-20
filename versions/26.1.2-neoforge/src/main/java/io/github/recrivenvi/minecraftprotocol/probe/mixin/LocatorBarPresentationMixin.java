package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocatorBarRenderer.class)
public abstract class LocatorBarPresentationMixin {
    @Redirect(
            method = {"extractBackground", "lambda$extractRenderState$1"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void minecraftProtocolProbe$backgroundOrArrow(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height) {
        graphics.blitSprite(pipeline, sprite, x, y, width, height);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.contextualSprite(
                    "locator", sprite.toString(), x, y, width, height, 0, 0xFFFFFFFF);
    }

    @Redirect(
            method = "lambda$extractRenderState$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
    private void minecraftProtocolProbe$marker(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height,
            int color) {
        graphics.blitSprite(pipeline, sprite, x, y, width, height, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.contextualSprite("locator", sprite.toString(), x, y, width, height, 0, color);
    }
}
