package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class SecurityGate {
    static final AttributeKey<String> CONNECTION_ID =
            AttributeKey.valueOf("minecraft-protocol-connection-id");
    private static final int MAX_CONNECTIONS = 32;
    private final String principalId;
    private final LongSupplier nanoTime;
    private final TokenBucket principalRequests;
    private final Map<String, TokenBucket> principalExpensive = new ConcurrentHashMap<>();
    private final Map<String, ConnectionBudget> connections = new ConcurrentHashMap<>();

    SecurityGate(String principalId) {
        this(principalId, System::nanoTime);
    }

    SecurityGate(String principalId, LongSupplier nanoTime) {
        this.principalId = principalId;
        this.nanoTime = nanoTime;
        this.principalRequests = new TokenBucket(240.0, 120.0, nanoTime);
    }

    String principalId() {
        return this.principalId;
    }

    String connectionId(Channel channel) {
        String existing = channel.attr(CONNECTION_ID).get();
        if (existing != null) return existing;
        if (this.connections.size() >= MAX_CONNECTIONS) {
            throw new ProtocolState.ProtocolException(
                    "CONNECTION_LIMIT_EXCEEDED", 429, "Runtime connection limit exceeded");
        }
        String created = UUID.randomUUID().toString();
        channel.attr(CONNECTION_ID).set(created);
        this.connections.put(created, new ConnectionBudget(this.nanoTime));
        return created;
    }

    void admit(Channel channel, String method, String path) {
        String connectionId = this.connectionId(channel);
        ConnectionBudget connection =
                this.connections.computeIfAbsent(
                        connectionId, ignored -> new ConnectionBudget(this.nanoTime));
        String category = category(method, path);
        // Preserve admission order and charging: a later rejection does not refund earlier buckets.
        this.principalRequests.acquire("principal", "requests", category);
        connection.requests.acquire("connection", "requests", category);
        TokenBucket principalCategory =
                this.principalExpensive.computeIfAbsent(category, this::bucket);
        TokenBucket connectionCategory =
                connection.expensive.computeIfAbsent(category, this::bucket);
        principalCategory.acquire("principal", category, category);
        connectionCategory.acquire("connection", category, category);
    }

    void remove(Channel channel) {
        String id = channel.attr(CONNECTION_ID).getAndSet(null);
        if (id != null) this.connections.remove(id);
    }

    void admitPeer(String operation) {
        String category =
                operation.startsWith("debug.") && !operation.equals("debug.capabilities")
                                || operation.startsWith("fixture.")
                        ? "debug"
                        : "world";
        principalRequests.acquire("principal", "requests", category);
        principalExpensive
                .computeIfAbsent(category, this::bucket)
                .acquire("principal", category, category);
    }

    int connectionCount() {
        return this.connections.size();
    }

    private static String category(String method, String path) {
        if (path.contains("/artifact")) return "artifact";
        if (path.startsWith("/v0/capture")) return "capture";
        if (path.startsWith("/v0/recordings") && (method.equals("POST") || method.equals("DELETE")))
            return "recording";
        if (path.startsWith("/v0/world") || path.startsWith("/v0/server/world")) return "world";
        if (path.startsWith("/v0/debug")) return "debug";
        if (path.startsWith("/v0/pipelines")) return "pipeline";
        if (path.startsWith("/v0/events")) return "event";
        return "ordinary";
    }

    private TokenBucket bucket(String category) {
        return switch (category) {
            case "artifact" -> new TokenBucket(2.0, 0.2, this.nanoTime);
            case "capture" -> new TokenBucket(8.0, 4.0, this.nanoTime);
            case "recording" -> new TokenBucket(12.0, 1.0, this.nanoTime);
            case "world" -> new TokenBucket(60.0, 30.0, this.nanoTime);
            case "debug" -> new TokenBucket(10.0, 2.0, this.nanoTime);
            case "pipeline" -> new TokenBucket(8.0, 2.0, this.nanoTime);
            case "event" -> new TokenBucket(30.0, 10.0, this.nanoTime);
            default -> new TokenBucket(120.0, 60.0, this.nanoTime);
        };
    }

    private static final class ConnectionBudget {
        private final TokenBucket requests;
        private final Map<String, TokenBucket> expensive = new ConcurrentHashMap<>();

        private ConnectionBudget(LongSupplier nanoTime) {
            this.requests = new TokenBucket(120.0, 60.0, nanoTime);
        }
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private final LongSupplier nanoTime;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(double capacity, double refillPerSecond, LongSupplier nanoTime) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.nanoTime = nanoTime;
            this.tokens = capacity;
            this.lastRefillNanos = nanoTime.getAsLong();
        }

        synchronized void acquire(String scope, String category, String operationCategory) {
            long now = this.nanoTime.getAsLong();
            double elapsed = Math.max(0L, now - this.lastRefillNanos) / 1_000_000_000.0;
            this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.refillPerSecond);
            this.lastRefillNanos = now;
            if (this.tokens < 1.0) {
                JsonObject budget = new JsonObject();
                budget.addProperty("scope", scope);
                budget.addProperty("category", category);
                budget.addProperty("operationCategory", operationCategory);
                budget.addProperty("capacity", (long) this.capacity);
                budget.addProperty("remaining", (long) Math.floor(this.tokens));
                budget.addProperty("refillPerSecond", this.refillPerSecond);
                budget.addProperty("cost", 1);
                JsonObject details = new JsonObject();
                details.add("rateLimit", budget);
                details.addProperty(
                        "retryAfterMillis",
                        Math.max(
                                1L,
                                (long)
                                        Math.ceil(
                                                (1.0 - this.tokens)
                                                        * 1000.0
                                                        / this.refillPerSecond)));
                boolean ordinary = category.equals("requests");
                throw new ProtocolState.ProtocolException(
                        ordinary ? "RATE_LIMITED" : "EXPENSIVE_OPERATION_RATE_LIMITED",
                        429,
                        ordinary
                                ? "Request rate budget exceeded"
                                : "Rate budget exceeded for expensive category: " + category,
                        details);
            }
            this.tokens -= 1.0;
        }
    }
}
