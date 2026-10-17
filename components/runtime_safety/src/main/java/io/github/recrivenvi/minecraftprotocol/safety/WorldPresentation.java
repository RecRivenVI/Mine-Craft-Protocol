package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;

/** Detached, completed native outline submissions; not a scan or a visibility solver. */
public final class WorldPresentation {
    public record Context(
            Object world,
            Object camera,
            Object screen,
            Object target,
            int width,
            int height,
            boolean active,
            String view) {}

    private Context pending;
    private Context completed;
    private boolean pendingOutline;
    private boolean completedOutline;
    private long completedAt;
    private Object previousTarget;
    private Object previousWorld;
    private String targetRef;

    public synchronized void begin(Context context) {
        pending = context;
        pendingOutline = false;
    }

    public synchronized void outlineSubmitted(Object target) {
        if (pending != null && Objects.equals(pending.target(), target)) pendingOutline = true;
    }

    public synchronized void finish(Context context, long now) {
        if (context == null
                || !context.active()
                || context.width() <= 0
                || context.height() <= 0
                || !context.equals(pending)) {
            completed = null;
            pending = null;
            previousWorld = null;
            previousTarget = null;
            targetRef = null;
            return;
        }
        completed = context;
        completedOutline = pendingOutline;
        completedAt = now;
        if (completedOutline
                && (targetRef == null
                        || previousWorld != context.world()
                        || !Objects.equals(previousTarget, context.target()))) {
            targetRef = UUID.randomUUID().toString();
        }
        previousWorld = context.world();
        previousTarget = context.target();
        if (!completedOutline) targetRef = null;
        pending = null;
    }

    public synchronized JsonObject read(Context context, long now) {
        boolean current =
                context != null
                        && context.active()
                        && context.equals(completed)
                        && now >= completedAt
                        && now - completedAt <= 250_000_000L;
        JsonObject result = new JsonObject();
        result.addProperty("type", "world.scene");
        result.addProperty("domain", "WORLD");
        result.addProperty("dataCategory", "USER");
        result.addProperty("authority", "CLIENT_PRESENTATION");
        result.addProperty("dataSource", "LIVE");
        result.addProperty("storageAccessed", false);
        result.addProperty("source", "native_block_outline_submission");
        result.addProperty("coverage", "first_person_outline_only");
        result.addProperty("available", current);
        result.addProperty("completeGameData", false);
        result.addProperty("fullScenePerception", false);
        result.addProperty("maxNodes", 1);
        result.addProperty("maxSampleAgeMs", 250);
        result.addProperty("precision", "gui_reference_center_only");
        JsonArray nodes = new JsonArray();
        if (current && completedOutline) {
            JsonObject node = new JsonObject();
            node.addProperty("role", "outlined_surface");
            node.addProperty("targetRef", targetRef);
            node.addProperty("source", "native_block_outline_submission");
            JsonObject hotspot = new JsonObject();
            hotspot.addProperty("source", "gui_reference_center_not_hit_geometry");
            hotspot.addProperty("x", context.width() / 2.0);
            hotspot.addProperty("y", context.height() / 2.0);
            node.add("hotspot", hotspot);
            node.add("actions", new JsonArray());
            nodes.add(node);
        }
        result.add("nodes", nodes);
        if (current) result.addProperty("sampleAgeMs", (now - completedAt) / 1_000_000L);
        else result.addProperty("unavailableReason", "no_current_completed_native_presentation");
        JsonArray limitations = new JsonArray();
        limitations.add(
                "Outline submission is a native presentation fact, not final framebuffer visibility or material identity");
        limitations.add(
                "No entity, terrain scan, F3, sound, material identification or semantic world action is implemented");
        limitations.add(
                "GUI center is reference metadata only, not a projected block boundary or an executable aim/click point");
        limitations.add(
                "Screen/overlay, hidden HUD, non-first-person, changed view/target or stale samples fail closed");
        result.add("limitations", limitations);
        return result;
    }
}
