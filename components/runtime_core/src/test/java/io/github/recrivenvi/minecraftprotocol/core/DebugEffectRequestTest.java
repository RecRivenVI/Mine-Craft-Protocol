package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugEffectRequestTest {
    private static JsonObject object(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void explicitNullMeansRemoveAndOmissionIsNotATransparentRemove() {
        var value =
                DebugEffectRequest.parse(
                        object(
                                "{\"effectId\":\"minecraft:speed\",\"value\":null,\"expectedValue\":null}"));
        assertNull(value.value());
        assertTrue(value.hasExpectedValue());
        assertDoesNotThrow(() -> value.verifyExpected(null));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugEffectRequest.parse(object("{\"effectId\":\"minecraft:speed\"}")));
    }

    @Test
    void nativeFlagsAndFlatHiddenChainsAreExplicitAndBounded() {
        var parsed =
                DebugEffectRequest.parse(
                        object(
                                "{\"effectId\":\"minecraft:speed\",\"value\":{\"duration\":200,\"showParticles\":false,\"hidden\":[{\"duration\":600,\"amplifier\":1}]}}"));
        assertFalse(parsed.value().get("showIcon").getAsBoolean());
        assertEquals(1, parsed.value().getAsJsonArray("hidden").size());
        assertEquals(
                1,
                parsed.value()
                        .getAsJsonArray("hidden")
                        .get(0)
                        .getAsJsonObject()
                        .get("amplifier")
                        .getAsInt());
        for (String value :
                new String[] {
                    "{\"duration\":1.5}",
                    "{\"duration\":\"20\"}",
                    "{\"duration\":-2}",
                    "{\"duration\":1,\"amplifier\":256}",
                    "{\"duration\":1,\"rawNbt\":{}}",
                    "{\"duration\":1,\"showParticles\":1}",
                    "{\"duration\":1,\"hidden\":[{\"duration\":2,\"hidden\":[]}]}"
                })
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () ->
                            DebugEffectRequest.parse(
                                    object(
                                            "{\"effectId\":\"minecraft:speed\",\"value\":"
                                                    + value
                                                    + "}")),
                    value);
        var request =
                object(
                        "{\"effectId\":\"minecraft:speed\",\"value\":{\"duration\":1,\"hidden\":[]}}");
        var array = request.getAsJsonObject("value").getAsJsonArray("hidden");
        for (int i = 0; i < 17; i++) array.add(object("{\"duration\":1}"));
        assertThrows(
                ProtocolState.ProtocolException.class, () -> DebugEffectRequest.parse(request));
    }
}
