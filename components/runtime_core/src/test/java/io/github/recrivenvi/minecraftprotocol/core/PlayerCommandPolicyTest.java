package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerCommandPolicyTest {
    static PlayerCommandPolicy.Frame frame(String root, String... literals) {
        return new PlayerCommandPolicy.Frame(
                root,
                java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(root), java.util.Arrays.stream(literals))
                        .map(name -> new PlayerCommandPolicy.Node(name, true))
                        .toList());
    }

    static PlayerCommandPolicy.Parsed parsed(PlayerCommandPolicy.Frame... frames) {
        return new PlayerCommandPolicy.Parsed(true, List.of(frames));
    }

    @Test
    void nativeVerbAndNestedTerminalDetermineEffects() {
        assertEquals(
                PlayerCommandPolicy.Effect.INFORMATION,
                PlayerCommandPolicy.classify(parsed(frame("time", "query"))));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(parsed(frame("time", "set"))));
        assertEquals(
                PlayerCommandPolicy.Effect.CONTROL_PLANE,
                PlayerCommandPolicy.classify(parsed(frame("execute", "run"), frame("stop"))));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        parsed(frame("execute", "store", "run"), frame("help"))));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        parsed(frame("execute", "summon", "run"), frame("help"))));
    }

    @Test
    void argumentTextCannotPretendToBeAQueryLiteral() {
        var route =
                new PlayerCommandPolicy.Frame(
                        "data",
                        List.of(
                                new PlayerCommandPolicy.Node("data", true),
                                new PlayerCommandPolicy.Node("modify", true),
                                new PlayerCommandPolicy.Node("get", false)));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(parsed(route)));
    }

    @Test
    void indirectAndUnknownEffectsFailClosed() {
        for (String root : List.of("function", "schedule", "clone", "place", "loot", "mod_command"))
            assertEquals(
                    PlayerCommandPolicy.Effect.UNRESOLVED,
                    PlayerCommandPolicy.classify(parsed(frame(root))));
        for (String literal : List.of("from", "modify"))
            assertEquals(
                    PlayerCommandPolicy.Effect.UNRESOLVED,
                    PlayerCommandPolicy.classify(parsed(frame("item", "replace", literal))));
        assertEquals(
                PlayerCommandPolicy.Effect.UNRESOLVED,
                PlayerCommandPolicy.classify(
                        parsed(frame("execute", "if", "function"), frame("help"))));
        assertEquals(
                PlayerCommandPolicy.Effect.UNRESOLVED,
                PlayerCommandPolicy.classify(
                        new PlayerCommandPolicy.Parsed(false, List.of(frame("help")))));
    }

    @Test
    void hostAndSimulationControlCannotBeWorldMutation() {
        for (String root :
                List.of(
                        "stop",
                        "save-all",
                        "reload",
                        "op",
                        "publish",
                        "tick",
                        "forceload",
                        "setidletimeout",
                        "test"))
            assertEquals(
                    PlayerCommandPolicy.Effect.CONTROL_PLANE,
                    PlayerCommandPolicy.classify(parsed(frame(root))));
    }

    @Test
    void syntaxAndRouteBudgetsAreExplicit() {
        assertEquals("help", PlayerCommandPolicy.normalize(" /help "));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> PlayerCommandPolicy.normalize("say x\nsay y"));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> PlayerCommandPolicy.normalize("data merge x " + "{".repeat(33)));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () ->
                        PlayerCommandPolicy.classify(
                                new PlayerCommandPolicy.Parsed(
                                        true,
                                        java.util.Collections.nCopies(
                                                17, frame("execute", "run")))));
        assertDoesNotThrow(
                () ->
                        PlayerCommandPolicy.normalize(
                                "tellraw @s {\"text\":\"" + "[".repeat(40) + "\"}"));
    }
}
