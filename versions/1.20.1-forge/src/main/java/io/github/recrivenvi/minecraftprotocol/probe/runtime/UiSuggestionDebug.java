package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.suggestion.Suggestion;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.core.UiMutationSupport;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.ChatUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.CommandSuggestionsUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SuggestionListUiAccessor;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/** Native suggestion selection is local draft presentation, not suggestion use or submission. */
final class UiSuggestionDebug {
    static final String OPERATION = "ui.list.selection.set";
    static final String CONTROL_KEY = "chat.suggestions";

    private UiSuggestionDebug() {}

    record Target(
            ChatScreen screen,
            CommandSuggestions suggestions,
            CommandSuggestions.SuggestionsList list,
            EditBox input) {}

    static Target target(Screen screen, JsonObject node, ToLongFunction<Object> identity) {
        if (!(screen instanceof ChatScreen chat)
                || !node.has("controlKey")
                || !CONTROL_KEY.equals(node.get("controlKey").getAsString())
                || !node.has("role")
                || !"list".equals(node.get("role").getAsString())
                || !node.has("elementIdentity")) return null;
        var suggestions = ((ChatUiAccessor) chat).minecraftProtocolProbe$suggestions();
        if (suggestions == null) return null;
        var access = (CommandSuggestionsUiAccessor) suggestions;
        var list = access.minecraftProtocolProbe$list();
        var input = access.minecraftProtocolProbe$input();
        if (list == null
                || input == null
                || node.get("elementIdentity").getAsLong() != identity.applyAsLong(list))
            return null;
        return new Target(chat, suggestions, list, input);
    }

    static void describe(
            JsonObject tree,
            Phase9ASpikeEngine revisions,
            Screen screen,
            ToLongFunction<Object> identity,
            UiItemDataGuard contentGuard) {
        if (!(screen instanceof ChatScreen) || !tree.has("children")) return;
        for (var value : tree.getAsJsonArray("children")) {
            var node = value.getAsJsonObject();
            var target = target(screen, node, identity);
            if (target == null) continue;
            describeState(tree, node, revisions, snapshot(target, identity, contentGuard));
            break;
        }
    }

    static void describeState(
            JsonObject tree, JsonObject node, Phase9ASpikeEngine revisions, JsonObject state) {
        boolean guarded = state.get("guardAvailable").getAsBoolean();
        node.add("debugSelectionIndex", state.get("value").deepCopy());
        JsonObject range = new JsonObject();
        range.addProperty("minimum", 0);
        range.addProperty("maximum", state.get("entryCount").getAsInt() - 1);
        node.add("debugSelectionRange", range);
        node.addProperty("debugSelectionUnit", "native_entry_index");
        node.addProperty("debugSelectionGuardAvailable", guarded);
        node.remove("resourceVersion");
        node.remove("debugMutation");
        if (guarded) {
            var version = state.deepCopy();
            for (String key :
                    Set.of(
                            "screenIdentity",
                            "screenRevision",
                            "width",
                            "height",
                            "guiScale",
                            "overlayIdentity")) version.add("context_" + key, tree.get(key));
            for (String key : Set.of("nodeId", "elementIdentity", "x", "y", "width", "height"))
                if (node.has(key)) version.add("node_" + key, node.get(key));
            node.add(
                    "resourceVersion",
                    revisions.uiRevision(
                            tree.get("screenIdentity").getAsString()
                                    + ":"
                                    + node.get("nodeId").getAsString(),
                            version));
        }
        UiMutationSupport.describe(
                node,
                Set.of(UiMutationSupport.Facet.LIST_SELECTION),
                guarded ? null : "NATIVE_SUGGESTION_CONTENT_GUARD_UNAVAILABLE");
    }

