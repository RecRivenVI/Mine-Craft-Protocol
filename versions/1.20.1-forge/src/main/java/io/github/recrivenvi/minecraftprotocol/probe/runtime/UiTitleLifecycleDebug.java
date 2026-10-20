package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.core.UiMutationSupport;
import io.github.recrivenvi.minecraftprotocol.core.UiTitleLifecycleSource;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.HudUiAccessor;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** Whole local title lifecycle, not a text-field shortcut for clearing one component. */
final class UiTitleLifecycleDebug {
    static final String NODE_ID = "hud.titles";
    static final String TIMES = "ui.hud.title.times.set";
    static final String CLEAR = "ui.hud.title.clear";
    private static final Set<String> TIME_KEYS = Set.of("fadeInTicks", "stayTicks", "fadeOutTicks");

    private UiTitleLifecycleDebug() {}

    static boolean handles(String nodeId) {
        return NODE_ID.equals(nodeId);
    }

    static void describe(
            JsonObject tree,
            Phase9ASpikeEngine revisions,
            Minecraft client,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard) {
        if (client == null || client.level == null || !tree.has("presentation")) return;
        var nodes = tree.getAsJsonObject("presentation").getAsJsonArray("children");
        var node = UiDebugEngine.find(nodes, NODE_ID);
        if (node == null) {
            node = new JsonObject();
            node.addProperty("nodeId", NODE_ID);
            node.addProperty("controlKey", NODE_ID);
            node.addProperty("role", "group");
            node.addProperty("label", "");
            node.addProperty("visible", false);
            node.addProperty("active", false);
            node.addProperty("boundsAvailable", false);
            node.add("actions", new JsonArray());
            nodes.add(node);
        }
        describeState(tree, node, revisions, snapshot(client.gui, client.level, identity, guard));
    }

    static JsonObject snapshot(
            Gui owner, Object world, ToLongFunction<Object> identity, UiItemDataGuard guard) {
        var hud = (HudUiAccessor) owner;
        var budget = new UiItemDataGuard.Budget();
        var title = UiPresentationDebug.content(guard, budget, hud.minecraftProtocolProbe$title());
        var subtitle =
                UiPresentationDebug.content(guard, budget, hud.minecraftProtocolProbe$subtitle());
        var state = new JsonObject();
        state.addProperty("hudOwner", identity.applyAsLong(owner));
        state.addProperty("worldOwner", identity.applyAsLong(world));
        state.addProperty(
                "titleOwner",
                hud.minecraftProtocolProbe$title() == null
                        ? 0
                        : identity.applyAsLong(hud.minecraftProtocolProbe$title()));
        state.addProperty(
                "subtitleOwner",
                hud.minecraftProtocolProbe$subtitle() == null
                        ? 0
                        : identity.applyAsLong(hud.minecraftProtocolProbe$subtitle()));
        state.addProperty(
                "generation",
                ((UiTitleLifecycleSource) owner).minecraftProtocolProbe$titleLifecycleGeneration());
        state.addProperty("guardAvailable", title.available() && subtitle.available());
        state.addProperty("titleFingerprint", title.fingerprint());
        state.addProperty("subtitleFingerprint", subtitle.fingerprint());
        state.add(
                "times",
                times(
                        hud.minecraftProtocolProbe$titleFadeInTime(),
                        hud.minecraftProtocolProbe$titleStayTime(),
                        hud.minecraftProtocolProbe$titleFadeOutTime()));
        state.addProperty("remainingTicks", hud.minecraftProtocolProbe$titleTime());
        state.addProperty(
                "cleared",
                hud.minecraftProtocolProbe$title() == null
                        && hud.minecraftProtocolProbe$subtitle() == null
                        && hud.minecraftProtocolProbe$titleTime() == 0);
        return state;
    }

    static JsonObject times(int fadeIn, int stay, int fadeOut) {
        var value = new JsonObject();
        value.addProperty("fadeInTicks", fadeIn);
        value.addProperty("stayTicks", stay);
        value.addProperty("fadeOutTicks", fadeOut);
        return value;
    }

    static JsonObject canonical(JsonObject state) {
        var canonical = state.deepCopy();
        // Only this observation is volatile. A setter, expiration, content or owner change still
        // invalidates the complete group; an ordinary countdown does not invalidate each receipt.
        canonical.remove("remainingTicks");
        return canonical;
    }

