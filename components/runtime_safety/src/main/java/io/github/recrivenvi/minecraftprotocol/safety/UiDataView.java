package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.function.Consumer;

/** Detached UI projections. Permission checks remain in the authenticated Runtime transport. */
public final class UiDataView {
    private static final String COUNT_SEMANTICS = "returned_projection_recursive";
    private static final Set<String> SLOT_OWNERS = Set.of("player_inventory", "menu", "unknown");

    public enum Category {
        USER,
        DEBUG;

        public static Category parse(String value) {
            return value == null ? USER : valueOf(value);
        }
    }

    private static final Set<String> ROOT_FIELDS =
            Set.of(
                    "type",
                    "target",
                    "protocol",
                    "protocolVersion",
                    "requestId",
                    "sessionEpoch",
                    "clientTick",
                    "serverTick",
                    "screenClass",
                    "title",
                    "screenRevision",
                    "menuRevision",
                    "screenIdentity",
                    "overlayIdentity",
                    "menuIdentity",
                    "menuId",
                    "gameCursorRevision",
                    "width",
                    "height",
                    "windowWidth",
                    "windowHeight",
                    "guiScale",
                    "coverage",
                    "fallbacks",
                    "treeTruncated",
                    "treeMaxNodes",
                    "treeMaxDepth",
                    "treeLimitations",
                    "editorContextKey",
                    "uiInputContext",
                    "spectatorContextKey");
    private static final Set<String> NODE_FIELDS =
            Set.of(
                    "nodeId",
                    "role",
                    "class",
                    "nodeRevision",
                    "contentRevision",
                    "contentGuardAvailable",
                    "elementIdentity",
                    "coverage",
                    "acquisition",
                    "capabilitySource",
                    "label",
                    "labelSource",
                    "textPhase",
                    "textSupported",
                    "iconSource",
                    "renderPhase",
                    "opacity",
                    "translationKey",
                    "worldName",
                    "worldId",
                    "worldLocked",
                    "resourceId",
                    "textureId",
                    "textureU",
                    "textureV",
                    "atlasWidth",
                    "atlasHeight",
                    "spriteSemantics",
                    "spriteRegion",
                    "controlKey",
                    "packId",
                    "entryIndex",
                    "slot",
                    "slotDisabled",
                    "menuIdentity",
                    "contextKey",
                    "inputRoute",
                    "inputKey",
                    "activationEnabled",
                    "boundsSemantics",
                    "focusOffset",
                    "multiline",
                    "textReplaceStrategy",
                    "valueComplete",
                    "editable",
                    "value",
                    "valueMinimum",
                    "valueMaximum",
                    "valueSemantics",
                    "cursorPosition",
                    "focused",
                    "hovered",
                    "selected",
                    "active",
                    "visible",
                    "boundsAvailable",
                    "x",
                    "y",
                    "width",
                    "height",
                    "interactionX",
                    "interactionY",
                    "interactionLayer",
                    "actions",
                    "item",
                    "count",
                    "scrollParentId",
                    "scrollAmount",
                    "dragAxis",
                    "scrollX",
                    "scrollY",
                    "canPanX",
                    "canPanY",
                    "clickEventType",
                    "untrustedContent",
                    "effect");

    private UiDataView() {}

    public static void requireReadScopes(Category category, Consumer<String> requireScope) {
        requireScope.accept("ui");
        if (category == Category.DEBUG) {
            requireScope.accept("debug");
            requireScope.accept("debug.ui.read");
        }
    }

    public static boolean allowsRead(String mode, Category category) {
        return mode != null
                && category != null
                && Set.of("READ", "OPERATE", "TAKEOVER").contains(mode);
    }

    public static JsonObject tree(JsonObject captured, Category category) {
        if (category == Category.DEBUG) {
            JsonObject result = captured.deepCopy();
            JsonObject facts = new JsonObject();
            facts.addProperty("capturedNodeCount", count(captured.getAsJsonArray("children")));
            facts.addProperty("capturedPresentationNodeCount", presentationCount(captured));
            facts.addProperty("scope", "bounded_captured_ui_graph");
            facts.addProperty("completeGameData", false);
            result.add("debugFacts", facts);
            counts(result);
            tag(result, category);
            return result;
        }
        JsonObject result = copy(captured, ROOT_FIELDS);
        // A loading/other Overlay blocks the underlying Screen. Do not disclose its controls.
        boolean blocked =
                captured.has("overlayIdentity") && captured.get("overlayIdentity").getAsLong() != 0;
        if (blocked) result.remove("title");
        JsonArray children =
                blocked ? new JsonArray() : visibleChildren(captured.getAsJsonArray("children"));
        result.add("children", children);
        result.addProperty("treeNodeCount", count(children));
        if (captured.has("presentation")) {
            JsonObject presentation = captured.getAsJsonObject("presentation");
            JsonObject visible =
                    copy(
                            presentation,
                            Set.of(
                                    "coverage",
                                    "limitations",
                                    "hudOcclusionPolicy",
                                    "hudIconSampleStatus"));
            visible.add(
                    "children",
                    blocked
                            ? new JsonArray()
                            : visibleChildren(presentation.getAsJsonArray("children")));
            result.add("presentation", visible);
        }
        if (!blocked && captured.has("carriedStack")) {
            result.add(
                    "carriedStack",
                    copy(
                            captured.getAsJsonObject("carriedStack"),
                            Set.of(
                                    "id",
                                    "count",
                                    "empty",
                                    "contentRevision",
                                    "contentGuardAvailable")));
        }
        if (!blocked && captured.has("screenData")) {
            result.add(
                    "screenData",
                    copy(
                            captured.getAsJsonObject("screenData"),
                            Set.of("family", "pageIndex", "pageCount", "pageText")));
        }
        counts(result);
        tag(result, category);
        return result;
    }

