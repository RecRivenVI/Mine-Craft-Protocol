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
    void useCanSelectANativeBlockSurfaceButDoesNotInheritPlacementOrHoldFields() {
        assertFalse(parse("\"action\":\"use\"").options().has("face"));
        var use =
                parse(
                        "\"action\":\"use\",\"face\":\"north\",\"hit\":{\"u\":0.8333333333333334,\"v\":0.75}");
        assertEquals("north", use.options().get("face").getAsString());
        assertEquals(.8333333333333334, use.options().get("u").getAsDouble());
        assertEquals(.75, use.options().get("v").getAsDouble());
        assertEquals(
                .5, parse("\"action\":\"use\",\"face\":\"up\"").options().get("u").getAsDouble());
        for (String fields :
                new String[] {
                    "\"action\":\"use\",\"hit\":{\"u\":0.5}",
                    "\"action\":\"use\",\"face\":\"side\"",
                    "\"action\":\"use\",\"face\":\"north\",\"hit\":{\"v\":1}",
                    "\"action\":\"use\",\"face\":\"north\",\"expectedDestinationBlockId\":\"minecraft:air\"",
                    "\"action\":\"use_hold\",\"face\":\"up\"",
                    "\"action\":\"attack\",\"face\":\"up\""
                }) assertThrows(RuntimeException.class, () -> parse(fields), fields);
    }

    @Test
    void entityBodyPointsAreExplicitNormalizedCoordinatesNotScreenOrBlockFaces() {
        var point =
                parse("\"action\":\"use\",\"entityHit\":{\"x\":0.5,\"y\":0.9,\"z\":0.05}")
                        .options()
                        .getAsJsonObject("entityHit");
        assertEquals(.9, point.get("y").getAsDouble());
        for (String fields :
                new String[] {
                    "\"action\":\"mine\",\"entityHit\":{\"x\":.5,\"y\":.5,\"z\":.5}",
                    "\"action\":\"place\",\"face\":\"up\",\"entityHit\":{\"x\":.5,\"y\":.5,\"z\":.5}",
                    "\"action\":\"use\",\"face\":\"up\",\"entityHit\":{\"x\":.5,\"y\":.5,\"z\":.5}",
                    "\"action\":\"use\",\"entityHit\":{\"x\":.5,\"y\":1,\"z\":.5}",
                    "\"action\":\"use\",\"entityHit\":{\"x\":.5,\"y\":.5}",
                    "\"action\":\"use\",\"entityHit\":{\"x\":.5,\"y\":.5,\"z\":\".5\"}"
                }) assertThrows(RuntimeException.class, () -> parse(fields), fields);
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
