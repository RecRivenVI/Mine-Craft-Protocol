package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.core.UiMutationSupport;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.HudUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.PlayerListUiAccessor;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Local HUD message fields, independent of player input and server/world mutation. */
final class UiPresentationDebug {
    private static final List<String> FIELDS =
            List.of(
                    "hud.title",
                    "hud.subtitle",
                    "hud.actionbar",
                    "hud.playerlist.header",
                    "hud.playerlist.footer");

    static boolean handles(String nodeId) {
        return FIELDS.contains(nodeId);
    }

    static UiItemDataGuard.Content content(
            UiItemDataGuard guard, UiItemDataGuard.Budget budget, Component component) {
        return guard.capture(
                budget,
                buffer -> {
                    buffer.writeBoolean(component != null);
                    if (component == null) return;
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(
                            buffer, component);
                    String text = component.getString();
                    buffer.writeInt(text.length());
                    for (int index = 0; index < text.length(); index++)
                        buffer.writeChar(text.charAt(index));
                });
    }

    static void describe(
            JsonObject tree,
            Phase9ASpikeEngine revisions,
            Minecraft client,
            ToLongFunction<Object> identity,
            UiItemDataGuard guard) {
        if (client == null || client.level == null || !tree.has("presentation")) return;
        var nodes = tree.getAsJsonObject("presentation").getAsJsonArray("children");
        var budget = new UiItemDataGuard.Budget();
        for (String key : FIELDS) {
            Component component = current(client, key);
            Object owner = playerListField(key) ? client.gui.hud.getTabList() : client.gui.hud;
            JsonObject node = UiDebugEngine.find(nodes, key);
            if (node == null) {
                node = new JsonObject();
                node.addProperty("nodeId", key);
                node.addProperty("controlKey", key);
                node.addProperty("role", "hud_text");
                node.addProperty("label", "");
                node.addProperty("visible", false);
                node.addProperty("active", false);
                node.addProperty("boundsAvailable", false);
                node.add("actions", new JsonArray());
                nodes.add(node);
            }
            String text = component == null ? "" : component.getString();
            node.addProperty("value", text.substring(0, Math.min(2048, text.length())));
            node.addProperty("nativeTextPresent", component != null);
            var encoded = content(guard, budget, component);
            node.addProperty("debugTextGuardAvailable", encoded.available());
            JsonObject state = new JsonObject();
            state.addProperty("hudOwner", identity.applyAsLong(client.gui.hud));
            if (playerListField(key))
                state.addProperty("playerListOwner", identity.applyAsLong(owner));
            state.addProperty("worldOwner", identity.applyAsLong(client.level));
            state.addProperty(
                    "componentOwner", component == null ? 0 : identity.applyAsLong(component));
            state.addProperty("contentGuardAvailable", encoded.available());
            if (encoded.available())
                state.addProperty("nativeContentFingerprint", encoded.fingerprint());
            for (String field :
                    Set.of("screenIdentity", "overlayIdentity", "width", "height", "guiScale"))
                state.add(field, tree.get(field));
            // Lifetime ticks are not content revisions; ticking must not invalidate every request.
            node.add(
                    "resourceVersion",
                    revisions.uiRevision(
                            "presentation:" + identity.applyAsLong(owner) + ":" + key, state));
            UiMutationSupport.describe(
                    node,
                    Set.of(
                            playerListField(key)
                                    ? UiMutationSupport.Facet.PLAYER_LIST_TEXT
                                    : UiMutationSupport.Facet.HUD_TEXT),
                    playerListField(key)
                            ? "PLAIN_TEXT_REPLACEMENT_DOES_NOT_RESTORE_FORMAT"
                            : "PLAIN_TEXT_REPLACEMENT_DOES_NOT_RESTORE_FORMAT_OR_LIFETIME");
        }
    }

