package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import org.junit.jupiter.api.Test;

class NativePlayerCommandDiscoveryTest {
    @Test
    void productionDiscoveryUsesNativeSuggestionsWithoutExecutingCommands() {
        var dispatcher = new CommandDispatcher<SharedSuggestionProvider>();
        var executions = new AtomicInteger();
        for (String name : List.of("help", "hello", "stop"))
            dispatcher.register(
                    LiteralArgumentBuilder.<SharedSuggestionProvider>literal(name)
                            .executes(context -> executions.incrementAndGet()));
        var checks = new AtomicInteger();
        var result =
                NativePlayerCommands.discover(
                                dispatcher,
                                null,
                                new PlayerCommandDiscovery.Query("/he", 3, 1),
                                checks::incrementAndGet,
                                Runnable::run)
                        .join();
        assertEquals(0, executions.get());
        assertEquals(2, checks.get());
        assertEquals(3, result.get("rootCount").getAsInt());
        assertEquals(2, result.get("suggestionCount").getAsInt());
        assertTrue(result.get("suggestionsTruncated").getAsBoolean());
        var suggestion = result.getAsJsonArray("suggestions").get(0).getAsJsonObject();
        assertEquals(1, suggestion.get("start").getAsInt());
        assertEquals(3, suggestion.get("end").getAsInt());
        assertFalse(result.get("parsedExecutable").getAsBoolean());
        assertEquals("USER", result.get("dataCategory").getAsString());
        assertEquals("CLIENT_LOCAL", result.get("completionSource").getAsString());
        assertEquals("client_local_only", result.get("completionScope").getAsString());
        assertTrue(result.get("serverCompletionSupported").getAsBoolean());
        assertFalse(result.get("serverCompletionRequested").getAsBoolean());
        var denied =
                NativePlayerCommands.discover(
                                dispatcher,
                                null,
                                new PlayerCommandDiscovery.Query("stop", 4, 8),
                                () -> {},
                                Runnable::run)
                        .join();
        assertTrue(denied.get("parsedExecutable").getAsBoolean());
        assertFalse(denied.get("effectPolicyAllowsSubmission").getAsBoolean());
        assertEquals("control_plane_out_of_scope", denied.get("policyReason").getAsString());
        assertEquals(0, executions.get());
    }

    @Test
    void permissionFilteredNativeTreeDoesNotInventMissingServerCommands() {
        var dispatcher = new CommandDispatcher<SharedSuggestionProvider>();
        dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("help")
                        .executes(context -> 1));
        var result =
                NativePlayerCommands.discover(
                                dispatcher,
                                null,
                                new PlayerCommandDiscovery.Query("", 0, 8),
                                () -> {},
                                Runnable::run)
                        .join();
        assertEquals(1, result.getAsJsonArray("roots").size());
        assertEquals("help", result.getAsJsonArray("roots").get(0).getAsString());
        assertEquals(1, result.get("suggestionCount").getAsInt());
    }

    @Test
    void delayedNativeSuggestionsRecheckOnOwnerThreadBeforePublishing() {
        var pending = new CompletableFuture<Suggestions>();
        var dispatcher = suggestionsDispatcher(pending);
        var live = new AtomicBoolean(true);
        List<Runnable> ownerQueue = new ArrayList<>();
        var result =
                NativePlayerCommands.discover(
                        dispatcher,
                        null,
                        new PlayerCommandDiscovery.Query("help ", 5, 8),
                        () -> {
                            if (!live.get())
                                throw new ProtocolState.ProtocolException(
                                        "PLAYER_COMMAND_CONTEXT_CHANGED",
                                        409,
                                        "test connection changed");
                        },
                        ownerQueue::add);
        pending.complete(
                new Suggestions(
                        StringRange.at(5), List.of(new Suggestion(StringRange.at(5), "topic"))));
        assertFalse(result.isDone());
        live.set(false);
        ownerQueue.remove(0).run();
        assertEquals(
                "PLAYER_COMMAND_CONTEXT_CHANGED",
                ((ProtocolState.ProtocolException)
                                assertThrows(
                                                java.util.concurrent.CompletionException.class,
                                                result::join)
                                        .getCause())
                        .code());
    }

    @Test
    void cancelledDiscoveryCannotPublishLateSuggestions() {
        var pending = new CompletableFuture<Suggestions>();
        List<Runnable> ownerQueue = new ArrayList<>();
        var result =
                NativePlayerCommands.discover(
                        suggestionsDispatcher(pending),
                        null,
                        new PlayerCommandDiscovery.Query("help ", 5, 8),
                        () -> {},
                        ownerQueue::add);
        assertTrue(result.cancel(false));
        pending.complete(
                new Suggestions(
                        StringRange.at(5), List.of(new Suggestion(StringRange.at(5), "topic"))));
        ownerQueue.forEach(Runnable::run);
        assertTrue(result.isCancelled());
    }

    @Test
    void independentReadSourceNeverCancelsOrCallsTheHumanServerCompletionSlot() {
        var humanCalls = new AtomicInteger();
        var humanPending = new CompletableFuture<Suggestions>();
        var original = new HumanSuggestionSource(humanCalls, humanPending);
        var serverRequired = new AtomicBoolean();
        var source =
                NativePlayerCommands.localSuggestionSource(null, null, original, serverRequired);
        var dispatcher = new CommandDispatcher<SharedSuggestionProvider>();
        dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("help")
                        .then(
                                RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                                                "topic", StringArgumentType.word())
                                        .suggests(
                                                (context, builder) ->
                                                        context.getSource()
                                                                .customSuggestion(context))
                                        .executes(
                                                context -> fail("Discovery must never execute"))));
        var result =
                NativePlayerCommands.discover(
                                dispatcher,
                                source,
                                new PlayerCommandDiscovery.Query("help ", 5, 8),
                                () -> {},
                                Runnable::run,
                                serverRequired::get)
                        .join();
        assertEquals(0, humanCalls.get());
        assertFalse(humanPending.isDone());
        assertTrue(serverRequired.get());
        assertEquals(0, result.getAsJsonArray("suggestions").size());
        assertFalse(result.get("completionComplete").getAsBoolean());
        assertFalse(result.get("serverCompletionRequested").getAsBoolean());
        assertTrue(result.get("serverCompletionRequired").getAsBoolean());
        assertEquals(List.of("visible_player"), source.getCustomTabSugggestions());
    }

    private static CommandDispatcher<SharedSuggestionProvider> suggestionsDispatcher(
            CompletableFuture<Suggestions> pending) {
        var dispatcher = new CommandDispatcher<SharedSuggestionProvider>();
        dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("help")
                        .then(
                                RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                                                "topic", StringArgumentType.word())
                                        .suggests((context, builder) -> pending)
                                        .executes(context -> 1)));
        return dispatcher;
    }

    private static final class HumanSuggestionSource extends ClientSuggestionProvider {
        private final AtomicInteger calls;
        private final CompletableFuture<Suggestions> pending;

        HumanSuggestionSource(AtomicInteger calls, CompletableFuture<Suggestions> pending) {
            super(null, null);
            this.calls = calls;
            this.pending = pending;
        }

        @Override
        public CompletableFuture<Suggestions> customSuggestion(
                com.mojang.brigadier.context.CommandContext<?> context) {
            calls.incrementAndGet();
            return pending;
        }

        @Override
        public java.util.Collection<String> getCustomTabSugggestions() {
            return List.of("visible_player");
        }
    }
}
