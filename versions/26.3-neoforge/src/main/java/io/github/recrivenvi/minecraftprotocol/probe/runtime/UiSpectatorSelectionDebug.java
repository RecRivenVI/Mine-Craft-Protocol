package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.core.UiMutationSupport;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SpectatorGuiUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SpectatorMenuUiAccessor;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.client.gui.spectator.SpectatorMenuItem;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.GameType;

/**
 * Selects only a different native highlight; selecting an already highlighted item can activate it.
 */
final class UiSpectatorSelectionDebug {
    static final String NODE_ID = "spectator.menu";
    static final String OPERATION = "ui.list.selection.set";

    private UiSpectatorSelectionDebug() {}

    record Target(Minecraft client, SpectatorGui gui, SpectatorMenu menu) {}

    static boolean handles(String nodeId) {
        return NODE_ID.equals(nodeId);
    }

    static Target target(Minecraft client) {
        if (client == null
                || client.level == null
                || client.player == null
                || client.gameMode == null
                || client.gameMode.getPlayerMode() != GameType.SPECTATOR) return null;
        var gui = client.gui.hud.getSpectatorGui();
        var menu = ((SpectatorGuiUiAccessor) gui).minecraftProtocolProbe$menu();
        return menu == null ? null : new Target(client, gui, menu);
    }

    static void describe(
            JsonObject tree,
            Phase9ASpikeEngine revisions,
            Minecraft client,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard) {
        var target = target(client);
        if (target == null || !tree.has("children")) return;
        var nodes = tree.getAsJsonArray("children");
        var node = UiDebugEngine.find(nodes, NODE_ID);
        if (node == null) {
            node = new JsonObject();
            node.addProperty("nodeId", NODE_ID);
            node.addProperty("controlKey", NODE_ID);
            node.addProperty("elementIdentity", identity.applyAsLong(target.menu()));
            node.addProperty("role", "group");
            node.addProperty("label", "");
            node.addProperty("visible", false);
            node.addProperty("active", false);
            node.addProperty("boundsAvailable", false);
            node.add("actions", new JsonArray());
            nodes.add(node);
        }
        describeState(tree, node, revisions, snapshot(target, identity, guard));
    }

    static JsonObject snapshot(
            Target target, ToLongFunction<Object> identity, UiItemDataGuard guard) {
        var menu = target.menu();
        var category = menu.getSelectedCategory();
        var entries = category.getItems();
        var pageItems = new SpectatorMenuItem[9];
        var items = new JsonArray();
        for (int index = 0; index < pageItems.length; index++) {
            var item = menu.getItem(index);
            pageItems[index] = item;
            var entry = new JsonObject();
            entry.addProperty("index", index);
            entry.addProperty("owner", identity.applyAsLong(item));
            entry.addProperty("empty", item == SpectatorMenu.EMPTY_SLOT);
            entry.addProperty("enabled", item.isEnabled());
            items.add(entry);
        }
        var content =
                guard.capture(
                        new UiItemDataGuard.Budget(),
                        buffer -> {
                            if (entries.size() > 2048)
                                throw new IllegalArgumentException(
                                        "Native spectator category exceeds the bounded content guard");
                            buffer.writeInt(entries.size());
                            for (var item : entries) {
                                buffer.writeLong(identity.applyAsLong(item));
                                buffer.writeBoolean(item.isEnabled());
                                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(
                                        buffer, item.getName());
                            }
                            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(
                                    buffer, category.getPrompt());
                            for (var item : pageItems)
                                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(
                                        buffer, item.getName());
                        });
        var state = new JsonObject();
        state.addProperty("worldOwner", identity.applyAsLong(target.client().level));
        state.addProperty("playerOwner", identity.applyAsLong(target.client().player));
        state.addProperty("guiOwner", identity.applyAsLong(target.gui()));
        state.addProperty("menuOwner", identity.applyAsLong(menu));
        state.addProperty("categoryOwner", identity.applyAsLong(category));
        state.addProperty("page", ((SpectatorMenuUiAccessor) menu).minecraftProtocolProbe$page());
        state.addProperty("value", menu.getSelectedSlot());
        state.add("items", items);
        state.addProperty("contentFingerprint", content.fingerprint());
        state.addProperty(
                "guardAvailable",
                content.available() && menu.getSelectedSlot() >= -1 && menu.getSelectedSlot() <= 8);
        return state;
    }

    static void describeState(
            JsonObject tree, JsonObject node, Phase9ASpikeEngine revisions, JsonObject state) {
        boolean guarded = state.get("guardAvailable").getAsBoolean();
        node.add("debugSelectionIndex", state.get("value").deepCopy());
        var range = new JsonObject();
        range.addProperty("minimum", 0);
        range.addProperty("maximum", 8);
        node.add("debugSelectionRange", range);
        node.addProperty("debugSelectionUnit", "native_entry_index");
        node.addProperty("debugSelectionGuardAvailable", guarded);
        node.remove("resourceVersion");
        node.remove("debugMutation");
        if (guarded) {
            var version = state.deepCopy();
            for (String key :
                    Set.of("screenIdentity", "overlayIdentity", "width", "height", "guiScale"))
                version.add("context_" + key, tree.get(key));
            node.add(
                    "resourceVersion",
                    revisions.uiRevision(
                            "spectator:" + state.get("menuOwner").getAsLong() + ":selection",
                            version));
        }
        UiMutationSupport.describe(
                node,
                Set.of(UiMutationSupport.Facet.SPECTATOR_SELECTION),
                guarded
                        ? "NONEMPTY_CURRENT_PAGE_ONLY_INITIAL_MINUS_ONE_REQUIRES_MENU_CLOSE_TO_RESTORE"
                        : "NATIVE_SPECTATOR_CONTENT_GUARD_UNAVAILABLE");
    }