    static JsonObject mutate(
            Minecraft client,
            JsonObject tree,
            JsonObject request,
            DebugMutationAuthorization authorization) {
        if (client == null || client.level == null || tree.get("overlayIdentity").getAsLong() != 0)
            throw error(
                    "UI_CONTEXT_UNAVAILABLE",
                    "A live client HUD without a blocking Overlay is required");
        String key = request.get("nodeId").getAsString();
        if (!handles(key) || !"ui.text.set".equals(request.get("operation").getAsString()))
            throw error("UI_DEBUG_UNSUPPORTED", "Only typed local HUD message text is supported");
        if (authorization.isCancelled())
            throw error("OPERATION_CANCELLED", "UI mutation cancelled");
        JsonObject node = UiDebugEngine.findNode(tree, key);
        if (node == null || !node.has("resourceVersion"))
            throw error("UI_NODE_NOT_FOUND", "Native HUD message owner no longer exists");
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject())
            throw error("INVALID_RESOURCE_VERSION", "UI mutation requires expectedResourceVersion");
        ResourceVersionVerifier.verify(
                request.getAsJsonObject("expectedResourceVersion"),
                node.getAsJsonObject("resourceVersion"));
        UiDebugEngine.requireTextGuard(node);
        Component before = current(client, key);
        String text = before == null ? "" : before.getString();
        if (request.has("expectedValue")
                && !Objects.equals(request.get("expectedValue"), new JsonPrimitive(text)))
            throw error("VALUE_PRECONDITION_FAILED", "HUD message text changed");
        JsonElement value = request.get("value");
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > 4096)
            throw error("INVALID_UI_DEBUG_VALUE", "Bounded plain HUD message string required");
        Component replacement = Component.literal(value.getAsString());
        try (var permit =
                authorization.authorize(
                        tree.get("uiContextId").getAsString(),
                        tree.get("sessionEpoch").getAsString(),
                        "ui",
                        "ui")) {
            switch (key) {
                case "hud.title" -> client.gui.hud.setTitle(replacement);
                case "hud.subtitle" -> client.gui.hud.setSubtitle(replacement);
                case "hud.actionbar" -> client.gui.hud.setOverlayMessage(replacement, false);
                case "hud.playerlist.header", "hud.playerlist.footer" ->
                        setPlayerListText(client.gui.hud.getTabList(), key, replacement);
                default -> throw error("UI_DEBUG_UNSUPPORTED", "Unknown local HUD field");
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "debug.mutation.result");
        result.addProperty("schemaVersion", "phase9c-debug-v0");
        result.addProperty("mechanism", "typed_native_ui_state");
        result.addProperty(
                "invariants",
                "bounded local HUD message fields; no player input or server/world mutation");
        result.addProperty("synchronization", "client_owner_thread");
        result.addProperty("gameplayEvidence", false);
        result.add("preconditions", request.get("expectedResourceVersion").deepCopy());
        result.addProperty("operation", "ui.text.set");
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
                playerListField(key)
                        ? "native_player_list_plain_text_only"
                        : "native_hud_plain_text_and_lifetime");
        result.addProperty("nodeId", key);
        result.add("before", node.deepCopy());
        result.add("beforeResourceVersion", node.get("resourceVersion").deepCopy());
        if (request.has("debugOperationId"))
            result.add("debugOperationId", request.get("debugOperationId"));
        return result;
    }

    private static Component current(Minecraft client, String key) {
        var hud = (HudUiAccessor) client.gui.hud;
        return switch (key) {
            case "hud.title" -> hud.minecraftProtocolProbe$title();
            case "hud.subtitle" -> hud.minecraftProtocolProbe$subtitle();
            case "hud.actionbar" -> hud.minecraftProtocolProbe$actionbar();
            case "hud.playerlist.header" ->
                    ((PlayerListUiAccessor) client.gui.hud.getTabList())
                            .minecraftProtocolProbe$header();
            case "hud.playerlist.footer" ->
                    ((PlayerListUiAccessor) client.gui.hud.getTabList())
                            .minecraftProtocolProbe$footer();
            default -> throw error("UI_DEBUG_UNSUPPORTED", "Unknown local HUD field");
        };
    }

    static boolean playerListField(String key) {
        return key.equals("hud.playerlist.header") || key.equals("hud.playerlist.footer");
    }

    static void setPlayerListText(PlayerTabOverlay overlay, String key, Component replacement) {
        switch (key) {
            case "hud.playerlist.header" -> overlay.setHeader(replacement);
            case "hud.playerlist.footer" -> overlay.setFooter(replacement);
            default -> throw error("UI_DEBUG_UNSUPPORTED", "Unknown player-list text field");
        }
    }

    private static ProtocolState.ProtocolException error(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }
}
