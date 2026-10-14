package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Client-owner-thread identities; inactive entries expire without invalidating the whole UI. */
public final class UiInteractionIdentities {
    // Four full 2048-node snapshots leave room for native owners and menu metadata.
    private static final int CAPACITY = 8192;
    private final LinkedHashMap<Key, Long> identities = new LinkedHashMap<>(256, 0.75f, true);
    private long sequence;

    public long id(Object value) {
        Key key = new Key(Objects.requireNonNull(value));
        Long existing = identities.get(key);
        if (existing != null) return existing;
        if (identities.size() == CAPACITY) {
            var oldest = identities.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        long allocated = Math.incrementExact(sequence);
        sequence = allocated;
        identities.put(key, allocated);
        return allocated;
    }

    int size() {
        return identities.size();
    }

    private record Key(Object value) {
        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && value == key.value;
        }
    }
}
