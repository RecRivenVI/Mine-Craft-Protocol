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
    @Unique private boolean minecraftProtocolProbe$savedDataSaveReturned;

    @Override
    public boolean minecraftProtocolProbe$savedDataSaveReturned() {
        return minecraftProtocolProbe$savedDataSaveReturned;
    }

    @Inject(
            method = "work",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/storage/DimensionDataStorage;save()V",
                            shift = At.Shift.AFTER))
    private void minecraftProtocolProbe$observeNativeSaveReturned(CallbackInfo callback) {
        minecraftProtocolProbe$savedDataSaveReturned = true;
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void minecraftProtocolProbe$observeNativeWorkerBeforeCancel(CallbackInfo callback) {
        UiWorldRecoveryFixtureScreen.observeNativeCancelStarting((WorldUpgrader) (Object) this);
    }

    @Inject(method = "cancel", at = @At("TAIL"))
    private void minecraftProtocolProbe$observeNativeWorkerCancel(CallbackInfo callback) {
        UiWorldRecoveryFixtureScreen.observeNativeCancel((WorldUpgrader) (Object) this);
    }
}