    static JsonObject snapshot(
            Target target, ToLongFunction<Object> identity, UiItemDataGuard contentGuard) {
        var access = (SuggestionListUiAccessor) (Object) target.list();
        var entries = access.minecraftProtocolProbe$entries();
        var rect = access.minecraftProtocolProbe$rect();
        var content =
                captureContent(
                        contentGuard,
                        access.minecraftProtocolProbe$original(),
                        target.input().getValue(),
                        entries);
        int current = access.minecraftProtocolProbe$current();
        var state = new JsonObject();
        state.addProperty("value", current);
        state.addProperty("entryCount", entries.size());
        state.addProperty(
                "guardAvailable", content.available() && current >= 0 && current < entries.size());
        state.addProperty("contentFingerprint", content.fingerprint());
        state.addProperty("screenOwner", identity.applyAsLong(target.screen()));
        state.addProperty("suggestionsOwner", identity.applyAsLong(target.suggestions()));
        state.addProperty("listOwner", identity.applyAsLong(target.list()));
        state.addProperty("inputOwner", identity.applyAsLong(target.input()));
        state.addProperty("offset", access.minecraftProtocolProbe$offset());
        state.addProperty("x", rect.getX());
        state.addProperty("y", rect.getY());
        state.addProperty("width", rect.getWidth());
        state.addProperty("height", rect.getHeight());
        return state;
    }

    static UiItemDataGuard.Content captureContent(
            UiItemDataGuard contentGuard, String original, String input, List<Suggestion> entries) {
        return contentGuard.capture(
                new UiItemDataGuard.Budget(),
                buffer -> {
                    writeText(buffer, original);
                    writeText(buffer, input);
                    buffer.writeInt(entries.size());
                    for (var entry : entries) {
                        buffer.writeInt(entry.getRange().getStart());
                        buffer.writeInt(entry.getRange().getEnd());
                        writeText(buffer, entry.getText());
                        var tooltip = entry.getTooltip();
                        buffer.writeBoolean(tooltip != null);
                        if (tooltip != null) writeText(buffer, tooltip.getString());
                    }
                });
    }

    private static void writeText(ByteBuf buffer, String text) {
        buffer.writeInt(text.length());
        // Encode UTF-16 units exactly; replacement of unpaired surrogates must not alias tokens.
        for (int index = 0; index < text.length(); index++) buffer.writeChar(text.charAt(index));
    }

    static int validate(JsonElement value, int entryCount) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())
                || value.getAsDouble() != Math.rint(value.getAsDouble())
                || value.getAsDouble() < 0
                || value.getAsDouble() >= entryCount)
            throw new ProtocolState.ProtocolException(
                    "INVALID_UI_DEBUG_VALUE",
                    409,
                    "Suggestion selection requires an integer inside the current native entry range");
        return value.getAsInt();
    }

    static Runnable prepare(
            Target target,
            JsonElement value,
            ToLongFunction<Object> identity,
            UiItemDataGuard contentGuard) {
        if (target == null)
            throw new ProtocolState.ProtocolException(
                    "UI_DEBUG_UNSUPPORTED",
                    409,
                    "A current native chat suggestion list is required");
        var accepted = snapshot(target, identity, contentGuard);
        if (!accepted.get("guardAvailable").getAsBoolean())
            throw new ProtocolState.ProtocolException(
                    "UI_CONTENT_GUARD_UNAVAILABLE",
                    409,
                    "The complete native suggestion state could not be guarded");
        int index = validate(value, accepted.get("entryCount").getAsInt());
        return () -> {
            var suggestions =
                    ((ChatUiAccessor) target.screen()).minecraftProtocolProbe$suggestions();
            if (suggestions != target.suggestions()
                    || ((CommandSuggestionsUiAccessor) suggestions).minecraftProtocolProbe$list()
                            != target.list()
                    || ((CommandSuggestionsUiAccessor) suggestions).minecraftProtocolProbe$input()
                            != target.input()
                    || !accepted.equals(snapshot(target, identity, contentGuard)))
                throw new ProtocolState.ProtocolException(
                        "VALUE_PRECONDITION_FAILED",
                        409,
                        "Native suggestion content or owner changed before selection");
            target.list().select(index);
        };
    }
}
