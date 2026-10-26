package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugTransferRequestTest {
    @Test
    void fractionalDestinationIsNotRoundedAndMissingAnglesRetainNativeState() {
        var value =
                DebugTransferRequest.destination(
                        JsonParser.parseString(
                                        """
                {"value":{"dimension":"minecraft:the_nether","position":{"x":-0.125,"y":70.75,"z":1.0625}}}
                """)
                                .getAsJsonObject());
        assertEquals(-0.125, value.x());
        assertEquals(70.75, value.y());
        assertNull(value.yaw());
        assertNull(value.pitch());
    }

    @Test
    void rejectsUntypedPayloadsAndMalformedNativeCoordinatesBeforeCommit() {
        for (String json :
                new String[] {
                    "{\"value\":{\"dimension\":\"overworld\",\"position\":{\"x\":0,\"y\":0,\"z\":0}}}",
                    "{\"value\":{\"dimension\":\"minecraft:overworld\",\"position\":{\"x\":\"0\",\"y\":0,\"z\":0}}}",
                    "{\"value\":{\"dimension\":\"minecraft:overworld\",\"position\":{\"x\":0,\"y\":0,\"z\":0},\"nbt\":{}}}",
                    "{\"value\":{\"dimension\":\"minecraft:overworld\",\"position\":{\"x\":0,\"y\":0,\"z\":0},\"pitch\":91}}"
                })
            assertEquals(
                    "INVALID_DEBUG_TRANSFER",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            DebugTransferRequest.destination(
                                                    JsonParser.parseString(json).getAsJsonObject()))
                            .code());
    }

    @Test
    void exactEntitySelectorsRejectAliasesDuplicatesAndOversizedQueries() {
        String one =
                "{\"dimension\":\"minecraft:overworld\",\"entityUuid\":\"00000000-0000-0000-0000-000000000001\"}";
        assertEquals(
                1, DebugTransferRequest.targets(JsonParser.parseString("[" + one + "]")).size());
        for (String json :
                new String[] {
                    "[]",
                    "[" + one + "," + one + "]",
                    "[{\"dimension\":\"minecraft:overworld\",\"entityUuid\":\"1-1-1-1-1\"}]"
                })
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugTransferRequest.targets(JsonParser.parseString(json)));
        var many = new com.google.gson.JsonArray();
        for (int i = 0; i <= DebugTransferRequest.MAX_ENTITY_TARGETS; i++)
            many.add(JsonParser.parseString(one));
        assertThrows(
                ProtocolState.ProtocolException.class, () -> DebugTransferRequest.targets(many));
    }
}
