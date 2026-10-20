package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiRecoveryFixtureScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes only this probe's actual native progress page after a client tick. */
@Mixin(Minecraft.class)
public abstract class UiRecoveryTickProbeMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void minecraftProtocolProbe$observeRecoveryCancellation(CallbackInfo callback) {
        if (UiRecoveryFixtureScreen.enabled()) UiRecoveryFixtureScreen.observeNativeTick();
    }
}
