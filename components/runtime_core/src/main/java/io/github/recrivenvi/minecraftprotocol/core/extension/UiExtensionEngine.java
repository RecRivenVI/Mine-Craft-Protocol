package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.extension.UiAdapter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/** Projects detached observations; deliberately has no click, setter or packet dispatch API. */
public final class UiExtensionEngine {
    private static final int MAX_NODES = 2048, MAX_BYTES = 131072;
    private static final long CALLBACK_BUDGET_NANOS = 10_000_000L;
    private static final Set<String> GEOMETRY = Set.of("x", "y", "width", "height");
    private static final Set<String> NATIVE_STATE =
            Set.of("x", "y", "width", "height", "active", "visible", "boundsAvailable", "occluded");
    private static final Set<String> PROTECTED =
            Set.of(
                    "nodeId",
                    "elementIdentity",
                    "children",
                    "capabilitySource",
                    "coverage",
                    "acquisition",
                    "class",
                    "inputRoute",
                    "inputKey",
                    "menuIdentity",
                    "slot",
                    "slotIndex",
                    "containerId",
                    "contextKey");
    private final String runtimeId;
    private final Supplier<List<UiAdapter>> adapters;

    public UiExtensionEngine(String runtimeId, Supplier<List<UiAdapter>> adapters) {
        this.runtimeId = java.util.Objects.requireNonNull(runtimeId);
        this.adapters = java.util.Objects.requireNonNull(adapters);
    }

