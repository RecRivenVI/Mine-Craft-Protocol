package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

final class WorldActionRequestTest {
    private WorldActionRequest parse(String fields) {
        return WorldActionRequest.parse(
                JsonParser.parseString(
                                "{\"type\":\"world.action\",\"targetRef\":\"world:test\","
                                        + fields
                                        + "}")
                        .getAsJsonObject());
    }

    @Test
    void boundedHoldsAndNamedFaceCoordinates() {
        assertEquals(5000, parse("\"action\":\"mine\"").holdMs());
        assertEquals(1000, parse("\"action\":\"use_hold\"").holdMs());
        assertEquals(
                "main_hand", parse("\"action\":\"use_hold\"").options().get("hand").getAsString());
        assertEquals(
                "off_hand",
                parse("\"action\":\"use_hold\",\"hand\":\"off_hand\"")
                        .options()
                        .get("hand")
                        .getAsString());
        assertEquals(10000, parse("\"action\":\"mine\",\"holdMs\":10000").holdMs());
        var place =
                parse(
                        "\"action\":\"place\",\"face\":\"west\",\"hit\":{\"u\":0.25,\"v\":0.75},\"expectedDestinationBlockId\":\"minecraft:air\"");
        assertEquals(.25, place.options().get("u").getAsDouble());
        assertEquals(.75, place.options().get("v").getAsDouble());
        for (String fields :
                new String[] {
                    "\"action\":\"mine\",\"holdMs\":10001",
                    "\"action\":\"mine\",\"holdMs\":49",
                    "\"action\":\"mine\",\"holdMs\":50.5",
                    "\"action\":\"mine\",\"holdMs\":\"100\"",
                    "\"action\":\"place\"",
                    "\"action\":\"place\",\"face\":\"side\"",
                    "\"action\":\"place\",\"face\":\"up\",\"hit\":{\"u\":0}",
                    "\"action\":\"aim\",\"face\":\"up\"",
                    "\"action\":\"use\",\"holdMs\":100",
                    "\"action\":\"mine\",\"teleport\":true",
                    "\"action\":\"use_hold\",\"hand\":\"left\"",
                    "\"action\":\"use_hold\",\"hand\":false",
                    "\"action\":\"mine\",\"hand\":\"off_hand\""
                }) assertThrows(RuntimeException.class, () -> parse(fields), fields);
    }
}
