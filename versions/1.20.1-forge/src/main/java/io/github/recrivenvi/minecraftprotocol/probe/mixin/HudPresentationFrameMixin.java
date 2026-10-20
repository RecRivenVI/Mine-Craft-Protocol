package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class HudPresentationFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(CallbackInfo ci) {
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null) source.begin(Minecraft.getInstance());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void minecraftProtocolProbe$finish(CallbackInfo ci) {
        var source = UiPresentationSnapshot.activeHudPresentation();
        if (source != null) source.finish(System.nanoTime());
    }
}
