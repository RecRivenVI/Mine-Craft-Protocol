package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Owner-thread attribution of native mapping changes, independent of held input bookkeeping. */
public final class NativeInputOwnership<T> {
    private record Snapshot(int pending, boolean down) {}

    private final Set<T> owned = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Read accessors must be passive; a throwing dispatch still records its actual changes. */
    public void track(
            Iterable<T> mappings, ToIntFunction<T> pending, Predicate<T> down, Runnable dispatch) {
        Objects.requireNonNull(mappings);
        Objects.requireNonNull(pending);
        Objects.requireNonNull(down);
        Objects.requireNonNull(dispatch);
        Map<T, Snapshot> before = new IdentityHashMap<>();
        for (T mapping : mappings) {
            Objects.requireNonNull(mapping);
            before.put(mapping, new Snapshot(pending.applyAsInt(mapping), down.test(mapping)));
        }
        try {
            dispatch.run();
        } finally {
            for (var entry : before.entrySet()) {
                T mapping = entry.getKey();
                Snapshot previous = entry.getValue();
                int currentPending = pending.applyAsInt(mapping);
                boolean currentDown = down.test(mapping);
                if (currentPending > previous.pending() || currentDown && !previous.down())
                    owned.add(mapping);
            }
        }
    }

    /** Strong cleanup only: normal completion must leave submitted native clicks consumable. */
    public int releaseOwned(Consumer<T> release) {
        Objects.requireNonNull(release);
        int released = 0;
        for (T mapping : new ArrayList<>(owned)) {
            release.accept(mapping);
            owned.remove(mapping);
            released++;
        }
        return released;
    }

    /** Forget only mappings whose submitted native clicks and held state are both finished. */
    public void retireConsumed(ToIntFunction<T> pending, Predicate<T> down) {
        Objects.requireNonNull(pending);
        Objects.requireNonNull(down);
        owned.removeIf(mapping -> pending.applyAsInt(mapping) == 0 && !down.test(mapping));
    }

    public int size() {
        return owned.size();
    }
}
