package io.github.recrivenvi.minecraftprotocol.probe.peer;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.DedicatedPeerClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PeerNetworking {
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("minecraft_protocol", "peer_v0"))
            .networkProtocolVersion(() -> "peer-v0")
            .clientAcceptedVersions(version -> version.equals("peer-v0") || version.equals(NetworkRegistry.ABSENT))
            .serverAcceptedVersions(version -> version.equals("peer-v0") || version.equals(NetworkRegistry.ABSENT))
            .simpleChannel();
    private static boolean initialized;

    private PeerNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        CHANNEL.messageBuilder(PeerMessage.class, 0)
                .encoder(PeerMessage::encode)
                .decoder(PeerMessage::decode)
                .consumerMainThread((message, contextSupplier) -> {
                    var context = contextSupplier.get();
                    if (context.getSender() != null) {
                        DedicatedPeerServer.handle(
                                message,
                                context.getSender(),
                                response -> CHANNEL.reply(response, context));
                    } else {
                        DedicatedPeerClient.receive(message);
                    }
                })
                .add();
    }
}
