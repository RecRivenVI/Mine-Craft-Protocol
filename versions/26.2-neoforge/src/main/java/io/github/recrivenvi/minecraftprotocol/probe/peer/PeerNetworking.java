package io.github.recrivenvi.minecraftprotocol.probe.peer;

import net.minecraft.server.level.ServerPlayer;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.DedicatedPeerClient;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PeerNetworking {
    private PeerNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("peer-v0").optional().playBidirectional(
                PeerPayload.TYPE,
                PeerPayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DedicatedPeerServer.handle(payload, player, context::reply);
                    }
                },
                (payload, context) -> DedicatedPeerClient.receive(payload));
    }
}
