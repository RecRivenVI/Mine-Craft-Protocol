package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ConditionEngine implements AutoCloseable {
    private final ProbeService service;
    private final ProtocolState protocolState;
    private final EventHub eventHub;
    private final EventHub debugEventHub;
    private final RecordingEngine recording;
    private final ObservationEngine observation;
    private final ScheduledExecutorService scheduler;

    ConditionEngine(
            ProbeService service,
            ProtocolState protocolState,
            EventHub eventHub,
            EventHub debugEventHub,
            RecordingEngine recording,
            ObservationEngine observation) {
        this.service = service;
        this.protocolState = protocolState;
        this.eventHub = eventHub;
        this.debugEventHub = debugEventHub;
        this.recording = recording;
        this.observation = observation;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "minecraft-protocol-condition");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    CompletableFuture<JsonObject> assertThat(JsonObject condition) {
        return CancellableWork.map(
                this.evaluate(condition),
                evaluation -> {
                    authorize(condition);
                    validateEventEvidence(condition, evaluation);
                    if (!evaluation.get("passed").getAsBoolean()) {
                        throw new ProtocolState.ProtocolException(
                                "ASSERTION_FAILED",
                                412,
                                evaluation.get("message").getAsString(),
                                readinessDetails(evaluation));
                    }
                    return evaluation;
                });
    }

    CompletableFuture<JsonObject> waitUntil(JsonObject condition, long requestedTimeoutMillis) {
        authorize(condition);
        long timeoutMillis = Math.max(1L, Math.min(requestedTimeoutMillis, 60_000L));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        PollingFuture result = new PollingFuture();
        Runnable categoryPublication =
                string(condition, "type", "").equals("operation")
                        ? operationReadGuard(condition)
                        : uiCondition(string(condition, "type", ""))
                                ? protocolState.uiReadGuard(string(condition, "category", "USER"))
                                : worldCondition(condition)
                                        ? observation
                                                .worldReads()
                                                .publicationGuard(
                                                        observation
                                                                .worldReads()
                                                                .category(condition)
                                                                .name())
                                        : () -> {};
        Runnable publication =
                () -> {
                    authorize(condition);
                    categoryPublication.run();
                };
        Runnable[] poll = new Runnable[1];
        poll[0] =
                () -> {
                    if (result.isDone()) return;
                    CompletableFuture<JsonObject> evaluation;
                    try {
                        publication.run();
                        evaluation = this.evaluate(condition);
                    } catch (Throwable denied) {
                        result.completeExceptionally(denied);
                        return;
                    }
                    result.current.set(evaluation);
                    evaluation.whenComplete(
                            (value, failure) -> {
                                if (result.isDone()) return;
                                try {
                                    publication.run();
                                    if (value != null) validateEventEvidence(condition, value);
                                } catch (Throwable denied) {
                                    result.completeExceptionally(denied);
                                    return;
                                }
                                if (failure != null) {
                                    result.completeExceptionally(unwrap(failure));
                                } else if (value.get("passed").getAsBoolean()) {
                                    value.addProperty("waited", true);
                                    result.complete(value);
                                } else if (System.nanoTime() >= deadline) {
                                    result.completeExceptionally(
                                            new ProtocolState.ProtocolException(
                                                    "WAIT_TIMEOUT",
                                                    408,
                                                    value.get("message").getAsString(),
                                                    readinessDetails(value)));
                                } else {
                                    result.pending.set(
                                            this.scheduler.schedule(
                                                    poll[0], 25L, TimeUnit.MILLISECONDS));
                                }
                            });
                };
        poll[0].run();
        return result;
    }

    private CompletableFuture<JsonObject> evaluate(JsonObject condition) {
        authorize(condition);
        String type = string(condition, "type", "");
        if (type.equals("operation")) {
            Runnable publication = operationReadGuard(condition);
            return CancellableWork.handle(
                    evaluateSource(condition),
                    (result, failure) -> {
                        publication.run();
                        if (failure != null) throw new CompletionException(failure);
                        var domains =
                                protocolState.operationDiagnosticDomains(
                                        requiredString(condition, "operationId"));
                        if (!domains.isEmpty()) {
                            result.addProperty("dataCategory", "DEBUG");
                            if (domains.size() == 1)
                                result.addProperty("domain", domains.iterator().next());
                        }
                        return result;
                    });
        }
        if (uiCondition(type)) {
            Runnable publication = protocolState.uiReadGuard(string(condition, "category", "USER"));
            return CancellableWork.handle(
                    evaluateSource(condition),
                    (result, failure) -> {
                        publication.run();
                        if (failure != null) throw new CompletionException(failure);
                        authorize(condition);
                        result.addProperty("domain", "UI");
                        result.addProperty("dataCategory", string(condition, "category", "USER"));
                        return result;
                    });
        }
        if (worldCondition(condition)) {
            String category = observation.worldReads().category(condition).name();
            // Validate the original values before legacy condition helpers can clamp or truncate
            // them.
            if (category.equals("USER")
                    && !string(condition, "perspective", "client").equals("server")
                    && (type.equals("block") || type.equals("entity"))) {
                JsonObject query = new JsonObject();
                for (String field :
                        type.equals("block")
                                ? new String[] {"x", "y", "z"}
                                : new String[] {"radius"})
                    if (condition.has(field)) query.add(field, condition.get(field).deepCopy());
                observation
                        .worldReads()
                        .validateUser(type.equals("block") ? "block" : "entities", query);
            }
            if (category.equals("USER") && type.equals("entity") && condition.has("uuid"))
                throw new ProtocolState.ProtocolException(
                        "WORLD_USER_FIELD_UNAVAILABLE", 409, "Raw entity UUID requires DEBUG");
            return observation
                    .worldReads()
                    .guarded(
                            category,
                            () ->
                                    CancellableWork.map(
                                            evaluateSource(condition),
                                            result -> {
                                                result.addProperty("domain", "WORLD");
                                                result.addProperty("dataCategory", category);
                                                return result;
                                            }));
        }
        return evaluateSource(condition);
    }

    private void validateEventEvidence(JsonObject condition, JsonObject result) {
        if (string(condition, "type", "").equals("event"))
            (string(condition, "category", "USER").equals("DEBUG") ? debugEventHub : eventHub)
                    .validateConditionEvidence(result);
    }

    void authorize(JsonObject condition) {
        protocolState.requireScope("read");
        if (condition == null)
            throw new ProtocolState.ProtocolException(
                    "INVALID_CONDITION", 400, "Condition object is required");
        String type = string(condition, "type", "");
        if (type.equals("input.released")) {
            protocolState.requireScope("diagnostics");
            if (!condition.keySet().equals(java.util.Set.of("type")))
                throw new ProtocolState.ProtocolException(
                        "INVALID_CONDITION", 400, "Input released condition accepts only its type");
        }
        if (uiCondition(type))
            protocolState.requireUiCategory(string(condition, "category", "USER"));
        if (type.equals("event")) protocolState.requireScope("event");
        if (type.equals("operation")) operationReadGuard(condition).run();
        if (worldCondition(condition))
            observation.worldReads().authorize(string(condition, "category", "USER"));
    }

    private static boolean uiCondition(String type) {
        return java.util.Set.of("screen", "ui.exists", "menu", "inventory").contains(type);
    }

    private CompletableFuture<JsonObject> uiTree(JsonObject condition) {
        return string(condition, "category", "USER").equals("DEBUG")
                ? service.debugUiTree()
                : service.uiTree();
    }

    private boolean worldCondition(JsonObject condition) {
        String type = string(condition, "type", "");
        return java.util.Set.of(
                        "player", "block", "entity", "provider", "scene", "event", "recording")
                .contains(type);
    }

    private Runnable operationReadGuard(JsonObject condition) {
        return protocolState.operationReadGuard(
                requiredString(condition, "operationId"),
                string(condition, "category", "USER"),
                observation.worldReads()::authorize);
    }

    private CompletableFuture<JsonObject> evaluateSource(JsonObject condition) {
        String type = string(condition, "type", "");
        return switch (type) {
            case "input.released" -> this.inputReleased(condition);
            case "screen" -> this.screen(condition);
            case "ui.exists" -> this.uiExists(condition);
            case "player" -> this.player(condition);
            case "block" -> this.block(condition);
            case "entity" -> this.entity(condition);
            case "scene" ->
                    CancellableWork.map(
                            observation
                                    .worldReads()
                                    .scene(observation.worldReads().category(condition).name()),
                            scene ->
                                    result(
                                            condition,
                                            scene,
                                            matchesExpected(scene, expected(condition)),
                                            "Scene condition"));
            case "menu", "inventory" -> this.menu(condition);
            case "recording" ->
                    CompletableFuture.completedFuture(
                            result(
                                    condition,
                                    this.recording.status(
                                            requiredString(condition, "recordingId"),
                                            observation.worldReads().category(condition).name()),
                                    matchesExpected(
                                            this.recording.status(
                                                    requiredString(condition, "recordingId"),
                                                    observation
                                                            .worldReads()
                                                            .category(condition)
                                                            .name()),
                                            expected(condition)),
                                    "Recording condition"));
            case "event" ->
                    CompletableFuture.completedFuture(
                            (observation.worldReads().category(condition).name().equals("DEBUG")
                                            ? this.debugEventHub
                                            : this.eventHub)
                                    .evaluateCondition(condition));
            case "operation" -> {
                JsonObject operation =
                        this.protocolState.operationStatus(
                                requiredString(condition, "operationId"));
                yield CompletableFuture.completedFuture(
                        result(
                                condition,
                                operation,
                                matchesExpected(operation, expected(condition)),
                                "Operation condition"));
            }
            case "provider" -> this.provider(condition);
            default ->
                    CompletableFuture.failedFuture(
                            new ProtocolState.ProtocolException(
                                    "UNSUPPORTED_CONDITION",
                                    400,
                                    "Unsupported condition type: " + type));
        };
    }

    private CompletableFuture<JsonObject> inputReleased(JsonObject condition) {
        return CancellableWork.handle(
                this.service.inputState(),
                (state, failure) -> {
                    authorize(condition);
                    if (failure != null) throw new CompletionException(failure);
                    int keys = heldCount(state, "pressedKeyCount");
                    int buttons = heldCount(state, "pressedButtonCount");
                    JsonObject evidence = new JsonObject();
                    evidence.addProperty("pressedKeyCount", keys);
                    evidence.addProperty("pressedButtonCount", buttons);
                    return result(
                            condition,
                            evidence,
                            keys == 0 && buttons == 0,
                            "Runtime held input release condition");
                });
    }

    private static int heldCount(JsonObject state, String name) {
        try {
            if (state != null
                    && state.has(name)
                    && state.get(name).isJsonPrimitive()
                    && state.getAsJsonPrimitive(name).isNumber()) {
                int count = state.get(name).getAsBigDecimal().intValueExact();
                if (count >= 0) return count;
            }
        } catch (ArithmeticException | NumberFormatException invalid) {
            // A malformed owner-thread snapshot cannot establish that input was released.
        }
        throw new ProtocolState.ProtocolException(
                "INPUT_STATE_UNAVAILABLE", 409, "Runtime held input counts are unavailable");
    }

    private CompletableFuture<JsonObject> screen(JsonObject condition) {
        boolean hud = condition.has("open") && !condition.get("open").getAsBoolean();
        if (bool(condition, "ready", false) && !hud) requiredObject(condition, "selector");
        return CancellableWork.map(
                this.uiTree(condition),
                tree -> {
                    boolean passed = true;
                    if (condition.has("classContains")) {
                        passed &=
                                normalized(string(tree, "screenClass", ""), false)
                                        .contains(
                                                normalized(
                                                        condition
                                                                .get("classContains")
                                                                .getAsString(),
                                                        false));
                    }
                    if (condition.has("titleContains")) {
                        passed &=
                                normalized(string(tree, "title", ""), false)
                                        .contains(
                                                normalized(
                                                        condition
                                                                .get("titleContains")
                                                                .getAsString(),
                                                        false));
                    }
                    if (condition.has("open")) {
                        boolean open = !string(tree, "screenClass", "").isEmpty();
                        passed &= open == condition.get("open").getAsBoolean();
                    }
                    if (bool(condition, "ready", false)) {
                        // One fresh owner-thread tree supplies the Screen, overlay, geometry
                        // and target. This read does not reserve the target for a later action.
                        JsonObject readiness = pageReadiness(tree, condition, passed, hud);
                        tree.add("readiness", readiness);
                        tree.addProperty("pageReady", readiness.get("ready").getAsBoolean());
                        passed &= readiness.get("ready").getAsBoolean();
                    }
                    return result(condition, tree, passed, "Screen condition");
                });
    }

    private static JsonObject readinessDetails(JsonObject evaluation) {
        JsonObject details = new JsonObject();
        JsonObject evidence = evaluation.getAsJsonObject("evidence");
        if (evidence != null && evidence.has("readiness"))
            details.add("readiness", evidence.get("readiness").deepCopy());
        return details;
    }

    private static JsonObject pageReadiness(
            JsonObject tree, JsonObject condition, boolean expectedScreen, boolean hud) {
        JsonObject result = new JsonObject();
        JsonObject checks = new JsonObject();
        JsonArray failures = new JsonArray();
        result.addProperty("kind", hud ? "hud" : "screen");
        result.add("checks", checks);
        result.add("failures", failures);
        readyCheck(
                checks,
                failures,
                "screen",
                expectedScreen
                        && (hud
                                ? string(tree, "screenClass", "").isEmpty()
                                        && number(tree, "screenIdentity", -1) == 0
                                : number(tree, "screenIdentity", 0) > 0)
                        && tree.has("screenRevision"),
                "SCREEN_NOT_READY");
        readyCheck(
                checks,
                failures,
                "overlay",
                tree.has("overlayIdentity") && number(tree, "overlayIdentity", -1) == 0,
                "OVERLAY_PRESENT");
        readyCheck(
                checks,
                failures,
                "viewport",
                number(tree, "windowWidth", 0) > 0
                        && number(tree, "windowHeight", 0) > 0
                        && number(tree, "width", 0) > 0
                        && number(tree, "height", 0) > 0,
                "VIEWPORT_UNAVAILABLE");
        if (!hud || condition.has("selector"))
            targetReadiness(
                    tree,
                    requiredObject(condition, "selector"),
                    string(condition, "readyAction", "click"),
                    checks,
                    failures);
        result.addProperty("ready", failures.isEmpty());
        return result;
    }

    private static void readyCheck(
            JsonObject checks, JsonArray failures, String field, boolean passed, String reason) {
        checks.addProperty(field, passed);
        if (!passed) failures.add(reason);
    }

    private static void targetReadiness(
            JsonObject tree,
            JsonObject selector,
            String action,
            JsonObject checks,
            JsonArray failures) {
        // Match only the authorized projection, never inspect hidden USER nodes for diagnostics.
        List<JsonObject> matches = findMatches(tree, selector);
        int index = selector.has("nth") ? selector.get("nth").getAsInt() : 0;
        boolean selected =
                index >= 0
                        && index < matches.size()
                        && (selector.has("nth") || matches.size() == 1);
        readyCheck(
                checks,
                failures,
                "selector",
                selected,
                matches.isEmpty() ? "TARGET_NOT_FOUND_OR_NOT_VISIBLE" : "TARGET_AMBIGUOUS");
        if (!selected) return;
        JsonObject node = matches.get(index);
        readyCheck(checks, failures, "active", bool(node, "active", false), "TARGET_INACTIVE");
        readyCheck(checks, failures, "visible", bool(node, "visible", false), "TARGET_NOT_VISIBLE");
        boolean supported = false;
        if (node.has("actions") && node.get("actions").isJsonArray())
            for (JsonElement candidate : node.getAsJsonArray("actions"))
                supported |= candidate.isJsonPrimitive() && candidate.getAsString().equals(action);
        readyCheck(
                checks, failures, "action", node.has("nodeId") && supported, "ACTION_UNSUPPORTED");
        double x = number(node, "x", Double.NaN),
                y = number(node, "y", Double.NaN),
                width = number(node, "width", 0),
                height = number(node, "height", 0);
        double cx = x + width / 2, cy = y + height / 2;
        readyCheck(
                checks,
                failures,
                "bounds",
                Double.isFinite(cx)
                        && Double.isFinite(cy)
                        && width > 0
                        && height > 0
                        && cx >= 0
                        && cy >= 0
                        && cx < number(tree, "width", 0)
                        && cy < number(tree, "height", 0),
                "TARGET_BOUNDS_UNAVAILABLE");
    }

    private CompletableFuture<JsonObject> uiExists(JsonObject condition) {
        JsonObject selector = requiredObject(condition, "selector");
        return CancellableWork.map(
                this.uiTree(condition),
                tree -> {
                    List<JsonObject> matches = findMatches(tree, selector);
                    boolean expected = bool(condition, "exists", true);
                    boolean passed = (matches.size() > 0) == expected;
                    JsonObject evidence = tree.deepCopy();
                    JsonArray values = new JsonArray();
                    matches.forEach(match -> values.add(match.deepCopy()));
                    evidence.add("matches", values);
                    evidence.addProperty("matchCount", matches.size());
                    return result(condition, evidence, passed, "UI existence condition");
                });
    }

    private CompletableFuture<JsonObject> player(JsonObject condition) {
        boolean server = string(condition, "perspective", "client").equals("server");
        String category = this.observation.worldReads().category(condition).name();
        return CancellableWork.map(
                this.observation
                        .worldReads()
                        .read(
                                category,
                                server ? "server/player" : "client/player",
                                new JsonObject(),
                                () ->
                                        server
                                                ? this.service.serverPlayerState()
                                                : this.service.playerState()),
                player -> {
                    boolean passed = matchesExpected(player, expected(condition));
                    if (condition.has("healthMin"))
                        passed &=
                                number(player, "health", Double.NEGATIVE_INFINITY)
                                        >= condition.get("healthMin").getAsDouble();
                    if (condition.has("healthMax"))
                        passed &=
                                number(player, "health", Double.POSITIVE_INFINITY)
                                        <= condition.get("healthMax").getAsDouble();
                    if (condition.has("position")) {
                        JsonObject position = condition.getAsJsonObject("position");
                        double tolerance = number(position, "tolerance", 0.0);
                        passed &=
                                close(player, position, "x", tolerance)
                                        && close(player, position, "y", tolerance)
                                        && close(player, position, "z", tolerance);
                    }
                    return result(condition, player, passed, "Player condition");
                });
    }

    private CompletableFuture<JsonObject> block(JsonObject condition) {
        int x = requiredInt(condition, "x");
        int y = requiredInt(condition, "y");
        int z = requiredInt(condition, "z");
        boolean server = string(condition, "perspective", "client").equals("server");
        String category = this.observation.worldReads().category(condition).name();
        return CancellableWork.map(
                this.observation
                        .worldReads()
                        .read(
                                category,
                                server ? "server/block" : "client/block",
                                blockQuery(x, y, z),
                                () ->
                                        server
                                                ? this.service.serverBlockState(x, y, z)
                                                : this.service.blockState(x, y, z)),
                block -> {
                    boolean passed = matchesExpected(block, expected(condition));
                    if (condition.has("blockId")) {
                        passed &=
                                string(block, "block", "")
                                        .equals(condition.get("blockId").getAsString());
                    }
                    if (condition.has("available")) {
                        passed &=
                                bool(block, "available", false)
                                        == condition.get("available").getAsBoolean();
                    }
                    return result(condition, block, passed, "Block condition");
                });
    }

    private CompletableFuture<JsonObject> entity(JsonObject condition) {
        double radius = Math.max(0.0, Math.min(number(condition, "radius", 16.0), 128.0));
        boolean server = string(condition, "perspective", "client").equals("server");
        String category = this.observation.worldReads().category(condition).name();
        return CancellableWork.map(
                this.observation
                        .worldReads()
                        .read(
                                category,
                                server ? "server/entities" : "client/entities",
                                entityQuery(radius),
                                () ->
                                        server
                                                ? this.service.serverEntities(radius)
                                                : this.service.entities(radius)),
                query -> {
                    if (category.equals("USER")
                            && !bool(condition, "exists", true)
                            && bool(query, "entitiesTruncated", false))
                        throw new ProtocolState.ProtocolException(
                                "WORLD_USER_QUERY_INCOMPLETE",
                                409,
                                "Truncated USER candidates cannot prove entity absence");
                    JsonArray matches = new JsonArray();
                    JsonArray entities =
                            query.has("entities")
                                    ? query.getAsJsonArray("entities")
                                    : new JsonArray();
                    for (JsonElement element : entities) {
                        JsonObject entity = element.getAsJsonObject();
                        if (condition.has("entityType")
                                && !string(entity, "type", "")
                                        .equals(condition.get("entityType").getAsString()))
                            continue;
                        if (condition.has("uuid")
                                && !string(entity, "uuid", "")
                                        .equals(condition.get("uuid").getAsString())) continue;
                        if (!matchesExpected(entity, expected(condition))) continue;
                        matches.add(entity.deepCopy());
                    }
                    int minimum =
                            condition.has("minCount")
                                    ? condition.get("minCount").getAsInt()
                                    : (bool(condition, "exists", true) ? 1 : 0);
                    boolean passed =
                            bool(condition, "exists", true)
                                    ? matches.size() >= minimum
                                    : matches.isEmpty();
                    JsonObject evidence = query.deepCopy();
                    evidence.add("matches", matches);
                    evidence.addProperty("matchCount", matches.size());
                    return result(condition, evidence, passed, "Entity condition");
                });
    }

    private static JsonObject blockQuery(int x, int y, int z) {
        JsonObject query = new JsonObject();
        query.addProperty("x", x);
        query.addProperty("y", y);
        query.addProperty("z", z);
        return query;
    }

    private static JsonObject entityQuery(double radius) {
        JsonObject query = new JsonObject();
        query.addProperty("radius", radius);
        return query;
    }

    private CompletableFuture<JsonObject> menu(JsonObject condition) {
        return CancellableWork.map(
                this.uiTree(condition),
                tree -> {
                    boolean passed = matchesExpected(tree, expected(condition));
                    if (condition.has("menuId")) {
                        passed &=
                                tree.has("menuId")
                                        && tree.get("menuId").getAsInt()
                                                == condition.get("menuId").getAsInt();
                    }
                    if (condition.has("open")) {
                        boolean open = tree.has("menuId");
                        passed &= open == condition.get("open").getAsBoolean();
                    }
                    JsonArray matches = new JsonArray();
                    if (condition.has("slot") && tree.has("children")) {
                        int slot = condition.get("slot").getAsInt();
                        for (JsonElement element : tree.getAsJsonArray("children")) {
                            JsonObject node = element.getAsJsonObject();
                            if (!node.has("slot") || node.get("slot").getAsInt() != slot) continue;
                            if (condition.has("itemId")
                                    && !string(node, "item", "")
                                            .equals(condition.get("itemId").getAsString()))
                                continue;
                            if (condition.has("countMin")
                                    && number(node, "count", 0.0)
                                            < condition.get("countMin").getAsDouble()) continue;
                            matches.add(node.deepCopy());
                        }
                        passed &= !matches.isEmpty();
                    }
                    JsonObject evidence = tree.deepCopy();
                    evidence.add("matches", matches);
                    return result(condition, evidence, passed, "Menu or inventory condition");
                });
    }

    private CompletableFuture<JsonObject> provider(JsonObject condition) {
        JsonObject request = new JsonObject();
        request.addProperty("providerId", requiredString(condition, "providerId"));
        if (condition.has("category"))
            request.add("category", condition.get("category").deepCopy());
        if (condition.has("params")) request.add("query", condition.get("params").deepCopy());
        return CancellableWork.map(
                this.observation.read(request),
                value ->
                        result(
                                condition,
                                value,
                                matchesExpected(value, expected(condition)),
                                "Provider condition"));
    }

    @Override
    public void close() {
        this.scheduler.shutdownNow();
    }

    private static JsonObject result(
            JsonObject condition, JsonObject evidence, boolean passed, String subject) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "assert.result");
        if (evidence.has("dataCategory")) {
            json.addProperty("domain", "WORLD");
            json.add("dataCategory", evidence.get("dataCategory").deepCopy());
        }
        json.add("condition", condition.deepCopy());
        json.addProperty("passed", passed);
        json.addProperty("message", subject + (passed ? " satisfied" : " is not satisfied"));
        json.add("evidence", evidence.deepCopy());
        return json;
    }

    private static JsonObject expected(JsonObject condition) {
        return condition.has("expected") && condition.get("expected").isJsonObject()
                ? condition.getAsJsonObject("expected")
                : new JsonObject();
    }

    public static List<JsonObject> findMatches(JsonObject tree, JsonObject selector) {
        List<JsonObject> matches = new ArrayList<>();
        if (!tree.has("children")) return matches;
        boolean caseSensitive = bool(selector, "caseSensitive", false);
        for (JsonElement element : tree.getAsJsonArray("children")) {
            JsonObject node = element.getAsJsonObject();
            if (matches(node, selector, caseSensitive)) matches.add(node);
            matches.addAll(findMatches(node, selector));
        }
        return matches;
    }

    private static boolean matches(JsonObject node, JsonObject selector, boolean caseSensitive) {
        if (!equalsField(node, selector, "nodeId", caseSensitive)) return false;
        if (!equalsField(node, selector, "role", caseSensitive)) return false;
        if (!equalsField(node, selector, "label", caseSensitive)) return false;
        if (!equalsField(node, selector, "class", caseSensitive)) return false;
        if (!equalsField(node, selector, "worldId", true)) return false;
        if (!equalsField(node, selector, "resourceId", true)) return false;
        if (!equalsField(node, selector, "entryIndex", true)) return false;
        if (!equalsField(node, selector, "controlKey", true)) return false;
        if (!equalsField(node, selector, "packId", true)) return false;
        if (!equalsField(node, selector, "translationKey", true)) return false;
        if (!containsField(node, selector, "labelContains", "label", caseSensitive)) return false;
        if (!containsField(node, selector, "classContains", "class", caseSensitive)) return false;
        if (selector.has("slot")
                && (!node.has("slot")
                        || node.get("slot").getAsInt() != selector.get("slot").getAsInt()))
            return false;
        if (bool(selector, "visibleOnly", true)
                && node.has("visible")
                && !node.get("visible").getAsBoolean()) return false;
        return !bool(selector, "activeOnly", false)
                || !node.has("active")
                || node.get("active").getAsBoolean();
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

    private static JsonObject requiredObject(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonObject()) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_CONDITION", 400, "Missing condition object: " + name);
        }
        return json.getAsJsonObject(name);
    }

    private static boolean matchesExpected(JsonObject actual, JsonObject expected) {
        for (var entry : expected.entrySet()) {
            if (!actual.has(entry.getKey()) || !same(actual.get(entry.getKey()), entry.getValue()))
                return false;
        }
        return true;
    }

    private static boolean same(JsonElement actual, JsonElement expected) {
        if (actual == null || expected == null) return actual == expected;
        if (expected.isJsonObject() && actual.isJsonObject()) {
            return matchesExpected(actual.getAsJsonObject(), expected.getAsJsonObject());
        }
        return actual.equals(expected);
    }

    private static boolean close(
            JsonObject actual, JsonObject expected, String key, double tolerance) {
        return !expected.has(key)
                || (actual.has(key)
                        && Math.abs(actual.get(key).getAsDouble() - expected.get(key).getAsDouble())
                                <= tolerance);
    }

    private static int requiredInt(JsonObject json, String name) {
        if (!json.has(name))
            throw new ProtocolState.ProtocolException(
                    "INVALID_CONDITION", 400, "Missing condition field: " + name);
        return json.get(name).getAsInt();
    }

    private static String requiredString(JsonObject json, String name) {
        if (!json.has(name) || json.get(name).getAsString().isBlank()) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_CONDITION", 400, "Missing condition field: " + name);
        }
        return json.get(name).getAsString();
    }

    private static String string(JsonObject json, String name, String fallback) {
        return json.has(name) && !json.get(name).isJsonNull()
                ? json.get(name).getAsString()
                : fallback;
    }

    private static double number(JsonObject json, String name, double fallback) {
        return json.has(name) ? json.get(name).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject json, String name, boolean fallback) {
        return json.has(name) ? json.get(name).getAsBoolean() : fallback;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null)
            current = current.getCause();
        return current;
    }

    private static final class PollingFuture extends CompletableFuture<JsonObject> {
        private final AtomicReference<CompletableFuture<?>> current = new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            CompletableFuture<?> active = this.current.getAndSet(null);
            if (active != null) active.cancel(mayInterruptIfRunning);
            ScheduledFuture<?> scheduled = this.pending.getAndSet(null);
            if (scheduled != null) scheduled.cancel(false);
            return super.cancel(mayInterruptIfRunning);
        }
    }
}
