package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerCommandDiscoveryTest {
    @Test
    void queryKeepsTheOriginalCursorAndMapsAnOptionalSlash() {
        var query = new PlayerCommandDiscovery.Query("/help ", 6, 8);
        assertEquals("help ", query.commandInput());
        assertEquals(5, query.commandCursor());
        assertEquals(1, query.inputOffset());
        assertEquals(0, new PlayerCommandDiscovery.Query("", 0, 1).commandCursor());
        assertEquals(0, new PlayerCommandDiscovery.Query("/", 0, 1).commandCursor());
    }

    @Test
    void invalidTextCursorAndBudgetsFailBeforeNativeDiscovery() {
        for (String text : List.of("help\nstop", "x".repeat(2049), "\uD800"))
            assertEquals(
                    "INVALID_COMMAND_DISCOVERY",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> new PlayerCommandDiscovery.Query(text, 0, 8))
                            .code());
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> new PlayerCommandDiscovery.Query("hello", 6, 8));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> new PlayerCommandDiscovery.Query("😀", 1, 8));
        for (int limit : List.of(0, -1, 129))
            assertEquals(
                    "PLAYER_COMMAND_BUDGET_EXCEEDED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> new PlayerCommandDiscovery.Query("", 0, limit))
                            .code());
    }

    @Test
    void projectionIsBoundedAndDoesNotPromiseExecutionOrAuthorization() {
        var result =
                PlayerCommandDiscovery.result(
                        new PlayerCommandDiscovery.Query("/he", 3, 1),
                        List.of("help", "stop"),
                        2,
                        List.of(
                                new PlayerCommandDiscovery.Suggestion(
                                        1, 3, "help", "native tooltip")),
                        2,
                        false,
                        null);
        assertEquals(1, result.getAsJsonArray("roots").size());
        assertTrue(result.get("rootsTruncated").getAsBoolean());
        assertTrue(result.get("suggestionsTruncated").getAsBoolean());
        assertEquals(
                1,
                result.getAsJsonArray("suggestions")
                        .get(0)
                        .getAsJsonObject()
                        .get("start")
                        .getAsInt());
        assertFalse(result.get("effectPolicyAllowsSubmission").getAsBoolean());
        assertFalse(result.get("commandExecuted").getAsBoolean());
        assertFalse(result.get("serverAcceptanceConfirmed").getAsBoolean());
        assertEquals("USER", result.get("dataCategory").getAsString());
    }

    @Test
    void nativeAvailabilityAndRuntimeEffectPolicyRemainDistinct() {
        for (var effect : PlayerCommandPolicy.Effect.values()) {
            var result =
                    PlayerCommandDiscovery.result(
                            new PlayerCommandDiscovery.Query("stop", 4, 8),
                            List.of("stop"),
                            1,
                            List.of(),
                            0,
                            true,
                            effect);
            assertTrue(result.get("parsedExecutable").getAsBoolean());
            assertEquals(
                    effect != PlayerCommandPolicy.Effect.CONTROL_PLANE
                            && effect != PlayerCommandPolicy.Effect.UNRESOLVED,
                    result.get("effectPolicyAllowsSubmission").getAsBoolean());
        }
    }
}
