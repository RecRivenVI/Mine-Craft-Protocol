package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class EventHub implements AutoCloseable {
    private static final Gson GSON = new Gson();
    static final int RING_CAPACITY = 1024;
    static final int CLIENT_QUEUE_CAPACITY = 128;
    private final String target;
    private final Supplier<CompletableFuture<JsonObject>> resyncSupplier;
    private final Runnable authorize;
    private final Predicate<JsonObject> current;
    private final AtomicLong sequence = new AtomicLong();
    private final Deque<EventRecord> ring = new ArrayDeque<>();
    private final Map<Channel, ClientSubscription> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    EventHub(String target, Supplier<CompletableFuture<JsonObject>> resyncSupplier) {
        this(target, resyncSupplier, () -> {});
    }

    EventHub(
            String target,
            Supplier<CompletableFuture<JsonObject>> resyncSupplier,
            Runnable authorize) {
        this(target, resyncSupplier, authorize, ignored -> true);
    }

    EventHub(
            String target,
            Supplier<CompletableFuture<JsonObject>> resyncSupplier,
            Runnable authorize,
            Predicate<JsonObject> current) {
        this.target = target;
        this.resyncSupplier = resyncSupplier;
        this.authorize = authorize;
        this.current = current;
    }

    JsonObject publish(JsonObject source) {
        authorize.run();
        if (this.closed) return null;
        JsonObject event = source.deepCopy();
        if (!this.current.test(event)) return null;
        authorize.run();
        EventRecord record;
        synchronized (this.ring) {
            // This predicate only compares local context identity; it must not authorize or
            // acquire ProtocolState/ControlSession. External authorization stays outside locks.
            if (this.closed || !this.current.test(event)) return null;
            long next = this.sequence.incrementAndGet();
            event.addProperty("sequence", next);
            if (!event.has("timestampMillis"))
                event.addProperty("timestampMillis", System.currentTimeMillis());
            record = new EventRecord(next, event);
            this.ring.addLast(record);
            while (this.ring.size() > RING_CAPACITY) this.ring.removeFirst();
        }
        for (ClientSubscription subscription : this.subscriptions.values())
            subscription.offer(record);
        return event;
    }

    JsonObject register(Channel channel, QueryStringDecoder requestUri) {
        authorize.run();
        if (this.closed) throw new IllegalStateException("Event hub is closed");
        long resumeFrom = parseLong(requestUri, "resumeFromSequence", this.sequence.get());
        EventFilter filter = EventFilter.fromQuery(requestUri.parameters());
        ClientSubscription subscription = new ClientSubscription(channel, filter);
        ClientSubscription old = this.subscriptions.put(channel, subscription);
        if (old != null) old.close();
        if (this.closed) {
            unregister(channel);
            throw new IllegalStateException("Event hub is closed");
        }
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "event.hello");
        hello.addProperty("target", this.target);
        hello.addProperty("sequence", this.sequence.get());
        hello.addProperty("ringCapacity", RING_CAPACITY);
        hello.addProperty("clientQueueCapacity", CLIENT_QUEUE_CAPACITY);
        hello.add("filter", filter.toJson());
        channel.eventLoop()
                .execute(
                        () -> {
                            try {
                                authorize.run();
                            } catch (RuntimeException denied) {
                                subscription.close();
                                channel.close();
                                return;
                            }
                            if (!subscription.closed && channel.isActive())
                                channel.writeAndFlush(new TextWebSocketFrame(GSON.toJson(hello)))
                                        .addListener(ignored -> subscription.resume(resumeFrom));
                        });
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
        try {
            authorize.run();
        } catch (RuntimeException denied) {
            unregister(channel);
            channel.close();
            return;
        }
        JsonObject command;
        try {
            command = JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException exception) {
            subscription.control(
                    error("INVALID_EVENT_COMMAND", "WebSocket command must be a JSON object"));
            return;
        }
        String type = string(command, "type", "");
        switch (type) {
            case "event.subscribe" -> {
                subscription.filter =
                        EventFilter.fromJson(
                                command.has("filter")
                                        ? command.getAsJsonObject("filter")
                                        : new JsonObject());
                JsonObject accepted = new JsonObject();
                accepted.addProperty("type", "event.subscription.accepted");
                accepted.addProperty("sequence", this.sequence.get());
                accepted.add("filter", subscription.filter.toJson());
                subscription.control(accepted);
            }
            case "event.ack" -> subscription.ack(Math.max(0L, longValue(command, "sequence", 0L)));
            case "event.resume" ->
                    subscription.resume(Math.max(0L, longValue(command, "resumeFromSequence", 0L)));
            case "event.resync" -> subscription.resync();
            default ->
                    subscription.control(
                            error(
                                    "UNSUPPORTED_EVENT_COMMAND",
                                    "Unsupported event command: " + type));
        }
    }

    long currentSequence() {
        return this.sequence.get();
    }

    int subscriptionCount() {
        return this.subscriptions.size();
    }

    boolean wantsPlayerState() {
        return wantsWorldEvent(PlayerStateStream.EVENT_TYPE);
    }

    boolean wantsWorldEvent(String type) {
        if (this.closed) return false;
        JsonObject pose = new JsonObject();
        pose.addProperty("type", type);
        pose.addProperty("category", PlayerStateStream.EVENT_CATEGORY);
        for (ClientSubscription subscription : this.subscriptions.values())
            if (!subscription.closed
                    && subscription.channel.isActive()
                    && subscription.filter.matches(pose)) return true;
        return false;
    }

    JsonObject evaluateCondition(JsonObject condition) {
        authorize.run();
        long after = Math.max(0L, longValue(condition, "afterSequence", 0L));
        String expectedType = string(condition, "eventType", "");
        String expectedCategory = string(condition, "eventCategory", "");
        EventRecord match = null;
        boolean stale = false;
        for (EventRecord event : this.replayAfter(after)) {
            if (!expectedType.isBlank()
                    && !expectedType.equals(string(event.payload(), "type", ""))) continue;
            if (!expectedCategory.isBlank()
                    && !expectedCategory.equals(string(event.payload(), "category", ""))) continue;
            if (!this.current.test(event.payload())) {
                stale = true;
                continue;
            }
            match = event;
            break;
        }
        authorize.run();
        if (match != null && !this.current.test(match.payload())) {
            stale = true;
            match = null;
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "assert.result");
        result.addProperty("passed", match != null);
        result.addProperty(
                "message",
                match == null
                        ? "Matching event has not been observed"
                        : "Event condition satisfied");
        result.addProperty("currentSequence", this.sequence.get());
        result.addProperty("oldestAvailableSequence", this.oldestSequence());
        result.add("condition", condition.deepCopy());
        if (stale) result.addProperty("fullResyncRequired", true);
        if (match != null) result.add("evidence", match.payload().deepCopy());
        return result;
    }

    void validateConditionEvidence(JsonObject result) {
        authorize.run();
        JsonElement evidence = result == null ? null : result.get("evidence");
        if (evidence == null || !evidence.isJsonObject()) return;
        JsonObject event = evidence.getAsJsonObject();
        if (!this.current.test(event))
            throw new ProtocolState.ProtocolException(
                    "EVENT_CONTEXT_CHANGED",
                    409,
                    "Event context or read authority changed before condition evidence publication");
    }

    @Override
    public void close() {
        this.closed = true;
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
        private volatile boolean closed;
        private ResyncAttempt pendingResync;
        private long lastAck;
        private long lastDelivered;
        private long gapFrom = -1L;
        private long gapTo = -1L;
        private boolean fullResyncRequired;

        private ClientSubscription(Channel channel, EventFilter filter) {
            this.channel = channel;
            this.filter = filter;
        }

        void resync() {
            ResyncAttempt attempt = new ResyncAttempt();
            boolean busy;
            synchronized (this) {
                if (closed) return;
                busy = pendingResync != null;
                if (!busy) pendingResync = attempt;
            }
            if (busy) {
                control(error("RESYNC_BUSY", "One resync is already pending"));
                return;
            }
            CompletableFuture<JsonObject> work;
            try {
                authorize.run();
                work = java.util.Objects.requireNonNull(resyncSupplier.get());
            } catch (RuntimeException failure) {
                boolean owned;
                synchronized (this) {
                    owned = !closed && pendingResync == attempt;
                    if (pendingResync == attempt) pendingResync = null;
                }
                if (owned) control(error("RESYNC_FAILED", "Resync could not start"));
                return;
            }
            boolean abandoned;
            synchronized (this) {
                abandoned = closed || pendingResync != attempt;
                if (!abandoned) attempt.work = work;
            }
            if (abandoned) {
                work.cancel(false);
                return;
            }
            work.whenComplete(
                    (snapshot, failure) -> {
                        synchronized (this) {
                            if (closed || pendingResync != attempt) return;
                            pendingResync = null;
                        }
                        if (failure != null) {
                            control(
                                    error(
                                            "RESYNC_FAILED",
                                            "Resync failed or its read context changed"));
                            return;
                        }
                        JsonObject event = new JsonObject();
                        event.addProperty("type", "event.resync.snapshot");
                        event.addProperty("sequence", sequence.get());
                        event.addProperty("fullResyncRequired", false);
                        event.add("snapshot", snapshot);
                        control(event, true);
                    });
        }

        void resume(long resumeFrom) {
            authorize.run();
            long oldest;
            List<EventRecord> replay;
            synchronized (ring) {
                oldest = oldestSequence();
                replay = replayAfter(resumeFrom);
            }
            synchronized (this) {
                if (this.closed) return;
                this.pending.clear();
                this.lastAck = resumeFrom;
                if (resumeFrom < oldest - 1L) {
                    this.markGap(resumeFrom + 1L, oldest - 1L, true);
                } else {
                    for (EventRecord event : replay)
                        if (filter.matches(event.payload())) {
                            if (current.test(event.payload())) this.offerLocked(event);
                            else this.markGap(event.sequence(), event.sequence(), true);
                        }
                }
            }
            this.scheduleFlush();
        }

        void offer(EventRecord event) {
            synchronized (this) {
                if (this.closed || !this.filter.matches(event.payload())) return;
                if (current.test(event.payload())) this.offerLocked(event);
                else this.markGap(event.sequence(), event.sequence(), true);
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
            control(message, false);
        }

        void control(JsonObject message, boolean resync) {
            if (closed || !this.channel.isActive()) return;
            this.channel
                    .eventLoop()
                    .execute(
                            () -> {
                                try {
                                    authorize.run();
                                } catch (RuntimeException denied) {
                                    close();
                                    channel.close();
                                    return;
                                }
                                if (closed || !this.channel.isActive()) return;
                                if (!current.test(message)) {
                                    if (resync) {
                                        long cursor =
                                                longValue(message, "sequence", sequence.get());
                                        synchronized (this) {
                                            if (closed) return;
                                            markGap(cursor, cursor, true);
                                        }
                                        scheduleFlush();
                                    }
                                    return;
                                }
                                if (resync) clearGap();
                                this.channel.writeAndFlush(
                                        new TextWebSocketFrame(GSON.toJson(message)));
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
            try {
                authorize.run();
            } catch (RuntimeException denied) {
                close();
                channel.close();
                return;
            }
            int sent = 0;
            while (this.channel.isActive() && this.channel.isWritable() && sent < 32) {
                JsonObject payload;
                EventRecord record = null;
                synchronized (this) {
                    if (this.closed) break;
                    if (this.gapFrom >= 0L) {
                        JsonObject gap = new JsonObject();
                        gap.addProperty("type", "event.gap");
                        gap.addProperty("fromSequence", this.gapFrom);
                        gap.addProperty("toSequence", this.gapTo);
                        gap.addProperty("fullResyncRequired", this.fullResyncRequired);
                        payload = gap;
                        this.gapFrom = -1L;
                        this.gapTo = -1L;
                    } else {
                        EventRecord event = this.pending.pollFirst();
                        if (event == null) break;
                        if (!this.filter.matches(event.payload())) continue;
                        record = event;
                        payload = event.payload();
                    }
                }
                try {
                    authorize.run();
                } catch (RuntimeException denied) {
                    close();
                    channel.close();
                    return;
                }
                if (!current.test(payload)) {
                    if (record != null)
                        synchronized (this) {
                            this.markGap(record.sequence(), record.sequence(), true);
                        }
                    continue;
                }
                if (this.closed) break;
                if (record != null)
                    synchronized (this) {
                        this.lastDelivered = record.sequence();
                    }
                this.channel.write(new TextWebSocketFrame(GSON.toJson(payload)));
                sent++;
            }
            if (sent > 0) this.channel.flush();
            synchronized (this) {
                this.flushScheduled = false;
                if (!this.closed
                        && this.channel.isActive()
                        && this.channel.isWritable()
                        && (this.gapFrom >= 0L || !this.pending.isEmpty())) this.scheduleFlush();
            }
        }

        private void markGap(long from, long to, boolean resync) {
            this.gapFrom = this.gapFrom < 0L ? from : Math.min(this.gapFrom, from);
            this.gapTo = Math.max(this.gapTo, to);
            this.fullResyncRequired |= resync;
        }

        void close() {
            CompletableFuture<JsonObject> work;
            synchronized (this) {
                this.closed = true;
                this.pending.clear();
                work = pendingResync == null ? null : pendingResync.work;
                pendingResync = null;
            }
            if (work != null) work.cancel(false);
        }

        private final class ResyncAttempt {
            private CompletableFuture<JsonObject> work;
        }
    }

    private record EventRecord(long sequence, JsonObject payload) {}

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
                    querySet(values, "type"),
                    querySet(values, "namespace"),
                    querySet(values, "eventCategory"),
                    querySet(values, "recordingId"),
                    querySet(values, "pipelineRunId"),
                    querySet(values, "screen"),
                    querySet(values, "entity"));
        }

        static EventFilter fromJson(JsonObject json) {
            return new EventFilter(
                    jsonSet(json, "types"),
                    jsonSet(json, "namespaces"),
                    jsonSet(json, "categories"),
                    jsonSet(json, "recordingIds"),
                    jsonSet(json, "pipelineRunIds"),
                    jsonSet(json, "screens"),
                    jsonSet(json, "entities"));
        }

        boolean matches(JsonObject event) {
            String type = string(event, "type", "");
            String namespace = type.contains(".") ? type.substring(0, type.indexOf('.')) : type;
            if ((PlayerStateStream.isStateEvent(event) || type.equals("world.target.block"))
                    && !this.types.contains(type)
                    && !this.namespaces.contains("world")) return false;
            return accepts(this.types, type)
                    && accepts(this.namespaces, namespace)
                    && accepts(this.categories, string(event, "category", ""))
                    && accepts(this.recordingIds, string(event, "recordingId", ""))
                    && accepts(this.pipelineRunIds, string(event, "pipelineRunId", ""))
                    && accepts(
                            this.screens, string(event, "screen", string(event, "screenClass", "")))
                    && accepts(
                            this.entities,
                            string(event, "entity", string(event, "entityUuid", "")));
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
        return json.has(name) && !json.get(name).isJsonNull()
                ? json.get(name).getAsString()
                : fallback;
    }
}
