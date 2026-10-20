package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiSavingIndicatorFixture;
import net.minecraft.server.MinecraftServer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class UiSavingServerFlagProbeMixin {
    @Inject(
            method = "saveEverything",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/server/MinecraftServer;isSaving:Z",
                            opcode = Opcodes.PUTFIELD,
                            ordinal = 0,
                            shift = At.Shift.AFTER))
    private void minecraftProtocolProbe$afterActualSavingFlag(
            boolean suppressLog,
            boolean flush,
            boolean forced,
            CallbackInfoReturnable<Boolean> callback) {
        UiSavingIndicatorFixture.afterNativeSavingFlag(
                (MinecraftServer) (Object) this, suppressLog, flush, forced);
    }

    @Inject(method = "saveEverything", at = @At("RETURN"))
    private void minecraftProtocolProbe$afterNativeSaveReturned(
            boolean suppressLog,
            boolean flush,
            boolean forced,
            CallbackInfoReturnable<Boolean> callback) {
        UiSavingIndicatorFixture.afterNativeSaveReturned(
                (MinecraftServer) (Object) this, callback.getReturnValue());
    }
}
