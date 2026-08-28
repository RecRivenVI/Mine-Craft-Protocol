package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

final class ConditionEngine implements AutoCloseable {
    private final ProbeService service;
    private final ProtocolState protocolState;
    private final EventHub eventHub;
    private final RecordingEngine recording;
    private final ObservationEngine observation;
    private final ScheduledExecutorService scheduler;

    ConditionEngine(
            ProbeService service,
            ProtocolState protocolState,
            EventHub eventHub,
            RecordingEngine recording,
            ObservationEngine observation) {
        this.service = service;
        this.protocolState = protocolState;
        this.eventHub = eventHub;
        this.recording = recording;
        this.observation = observation;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-condition");
            thread.setDaemon(true);
            return thread;
        });
    }

    CompletableFuture<JsonObject> assertThat(JsonObject condition) {
        return this.evaluate(condition).thenApply(evaluation -> {
            if (!evaluation.get("passed").getAsBoolean()) {
                throw new ProtocolState.ProtocolException(
                        "ASSERTION_FAILED", 412, evaluation.get("message").getAsString());
            }
            return evaluation;
        });
    }

    CompletableFuture<JsonObject> waitUntil(JsonObject condition, long requestedTimeoutMillis) {
        long timeoutMillis = Math.max(1L, Math.min(requestedTimeoutMillis, 60_000L));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        PollingFuture result = new PollingFuture();
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (result.isDone()) return;
            CompletableFuture<JsonObject> evaluation = this.evaluate(condition);
            result.current.set(evaluation);
            evaluation.whenComplete((value, failure) -> {
                if (result.isDone()) return;
                if (failure != null) {
                    result.completeExceptionally(unwrap(failure));
                } else if (value.get("passed").getAsBoolean()) {
                    value.addProperty("waited", true);
                    result.complete(value);
                } else if (System.nanoTime() >= deadline) {
                    result.completeExceptionally(new ProtocolState.ProtocolException(
                            "WAIT_TIMEOUT", 408, value.get("message").getAsString()));
                } else {
                    result.pending.set(this.scheduler.schedule(poll[0], 25L, TimeUnit.MILLISECONDS));
                }
            });
        };
        poll[0].run();
        return result;
    }

    private CompletableFuture<JsonObject> evaluate(JsonObject condition) {
        String type = string(condition, "type", "");
        return switch (type) {
            case "screen" -> this.screen(condition);
            case "ui.exists" -> this.uiExists(condition);
            case "player" -> this.player(condition);
            case "block" -> this.block(condition);
            case "entity" -> this.entity(condition);
            case "menu", "inventory" -> this.menu(condition);
            case "recording" -> CompletableFuture.completedFuture(
                    result(condition, this.recording.status(requiredString(condition, "recordingId")),
                            matchesExpected(this.recording.status(requiredString(condition, "recordingId")), expected(condition)),
                            "Recording condition"));
            case "event" -> CompletableFuture.completedFuture(this.eventHub.evaluateCondition(condition));
            case "operation" -> {
                JsonObject operation = this.protocolState.operationStatus(requiredString(condition, "operationId"));
                yield CompletableFuture.completedFuture(result(
                        condition, operation, matchesExpected(operation, expected(condition)), "Operation condition"));
            }
            case "provider" -> this.provider(condition);
            default -> CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "UNSUPPORTED_CONDITION", 400, "Unsupported condition type: " + type));
        };
    }

    private CompletableFuture<JsonObject> screen(JsonObject condition) {
        return this.service.uiTree().thenApply(tree -> {
            boolean passed = true;
            if (condition.has("classContains")) {
                passed &= normalized(string(tree, "screenClass", ""), false)
                        .contains(normalized(condition.get("classContains").getAsString(), false));
            }
            if (condition.has("titleContains")) {
                passed &= normalized(string(tree, "title", ""), false)
                        .contains(normalized(condition.get("titleContains").getAsString(), false));
            }
            if (condition.has("open")) {
                boolean open = !string(tree, "screenClass", "").isEmpty();
                passed &= open == condition.get("open").getAsBoolean();
            }
            return result(condition, tree, passed, "Screen condition");
        });
    }

    private CompletableFuture<JsonObject> uiExists(JsonObject condition) {
        JsonObject selector = requiredObject(condition, "selector");
        return this.service.uiTree().thenApply(tree -> {
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
        return (server ? this.service.serverPlayerState() : this.service.playerState()).thenApply(player -> {
            boolean passed = matchesExpected(player, expected(condition));
            if (condition.has("healthMin")) passed &= number(player, "health", Double.NEGATIVE_INFINITY)
                    >= condition.get("healthMin").getAsDouble();
            if (condition.has("healthMax")) passed &= number(player, "health", Double.POSITIVE_INFINITY)
                    <= condition.get("healthMax").getAsDouble();
            if (condition.has("position")) {
                JsonObject position = condition.getAsJsonObject("position");
                double tolerance = number(position, "tolerance", 0.0);
                passed &= close(player, position, "x", tolerance)
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
        return (server ? this.service.serverBlockState(x, y, z) : this.service.blockState(x, y, z))
                .thenApply(block -> {
                    boolean passed = matchesExpected(block, expected(condition));
                    if (condition.has("blockId")) {
                        passed &= string(block, "block", "").equals(condition.get("blockId").getAsString());
                    }
                    if (condition.has("available")) {
                        passed &= bool(block, "available", false) == condition.get("available").getAsBoolean();
                    }
                    return result(condition, block, passed, "Block condition");
                });
    }

    private CompletableFuture<JsonObject> entity(JsonObject condition) {
        double radius = Math.max(0.0, Math.min(number(condition, "radius", 16.0), 128.0));
        boolean server = string(condition, "perspective", "client").equals("server");
        return (server ? this.service.serverEntities(radius) : this.service.entities(radius)).thenApply(query -> {
            JsonArray matches = new JsonArray();
            JsonArray entities = query.has("entities") ? query.getAsJsonArray("entities") : new JsonArray();
            for (JsonElement element : entities) {
                JsonObject entity = element.getAsJsonObject();
                if (condition.has("entityType")
                        && !string(entity, "type", "").equals(condition.get("entityType").getAsString())) continue;
                if (condition.has("uuid")
                        && !string(entity, "uuid", "").equals(condition.get("uuid").getAsString())) continue;
                if (!matchesExpected(entity, expected(condition))) continue;
                matches.add(entity.deepCopy());
            }
            int minimum = condition.has("minCount") ? condition.get("minCount").getAsInt()
                    : (bool(condition, "exists", true) ? 1 : 0);
            boolean passed = bool(condition, "exists", true) ? matches.size() >= minimum : matches.isEmpty();
            JsonObject evidence = query.deepCopy();
            evidence.add("matches", matches);
            evidence.addProperty("matchCount", matches.size());
            return result(condition, evidence, passed, "Entity condition");
        });
    }

    private CompletableFuture<JsonObject> menu(JsonObject condition) {
        return this.service.uiTree().thenApply(tree -> {
            boolean passed = matchesExpected(tree, expected(condition));
            if (condition.has("menuId")) {
                passed &= tree.has("menuId") && tree.get("menuId").getAsInt() == condition.get("menuId").getAsInt();
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
                            && !string(node, "item", "").equals(condition.get("itemId").getAsString())) continue;
                    if (condition.has("countMin")
                            && number(node, "count", 0.0) < condition.get("countMin").getAsDouble()) continue;
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
        if (condition.has("params")) request.add("params", condition.get("params").deepCopy());
        return this.observation.read(request).thenApply(value -> result(
                condition, value, matchesExpected(value, expected(condition)), "Provider condition"));
    }

    @Override
    public void close() {
        this.scheduler.shutdownNow();
    }

    private static JsonObject result(
            JsonObject condition, JsonObject evidence, boolean passed, String subject) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "assert.result");
        json.add("condition", condition.deepCopy());
        json.addProperty("passed", passed);
        json.addProperty("message", subject + (passed ? " satisfied" : " is not satisfied"));
        json.add("evidence", evidence.deepCopy());
        return json;
    }

    private static JsonObject expected(JsonObject condition) {
        return condition.has("expected") && condition.get("expected").isJsonObject()
                ? condition.getAsJsonObject("expected") : new JsonObject();
    }

    static List<JsonObject> findMatches(JsonObject tree, JsonObject selector) {
        List<JsonObject> matches = new ArrayList<>();
        if (!tree.has("children")) return matches;
        boolean caseSensitive = bool(selector, "caseSensitive", false);
        for (JsonElement element : tree.getAsJsonArray("children")) {
            JsonObject node = element.getAsJsonObject();
            if (matches(node, selector, caseSensitive)) matches.add(node);
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
        return !bool(selector, "activeOnly", false) || !node.has("active") || node.get("active").getAsBoolean();
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
            if (!actual.has(entry.getKey()) || !same(actual.get(entry.getKey()), entry.getValue())) return false;
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

    private static boolean close(JsonObject actual, JsonObject expected, String key, double tolerance) {
        return !expected.has(key) || (actual.has(key)
                && Math.abs(actual.get(key).getAsDouble() - expected.get(key).getAsDouble()) <= tolerance);
    }

    private static int requiredInt(JsonObject json, String name) {
        if (!json.has(name)) throw new ProtocolState.ProtocolException(
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
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : fallback;
    }

    private static double number(JsonObject json, String name, double fallback) {
        return json.has(name) ? json.get(name).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject json, String name, boolean fallback) {
        return json.has(name) ? json.get(name).getAsBoolean() : fallback;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) current = current.getCause();
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
