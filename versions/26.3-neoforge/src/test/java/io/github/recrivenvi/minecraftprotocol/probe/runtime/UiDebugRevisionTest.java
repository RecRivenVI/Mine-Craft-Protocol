package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class UiDebugRevisionTest {
    @Test
    void nativeContainerAndItsRenderedRegionsCannotAliasOrChurnTokens() {
        try (var engine = new Phase9ASpikeEngine("test")) {
            JsonObject tree =
                    JsonParser.parseString(
                                    """
                {"screenIdentity":7,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
                  {"nodeId":"list","elementIdentity":10,"capabilitySource":"native_widget_contract","scrollAmount":20,"hovered":true,"children":[]},
                  {"nodeId":"scrollbar","elementIdentity":10,"capabilitySource":"confirmed_vanilla_input_handler","scrollAmount":20}
                ]}
                """)
                            .getAsJsonObject();
            JsonObject next = tree.deepCopy();
            UiDebugEngine.describe(tree, engine);
            UiDebugEngine.describe(next, engine);
            JsonObject first = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            JsonObject second = next.getAsJsonArray("children").get(0).getAsJsonObject();
            assertEquals(first.get("resourceVersion"), second.get("resourceVersion"));
            assertFalse(
                    next.getAsJsonArray("children")
                            .get(1)
                            .getAsJsonObject()
                            .has("resourceVersion"));
            second.addProperty("scrollAmount", 21);
            UiDebugEngine.describe(next, engine);
            assertNotEquals(first.get("resourceVersion"), second.get("resourceVersion"));
            assertEquals(
                    "ui_node",
                    second.getAsJsonObject("resourceVersion").get("resourceType").getAsString());
        }
    }
}
