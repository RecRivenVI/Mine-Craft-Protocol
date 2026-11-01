package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandSuggestionRequests;
import io.github.recrivenvi.minecraftprotocol.runtime.NativeCommandSuggestions;
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
    private final PlayerCommandSuggestionRequests<Suggestions> mineCraftProtocol$requests =
            new PlayerCommandSuggestionRequests<>();

    @Override
    public PlayerCommandSuggestionRequests<Suggestions> mineCraftProtocol$suggestionRequests() {
        return mineCraftProtocol$requests;
    }

    @Inject(
            method = "handleCommandSuggestions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/ClientSuggestionProvider;completeCustomSuggestions(ILcom/mojang/brigadier/suggestion/Suggestions;)V"),
            cancellable = true)
    private void mineCraftProtocol$ownedSuggestions(
            ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        if (mineCraftProtocol$requests.owns(packet.getId())) {
            mineCraftProtocol$requests.receive(
                    packet.getId(),
                    packet.getSuggestions(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mineCraftProtocol$closeSuggestions(CallbackInfo ci) {
        mineCraftProtocol$requests.close();
    }
}
