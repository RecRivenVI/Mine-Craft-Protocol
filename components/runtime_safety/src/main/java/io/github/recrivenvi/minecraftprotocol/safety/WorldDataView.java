package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.function.Consumer;

/** Bounded detached world views; never infer perception from raw client state. */
public final class WorldDataView {
    public enum Category {
        USER,
        DEBUG;

        public static Category parse(String value) {
            return value == null ? USER : valueOf(value);
        }
    }

    private static final int MAX_PRESENTATION_NODES = 64;

    private WorldDataView() {}

    public static void requireReadScopes(Category category, Consumer<String> requireScope) {
        requireScope.accept("read");
        if (category == Category.DEBUG) {
            requireScope.accept("debug");
            requireScope.accept("debug.world.read");
        }
    }

    public static boolean allowsRead(String mode, Category category) {
        return "READ".equals(mode)
                || ("TAKEOVER".equals(mode) && category == Category.USER)
                || ("OPERATE".equals(mode) && category == Category.DEBUG);
    }

    public static JsonObject debug(JsonObject captured) {
        JsonObject result = captured.deepCopy();
        tag(result, Category.DEBUG);
        result.addProperty("perceptionFiltered", false);
        result.addProperty("completeGameData", false);
        return result;
    }

    public static JsonObject userPlayer(JsonObject capturedUi) {
        JsonObject visibleUi = UiDataView.tree(capturedUi, UiDataView.Category.USER);
        JsonObject result = new JsonObject();
        for (String name : Set.of("target", "protocol", "protocolVersion", "sessionEpoch")) {
            if (visibleUi.has(name)) result.add(name, visibleUi.get(name).deepCopy());
        }
        result.addProperty("type", "player.state");
        tag(result, Category.USER);
        result.addProperty("authority", "CLIENT_UI");
        result.addProperty("source", "native_hud_presentation");
        result.addProperty("dataSource", "LIVE");
        result.addProperty("storageAccessed", false);
        result.addProperty("stateSemantics", "visible_hud_only");
        result.addProperty("perceptionFiltered", true);
        result.addProperty("completeGameData", false);
        JsonArray nodes = new JsonArray();
        int matched = 0;
        boolean viewportValid = true;
        for (String extent : new String[] {"width", "height", "windowWidth", "windowHeight"}) {
            if (!visibleUi.has(extent) || visibleUi.get(extent).getAsDouble() <= 0)
                viewportValid = false;
        }
        if (viewportValid && visibleUi.has("presentation")) {
            JsonArray children =
                    visibleUi.getAsJsonObject("presentation").getAsJsonArray("children");
            if (children != null) {
                for (JsonElement child : children) {
                    JsonObject node = child.getAsJsonObject();
                    String key = node.has("controlKey") ? node.get("controlKey").getAsString() : "";
                    if (!key.startsWith("hud.hotbar")
                            && !key.startsWith("hud.offhand")
                            && !key.startsWith("hud.effect")) continue;
                    matched++;
                    if (nodes.size() < MAX_PRESENTATION_NODES) nodes.add(node.deepCopy());
                }
            }
        }
        result.add("presentation", nodes);
        result.addProperty("available", !nodes.isEmpty());
        result.addProperty("presentationTruncated", matched > nodes.size());
        result.addProperty("maxPresentationNodes", MAX_PRESENTATION_NODES);
        result.addProperty("precision", "native_presented_fields_only");
        JsonArray limitations = new JsonArray();
        limitations.add("Only existing visible hotbar/offhand/effect presentation is projected");
        limitations.add("Health, F3, scene targets and sound perception are not implemented");
        result.add("limitations", limitations);
        return result;
    }

    public static JsonObject contract() {
        JsonObject result = new JsonObject();
        result.addProperty("domain", "WORLD");
        result.addProperty("implementationStage", "basic_read_classification");
        result.addProperty("defaultReadCategory", "USER");
        result.addProperty("debugReadScope", "debug.world.read");
        result.addProperty("debugReadRequiresArm", false);
        result.addProperty("userPlayerCoverage", "visible_hud_only");
        result.addProperty("userScenePerception", "not_implemented");
        result.addProperty("consumerIsolationComplete", false);
        result.addProperty("milestoneComplete", false);
        JsonArray paths = new JsonArray();
        for (String path :
                new String[] {
                    "/v0/player",
                    "/v0/server/player",
                    "/v0/world/block",
                    "/v0/server/world/block",
                    "/v0/world/entities",
                    "/v0/server/world/entities"
                }) paths.add(path);
        result.add("categorizedReadPaths", paths);
        JsonArray pending = new JsonArray();
        for (String value :
                new String[] {
                    "deep_observation",
                    "providers_and_state_frames",
                    "wait_assert",
                    "events_and_resync",
                    "recording_and_artifact",
                    "player_command_effects"
                }) pending.add(value);
        result.add("pendingClassification", pending);
        return result;
    }

    private static void tag(JsonObject result, Category category) {
        result.addProperty("domain", "WORLD");
        result.addProperty("dataCategory", category.name());
    }
}
