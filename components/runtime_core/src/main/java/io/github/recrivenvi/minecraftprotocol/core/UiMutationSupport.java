package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;

/** Describes typed setters confirmed by a Target's actual native owner, never player input. */
public final class UiMutationSupport {
    public enum Facet {
        WIDGET_FLAGS,
        EDITOR_TEXT,
        LIST_SCROLL,
        LIST_SELECTION,
        SPECTATOR_SELECTION,
        NORMALIZED_SCROLL,
        CANVAS_PAN,
        SLIDER_VALUE,
        HUD_TEXT,
        HUD_TITLE_LIFECYCLE,
        PLAYER_LIST_TEXT,
        SYSTEM_TOAST_RESET,
        SYSTEM_TOAST_HIDE,
        TUTORIAL_TOAST_PROGRESS,
        TUTORIAL_TOAST_HIDE,
        LOCAL_TOAST_HIDE
    }

    private UiMutationSupport() {}

    public static void describe(JsonObject node, Set<Facet> facets, String limitation) {
        JsonObject descriptor = new JsonObject();
        JsonObject operations = new JsonObject();
        JsonObject blocked = new JsonObject();
        boolean guarded =
                !node.has("debugTextGuardAvailable")
                        || node.get("debugTextGuardAvailable").getAsBoolean();
        boolean toastGuarded =
                !node.has("debugToastGuardAvailable")
                        || node.get("debugToastGuardAvailable").getAsBoolean();
        boolean versioned =
                node.has("resourceVersion") && node.get("resourceVersion").isJsonObject();
        for (Facet facet : facets) {
            switch (facet) {
                case WIDGET_FLAGS -> {
                    add(operations, "ui.widget.active.set", "boolean", "native_widget_flag");
                    add(operations, "ui.widget.visible.set", "boolean", "native_widget_flag");
                }
                case EDITOR_TEXT, HUD_TEXT, PLAYER_LIST_TEXT -> {
                    if (!guarded)
                        blocked.addProperty("ui.text.set", "UI_CONTENT_GUARD_UNAVAILABLE");
                    else {
                        JsonObject value =
                                add(
                                        operations,
                                        "ui.text.set",
                                        "string",
                                        facet == Facet.PLAYER_LIST_TEXT
                                                ? "native_player_list_plain_text_only"
                                                : facet == Facet.HUD_TEXT
                                                        ? "native_hud_plain_text_and_lifetime"
                                                        : "native_editor_validator_and_responder_may_submit");
                        value.addProperty("maxLength", 4096);
                    }
                }
                case LIST_SELECTION, SPECTATOR_SELECTION -> {
                    if (!node.has("debugSelectionGuardAvailable")
                            || !node.get("debugSelectionGuardAvailable").getAsBoolean())
                        blocked.addProperty(
                                "ui.list.selection.set", "UI_CONTENT_GUARD_UNAVAILABLE");
                    else {
                        JsonObject value =
                                add(
                                        operations,
                                        "ui.list.selection.set",
                                        "integer",
                                        facet == Facet.SPECTATOR_SELECTION
                                                ? "native_local_spectator_highlight_not_activation"
                                                : "native_local_suggestion_selection_not_use_or_submit");
                        value.addProperty("unit", "native_entry_index");
                        if (node.has("debugSelectionRange")) {
                            JsonObject range = node.getAsJsonObject("debugSelectionRange");
                            value.add("minimum", range.get("minimum").deepCopy());
                            value.add("maximum", range.get("maximum").deepCopy());
                        }
                    }
                }
                case HUD_TITLE_LIFECYCLE -> {
                    if (!node.has("debugTitleGuardAvailable")
                            || !node.get("debugTitleGuardAvailable").getAsBoolean()) {
                        blocked.addProperty(
                                "ui.hud.title.times.set", "UI_CONTENT_GUARD_UNAVAILABLE");
                        blocked.addProperty("ui.hud.title.clear", "UI_CONTENT_GUARD_UNAVAILABLE");
                    } else {
                        JsonObject timing =
                                add(
                                        operations,
                                        "ui.hud.title.times.set",
                                        "object",
                                        "native_hud_title_timing_and_remaining_reset");
                        timing.addProperty(
                                "requiredShape", "explicit_fadeInTicks_stayTicks_fadeOutTicks");
                        timing.addProperty("minimumFieldTicks", 0);
                        timing.addProperty("maximumFieldTicks", 12000);
                        JsonObject clear =
                                add(
                                        operations,
                                        "ui.hud.title.clear",
                                        "boolean",
                                        "native_hud_title_and_subtitle_clear");
                        clear.addProperty("onlyValue", true);
                    }
                }
                case LIST_SCROLL, NORMALIZED_SCROLL -> {
                    JsonObject value =
                            add(
                                    operations,
                                    "ui.scroll.set",
                                    "number",
                                    "native_local_display_offset");
                    value.addProperty("minimum", 0);
                    value.addProperty(
                            "unit", facet == Facet.LIST_SCROLL ? "native_pixels" : "normalized");
                    if (facet == Facet.LIST_SCROLL && node.has("maxScrollAmount"))
                        value.add("maximum", node.get("maxScrollAmount").deepCopy());
                    if (facet == Facet.NORMALIZED_SCROLL)
                        value.addProperty(
                                "maximum",
                                node.has("active") && node.get("active").getAsBoolean() ? 1 : 0);
                }
                case SLIDER_VALUE, TUTORIAL_TOAST_PROGRESS -> {
                    JsonObject value =
                            add(
                                    operations,
                                    "ui.slider.set",
                                    "number",
                                    facet == Facet.SLIDER_VALUE
                                            ? "native_slider_setting_callback"
                                            : "native_tutorial_display_progress_only");
                    value.addProperty("minimum", 0);
                    value.addProperty("maximum", 1);
                }
                case CANVAS_PAN -> {
                    if (!node.has("debugPanReady") || !node.get("debugPanReady").getAsBoolean())
                        blocked.addProperty("ui.canvas.pan.set", "UI_CONTEXT_NOT_READY");
                    else {
                        JsonObject value =
                                add(
                                        operations,
                                        "ui.canvas.pan.set",
                                        "object",
                                        "native_local_canvas_offset");
                        value.addProperty("requiredShape", "explicit_finite_x_and_y");
                        value.addProperty("unit", "native_gui_pixels");
                        if (node.has("debugPanRange"))
                            value.add("range", node.get("debugPanRange").deepCopy());
                    }
                }
                case SYSTEM_TOAST_RESET -> {
                    JsonObject value =
                            add(
                                    operations,
                                    "ui.toast.reset",
                                    "object",
                                    "native_toast_plain_text_reflow_and_lifetime");
                    value.addProperty("requiredShape", "explicit_title_and_message_strings");
                    value.addProperty("maxFieldLength", 4096);
                }
                case SYSTEM_TOAST_HIDE, TUTORIAL_TOAST_HIDE, LOCAL_TOAST_HIDE -> {
                    JsonObject value =
                            add(
                                    operations,
                                    "ui.toast.hide",
                                    "boolean",
                                    "native_toast_hide_requested_not_reversible");
                    value.addProperty("onlyValue", true);
                }
            }
        }
        if (!guarded || !toastGuarded || !versioned) {
            for (String operation : Set.copyOf(operations.keySet()))
                blocked.addProperty(
                        operation,
                        !versioned
                                ? "RESOURCE_VERSION_UNAVAILABLE"
                                : "UI_CONTENT_GUARD_UNAVAILABLE");
            operations = new JsonObject();
        }
        JsonArray supported = new JsonArray();
        operations.keySet().forEach(supported::add);
        descriptor.add("supportedOperations", supported);
        descriptor.add("operations", operations);
        descriptor.add("blockedOperations", blocked);
        descriptor.addProperty("inputDispatchCount", 0);
        descriptor.addProperty("grantsPermission", false);
        descriptor.addProperty("requiresUiArm", true);
        descriptor.addProperty("requiresResourceVersion", true);
        descriptor.addProperty("requiresInputLease", false);
        if (limitation != null) descriptor.addProperty("limitation", limitation);
        node.add("debugMutation", descriptor);
    }

    private static JsonObject add(JsonObject operations, String name, String type, String effects) {
        JsonObject value = new JsonObject();
        value.addProperty("valueType", type);
        value.addProperty("sideEffects", effects);
        operations.add(name, value);
        return value;
    }
}
