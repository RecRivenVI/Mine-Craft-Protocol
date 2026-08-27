package io.github.recrivenvi.minecraftprotocol.probe.peer;

import net.minecraft.server.level.ServerPlayer;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.DedicatedPeerClient;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;

public final class PeerNetworking {
    private PeerNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("peer-v0").optional().playBidirectional(
                PeerPayload.TYPE,
                PeerPayload.CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> DedicatedPeerClient.receive(payload),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                DedicatedPeerServer.handle(payload, player, context::reply);
                            }
                        }));
    }
}
