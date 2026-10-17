package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void minecraftProtocolProbe$worldFrame(CallbackInfo ci) {
        io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldPresentationSnapshot.begin();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void minecraftProtocolProbe$contentReady(CallbackInfo ci) {
        FabricProbeRuntime.contentRendered();
        io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldPresentationSnapshot.finish();
    }
}
