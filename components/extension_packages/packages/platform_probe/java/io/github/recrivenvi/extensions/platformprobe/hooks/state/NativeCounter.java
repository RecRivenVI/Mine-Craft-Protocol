package io.github.recrivenvi.extensions.platformprobe.hooks.state;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Passive physical-JVM instrumentation; each Runtime owns only its own enable registration. */
public final class NativeCounter {
    private static final Set<String> OWNERS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong CALLS = new AtomicLong();
    private static volatile String thread = "";

    private NativeCounter() {}

    public static void enable(String runtime) {
        OWNERS.add(runtime);
    }

    public static void disable(String runtime) {
        OWNERS.remove(runtime);
    }

    public static void hit() {
        if (!OWNERS.isEmpty()) {
            CALLS.incrementAndGet();
            thread = Thread.currentThread().getName();
        }
    }

    public static long calls() {
        return CALLS.get();
    }

    public static String thread() {
        return thread;
    }
}
