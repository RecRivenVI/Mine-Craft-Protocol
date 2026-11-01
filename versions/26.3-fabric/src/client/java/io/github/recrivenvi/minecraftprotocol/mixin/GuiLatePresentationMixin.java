package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiLatePresentationMixin
        implements UiPresentationSnapshot.GuiPresentationSource {
    @Unique
    private final UiPresentationSnapshot.RenderedHud mineCraftProtocol$latePresentation =
            new UiPresentationSnapshot.RenderedHud(128);

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mineCraftProtocol$begin(CallbackInfo ci) {
        mineCraftProtocol$latePresentation.beginGui(Minecraft.getInstance());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void mineCraftProtocol$finish(CallbackInfo ci) {
        mineCraftProtocol$latePresentation.finish(System.nanoTime());
    }

    @Override
    public UiPresentationSnapshot.RenderedHud mineCraftProtocol$guiPresentation() {
        return mineCraftProtocol$latePresentation;
    }
}
