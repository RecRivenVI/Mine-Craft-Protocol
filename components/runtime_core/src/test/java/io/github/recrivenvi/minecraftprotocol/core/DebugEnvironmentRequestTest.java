package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugEnvironmentRequestTest {
    @Test
    void typedNativeValuesDoNotCoerceStringsFractionsOrHiddenPayloads() {
        for (String value : new String[] {"\"1\"", "1.5", "1e50", "null", "true"})
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugEnvironmentRequest.ticks(JsonParser.parseString(value)));
        assertEquals(-2000, DebugEnvironmentRequest.ticks(JsonParser.parseString("-2000")));
        for (String value : new String[] {"\"true\"", "1.5", "2147483648", "{}"})
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugEnvironmentRequest.rule(JsonParser.parseString(value)));
        var weather =
                JsonParser.parseString(
                        "{\"clearWeatherTime\":0,\"rainTime\":6000,\"thunderTime\":3000,\"raining\":true,\"thundering\":false}");
        assertEquals(weather, DebugEnvironmentRequest.weather(weather));
        weather.getAsJsonObject().addProperty("nbt", "{}");
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> DebugEnvironmentRequest.weather(weather));
    }

    @Test
    void independentNativeEditGenerationsDoNotAdvanceOnReadAndDoAdvanceForAbaEdits() {
        Object clock = new Object(), other = new Object();
        assertEquals(0, EnvironmentControlRevisions.time(clock));
        assertEquals(0, EnvironmentControlRevisions.weather(clock));
        EnvironmentControlRevisions.timeEdited(clock);
        long first = EnvironmentControlRevisions.time(clock);
        assertTrue(first > 0);
        assertEquals(first, EnvironmentControlRevisions.time(clock));
        assertEquals(0, EnvironmentControlRevisions.time(other));
        EnvironmentControlRevisions.weatherEdited(clock);
        assertEquals(first, EnvironmentControlRevisions.time(clock));
        assertTrue(EnvironmentControlRevisions.weather(clock) > first);
        EnvironmentControlRevisions.timeEdited(clock);
        assertTrue(EnvironmentControlRevisions.time(clock) > first);
    }
}
