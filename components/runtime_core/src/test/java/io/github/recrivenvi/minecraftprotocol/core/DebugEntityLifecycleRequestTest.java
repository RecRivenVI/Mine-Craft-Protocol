package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugEntityLifecycleRequestTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void negativeFractionalCoordinatesUseTheActualDestinationChunkWithoutRoundingInput() {
        var v =
                DebugEntityLifecycleRequest.spawn(
                        json(
                                "{\"value\":{\"entityType\":\"minecraft:pig\",\"position\":{\"x\":-0.125,\"y\":70.25,\"z\":-16.125},\"yaw\":20.5}}"));
        assertEquals(-0.125, v.x());
        assertEquals(70.25, v.y());
        assertEquals(new DebugEntityLifecycleRequest.Chunk(-1, -2), v.chunk());
        assertEquals(20.5F, v.yaw());
    }

    @Test
    void sourceSelectorsAreExactAndFactoryDoesNotAcceptIdentityNbtOrExtraPayloads() {
        for (String input :
                new String[] {
                    "{}",
                    "{\"x\":0,\"z\":1.5}",
                    "{\"x\":\"0\",\"z\":0}",
                    "{\"x\":0,\"z\":0,\"y\":0}",
                    "{\"x\":1875000,\"z\":0}"
                })
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugEntityLifecycleRequest.chunk(json(input)));
        for (String field : new String[] {"nbt", "uuid", "components", "entityData"}) {
            var request =
                    json(
                            "{\"value\":{\"entityType\":\"minecraft:pig\",\"position\":{\"x\":0,\"y\":70,\"z\":0}}}");
            request.getAsJsonObject("value").addProperty(field, "untrusted");
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugEntityLifecycleRequest.spawn(request));
        }
        assertThrows(
                ProtocolState.ProtocolException.class,
                () ->
                        DebugEntityLifecycleRequest.spawn(
                                json(
                                        "{\"value\":{\"entityType\":\"minecraft:pig\",\"position\":{\"x\":1e400,\"y\":0,\"z\":0}}}")));
    }
}