    public static JsonObject userNode(JsonObject node) {
        if (!visible(node)) return null;
        JsonObject result = copy(node, NODE_FIELDS);
        // Native HUD text can have a separate unabridged DEBUG edit value.
        if ("hud_text".equals(string(node, "role"))) result.remove("value");
        if ("slot".equals(string(node, "role")) && node.has("slotOwner")) {
            JsonElement owner = node.get("slotOwner");
            if (owner.isJsonPrimitive()
                    && owner.getAsJsonPrimitive().isString()
                    && SLOT_OWNERS.contains(owner.getAsString()))
                result.add("slotOwner", owner.deepCopy());
        }
        if (node.has("children"))
            result.add("children", visibleChildren(node.getAsJsonArray("children")));
        // Tooltips must first be revealed by a real hover, not extracted from inactive metadata.
        if ("native_tooltip".equals(string(node, "labelSource")) && !flag(node, "hovered")) {
            result.remove("label");
            result.remove("labelSource");
        }
        return result;
    }

    public static JsonObject actionResult(JsonObject captured) {
        JsonObject result = captured.deepCopy();
        if (result.has("resolvedNode")) {
            JsonObject node = userNode(result.getAsJsonObject("resolvedNode"));
            result.remove("resolvedNode");
            if (node != null) result.add("resolvedNode", node);
        }
        tag(result, Category.USER);
        return result;
    }

    public static JsonObject contract() {
        JsonObject result = new JsonObject();
        result.addProperty("domain", "UI");
        JsonArray reads = new JsonArray();
        reads.add("USER");
        reads.add("DEBUG");
        result.add("readCategories", reads);
        result.addProperty("defaultReadCategory", "USER");
        result.addProperty("readModeIndependent", true);
        result.addProperty(
                "legacyCategorySemantics",
                "takeover_input_and_typed_operate_mutation_not_read_restrictions");
        result.addProperty("takeoverCategory", "USER");
        result.addProperty("operateCategory", "DEBUG");
        result.addProperty("debugReadScope", "debug.ui.read");
        result.addProperty("debugReadRequiresArm", false);
        result.addProperty("countSemantics", COUNT_SEMANTICS);
        result.addProperty("milestoneComplete", false);
        return result;
    }

    private static void tag(JsonObject result, Category category) {
        result.addProperty("domain", "UI");
        result.addProperty("dataCategory", category.name());
        result.addProperty("authority", "CLIENT_UI");
    }

    private static boolean visible(JsonObject node) {
        return flag(node, "visible") && !flag(node, "occluded");
    }

    private static boolean flag(JsonObject node, String name) {
        return node.has(name)
                && node.get(name).isJsonPrimitive()
                && node.get(name).getAsJsonPrimitive().isBoolean()
                && node.get(name).getAsBoolean();
    }

    private static String string(JsonObject node, String name) {
        return node.has(name) ? node.get(name).getAsString() : "";
    }

    private static JsonArray visibleChildren(JsonArray children) {
        JsonArray result = new JsonArray();
        if (children != null)
            for (JsonElement element : children) {
                JsonObject node = userNode(element.getAsJsonObject());
                if (node != null) result.add(node);
            }
        return result;
    }

    private static JsonObject copy(JsonObject source, Set<String> fields) {
        JsonObject result = new JsonObject();
        for (String field : fields)
            if (source.has(field)) result.add(field, source.get(field).deepCopy());
        return result;
    }

    private static int count(JsonArray children) {
        int result = 0;
        if (children != null)
            for (JsonElement element : children) {
                result += 1 + count(element.getAsJsonObject().getAsJsonArray("children"));
            }
        return result;
    }

    private static int presentationCount(JsonObject tree) {
        return tree.has("presentation")
                ? count(tree.getAsJsonObject("presentation").getAsJsonArray("children"))
                : 0;
    }

    private static void counts(JsonObject result) {
        result.addProperty("interactionNodeCount", count(result.getAsJsonArray("children")));
        result.addProperty("presentationNodeCount", presentationCount(result));
        result.addProperty("countSemantics", COUNT_SEMANTICS);
    }
}
