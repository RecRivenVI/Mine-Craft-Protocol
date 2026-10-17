package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.ForgeProbeRuntime;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void minecraftProtocolProbe$worldFrame(CallbackInfo ci) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.gui
                instanceof io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons.Source source)
            source.minecraftProtocolProbe$hudIcons()
                    .begin(
                            client.level,
                            client.getCameraEntity(),
                            client.screen,
                            client.getWindow().getGuiScaledWidth(),
                            client.getWindow().getGuiScaledHeight());
        io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldPresentationSnapshot.begin();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void minecraftProtocolProbe$contentReady(CallbackInfo ci) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.gui
                instanceof io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons.Source source)
            source.minecraftProtocolProbe$hudIcons().finish(System.nanoTime());
        ForgeProbeRuntime.contentRendered();
        io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldPresentationSnapshot.finish();
    }
}
