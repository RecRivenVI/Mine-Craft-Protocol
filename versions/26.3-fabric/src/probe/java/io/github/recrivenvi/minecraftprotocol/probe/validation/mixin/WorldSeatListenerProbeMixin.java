package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.WorldSeatFixture;
import java.io.IOException;
import java.net.InetAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
abstract class WorldSeatListenerProbeMixin {
    @Redirect(
            method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void minecraftProtocol$loopbackSeatListener(
            ServerConnectionListener listener, InetAddress address, int port) throws IOException {
        // The two offline diagnostic players must not expose an unauthenticated LAN listener.
        listener.startTcpServerListener(
                WorldSeatFixture.enabled()
                        ? InetAddress.getByAddress(new byte[] {127, 0, 0, 1})
                        : address,
                port);
    }
}
