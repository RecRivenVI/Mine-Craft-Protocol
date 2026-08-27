package io.github.recrivenvi.minecraftprotocol.probe.peer;

import net.minecraft.network.FriendlyByteBuf;

public record PeerMessage(String json) {
    public static final int MAX_JSON_BYTES = 32 * 1024 - 1;

    public static void encode(PeerMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.json(), MAX_JSON_BYTES);
    }

    public static PeerMessage decode(FriendlyByteBuf buffer) {
        return new PeerMessage(buffer.readUtf(MAX_JSON_BYTES));
    }
}
