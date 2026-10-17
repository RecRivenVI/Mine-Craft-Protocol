package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.CreativeUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.LoomUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.MerchantUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SignUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SliderValueAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.StonecutterUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.TextFieldUiAccessor;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;

/** Typed client-owner-thread setters, not arbitrary callbacks or player input dispatch. */
final class UiDebugEngine {
    static final List<String> OPERATIONS =
            List.of(
                    "ui.widget.active.set",
                    "ui.widget.visible.set",
                    "ui.text.set",
                    "ui.scroll.set",
                    "ui.slider.set",
                    "ui.toast.reset",
                    "ui.toast.hide");

    static void describe(
            JsonObject root,
            Phase9ASpikeEngine revisions,
            Screen screen,
            ToLongFunction<Object> identity,
            UiItemDataGuard contentGuard) {
        Map<Long, UiItemDataGuard.Content> textContent = new HashMap<>();
        if (screen != null) {
            var pending = new ArrayDeque<GuiEventListener>(screen.children());
            Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            var budget = new UiItemDataGuard.Budget();
            if (screen instanceof AbstractSignEditScreen sign) {
                var draft = new StringBuilder();
                for (String line : ((SignUiAccessor) sign).minecraftProtocolProbe$messages())
                    draft.append(line.length()).append(':').append(line);
                textContent.put(
                        identity.applyAsLong(screen),
                        textContent(contentGuard, budget, draft.toString()));
            }
            while (!pending.isEmpty() && visited.size() < 2048) {
                var item = pending.removeFirst();
                if (!visited.add(item)) continue;
                String fullValue =
                        item instanceof EditBox edit
                                ? edit.getValue()
                                : item instanceof MultiLineEditBox edit ? edit.getValue() : null;
                if (fullValue != null)
                    textContent.put(
                            identity.applyAsLong(item),
                            textContent(contentGuard, budget, fullValue));
                if (item instanceof ContainerEventHandler parent)
                    for (var child : parent.children()) {
                        if (pending.size() >= 2048) break;
                        pending.add(child);
                    }
            }
        }
        describe(root, revisions, textContent);
        UiPresentationDebug.describe(
                root,
                revisions,
                net.minecraft.client.Minecraft.getInstance(),
                identity,
                contentGuard);
        UiToastDebug.describe(
                root,
                revisions,
                net.minecraft.client.Minecraft.getInstance(),
                identity,
                contentGuard);
    }

    static UiItemDataGuard.Content textContent(
            UiItemDataGuard guard, UiItemDataGuard.Budget budget, String value) {
        return guard.capture(
                budget,
                buffer -> {
                    // Native String identity includes unpaired UTF-16 units; charset replacement
                    // must not let different local drafts share a mutation precondition.
                    for (int index = 0; index < value.length(); index++)
                        buffer.writeChar(value.charAt(index));
                });
    }

    static void requireTextGuard(JsonObject node) {
        if (node.has("debugTextGuardAvailable")
                && !node.get("debugTextGuardAvailable").getAsBoolean())
            throw error(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    "Full native UI text could not be guarded within the capture budget");
    }

    static void describe(
            JsonObject root,
            Phase9ASpikeEngine revisions,
            Map<Long, UiItemDataGuard.Content> textContent) {
        root.addProperty("uiContextId", "ui-session:" + revisions.sessionEpoch());
        root.addProperty("sessionEpoch", revisions.sessionEpoch());
        describeNodes(root, root.getAsJsonArray("children"), revisions, textContent);
    }

