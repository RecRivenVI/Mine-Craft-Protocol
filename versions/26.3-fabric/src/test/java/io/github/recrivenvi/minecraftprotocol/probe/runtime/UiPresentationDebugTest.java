package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import org.junit.jupiter.api.Test;

class UiPresentationDebugTest {

    @Test
    void presentationFieldsAreNotPlayerTargetsAndUserCannotReadTheirPrivateGuard() {
        var tree =
                JsonParser.parseString(
                                """
            {"children":[],"presentation":{"children":[
              {"nodeId":"hud.title","controlKey":"hud.title","role":"hud_text","label":"shown","value":"shown","visible":true,"active":false,"actions":[],"nativeTextPresent":true,"debugTextGuardAvailable":true,"resourceVersion":{"revision":1}},
              {"nodeId":"hud.subtitle","controlKey":"hud.subtitle","role":"hud_text","label":"hidden","value":"hidden","visible":false,"active":false,"actions":[]}
            ]}}
            """)
                        .getAsJsonObject();
        assertNotNull(UiDebugEngine.findNode(tree, "hud.title"));
        assertNull(UiDebugEngine.find(tree.getAsJsonArray("children"), "hud.title"));
        var user = UiDataView.tree(tree, UiDataView.Category.USER);
        var nodes = user.getAsJsonObject("presentation").getAsJsonArray("children");
        assertEquals(1, nodes.size());
        var field = nodes.get(0).getAsJsonObject();
        assertEquals("shown", field.get("value").getAsString());
        for (String name :
                new String[] {"nativeTextPresent", "debugTextGuardAvailable", "resourceVersion"})
            assertFalse(field.has(name));
        assertEquals(0, field.getAsJsonArray("actions").size());
        assertTrue(
                UiDataView.tree(tree, UiDataView.Category.DEBUG)
                        .getAsJsonObject("presentation")
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .has("resourceVersion"));
    }
}