    public void project(
            JsonObject root,
            String worldContext,
            Object screen,
            int framebufferWidth,
            int framebufferHeight,
            ToLongFunction<Object> identity,
            BooleanSupplier owner,
            UiAdapter.View view) {
        if (!owner.getAsBoolean())
            throw new IllegalStateException("UI projection is client-owner only");
        if (!root.has("children")
                || integer(root, "width") < 1
                || integer(root, "height") < 1
                || integer(root, "windowWidth") < 1
                || integer(root, "windowHeight") < 1
                || framebufferWidth < 1
                || framebufferHeight < 1) return;
        UiAdapter.Frame frame =
                new UiAdapter.Frame(
                        runtimeId,
                        worldContext,
                        root.get("screenClass").getAsString(),
                        root.get("screenIdentity").getAsLong(),
                        root.get("screenRevision").getAsLong(),
                        integer(root, "width"),
                        integer(root, "height"),
                        integer(root, "windowWidth"),
                        integer(root, "windowHeight"),
                        framebufferWidth,
                        framebufferHeight,
                        root.get("guiScale").getAsDouble());
        JsonArray children = root.getAsJsonArray("children"), issues = new JsonArray();
        JsonObject nativeSnapshot = root.deepCopy();
        int total = count(children), bytes = 0;
        Set<Long> enriched = new HashSet<>();
        for (UiAdapter adapter : adapters.get()) {
            UiAdapter.Descriptor descriptor = null;
            var active = new java.util.concurrent.atomic.AtomicBoolean(true);
            UiAdapter.Context context =
                    new UiAdapter.Context() {
                        private void requireOwner() {
                            if (!active.get() || !owner.getAsBoolean())
                                throw new IllegalStateException(
                                        "Native UI capture context is no longer active on its owner");
                        }

                        @Override
                        public UiAdapter.Frame frame() {
                            return frame;
                        }

                        @Override
                        public UiAdapter.View view() {
                            return view;
                        }

                        @Override
                        public Object nativeScreen() {
                            requireOwner();
                            return screen;
                        }

                        @Override
                        public JsonObject nativeTree() {
                            requireOwner();
                            return view == UiAdapter.View.USER
                                    ? io.github.recrivenvi.minecraftprotocol.safety.UiDataView.tree(
                                            nativeSnapshot,
                                            io.github.recrivenvi.minecraftprotocol.safety.UiDataView
                                                    .Category.USER)
                                    : nativeSnapshot.deepCopy();
                        }

                        @Override
                        public long identify(Object element) {
                            requireOwner();
                            return identity.applyAsLong(java.util.Objects.requireNonNull(element));
                        }
                    };
            try {
                descriptor = adapter.descriptor();
                if (!descriptor.views().contains(view)) continue;
                long started = System.nanoTime();
                boolean matches = adapter.matches(frame);
                UiAdapter.Projection projection = matches ? adapter.capture(context) : null;
                long now = System.nanoTime();
                if (now - started > CALLBACK_BUDGET_NANOS)
                    throw new ExtensionFailure(
                            "EXTENSION_UI_ENTRY_BUDGET",
                            "UI capture must return a ready snapshot without waiting");
                if (!matches) continue;
                if (projection == null) {
                    issue(issues, descriptor.id(), "snapshot_pending");
                    continue;
                }
                if (!projection.frame().equals(frame)
                        || projection.capturedAtNanos() > now
                        || now - projection.capturedAtNanos()
                                > descriptor.maxAgeMillis() * 1_000_000L)
                    throw new ExtensionFailure(
                            "EXTENSION_UI_SNAPSHOT_STALE",
                            "Snapshot context/layout/time no longer matches");
                if (projection.nodes().size() > descriptor.maxNodes())
                    throw new ExtensionFailure(
                            "EXTENSION_UI_NODE_BUDGET", "Declared node budget exceeded");
                Set<String> keys = new HashSet<>();
                Set<Long> owned = new HashSet<>();
                var pending = new java.util.ArrayList<Runnable>();
                int added = 0, payloadBytes = 0;
                for (var item : projection.nodes()) {
                    if (!keys.add(item.key()))
                        throw new ExtensionFailure(
                                "EXTENSION_UI_DUPLICATE_TARGET", "Projection keys must be unique");
                    JsonObject data = item.properties();
                    if (data.keySet().stream().anyMatch(PROTECTED::contains))
                        throw new ExtensionFailure(
                                "EXTENSION_UI_NODE_INVALID",
                                "Projection cannot replace native identity/routing");
                    payloadBytes += data.toString().getBytes(StandardCharsets.UTF_8).length;
                    if (payloadBytes + bytes > MAX_BYTES)
                        throw new ExtensionFailure(
                                "EXTENSION_UI_BYTE_BUDGET", "Projection payload budget exceeded");
                    JsonObject nativeNode =
                            item.nativeElementIdentity() == 0
                                    ? null
                                    : find(children, item.nativeElementIdentity());
                    if (item.nativeElementIdentity() != 0 && nativeNode == null)
                        throw new ExtensionFailure(
                                "EXTENSION_UI_NATIVE_TARGET_GONE",
                                "Native enrichment target is not in the current tree");
                    if (nativeNode != null
                            && (!owned.add(item.nativeElementIdentity())
                                    || enriched.contains(item.nativeElementIdentity())))
                        throw new ExtensionFailure(
                                "EXTENSION_UI_DUPLICATE_TARGET",
                                "Only one projection may enrich a native element");
                    if (nativeNode != null
                            && data.keySet().stream().anyMatch(NATIVE_STATE::contains))
                        throw new ExtensionFailure(
                                "EXTENSION_UI_GEOMETRY_PROTECTED",
                                "Native widget geometry comes from Minecraft");
                    JsonObject node = nativeNode == null ? new JsonObject() : nativeNode.deepCopy();
                    data.entrySet()
                            .forEach(
                                    entry -> node.add(entry.getKey(), entry.getValue().deepCopy()));
                    if (nativeNode == null) {
                        if (!bounds(node, item.space(), frame, view)) continue;
                        String key = descriptor.id() + "\u0000" + item.key();
                        node.addProperty(
                                "nodeId",
                                "extension:"
                                        + frame.screenIdentity()
                                        + ":"
                                        + frame.screenRevision()
                                        + ":"
                                        + ExtensionManifest.digest(
                                                        key.getBytes(StandardCharsets.UTF_8))
                                                .substring(0, 24));
                        node.addProperty("class", "extension_region");
                        added++;
                    }
                    node.addProperty("capabilitySource", descriptor.id());
                    node.addProperty(
                            "coverage",
                            nativeNode == null
                                    ? "external_visible_projection"
                                    : "native_with_extension_semantics");
                    node.addProperty("acquisition", "installed_extension");
                    JsonObject semantic =
                            node.has("semanticState") && node.get("semanticState").isJsonObject()
                                    ? node.getAsJsonObject("semanticState").deepCopy()
                                    : new JsonObject();
                    semantic.addProperty("extensionTargetRevision", item.targetRevision());
                    semantic.addProperty("extensionTargetKey", item.key());
                    node.add("semanticState", semantic);
                    if (nativeNode == null) pending.add(() -> children.add(node));
                    else
                        pending.add(
                                () -> {
                                    nativeNode.keySet().clear();
                                    node.entrySet()
                                            .forEach(
                                                    entry ->
                                                            nativeNode.add(
                                                                    entry.getKey(),
                                                                    entry.getValue()));
                                });
                }
                if (total + added > MAX_NODES)
                    throw new ExtensionFailure(
                            "EXTENSION_UI_NODE_BUDGET", "Combined tree exceeds native node budget");
                pending.forEach(Runnable::run);
                enriched.addAll(owned);
                total += added;
                bytes += payloadBytes;
            } catch (Throwable failure) {
                issue(
                        issues,
                        descriptor == null ? "unknown_adapter" : descriptor.id(),
                        failure instanceof ExtensionFailure known
                                ? known.code()
                                : "EXTENSION_UI_CAPTURE_FAILED");
            } finally {
                active.set(false);
            }
        }
        root.addProperty("treeNodeCount", total);
        if (!issues.isEmpty()) {
            JsonArray limits =
                    root.has("treeLimitations")
                            ? root.getAsJsonArray("treeLimitations")
                            : new JsonArray();
            issues.forEach(limits::add);
            root.add("treeLimitations", limits);
        }
    }

