package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudPresentationMixin implements UiPresentationSnapshot.HudPresentationSource {
    @Unique
    private final UiPresentationSnapshot.RenderedHud mineCraftProtocol$presentation =
            new UiPresentationSnapshot.RenderedHud();

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mineCraftProtocol$begin(CallbackInfo ci) {
        mineCraftProtocol$presentation.begin(Minecraft.getInstance());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void mineCraftProtocol$finish(CallbackInfo ci) {
        mineCraftProtocol$presentation.finish(System.nanoTime());
    }

    @Redirect(
            method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void mineCraftProtocol$selectedName(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        graphics.textWithBackdrop(font, text, x, y, width, color);
        mineCraftProtocol$presentation.selectedName(text.getString(), x, y, width, color);
    }

    @Redirect(
            method = "extractHotbarAndDecorations",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void mineCraftProtocol$experienceLevel(
            GuiGraphicsExtractor graphics, Font font, int level) {
        ContextualBar.extractExperienceLevel(graphics, font, level);
        Component text = Component.translatable("gui.experience.level", level);
        int width = font.width(text);
        mineCraftProtocol$presentation.experienceLevel(
                text.getString(),
                (graphics.guiWidth() - width) / 2,
                graphics.guiHeight() - 24 - 9 - 2,
                width);
    }

    @Override
    public UiPresentationSnapshot.RenderedHud mineCraftProtocol$hudPresentation() {
        return mineCraftProtocol$presentation;
    }
}
