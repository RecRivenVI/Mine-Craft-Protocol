package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugRelationRequestTest {
    @Test
    void identitiesAreExplicitCompleteAndCannotCarryArbitraryPayloads() {
        assertNull(DebugRelationRequest.uuid(JsonNull.INSTANCE));
        assertEquals(
                "123e4567-e89b-42d3-a456-426614174000",
                DebugRelationRequest.uuid(
                        JsonParser.parseString("\"123e4567-e89b-42d3-a456-426614174000\"")));
        for (String value : new String[] {"123", "true", "{}", "\"1-1-1-1-1\""})
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugRelationRequest.uuid(JsonParser.parseString(value)));
        for (String value :
                new String[] {
                    "{}",
                    "{\"ownerUuid\":null,\"tamed\":true}",
                    "{\"ownerUuid\":null,\"tamed\":\"false\"}",
                    "{\"ownerUuid\":null,\"tamed\":false,\"nbt\":{}}"
                })
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugRelationRequest.owner(JsonParser.parseString(value)));
    }
}
