package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.FabricRuntime;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Inject(
            method = "handleContainerClick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"))
    private void mineCraftProtocol$observeServerValidation(
            ServerboundContainerClickPacket packet, CallbackInfo callbackInfo) {
        FabricRuntime.observeServerContainerPacket(packet.getClass().getName());
    }
}
