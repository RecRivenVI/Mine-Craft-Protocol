package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class PlayerCommandMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$command(String command, CallbackInfo ci) {
        if (!NeoForgeRuntime.allowPlayerCommand(
                command, false, ((ClientPacketListener) (Object) this).getConnection()))
            ci.cancel();
    }

    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$linkCommand(
            String command,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!NeoForgeRuntime.allowPlayerCommand(
                command, false, ((ClientPacketListener) (Object) this).getConnection()))
            cir.setReturnValue(false);
    }
}
