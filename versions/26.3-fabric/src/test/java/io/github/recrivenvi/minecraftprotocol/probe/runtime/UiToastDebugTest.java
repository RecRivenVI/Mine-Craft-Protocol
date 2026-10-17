package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import org.junit.jupiter.api.Test;

class UiToastDebugTest {
    @Test
    void resetRequiresTwoExplicitBoundedPlainFields() {
        for (String json :
                new String[] {
                    "null",
                    "false",
                    "0",
                    "\"text\"",
                    "{}",
                    "{\"title\":\"a\"}",
                    "{\"title\":\"a\",\"message\":null}",
                    "{\"title\":\"a\",\"message\":\"b\",\"callback\":true}"
                })
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () -> UiToastDebug.resetValue(JsonParser.parseString(json)))
                            .code());
        var value = JsonParser.parseString("{\"title\":\"a\",\"message\":\"b\"}");
        assertEquals(value, UiToastDebug.resetValue(value));
        value.getAsJsonObject().addProperty("title", "x".repeat(4097));
        assertThrows(ProtocolState.ProtocolException.class, () -> UiToastDebug.resetValue(value));
    }

    @Test
    void progressIsNormalizedAndNeverCoercesBooleanOrString() {
        for (String json : new String[] {"null", "true", "\"0.5\"", "{}", "-0.1", "1.1", "1e999"})
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> UiToastDebug.progressValue(JsonParser.parseString(json)));
        assertEquals(0F, UiToastDebug.progressValue(new JsonPrimitive(0)));
        assertEquals(0.5F, UiToastDebug.progressValue(new JsonPrimitive(0.5)));
        assertEquals(1F, UiToastDebug.progressValue(new JsonPrimitive(1)));
    }

    @Test
    void userProjectionDoesNotPublishDebugToastContentOrMutationAuthority() {
        var captured =
                JsonParser.parseString(
                                """
            {"children":[],"presentation":{"children":[{"nodeId":"toast.0","controlKey":"toast.0","role":"toast","label":"visible","visible":true,"active":false,"actions":[],"debugToastKind":"system","debugToastValue":{"title":"secret","message":"secret"},"debugToastGuardAvailable":true,"debugProgress":0.3,"debugProgressable":true,"debugHideRequested":false,"resourceVersion":{"revision":2}}]}}
            """)
                        .getAsJsonObject();
        var user =
                UiDataView.tree(captured, UiDataView.Category.USER)
                        .getAsJsonObject("presentation")
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject();
        assertEquals("visible", user.get("label").getAsString());
        assertFalse(user.toString().contains("secret"));
        assertFalse(user.toString().contains("debug"));
        assertFalse(user.has("resourceVersion"));
        assertEquals(0, user.getAsJsonArray("actions").size());
    }
}
