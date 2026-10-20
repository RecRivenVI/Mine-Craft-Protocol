package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ExperienceBar.class, JumpableVehicleBar.class})
public abstract class ContextualBarPresentationMixin {
    @Redirect(
            method = "extractBackground",
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
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.contextualSprite(
                    (Object) this instanceof ExperienceBar ? "experience" : "jump",
                    sprite.toString(),
                    x,
                    y,
                    width,
                    height,
                    0,
                    0xFFFFFFFF);
    }

    @Redirect(
            method = "extractBackground",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
    private void minecraftProtocolProbe$progress(
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
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.contextualSprite(
                    (Object) this instanceof ExperienceBar ? "experience" : "jump",
                    sprite.toString(),
                    x,
                    y,
                    width,
                    height,
                    textureWidth,
                    0xFFFFFFFF);
    }
}
