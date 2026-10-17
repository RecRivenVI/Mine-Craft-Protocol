package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class PlayerCommandMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$command(String command, CallbackInfo ci) {
        if (!NeoForgeProbeRuntime.allowPlayerCommand(
                command, false, ((ClientPacketListener) (Object) this).getConnection()))
            ci.cancel();
    }

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$linkCommand(
            String command, net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        if (!NeoForgeProbeRuntime.allowPlayerCommand(
                command, false, ((ClientPacketListener) (Object) this).getConnection()))
            ci.cancel();
    }
}
