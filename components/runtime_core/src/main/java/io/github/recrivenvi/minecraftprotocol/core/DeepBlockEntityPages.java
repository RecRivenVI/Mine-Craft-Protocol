package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded continuation over live, ordered cache keys; not a cross-page world snapshot. */
public final class DeepBlockEntityPages {
    public static final int MAX_CANDIDATES = 8192;
    private static final int MAX_CURSORS = 32;
    private static final long TTL_MILLIS = 60_000;
    private final Map<String, Cursor> cursors = new LinkedHashMap<>();
    private final LongSupplier clock;

    public DeepBlockEntityPages() {
        this(System::currentTimeMillis);
    }

    DeepBlockEntityPages(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized Page select(
            Object world,
            Object player,
            String context,
            String cursor,
            List<String> keys,
            int limit) {
        long now = this.clock.getAsLong();
        this.cursors.values().removeIf(value -> value.expiresAt <= now);
        if (keys.size() > MAX_CANDIDATES)
            throw new ProtocolState.ProtocolException(
                    "DEEP_PAGE_SCAN_LIMIT_EXCEEDED",
                    413,
                    "Loaded block entity candidate bound exceeded; narrow selector.chunkRadius");
        List<String> ordered = new ArrayList<>(keys);
        Collections.sort(ordered);
        int from = 0;
        if (cursor != null) {
            Cursor previous = this.cursors.get(cursor);
            if (previous == null
                    || previous.world.get() != world
                    || previous.player.get() != player
                    || !previous.context.equals(context))
                throw new ProtocolState.ProtocolException(
                        "DEEP_PAGE_CURSOR_STALE",
                        409,
                        "Block entity continuation expired or its world, player, anchor or selector changed; restart without the cursor");
            int position = Collections.binarySearch(ordered, previous.after);
            from = position < 0 ? -position - 1 : position + 1;
        }
        int to = Math.min(ordered.size(), from + limit);
        List<String> selected = List.copyOf(ordered.subList(from, to));
        JsonObject metadata = new JsonObject();
        metadata.addProperty("returnedCount", selected.size());
        metadata.addProperty("limit", limit);
        metadata.addProperty("candidateCount", ordered.size());
        metadata.addProperty("consistency", "per_page_live");
        metadata.addProperty("ordering", "coordinate_key_lexicographic");
        metadata.addProperty("hasMore", to < ordered.size());
        metadata.addProperty("cursorTtlMs", TTL_MILLIS);
        metadata.addProperty("chunkLoadRequested", false);
        if (to < ordered.size()) {
            String next = UUID.randomUUID().toString();
            this.cursors.put(
                    next,
                    new Cursor(
                            new WeakReference<>(world),
                            new WeakReference<>(player),
                            context,
                            selected.get(selected.size() - 1),
                            now + TTL_MILLIS));
            while (this.cursors.size() > MAX_CURSORS)
                this.cursors.remove(this.cursors.keySet().iterator().next());
            metadata.addProperty("nextCursor", next);
        }
        return new Page(selected, metadata);
    }

    public synchronized void clear() {
        this.cursors.clear();
    }

    public record Page(List<String> keys, JsonObject metadata) {}

    private record Cursor(
            WeakReference<Object> world,
            WeakReference<Object> player,
            String context,
            String after,
            long expiresAt) {}
}
