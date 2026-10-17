package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class WorldHitEvidenceTest {
    @Test
    void malformedOrUnboundedHitsStayUnknownAndCannotPublishAnArbitraryGuard() {
        for (String json :
                new String[] {
                    "{}",
                    "{\"kind\":\"block\",\"guard\":\"secret\"}",
                    "{\"kind\":\"miss\",\"distance\":33,\"position\":{\"x\":0,\"y\":0,\"z\":0}}",
                    "{\"kind\":\"entity\",\"distance\":2,\"entityType\":\"secret instructions\",\"position\":{\"x\":0,\"y\":0,\"z\":0}}"
                }) {
            var hit = WorldHitEvidence.bounded(JsonParser.parseString(json).getAsJsonObject());
            assertEquals("unknown", hit.get("kind").getAsString());
            assertEquals("USER", hit.get("dataCategory").getAsString());
            assertFalse(hit.toString().contains("secret"));
        }
        assertTrue(WorldHitEvidence.details(null).isEmpty());
    }

    @Test
    void entityAndMissRetainOnlyClientGeometryAndNeverInternalIdentity() {
        for (String kind : new String[] {"entity", "miss"}) {
            JsonObject source =
                    JsonParser.parseString(
                                    """
                    {"kind":"miss","source":"bounded_client_vanilla_ray","distance":3.25,
                     "position":{"x":1.125,"y":2,"z":3.75},"entityType":"minecraft:pig","uuid":"secret"}
                    """)
                            .getAsJsonObject();
            source.addProperty("kind", kind);
            var hit = WorldHitEvidence.bounded(source);
            assertEquals(kind, hit.get("kind").getAsString());
            assertEquals(1.125, hit.getAsJsonObject("position").get("x").getAsDouble());
            assertFalse(hit.has("uuid"));
            assertFalse(hit.has("face"));
            assertEquals(kind.equals("entity"), hit.has("entityType"));
        }
    }
}
