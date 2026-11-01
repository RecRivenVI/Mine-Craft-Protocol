package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudVitalIconsMixin implements UiHudIcons.Source {
    @Unique private final UiHudIcons mineCraftProtocol$vitals = new UiHudIcons();

    @Inject(method = "render", at = @At("HEAD"))
    private void mineCraftProtocol$begin(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        mineCraftProtocol$vitals.begin(
                client.level,
                client.getCameraEntity(),
                client.screen,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mineCraftProtocol$finish(CallbackInfo ci) {
        mineCraftProtocol$vitals.finish(System.nanoTime());
    }

    @Redirect(
            method = "renderHeart",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void mineCraftProtocol$heart(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.HEALTH, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderArmor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private static void mineCraftProtocol$armor(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.ARMOR, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderFood",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void mineCraftProtocol$food(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.FOOD, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderAirLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void mineCraftProtocol$air(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.AIR, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderVehicleHealth",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void mineCraftProtocol$mount(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.MOUNT_HEALTH, sprite, x, y, w, h);
    }

    @Unique
    private static void mineCraftProtocol$record(
            UiHudIcons.Kind kind, ResourceLocation sprite, int x, int y, int w, int h) {
        ((UiHudIcons.Source) Minecraft.getInstance().gui)
                .mineCraftProtocol$hudIcons()
                .observe(kind, sprite.toString(), sprite.getPath(), x, y, w, h);
    }

    @Override
    public UiHudIcons mineCraftProtocol$hudIcons() {
        return mineCraftProtocol$vitals;
    }
}
