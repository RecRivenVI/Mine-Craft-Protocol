package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DebugBlockStateRequestTest {
    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    @Test
    void optionalMapsPreserveLegacyRequestsAndPermitAnExplicitEmptyMap() {
        assertEquals(Map.of(), DebugBlockStateRequest.properties(json("{}"), "properties"));
        assertEquals(
                Map.of(),
                DebugBlockStateRequest.properties(json("{\"properties\":{}}"), "properties"));
    }

    @Test
    void stringValuesAreNotCoercedAndProgramPayloadsAreRejected() {
        for (String value :
                new String[] {
                    "null",
                    "[]",
                    "false",
                    "{\"powered\":true}",
                    "{\"age\":1}",
                    "{\"axis\":null}",
                    "{\"axis\":{}}",
                    "{\"axis\":\"x]{Command:run}\"}",
                    "{\"Axis\":\"x\"}"
                }) {
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () ->
                                    DebugBlockStateRequest.properties(
                                            json("{\"properties\":" + value + "}"), "properties"));
            assertEquals("INVALID_DEBUG_BLOCK_STATE", error.code());
        }
    }

    @Test
    void mapsAndStringsAreBoundedWithoutTruncatingTheRequest() {
        JsonObject values = new JsonObject();
        for (int i = 0; i < 65; i++) values.addProperty("p" + i, "true");
        JsonObject request = new JsonObject();
        request.add("properties", values);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugBlockStateRequest.properties(request, "properties"));
        values.remove("p64");
        assertEquals(64, DebugBlockStateRequest.properties(request, "properties").size());
        values.addProperty("p0", "x".repeat(65));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugBlockStateRequest.properties(request, "properties"));
    }

    @Test
    void preconditionsAreSubsetChecksButMissingOrChangedPropertiesFailClosed() {
        var actual = json("{\"axis\":\"x\",\"powered\":\"false\"}");
        DebugBlockStateRequest.verifyExpected(
                json("{\"expectedProperties\":{\"axis\":\"x\"}}"), actual);
        for (String expected : new String[] {"{\"axis\":\"z\"}", "{\"absent\":\"false\"}"}) {
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () ->
                                    DebugBlockStateRequest.verifyExpected(
                                            json("{\"expectedProperties\":" + expected + "}"),
                                            actual));
            assertEquals("VALUE_PRECONDITION_FAILED", error.code());
        }
        assertEquals("x", actual.get("axis").getAsString());
    }
}
