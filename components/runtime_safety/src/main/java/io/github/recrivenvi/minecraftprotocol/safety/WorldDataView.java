package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.function.Consumer;

/** World read categories. Client knowledge is not a claim of visual visibility or server truth. */
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

    /** Unknown/native/internal events never enter the USER ring or its counters. */
    public static JsonObject userEvent(JsonObject source) {
        String type = source.has("type") ? source.get("type").getAsString() : "";
        if (type.equals("command.player.submission")
                || type.equals("command.player.configuration")) {
            JsonObject result = new JsonObject();
            for (String key :
                    Set.of(
                            "type",
                            "mode",
                            "mechanism",
                            "effectClassification",
                            "classificationSource",
                            "submissionStatus",
                            "effectConfirmed",
                            "serverValidationObserved",
                            "gameplayAcceptanceEligible",
                            "evidenceContaminated",
                            "contaminationReason",
                            "gestureId"))
                if (source.has(key) && source.get(key).isJsonPrimitive())
                    result.add(key, source.get(key).deepCopy());
            if (type.equals("command.player.configuration"))
                for (String key :
                        Set.of(
                                "configurationKind",
                                "configurationPayloadType",
                                "configuredCommandEffect"))
                    if (source.has(key) && source.get(key).isJsonPrimitive())
                        result.add(key, source.get(key).deepCopy());
            result.addProperty("dataCategory", "USER");
            return result;
        }
        if (Set.of("ui.action_result", "ui.type_text", "ui.resolve").contains(type)
                && source.has("dataCategory")
                && source.get("dataCategory").getAsString().equals("USER")) {
            JsonObject result = new JsonObject();
            for (String key :
                    Set.of(
                            "type",
                            "action",
                            "targetingSource",
                            "x",
                            "y",
                            "mechanism",
                            "inputSource",
                            "typedCodePoints"))
                if (source.has(key) && source.get(key).isJsonPrimitive())
                    result.add(key, source.get(key).deepCopy());
            if (source.has("resolvedNode") && source.get("resolvedNode").isJsonObject()) {
                JsonObject node = UiDataView.userNode(source.getAsJsonObject("resolvedNode"));
                if (node != null) result.add("resolvedNode", node);
            }
            result.addProperty("dataCategory", "USER");
            return result;
        }
        if (!type.equals("event.screen.changed")
                && !type.startsWith("control.")
                && !type.startsWith("task.")
                && !type.startsWith("agent.task.")
                && !type.startsWith("event.pipeline.")
                && !type.equals("event.input.dispatched")
                && !type.equals("diagnostics.event.self_test")
                && !type.startsWith("input.")
                && !type.equals("recording.session")) return null;
        JsonObject result = new JsonObject();
        for (String key :
                Set.of(
                        "type",
                        "category",
                        "timestampMillis",
                        "screenClass",
                        "screenRevision",
                        "controlState",
                        "reason",
                        "reconsentRequired",
                        "controlTransitionSequence",
                        "mode",
                        "controlSessionId",
                        "modeGeneration",
                        "operationId",
                        "status",
                        "taskId",
                        "phase",
                        "gestureId",
                        "index",
                        "recordingId",
                        "action",
                        "key",
                        "button",
                        "modifiers",
                        "mechanism",
                        "targetingSource",
                        "inputSource")) {
            if (source.has(key) && source.get(key).isJsonPrimitive())
                result.add(key, source.get(key).deepCopy());
        }
        result.addProperty("dataCategory", "USER");
        if (type.equals("diagnostics.event.self_test") && source.has("payload"))
            result.add("payload", source.get("payload").deepCopy());
        return result;
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
        result.addProperty("implementationStage", "bounded_client_world_model");
        result.addProperty("defaultReadCategory", "USER");
        result.addProperty("debugReadScope", "debug.world.read");
        result.addProperty("debugReadRequiresArm", false);
        result.addProperty("userPlayerCoverage", "client_player_state");
        result.addProperty("userScenePerception", "bounded_client_cache_not_visibility_filtered");
        result.addProperty("userMaxRadius", WorldClientModel.MAX_RADIUS);
        result.addProperty("userMaxSceneRadius", WorldClientModel.MAX_SCENE_RADIUS);
        result.addProperty("userMaxBlocks", WorldClientModel.MAX_BLOCKS);
        result.addProperty("userMaxEntities", WorldClientModel.MAX_ENTITIES);
        result.addProperty("userMaxBlockScan", WorldClientModel.MAX_BLOCK_SCAN);
        result.addProperty("userMaxEntityScan", WorldClientModel.MAX_ENTITY_SCAN);
        result.addProperty("userMaxResponseBytes", WorldClientModel.MAX_BYTES);
        result.addProperty("userVisibilityGuaranteed", false);
        result.addProperty("userServerDataAllowed", false);
        result.addProperty("userChunkLoadingAllowed", false);
        result.addProperty("readConsumerIsolationComplete", true);
        JsonArray semanticActions = new JsonArray();
        for (String action : WorldActionRequest.ACTIONS) semanticActions.add(action);
        result.add("userSemanticActions", semanticActions);
        result.addProperty("userSemanticActionStep", "world.action");
        result.addProperty("userMaxHoldMs", WorldActionRequest.MAX_HOLD_MILLIS);
        result.addProperty("userPlacement", "named_face_bounded_uv_native_pick");
        result.addProperty(
                "userMiningCompletion", "bounded_synchronous_native_destroy_including_fluid");
        result.addProperty("userHeldHands", "main_hand_or_off_hand_empty_main");
        result.addProperty(
                "userHeldCancellation", "release_not_rollback_native_release_effects_possible");
        result.addProperty("userTargetTtlMs", 15000);
        result.addProperty("userSemanticEffectConfirmation", "requires_separate_observation");
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
                    "/v0/server/world/entities",
                    "/v0/world/scene",
                    "/v0/providers/read",
                    "/v0/state/frames",
                    "/v0/observe/deep",
                    "/v0/wait/until",
                    "/v0/assert",
                    "/v0/events",
                    "/v0/events/resync",
                    "/v0/recordings",
                    "/v0/recordings/{recordingId}/artifact"
                }) paths.add(path);
        result.add("categorizedReadPaths", paths);
        JsonArray pending = new JsonArray();
        for (String value : new String[] {"player_command_effects"}) pending.add(value);
        result.add("pendingClassification", pending);
        return result;
    }

    private static void tag(JsonObject result, Category category) {
        result.addProperty("domain", "WORLD");
        result.addProperty("dataCategory", category.name());
    }
}
