package io.github.recrivenvi.minecraftprotocol.probe.mixin;

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
    @Unique private final UiHudIcons minecraftProtocolProbe$vitals = new UiHudIcons();

    @Inject(method = "render", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        minecraftProtocolProbe$vitals.begin(
                client.level,
                client.getCameraEntity(),
                client.screen,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void minecraftProtocolProbe$finish(CallbackInfo ci) {
        minecraftProtocolProbe$vitals.finish(System.nanoTime());
    }

    @Redirect(
            method = "renderHeart",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void minecraftProtocolProbe$heart(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        minecraftProtocolProbe$record(UiHudIcons.Kind.HEALTH, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderArmor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private static void minecraftProtocolProbe$armor(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        minecraftProtocolProbe$record(UiHudIcons.Kind.ARMOR, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderFood",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void minecraftProtocolProbe$food(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        minecraftProtocolProbe$record(UiHudIcons.Kind.FOOD, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderAirLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void minecraftProtocolProbe$air(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        minecraftProtocolProbe$record(UiHudIcons.Kind.AIR, sprite, x, y, w, h);
    }

    @Redirect(
            method = "renderVehicleHealth",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void minecraftProtocolProbe$mount(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        graphics.blitSprite(sprite, x, y, w, h);
        minecraftProtocolProbe$record(UiHudIcons.Kind.MOUNT_HEALTH, sprite, x, y, w, h);
    }

    @Unique
    private static void minecraftProtocolProbe$record(
            UiHudIcons.Kind kind, ResourceLocation sprite, int x, int y, int w, int h) {
        ((UiHudIcons.Source) Minecraft.getInstance().gui)
                .minecraftProtocolProbe$hudIcons()
                .observe(kind, sprite.toString(), sprite.getPath(), x, y, w, h);
    }

    @Override
    public UiHudIcons minecraftProtocolProbe$hudIcons() {
        return minecraftProtocolProbe$vitals;
    }
}
