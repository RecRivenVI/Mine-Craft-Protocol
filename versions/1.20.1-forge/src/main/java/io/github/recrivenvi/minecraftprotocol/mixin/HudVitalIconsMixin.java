package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.HudVitalAtlas;
import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class HudVitalIconsMixin implements UiHudIcons.Source {
    @Unique private final UiHudIcons mineCraftProtocol$vitals = new UiHudIcons();

    @Redirect(
            method = "renderHeart",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void mineCraftProtocol$heart(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int w,
            int h) {
        graphics.blit(texture, x, y, u, v, w, h);
        String label = HudVitalAtlas.label(UiHudIcons.Kind.HEALTH, u, v);
        if (label != null && texture.toString().equals("minecraft:textures/gui/icons.png"))
            ((UiHudIcons.Source) Minecraft.getInstance().gui)
                    .mineCraftProtocol$hudIcons()
                    .observe(UiHudIcons.Kind.HEALTH, texture.toString(), label, x, y, w, h);
    }

    @Override
    public UiHudIcons mineCraftProtocol$hudIcons() {
        return mineCraftProtocol$vitals;
    }
}