    static void describeState(
            JsonObject tree, JsonObject node, Phase9ASpikeEngine revisions, JsonObject state) {
        boolean guarded = state.get("guardAvailable").getAsBoolean();
        node.add("debugTitleTimes", state.get("times").deepCopy());
        node.add("debugTitleCleared", state.get("cleared").deepCopy());
        node.add("debugTitleRemainingTicks", state.get("remainingTicks").deepCopy());
        node.addProperty("debugTitleGuardAvailable", guarded);
        node.remove("resourceVersion");
        node.remove("debugMutation");
        if (guarded) {
            var version = canonical(state);
            for (String key :
                    Set.of("screenIdentity", "overlayIdentity", "width", "height", "guiScale"))
                version.add("context_" + key, tree.get(key));
            node.add(
                    "resourceVersion",
                    revisions.uiRevision(
                            "presentation:" + state.get("hudOwner").getAsLong() + ":" + NODE_ID,
                            version));
        }
        UiMutationSupport.describe(
                node,
                Set.of(UiMutationSupport.Facet.HUD_TITLE_LIFECYCLE),
                guarded
                        ? "CLEAR_REMOVES_BOTH_COMPONENTS_AND_CANNOT_RESTORE_ELAPSED_LIFETIME"
                        : "NATIVE_TITLE_CONTENT_GUARD_UNAVAILABLE");
    }

    static JsonObject validateTimes(JsonElement value) {
        if (value == null
                || !value.isJsonObject()
                || !value.getAsJsonObject().keySet().equals(TIME_KEYS))
            throw error(
                    "INVALID_UI_DEBUG_VALUE",
                    "Title timing requires all three explicit tick fields");
        var object = value.getAsJsonObject();
        for (String key : TIME_KEYS) {
            var field = object.get(key);
            if (!field.isJsonPrimitive()
                    || !field.getAsJsonPrimitive().isNumber()
                    || !Double.isFinite(field.getAsDouble())
                    || field.getAsDouble() != Math.rint(field.getAsDouble())
                    || field.getAsDouble() < 0
                    || field.getAsDouble() > 12000)
                throw error(
                        "INVALID_UI_DEBUG_VALUE",
                        "Each title duration must be an integer from 0 to 12000 ticks");
        }
        return object.deepCopy();
    }

    static Runnable prepare(Gui owner, String operation, JsonElement value) {
        if (TIMES.equals(operation)) {
            var timing = validateTimes(value);
            return () ->
                    owner.setTimes(
                            timing.get("fadeInTicks").getAsInt(),
                            timing.get("stayTicks").getAsInt(),
                            timing.get("fadeOutTicks").getAsInt());
        }
        if (!CLEAR.equals(operation))
            throw error(
                    "UI_DEBUG_UNSUPPORTED",
                    "Only title timing and complete title clearing are supported");
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()
                || !value.getAsBoolean())
            throw error("INVALID_UI_DEBUG_VALUE", "Complete title clearing requires value true");
        return owner::clear;
    }

    static JsonObject mutate(
            Minecraft client,
            JsonObject tree,
            JsonObject request,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard,
            DebugMutationAuthorization authorization) {
        if (client == null || client.level == null || tree.get("overlayIdentity").getAsLong() != 0)
            throw error(
                    "UI_CONTEXT_UNAVAILABLE",
                    "A live client HUD without a blocking Overlay is required");
        if (authorization.isCancelled())
            throw error("OPERATION_CANCELLED", "UI mutation cancelled");
        String operation = request.get("operation").getAsString();
        var node = UiDebugEngine.findNode(tree, NODE_ID);
        if (node == null || !node.has("resourceVersion"))
            throw error(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    "The complete native title group cannot be guarded");
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject())
            throw error("INVALID_RESOURCE_VERSION", "UI mutation requires expectedResourceVersion");
        ResourceVersionVerifier.verify(
                request.getAsJsonObject("expectedResourceVersion"),
                node.getAsJsonObject("resourceVersion"));
        var owner = client.gui;
        var world = client.level;
        var before = snapshot(owner, world, identity, guard);
        if (!before.get("guardAvailable").getAsBoolean())
            throw error(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    "The complete native title group cannot be guarded");
        JsonElement current = TIMES.equals(operation) ? before.get("times") : before.get("cleared");
        if (request.has("expectedValue") && !Objects.equals(request.get("expectedValue"), current))
            throw error("VALUE_PRECONDITION_FAILED", "Native title lifecycle changed");
        Runnable apply = prepare(owner, operation, request.get("value"));
        try (var permit =
                authorization.authorize(
                        tree.get("uiContextId").getAsString(),
                        tree.get("sessionEpoch").getAsString(),
                        "ui",
                        "ui")) {
            if (client.gui != owner
                    || client.level != world
                    || !canonical(before)
                            .equals(canonical(snapshot(owner, world, identity, guard))))
                throw error(
                        "VALUE_PRECONDITION_FAILED",
                        "Native title content or owner changed before mutation");
            apply.run();
        }
        var result = new JsonObject();
        result.addProperty("type", "debug.mutation.result");
        result.addProperty("schemaVersion", "phase9c-debug-v0");
        result.addProperty("mechanism", "typed_native_ui_state");
        result.addProperty(
                "invariants", "whole local title lifecycle; no player input or world mutation");
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
        result.addProperty(
                "sideEffects",
                TIMES.equals(operation)
                        ? "native_hud_title_timing_and_remaining_reset"
                        : "native_hud_title_and_subtitle_clear");
        result.addProperty("nodeId", NODE_ID);
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
