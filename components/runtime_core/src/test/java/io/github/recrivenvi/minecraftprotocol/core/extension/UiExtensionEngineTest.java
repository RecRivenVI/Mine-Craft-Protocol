package io.github.recrivenvi.minecraftprotocol.core.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.probe.extension.UiAdapter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UiExtensionEngineTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject tree() {
        return json(
                """
                {"screenClass":"Screen","screenIdentity":7,"screenRevision":3,"width":480,"height":270,
                 "windowWidth":960,"windowHeight":540,"guiScale":4,"children":[
                   {"nodeId":"widget:7:10","elementIdentity":10,"x":20,"y":30,"width":40,"height":15,
                    "visible":true,"active":true,"label":"native","actions":["click"]}]}
                """);
    }

    private static UiAdapter adapter(
            java.util.function.Function<UiAdapter.Context, UiAdapter.Projection> capture) {
        return new UiAdapter() {
            public Descriptor descriptor() {
                return new Descriptor("example:ui", Set.of(View.USER, View.DEBUG), 32, 1000);
            }

            public boolean matches(Frame frame) {
                return true;
            }

            public Projection capture(Context context) {
                return capture.apply(context);
            }
        };
    }

    private static UiAdapter.Node region(String key, UiAdapter.Space space, String properties) {
        return new UiAdapter.Node(key, 0, "target-v1", space, json(properties));
    }

    private static void capture(JsonObject tree, UiAdapter.View view, UiAdapter... adapters) {
        new UiExtensionEngine("runtime", () -> List.of(adapters))
                .project(tree, "world", new Object(), 1920, 1080, object -> 10, () -> true, view);
    }

    @Test
    void nativeEnrichmentPreservesIdentityGeometryAndPublishesDetachedProperties() {
        var original = json("{\"label\":\"terminal\",\"value\":\"ready\"}");
        var item = new UiAdapter.Node("terminal", 10, "terminal-1", UiAdapter.Space.GUI, original);
        original.addProperty("label", "changed");
        var root = tree();
        capture(
                root,
                UiAdapter.View.USER,
                adapter(
                        c ->
                                new UiAdapter.Projection(
                                        c.frame(), System.nanoTime(), List.of(item))));
        var nativeNode = root.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("widget:7:10", nativeNode.get("nodeId").getAsString());
        assertEquals(20, nativeNode.get("x").getAsInt());
        assertEquals("terminal", nativeNode.get("label").getAsString());
        assertEquals(
                "terminal-1",
                nativeNode
                        .getAsJsonObject("semanticState")
                        .get("extensionTargetRevision")
                        .getAsString());
    }

    @Test
    void explicitCoordinateSpacesClipWithoutMovingOrRoundingTheInputPoint() {
        var root = tree();
        capture(
                root,
                UiAdapter.View.USER,
                adapter(
                        c ->
                                new UiAdapter.Projection(
                                        c.frame(),
                                        System.nanoTime(),
                                        List.of(
                                                region(
                                                        "window",
                                                        UiAdapter.Space.WINDOW,
                                                        "{\"x\":201.5,\"y\":101.5,\"width\":100,\"height\":40,\"visible\":true}"),
                                                region(
                                                        "framebuffer",
                                                        UiAdapter.Space.FRAMEBUFFER,
                                                        "{\"x\":-20,\"y\":40,\"width\":100,\"height\":40,\"visible\":true}")))));
        var nodes = root.getAsJsonArray("children");
        assertEquals(100.75, nodes.get(1).getAsJsonObject().get("x").getAsDouble());
        assertEquals(20, nodes.get(2).getAsJsonObject().get("width").getAsDouble());
        assertEquals(3, root.get("treeNodeCount").getAsInt());
    }

    @Test
    void invalidSecondNodeRollsBackTheWholeAdapterAndDoesNotHideOtherPackages() {
        var root = tree();
        var bad =
                adapter(
                        c ->
                                new UiAdapter.Projection(
                                        c.frame(),
                                        System.nanoTime(),
                                        List.of(
                                                new UiAdapter.Node(
                                                        "native",
                                                        10,
                                                        "1",
                                                        UiAdapter.Space.GUI,
                                                        json("{\"label\":\"must not publish\"}")),
                                                region(
                                                        "bad",
                                                        UiAdapter.Space.GUI,
                                                        "{\"nodeId\":\"forged\"}"))));
        var good =
                adapter(
                        c ->
                                new UiAdapter.Projection(
                                        c.frame(),
                                        System.nanoTime(),
                                        List.of(
                                                region(
                                                        "good",
                                                        UiAdapter.Space.GUI,
                                                        "{\"x\":1,\"y\":2,\"width\":3,\"height\":4,\"visible\":true}"))));
        capture(root, UiAdapter.View.USER, bad, good);
        assertEquals(
                "native",
                root.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("label")
                        .getAsString());
        assertEquals(2, root.getAsJsonArray("children").size());
        assertTrue(root.get("treeLimitations").toString().contains("EXTENSION_UI_NODE_INVALID"));
    }

    @Test
    void staleScreenResizeOrAgeCannotPublishAndNativeContextCannotEscape() {
        var leaked = new AtomicReference<UiAdapter.Context>();
        var old = new AtomicReference<UiAdapter.Projection>();
        var adapter =
                adapter(
                        c -> {
                            leaked.set(c);
                            return old.updateAndGet(
                                    value ->
                                            value == null
                                                    ? new UiAdapter.Projection(
                                                            c.frame(),
                                                            System.nanoTime(),
                                                            List.of(
                                                                    region(
                                                                            "one",
                                                                            UiAdapter.Space.GUI,
                                                                            "{\"x\":1,\"y\":2,\"width\":3,\"height\":4,\"visible\":true}")))
                                                    : value);
                        });
        var root = tree();
        capture(root, UiAdapter.View.USER, adapter);
        assertThrows(IllegalStateException.class, () -> leaked.get().nativeScreen());
        for (String field :
                List.of("screenIdentity", "screenRevision", "width", "windowWidth", "guiScale")) {
            root = tree();
            root.addProperty(field, 99);
            capture(root, UiAdapter.View.USER, adapter);
            assertEquals(1, root.getAsJsonArray("children").size(), field);
            assertTrue(root.get("treeLimitations").toString().contains("SNAPSHOT_STALE"));
        }
        root = tree();
        capture(
                root,
                UiAdapter.View.USER,
                adapter(c -> new UiAdapter.Projection(c.frame(), 0, List.of())));
        assertTrue(root.get("treeLimitations").toString().contains("SNAPSHOT_STALE"));
    }

    @Test
    void userNeverReceivesInvisibleRegionsAndDebugRegionsHaveNoUserActions() {
        var hidden =
                adapter(
                        c ->
                                new UiAdapter.Projection(
                                        c.frame(),
                                        System.nanoTime(),
                                        List.of(
                                                region(
                                                        "hidden",
                                                        UiAdapter.Space.GUI,
                                                        "{\"x\":1,\"y\":2,\"width\":3,\"height\":4,\"visible\":false,\"actions\":[\"click\"]}"))));
        var user = tree();
        capture(user, UiAdapter.View.USER, hidden);
        assertEquals(1, user.getAsJsonArray("children").size());
        var debug = tree();
        capture(debug, UiAdapter.View.DEBUG, hidden);
        assertEquals(
                0,
                debug.getAsJsonArray("children")
                        .get(1)
                        .getAsJsonObject()
                        .getAsJsonArray("actions")
                        .size());
        assertThrows(
                IllegalStateException.class,
                () ->
                        new UiExtensionEngine("r", List::of)
                                .project(
                                        tree(),
                                        "w",
                                        null,
                                        1920,
                                        1080,
                                        o -> 1,
                                        () -> false,
                                        UiAdapter.View.USER));
    }
}
