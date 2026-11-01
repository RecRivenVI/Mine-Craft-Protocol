package io.github.recrivenvi.minecraftprotocol.peer;

import io.github.recrivenvi.minecraftprotocol.runtime.DedicatedPeerClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class PeerClientNetworking {
    private PeerClientNetworking() {}

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(
                PeerPayload.TYPE, (payload, context) -> DedicatedPeerClient.receive(payload));
    }
}
