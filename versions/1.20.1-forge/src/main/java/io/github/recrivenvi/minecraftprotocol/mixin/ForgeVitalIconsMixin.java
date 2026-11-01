package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.HudVitalAtlas;
import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ForgeGui.class)
public abstract class ForgeVitalIconsMixin {
    @Redirect(
            method = "renderArmor",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
                            remap = true))
    private void mineCraftProtocol$armor(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int w,
            int h) {
        graphics.blit(texture, x, y, u, v, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.ARMOR, texture, x, y, u, v, w, h);
    }

    @Redirect(
            method = "renderFood",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
                            remap = true))
    private void mineCraftProtocol$food(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int w,
            int h) {
        graphics.blit(texture, x, y, u, v, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.FOOD, texture, x, y, u, v, w, h);
    }

    @Redirect(
            method = "renderAir",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
                            remap = true))
    private void mineCraftProtocol$air(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int w,
            int h) {
        graphics.blit(texture, x, y, u, v, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.AIR, texture, x, y, u, v, w, h);
    }

    @Redirect(
            method = "renderHealthMount",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
                            remap = true))
    private void mineCraftProtocol$mount(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int w,
            int h) {
        graphics.blit(texture, x, y, u, v, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.MOUNT_HEALTH, texture, x, y, u, v, w, h);
    }

    @Unique
    private static void mineCraftProtocol$record(
            UiHudIcons.Kind kind,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int w,
            int h) {
        String label = HudVitalAtlas.label(kind, u, v);
        if (label != null && texture.toString().equals("minecraft:textures/gui/icons.png"))
            ((UiHudIcons.Source) Minecraft.getInstance().gui)
                    .mineCraftProtocol$hudIcons()
                    .observe(kind, texture.toString(), label, x, y, w, h);
    }
}
