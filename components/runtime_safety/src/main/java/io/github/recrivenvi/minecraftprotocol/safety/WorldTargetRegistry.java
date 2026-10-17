package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Short-lived references are bounded hints, never authorization or a substitute for native checks.
 */
public final class WorldTargetRegistry<T> {
    public static final int CAPACITY = 1024;
    public static final long TTL_NANOS = 15_000_000_000L;

    private record Entry<T>(T value, long created) {}

    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>();
    private final LongSupplier clock;

    public WorldTargetRegistry() {
        this(System::nanoTime);
    }

    public WorldTargetRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized String add(T value) {
        entries.entrySet().removeIf(e -> clock.getAsLong() - e.getValue().created() >= TTL_NANOS);
        while (entries.size() >= CAPACITY) entries.remove(entries.keySet().iterator().next());
        String token = "world:" + UUID.randomUUID();
        entries.put(token, new Entry<>(value, clock.getAsLong()));
        return token;
    }

    public synchronized T get(String token) {
        Entry<T> entry = entries.get(token);
        if (entry == null) return null;
        if (clock.getAsLong() - entry.created() >= TTL_NANOS) {
            entries.remove(token);
            return null;
        }
        return entry.value();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
