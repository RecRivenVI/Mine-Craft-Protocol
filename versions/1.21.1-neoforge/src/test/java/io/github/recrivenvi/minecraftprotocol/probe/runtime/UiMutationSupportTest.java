package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class UiMutationSupportTest {
    @Test
    void productionCaptureUsesActualNativeOwnerNotSpoofedRoleOrClass() {
        Button button =
                Button.builder(Component.literal("Native"), ignored -> {})
                        .bounds(10, 20, 80, 20)
                        .build();
        JsonObject tree = tree();
        tree.add(
                "children",
                new VanillaUiTree(1, System::identityHashCode)
                        .captureChildren(List.of(button), new ScreenRectangle(0, 0, 320, 240)));
        JsonObject node = tree.getAsJsonArray("children").get(0).getAsJsonObject();
        node.addProperty("role", "slider");
        node.addProperty("class", "net.minecraft.client.gui.components.EditBox");
        try (var revisions = new Phase9ASpikeEngine("test")) {
            UiDebugEngine.describe(tree, revisions, Map.of());
            UiDebugEngine.describeMutationSupport(
                    tree,
                    null,
                    Map.of((long) System.identityHashCode(button), button),
                    System::identityHashCode);
        }
        var operations = node.getAsJsonObject("debugMutation").getAsJsonObject("operations");
        assertTrue(operations.has("ui.widget.active.set"));
        assertTrue(operations.has("ui.widget.visible.set"));
        assertEquals(2, operations.size());
        assertFalse(operations.has("ui.slider.set"));
        assertFalse(operations.has("ui.text.set"));
        assertFalse(
                UiDataView.tree(tree, UiDataView.Category.USER)
                        .toString()
                        .contains("debugMutation"));
        assertTrue(
                UiDataView.tree(tree, UiDataView.Category.DEBUG)
                        .toString()
                        .contains("debugMutation"));
    }

    @Test
    void nativeListenerAndSemanticSlotDoNotPretendToHaveWidgetSetters() {
        GuiEventListener listener =
                new AbstractContainerEventHandler() {
                    @Override
                    public List<? extends GuiEventListener> children() {
                        return List.of();
                    }

                    @Override
                    public ScreenRectangle getRectangle() {
                        return new ScreenRectangle(0, 0, 100, 100);
                    }
                };
        JsonObject tree = tree();
        tree.add(
                "children",
                new VanillaUiTree(1, System::identityHashCode)
                        .captureChildren(List.of(listener), new ScreenRectangle(0, 0, 320, 240)));
        JsonObject slot =
                JsonParser.parseString("{\"nodeId\":\"slot\",\"role\":\"slot\"}").getAsJsonObject();
        tree.getAsJsonArray("children").add(slot);
        try (var revisions = new Phase9ASpikeEngine("test")) {
            UiDebugEngine.describe(tree, revisions, Map.of());
            UiDebugEngine.describeMutationSupport(
                    tree,
                    null,
                    Map.of((long) System.identityHashCode(listener), listener),
                    System::identityHashCode);
        }
        assertEquals(
                0,
                tree.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("debugMutation")
                        .getAsJsonArray("supportedOperations")
                        .size());
        assertEquals(
                "MENU_ITEM_REQUIRES_WORLD_RESOURCE_AUTHORIZATION",
                slot.getAsJsonObject("debugMutation").get("limitation").getAsString());
    }

    @Test
    void descriptorDoesNotChurnTheNativeResourceVersion() {
        Button button =
                Button.builder(Component.literal("Native"), ignored -> {})
                        .bounds(10, 20, 80, 20)
                        .build();
        JsonObject tree = tree();
        tree.add(
                "children",
                new VanillaUiTree(1, System::identityHashCode)
                        .captureChildren(List.of(button), new ScreenRectangle(0, 0, 320, 240)));
        try (var revisions = new Phase9ASpikeEngine("test")) {
            UiDebugEngine.describe(tree, revisions, Map.of());
            JsonObject node = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            JsonElement version = node.get("resourceVersion").deepCopy();
            UiDebugEngine.describeMutationSupport(
                    tree,
                    null,
                    Map.of((long) System.identityHashCode(button), button),
                    System::identityHashCode);
            UiDebugEngine.describe(tree, revisions, Map.of());
            assertEquals(version, node.get("resourceVersion"));
            node.addProperty("active", false);
            UiDebugEngine.describe(tree, revisions, Map.of());
            assertNotEquals(version, node.get("resourceVersion"));
        }
    }

    private static JsonObject tree() {
        return JsonParser.parseString(
                        "{\"screenIdentity\":7,\"screenRevision\":1,\"width\":320,"
                                + "\"height\":240,\"guiScale\":1,\"overlayIdentity\":0,\"children\":[]}")
                .getAsJsonObject();
    }
}
