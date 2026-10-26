package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugStateRequestTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void rejectsUnknownAndLossyValuesBeforeNativeCommit() {
        for (String value :
                new String[] {
                    "{}",
                    "{\"food\":1.5}",
                    "{\"food\":\"2\"}",
                    "{\"food\":21}",
                    "{\"rawNbt\":{}}",
                    "{\"velocity\":{\"x\":1,\"y\":0}}",
                    "{\"velocity\":{\"x\":11,\"y\":0,\"z\":0}}",
                    "{\"silent\":true}",
                    "{\"experienceLevel\":2,\"experiencePoints\":3}"
                }) {
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugStateRequest.parse(json("{\"value\":" + value + "}"), true),
                    value);
        }
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugStateRequest.parse(json("{\"value\":{\"food\":2}}"), false));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugStateRequest.parse(json("{\"value\":{\"glowing\":1}}"), false));
    }

    @Test
    void expectedValueUsesObservedWirePrecisionAndPreservesPartialConditions() {
        JsonObject current = new JsonObject();
        current.addProperty("saturation", 0.2F);
        current.addProperty("food", 12);
        JsonObject request =
                json("{\"value\":{\"food\":18},\"expectedValue\":{\"saturation\":0.2}}");
        assertEquals(18, DebugStateRequest.parse(request, true).get("food").getAsInt());
        assertDoesNotThrow(() -> DebugStateRequest.verifyValues(request, current));
        request.getAsJsonObject("expectedValue").addProperty("food", 10);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugStateRequest.verifyValues(request, current));
        assertTrue(DebugStateRequest.unchanged(json("{\"saturation\":0.2}"), current));
    }
}
