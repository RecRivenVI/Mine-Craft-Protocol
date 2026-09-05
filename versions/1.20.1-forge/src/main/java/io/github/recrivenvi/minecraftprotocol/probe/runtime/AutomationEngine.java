package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Function;

final class AutomationEngine implements AutoCloseable {
    private static final int MAX_PIPELINE_STEPS = 256;
    private static final long MAX_PIPELINE_MILLIS = 300_000L;
    private static final long MAX_STEP_DELAY_MILLIS = 60_000L;

    private final ProbeService service;
    private final ScheduledExecutorService scheduler;
    private final Set<PipelineExecution> activeExecutions = ConcurrentHashMap.newKeySet();
    private volatile ConditionEngine conditionEngine;
    private final io.github.recrivenvi.minecraftprotocol.safety.InputSequenceQueue sequences = new io.github.recrivenvi.minecraftprotocol.safety.InputSequenceQueue();
    private volatile boolean closed;

    AutomationEngine(ProbeService service) {
        this.service = service;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-automation");
            thread.setDaemon(true);
            return thread;
        });
    }

    void addQueueState(JsonObject json) {
        json.addProperty("inputQueueDepth", this.sequences.pendingCount());
        json.addProperty("inputQueueCapacity", io.github.recrivenvi.minecraftprotocol.safety.InputSequenceQueue.CAPACITY);
        json.addProperty("inputSequenceActive", this.sequences.busy());
    }

    void setConditionEngine(ConditionEngine conditionEngine) {
        this.conditionEngine = conditionEngine;
    }

    CompletableFuture<JsonObject> resolve(JsonObject selector) {
        return this.service.uiTree().thenApply(tree -> resolveTree(tree, selector));
    }

    CompletableFuture<JsonObject> visionContext() {
        return this.service.uiTree().thenApply(tree -> {
            JsonObject json = new JsonObject();
            json.addProperty("type", "ui.vision_context");
            copy(tree, json, "target", "clientTick", "screenClass", "screenRevision", "menuRevision", "width", "height");
            int semanticNodes = tree.has("children") ? tree.getAsJsonArray("children").size() : 0;
            json.addProperty("treeCoverage", semanticNodes > 0 ? "semantic_native" : "vision_only");
            json.addProperty("semanticNodeCount", semanticNodes);
            json.addProperty("coordinateSpace", "gui_scaled");
            json.addProperty("captureEndpoint", "/v0/capture");
            json.addProperty("coordinateActionEndpoint", "/v0/ui/action");
            json.addProperty("visionFallbackAvailable", true);
            return json;
        });
    }

    CompletableFuture<JsonObject> uiAction(JsonObject request, Runnable leaseCheck) {
        return this.uiAction(request, leaseCheck, Supplier::get);
    }

    CompletableFuture<JsonObject> uiAction(JsonObject request, Runnable leaseCheck,
            Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission) {
        JsonObject step = request.deepCopy();
        step.addProperty("type", "ui.action");
        JsonArray steps = new JsonArray();
        steps.add(step);
        JsonObject pipeline = new JsonObject();
        pipeline.add("steps", steps);
        pipeline.addProperty("cleanupOnComplete", false);
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.executePipeline(pipeline, leaseCheck, admission),
                result -> CompletableFuture.completedFuture(result.getAsJsonArray("steps")
                        .get(0).getAsJsonObject().getAsJsonObject("result")));
    }

    void cancelControlWork() {
        this.sequences.cancelAll();
        for (PipelineExecution execution : this.activeExecutions) execution.result.cancel(false);
    }

    CompletableFuture<JsonObject> assertThat(JsonObject condition) {
        return requireConditionEngine().assertThat(condition);
    }

    CompletableFuture<JsonObject> waitUntil(JsonObject condition, long timeoutMillis) {
        return requireConditionEngine().waitUntil(condition, timeoutMillis);
    }

    CompletableFuture<JsonObject> executePipeline(JsonObject request, Runnable leaseCheck) {
        return this.executePipeline(request, leaseCheck, Supplier::get);
    }

    CompletableFuture<JsonObject> executePipeline(JsonObject request, Runnable leaseCheck,
            Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission) {
        return this.executeInput(request, leaseCheck, admission, false, null);
    }

    CompletableFuture<JsonObject> rawInput(JsonObject step, Runnable leaseCheck,
            Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission) {
        JsonArray steps = new JsonArray(); steps.add(step.deepCopy());
        JsonObject request = new JsonObject(); request.add("steps", steps);
        request.addProperty("cleanupOnComplete", false);
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.executeInput(request, leaseCheck, admission, true, null),
                value -> CompletableFuture.completedFuture(value.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("result")));
    }

    CompletableFuture<JsonObject> playerCommand(String command, Runnable leaseCheck,
            Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission) {
        JsonObject step = new JsonObject(); step.addProperty("type", "internal.player.command");
        JsonArray steps = new JsonArray(); steps.add(step);
        JsonObject request = new JsonObject(); request.add("steps", steps); request.addProperty("cleanupOnComplete", false);
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.executeInput(request, leaseCheck, admission, false, command),
                value -> CompletableFuture.completedFuture(value.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("result")));
    }

    private CompletableFuture<JsonObject> executeInput(JsonObject request, Runnable leaseCheck,
            Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission,
            boolean raw, String command) {
        if (this.closed) return failed("INPUT_QUEUE_CLOSED", 409, "Input is closed");
        if (!request.has("steps") || !request.get("steps").isJsonArray()) {
            return failed("INVALID_PIPELINE", 400, "Pipeline requires a steps array");
        }
        JsonArray steps = request.getAsJsonArray("steps");
        if (steps.isEmpty() || steps.size() > MAX_PIPELINE_STEPS) {
            return failed("INVALID_PIPELINE", 400, "Pipeline step count must be between 1 and 256");
        }
        for (JsonElement element : steps) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("type")) {
                return failed("INVALID_PIPELINE", 400, "Every pipeline step requires a type");
            }
        }
        long timeout = bounded(longValue(request, "timeoutMs", 60_000L), 1L, MAX_PIPELINE_MILLIS);
        boolean cleanupOnComplete = bool(request, "cleanupOnComplete", true);
        PipelineExecution execution = new PipelineExecution(
                steps.deepCopy(), leaseCheck, timeout, cleanupOnComplete, admission, raw, command);
        this.activeExecutions.add(execution);
        CompletableFuture<JsonObject> result = execution.result;
        try {
            execution.armDeadline();
            this.sequences.submit(execution::start, () -> execution.result.cancel(false), execution.drained);
        } catch (java.util.concurrent.RejectedExecutionException error) {
            execution.fail(new ProtocolState.ProtocolException(error.getMessage(), error.getMessage().equals("INPUT_QUEUE_FULL") ? 429 : 409, error.getMessage()));
        }
        result.whenComplete((ignored, error) -> this.activeExecutions.remove(execution));
        return result;
    }

    @Override
    public void close() {
        this.closed = true;
        this.cancelControlWork();
        this.sequences.close();
        this.scheduler.shutdownNow();
    }

    private static void requireActionable(JsonObject node, String action) {
        if (action.equals("hover") && hasBounds(node) && (!node.has("visible") || node.get("visible").getAsBoolean())) return;
        if ((node.has("active") && !node.get("active").getAsBoolean())
                || (node.has("visible") && !node.get("visible").getAsBoolean())) {
            throw new ProtocolState.ProtocolException("UI_NODE_NOT_ACTIONABLE", 409, "UI node is disabled or hidden");
        }
        String required = action.equals("double_click") ? "click" : action;
        if (!node.has("actions") || !node.get("actions").isJsonArray()) {
            throw new ProtocolState.ProtocolException("UI_NODE_NOT_ACTIONABLE", 409, "UI node declares no actions");
        }
        for (JsonElement element : node.getAsJsonArray("actions")) {
            if (element.getAsString().equals(required)) return;
        }
        throw new ProtocolState.ProtocolException(
                "UI_NODE_NOT_ACTIONABLE", 409, "UI node does not support action: " + action);
    }

    private CompletableFuture<JsonObject> resolve(JsonObject selector, PipelineExecution execution) {
        return execution.effect(this.service::uiTree).thenApply(tree -> resolveTree(tree, selector));
    }

    private static final String[] FRAME_FIELDS = {
            "screenClass", "screenIdentity", "overlayIdentity", "screenRevision", "menuRevision",
            "width", "height", "windowWidth", "windowHeight", "guiScale"
    };

    private static JsonObject guard(JsonObject state, JsonObject selector, String action) {
        JsonObject result = new JsonObject(), frame = new JsonObject();
        copy(state, frame, FRAME_FIELDS);
        result.add("frame", frame);
        if (selector != null) {
            JsonObject resolution = resolveTree(state, selector);
            requireActionable(resolution.getAsJsonObject("node"), action);
            result.add("selector", selector.deepCopy());
            result.add("node", resolution.getAsJsonObject("node").deepCopy());
            result.addProperty("action", action);
        }
        return result;
    }

    static void validatePointerGuard(JsonObject guard, JsonObject tree) {
        if (guard == null) return;
        JsonObject before = guard.getAsJsonObject("frame");
        for (String field : FRAME_FIELDS) {
            if (before.has(field) && (!tree.has(field) || !before.get(field).equals(tree.get(field))))
                throw new ProtocolState.ProtocolException("UI_TARGET_INVALIDATED", 409, "Pointer context changed: " + field);
        }
        if (tree.has("overlayIdentity") && tree.get("overlayIdentity").getAsLong() != 0)
            throw new ProtocolState.ProtocolException("UI_TARGET_INVALIDATED", 409, "An Overlay now blocks the target");
        if (guard.has("selector")) {
            JsonObject current;
            try { current = resolveTree(tree, guard.getAsJsonObject("selector")).getAsJsonObject("node"); }
            catch (ProtocolState.ProtocolException error) { throw new ProtocolState.ProtocolException("UI_TARGET_INVALIDATED", 409, "Pointer target no longer resolves"); }
            JsonObject original = guard.getAsJsonObject("node");
            for (String field : new String[] {"nodeId", "elementIdentity", "class", "x", "y", "width", "height"}) {
                if (original.has(field) && (!current.has(field) || !original.get(field).equals(current.get(field))))
                    throw new ProtocolState.ProtocolException("UI_TARGET_INVALIDATED", 409, "Pointer target changed: " + field);
            }
            requireActionable(current, string(guard, "action", "click"));
        }
        if (guard.has("destination")) validatePointerGuard(guard.getAsJsonObject("destination"), tree);
    }

    private CompletableFuture<JsonObject> smoothMove(double x, double y, PipelineExecution execution, JsonObject checked) {
        return execution.effect(service::pointerState).thenCompose(state -> {
            boolean gui = bool(state, "guiAbsolute", !string(state, "screenClass", "").isEmpty());
            JsonObject expected = checked == null ? guard(state, null, "click") : checked;
            if (!gui) return execution.effect(() -> service.mouseMoveGuarded(x, y, expected));
            double startX = doubleValue(state, "x", x), startY = doubleValue(state, "y", y);
            CompletableFuture<JsonObject> chain = CompletableFuture.completedFuture(simpleResult("pointer.move"));
            for (int i = 1; i <= 12; i++) {
                double px = io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.interpolate(startX, x, i / 12.0);
                double py = io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.interpolate(startY, y, i / 12.0);
                chain = chain.thenCompose(ignored -> execution.delay(15))
                        .thenCompose(ignored -> execution.effect(() -> service.mouseMoveGuarded(px, py, expected)));
            }
            return chain;
        });
    }

    private CompletableFuture<JsonObject> uiAction(JsonObject request, PipelineExecution execution) {
        String action = string(request, "action", "click");
        int button = integer(request, "button", 0), modifiers = integer(request, "modifiers", 0);
        long hold = bounded(longValue(request, "holdMs", 40), 0, 5000);
        return execution.effect(service::pointerState).thenCompose(state -> {
            JsonObject selector = request.has("selector") ? request.getAsJsonObject("selector") : null;
            JsonObject expected = guard(state, selector, action);
            JsonObject node = expected.has("node") ? expected.getAsJsonObject("node") : null;
            double x, y;
            if (request.has("coordinates")) {
                JsonObject coordinates = request.getAsJsonObject("coordinates");
                x = requiredDouble(coordinates, "x"); y = requiredDouble(coordinates, "y");
            } else {
                if (node == null) return failed("INVALID_UI_ACTION", 400, "ui.action requires selector or coordinates");
                x = node.get("x").getAsDouble() + node.get("width").getAsDouble() / 2;
                y = node.get("y").getAsDouble() + node.get("height").getAsDouble() / 2;
            }
            String source = selector == null ? string(request, "source", "explicit_coordinate") : "interaction_tree";
            return smoothMove(x, y, execution, expected).thenCompose(ignored -> switch (action) {
                case "hover" -> CompletableFuture.completedFuture(simpleResult("pointer.hover"));
                case "click" -> buttonClick(button, modifiers, hold, execution, expected);
                case "double_click" -> buttonClick(button, modifiers, hold, execution, expected)
                        .thenCompose(done -> execution.delay(50))
                        .thenCompose(done -> buttonClick(button, modifiers, hold, execution, expected));
                case "mouse_down" -> execution.effect(() -> service.mouseButtonGuarded(button, 1, modifiers, expected));
                case "mouse_up" -> execution.effect(() -> service.mouseButton(button, 0, modifiers));
                case "scroll" -> execution.effect(() -> service.mouseScrollGuarded(doubleValue(request, "xOffset", 0), doubleValue(request, "yOffset", 0), expected));
                default -> failed("UNSUPPORTED_UI_ACTION", 400, "Unsupported UI action: " + action);
            }).thenApply(result -> decorateAction(result, node, x, y, source, action));
        });
    }

    private CompletableFuture<JsonObject> buttonClick(int button, int modifiers, long hold, PipelineExecution execution, JsonObject guard) {
        // Press is checked atomically on the owner thread. Release cleans the pressed
        // owner, without calling mouseReleased on a replacement Screen.
        return execution.effect(() -> service.mouseButtonGuarded(button, 1, modifiers, guard))
                .thenCompose(ignored -> execution.delay(hold))
                .thenCompose(ignored -> execution.effect(() -> service.mouseButton(button, 0, modifiers)));
    }

    private CompletableFuture<JsonObject> click(double x, double y, int button, int modifiers, long hold, PipelineExecution execution) {
        return execution.effect(service::pointerState).thenCompose(state -> {
            JsonObject expected = guard(state, null, "click");
            return smoothMove(x, y, execution, expected)
                    .thenCompose(ignored -> buttonClick(button, modifiers, hold, execution, expected));
        });
    }

    private CompletableFuture<JsonObject> assertThat(JsonObject condition, PipelineExecution execution) {
        return execution.track(requireConditionEngine().assertThat(condition));
    }

    private CompletableFuture<JsonObject> waitUntil(
            JsonObject condition, long timeoutMillis, PipelineExecution execution) {
        return execution.track(requireConditionEngine().waitUntil(condition, timeoutMillis));
    }

    private CompletableFuture<JsonObject> runStep(JsonObject step, PipelineExecution execution) {
        String type = step.get("type").getAsString();
        return switch (type) {
            case "delay" -> execution.delay(bounded(longValue(step, "durationMs", 0L), 0L, MAX_STEP_DELAY_MILLIS))
                    .thenApply(ignored -> simpleResult("delay"));
            case "mouse.move" -> this.smoothMove(requiredDouble(step, "x"), requiredDouble(step, "y"), execution, null);
            case "mouse.delta" -> execution.effect(() -> this.service.mouseDelta(requiredDouble(step, "dx"), requiredDouble(step, "dy")));
            case "internal.player.command" -> execution.command == null ? failed("UNSUPPORTED_PIPELINE_STEP", 400, "Internal step is not public")
                    : execution.effect(() -> service.playerCommand(execution.command));
            case "mouse.button" -> execution.effect(() -> this.service.mouseButton(
                    requiredInt(step, "button"), requiredInt(step, "action"), integer(step, "modifiers", 0)));
            case "mouse.click" -> this.click(
                    requiredDouble(step, "x"), requiredDouble(step, "y"),
                    integer(step, "button", 0), integer(step, "modifiers", 0),
                    bounded(longValue(step, "holdMs", 40L), 0L, 5_000L), execution);
            case "mouse.scroll" -> execution.effect(() -> this.service.mouseScroll(
                    doubleValue(step, "xOffset", 0.0), doubleValue(step, "yOffset", 0.0)));
            case "mouse.drag" -> this.drag(step, execution);
            case "key" -> execution.effect(() -> this.service.key(
                    requiredInt(step, "key"), integer(step, "scanCode", 0),
                    requiredInt(step, "action"), integer(step, "modifiers", 0)));
            case "key.tap" -> this.keyTap(step, execution);
            case "key.chord" -> this.keyChord(step, execution);
            case "ui.action" -> this.uiAction(step, execution);
            case "ui.drag" -> this.uiDrag(step, execution);
            case "wait.until" -> this.waitUntil(
                    requiredObject(step, "condition"), longValue(step, "timeoutMs", 5_000L), execution);
            case "assert.that" -> this.assertThat(requiredObject(step, "condition"), execution);
            default -> failed("UNSUPPORTED_PIPELINE_STEP", 400, "Unsupported pipeline step: " + type);
        };
    }

    private CompletableFuture<JsonObject> keyTap(JsonObject step, PipelineExecution execution) {
        int key = requiredInt(step, "key");
        int scanCode = integer(step, "scanCode", 0);
        int modifiers = integer(step, "modifiers", 0);
        long hold = bounded(longValue(step, "holdMs", 40L), 0L, 5_000L);
        return execution.effect(() -> this.service.key(key, scanCode, 1, modifiers))
                .thenCompose(ignored -> execution.delay(hold))
                .thenCompose(ignored -> execution.effect(() -> this.service.key(key, scanCode, 0, modifiers)));
    }

    private CompletableFuture<JsonObject> keyChord(JsonObject step, PipelineExecution execution) {
        if (!step.has("keys") || !step.get("keys").isJsonArray()) {
            return failed("INVALID_KEY_CHORD", 400, "key.chord requires keys");
        }
        List<JsonObject> keys = new ArrayList<>();
        for (JsonElement element : step.getAsJsonArray("keys")) keys.add(element.getAsJsonObject());
        if (keys.isEmpty() || keys.size() > 16) {
            return failed("INVALID_KEY_CHORD", 400, "key.chord supports 1 to 16 keys");
        }
        CompletableFuture<JsonObject> chain = CompletableFuture.completedFuture(simpleResult("key.chord"));
        for (JsonObject key : keys) {
            chain = chain.thenCompose(ignored -> execution.effect(() -> this.service.key(
                    requiredInt(key, "key"), integer(key, "scanCode", 0), 1, integer(key, "modifiers", 0))));
        }
        chain = chain.thenCompose(ignored -> execution.delay(bounded(longValue(step, "holdMs", 40L), 0L, 60_000L)))
                .thenApply(ignored -> simpleResult("key.chord"));
        Collections.reverse(keys);
        for (JsonObject key : keys) {
            chain = chain.thenCompose(ignored -> execution.effect(() -> this.service.key(
                    requiredInt(key, "key"), integer(key, "scanCode", 0), 0, integer(key, "modifiers", 0))));
        }
        return chain;
    }

    private CompletableFuture<JsonObject> drag(JsonObject step, PipelineExecution execution) {
        return execution.effect(service::pointerState).thenCompose(state -> drag(step, execution, guard(state, null, "click")));
    }

    private CompletableFuture<JsonObject> drag(JsonObject step, PipelineExecution execution, JsonObject expected) {
        double fromX = requiredDouble(step, "fromX"), fromY = requiredDouble(step, "fromY");
        double toX = requiredDouble(step, "toX"), toY = requiredDouble(step, "toY");
        int button = integer(step, "button", 0), modifiers = integer(step, "modifiers", 0);
        int segments = (int) bounded(longValue(step, "segments", 12), 1, 120);
        long duration = bounded(longValue(step, "durationMs", 250), 0, 60000);
        CompletableFuture<JsonObject> chain = smoothMove(fromX, fromY, execution, expected)
                .thenCompose(ignored -> execution.effect(() -> service.mouseButtonGuarded(button, 1, modifiers, expected)));
        JsonObject dragging = expected.deepCopy();
        dragging.getAsJsonObject("frame").remove("menuRevision"); // own pickup legitimately changes carried/slot state
        if (dragging.has("destination")) dragging.getAsJsonObject("destination").getAsJsonObject("frame").remove("menuRevision");
        for (int index = 1; index <= segments; index++) {
            double x = io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.interpolate(fromX, toX, (double) index / segments);
            double y = io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.interpolate(fromY, toY, (double) index / segments);
            chain = chain.thenCompose(ignored -> execution.delay(duration / segments))
                    .thenCompose(ignored -> execution.effect(() -> service.mouseMoveGuarded(x, y, dragging)));
        }
        return chain.thenCompose(ignored -> execution.effect(() -> service.mouseButton(button, 0, modifiers)));
    }

    private CompletableFuture<JsonObject> uiDrag(JsonObject step, PipelineExecution execution) {
        return execution.effect(service::pointerState).thenCompose(state -> {
            JsonObject fromSelector = requiredObject(step, "fromSelector"), toSelector = requiredObject(step, "toSelector");
            JsonObject expected = guard(state, fromSelector, "click");
            JsonObject destination = guard(state, toSelector, "click");
            expected.add("destination", destination);
            JsonObject from = expected.getAsJsonObject("node"), to = destination.getAsJsonObject("node");
            JsonObject request = step.deepCopy();
            request.addProperty("fromX", from.get("x").getAsDouble() + from.get("width").getAsDouble() / 2);
            request.addProperty("fromY", from.get("y").getAsDouble() + from.get("height").getAsDouble() / 2);
            request.addProperty("toX", to.get("x").getAsDouble() + to.get("width").getAsDouble() / 2);
            request.addProperty("toY", to.get("y").getAsDouble() + to.get("height").getAsDouble() / 2);
            return drag(request, execution, expected);
        });
    }

    private CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.scheduler.schedule(() -> future.complete(null), Math.max(0L, millis), TimeUnit.MILLISECONDS);
        return future;
    }

    static JsonObject resolveTree(JsonObject tree, JsonObject selector) {
        List<JsonObject> matches = ConditionEngine.findMatches(tree, selector);
        if (matches.isEmpty()) {
            throw new ProtocolState.ProtocolException("UI_NODE_NOT_FOUND", 404, "UI selector matched no nodes");
        }
        int selectedIndex = selector.has("nth") ? selector.get("nth").getAsInt() : -1;
        if (selectedIndex < 0 && matches.size() > 1) {
            throw new ProtocolState.ProtocolException(
                    "UI_SELECTOR_AMBIGUOUS", 409, "UI selector matched " + matches.size() + " nodes");
        }
        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= matches.size()) {
            throw new ProtocolState.ProtocolException("UI_NODE_NOT_FOUND", 404, "UI selector nth is out of range");
        }
        JsonObject node = matches.get(selectedIndex).deepCopy();
        if (!hasBounds(node)) {
            throw new ProtocolState.ProtocolException("UI_NODE_NOT_ACTIONABLE", 409, "UI node has no bounds");
        }
        JsonObject point = new JsonObject();
        point.addProperty("x", node.get("x").getAsDouble() + node.get("width").getAsDouble() / 2.0);
        point.addProperty("y", node.get("y").getAsDouble() + node.get("height").getAsDouble() / 2.0);
        point.addProperty("source", "bounds_center");

        JsonObject json = new JsonObject();
        json.addProperty("type", "ui.resolve");
        copy(tree, json, "target", "clientTick", "screenClass", "screenRevision", "menuRevision");
        json.addProperty("matchCount", matches.size());
        json.addProperty("selectedIndex", selectedIndex);
        json.add("node", node);
        json.add("interactionPoint", point);
        return json;
    }

    private ConditionEngine requireConditionEngine() {
        ConditionEngine engine = this.conditionEngine;
        if (engine == null) {
            throw new ProtocolState.ProtocolException(
                    "CONDITION_ENGINE_UNAVAILABLE", 503, "Typed condition engine is unavailable");
        }
        return engine;
    }

    private static JsonObject decorateAction(
            JsonObject input,
            JsonObject node,
            double x,
            double y,
            String source,
            String action) {
        JsonObject result = input.deepCopy();
        result.addProperty("type", "ui.action_result");
        result.addProperty("action", action);
        result.addProperty("targetingSource", source);
        result.addProperty("x", x);
        result.addProperty("y", y);
        if (node != null) result.add("resolvedNode", node.deepCopy());
        return result;
    }

    private static JsonObject simpleResult(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    private static boolean hasBounds(JsonObject node) {
        return node.has("x") && node.has("y") && node.has("width") && node.has("height")
                && node.get("width").getAsDouble() > 0 && node.get("height").getAsDouble() > 0;
    }

    private static JsonObject requiredObject(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonObject()) {
            throw new ProtocolState.ProtocolException("INVALID_ARGUMENT", 400, name + " must be an object");
        }
        return object.getAsJsonObject(name);
    }

    private static int requiredInt(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new ProtocolState.ProtocolException("INVALID_ARGUMENT", 400, "Missing " + name);
        }
        return object.get(name).getAsInt();
    }

    private static double requiredDouble(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new ProtocolState.ProtocolException("INVALID_ARGUMENT", 400, "Missing " + name);
        }
        return object.get(name).getAsDouble();
    }

    private static int integer(JsonObject object, String name, int fallback) {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static long longValue(JsonObject object, String name, long fallback) {
        return object.has(name) ? object.get(name).getAsLong() : fallback;
    }

    private static double doubleValue(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }

    private static long bounded(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static void copy(JsonObject source, JsonObject target, String... fields) {
        for (String field : fields) {
            if (source.has(field)) target.add(field, source.get(field).deepCopy());
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException
                || current.getClass().getName().equals("java.util.concurrent.ExecutionException"))
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static <T> CompletableFuture<T> failed(String code, int status, String message) {
        return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(code, status, message));
    }

    private final class PipelineExecution {
        private final JsonArray steps;
        private final Runnable leaseCheck;
        private final Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission;
        private final long timeoutMillis;
        private final boolean cleanupOnComplete;
        private final long startedAtMillis = System.currentTimeMillis();
        private final CompletableFuture<JsonObject> result;
        private final JsonArray stepResults = new JsonArray();
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final Set<CompletableFuture<?>> children = ConcurrentHashMap.newKeySet();
        private final Set<ScheduledFuture<?>> scheduled = ConcurrentHashMap.newKeySet();
        private volatile CompletableFuture<?> currentStep;
        private volatile String cancellationReason;
        private int index;
        private final boolean raw;
        private final String command;
        private final String gestureId = java.util.UUID.randomUUID().toString();
        private final AtomicBoolean started = new AtomicBoolean();
        private final CompletableFuture<Void> drained = new CompletableFuture<>();
        private final CompletableFuture<JsonObject> cleanupResult = new CompletableFuture<>();

        private PipelineExecution(
                JsonArray steps, Runnable leaseCheck, long timeoutMillis, boolean cleanupOnComplete,
                Function<Supplier<CompletableFuture<JsonObject>>, CompletableFuture<JsonObject>> admission, boolean raw, String command) {
            this.raw = raw; this.command = command;
            this.steps = steps;
            this.admission = admission;
            this.leaseCheck = leaseCheck;
            this.timeoutMillis = timeoutMillis;
            this.cleanupOnComplete = cleanupOnComplete;
            this.result = new PipelineFuture(this);
        }

        private void armDeadline() {
            ScheduledFuture<?> deadline = scheduler.schedule(() -> this.fail(new ProtocolState.ProtocolException(
                    "PIPELINE_TIMEOUT", 408, "Input sequence deadline expired")),
                    this.timeoutMillis, TimeUnit.MILLISECONDS);
            this.scheduled.add(deadline);
            this.result.whenComplete((value, error) -> deadline.cancel(false));
        }

        private void start() {
            if (this.result.isDone() || this.cancellationRequested.get()) { this.drained.complete(null); return; }
            try { this.leaseCheck.run(); this.checkActive(); }
            catch (Throwable stale) { this.fail(stale); return; }
            this.started.set(true);
            this.drained.whenComplete((ignored, error) -> service.gestureEvent(this.gestureId, "drained", null));
            try {
                service.gestureEvent(this.gestureId, "started", null);
                if (!this.raw) {
                    this.effect(service::inputState).whenComplete((state, error) -> {
                        if (error != null) this.fail(unwrap(error));
                        else if (state != null && integer(state, "pressedButtonCount", 0) > 0) {
                            this.finishing.set(true); this.drained.complete(null);
                            this.result.completeExceptionally(new ProtocolState.ProtocolException(
                                    "POINTER_HELD", 409, "Finish the Lease-owned raw button stream before a new gesture"));
                        } else this.enqueueNext(this::next);
                    });
                } else this.enqueueNext(this::next);
            } catch (Throwable error) { this.fail(error); }
        }

        private void enqueueNext(Runnable next) {
            try { scheduler.execute(next); }
            catch (java.util.concurrent.RejectedExecutionException closed) { this.result.cancel(false); }
        }

        private void next() {
            if (this.result.isDone() || this.cancellationRequested.get() || this.finishing.get()) return;
            if (System.currentTimeMillis() - this.startedAtMillis > this.timeoutMillis) {
                this.fail(new ProtocolState.ProtocolException(
                        "PIPELINE_TIMEOUT", 408, "Pipeline exceeded its timeout"));
                return;
            }
            if (this.index >= this.steps.size()) {
                this.complete();
                return;
            }
            try {
                this.leaseCheck.run();
                JsonObject step = this.steps.get(this.index).getAsJsonObject();
                int stepIndex = this.index++;
                CompletableFuture<JsonObject> stepFuture = this.track(runStep(step, this));
                this.currentStep = stepFuture;
                stepFuture.whenComplete((stepResult, error) -> this.enqueueNext(() -> {
                    this.currentStep = null;
                    if (this.result.isDone() || this.cancellationRequested.get() || this.finishing.get()) return;
                    if (error != null) {
                        this.fail(unwrap(error));
                        return;
                    }
                    JsonObject recorded = new JsonObject();
                    recorded.addProperty("index", stepIndex);
                    recorded.addProperty("stepType", step.get("type").getAsString());
                    recorded.addProperty("status", "completed");
                    recorded.add("result", stepResult == null ? new JsonObject() : stepResult.deepCopy());
                    this.stepResults.add(recorded);
                    this.next();
                }));
            } catch (Throwable throwable) {
                this.fail(unwrap(throwable));
            }
        }

        private void complete() {
            if (!this.finishing.compareAndSet(false, true)) return;
            CompletableFuture<JsonObject> cleanup = this.cleanupOnComplete
                    ? this.cleanup("pipeline_completed")
                    : CompletableFuture.completedFuture(simpleResult("input.preserved"));
            cleanup.whenComplete((cleanupResult, error) -> {
                if (this.result.isDone()) return;
                if (error != null) {
                    this.result.completeExceptionally(unwrap(error));
                    this.drained.completeExceptionally(error);
                    return;
                }
                JsonObject json = new JsonObject();
                json.addProperty("type", "pipeline.result");
                json.addProperty("gestureId", this.gestureId);
                json.addProperty("status", "completed");
                json.addProperty("stepCount", this.steps.size());
                json.addProperty("durationMillis", System.currentTimeMillis() - this.startedAtMillis);
                json.addProperty("cleanupOnComplete", this.cleanupOnComplete);
                json.add("steps", this.stepResults.deepCopy());
                json.add("cleanup", cleanupResult == null ? new JsonObject() : cleanupResult.deepCopy());
                this.result.complete(json);
                this.drained.complete(null);
            });
        }

        private void fail(Throwable throwable) {
            if (throwable instanceof CancellationException) {
                this.result.cancel(false);
                return;
            }
            if (!this.finishing.compareAndSet(false, true)) return;
            this.stopPendingWork();
            this.cleanup("pipeline_failed").whenComplete((ignored, cleanupError) -> {
                this.result.completeExceptionally(throwable);
                if (cleanupError != null) this.drained.completeExceptionally(cleanupError); else this.drained.complete(null);
            });
        }

        private boolean requestCancellation(String reason) {
            if (!this.cancellationRequested.compareAndSet(false, true)) return false;
            this.cancellationReason = reason;
            this.finishing.set(true);
            this.stopPendingWork();
            this.cleanup("pipeline_cancelled:" + reason).whenComplete((ignored, error) -> {
                if (error != null) this.drained.completeExceptionally(error); else this.drained.complete(null);
            });
            return true;
        }

        private void stopPendingWork() {
            CompletableFuture<?> active = this.currentStep;
            if (active != null) active.cancel(true);
            for (ScheduledFuture<?> future : this.scheduled) future.cancel(false);
            this.scheduled.clear();
            for (CompletableFuture<?> future : this.children) future.cancel(true);
            this.children.clear();
        }

        private void checkActive() {
            if (System.currentTimeMillis() - this.startedAtMillis > this.timeoutMillis) throw new ProtocolState.ProtocolException("PIPELINE_TIMEOUT", 408, "Input sequence deadline expired");
            if (this.cancellationRequested.get() || this.result.isCancelled()) {
                throw new CancellationException(this.cancellationReason == null
                        ? "Pipeline cancelled" : "Pipeline cancelled: " + this.cancellationReason);
            }
            if (this.finishing.get()) throw new CancellationException("Pipeline is terminating");
        }

        private CompletableFuture<JsonObject> effect(Supplier<CompletableFuture<JsonObject>> supplier) {
            this.checkActive();
            this.leaseCheck.run();
            CompletableFuture<JsonObject> future;
            try {
                future = this.admission.apply(() -> { this.checkActive(); return supplier.get(); });
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
            this.track(future);
            if (this.cancellationRequested.get()) future.cancel(true);
            return future;
        }

        private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
            this.children.add(future);
            future.whenComplete((ignored, error) -> this.children.remove(future));
            if (this.cancellationRequested.get()) future.cancel(true);
            return future;
        }

        private CompletableFuture<Void> delay(long millis) {
            this.checkActive();
            CompletableFuture<Void> future = this.track(new CompletableFuture<>());
            AtomicReference<ScheduledFuture<?>> reference = new AtomicReference<>();
            ScheduledFuture<?> handle = scheduler.schedule(() -> {
                ScheduledFuture<?> scheduledFuture = reference.get();
                if (scheduledFuture != null) this.scheduled.remove(scheduledFuture);
                if (this.cancellationRequested.get()) future.cancel(false);
                else future.complete(null);
            }, Math.max(0L, millis), TimeUnit.MILLISECONDS);
            reference.set(handle);
            this.scheduled.add(handle);
            if (this.cancellationRequested.get() && handle.cancel(false)) {
                this.scheduled.remove(handle);
                future.cancel(false);
            }
            return future;
        }

        private CompletableFuture<JsonObject> cleanup(String reason) {
            if (!this.cleanupStarted.compareAndSet(false, true)) {
                return this.cleanupResult;
            }
            if (!this.started.get()) { this.cleanupResult.complete(simpleResult("input.queued_cancelled")); return this.cleanupResult; }
            service.releaseAllInput(reason).whenComplete((value, error) -> {
                if (error != null) this.cleanupResult.completeExceptionally(error); else this.cleanupResult.complete(value);
            });
            return this.cleanupResult;
        }
    }

    private static final class PipelineFuture extends CompletableFuture<JsonObject> {
        private final PipelineExecution execution;

        private PipelineFuture(PipelineExecution execution) {
            this.execution = execution;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            this.execution.requestCancellation("operation_cancelled");
            return super.cancel(mayInterruptIfRunning);
        }
    }
}
