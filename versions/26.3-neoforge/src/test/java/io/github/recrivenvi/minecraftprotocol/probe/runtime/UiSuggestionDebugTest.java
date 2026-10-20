package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class UiSuggestionDebugTest {
    @Test
    void selectionUsesAnAbsoluteNativeEntryIndexWithoutWrappingOrTruncating() {
        assertEquals(0, UiSuggestionDebug.validate(new JsonPrimitive(0), 3));
        assertEquals(2, UiSuggestionDebug.validate(new JsonPrimitive(2.0), 3));
        for (String value :
                List.of("null", "false", "\"1\"", "[]", "{}", "-1", "3", "1.25", "1e999"))
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiSuggestionDebug.validate(
                                                    JsonParser.parseString(value), 3))
                            .code(),
                    value);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> UiSuggestionDebug.validate(new JsonPrimitive(0), 0));
    }

    @Test
    void completeDraftEntriesRangesAndTooltipContentAreGuardedWithoutDisplayTruncation() {
        var guard = new UiItemDataGuard();
        String prefix = "a".repeat(5000);
        var entry =
                new Suggestion(
                        StringRange.between(0, 1), prefix + "one", new LiteralMessage("tip"));
        var content = UiSuggestionDebug.captureContent(guard, "/", "/", List.of(entry));
        assertTrue(content.available());
        assertEquals(content, UiSuggestionDebug.captureContent(guard, "/", "/", List.of(entry)));
        for (var changed :
                List.of(
                        new Suggestion(
                                StringRange.between(0, 1),
                                prefix + "two",
                                new LiteralMessage("tip")),
                        new Suggestion(
                                StringRange.between(0, 2),
                                prefix + "one",
                                new LiteralMessage("tip")),
                        new Suggestion(
                                StringRange.between(0, 1),
                                prefix + "one",
                                new LiteralMessage("other"))))
            assertNotEquals(
                    content, UiSuggestionDebug.captureContent(guard, "/", "/", List.of(changed)));
        assertNotEquals(
                content, UiSuggestionDebug.captureContent(guard, "/other", "/", List.of(entry)));
        assertNotEquals(
                content, UiSuggestionDebug.captureContent(guard, "/", "/other", List.of(entry)));
        assertNotEquals(
                UiSuggestionDebug.captureContent(guard, "\uD800", "/", List.of(entry)),
                UiSuggestionDebug.captureContent(guard, "\uD801", "/", List.of(entry)));
        assertFalse(
                UiSuggestionDebug.captureContent(
                                guard,
                                "a".repeat(UiItemDataGuard.MAX_ITEM_BYTES),
                                "/",
                                List.of(entry))
                        .available());
    }

    @Test
    void productionDescriptionVersionsCompleteNativeStateAndNeverItsOwnDescriptor() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var node = node(tree);
            UiSuggestionDebug.describeState(tree, node, revisions, state(true));
            var original = node.getAsJsonObject("resourceVersion").deepCopy();
            assertEquals(
                    List.of("ui.list.selection.set"),
                    node
                            .getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .asList()
                            .stream()
                            .map(value -> value.getAsString())
                            .toList());
            assertEquals(0, node.get("debugSelectionIndex").getAsInt());
            assertEquals(2, node.getAsJsonObject("debugSelectionRange").get("maximum").getAsInt());
            node.getAsJsonObject("debugMutation").addProperty("notNativeState", true);
            UiSuggestionDebug.describeState(tree, node, revisions, state(true));
            assertEquals(original, node.get("resourceVersion"));
            for (String key :
                    List.of(
                            "value",
                            "entryCount",
                            "offset",
                            "screenOwner",
                            "suggestionsOwner",
                            "listOwner",
                            "inputOwner",
                            "x",
                            "y",
                            "width",
                            "height",
                            "contentFingerprint")) {
                var changed = state(true);
                if (key.equals("contentFingerprint"))
                    changed.addProperty(key, "different-full-draft-or-entries");
                else changed.addProperty(key, changed.get(key).getAsInt() + 1);
                UiSuggestionDebug.describeState(tree, node, revisions, changed);
                assertEquals(
                        "STALE_RESOURCE_REVISION",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () ->
                                                ResourceVersionVerifier.verify(
                                                        original,
                                                        node.getAsJsonObject("resourceVersion")))
                                .code(),
                        key);
            }
            UiSuggestionDebug.describeState(tree, node, revisions, state(true));
            var beforeScale = node.get("resourceVersion").deepCopy();
            tree.addProperty("guiScale", 3);
            UiSuggestionDebug.describeState(tree, node, revisions, state(true));
            assertNotEquals(beforeScale, node.get("resourceVersion"));
        }
    }

    @Test
    void unavailableFullContentCannotAdvertiseOrPublishAMutableSuggestionOwner() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var node = node(tree);
            UiSuggestionDebug.describeState(tree, node, revisions, state(true));
            assertTrue(node.has("resourceVersion"));
            UiSuggestionDebug.describeState(tree, node, revisions, state(false));
            assertFalse(node.has("resourceVersion"));
            assertFalse(node.get("debugSelectionGuardAvailable").getAsBoolean());
            assertTrue(
                    node.getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .isEmpty());
        }
    }

    @Test
    void productionUserProjectionRemovesSelectionMetadataWhileDebugRetainsIt() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            UiSuggestionDebug.describeState(tree, node(tree), revisions, state(true));
            var user = node(UiDataView.tree(tree, UiDataView.Category.USER));
            var debug = node(UiDataView.tree(tree, UiDataView.Category.DEBUG));
            for (String field :
                    List.of(
                            "debugSelectionIndex",
                            "debugSelectionRange",
                            "debugSelectionUnit",
                            "debugSelectionGuardAvailable",
                            "debugMutation",
                            "resourceVersion")) {
                assertFalse(user.has(field), field);
                assertTrue(debug.has(field), field);
            }
            assertEquals("chat.suggestions", user.get("controlKey").getAsString());
            assertEquals(100, user.get("width").getAsInt());
        }
    }

    @Test
    void aRenderedListCannotReplaceTheCurrentNativeSuggestionOwner() {
        assertNull(UiSuggestionDebug.target(null, node(tree()), System::identityHashCode));
        assertEquals(
                "UI_DEBUG_UNSUPPORTED",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        UiSuggestionDebug.prepare(
                                                null,
                                                new JsonPrimitive(0),
                                                System::identityHashCode,
                                                new UiItemDataGuard()))
                        .code());
    }

    @Test
    void productionDispatchUsesNativeSelectOnlyAndRechecksItsCompleteOwnerState() throws Exception {
        var helper = compiled("UiSuggestionDebug");
        int select = 0, rechecks = 0;
        for (var method : helper.methods)
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(
                                "net/minecraft/client/gui/components/CommandSuggestions$SuggestionsList")
                        && call.name.equals("select")
                        && call.desc.equals("(I)V")) select++;
                if (method.name.startsWith("lambda$prepare$") && call.name.equals("snapshot"))
                    rechecks++;
                assertNotEquals("useSuggestion", call.name);
                assertNotEquals("setValue", call.name);
                assertFalse(call.owner.contains("InputDispatchContext"));
                assertFalse(call.owner.startsWith("net/minecraft/network/protocol/"));
            }
        assertEquals(1, select);
        assertEquals(1, rechecks);
        for (String methodName : List.of("describe", "mutate")) {
            var entry =
                    compiled("UiDebugEngine").methods.stream()
                            .filter(method -> method.name.equals(methodName))
                            .toList();
            assertTrue(
                    entry.stream()
                            .flatMap(
                                    method -> {
                                        var calls = new java.util.ArrayList<MethodInsnNode>();
                                        for (var instruction : method.instructions)
                                            if (instruction instanceof MethodInsnNode call)
                                                calls.add(call);
                                        return calls.stream();
                                    })
                            .anyMatch(
                                    call ->
                                            call.owner.endsWith("/UiSuggestionDebug")
                                                    && call.name.equals(
                                                            methodName.equals("mutate")
                                                                    ? "prepare"
                                                                    : "describe")));
        }
    }

    private static JsonObject tree() {
        return JsonParser.parseString(
                        """
                {"screenIdentity":7,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
                  {"nodeId":"suggestion_list","elementIdentity":10,"role":"list","class":"native.SuggestionsList","controlKey":"chat.suggestions","visible":true,"active":true,"x":9,"y":18,"width":100,"height":36,"entryCount":3}
                ]}
                """)
                .getAsJsonObject();
    }

    private static JsonObject node(JsonObject tree) {
        return tree.getAsJsonArray("children").get(0).getAsJsonObject();
    }

    private static JsonObject state(boolean guarded) {
        var state =
                JsonParser.parseString(
                                """
                {"value":0,"entryCount":3,"contentFingerprint":"complete-content-hash","screenOwner":7,"suggestionsOwner":8,"listOwner":10,"inputOwner":11,"offset":0,"x":9,"y":18,"width":100,"height":36}
                """)
                        .getAsJsonObject();
        state.addProperty("guardAvailable", guarded);
        return state;
    }

    private static ClassNode compiled(String localName) throws Exception {
        String name =
                "/io/github/recrivenvi/minecraftprotocol/probe/runtime/" + localName + ".class";
        try (var input = UiSuggestionDebugTest.class.getResourceAsStream(name)) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
