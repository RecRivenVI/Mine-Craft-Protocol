package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.core.UiMutationSupport;
import io.github.recrivenvi.minecraftprotocol.core.UiToastResetSource;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.*;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.FormattedCharSequence;

/** Explicit local presentation mutations, never tutorial completion or gameplay input. */
final class UiToastDebug {
    static boolean handles(String nodeId) {
        return nodeId.startsWith("toast.");
    }

    static UiItemDataGuard.Content content(
            UiItemDataGuard guard, UiItemDataGuard.Budget budget, Toast toast) {
        return guard.capture(
                budget,
                buffer -> {
                    if (toast instanceof SystemToast) {
                        buffer.writeByte(1);
                        var system = (SystemToastUiAccessor) toast;
                        lines(buffer, system.minecraftProtocolProbe$title());
                        lines(buffer, system.minecraftProtocolProbe$message());
                    } else if (toast instanceof TutorialToast) {
                        buffer.writeByte(2);
                        lines(
                                buffer,
                                ((TutorialToastUiAccessor) toast).minecraftProtocolProbe$lines());
                    } else if (visibilityOnly(toast)) {
                        buffer.writeByte(4);
                        buffer.writeBoolean(toast.getWantedVisibility() == Toast.Visibility.HIDE);
                    } else throw new IllegalArgumentException("Unsupported native Toast type");
                });
    }

    static boolean visibilityOnly(Toast toast) {
        return toast instanceof FriendToast;
    }

    private static java.util.Map<String, Object> residentInstances(Object manager) {
        var result = new java.util.LinkedHashMap<String, Object>();
        int index = 0;
        for (Object instance :
                ((ToastManagerUiAccessor) manager).minecraftProtocolProbe$visible()) {
            if (index >= 8) break;
            result.put("toast." + index++, instance);
        }
        return result;
    }

    private static void lines(ByteBuf buffer, List<FormattedCharSequence> lines) {
        buffer.writeInt(lines.size());
        for (var line : lines) {
            buffer.writeBoolean(line != null);
            if (line == null) continue;
            // The native fallback Language returns false after a complete visit, unlike
            // ClientLanguage. Our sink never short-circuits; exceeding the budget throws.
            line.accept(
                    (index, style, codepoint) -> {
                        buffer.writeBoolean(true);
                        buffer.writeInt(index);
                        buffer.writeInt(codepoint);
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(
                                buffer, Component.literal("").setStyle(style));
                        return true;
                    });
            buffer.writeBoolean(false);
        }
    }

    static void describe(
            JsonObject tree,
            Phase9ASpikeEngine revisions,
            Minecraft client,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard) {
        if (client == null || !tree.has("presentation")) return;
        Object manager = client.gui.toastManager();
        var budget = new UiItemDataGuard.Budget();
        for (var entry : residentInstances(manager).entrySet()) {
            String key = entry.getKey();
            Object instance = entry.getValue();
            Toast toast = ((ToastInstanceUiAccessor) instance).minecraftProtocolProbe$toast();
            if (!(toast instanceof SystemToast)
                    && !(toast instanceof TutorialToast)
                    && !visibilityOnly(toast)) continue;
            JsonObject node = UiDebugEngine.findNode(tree, key);
            if (node == null) continue;
            var encoded = content(guard, budget, toast);
            node.addProperty("debugToastGuardAvailable", encoded.available());
            node.addProperty(
                    "debugToastGuardScope",
                    visibilityOnly(toast) ? "native_visibility" : "complete_native_text");
            node.addProperty(
                    "debugToastKind",
                    toast instanceof SystemToast
                            ? "system"
                            : toast instanceof TutorialToast ? "tutorial" : "friend");
            JsonObject state =
                    canonicalState(tree, manager, instance, toast, encoded, identity, node);
            node.add(
                    "resourceVersion",
                    revisions.uiRevision("toast:" + identity.applyAsLong(instance), state));
            var facets = java.util.EnumSet.noneOf(UiMutationSupport.Facet.class);
            if (toast instanceof SystemToast) {
                facets.add(UiMutationSupport.Facet.SYSTEM_TOAST_RESET);
                facets.add(UiMutationSupport.Facet.SYSTEM_TOAST_HIDE);
            } else if (toast instanceof TutorialToast) {
                facets.add(UiMutationSupport.Facet.TUTORIAL_TOAST_HIDE);
                if (node.get("debugProgressable").getAsBoolean())
                    facets.add(UiMutationSupport.Facet.TUTORIAL_TOAST_PROGRESS);
            } else {
                facets.add(UiMutationSupport.Facet.LOCAL_TOAST_HIDE);
            }
            UiMutationSupport.describe(
                    node, facets, "LOCAL_DISPLAY_ONLY_NOT_TUTORIAL_OR_GAMEPLAY_COMPLETION");
        }
    }

