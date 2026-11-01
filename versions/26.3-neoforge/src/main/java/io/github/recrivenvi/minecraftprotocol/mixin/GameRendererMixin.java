package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void mineCraftProtocol$worldFrame(CallbackInfo ci) {
        io.github.recrivenvi.minecraftprotocol.runtime.WorldPresentationSnapshot.begin();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mineCraftProtocol$contentReady(CallbackInfo ci) {
        NeoForgeRuntime.contentRendered();
        io.github.recrivenvi.minecraftprotocol.runtime.WorldPresentationSnapshot.finish();
    }
}
