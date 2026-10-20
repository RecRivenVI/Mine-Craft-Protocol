package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphics.class)
public abstract class ItemDecorationPresentationMixin {

    @Redirect(
            method =
                    "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V",
                            ordinal = 0))
    private void minecraftProtocolProbe$durabilityBackground(
            GuiGraphics graphics,
            RenderType renderType,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            Font font,
            ItemStack stack,
            int itemX,
            int itemY,
            String countLabel) {
        graphics.fill(renderType, left, top, right, bottom, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.durabilityBackground(itemX, itemY, left, top, right - left, bottom - top);
    }

    @Redirect(
            method =
                    "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V",
                            ordinal = 1))
    private void minecraftProtocolProbe$durabilityForeground(
            GuiGraphics graphics,
            RenderType renderType,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            Font font,
            ItemStack stack,
            int itemX,
            int itemY,
            String countLabel) {
        graphics.fill(renderType, left, top, right, bottom, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null) source.durabilityForeground(itemX, itemY, right - left);
    }

    @Redirect(
            method =
                    "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V",
                            ordinal = 2))
    private void minecraftProtocolProbe$cooldown(
            GuiGraphics graphics,
            RenderType renderType,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            Font font,
            ItemStack stack,
            int itemX,
            int itemY,
            String countLabel) {
        graphics.fill(renderType, left, top, right, bottom, color);
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null)
            source.cooldown(itemX, itemY, left, top, right - left, bottom - top, color);
    }
}
