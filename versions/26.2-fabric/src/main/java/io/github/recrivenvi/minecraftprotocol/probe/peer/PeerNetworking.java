package io.github.recrivenvi.minecraftprotocol.probe.peer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class PeerNetworking {
    private PeerNetworking() {
    }

    public static void initializeCommon() {
        PayloadTypeRegistry.serverboundPlay().register(PeerPayload.TYPE, PeerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PeerPayload.TYPE, PeerPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PeerPayload.TYPE, (payload, context) ->
                DedicatedPeerServer.handle(payload, context.player(), context.responseSender()::sendPacket));
    }
}
