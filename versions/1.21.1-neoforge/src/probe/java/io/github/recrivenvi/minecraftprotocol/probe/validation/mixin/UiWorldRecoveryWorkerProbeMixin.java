package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiWorldRecoveryFixtureScreen;
import net.minecraft.util.worldupdate.WorldUpgrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldUpgrader.class)
public abstract class UiWorldRecoveryWorkerProbeMixin
        implements UiWorldRecoveryFixtureScreen.NativeWorkerEvidence {
    @Unique private boolean mineCraftProtocol$savedDataSaveReturned;

    @Override
    public boolean mineCraftProtocol$savedDataSaveReturned() {
        return mineCraftProtocol$savedDataSaveReturned;
    }

    @Inject(
            method = "work",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/storage/DimensionDataStorage;save()V",
                            shift = At.Shift.AFTER))
    private void mineCraftProtocol$observeNativeSaveReturned(CallbackInfo callback) {
        mineCraftProtocol$savedDataSaveReturned = true;
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void mineCraftProtocol$observeNativeWorkerBeforeCancel(CallbackInfo callback) {
        UiWorldRecoveryFixtureScreen.observeNativeCancelStarting((WorldUpgrader) (Object) this);
    }

    @Inject(method = "cancel", at = @At("TAIL"))
    private void mineCraftProtocol$observeNativeWorkerCancel(CallbackInfo callback) {
        UiWorldRecoveryFixtureScreen.observeNativeCancel((WorldUpgrader) (Object) this);
    }
}
