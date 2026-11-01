package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudVitalIconsMixin implements UiHudIcons.Source {
    @Unique private final UiHudIcons mineCraftProtocol$vitals = new UiHudIcons();

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mineCraftProtocol$begin(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        mineCraftProtocol$vitals.begin(
                client.level,
                client.getCameraEntity(),
                client.gui.screen(),
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void mineCraftProtocol$finish(CallbackInfo ci) {
        mineCraftProtocol$vitals.finish(System.nanoTime());
    }

    @Redirect(
            method = "extractHeart",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void mineCraftProtocol$heart(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int w,
            int h) {
        graphics.blitSprite(pipeline, sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.HEALTH, sprite, x, y, w, h);
    }

    @Redirect(
            method = "extractArmor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private static void mineCraftProtocol$armor(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int w,
            int h) {
        graphics.blitSprite(pipeline, sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.ARMOR, sprite, x, y, w, h);
    }

    @Redirect(
            method = "extractFood",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void mineCraftProtocol$food(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int w,
            int h) {
        graphics.blitSprite(pipeline, sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.FOOD, sprite, x, y, w, h);
    }

    @Redirect(
            method = "extractAirBubbles",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void mineCraftProtocol$air(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int w,
            int h) {
        graphics.blitSprite(pipeline, sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.AIR, sprite, x, y, w, h);
    }

    @Redirect(
            method = "extractVehicleHealth",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void mineCraftProtocol$mount(
            GuiGraphicsExtractor graphics,
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int w,
            int h) {
        graphics.blitSprite(pipeline, sprite, x, y, w, h);
        mineCraftProtocol$record(UiHudIcons.Kind.MOUNT_HEALTH, sprite, x, y, w, h);
    }

    @Unique
    private static void mineCraftProtocol$record(
            UiHudIcons.Kind kind, Identifier sprite, int x, int y, int w, int h) {
        ((UiHudIcons.Source) Minecraft.getInstance().gui.hud)
                .mineCraftProtocol$hudIcons()
                .observe(kind, sprite.toString(), sprite.getPath(), x, y, w, h);
    }

    @Override
    public UiHudIcons mineCraftProtocol$hudIcons() {
        return mineCraftProtocol$vitals;
    }
}
