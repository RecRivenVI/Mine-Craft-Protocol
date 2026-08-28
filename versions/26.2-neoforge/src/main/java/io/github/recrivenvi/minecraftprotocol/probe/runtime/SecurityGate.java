package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SecurityGate {
    static final AttributeKey<String> CONNECTION_ID =
            AttributeKey.valueOf("minecraft-protocol-connection-id");
    private static final int MAX_CONNECTIONS = 32;
    private final String principalId;
    private final TokenBucket principalRequests = new TokenBucket(240.0, 120.0);
    private final Map<String, TokenBucket> principalExpensive = new ConcurrentHashMap<>();
    private final Map<String, ConnectionBudget> connections = new ConcurrentHashMap<>();

    SecurityGate(String principalId) {
        this.principalId = principalId;
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
        this.connections.put(created, new ConnectionBudget());
        return created;
    }

    void admit(Channel channel, String method, String path) {
        String connectionId = this.connectionId(channel);
        ConnectionBudget connection = this.connections.computeIfAbsent(connectionId, ignored -> new ConnectionBudget());
        if (!this.principalRequests.tryAcquire() || !connection.requests.tryAcquire()) {
            throw new ProtocolState.ProtocolException(
                    "RATE_LIMITED", 429, "Request rate budget exceeded");
        }
        String category = category(method, path);
        TokenBucket principalCategory = this.principalExpensive.computeIfAbsent(category, SecurityGate::bucket);
        TokenBucket connectionCategory = connection.expensive.computeIfAbsent(category, SecurityGate::bucket);
        if (!principalCategory.tryAcquire() || !connectionCategory.tryAcquire()) {
            throw new ProtocolState.ProtocolException(
                    "EXPENSIVE_OPERATION_RATE_LIMITED", 429,
                    "Rate budget exceeded for expensive category: " + category);
        }
    }

    void remove(Channel channel) {
        String id = channel.attr(CONNECTION_ID).getAndSet(null);
        if (id != null) this.connections.remove(id);
    }

    int connectionCount() {
        return this.connections.size();
    }

    private static String category(String method, String path) {
        if (path.contains("/artifact")) return "artifact";
        if (path.startsWith("/v0/capture")) return "capture";
        if (path.startsWith("/v0/recordings")
                && (method.equals("POST") || method.equals("DELETE"))) return "recording";
        if (path.startsWith("/v0/world") || path.startsWith("/v0/server/world")) return "world";
        if (path.startsWith("/v0/debug")) return "debug";
        if (path.startsWith("/v0/pipelines")) return "pipeline";
        if (path.startsWith("/v0/events")) return "event";
        return "ordinary";
    }

    private static TokenBucket bucket(String category) {
        return switch (category) {
            case "artifact" -> new TokenBucket(2.0, 0.2);
            case "capture" -> new TokenBucket(8.0, 4.0);
            case "recording" -> new TokenBucket(12.0, 1.0);
            case "world" -> new TokenBucket(60.0, 30.0);
            case "debug" -> new TokenBucket(10.0, 2.0);
            case "pipeline" -> new TokenBucket(8.0, 2.0);
            case "event" -> new TokenBucket(30.0, 10.0);
            default -> new TokenBucket(120.0, 60.0);
        };
    }

    private static final class ConnectionBudget {
        private final TokenBucket requests = new TokenBucket(120.0, 60.0);
        private final Map<String, TokenBucket> expensive = new ConcurrentHashMap<>();
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        private TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double elapsed = Math.max(0L, now - this.lastRefillNanos) / 1_000_000_000.0;
            this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.refillPerSecond);
            this.lastRefillNanos = now;
            if (this.tokens < 1.0) return false;
            this.tokens -= 1.0;
            return true;
        }
    }
}
