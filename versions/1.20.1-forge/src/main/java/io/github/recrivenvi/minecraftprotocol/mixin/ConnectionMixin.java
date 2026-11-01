package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.ForgeRuntime;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Inject(
            method =
                    "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mineCraftProtocol$commandPacket(
            Packet<?> packet, net.minecraft.network.PacketSendListener listener, CallbackInfo ci) {
        if (!ForgeRuntime.allowPlayerCommandPacket(packet, (Connection) (Object) this)) ci.cancel();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void mineCraftProtocol$observePacket(Packet<?> packet, CallbackInfo callbackInfo) {
        if (packet instanceof ServerboundContainerClickPacket) {
            ForgeRuntime.observeContainerPacket(packet.getClass().getName());
        }
    }
}
