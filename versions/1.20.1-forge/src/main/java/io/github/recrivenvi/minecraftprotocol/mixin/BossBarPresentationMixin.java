package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiPresentationSnapshot;
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
                    "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void mineCraftProtocol$atlas(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height) {
        graphics.blit(texture, x, y, u, v, width, height);
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame != null) frame.bossAtlas(texture.toString(), x, y, u, v, width, height);
    }
}
