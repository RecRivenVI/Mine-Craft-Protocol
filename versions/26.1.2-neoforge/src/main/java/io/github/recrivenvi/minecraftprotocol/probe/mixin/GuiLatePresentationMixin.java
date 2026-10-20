package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GuiLatePresentationMixin
        implements UiPresentationSnapshot.GuiPresentationSource {
    @Unique
    private final UiPresentationSnapshot.RenderedHud minecraftProtocolProbe$latePresentation =
            new UiPresentationSnapshot.RenderedHud(128);

    @Inject(method = "extractGui", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(CallbackInfo ci) {
        minecraftProtocolProbe$latePresentation.beginGui(Minecraft.getInstance());
    }

    @Inject(method = "extractGui", at = @At("RETURN"))
    private void minecraftProtocolProbe$finish(CallbackInfo ci) {
        minecraftProtocolProbe$latePresentation.finish(System.nanoTime());
    }

    @Override
    public UiPresentationSnapshot.RenderedHud minecraftProtocolProbe$guiPresentation() {
        return minecraftProtocolProbe$latePresentation;
    }
}
