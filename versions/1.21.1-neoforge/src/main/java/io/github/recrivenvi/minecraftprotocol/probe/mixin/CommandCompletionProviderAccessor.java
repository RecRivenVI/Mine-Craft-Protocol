package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientSuggestionProvider.class)
public interface CommandCompletionProviderAccessor {
    @Accessor("pendingSuggestionsFuture")
    CompletableFuture<Suggestions> minecraftProtocolProbe$pendingSuggestions();
}
