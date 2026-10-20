package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BossHealthOverlay.class)
public abstract class BossBarPresentationMixin {
    @Redirect(
            method =
                    "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
    private void minecraftProtocolProbe$sprite(
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
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame != null) frame.bossSprite(sprite.toString(), x, y, width, height);
    }
}
