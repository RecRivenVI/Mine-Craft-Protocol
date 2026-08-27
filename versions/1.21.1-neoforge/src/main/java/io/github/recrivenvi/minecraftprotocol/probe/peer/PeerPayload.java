package io.github.recrivenvi.minecraftprotocol.probe.peer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PeerPayload(String json) implements CustomPacketPayload {
    public static final int MAX_JSON_BYTES = 32 * 1024 - 1;
    public static final Type<PeerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("minecraft_protocol", "peer_v0"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PeerPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.json(), MAX_JSON_BYTES),
            buffer -> new PeerPayload(buffer.readUtf(MAX_JSON_BYTES)));

    @Override
    public Type<PeerPayload> type() {
        return TYPE;
    }
}
