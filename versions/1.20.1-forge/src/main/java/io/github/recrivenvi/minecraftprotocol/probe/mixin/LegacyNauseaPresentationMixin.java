package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class LegacyNauseaPresentationMixin {
    @Redirect(
            method = "renderConfusionOverlay",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V"))
    private void minecraftProtocolProbe$nausea(
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
        UiNativeText.nausea(graphics, texture.toString(), x, y, depth, width, height);
    }
}
