package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded elapsed measurements, not an acknowledgement of Minecraft or server effects. */
final class PipelineTiming {
    private final LongSupplier clock;
    private final long created;
    private final Map<String, Long> elapsed = new LinkedHashMap<>();
    private final Map<String, Integer> calls = new LinkedHashMap<>();
    private long queueNanos;
    private long requestedDelayMillis;
    private long firstInput = -1;
    private long lastInput;
    private int inputCallbacks;

    PipelineTiming() {
        this(System::nanoTime);
    }

    PipelineTiming(LongSupplier clock) {
        this.clock = clock;
        this.created = clock.getAsLong();
    }

    long now() {
        return clock.getAsLong();
    }

    synchronized void started() {
        queueNanos = Math.max(0, now() - created);
    }

    synchronized void record(String stage, long started) {
        elapsed.merge(stage, Math.max(0, now() - started), Long::sum);
        calls.merge(stage, 1, Integer::sum);
    }

    synchronized void requestedDelay(long millis) {
        requestedDelayMillis += Math.max(0, millis);
    }

    synchronized void inputCallback() {
        long offset = Math.max(0, now() - created);
        if (firstInput < 0) firstInput = offset;
        lastInput = offset;
        inputCallbacks++;
    }

    synchronized JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("timeSource", "monotonic_elapsed");
        result.addProperty("scope", "automation_pipeline_not_http_or_server_effect");
        result.addProperty("queueMillis", milliseconds(queueNanos));
        result.addProperty("totalMillis", milliseconds(Math.max(0, now() - created)));
        result.addProperty("requestedDelayMillis", requestedDelayMillis);
        result.addProperty("inputCallbackCount", inputCallbacks);
        JsonObject stages = new JsonObject();
        elapsed.forEach(
                (name, nanos) -> {
                    JsonObject stage = new JsonObject();
                    stage.addProperty("elapsedMillis", milliseconds(nanos));
                    stage.addProperty("calls", calls.get(name));
                    stages.add(name, stage);
                });
        result.add("stages", stages);
        if (firstInput >= 0) {
            result.addProperty("firstInputCallbackOffsetMillis", milliseconds(firstInput));
            result.addProperty("lastInputCallbackOffsetMillis", milliseconds(lastInput));
        }
        return result;
    }

    static double milliseconds(long nanos) {
        return nanos / 1_000_000.0;
    }
}