    static JsonObject canonicalState(
            JsonObject tree,
            Object manager,
            Object instance,
            Toast toast,
            UiItemDataGuard.Content encoded,
            ToLongFunction<Object> identity,
            JsonObject node) {
        JsonObject state = new JsonObject();
        state.addProperty("manager", identity.applyAsLong(manager));
        state.addProperty("instance", identity.applyAsLong(instance));
        state.addProperty("toast", identity.applyAsLong(toast));
        state.addProperty("guardAvailable", encoded.available());
        if (encoded.available()) state.addProperty("content", encoded.fingerprint());
        if (toast instanceof SystemToast) {
            var system = (SystemToastUiAccessor) toast;
            JsonObject value = systemValue(system, false);
            node.add("debugToastValue", value);
            node.addProperty("debugHideRequested", system.minecraftProtocolProbe$forceHide());
            systemResetState(state, (UiToastResetSource) system);
            state.add("hideRequested", node.get("debugHideRequested"));
        } else if (toast instanceof TutorialToast) {
            var tutorial = (TutorialToastUiAccessor) toast;
            node.addProperty("debugProgressable", tutorial.minecraftProtocolProbe$progressable());
            node.addProperty("debugProgress", tutorial.minecraftProtocolProbe$progress());
            node.addProperty(
                    "debugHideRequested",
                    tutorial.minecraftProtocolProbe$visibility() == Toast.Visibility.HIDE);
            state.add("progressable", node.get("debugProgressable"));
            state.add("progress", node.get("debugProgress"));
            state.add("hideRequested", node.get("debugHideRequested"));
        } else {
            node.addProperty(
                    "debugHideRequested", toast.getWantedVisibility() == Toast.Visibility.HIDE);
            state.add("hideRequested", node.get("debugHideRequested"));
        }
        for (String field :
                Set.of("screenIdentity", "overlayIdentity", "width", "height", "guiScale"))
            state.add(field, tree.get(field));
        return state;
    }

    static void systemResetState(JsonObject state, UiToastResetSource source) {
        // A reset's native tick consumes changed/lastChanged after its mutation receipt.
        state.addProperty("resetGeneration", source.minecraftProtocolProbe$resetGeneration());
    }

    private static JsonObject systemValue(SystemToastUiAccessor system, boolean full) {
        JsonObject value = new JsonObject();
        value.addProperty("title", plain(system.minecraftProtocolProbe$title(), full));
        value.addProperty("message", plain(system.minecraftProtocolProbe$message(), full));
        return value;
    }

    private static String plain(List<FormattedCharSequence> lines, boolean full) {
        if (!full) return UiPresentationSnapshot.lines(lines);
        StringBuilder text = new StringBuilder();
        for (var line : lines) {
            if (!text.isEmpty()) text.append('\n');
            if (line != null)
                line.accept(
                        (index, style, codepoint) -> {
                            text.appendCodePoint(codepoint);
                            return true;
                        });
        }
        return text.toString();
    }

    static JsonObject resetValue(JsonElement value) {
        if (value == null
                || !value.isJsonObject()
                || !value.getAsJsonObject().keySet().equals(Set.of("title", "message")))
            throw error(
                    "INVALID_UI_DEBUG_VALUE", "Toast reset requires explicit title and message");
        for (String key : Set.of("title", "message")) {
            JsonElement text = value.getAsJsonObject().get(key);
            if (!text.isJsonPrimitive()
                    || !text.getAsJsonPrimitive().isString()
                    || text.getAsString().length() > 4096)
                throw error("INVALID_UI_DEBUG_VALUE", "Toast reset requires bounded plain strings");
        }
        return value.getAsJsonObject();
    }

