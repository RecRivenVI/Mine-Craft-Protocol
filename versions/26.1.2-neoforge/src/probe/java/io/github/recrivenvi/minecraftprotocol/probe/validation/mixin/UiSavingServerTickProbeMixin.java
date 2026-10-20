package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiSavingIndicatorFixture;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public abstract class UiSavingServerTickProbeMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void minecraftProtocolProbe$observeNativePauseState(CallbackInfo callback) {
        UiSavingIndicatorFixture.observeServerTick((IntegratedServer) (Object) this);
    }

    @Inject(
            method = "tickServer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/server/IntegratedServer;saveEverything(ZZZ)Z"))
    private void minecraftProtocolProbe$selectNativePauseSave(CallbackInfo callback) {
        UiSavingIndicatorFixture.beforeNativePauseSave((IntegratedServer) (Object) this);
    }
}
