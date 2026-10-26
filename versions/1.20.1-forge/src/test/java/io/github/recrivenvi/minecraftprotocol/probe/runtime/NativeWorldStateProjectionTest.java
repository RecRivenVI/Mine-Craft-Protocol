package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class NativeWorldStateProjectionTest {
    @Test
    void actualNativeProjectorKeepsApprovedStateFieldsOnlyInTheirDeclaredGroups() throws Exception {
        JsonObject snapshot =
                JsonParser.parseString(
                                """
                {"player":{"uuid":"p","food":17,"saturation":5,"air":200,"maxAirSupply":300,"experiencePoints":2,"privateUnprojected":"x"},
                "entities":[{"uuid":"e","health":19,"maxHealth":20,"absorption":0,"air":200,"maxAirSupply":300,"silent":true,"glowing":false,"noGravity":true,"privateUnprojected":"x"}]}
                """)
                        .getAsJsonObject();
        JsonObject request =
                JsonParser.parseString(
                                """
                {"projection":{"playerFields":["vitals"],"entityFields":["living","common_state"]}}
                """)
                        .getAsJsonObject();
        var method =
                Phase9ASpikeEngine.class.getDeclaredMethod(
                        "applyProjection", JsonObject.class, JsonObject.class);
        method.setAccessible(true);
        method.invoke(null, snapshot, request);
        assertEquals(5, snapshot.getAsJsonObject("player").get("saturation").getAsInt());
        assertEquals(2, snapshot.getAsJsonObject("player").get("experiencePoints").getAsInt());
        assertEquals(300, snapshot.getAsJsonObject("player").get("maxAirSupply").getAsInt());
        JsonObject entity = snapshot.getAsJsonArray("entities").get(0).getAsJsonObject();
        assertTrue(entity.get("silent").getAsBoolean());
        assertFalse(entity.get("glowing").getAsBoolean());
        assertEquals(0, entity.get("absorption").getAsInt());
        assertFalse(entity.has("privateUnprojected"));
        assertFalse(snapshot.getAsJsonObject("player").has("privateUnprojected"));
    }
}
