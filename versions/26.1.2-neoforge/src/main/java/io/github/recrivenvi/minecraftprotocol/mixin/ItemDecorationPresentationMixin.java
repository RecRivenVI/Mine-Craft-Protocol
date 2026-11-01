package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemDecorationPresentationMixin {
    @Redirect(
            method = "itemBar",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V",
                            ordinal = 0))
    private void mineCraftProtocol$durabilityBackground(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            ItemStack stack,
            int itemX,
            int itemY) {
        graphics.fill(pipeline, left, top, right, bottom, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.durabilityBackground(itemX, itemY, left, top, right - left, bottom - top);
    }

    @Redirect(
            method = "itemBar",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V",
                            ordinal = 1))
    private void mineCraftProtocol$durabilityForeground(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            ItemStack stack,
            int itemX,
            int itemY) {
        graphics.fill(pipeline, left, top, right, bottom, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null) source.durabilityForeground(itemX, itemY, right - left);
    }

    @Redirect(
            method = "itemCooldown",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V"))
    private void mineCraftProtocol$cooldown(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            ItemStack stack,
            int itemX,
            int itemY) {
        graphics.fill(pipeline, left, top, right, bottom, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.cooldown(itemX, itemY, left, top, right - left, bottom - top, color);
    }
}
