package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DeepObservationSelectorTest {
    @Test
    void continuationIsExplicitAndCannotBeCoerced() {
        assertNull(DeepObservationSelector.blockEntityCursor(new JsonObject()));
        assertEquals(
                "opaque",
                DeepObservationSelector.blockEntityCursor(
                        request("{\"blockEntityCursor\":\"opaque\"}")));
        for (String invalid : new String[] {"null", "0", "[]", "{}", "true", "\"\"", "\" \""})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            DeepObservationSelector.blockEntityCursor(
                                    request("{\"blockEntityCursor\":" + invalid + "}")));
    }

    private static JsonObject request(String selector) {
        return JsonParser.parseString("{\"selector\":" + selector + "}").getAsJsonObject();
    }

    @Test
    void validRadiiAreNotChangedAndOmissionHasTheDocumentedDefault() {
        assertEquals(0, DeepObservationSelector.chunkRadius(new JsonObject()));
        assertEquals(0, DeepObservationSelector.chunkRadius(request("{}")));
        for (int radius = 0; radius <= 2; radius++) {
            JsonObject request = request("{\"chunkRadius\":" + radius + "}");
            JsonObject original = request.deepCopy();
            assertEquals(radius, DeepObservationSelector.chunkRadius(request));
            assertEquals(original, request, "Validation must not rewrite a caller's selector");
        }
        assertEquals(1, DeepObservationSelector.chunkRadius(request("{\"chunkRadius\":1.0}")));
    }

    @Test
    void invalidRadiusTypesAndValuesAreRejectedRatherThanClampedOrCoerced() {
        for (String radius :
                new String[] {
                    "-1",
                    "3",
                    "2147483648",
                    "1e100",
                    "0.5",
                    "1.999",
                    "\"1\"",
                    "true",
                    "null",
                    "{}",
                    "[]"
                }) {
            JsonObject request = request("{\"chunkRadius\":" + radius + "}");
            JsonObject original = request.deepCopy();
            IllegalArgumentException error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> DeepObservationSelector.chunkRadius(request),
                            radius);
            assertTrue(error.getMessage().contains("selector.chunkRadius"));
            assertEquals(original, request);
        }
    }

    @Test
    void malformedSelectorCannotMasqueradeAsAnOmittedRadius() {
        for (String selector : new String[] {"null", "[]", "0", "true", "\"selector\""})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DeepObservationSelector.chunkRadius(request(selector)));
    }
}
