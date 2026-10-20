package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class UiSpectatorSelectionDebugTest {
    @Test
    void highlightRequiresAnExistingCurrentPageItemAndNeverWritesInitialMinusOne() {
        assertEquals(0, UiSpectatorSelectionDebug.validate(new JsonPrimitive(0), state()));
        assertEquals(8, UiSpectatorSelectionDebug.validate(new JsonPrimitive(8.0), state()));
        for (String invalid :
                List.of("null", "false", "\"0\"", "{}", "[]", "-1", "9", "0.25", "1e999"))
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiSpectatorSelectionDebug.validate(
                                                    JsonParser.parseString(invalid), state()))
                            .code(),
                    invalid);
        var changed = state();
        changed.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("empty", true);
        assertEquals(
                "INVALID_UI_DEBUG_VALUE",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        UiSpectatorSelectionDebug.validate(
                                                new JsonPrimitive(1), changed))
                        .code());
        assertEquals(
                -1,
                state().get("value").getAsInt(),
                "The native initial value is observed, not a writable pseudo-slot");
    }

    @Test
    void productionDescriptionVersionsTheActualMenuCategoryPageAndItemContent() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var node = node(tree);
            UiSpectatorSelectionDebug.describeState(tree, node, revisions, state());
            var old = node.getAsJsonObject("resourceVersion").deepCopy();
            assertEquals(-1, node.get("debugSelectionIndex").getAsInt());
            assertEquals(
                    List.of("ui.list.selection.set"),
                    node
                            .getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .asList()
                            .stream()
                            .map(value -> value.getAsString())
                            .toList());
            node.getAsJsonObject("debugMutation").addProperty("notNative", true);
            UiSpectatorSelectionDebug.describeState(tree, node, revisions, state());
            assertEquals(old, node.get("resourceVersion"));
            for (String key :
                    List.of(
                            "worldOwner",
                            "playerOwner",
                            "guiOwner",
                            "menuOwner",
                            "categoryOwner",
                            "page",
                            "value",
                            "items",
                            "contentFingerprint")) {
                var changed = state();
                if (key.equals("items"))
                    changed.getAsJsonArray("items")
                            .get(0)
                            .getAsJsonObject()
                            .addProperty("owner", 999);
                else if (key.equals("contentFingerprint"))
                    changed.addProperty(key, "changed-full-component-content");
                else changed.addProperty(key, changed.get(key).getAsLong() + 1);
                UiSpectatorSelectionDebug.describeState(tree, node, revisions, changed);
                assertEquals(
                        key.equals("menuOwner") ? "RESOURCE_MISMATCH" : "STALE_RESOURCE_REVISION",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () ->
                                                ResourceVersionVerifier.verify(
                                                        old,
                                                        node.getAsJsonObject("resourceVersion")))
                                .code(),
                        key);
            }
            UiSpectatorSelectionDebug.describeState(tree, node, revisions, state());
            assertNotEquals(old, node.get("resourceVersion"));
        }
    }

    @Test
    void missingFullGuardCannotAdvertiseOrPublishAMutableOwner() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var state = state();
            UiSpectatorSelectionDebug.describeState(tree, node(tree), revisions, state);
            assertTrue(node(tree).has("resourceVersion"));
            state.addProperty("guardAvailable", false);
            UiSpectatorSelectionDebug.describeState(tree, node(tree), revisions, state);
            assertFalse(node(tree).has("resourceVersion"));
            assertTrue(
                    node(tree)
                            .getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .isEmpty());
        }
    }

    @Test
    void userProjectionNeverExposesTheDebugOnlyNativeMenuOwner() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            UiSpectatorSelectionDebug.describeState(tree, node(tree), revisions, state());
            var user = UiDataView.tree(tree, UiDataView.Category.USER);
            var debug = UiDataView.tree(tree, UiDataView.Category.DEBUG);
            for (String field :
                    List.of(
                            "debugSelectionIndex",
                            "debugSelectionRange",
                            "debugSelectionUnit",
                            "debugSelectionGuardAvailable",
                            "resourceVersion",
                            "debugMutation")) {
                assertFalse(user.toString().contains(field), field);
                assertTrue(node(debug).has(field), field);
            }
        }
    }

    @Test
    void anAbsentNativeMenuCannotBeReplacedWithASyntheticOwner() {
        assertNull(UiSpectatorSelectionDebug.target(null));
        assertEquals(
                "UI_CONTEXT_UNAVAILABLE",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        UiSpectatorSelectionDebug.prepare(
                                                null,
                                                new JsonPrimitive(0),
                                                System::identityHashCode,
                                                new UiItemDataGuard()))
                        .code());
        assertTrue(UiSpectatorSelectionDebug.handles("spectator.menu"));
        assertFalse(UiSpectatorSelectionDebug.handles("spectator.slot.0"));
    }

    @Test
    void productionSameIndexReturnsBeforeTheOnlyNativeSelectAndNeverActivatesAnItem()
            throws Exception {
        var helper =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiSpectatorSelectionDebug");
        var select =
                helper.methods.stream()
                        .filter(method -> method.name.equals("selectHighlight"))
                        .findFirst()
                        .orElseThrow();
        var code = select.instructions.toArray();
        int currentRead = -1, conditional = -1, earlyReturn = -1, nativeSelect = -1;
        JumpInsnNode branch = null;
        for (int index = 0; index < code.length; index++) {
            if (code[index] instanceof MethodInsnNode call && call.name.equals("getSelectedSlot"))
                currentRead = index;
            if (code[index] instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ICMPNE) {
                conditional = index;
                branch = jump;
            }
            if (code[index].getOpcode() == Opcodes.RETURN && nativeSelect < 0) earlyReturn = index;
            if (code[index] instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/gui/spectator/SpectatorMenu")
                    && call.name.equals("selectSlot")) nativeSelect = index;
        }
        assertTrue(
                currentRead >= 0
                        && currentRead < conditional
                        && conditional < earlyReturn
                        && earlyReturn < nativeSelect);
        assertNotNull(branch);
        assertTrue(select.instructions.indexOf(branch.label) > earlyReturn);
        int prepareSnapshots = 0;
        for (var method : helper.methods)
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (method.name.startsWith("lambda$prepare$") && call.name.equals("snapshot"))
                    prepareSnapshots++;
                assertNotEquals("selectItem", call.name);
                assertNotEquals("selectCategory", call.name);
                assertNotEquals("exit", call.name);
                assertFalse(call.owner.startsWith("net/minecraft/network/protocol/"));
                assertFalse(call.owner.contains("InputDispatchContext"));
            }
        assertEquals(
                1, prepareSnapshots, "Commit rechecks all owners, page and guarded item content");
        // The actual native implementation has an activation branch. This verifies the guard is
        // protecting a real distinction, without pretending to run a Loader-owned menu in JUnit.
        var nativeMenu = compiled("net/minecraft/client/gui/spectator/SpectatorMenu");
        var nativeMethod =
                nativeMenu.methods.stream()
                        .filter(method -> method.name.equals("selectSlot"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                java.util.stream.StreamSupport.stream(
                                nativeMethod.instructions.spliterator(), false)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.name.equals("selectItem")));
        var engine = compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/UiDebugEngine");
        for (String entry : List.of("describe", "mutate"))
            assertTrue(
                    engine.methods.stream()
                            .filter(method -> method.name.equals(entry))
                            .anyMatch(
                                    method -> {
                                        for (var instruction : method.instructions)
                                            if (instruction instanceof MethodInsnNode call
                                                    && call.owner.endsWith(
                                                            "/UiSpectatorSelectionDebug")
                                                    && call.name.equals(entry)) return true;
                                        return false;
                                    }));
    }

    private static JsonObject tree() {
        return JsonParser.parseString(
                        """
                {"screenIdentity":0,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
                {"nodeId":"spectator.menu","elementIdentity":11,"controlKey":"spectator.menu","role":"group","label":"","visible":false,"active":false,"boundsAvailable":false,"actions":[]}]}
                """)
                .getAsJsonObject();
    }

    private static JsonObject node(JsonObject tree) {
        return tree.getAsJsonArray("children").get(0).getAsJsonObject();
    }

    private static JsonObject state() {
        var state =
                JsonParser.parseString(
                                """
                {"worldOwner":8,"playerOwner":9,"guiOwner":10,"menuOwner":11,"categoryOwner":12,"page":0,"value":-1,"contentFingerprint":"full-items-and-prompt","guardAvailable":true,"items":[]}
                """)
                        .getAsJsonObject();
        for (int index = 0; index < 9; index++) {
            var item = new JsonObject();
            item.addProperty("index", index);
            item.addProperty("owner", 100 + index);
            item.addProperty("empty", false);
            item.addProperty("enabled", true);
            state.getAsJsonArray("items").add(item);
        }
        return state;
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input =
                UiSpectatorSelectionDebugTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
