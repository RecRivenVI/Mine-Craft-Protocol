package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandSuggestionRequests;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import org.junit.jupiter.api.Test;

class NativeServerCommandCompletionTest {
    @Test
    void nativePacketsPreserveOwnedIdentityPrefixAndActualSuggestions() {
        try (var requests = new PlayerCommandSuggestionRequests<Suggestions>()) {
            var request = requests.begin(1000, () -> {});
            var outbound = new ServerboundCommandSuggestionPacket(request.requestId(), "he");
            assertEquals(-2, outbound.id());
            assertEquals("he", outbound.command());
            var suggestions =
                    new Suggestions(
                            StringRange.between(0, 2),
                            List.of(new Suggestion(StringRange.between(0, 2), "help")));
            var inbound = new ClientboundCommandSuggestionsPacket(outbound.id(), suggestions);
            assertTrue(requests.owns(inbound.id()));
            assertTrue(requests.receive(inbound.id(), inbound.toSuggestions(), 1001));
            assertEquals("help", request.future().join().getList().get(0).getText());
            assertEquals(StringRange.between(0, 2), request.future().join().getRange());
            assertFalse(
                    requests.owns(0), "The hook must leave the native human response untouched");
        }
    }

    @Test
    void unfinishedHumanRequestPreventsAnyAgentPacketWithoutCancellingIt() {
        var human = new CompletableFuture<Suggestions>();
        try (var requests = new PlayerCommandSuggestionRequests<Suggestions>()) {
            var request = requests.begin(1000, () -> {});
            var sends = new AtomicInteger();
            requests.dispatch(
                    request,
                    () -> NativeCommandSuggestions.requireHumanIdle(human),
                    id -> sends.incrementAndGet());
            assertEquals(0, sends.get());
            assertFalse(human.isDone());
            var error = assertThrows(CompletionException.class, () -> request.future().join());
            assertEquals(
                    "PLAYER_COMMAND_COMPLETION_BUSY",
                    ((ProtocolState.ProtocolException) error.getCause()).code());
            assertFalse(human.isCancelled());
        }
    }

    @Test
    void completedNativeFutureDoesNotPermanentlyBlockLaterAgentQueries() {
        var completed = CompletableFuture.completedFuture(Suggestions.empty().join());
        assertDoesNotThrow(() -> NativeCommandSuggestions.requireHumanIdle(null));
        assertDoesNotThrow(() -> NativeCommandSuggestions.requireHumanIdle(completed));
        try (var requests = new PlayerCommandSuggestionRequests<Suggestions>()) {
            var request = requests.begin(1000, () -> {});
            var sends = new AtomicInteger();
            requests.dispatch(
                    request,
                    () -> NativeCommandSuggestions.requireHumanIdle(completed),
                    id -> sends.incrementAndGet());
            assertEquals(1, sends.get());
            assertTrue(completed.isDone());
            assertFalse(completed.isCancelled());
        }
    }
}
