package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandSuggestionRequests;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.NativeCommandSuggestions;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class CommandCompletionConnectionMixin
        implements NativeCommandSuggestions.ConnectionAccess {
    @Unique
    private final PlayerCommandSuggestionRequests<Suggestions> minecraftProtocolProbe$requests =
            new PlayerCommandSuggestionRequests<>();

    @Override
    public PlayerCommandSuggestionRequests<Suggestions>
            minecraftProtocolProbe$suggestionRequests() {
        return minecraftProtocolProbe$requests;
    }

    @Inject(
            method = "handleCommandSuggestions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/ClientSuggestionProvider;completeCustomSuggestions(ILcom/mojang/brigadier/suggestion/Suggestions;)V"),
            cancellable = true)
    private void minecraftProtocolProbe$ownedSuggestions(
            ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        if (minecraftProtocolProbe$requests.owns(packet.id())) {
            minecraftProtocolProbe$requests.receive(
                    packet.id(),
                    packet.toSuggestions(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void minecraftProtocolProbe$closeSuggestions(CallbackInfo ci) {
        minecraftProtocolProbe$requests.close();
    }
}
