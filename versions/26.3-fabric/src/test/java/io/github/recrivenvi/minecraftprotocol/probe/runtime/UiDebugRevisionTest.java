package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.minecraft.client.gui.font.TextFieldHelper;
import org.junit.jupiter.api.Test;

class UiDebugRevisionTest {
    @Test
    void nativeScreenScrollRangeIsFiniteNormalizedAndRejectsDisabledMovement() {
        assertEquals(
                0.75F, UiDebugEngine.normalizedScreenScroll(JsonParser.parseString("0.75"), true));
        assertEquals(0F, UiDebugEngine.normalizedScreenScroll(JsonParser.parseString("0"), false));
        for (String value : List.of("-0.01", "1.01", "true", "null", "{}", "[]", "\"0.5\""))
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiDebugEngine.normalizedScreenScroll(
                                                    JsonParser.parseString(value), true))
                            .code());
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> UiDebugEngine.normalizedScreenScroll(JsonParser.parseString("0.01"), false));
    }

    @Test
    void nativeScreenScrollerTokensBindOffsetAndContextWithoutAcceptingOtherRegions() {
        try (var engine = new Phase9ASpikeEngine("test")) {
            JsonObject root =
                    JsonParser.parseString(
                                    """
                {"screenIdentity":7,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
                  {"nodeId":"scroll","elementIdentity":7,"controlKey":"creative.scrollbar.track","role":"scrollbar","capabilitySource":"confirmed_vanilla_input_handler","scrollAmount":0,"active":true},
                  {"nodeId":"other","elementIdentity":7,"controlKey":"unknown.scrollbar.track","role":"scrollbar","capabilitySource":"confirmed_vanilla_input_handler"}
                ]}
                """)
                            .getAsJsonObject();
            UiDebugEngine.describe(root, engine, Map.of());
            var scroll = root.getAsJsonArray("children").get(0).getAsJsonObject();
            assertTrue(scroll.has("resourceVersion"));
            assertFalse(
                    root.getAsJsonArray("children")
                            .get(1)
                            .getAsJsonObject()
                            .has("resourceVersion"));
            var token = scroll.get("resourceVersion").deepCopy();
            scroll.addProperty("scrollAmount", 0.5);
            UiDebugEngine.describe(root, engine, Map.of());
            assertNotEquals(token, scroll.get("resourceVersion"));
            token = scroll.get("resourceVersion").deepCopy();
            root.addProperty("guiScale", 3);
            UiDebugEngine.describe(root, engine, Map.of());
            assertNotEquals(token, scroll.get("resourceVersion"));
        }
    }

    @Test
    void nativeHelperValidationDoesNotChangeTextOrSelectionBeforeAuthorizedApply() {
        String[] draft = {"before"};
        var writes = new AtomicInteger();
        Predicate<String> validator = value -> value.length() <= 8;
        var helper =
                new TextFieldHelper(
                        () -> draft[0],
                        value -> {
                            draft[0] = value;
                            writes.incrementAndGet();
                        },
                        () -> "",
                        value -> {},
                        validator);
        helper.setSelectionRange(2, 4);
        assertEquals(
                "INVALID_UI_DEBUG_VALUE",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        UiDebugEngine.prepareHelperText(
                                                helper, validator, "too long for native"))
                        .code());
        assertEquals("before", draft[0]);
        assertEquals(2, helper.getCursorPos());
        assertEquals(4, helper.getSelectionPos());
        assertEquals(0, writes.get());
        Runnable apply = UiDebugEngine.prepareHelperText(helper, validator, "after");
        assertEquals(0, writes.get());
        apply.run();
        assertEquals("after", draft[0]);
        assertEquals(1, writes.get());
    }

    @Test
    void semanticSignLinesHaveSeparateTokensBoundToTheCompleteNativeDraft() {
        try (var engine = new Phase9ASpikeEngine("test")) {
            JsonObject root =
                    JsonParser.parseString(
                                    """
                {"screenIdentity":7,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
                  {"nodeId":"sign0","elementIdentity":7,"controlKey":"sign.line.0","role":"text_field","value":"draft"},
                  {"nodeId":"sign1","elementIdentity":7,"controlKey":"sign.line.1","role":"text_field","value":"draft"}
                ]}
                """)
                            .getAsJsonObject();
            var guard = new UiItemDataGuard();
            UiDebugEngine.describe(
                    root,
                    engine,
                    Map.of(
                            7L,
                            UiDebugEngine.textContent(
                                    guard, new UiItemDataGuard.Budget(), "5:draft5:draft0:0:")));
            var first = root.getAsJsonArray("children").get(0).getAsJsonObject();
            var second = root.getAsJsonArray("children").get(1).getAsJsonObject();
            assertNotEquals(first.get("resourceVersion"), second.get("resourceVersion"));
            var before = first.get("resourceVersion").deepCopy();
            UiDebugEngine.describe(
                    root,
                    engine,
                    Map.of(
                            7L,
                            UiDebugEngine.textContent(
                                    guard, new UiItemDataGuard.Budget(), "5:draft7:changed0:0:")));
            assertNotEquals(before, first.get("resourceVersion"));
            assertTrue(first.get("debugTextGuardAvailable").getAsBoolean());
            assertFalse(first.has("nativeTextContentFingerprint"));
        }
    }

    @Test
    void textBeyondThePublishedPrefixMustStillInvalidateTheNativeUiToken() {
        for (String[] tails : List.of(new String[] {"A", "B"}, new String[] {"\uD800", "\uD801"})) {
            try (var engine = new Phase9ASpikeEngine("test")) {
                String prefix = "a".repeat(2048);
                var guard = new UiItemDataGuard();
                JsonObject first = textTree(prefix + tails[0]),
                        second = textTree(prefix + tails[1]);
                assertEquals(
                        first.getAsJsonArray("children").get(0).getAsJsonObject().get("value"),
                        second.getAsJsonArray("children").get(0).getAsJsonObject().get("value"));
                UiDebugEngine.describe(
                        first,
                        engine,
                        Map.of(
                                10L,
                                UiDebugEngine.textContent(
                                        guard, new UiItemDataGuard.Budget(), prefix + tails[0])));
                UiDebugEngine.describe(
                        second,
                        engine,
                        Map.of(
                                10L,
                                UiDebugEngine.textContent(
                                        guard, new UiItemDataGuard.Budget(), prefix + tails[1])));
                assertNotEquals(
                        first.getAsJsonArray("children")
                                .get(0)
                                .getAsJsonObject()
                                .get("resourceVersion"),
                        second.getAsJsonArray("children")
                                .get(0)
                                .getAsJsonObject()
                                .get("resourceVersion"),
                        "A different native text tail cannot share a mutation precondition");
            }
        }
    }

    @Test
    void textCaptureBudgetOrMissingNativeOwnerCannotAuthorizeMutation() {
        var guard = new UiItemDataGuard();
        var budget = new UiItemDataGuard.Budget(16);
        var unavailable = UiDebugEngine.textContent(guard, budget, "a".repeat(32));
        assertFalse(unavailable.available());
        assertEquals(0, budget.remaining());
        try (var engine = new Phase9ASpikeEngine("test")) {
            for (var contents :
                    List.of(Map.<Long, UiItemDataGuard.Content>of(), Map.of(10L, unavailable))) {
                var root = textTree("draft");
                UiDebugEngine.describe(root, engine, contents);
                var node = root.getAsJsonArray("children").get(0).getAsJsonObject();
                assertFalse(node.get("debugTextGuardAvailable").getAsBoolean());
                assertFalse(node.has("nativeTextContentFingerprint"));
                assertEquals(
                        "UI_CONTENT_GUARD_UNAVAILABLE",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () -> UiDebugEngine.requireTextGuard(node))
                                .code());
            }
        }
    }

    private static JsonObject textTree(String fullValue) {
        var root =
                JsonParser.parseString(
                                """
            {"screenIdentity":7,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[
              {"nodeId":"text","elementIdentity":10,"capabilitySource":"native_widget_contract","role":"text_field"}
            ]}
            """)
                        .getAsJsonObject();
        root.getAsJsonArray("children")
                .get(0)
                .getAsJsonObject()
                .addProperty("value", VanillaUiTree.bounded(fullValue));
        return root;
    }

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
            UiDebugEngine.describe(tree, engine, Map.of());
            UiDebugEngine.describe(next, engine, Map.of());
            JsonObject first = tree.getAsJsonArray("children").get(0).getAsJsonObject();
            JsonObject second = next.getAsJsonArray("children").get(0).getAsJsonObject();
            assertEquals(first.get("resourceVersion"), second.get("resourceVersion"));
            assertFalse(
                    next.getAsJsonArray("children")
                            .get(1)
                            .getAsJsonObject()
                            .has("resourceVersion"));
            second.addProperty("scrollAmount", 21);
            UiDebugEngine.describe(next, engine, Map.of());
            assertNotEquals(first.get("resourceVersion"), second.get("resourceVersion"));
            assertEquals(
                    "ui_node",
                    second.getAsJsonObject("resourceVersion").get("resourceType").getAsString());
        }
    }
}
