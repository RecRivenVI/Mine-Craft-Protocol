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
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source == null
                || !texture.toString().equals("minecraft:textures/gui/icons.png")
                || u != 0
                || height != 5) return;
        String family, region;
        int barWidth;
        switch (v) {
            case 64 -> {
                family = "experience";
                region = "experience_bar_background";
                barWidth = 0;
            }
            case 69 -> {
                family = "experience";
                region = "experience_bar_progress";
                barWidth = 182;
            }
            case 84 -> {
                family = "jump";
                region = "jump_bar_background";
                barWidth = 0;
            }
            case 89 -> {
                family = "jump";
                region = "jump_bar_progress";
                barWidth = 182;
            }
            case 74 -> {
                family = "jump";
                region = "jump_bar_cooldown";
                barWidth = 0;
            }
            default -> {
                return;
            }
        }
        source.legacyContextualSprite(
                family, "hud/" + region, texture.toString(), x, y, u, v, width, height, barWidth);
    }
}
