package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BossHealthOverlay.class)
public abstract class BossBarPresentationMixin {
    @Redirect(
            method =
                    "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/ResourceLocation;[Lnet/minecraft/resources/ResourceLocation;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"))
    private void minecraftProtocolProbe$sprite(
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
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame != null) frame.bossSprite(sprite.toString(), x, y, width, height);
    }
}