    static int validate(JsonElement value, JsonObject state) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())
                || value.getAsDouble() != Math.rint(value.getAsDouble())
                || value.getAsDouble() < 0
                || value.getAsDouble() > 8)
            throw error(
                    "INVALID_UI_DEBUG_VALUE",
                    "Spectator highlighting requires an integer current-page index from 0 to 8");
        int index = value.getAsInt();
        var item = state.getAsJsonArray("items").get(index).getAsJsonObject();
        if (item.get("empty").getAsBoolean())
            throw error(
                    "INVALID_UI_DEBUG_VALUE",
                    "An existing native item on the current spectator page is required");
        return index;
    }

    static Runnable prepare(
            Target target,
            JsonElement value,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard) {
        if (target == null)
            throw error("UI_CONTEXT_UNAVAILABLE", "A live native spectator menu is required");
        var accepted = snapshot(target, identity, guard);
        if (!accepted.get("guardAvailable").getAsBoolean())
            throw error(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    "The complete native spectator state could not be guarded");
        int index = validate(value, accepted);
        return () -> {
            if (((SpectatorGuiUiAccessor) target.gui()).minecraftProtocolProbe$menu()
                            != target.menu()
                    || !accepted.equals(snapshot(target, identity, guard)))
                throw error(
                        "VALUE_PRECONDITION_FAILED",
                        "Native spectator owner, page or items changed before selection");
            selectHighlight(target.menu(), index);
        };
    }

    static void selectHighlight(SpectatorMenu menu, int index) {
        // Native selectSlot activates an enabled item if it is already selected. Never call it in
        // that branch: this operation changes local highlighting, not category or world state.
        if (menu.getSelectedSlot() == index) return;
        menu.selectSlot(index);
    }

    static JsonObject mutate(
            Minecraft client,
            JsonObject tree,
            JsonObject request,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard,
            DebugMutationAuthorization authorization) {
        if (tree.get("overlayIdentity").getAsLong() != 0)
            throw error(
                    "UI_CONTEXT_UNAVAILABLE",
                    "A spectator menu without a blocking Overlay is required");
        if (!OPERATION.equals(request.get("operation").getAsString()))
            throw error("UI_DEBUG_UNSUPPORTED", "Only local spectator highlighting is supported");
        if (authorization.isCancelled())
            throw error("OPERATION_CANCELLED", "UI mutation cancelled");
        var target = target(client);
        if (target == null)
            throw error("UI_CONTEXT_UNAVAILABLE", "The native spectator menu no longer exists");
        var node = UiDebugEngine.findNode(tree, NODE_ID);
        if (node == null || !node.has("resourceVersion"))
            throw error(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    "The complete native spectator state could not be guarded");
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject())
            throw error("INVALID_RESOURCE_VERSION", "UI mutation requires expectedResourceVersion");
        ResourceVersionVerifier.verify(
                request.getAsJsonObject("expectedResourceVersion"),
                node.getAsJsonObject("resourceVersion"));
        var current = snapshot(target, identity, guard);
        if (request.has("expectedValue")
                && !Objects.equals(request.get("expectedValue"), current.get("value")))
            throw error("VALUE_PRECONDITION_FAILED", "Native spectator highlight changed");
        var apply = prepare(target, request.get("value"), identity, guard);
        try (var permit =
                authorization.authorize(
                        tree.get("uiContextId").getAsString(),
                        tree.get("sessionEpoch").getAsString(),
                        "ui",
                        "ui")) {
            var active = target(client);
            if (active == null || active.gui() != target.gui() || active.menu() != target.menu())
                throw error(
                        "VALUE_PRECONDITION_FAILED",
                        "The native spectator menu owner was replaced");
            apply.run();
        }
        var result = new JsonObject();
        result.addProperty("type", "debug.mutation.result");
        result.addProperty("schemaVersion", "phase9c-debug-v0");
        result.addProperty("mechanism", "typed_native_ui_state");
        result.addProperty(
                "invariants",
                "local spectator highlight only; no item activation, player input or world mutation");
        result.addProperty("synchronization", "client_owner_thread");
        result.addProperty("gameplayEvidence", false);
        result.add("preconditions", request.get("expectedResourceVersion").deepCopy());
        result.addProperty("operation", OPERATION);
        result.addProperty("namespace", "ui");
        result.addProperty("domain", "UI");
        result.addProperty("dataCategory", "DEBUG");
        result.addProperty("authority", "CLIENT_UI");
        result.addProperty("evidence", "diagnostic");
        result.addProperty("evidenceAuthority", "DEBUG_PRIVILEGED");
        result.addProperty("directMutationUsed", true);
        result.addProperty("inputDispatchCount", 0);
        result.addProperty("sideEffects", "native_local_spectator_highlight_not_activation");
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