    private static void describeNodes(
            JsonObject root,
            JsonArray nodes,
            Phase9ASpikeEngine revisions,
            Map<Long, UiItemDataGuard.Content> textContent) {
        if (nodes == null) return;
        for (JsonElement value : nodes) {
            JsonObject node = value.getAsJsonObject();
            if (node.has("elementIdentity")
                            && node.has("capabilitySource")
                            && Set.of("native_widget_contract", "native_listener_contract")
                                    .contains(node.get("capabilitySource").getAsString())
                    || node.has("elementIdentity")
                            && node.has("controlKey")
                            && node.get("controlKey").getAsString().matches("sign\\.line\\.[0-3]")
                            && textContent.containsKey(node.get("elementIdentity").getAsLong())
                    || node.has("elementIdentity")
                            && node.get("elementIdentity").equals(root.get("screenIdentity"))
                            && screenScrollerKey(node) != null) {
                var content = textContent.get(node.get("elementIdentity").getAsLong());
                boolean text =
                        "text_field".equals(node.has("role") ? node.get("role").getAsString() : "")
                                && node.has("value");
                if (text)
                    node.addProperty(
                            "debugTextGuardAvailable", content != null && content.available());
                JsonObject state = node.deepCopy();
                if (text && content != null && content.available())
                    state.addProperty("nativeTextContentFingerprint", content.fingerprint());
                state.remove("resourceVersion");
                state.remove("hovered");
                // A container token describes the container, not its separately versioned children.
                state.remove("children");
                for (String key :
                        List.of(
                                "screenIdentity",
                                "screenRevision",
                                "width",
                                "height",
                                "guiScale",
                                "overlayIdentity")) state.add("context_" + key, root.get(key));
                node.add(
                        "resourceVersion",
                        revisions.uiRevision(
                                root.get("screenIdentity").getAsString()
                                        + ":"
                                        + node.get("nodeId").getAsString(),
                                state));
            }
            describeNodes(root, node.getAsJsonArray("children"), revisions, textContent);
        }
    }

