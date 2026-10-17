package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerCommandPayloadsTest {
    @Test
    void structuredCarrierKeysAreNotCommandSubstrings() {
        var payloads = new PlayerCommandPayloads();
        payloads.capture(
                JsonParser.parseString(
                        "{\"CustomName\":\"reload\",\"Command\":\"time query gametime\",\"Passengers\":[{\"Command\":\"reload\"}],\"minecraft:custom_data\":{\"Command\":\"stop\"}}"));
        assertEquals(
                List.of(
                        new PlayerCommandPayloads.Program(
                                PlayerCommandPayloads.Kind.COMMAND, "time query gametime"),
                        new PlayerCommandPayloads.Program(
                                PlayerCommandPayloads.Kind.COMMAND, "reload")),
                payloads.programs());
        assertEquals(2, payloads.programs().size());
        assertThrows(UnsupportedOperationException.class, () -> payloads.programs().clear());
    }

    @Test
    void jigsawStateIsASeparateNativeBlockProgramAndEmptyCommandsDoNotExecute() {
        var payloads = new PlayerCommandPayloads();
        payloads.capture(
                JsonParser.parseString("{\"Command\":\"  \",\"final_state\":\"minecraft:stone\"}"));
        assertEquals(PlayerCommandPayloads.Kind.BLOCK_STATE, payloads.programs().get(0).kind());
        assertEquals(1, payloads.programs().size());
    }

    @Test
    void nativePathFieldsRespectQuotesFiltersAndArrayEndpoints() {
        for (String path :
                List.of(
                        "Command",
                        "\"Command\"",
                        "'Command'",
                        "Passengers[0].Command",
                        "Passengers[{id:\"Command\",tag:{text:\"]}\"}}].Command",
                        "foo{Command:'not the target'}.Command"))
            assertTrue(PlayerCommandPayloads.targetsCommand(path), path);
        for (String path :
                List.of(
                        "CustomName",
                        "\"foo.Command\"",
                        "Command[0]",
                        "Command[]",
                        "Foo{Command:'reload'}",
                        "foo[{'Command':'reload'}].bar"))
            assertFalse(PlayerCommandPayloads.targetsCommand(path), path);
    }

    @Test
    void allPayloadBudgetsAreExplicitAndDoNotEmitPartialPrograms() {
        var many = new JsonArray();
        for (int i = 0; i < 513; i++) many.add(i);
        var error =
                assertThrows(
                        ProtocolState.ProtocolException.class,
                        () -> new PlayerCommandPayloads().capture(many));
        assertEquals("PLAYER_COMMAND_BUDGET_EXCEEDED", error.code());
        var depth = new JsonObject();
        var child = depth;
        for (int i = 0; i < 34; i++) {
            var next = new JsonObject();
            child.add("child", next);
            child = next;
        }
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> new PlayerCommandPayloads().capture(depth));
        var payloads = new PlayerCommandPayloads();
        for (int i = 0; i < 16; i++) payloads.command("help");
        assertThrows(ProtocolState.ProtocolException.class, () -> payloads.command("help"));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> new PlayerCommandPayloads().command("x".repeat(2049)));
    }

    @Test
    void nestedEffectsCannotBeWashedByAnOrdinaryMutationRoot() {
        var frames = List.of(PlayerCommandPolicyTest.frame("summon"));
        for (var effect :
                List.of(
                        PlayerCommandPolicy.Effect.CONTROL_PLANE,
                        PlayerCommandPolicy.Effect.UNRESOLVED))
            assertEquals(
                    effect,
                    PlayerCommandPolicy.classify(
                            new PlayerCommandPolicy.Parsed(true, frames, List.of(effect))));
        assertEquals(
                PlayerCommandPolicy.Effect.STATE_MUTATION,
                PlayerCommandPolicy.classify(
                        new PlayerCommandPolicy.Parsed(
                                true, frames, List.of(PlayerCommandPolicy.Effect.INFORMATION))));
    }

    @Test
    void missingNativeParseCannotMasqueradeAsAnEmptyConfiguration() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(world.gate.allow("help", true, true, () -> null));
                        assertEquals(
                                "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void nativeAnalysisCannotPublishAfterModeLossOrHideAnEmbeddedRejection() {
        for (var effect :
                List.of(
                        PlayerCommandPolicy.Effect.CONTROL_PLANE,
                        PlayerCommandPolicy.Effect.UNRESOLVED)) {
            try (var world = new PlayerCommandGateTest.World()) {
                world.control.acquire();
                world.dispatch(
                        dispatch -> {
                            assertFalse(
                                    world.gate.allow(
                                            "summon entity",
                                            true,
                                            true,
                                            () ->
                                                    new PlayerCommandPolicy.Parsed(
                                                            true,
                                                            List.of(
                                                                    PlayerCommandPolicyTest.frame(
                                                                            "summon")),
                                                            List.of(effect))));
                            assertThrows(ProtocolState.ProtocolException.class, dispatch::check);
                        });
                assertEquals(0, world.observed.get());
            }
        }
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allow(
                                        "help",
                                        true,
                                        true,
                                        () -> {
                                            world.control.release("test_during_native_parse");
                                            return world.parse("help");
                                        }));
                        assertThrows(ProtocolState.ProtocolException.class, dispatch::check);
                    });
            assertEquals(0, world.observed.get());
        }
    }
}
