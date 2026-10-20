package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiWorldRecoveryFixtureScreen;
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptimizeWorldScreen.class)
public abstract class UiWorldRecoveryOptimizeProbeMixin {
    @Inject(method = "removed", at = @At("TAIL"))
    private void minecraftProtocolProbe$observeNativeOptimizerRemoved(CallbackInfo callback) {
        UiWorldRecoveryFixtureScreen.observeNativeRemoved((OptimizeWorldScreen) (Object) this);
    }
}