    static float progressValue(JsonElement value) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())
                || value.getAsDouble() < 0
                || value.getAsDouble() > 1)
            throw error("INVALID_UI_DEBUG_VALUE", "Toast progress must be finite in [0,1]");
        return value.getAsFloat();
    }

    static JsonObject mutate(
            Minecraft client,
            JsonObject tree,
            JsonObject request,
            ToLongFunction<Object> identity,
            DebugMutationAuthorization authorization) {
        if (client == null || tree.get("overlayIdentity").getAsLong() != 0)
            throw error("UI_CONTEXT_UNAVAILABLE", "An unblocked client Toast manager is required");
        if (authorization.isCancelled())
            throw error("OPERATION_CANCELLED", "Toast mutation cancelled");
        String key = request.get("nodeId").getAsString();
        JsonObject node = UiDebugEngine.findNode(tree, key);
        if (node == null || !node.has("resourceVersion"))
            throw error("UI_NODE_NOT_FOUND", "Supported resident native Toast no longer exists");
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject())
            throw error(
                    "INVALID_RESOURCE_VERSION", "Toast mutation requires expectedResourceVersion");
        ResourceVersionVerifier.verify(
                request.getAsJsonObject("expectedResourceVersion"),
                node.getAsJsonObject("resourceVersion"));
        if (!node.get("debugToastGuardAvailable").getAsBoolean())
            throw error(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    "Required native Toast state exceeded its capture budget");
        Object instance = residentInstances(client.gui.toastManager()).get(key);
        Toast toast =
                instance == null
                        ? null
                        : ((ToastInstanceUiAccessor) instance).minecraftProtocolProbe$toast();
        if (toast == null) throw error("UI_NODE_NOT_FOUND", "Native Toast instance disappeared");
        String operation = request.get("operation").getAsString();
        JsonElement before;
        Runnable apply;
        String sideEffects;
        if (operation.equals("ui.toast.reset") && toast instanceof SystemToast system) {
            JsonObject replacement = resetValue(request.get("value"));
            before = systemValue((SystemToastUiAccessor) system, true);
            apply =
                    () ->
                            system.reset(
                                    Component.literal(replacement.get("title").getAsString()),
                                    Component.literal(replacement.get("message").getAsString()));
            sideEffects = "native_toast_plain_text_reflow_and_lifetime";
        } else if (operation.equals("ui.slider.set") && toast instanceof TutorialToast tutorial) {
            if (!node.get("debugProgressable").getAsBoolean())
                throw error("UI_DEBUG_UNSUPPORTED", "This native tutorial has no progress bar");
            float replacement = progressValue(request.get("value"));
            before = node.get("debugProgress");
            apply = () -> tutorial.updateProgress(replacement);
            sideEffects = "native_tutorial_display_progress_only";
        } else if (operation.equals("ui.toast.hide")) {
            if (!new JsonPrimitive(true).equals(request.get("value")))
                throw error("INVALID_UI_DEBUG_VALUE", "Toast hide is one-way; value must be true");
            before = node.get("debugHideRequested");
            if (toast instanceof TutorialToast tutorial) apply = tutorial::hide;
            else if (toast instanceof SystemToast system) apply = system::forceHide;
            else if (toast instanceof FriendToast friend) apply = friend::hide;
            else throw error("UI_DEBUG_UNSUPPORTED", "Unsupported native Toast type");
            sideEffects = "native_toast_hide_requested_not_reversible";
        } else
            throw error("UI_DEBUG_UNSUPPORTED", "No approved setter for this Toast and operation");
        if (request.has("expectedValue") && !Objects.equals(request.get("expectedValue"), before))
            throw error("VALUE_PRECONDITION_FAILED", "Toast value changed");
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
                "invariants", "local resident Toast fields; no gameplay or tutorial completion");
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
        result.addProperty("sideEffects", sideEffects);
        result.addProperty("nodeId", key);
        result.add("before", node.deepCopy());
        result.add("beforeResourceVersion", node.get("resourceVersion").deepCopy());
        if (request.has("debugOperationId"))
            result.add("debugOperationId", request.get("debugOperationId"));
        return result;
    }

    private static ProtocolState.ProtocolException error(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }
}
