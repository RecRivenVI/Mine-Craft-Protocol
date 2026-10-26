package io.github.recrivenvi.minecraftprotocol.core;

import java.util.Map;
import java.util.WeakHashMap;

/** Revisions of named native environment edits, not natural timer progression. */
public final class EnvironmentControlRevisions {
    private static final Map<Object, Generation> GENERATIONS = new WeakHashMap<>();
    private static long sequence;

    private EnvironmentControlRevisions() {}

    private static final class Generation {
        long time;
        long weather;
    }

    public static synchronized void timeEdited(Object nativeClock) {
        GENERATIONS.computeIfAbsent(nativeClock, ignored -> new Generation()).time = ++sequence;
    }

    public static synchronized void weatherEdited(Object nativeOwner) {
        GENERATIONS.computeIfAbsent(nativeOwner, ignored -> new Generation()).weather = ++sequence;
    }

    public static synchronized long time(Object nativeClock) {
        Generation value = GENERATIONS.get(nativeClock);
        return value == null ? 0 : value.time;
    }

    public static synchronized long weather(Object nativeOwner) {
        Generation value = GENERATIONS.get(nativeOwner);
        return value == null ? 0 : value.weather;
    }
}