    static JsonObject mutate(
            Screen screen,
            JsonObject tree,
            JsonObject request,
            ToLongFunction<Object> identity,
            DebugMutationAuthorization authorization) {
        String operation = string(request, "operation");
        if (!OPERATIONS.contains(operation))
            throw error("CAPABILITY_UNAVAILABLE", "Unknown typed UI operation");
        if (UiPresentationDebug.handles(string(request, "nodeId")))
            return UiPresentationDebug.mutate(
                    net.minecraft.client.Minecraft.getInstance(), tree, request, authorization);
        if (UiToastDebug.handles(string(request, "nodeId")))
            return UiToastDebug.mutate(
                    net.minecraft.client.Minecraft.getInstance(),
                    tree,
                    request,
                    identity,
                    authorization);
        if (screen == null || tree.get("overlayIdentity").getAsLong() != 0)
            throw error(
                    "UI_CONTEXT_UNAVAILABLE",
                    "A live Screen without a blocking Overlay is required");
        if (authorization.isCancelled())
            throw error("OPERATION_CANCELLED", "UI mutation cancelled");
        JsonObject node = find(tree.getAsJsonArray("children"), string(request, "nodeId"));
        if (node == null || !node.has("resourceVersion"))
            throw error("UI_NODE_NOT_FOUND", "Native UI node no longer exists");
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject())
            throw error("INVALID_RESOURCE_VERSION", "UI mutation requires expectedResourceVersion");
        ResourceVersionVerifier.verify(
                request.getAsJsonObject("expectedResourceVersion"),
                node.getAsJsonObject("resourceVersion"));
        requireTextGuard(node);
        GuiEventListener target = locate(screen, node.get("elementIdentity").getAsLong(), identity);
        int signLine = signLine(screen, node, identity);
        boolean screenScroll = nativeScreenScroller(screen, node, identity);
        if (target == null && signLine < 0 && !screenScroll)
            throw error(
                    "UI_DEBUG_UNSUPPORTED",
                    "This rendered node has no supported typed native mutation");
        String nativeText =
                target instanceof EditBox edit
                        ? edit.getValue()
                        : target instanceof MultiLineEditBox edit
                                ? edit.getValue()
                                : signLine >= 0
                                        ? ((SignUiAccessor) screen)
                                                .minecraftProtocolProbe$messages()[signLine]
                                        : null;
        JsonElement current =
                operation.equals("ui.text.set") && nativeText != null
                        ? new JsonPrimitive(nativeText)
                        : currentValue(operation, node);
        if (request.has("expectedValue") && !Objects.equals(request.get("expectedValue"), current))
            throw error("VALUE_PRECONDITION_FAILED", "UI value changed");
        JsonElement value = request.get("value");
        if (operation.equals("ui.text.set")) requireTextMutationContext(screen.getClass());
        // Validate the full request and native type before obtaining the mutation permit.
        Runnable apply =
                switch (operation) {
                    case "ui.widget.active.set", "ui.widget.visible.set" -> {
                        if (!(target instanceof AbstractWidget widget)
                                || value == null
                                || !value.isJsonPrimitive()
                                || !value.getAsJsonPrimitive().isBoolean())
                            throw error(
                                    "INVALID_UI_DEBUG_VALUE",
                                    "Boolean native widget value required");
                        boolean flag = value.getAsBoolean();
                        yield () -> {
                            if (operation.equals("ui.widget.active.set")) widget.active = flag;
                            else widget.visible = flag;
                        };
                    }
                    case "ui.text.set" -> {
                        if (nativeText == null
                                || value == null
                                || !value.isJsonPrimitive()
                                || !value.getAsJsonPrimitive().isString()
                                || value.getAsString().length() > 4096)
                            throw error(
                                    "INVALID_UI_DEBUG_VALUE",
                                    "Bounded native editor string required");
                        String text = value.getAsString();
                        if (target instanceof EditBox edit) yield () -> edit.setValue(text);
                        if (target instanceof MultiLineEditBox edit)
                            yield () -> edit.setValue(text);
                        var sign = (SignUiAccessor) screen;
                        var helper = sign.minecraftProtocolProbe$field();
                        var replace =
                                prepareHelperText(
                                        helper,
                                        ((TextFieldUiAccessor) helper)
                                                .minecraftProtocolProbe$validator(),
                                        text);
                        yield () -> {
                            int previousLine = sign.minecraftProtocolProbe$line();
                            int cursor = helper.getCursorPos(),
                                    selection = helper.getSelectionPos();
                            try {
                                sign.minecraftProtocolProbe$line(signLine);
                                replace.run();
                            } finally {
                                sign.minecraftProtocolProbe$line(previousLine);
                                helper.setSelectionRange(cursor, selection);
                            }
                        };
                    }
                    case "ui.slider.set" -> {
                        if (!(target instanceof AbstractSliderButton slider)
                                || value == null
                                || !value.isJsonPrimitive()
                                || !value.getAsJsonPrimitive().isNumber()
                                || !Double.isFinite(value.getAsDouble())
                                || value.getAsDouble() < 0
                                || value.getAsDouble() > 1)
                            throw error(
                                    "INVALID_UI_DEBUG_VALUE",
                                    "Native slider requires a finite normalized value in [0,1]");
                        // Use the native setter: writing the field alone would leave its setting
                        // and displayed message inconsistent. Native setting callbacks still run.
                        yield () ->
                                ((SliderValueAccessor) slider)
                                        .minecraftProtocolProbe$setValue(value.getAsDouble());
                    }
                    case "ui.scroll.set" -> {
                        if (screenScroll) yield prepareScreenScroll(screen, node, value);
                        if (!(target instanceof AbstractSelectionList<?> list)
                                || value == null
                                || !value.isJsonPrimitive()
                                || !value.getAsJsonPrimitive().isNumber()
                                || !Double.isFinite(value.getAsDouble())
                                || value.getAsDouble() < 0
                                || value.getAsDouble() > list.maxScrollAmount())
                            throw error(
                                    "INVALID_UI_DEBUG_VALUE",
                                    "Native list scroll amount outside its bounds");
                        yield () -> list.setScrollAmount(value.getAsDouble());
                    }
                    default -> throw error("CAPABILITY_UNAVAILABLE", "Unsupported UI mutation");
                };
        try (var permit =
                authorization.authorize(
                        tree.get("uiContextId").getAsString(),
                        tree.get("sessionEpoch").getAsString(),
                        "ui",
                        "ui")) {
            apply.run();
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "debug.mutation.result");
        result.addProperty("schemaVersion", "phase9c-debug-v0");
        result.addProperty("mechanism", "typed_native_ui_state");
        result.addProperty(
                "invariants",
                "bounded native widget types; no arbitrary callbacks or player input");
        result.addProperty("synchronization", "client_owner_thread");
        result.addProperty("gameplayEvidence", false);
        result.add("preconditions", request.get("expectedResourceVersion").deepCopy());
        result.addProperty("operation", operation);
        result.addProperty("namespace", "ui");
        result.addProperty("domain", "UI");
        result.addProperty("dataCategory", "DEBUG");
        result.addProperty("authority", "CLIENT_UI");
        result.addProperty("evidence", "diagnostic");
        result.addProperty("evidenceAuthority", "DEBUG_PRIVILEGED");
        result.addProperty("directMutationUsed", true);
        result.addProperty("inputDispatchCount", 0);
        if (operation.equals("ui.slider.set"))
            result.addProperty("sideEffects", "native_slider_setting_callback");
        if (operation.equals("ui.text.set")
                && (signLine >= 0 || target instanceof MultiLineEditBox))
            result.addProperty("sideEffects", "native_editor_draft_may_submit_on_close");
        result.addProperty("nodeId", node.get("nodeId").getAsString());
        result.add("before", node.deepCopy());
        result.add("beforeResourceVersion", node.get("resourceVersion").deepCopy());
        if (request.has("debugOperationId"))
            result.add("debugOperationId", request.get("debugOperationId"));
        return result;
    }

    static String screenScrollerKey(JsonObject node) {
        if (!node.has("role")
                || !"scrollbar".equals(node.get("role").getAsString())
                || !node.has("capabilitySource")
                || !"confirmed_vanilla_input_handler"
                        .equals(node.get("capabilitySource").getAsString())
                || !node.has("controlKey")) return null;
        String key = node.get("controlKey").getAsString();
        return Set.of(
                                "creative.scrollbar.track",
                                "loom.scrollbar.track",
                                "stonecutter.scrollbar.track",
                                "merchant.scrollbar.track")
                        .contains(key)
                ? key
                : null;
    }

    private static boolean nativeScreenScroller(
            Screen screen, JsonObject node, ToLongFunction<Object> identity) {
        String key = screenScrollerKey(node);
        if (key == null || node.get("elementIdentity").getAsLong() != identity.applyAsLong(screen))
            return false;
        return switch (key) {
            case "creative.scrollbar.track" -> screen instanceof CreativeModeInventoryScreen;
            case "loom.scrollbar.track" -> screen instanceof LoomScreen;
            case "stonecutter.scrollbar.track" -> screen instanceof StonecutterScreen;
            case "merchant.scrollbar.track" -> screen instanceof MerchantScreen;
            default -> false;
        };
    }

    static float normalizedScreenScroll(JsonElement value, boolean enabled) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())
                || value.getAsDouble() < 0
                || value.getAsDouble() > (enabled ? 1 : 0))
            throw error(
                    "INVALID_UI_DEBUG_VALUE",
                    "Native Screen scrollbar requires a finite normalized value within its current range");
        return value.getAsFloat();
    }

    private static Runnable prepareScreenScroll(Screen screen, JsonObject node, JsonElement value) {
        float offset = normalizedScreenScroll(value, node.get("active").getAsBoolean());
        // Only the local display position changes. Never select an offer/recipe or send a packet.
        if (screen instanceof CreativeModeInventoryScreen creative) {
            var access = (CreativeUiAccessor) creative;
            return () -> {
                access.minecraftProtocolProbe$scrollOffs(offset);
                creative.getMenu().scrollTo(offset);
            };
        }
        if (screen instanceof LoomScreen loom) {
            var access = (LoomUiAccessor) loom;
            int row =
                    Math.max(
                            0,
                            Math.round(offset * (access.minecraftProtocolProbe$totalRows() - 4)));
            return () -> {
                access.minecraftProtocolProbe$scrollOffs(offset);
                access.minecraftProtocolProbe$startRow(row);
            };
        }
        if (screen instanceof StonecutterScreen stonecutter) {
            var access = (StonecutterUiAccessor) stonecutter;
            int index =
                    Math.max(0, Math.round(offset * access.minecraftProtocolProbe$offscreenRows()))
                            * 4;
            return () -> {
                access.minecraftProtocolProbe$scrollOffs(offset);
                access.minecraftProtocolProbe$startIndex(index);
            };
        }
        var merchant = (MerchantScreen) screen;
        int row = Math.max(0, Math.round(offset * (merchant.getMenu().getOffers().size() - 7)));
        return () -> ((MerchantUiAccessor) merchant).minecraftProtocolProbe$scrollOff(row);
    }

    static Runnable prepareHelperText(
            TextFieldHelper helper, Predicate<String> validator, String text) {
        if (text.length() > 4096 || !validator.test(text))
            throw error(
                    "INVALID_UI_DEBUG_VALUE", "Native editor rejected the complete draft value");
        return () -> {
            helper.selectAll();
            helper.insertText(text);
        };
    }

    private static int signLine(Screen screen, JsonObject node, ToLongFunction<Object> identity) {
        if (!(screen instanceof AbstractSignEditScreen sign)
                || node.get("elementIdentity").getAsLong() != identity.applyAsLong(screen)
                || !node.has("controlKey")
                || !node.has("lineIndex")) return -1;
        int line = node.get("lineIndex").getAsInt();
        var access = (SignUiAccessor) sign;
        if (line < 0
                || line >= Math.min(4, access.minecraftProtocolProbe$messages().length)
                || !("sign.line." + line).equals(node.get("controlKey").getAsString())
                || !access.minecraftProtocolProbe$valid()) return -1;
        return line;
    }

    private static JsonElement currentValue(String operation, JsonObject node) {
        return node.get(
                switch (operation) {
                    case "ui.widget.active.set" -> "active";
                    case "ui.widget.visible.set" -> "visible";
                    case "ui.scroll.set" -> "scrollAmount";
                    default -> "value";
                });
    }

    static void requireTextMutationContext(Class<? extends Screen> screen) {
        // Its EditBox responder sends management QUERY packets even without a player click.
        if (net.minecraft.client.gui.screens.inventory.TestInstanceBlockEditScreen.class
                .isAssignableFrom(screen))
            throw error(
                    "UI_DEBUG_UNSUPPORTED",
                    "Test-instance text responders have unclassified management effects; no text or packet was changed");
    }

    private static GuiEventListener locate(
            Screen screen, long id, ToLongFunction<Object> identity) {
        ArrayDeque<GuiEventListener> pending = new ArrayDeque<>(screen.children());
        Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int budget = 2048;
        while (!pending.isEmpty() && budget-- > 0) {
            GuiEventListener item = pending.removeFirst();
            if (!visited.add(item)) continue;
            if (identity.applyAsLong(item) == id) return item;
            if (item instanceof ContainerEventHandler parent)
                for (GuiEventListener child : parent.children()) {
                    if (pending.size() >= 2048) break;
                    pending.add(child);
                }
        }
        return null;
    }

    static JsonObject findNode(JsonObject tree, String id) {
        JsonObject node = find(tree.getAsJsonArray("children"), id);
        if (node != null || !tree.has("presentation")) return node;
        return find(tree.getAsJsonObject("presentation").getAsJsonArray("children"), id);
    }

    static JsonObject find(JsonArray nodes, String id) {
        if (nodes == null) return null;
        for (JsonElement item : nodes) {
            JsonObject node = item.getAsJsonObject();
            if (node.has("nodeId") && id.equals(node.get("nodeId").getAsString())) return node;
            JsonObject nested = find(node.getAsJsonArray("children"), id);
            if (nested != null) return nested;
        }
        return null;
    }

    private static String string(JsonObject value, String key) {
        if (!value.has(key)
                || !value.get(key).isJsonPrimitive()
                || !value.get(key).getAsJsonPrimitive().isString())
            throw error("INVALID_UI_DEBUG_REQUEST", "Missing string: " + key);
        return value.get(key).getAsString();
    }

    private static ProtocolState.ProtocolException error(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }
}
