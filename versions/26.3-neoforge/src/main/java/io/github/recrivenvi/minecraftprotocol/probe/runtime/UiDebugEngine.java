package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SliderValueAccessor;
import java.util.*;
import java.util.function.ToLongFunction;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.Screen;

/** Typed client-owner-thread setters, not arbitrary callbacks or player input dispatch. */
final class UiDebugEngine {
    static final List<String> OPERATIONS =
            List.of(
                    "ui.widget.active.set",
                    "ui.widget.visible.set",
                    "ui.text.set",
                    "ui.scroll.set",
                    "ui.slider.set");

    static void describe(JsonObject root, Phase9ASpikeEngine revisions) {
        root.addProperty("uiContextId", "ui-session:" + revisions.sessionEpoch());
        root.addProperty("sessionEpoch", revisions.sessionEpoch());
        describeNodes(root, root.getAsJsonArray("children"), revisions);
    }

    private static void describeNodes(
            JsonObject root, JsonArray nodes, Phase9ASpikeEngine revisions) {
        if (nodes == null) return;
        for (JsonElement value : nodes) {
            JsonObject node = value.getAsJsonObject();
            if (node.has("elementIdentity")
                    && node.has("capabilitySource")
                    && Set.of("native_widget_contract", "native_listener_contract")
                            .contains(node.get("capabilitySource").getAsString())) {
                JsonObject state = node.deepCopy();
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
            describeNodes(root, node.getAsJsonArray("children"), revisions);
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
        if (request.has("expectedValue")
                && !Objects.equals(request.get("expectedValue"), currentValue(operation, node)))
            throw error("VALUE_PRECONDITION_FAILED", "UI value changed");
        GuiEventListener target = locate(screen, node.get("elementIdentity").getAsLong(), identity);
        if (target == null)
            throw error(
                    "UI_DEBUG_UNSUPPORTED",
                    "This rendered node has no supported typed native mutation");
        JsonElement value = request.get("value");
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
                        if (!(target instanceof EditBox edit)
                                || value == null
                                || !value.isJsonPrimitive()
                                || !value.getAsJsonPrimitive().isString()
                                || value.getAsString().length() > 4096)
                            throw error(
                                    "INVALID_UI_DEBUG_VALUE", "Bounded EditBox string required");
                        yield () -> edit.setValue(value.getAsString());
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
        result.addProperty("nodeId", node.get("nodeId").getAsString());
        result.add("before", node.deepCopy());
        result.add("beforeResourceVersion", node.get("resourceVersion").deepCopy());
        if (request.has("debugOperationId"))
            result.add("debugOperationId", request.get("debugOperationId"));
        return result;
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
