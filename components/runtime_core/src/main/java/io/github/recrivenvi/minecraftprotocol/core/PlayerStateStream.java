package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Optional;

/** Client-tick pose projection; EventHub owns transport, sequence, replay and backpressure. */
public final class PlayerStateStream implements AutoCloseable {
    public static final String EVENT_TYPE = "world.player.state";
    public static final String EVENT_CATEGORY = "world";
    private static final String MARKER = "client_player_pose";
    private Context context;
    private long highestGeneration = -1L;
    private long lastSourceTick = -1L;
    private long sampleCount;
    private boolean closed;

    public record Context(long generation, String opaqueId) {
        public Context {
            if (generation < 0L) throw new IllegalArgumentException("Negative context generation");
            Objects.requireNonNull(opaqueId, "opaqueId");
            if (opaqueId.isBlank() || opaqueId.length() > 128)
                throw new IllegalArgumentException("Invalid opaque stream context identity");
        }
    }

    public record Sample(
            long sourceTick,
            long sampledAtMillis,
            double x,
            double y,
            double z,
            double yaw,
            double pitch,
            boolean onGround,
            double velocityX,
            double velocityY,
            double velocityZ) {
        public Sample {
            if (sourceTick < 0L || sampledAtMillis < 0L)
                throw new IllegalArgumentException("Negative sample tick or time");
            for (double value : new double[] {x, y, z, yaw, pitch, velocityX, velocityY, velocityZ})
                if (!Double.isFinite(value))
                    throw new IllegalArgumentException("Player pose must contain finite values");
        }
    }

    /** The caller derives a new generation from real level/player/connection replacement. */
    public synchronized void setContext(Context next) {
        if (closed) throw new IllegalStateException("Player state stream is closed");
        if (Objects.equals(context, next)) return;
        if (next != null) {
            if (next.generation() <= highestGeneration)
                throw new IllegalArgumentException("Stream context generation must advance");
            highestGeneration = next.generation();
        }
        context = next;
        lastSourceTick = -1L;
    }

    /** At most one authorized frame per native client tick; no independent timer is created. */
    public Optional<JsonObject> sample(
            Context capturedContext, Sample captured, Runnable authorize) {
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(authorize, "authorize");
        synchronized (this) {
            if (!eligible(capturedContext, captured.sourceTick())) return Optional.empty();
        }
        // Authorization can take external state locks or invalidate this context reentrantly.
        authorize.run();
        synchronized (this) {
            if (!eligible(capturedContext, captured.sourceTick())) return Optional.empty();
            JsonObject event = project(capturedContext, captured);
            lastSourceTick = captured.sourceTick();
            sampleCount++;
            return Optional.of(event);
        }
    }

    /**
     * Counts authorized projections, not WebSocket delivery, acknowledgements or server effects.
     */
    public synchronized long sampleCount() {
        return sampleCount;
    }

    /** Non-state events remain subject to EventHub's existing independent policy. */
    public synchronized boolean current(JsonObject event) {
        if (!isStateEvent(event)) return true;
        if (closed || context == null || !MARKER.equals(string(event, "stateStream"))) return false;
        JsonElement value = event.get("streamContext");
        if (value == null || !value.isJsonObject()) return false;
        JsonObject identity = value.getAsJsonObject();
        JsonElement generation = identity.get("generation");
        if (generation == null
                || !generation.isJsonPrimitive()
                || !generation.getAsJsonPrimitive().isNumber()) return false;
        try {
            return generation.getAsBigDecimal().longValueExact() == context.generation()
                    && context.opaqueId().equals(string(identity, "opaqueId"));
        } catch (ArithmeticException | NumberFormatException malformed) {
            return false;
        }
    }

    public static boolean isStateEvent(JsonObject event) {
        return event != null && EVENT_TYPE.equals(string(event, "type"));
    }

    @Override
    public synchronized void close() {
        closed = true;
        context = null;
        lastSourceTick = -1L;
    }

    private boolean eligible(Context capturedContext, long sourceTick) {
        return !closed
                && context != null
                && context.equals(capturedContext)
                && sourceTick > lastSourceTick;
    }

    private static JsonObject project(Context context, Sample sample) {
        JsonObject identity = new JsonObject();
        identity.addProperty("generation", context.generation());
        identity.addProperty("opaqueId", context.opaqueId());
        JsonObject event = new JsonObject();
        event.addProperty("type", EVENT_TYPE);
        event.addProperty("category", EVENT_CATEGORY);
        event.addProperty("stateStream", MARKER);
        event.add("streamContext", identity);
        event.addProperty("domain", "WORLD");
        event.addProperty("dataCategory", "USER");
        event.addProperty("authority", "CLIENT_KNOWN");
        event.addProperty("source", "client_tick_state");
        event.addProperty("dataSource", "LIVE");
        event.addProperty("storageAccessed", false);
        event.addProperty("serverAuthoritative", false);
        event.addProperty("sourceTick", sample.sourceTick());
        event.addProperty("sampledAtMillis", sample.sampledAtMillis());
        event.addProperty("x", sample.x());
        event.addProperty("y", sample.y());
        event.addProperty("z", sample.z());
        event.addProperty("yaw", sample.yaw());
        event.addProperty("pitch", sample.pitch());
        event.addProperty("onGround", sample.onGround());
        event.addProperty("velocityX", sample.velocityX());
        event.addProperty("velocityY", sample.velocityY());
        event.addProperty("velocityZ", sample.velocityZ());
        return event;
    }

    private static String string(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }
}
