package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SemanticTextReplacementTest {
    private static final class Editor {
        final PointerGestureTest.World frame = new PointerGestureTest.World();
        final List<Integer> keys = new ArrayList<>();
        final Set<Integer> held = new HashSet<>();
        String value = "A😀中", strategy = "backspace_from_end";
        boolean complete = true, selectAllSupported, selected, brokenBackspace;
        volatile boolean revoked;
        int characters;
        Runnable afterBackspace = () -> {};

        JsonObject tree() {
            var tree = frame.tree();
            var field = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            field.addProperty("role", "text_field");
            field.addProperty("class", "NativeKeyboardEditor");
            field.addProperty("focused", true);
            field.addProperty("inputRoute", "screen_keyboard");
            field.addProperty("boundsAvailable", false);
            field.addProperty("value", value);
            field.addProperty("textReplaceStrategy", strategy);
            field.addProperty("valueComplete", complete);
            field.add("actions", JsonParser.parseString("[\"type_text\"]"));
            return tree;
        }

        ProbeService service() {
            return (ProbeService)
                    Proxy.newProxyInstance(
                            ProbeService.class.getClassLoader(),
                            new Class<?>[] {ProbeService.class},
                            (proxy, method, args) -> {
                                if (method.getReturnType() == void.class) return null;
                                if (Set.of(
                                                "pointerState",
                                                "inputPacingState",
                                                "uiTree",
                                                "debugUiTree")
                                        .contains(method.getName()))
                                    return CompletableFuture.completedFuture(tree());
                                if (method.getName().equals("keyGuarded")) {
                                    AutomationEngine.validatePointerGuard(
                                            (JsonObject) args[4], tree());
                                    int key = (int) args[0], action = (int) args[2];
                                    if (action == 1) {
                                        keys.add(key);
                                        held.add(key);
                                        if (key == 65 && selectAllSupported && held.contains(341))
                                            selected = true;
                                        if (key == 259 && !brokenBackspace) {
                                            if (selected) value = "";
                                            else if (!value.isEmpty())
                                                value =
                                                        value.substring(
                                                                0,
                                                                value.offsetByCodePoints(
                                                                        value.length(), -1));
                                            selected = false;
                                            afterBackspace.run();
                                        }
                                    } else held.remove(key);
                                }
                                if (method.getName().equals("character")) {
                                    AutomationEngine.validatePointerGuard(
                                            (JsonObject) args[1], tree());
                                    characters++;
                                    value += new String(Character.toChars((int) args[0]));
                                }
                                if (method.getName().equals("releaseAllInput")) held.clear();
                                return CompletableFuture.completedFuture(new JsonObject());
                            });
        }
    }

    private JsonObject replace(String value) {
        var request =
                JsonParser.parseString(
                                "{\"selector\":{\"nodeId\":\"button-0\"},\"action\":\"type_text\",\"replace\":true}")
                        .getAsJsonObject();
        request.addProperty("text", value);
        return request;
    }

    @Test
    void appendOnlyNativeEditorClearsUnicodeThroughRealBackspaceEventsBeforeTyping()
            throws Exception {
        for (String next : List.of("", "Replacement")) {
            Editor editor = new Editor();
            try (var engine = new AutomationEngine(editor.service())) {
                engine.uiAction(replace(next), () -> {}).get(3, TimeUnit.SECONDS);
                assertEquals(next, editor.value);
                assertEquals(List.of(259, 259, 259), editor.keys);
                assertTrue(editor.held.isEmpty());
            }
        }
    }

    @Test
    void ordinaryEditorStillUsesItsNativeSelectAllPath() throws Exception {
        Editor editor = new Editor();
        editor.strategy = "select_all";
        editor.selectAllSupported = true;
        try (var engine = new AutomationEngine(editor.service())) {
            engine.uiAction(replace("Replacement"), () -> {}).get(3, TimeUnit.SECONDS);
            assertEquals("Replacement", editor.value);
            assertEquals(List.of(341, 65, 259), editor.keys);
            assertTrue(editor.held.isEmpty());
        }
    }

    @Test
    void incompleteOrUnknownEditorContractRefusesBeforeAnyInput() throws Exception {
        for (String strategy : List.of("backspace_from_end", "unknown")) {
            Editor editor = new Editor();
            editor.strategy = strategy;
            editor.complete = false;
            try (var engine = new AutomationEngine(editor.service())) {
                assertThrows(
                        ExecutionException.class,
                        () ->
                                engine.uiAction(replace("MUST NOT ARRIVE"), () -> {})
                                        .get(3, TimeUnit.SECONDS));
                assertTrue(editor.keys.isEmpty());
                assertEquals(0, editor.characters);
                assertEquals("A😀中", editor.value);
            }
        }
    }

    @Test
    void failedNativeClearNeverAppendsReplacementToTheOldValue() throws Exception {
        Editor editor = new Editor();
        editor.strategy = "select_all";
        editor.brokenBackspace = true;
        try (var engine = new AutomationEngine(editor.service())) {
            assertThrows(
                    ExecutionException.class,
                    () ->
                            engine.uiAction(replace("MUST NOT ARRIVE"), () -> {})
                                    .get(3, TimeUnit.SECONDS));
            assertEquals("A😀中", editor.value);
            assertEquals(0, editor.characters);
            assertTrue(editor.held.isEmpty());
        }
    }

    @Test
    void revokeOrScreenReplacementDuringClearHasNoLateKeyOrCharacter() throws Exception {
        for (boolean revoke : List.of(false, true)) {
            Editor editor = new Editor();
            editor.afterBackspace =
                    () -> {
                        if (revoke) editor.revoked = true;
                        else editor.frame.revision++;
                    };
            try (var engine = new AutomationEngine(editor.service())) {
                assertThrows(
                        ExecutionException.class,
                        () ->
                                engine.uiAction(
                                                replace("MUST NOT ARRIVE"),
                                                () -> {
                                                    if (editor.revoked)
                                                        throw new ProtocolState.ProtocolException(
                                                                "CONTROL_LEASE_REQUIRED",
                                                                409,
                                                                "revoked");
                                                })
                                        .get(3, TimeUnit.SECONDS));
                assertEquals(List.of(259), editor.keys);
                assertEquals(0, editor.characters);
                assertTrue(editor.held.isEmpty());
            }
        }
    }
}
