package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Bounded client chat sampling; temporal association is never execution acknowledgement. */
public final class PlayerCommandFeedback {
    public record Message(Object identity, String text) {}

    public static final int WINDOW_MILLIS = 500;
    private static final int MAX_MESSAGES = 16;
    private final LongSupplier clock;
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private JsonArray messages = new JsonArray();
    private long started;
    private boolean active;
    private boolean truncated;
    private boolean readable;

    public PlayerCommandFeedback() {
        this(System::nanoTime);
    }

    PlayerCommandFeedback(LongSupplier clock) {
        this.clock = clock;
    }

    public void begin(List<Message> baseline, boolean readable) {
        clear();
        baseline.stream().limit(100).forEach(message -> seen.add(message.identity()));
        this.readable = readable;
        started = clock.getAsLong();
        active = true;
    }

    public JsonObject poll(List<Message> newestFirst, boolean readable) {
        if (!active)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_CONTEXT_CHANGED", 409, "Command feedback window is not active");
        long elapsed = Math.max(0, TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - started));
        this.readable &= readable;
        // The first owner-thread sample at the deadline closes the window. Late samples must
        // not silently extend the promised budget or include messages received afterwards.
        if (elapsed <= WINDOW_MILLIS && this.readable) {
            for (int i = Math.min(100, newestFirst.size()) - 1; i >= 0; i--) {
                Message message = newestFirst.get(i);
                if (seen.contains(message.identity())) continue;
                if (messages.size() >= MAX_MESSAGES || seen.size() >= 128) {
                    truncated = true;
                    continue;
                }
                seen.add(message.identity());
                String text = message.text();
                if (text == null) continue;
                int count = text.codePointCount(0, text.length());
                if (count > 512) {
                    text = text.substring(0, text.offsetByCodePoints(0, 512));
                    truncated = true;
                }
                JsonObject sample = new JsonObject();
                sample.addProperty("source", "client_chat");
                sample.addProperty("text", text);
                sample.addProperty("offsetMillis", elapsed);
                messages.add(sample);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("association", "temporal_not_guaranteed");
        result.addProperty("windowMillis", WINDOW_MILLIS);
        result.addProperty("observedMillis", Math.min(WINDOW_MILLIS, elapsed));
        result.addProperty("complete", elapsed >= WINDOW_MILLIS);
        result.addProperty("readable", this.readable);
        result.addProperty("truncated", truncated);
        result.add("messages", this.readable ? messages.deepCopy() : new JsonArray());
        return result;
    }

    public JsonObject finishContextChanged() {
        JsonObject result = poll(List.of(), readable);
        result.addProperty("complete", true);
        result.addProperty("terminationReason", "client_context_changed");
        clear();
        return result;
    }

    public void clear() {
        active = false;
        readable = false;
        truncated = false;
        seen.clear();
        messages = new JsonArray();
    }
}
