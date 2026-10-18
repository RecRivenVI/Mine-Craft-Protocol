package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.UiMutationSupport;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.AdvancementTabUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.AdvancementsUiAccessor;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;

/** A selected native advancement canvas is not a Widget or a player-input target owner. */
final class UiAdvancementPanDebug {
    static final String OPERATION = "ui.canvas.pan.set";
    static final String CONTROL_KEY = "advancement.canvas";

    private UiAdvancementPanDebug() {}

    static AdvancementTab target(Screen screen, JsonObject node, ToLongFunction<Object> identity) {
        if (!(screen instanceof AdvancementsScreen)
                || !node.has("controlKey")
                || !CONTROL_KEY.equals(node.get("controlKey").getAsString())
                || !node.has("role")
                || !"canvas".equals(node.get("role").getAsString())
                || !node.has("elementIdentity")) return null;
        var owner = (AdvancementsUiAccessor) screen;
        var tab = owner.minecraftProtocolProbe$selected();
        if (tab == null
                || tab.getScreen() != screen
                || owner.minecraftProtocolProbe$tabs().values().stream()
                        .noneMatch(value -> value == tab)
                || node.get("elementIdentity").getAsLong() != identity.applyAsLong(tab))
            return null;
        return tab;
    }

    static void describe(
            JsonObject tree,
            Phase9ASpikeEngine revisions,
            Screen screen,
            ToLongFunction<Object> identity) {
        if (!(screen instanceof AdvancementsScreen)) return;
        for (var value : tree.getAsJsonArray("children")) {
            JsonObject node = value.getAsJsonObject();
            var tab = target(screen, node, identity);
            if (tab == null) continue;
            JsonObject state = snapshot(tab, identity);
            describeState(tree, node, revisions, state);
            break;
        }
    }

    static void describeState(
            JsonObject tree, JsonObject node, Phase9ASpikeEngine revisions, JsonObject state) {
        node.add("debugPanValue", state.get("value").deepCopy());
        node.add("debugPanRange", state.get("range").deepCopy());
        node.addProperty("debugPanReady", state.get("ready").getAsBoolean());
        node.remove("resourceVersion");
        node.remove("debugMutation");
        if (state.get("ready").getAsBoolean()) {
            JsonObject version = state.deepCopy();
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
                Set.of(UiMutationSupport.Facet.CANVAS_PAN),
                state.get("ready").getAsBoolean() ? null : "AWAITING_NATIVE_ADVANCEMENT_CENTERING");
    }

    static JsonObject snapshot(AdvancementTab tab, ToLongFunction<Object> identity) {
        var access = (AdvancementTabUiAccessor) tab;
        var value =
                pair(
                        access.minecraftProtocolProbe$scrollX(),
                        access.minecraftProtocolProbe$scrollY());
        JsonObject range = new JsonObject();
        range.add(
                "x",
                axis(
                        access.minecraftProtocolProbe$maxX() - access.minecraftProtocolProbe$minX()
                                > AdvancementsScreen.WINDOW_INSIDE_WIDTH,
                        value.get("x").getAsDouble(),
                        AdvancementsScreen.WINDOW_INSIDE_WIDTH
                                - (double) access.minecraftProtocolProbe$maxX()));
        range.add(
                "y",
                axis(
                        access.minecraftProtocolProbe$maxY() - access.minecraftProtocolProbe$minY()
                                > AdvancementsScreen.WINDOW_INSIDE_HEIGHT,
                        value.get("y").getAsDouble(),
                        AdvancementsScreen.WINDOW_INSIDE_HEIGHT
                                - (double) access.minecraftProtocolProbe$maxY()));
        range.addProperty("unit", "native_gui_pixels");
        JsonObject state = new JsonObject();
        state.add("value", value);
        state.add("range", range);
        state.addProperty("ready", access.minecraftProtocolProbe$centered());
        state.addProperty("tabOwner", identity.applyAsLong(tab));
        state.addProperty("screenOwner", identity.applyAsLong(tab.getScreen()));
        state.addProperty("root", tab.getAdvancement().getId().toString());
        state.addProperty("page", tab.getPage());
        state.addProperty(
                "widgetCollection", identity.applyAsLong(access.minecraftProtocolProbe$widgets()));
        state.addProperty("widgetCount", access.minecraftProtocolProbe$widgets().size());
        state.addProperty("minX", access.minecraftProtocolProbe$minX());
        state.addProperty("maxX", access.minecraftProtocolProbe$maxX());
        state.addProperty("minY", access.minecraftProtocolProbe$minY());
        state.addProperty("maxY", access.minecraftProtocolProbe$maxY());
        return state;
    }

    private static JsonObject axis(boolean enabled, double current, double minimum) {
        JsonObject axis = new JsonObject();
        axis.addProperty("enabled", enabled);
        axis.addProperty("minimum", enabled ? minimum : current);
        axis.addProperty("maximum", enabled ? 0 : current);
        return axis;
    }

    static JsonObject pair(double x, double y) {
        JsonObject pair = new JsonObject();
        pair.addProperty("x", x);
        pair.addProperty("y", y);
        return pair;
    }

    static JsonObject validate(JsonElement requested, JsonObject range) {
        if (requested == null
                || !requested.isJsonObject()
                || !requested.getAsJsonObject().keySet().equals(Set.of("x", "y"))) throw invalid();
        for (String key : Set.of("x", "y")) {
            JsonElement value = requested.getAsJsonObject().get(key);
            var axis = range.getAsJsonObject(key);
            double minimum = axis.get("minimum").getAsDouble();
            double maximum = axis.get("maximum").getAsDouble();
            if (!value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()
                    || !Double.isFinite(value.getAsDouble())
                    || !Double.isFinite(minimum)
                    || !Double.isFinite(maximum)
                    || minimum > maximum
                    || value.getAsDouble() < minimum
                    || value.getAsDouble() > maximum) throw invalid();
        }
        return requested.getAsJsonObject();
    }

    static Runnable prepare(
            AdvancementTab tab, JsonElement requested, ToLongFunction<Object> identity) {
        if (tab == null)
            throw new ProtocolState.ProtocolException(
                    "UI_DEBUG_UNSUPPORTED",
                    409,
                    "A current selected native advancement canvas is required");
        JsonObject accepted = snapshot(tab, identity);
        if (!accepted.get("ready").getAsBoolean())
            throw new ProtocolState.ProtocolException(
                    "UI_CONTEXT_NOT_READY",
                    409,
                    "The native advancement renderer has not centered its canvas");
        JsonObject value = validate(requested, accepted.getAsJsonObject("range"));
        double dx =
                value.get("x").getAsDouble()
                        - accepted.getAsJsonObject("value").get("x").getAsDouble();
        double dy =
                value.get("y").getAsDouble()
                        - accepted.getAsJsonObject("value").get("y").getAsDouble();
        return () -> {
            if (!accepted.equals(snapshot(tab, identity)))
                throw new ProtocolState.ProtocolException(
                        "VALUE_PRECONDITION_FAILED",
                        409,
                        "Native advancement state changed before applying the local pan");
            tab.scroll(dx, dy);
        };
    }

    private static ProtocolState.ProtocolException invalid() {
        return new ProtocolState.ProtocolException(
                "INVALID_UI_DEBUG_VALUE",
                409,
                "Canvas pan requires finite absolute {x,y} offsets inside each native range; a fixed axis must remain unchanged");
    }
}
