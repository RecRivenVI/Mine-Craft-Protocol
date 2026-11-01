package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudPresentationMixin implements UiPresentationSnapshot.HudPresentationSource {
    @Unique
    private final UiPresentationSnapshot.RenderedHud mineCraftProtocol$presentation =
            new UiPresentationSnapshot.RenderedHud();

    @Inject(method = "render", at = @At("HEAD"))
    private void mineCraftProtocol$begin(CallbackInfo ci) {
        mineCraftProtocol$presentation.begin(Minecraft.getInstance());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mineCraftProtocol$finish(CallbackInfo ci) {
        mineCraftProtocol$presentation.finish(System.nanoTime());
    }

    @Redirect(
            method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
    private int mineCraftProtocol$selectedName(
            GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        int result = graphics.drawStringWithBackdrop(font, text, x, y, width, color);
        mineCraftProtocol$presentation.selectedName(text.getString(), x, y, width, color);
        return result;
    }

    @Redirect(
            method = "renderExperienceLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
                            ordinal = 4))
    private int mineCraftProtocol$experienceLevel(
            GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        int result = graphics.drawString(font, text, x, y, color, shadow);
        mineCraftProtocol$presentation.experienceLevel(text, x, y, font.width(text));
        return result;
    }

    @Override
    public UiPresentationSnapshot.RenderedHud mineCraftProtocol$hudPresentation() {
        return mineCraftProtocol$presentation;
    }
}