    private static void issue(JsonArray issues, String id, String reason) {
        issues.add(id + ":" + reason);
    }

    private static int integer(JsonObject value, String key) {
        return value.has(key) ? value.get(key).getAsInt() : 0;
    }

    private static int count(JsonArray nodes) {
        int count = 0;
        for (JsonElement value : nodes)
            if (value.isJsonObject()) {
                count++;
                if (value.getAsJsonObject().has("children"))
                    count += count(value.getAsJsonObject().getAsJsonArray("children"));
            }
        return count;
    }

    private static JsonObject find(JsonArray nodes, long identity) {
        for (JsonElement value : nodes)
            if (value.isJsonObject()) {
                JsonObject node = value.getAsJsonObject();
                if (node.has("elementIdentity")
                        && node.get("elementIdentity").getAsLong() == identity) return node;
                if (node.has("children")) {
                    var found = find(node.getAsJsonArray("children"), identity);
                    if (found != null) return found;
                }
            }
        return null;
    }

    private static boolean bounds(
            JsonObject node, UiAdapter.Space space, UiAdapter.Frame frame, UiAdapter.View view) {
        for (String key : GEOMETRY)
            if (!node.has(key)
                    || !node.get(key).isJsonPrimitive()
                    || !node.getAsJsonPrimitive(key).isNumber()
                    || !Double.isFinite(node.get(key).getAsDouble()))
                throw new ExtensionFailure(
                        "EXTENSION_UI_BOUNDS_INVALID", "Finite region bounds required");
        double scaleX =
                space == UiAdapter.Space.GUI
                        ? 1.0
                        : (double) frame.guiWidth()
                                / (space == UiAdapter.Space.WINDOW
                                        ? frame.windowWidth()
                                        : frame.framebufferWidth());
        double scaleY =
                space == UiAdapter.Space.GUI
                        ? 1.0
                        : (double) frame.guiHeight()
                                / (space == UiAdapter.Space.WINDOW
                                        ? frame.windowHeight()
                                        : frame.framebufferHeight());
        double x = node.get("x").getAsDouble() * scaleX,
                y = node.get("y").getAsDouble() * scaleY,
                w = node.get("width").getAsDouble() * scaleX,
                h = node.get("height").getAsDouble() * scaleY;
        if (w <= 0 || h <= 0)
            throw new ExtensionFailure(
                    "EXTENSION_UI_BOUNDS_INVALID", "Positive region size required");
        double left = Math.max(0, x),
                top = Math.max(0, y),
                right = Math.min(frame.guiWidth(), x + w),
                bottom = Math.min(frame.guiHeight(), y + h);
        boolean visible =
                right > left
                        && bottom > top
                        && node.has("visible")
                        && node.get("visible").isJsonPrimitive()
                        && node.getAsJsonPrimitive("visible").isBoolean()
                        && node.get("visible").getAsBoolean();
        if (view == UiAdapter.View.USER && !visible) return false;
        node.addProperty("x", left);
        node.addProperty("y", top);
        node.addProperty("width", Math.max(0, right - left));
        node.addProperty("height", Math.max(0, bottom - top));
        node.addProperty("boundsAvailable", visible);
        node.addProperty("visible", visible);
        if (!visible) node.add("actions", new JsonArray());
        return true;
    }
}
