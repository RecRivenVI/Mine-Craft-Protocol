package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class EventHub implements AutoCloseable {
    private static final Gson GSON = new Gson();
    static final int RING_CAPACITY = 1024;
    static final int CLIENT_QUEUE_CAPACITY = 128;
    private final String target;
    private final Supplier<CompletableFuture<JsonObject>> resyncSupplier;
    private final AtomicLong sequence = new AtomicLong();
    private final Deque<EventRecord> ring = new ArrayDeque<>();
    private final Map<Channel, ClientSubscription> subscriptions = new ConcurrentHashMap<>();

    EventHub(String target, Supplier<CompletableFuture<JsonObject>> resyncSupplier) {
        this.target = target;
        this.resyncSupplier = resyncSupplier;
    }

    JsonObject publish(JsonObject source) {
        JsonObject event = source.deepCopy();
        long next = this.sequence.incrementAndGet();
        event.addProperty("sequence", next);
        if (!event.has("timestampMillis")) event.addProperty("timestampMillis", System.currentTimeMillis());
        EventRecord record = new EventRecord(next, event);
        synchronized (this.ring) {
            this.ring.addLast(record);
            while (this.ring.size() > RING_CAPACITY) this.ring.removeFirst();
        }
        for (ClientSubscription subscription : this.subscriptions.values()) subscription.offer(record);
        return event;
    }

    JsonObject register(Channel channel, QueryStringDecoder requestUri) {
        long resumeFrom = parseLong(requestUri, "resumeFromSequence", this.sequence.get());
        EventFilter filter = EventFilter.fromQuery(requestUri.parameters());
        ClientSubscription subscription = new ClientSubscription(channel, filter);
        ClientSubscription old = this.subscriptions.put(channel, subscription);
        if (old != null) old.close();
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "event.hello");
        hello.addProperty("target", this.target);
        hello.addProperty("sequence", this.sequence.get());
        hello.addProperty("ringCapacity", RING_CAPACITY);
        hello.addProperty("clientQueueCapacity", CLIENT_QUEUE_CAPACITY);
        hello.add("filter", filter.toJson());
        channel.eventLoop().execute(() -> channel.writeAndFlush(new TextWebSocketFrame(GSON.toJson(hello)))
                .addListener(ignored -> subscription.resume(resumeFrom)));
        return hello;
    }

    void unregister(Channel channel) {
        ClientSubscription subscription = this.subscriptions.remove(channel);
        if (subscription != null) subscription.close();
    }

    void channelWritable(Channel channel) {
        ClientSubscription subscription = this.subscriptions.get(channel);
        if (subscription != null) subscription.scheduleFlush();
    }

    void accept(Channel channel, String payload) {
        ClientSubscription subscription = this.subscriptions.get(channel);
        if (subscription == null) return;
        JsonObject command;
        try {
            command = JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException exception) {
            subscription.control(error("INVALID_EVENT_COMMAND", "WebSocket command must be a JSON object"));
            return;
        }
        String type = string(command, "type", "");
        switch (type) {
            case "event.subscribe" -> {
                subscription.filter = EventFilter.fromJson(command.has("filter")
                        ? command.getAsJsonObject("filter") : new JsonObject());
                JsonObject accepted = new JsonObject();
                accepted.addProperty("type", "event.subscription.accepted");
                accepted.addProperty("sequence", this.sequence.get());
                accepted.add("filter", subscription.filter.toJson());
                subscription.control(accepted);
            }
            case "event.ack" -> subscription.ack(Math.max(0L, longValue(command, "sequence", 0L)));
            case "event.resume" -> subscription.resume(Math.max(0L, longValue(command, "resumeFromSequence", 0L)));
            case "event.resync" -> this.resyncSupplier.get().whenComplete((snapshot, failure) -> {
                if (failure != null) {
                    subscription.control(error("RESYNC_FAILED", failure.getMessage()));
                    return;
                }
                JsonObject event = new JsonObject();
                event.addProperty("type", "event.resync.snapshot");
                event.addProperty("sequence", this.sequence.get());
                event.addProperty("fullResyncRequired", false);
                event.add("snapshot", snapshot);
                subscription.clearGap();
                subscription.control(event);
            });
            default -> subscription.control(error("UNSUPPORTED_EVENT_COMMAND", "Unsupported event command: " + type));
        }
    }

    long currentSequence() {
        return this.sequence.get();
    }

    int subscriptionCount() {
        return this.subscriptions.size();
    }

    JsonObject evaluateCondition(JsonObject condition) {
        long after = Math.max(0L, longValue(condition, "afterSequence", 0L));
        String expectedType = string(condition, "eventType", "");
        String expectedCategory = string(condition, "category", "");
        EventRecord match = null;
        synchronized (this.ring) {
            for (EventRecord event : this.ring) {
                if (event.sequence() <= after) continue;
                if (!expectedType.isBlank() && !expectedType.equals(string(event.payload(), "type", ""))) continue;
                if (!expectedCategory.isBlank()
                        && !expectedCategory.equals(string(event.payload(), "category", ""))) continue;
                match = event;
                break;
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "assert.result");
        result.addProperty("passed", match != null);
        result.addProperty("message", match == null ? "Matching event has not been observed" : "Event condition satisfied");
        result.addProperty("currentSequence", this.sequence.get());
        result.addProperty("oldestAvailableSequence", this.oldestSequence());
        result.add("condition", condition.deepCopy());
        if (match != null) result.add("evidence", match.payload().deepCopy());
        return result;
    }

    @Override
    public void close() {
        for (ClientSubscription subscription : this.subscriptions.values()) subscription.close();
        this.subscriptions.clear();
        synchronized (this.ring) {
            this.ring.clear();
        }
    }

    private List<EventRecord> replayAfter(long resumeFrom) {
        synchronized (this.ring) {
            if (this.ring.isEmpty()) return List.of();
            return this.ring.stream().filter(event -> event.sequence() > resumeFrom).toList();
        }
    }

    private long oldestSequence() {
        synchronized (this.ring) {
            return this.ring.isEmpty() ? this.sequence.get() + 1L : this.ring.getFirst().sequence();
        }
    }

    private final class ClientSubscription {
        private final Channel channel;
        private final Deque<EventRecord> pending = new ArrayDeque<>();
        private volatile EventFilter filter;
        private boolean flushScheduled;
        private boolean closed;
        private long lastAck;
        private long lastDelivered;
        private long gapFrom = -1L;
        private long gapTo = -1L;
        private boolean fullResyncRequired;

        private ClientSubscription(Channel channel, EventFilter filter) {
            this.channel = channel;
            this.filter = filter;
        }

        void resume(long resumeFrom) {
            synchronized (this) {
                if (this.closed) return;
                this.pending.clear();
                this.lastAck = resumeFrom;
                long oldest = oldestSequence();
                if (resumeFrom < oldest - 1L) {
                    this.markGap(resumeFrom + 1L, oldest - 1L, true);
                } else {
                    for (EventRecord event : replayAfter(resumeFrom)) this.offerLocked(event);
                }
            }
            this.scheduleFlush();
        }

        void offer(EventRecord event) {
            synchronized (this) {
                if (this.closed || !this.filter.matches(event.payload())) return;
                this.offerLocked(event);
            }
            this.scheduleFlush();
        }

        private void offerLocked(EventRecord event) {
            if (this.pending.size() >= CLIENT_QUEUE_CAPACITY) {
                EventRecord dropped = this.pending.removeFirst();
                this.markGap(dropped.sequence(), dropped.sequence(), true);
            }
            this.pending.addLast(event);
        }

        void control(JsonObject message) {
            if (!this.channel.isActive()) return;
            this.channel.eventLoop().execute(() -> {
                if (this.channel.isActive()) this.channel.writeAndFlush(new TextWebSocketFrame(GSON.toJson(message)));
            });
        }

        void ack(long acknowledged) {
            synchronized (this) {
                this.lastAck = Math.max(this.lastAck, Math.min(acknowledged, this.lastDelivered));
            }
        }

        void clearGap() {
            synchronized (this) {
                this.gapFrom = -1L;
                this.gapTo = -1L;
                this.fullResyncRequired = false;
            }
        }

        void scheduleFlush() {
            synchronized (this) {
                if (this.closed || this.flushScheduled || !this.channel.isActive()) return;
                this.flushScheduled = true;
            }
            this.channel.eventLoop().execute(this::flush);
        }

        private void flush() {
            int sent = 0;
            while (this.channel.isActive() && this.channel.isWritable() && sent < 32) {
                String payload;
                synchronized (this) {
                    if (this.closed) break;
                    if (this.gapFrom >= 0L) {
                        JsonObject gap = new JsonObject();
                        gap.addProperty("type", "event.gap");
                        gap.addProperty("fromSequence", this.gapFrom);
                        gap.addProperty("toSequence", this.gapTo);
                        gap.addProperty("fullResyncRequired", this.fullResyncRequired);
                        payload = GSON.toJson(gap);
                        this.gapFrom = -1L;
                        this.gapTo = -1L;
                    } else {
                        EventRecord event = this.pending.pollFirst();
                        if (event == null) break;
                        this.lastDelivered = event.sequence();
                        payload = GSON.toJson(event.payload());
                    }
                }
                this.channel.write(new TextWebSocketFrame(payload));
                sent++;
            }
            if (sent > 0) this.channel.flush();
            synchronized (this) {
                this.flushScheduled = false;
                if (!this.closed && this.channel.isActive() && this.channel.isWritable()
                        && (this.gapFrom >= 0L || !this.pending.isEmpty())) this.scheduleFlush();
            }
        }

        private void markGap(long from, long to, boolean resync) {
            this.gapFrom = this.gapFrom < 0L ? from : Math.min(this.gapFrom, from);
            this.gapTo = Math.max(this.gapTo, to);
            this.fullResyncRequired |= resync;
        }

        void close() {
            synchronized (this) {
                this.closed = true;
                this.pending.clear();
            }
        }
    }

    private record EventRecord(long sequence, JsonObject payload) {
    }

    private record EventFilter(
            Set<String> types,
            Set<String> namespaces,
            Set<String> categories,
            Set<String> recordingIds,
            Set<String> pipelineRunIds,
            Set<String> screens,
            Set<String> entities) {
        static EventFilter fromQuery(Map<String, List<String>> values) {
            return new EventFilter(
                    querySet(values, "type"), querySet(values, "namespace"), querySet(values, "category"),
                    querySet(values, "recordingId"), querySet(values, "pipelineRunId"),
                    querySet(values, "screen"), querySet(values, "entity"));
        }

        static EventFilter fromJson(JsonObject json) {
            return new EventFilter(
                    jsonSet(json, "types"), jsonSet(json, "namespaces"), jsonSet(json, "categories"),
                    jsonSet(json, "recordingIds"), jsonSet(json, "pipelineRunIds"),
                    jsonSet(json, "screens"), jsonSet(json, "entities"));
        }

        boolean matches(JsonObject event) {
            String type = string(event, "type", "");
            String namespace = type.contains(".") ? type.substring(0, type.indexOf('.')) : type;
            return accepts(this.types, type)
                    && accepts(this.namespaces, namespace)
                    && accepts(this.categories, string(event, "category", ""))
                    && accepts(this.recordingIds, string(event, "recordingId", ""))
                    && accepts(this.pipelineRunIds, string(event, "pipelineRunId", ""))
                    && accepts(this.screens, string(event, "screen", string(event, "screenClass", "")))
                    && accepts(this.entities, string(event, "entity", string(event, "entityUuid", "")));
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.add("types", array(this.types));
            json.add("namespaces", array(this.namespaces));
            json.add("categories", array(this.categories));
            json.add("recordingIds", array(this.recordingIds));
            json.add("pipelineRunIds", array(this.pipelineRunIds));
            json.add("screens", array(this.screens));
            json.add("entities", array(this.entities));
            return json;
        }
    }

    private static boolean accepts(Set<String> accepted, String value) {
        return accepted.isEmpty() || accepted.contains(value);
    }

    private static Set<String> querySet(Map<String, List<String>> values, String key) {
        Set<String> result = new HashSet<>();
        for (String value : values.getOrDefault(key, List.of())) {
            for (String item : value.split(",")) if (!item.isBlank()) result.add(item.trim());
        }
        return Set.copyOf(result);
    }

    private static Set<String> jsonSet(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        JsonElement value = json.get(key);
        Set<String> result = new HashSet<>();
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) result.add(item.getAsString());
        } else {
            result.add(value.getAsString());
        }
        return Set.copyOf(result);
    }

    private static JsonArray array(Set<String> values) {
        JsonArray json = new JsonArray();
        values.stream().sorted().forEach(json::add);
        return json;
    }

    private static JsonObject error(String code, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "event.error");
        json.addProperty("error", code);
        json.addProperty("message", message == null ? "unknown" : message);
        return json;
    }

    private static long parseLong(QueryStringDecoder uri, String name, long fallback) {
        List<String> values = uri.parameters().get(name);
        if (values == null || values.isEmpty()) return fallback;
        try {
            return Long.parseLong(values.get(0));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject json, String name, long fallback) {
        return json.has(name) ? json.get(name).getAsLong() : fallback;
    }

    private static String string(JsonObject json, String name, String fallback) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : fallback;
    }
}
