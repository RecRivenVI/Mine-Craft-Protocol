package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AutomationEngine implements AutoCloseable {
    private static final int MAX_PIPELINE_STEPS = 256;
    private static final long MAX_PIPELINE_MILLIS = 300_000L;
    private static final long MAX_STEP_DELAY_MILLIS = 60_000L;

    private final ProbeService service;
    private final ScheduledExecutorService scheduler;

    AutomationEngine(ProbeService service) {
        this.service = service;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-automation");
            thread.setDaemon(true);
            return thread;
        });
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

    CompletableFuture<JsonObject> uiAction(JsonObject request) {
        String action = string(request, "action", "click");
        int button = integer(request, "button", 0);
        int modifiers = integer(request, "modifiers", 0);
        long holdMillis = bounded(longValue(request, "holdMs", 40L), 0L, 5_000L);

        if (request.has("coordinates")) {
            JsonObject coordinates = request.getAsJsonObject("coordinates");
            double x = requiredDouble(coordinates, "x");
            double y = requiredDouble(coordinates, "y");
            String source = string(request, "source", "explicit_coordinate");
            return this.performUiAction(action, x, y, button, modifiers, holdMillis, request)
                    .thenApply(result -> decorateAction(result, null, x, y, source, action));
        }

        if (!request.has("selector")) {
            return failed("INVALID_UI_ACTION", 400, "ui.action requires selector or coordinates");
        }
        JsonObject selector = request.getAsJsonObject("selector");
        return this.resolve(selector).thenCompose(resolution -> {
            JsonObject node = resolution.getAsJsonObject("node");
            requireActionable(node, action);
            JsonObject point = resolution.getAsJsonObject("interactionPoint");
            double x = point.get("x").getAsDouble();
            double y = point.get("y").getAsDouble();
            Long screenRevision = resolution.has("screenRevision")
                    ? resolution.get("screenRevision").getAsLong() : null;
            Long menuRevision = resolution.has("menuRevision")
                    ? resolution.get("menuRevision").getAsLong() : null;
            return this.service.validatePreconditions(screenRevision, menuRevision)
                    .thenCompose(ignored -> this.performUiAction(
                            action, x, y, button, modifiers, holdMillis, request))
                    .thenApply(result -> decorateAction(
                            result, node, x, y,
                            "interaction_tree", action));
        });
    }

    CompletableFuture<JsonObject> assertThat(JsonObject condition) {
        return this.service.uiTree().thenApply(tree -> {
            JsonObject evaluation = evaluate(tree, condition);
            if (!evaluation.get("passed").getAsBoolean()) {
                throw new ProtocolState.ProtocolException(
                        "ASSERTION_FAILED", 412, evaluation.get("message").getAsString());
            }
            return evaluation;
        });
    }

    CompletableFuture<JsonObject> waitUntil(JsonObject condition, long timeoutMillis) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        long timeout = bounded(timeoutMillis, 1L, 60_000L);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (result.isDone()) return;
                service.uiTree().whenComplete((tree, error) -> {
                    if (result.isDone()) return;
                    if (error != null) {
                        result.completeExceptionally(unwrap(error));
                        return;
                    }
                    JsonObject evaluation = evaluate(tree, condition);
                    if (evaluation.get("passed").getAsBoolean()) {
                        evaluation.addProperty("waited", true);
                        result.complete(evaluation);
                    } else if (System.nanoTime() >= deadline) {
                        result.completeExceptionally(new ProtocolState.ProtocolException(
                                "WAIT_TIMEOUT", 408, evaluation.get("message").getAsString()));
                    } else {
                        scheduler.schedule(this, 25L, TimeUnit.MILLISECONDS);
                    }
                });
            }
        };
        poll.run();
        return result;
    }

    CompletableFuture<JsonObject> executePipeline(JsonObject request, Runnable leaseCheck) {
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
                steps.deepCopy(), leaseCheck, timeout, cleanupOnComplete);
        return execution.start();
    }

    @Override
    public void close() {
        this.scheduler.shutdownNow();
    }

    private CompletableFuture<JsonObject> performUiAction(
            String action,
            double x,
            double y,
            int button,
            int modifiers,
            long holdMillis,
            JsonObject request) {
        return switch (action) {
            case "click" -> this.click(x, y, button, modifiers, holdMillis);
            case "double_click" -> this.click(x, y, button, modifiers, holdMillis)
                    .thenCompose(ignored -> delay(50L))
                    .thenCompose(ignored -> this.click(x, y, button, modifiers, holdMillis));
            case "mouse_down" -> this.service.mouseMove(x, y)
                    .thenCompose(ignored -> this.service.mouseButton(button, 1, modifiers));
            case "mouse_up" -> this.service.mouseMove(x, y)
                    .thenCompose(ignored -> this.service.mouseButton(button, 0, modifiers));
            case "scroll" -> this.service.mouseMove(x, y).thenCompose(ignored -> this.service.mouseScroll(
                    doubleValue(request, "xOffset", 0.0), doubleValue(request, "yOffset", 0.0)));
            default -> failed("UNSUPPORTED_UI_ACTION", 400, "Unsupported UI action: " + action);
        };
    }

    private static void requireActionable(JsonObject node, String action) {
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

    private CompletableFuture<JsonObject> click(
            double x, double y, int button, int modifiers, long holdMillis) {
        return this.service.mouseMove(x, y)
                .thenCompose(ignored -> this.service.mouseButton(button, 1, modifiers))
                .thenCompose(ignored -> delay(holdMillis))
                .thenCompose(ignored -> this.service.mouseButton(button, 0, modifiers));
    }

    private CompletableFuture<JsonObject> runStep(JsonObject step) {
        String type = step.get("type").getAsString();
        return switch (type) {
            case "delay" -> delay(bounded(longValue(step, "durationMs", 0L), 0L, MAX_STEP_DELAY_MILLIS))
                    .thenApply(ignored -> simpleResult("delay"));
            case "mouse.move" -> this.service.mouseMove(requiredDouble(step, "x"), requiredDouble(step, "y"));
            case "mouse.button" -> this.service.mouseButton(
                    requiredInt(step, "button"), requiredInt(step, "action"), integer(step, "modifiers", 0));
            case "mouse.click" -> this.click(
                    requiredDouble(step, "x"), requiredDouble(step, "y"),
                    integer(step, "button", 0), integer(step, "modifiers", 0),
                    bounded(longValue(step, "holdMs", 40L), 0L, 5_000L));
            case "mouse.scroll" -> this.service.mouseScroll(
                    doubleValue(step, "xOffset", 0.0), doubleValue(step, "yOffset", 0.0));
            case "mouse.drag" -> this.drag(step);
            case "key" -> this.service.key(
                    requiredInt(step, "key"), integer(step, "scanCode", 0),
                    requiredInt(step, "action"), integer(step, "modifiers", 0));
            case "key.tap" -> this.keyTap(step);
            case "key.chord" -> this.keyChord(step);
            case "ui.action" -> this.uiAction(step);
            case "ui.drag" -> this.uiDrag(step);
            case "wait.until" -> this.waitUntil(
                    requiredObject(step, "condition"), longValue(step, "timeoutMs", 5_000L));
            case "assert.that" -> this.assertThat(requiredObject(step, "condition"));
            default -> failed("UNSUPPORTED_PIPELINE_STEP", 400, "Unsupported pipeline step: " + type);
        };
    }

    private CompletableFuture<JsonObject> keyTap(JsonObject step) {
        int key = requiredInt(step, "key");
        int scanCode = integer(step, "scanCode", 0);
        int modifiers = integer(step, "modifiers", 0);
        long hold = bounded(longValue(step, "holdMs", 40L), 0L, 5_000L);
        return this.service.key(key, scanCode, 1, modifiers)
                .thenCompose(ignored -> delay(hold))
                .thenCompose(ignored -> this.service.key(key, scanCode, 0, modifiers));
    }

    private CompletableFuture<JsonObject> keyChord(JsonObject step) {
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
            chain = chain.thenCompose(ignored -> this.service.key(
                    requiredInt(key, "key"), integer(key, "scanCode", 0), 1, integer(key, "modifiers", 0)));
        }
        chain = chain.thenCompose(ignored -> delay(bounded(longValue(step, "holdMs", 40L), 0L, 60_000L)))
                .thenApply(ignored -> simpleResult("key.chord"));
        Collections.reverse(keys);
        for (JsonObject key : keys) {
            chain = chain.thenCompose(ignored -> this.service.key(
                    requiredInt(key, "key"), integer(key, "scanCode", 0), 0, integer(key, "modifiers", 0)));
        }
        return chain;
    }

    private CompletableFuture<JsonObject> drag(JsonObject step) {
        double fromX = requiredDouble(step, "fromX");
        double fromY = requiredDouble(step, "fromY");
        double toX = requiredDouble(step, "toX");
        double toY = requiredDouble(step, "toY");
        int button = integer(step, "button", 0);
        int modifiers = integer(step, "modifiers", 0);
        int segments = (int) bounded(longValue(step, "segments", 8L), 1L, 120L);
        long duration = bounded(longValue(step, "durationMs", 250L), 0L, 60_000L);
        long segmentDelay = segments == 0 ? 0L : duration / segments;
        CompletableFuture<JsonObject> chain = this.service.mouseMove(fromX, fromY)
                .thenCompose(ignored -> this.service.mouseButton(button, 1, modifiers));
        for (int index = 1; index <= segments; index++) {
            double progress = (double) index / segments;
            double x = fromX + (toX - fromX) * progress;
            double y = fromY + (toY - fromY) * progress;
            chain = chain.thenCompose(ignored -> delay(segmentDelay))
                    .thenCompose(ignored -> this.service.mouseMove(x, y));
        }
        return chain.thenCompose(ignored -> this.service.mouseButton(button, 0, modifiers));
    }

    private CompletableFuture<JsonObject> uiDrag(JsonObject step) {
        JsonObject fromSelector = requiredObject(step, "fromSelector");
        JsonObject toSelector = requiredObject(step, "toSelector");
        return this.resolve(fromSelector).thenCompose(from -> this.resolve(toSelector).thenCompose(to -> {
            JsonObject fromPoint = from.getAsJsonObject("interactionPoint");
            JsonObject toPoint = to.getAsJsonObject("interactionPoint");
            JsonObject drag = step.deepCopy();
            drag.addProperty("fromX", fromPoint.get("x").getAsDouble());
            drag.addProperty("fromY", fromPoint.get("y").getAsDouble());
            drag.addProperty("toX", toPoint.get("x").getAsDouble());
            drag.addProperty("toY", toPoint.get("y").getAsDouble());
            return this.drag(drag);
        }));
    }

    private CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.scheduler.schedule(() -> future.complete(null), Math.max(0L, millis), TimeUnit.MILLISECONDS);
        return future;
    }

    private static JsonObject resolveTree(JsonObject tree, JsonObject selector) {
        List<JsonObject> matches = findMatches(tree, selector);
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

    private static List<JsonObject> findMatches(JsonObject tree, JsonObject selector) {
        List<JsonObject> matches = new ArrayList<>();
        if (!tree.has("children")) return matches;
        boolean caseSensitive = bool(selector, "caseSensitive", false);
        for (JsonElement element : tree.getAsJsonArray("children")) {
            JsonObject node = element.getAsJsonObject();
            if (!matches(node, selector, caseSensitive)) continue;
            matches.add(node);
        }
        return matches;
    }

    private static boolean matches(JsonObject node, JsonObject selector, boolean caseSensitive) {
        if (!equalsField(node, selector, "nodeId", caseSensitive)) return false;
        if (!equalsField(node, selector, "role", caseSensitive)) return false;
        if (!equalsField(node, selector, "label", caseSensitive)) return false;
        if (!equalsField(node, selector, "class", caseSensitive)) return false;
        if (!containsField(node, selector, "labelContains", "label", caseSensitive)) return false;
        if (!containsField(node, selector, "classContains", "class", caseSensitive)) return false;
        if (selector.has("slot") && (!node.has("slot")
                || node.get("slot").getAsInt() != selector.get("slot").getAsInt())) return false;
        if (bool(selector, "visibleOnly", true) && node.has("visible") && !node.get("visible").getAsBoolean()) return false;
        if (bool(selector, "activeOnly", false) && node.has("active") && !node.get("active").getAsBoolean()) return false;
        return true;
    }

    private static JsonObject evaluate(JsonObject tree, JsonObject condition) {
        String type = string(condition, "type", "screen");
        boolean passed;
        String message;
        if (type.equals("screen")) {
            passed = true;
            if (condition.has("classContains")) {
                passed &= normalized(tree.has("screenClass") ? tree.get("screenClass").getAsString() : "", false)
                        .contains(normalized(condition.get("classContains").getAsString(), false));
            }
            if (condition.has("titleContains")) {
                passed &= normalized(tree.has("title") ? tree.get("title").getAsString() : "", false)
                        .contains(normalized(condition.get("titleContains").getAsString(), false));
            }
            if (condition.has("open")) {
                boolean open = tree.has("screenClass") && !tree.get("screenClass").getAsString().isEmpty();
                passed &= open == condition.get("open").getAsBoolean();
            }
            message = passed ? "Screen condition satisfied" : "Screen condition is not satisfied";
        } else if (type.equals("ui.exists")) {
            JsonObject selector = requiredObject(condition, "selector");
            int count = findMatches(tree, selector).size();
            boolean expected = bool(condition, "exists", true);
            passed = (count > 0) == expected;
            message = passed ? "UI existence condition satisfied" : "UI existence condition is not satisfied";
        } else {
            throw new ProtocolState.ProtocolException(
                    "UNSUPPORTED_CONDITION", 400, "Unsupported condition type: " + type);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "assert.result");
        copy(tree, json, "target", "clientTick", "screenClass", "screenRevision", "menuRevision");
        json.add("condition", condition.deepCopy());
        json.addProperty("passed", passed);
        json.addProperty("message", message);
        return json;
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

    private static boolean equalsField(
            JsonObject node, JsonObject selector, String field, boolean caseSensitive) {
        if (!selector.has(field)) return true;
        if (!node.has(field)) return false;
        return normalized(node.get(field).getAsString(), caseSensitive)
                .equals(normalized(selector.get(field).getAsString(), caseSensitive));
    }

    private static boolean containsField(
            JsonObject node,
            JsonObject selector,
            String selectorField,
            String nodeField,
            boolean caseSensitive) {
        if (!selector.has(selectorField)) return true;
        if (!node.has(nodeField)) return false;
        return normalized(node.get(nodeField).getAsString(), caseSensitive)
                .contains(normalized(selector.get(selectorField).getAsString(), caseSensitive));
    }

    private static String normalized(String value, boolean caseSensitive) {
        return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
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
        private final long timeoutMillis;
        private final boolean cleanupOnComplete;
        private final long startedAtMillis = System.currentTimeMillis();
        private final CompletableFuture<JsonObject> result = new CompletableFuture<>();
        private final JsonArray stepResults = new JsonArray();
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();
        private int index;

        private PipelineExecution(
                JsonArray steps, Runnable leaseCheck, long timeoutMillis, boolean cleanupOnComplete) {
            this.steps = steps;
            this.leaseCheck = leaseCheck;
            this.timeoutMillis = timeoutMillis;
            this.cleanupOnComplete = cleanupOnComplete;
        }

        private CompletableFuture<JsonObject> start() {
            this.result.whenComplete((ignored, error) -> {
                if (this.result.isCancelled()) this.cleanup("pipeline_cancelled");
            });
            scheduler.execute(this::next);
            return this.result;
        }

        private void next() {
            if (this.result.isDone()) return;
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
                runStep(step).whenComplete((stepResult, error) -> scheduler.execute(() -> {
                    if (this.result.isDone()) return;
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
            CompletableFuture<JsonObject> cleanup = this.cleanupOnComplete
                    ? this.cleanup("pipeline_completed")
                    : CompletableFuture.completedFuture(simpleResult("input.preserved"));
            cleanup.whenComplete((cleanupResult, error) -> {
                if (error != null) {
                    this.result.completeExceptionally(unwrap(error));
                    return;
                }
                JsonObject json = new JsonObject();
                json.addProperty("type", "pipeline.result");
                json.addProperty("status", "completed");
                json.addProperty("stepCount", this.steps.size());
                json.addProperty("durationMillis", System.currentTimeMillis() - this.startedAtMillis);
                json.addProperty("cleanupOnComplete", this.cleanupOnComplete);
                json.add("steps", this.stepResults.deepCopy());
                json.add("cleanup", cleanupResult == null ? new JsonObject() : cleanupResult.deepCopy());
                this.result.complete(json);
            });
        }

        private void fail(Throwable throwable) {
            this.cleanup("pipeline_failed").whenComplete((ignored, cleanupError) ->
                    this.result.completeExceptionally(throwable));
        }

        private CompletableFuture<JsonObject> cleanup(String reason) {
            if (!this.cleanupStarted.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(simpleResult("input.cleanup_already_started"));
            }
            return service.releaseAllInput(reason);
        }
    }
}
