package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.Objects;
import java.util.function.Supplier;

/** Explicit one-use callback admission plus scoped execution origin, never an ambient input permission. */
public final class AgentInputContext {
    public enum Kind { KEY, CHARACTER, PREEDIT, MOVE, BUTTON, SCROLL, DROP }
    public record Event(Kind kind, long window, long a, long b, long c, long d) {
        public static Event key(long window, int key, int scan, int action, int mods) {
            return new Event(Kind.KEY, window, key, scan, action, mods);
        }
        public static Event button(long window, int button, int action, int mods) {
            return new Event(Kind.BUTTON, window, button, action, mods, 0);
        }
        public static Event point(Kind kind, long window, double x, double y) {
            return new Event(kind, window, Double.doubleToLongBits(x), Double.doubleToLongBits(y), 0, 0);
        }
    }
    private static final class Admission {
        final Event event;
        boolean consumed;
        Admission(Event event) { this.event = event; }
    }
    private static final ThreadLocal<Admission> ADMISSION = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROUTED_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> NATIVE = ThreadLocal.withInitial(() -> false);
    private AgentInputContext() { }

    /** Used only for Vanilla logical focus/camera processing, not callback authorization. */
    public static boolean isAgentRouted() { return ROUTED_DEPTH.get() > 0 && !NATIVE.get(); }

    public static boolean consume(Event event) {
        Admission admission = ADMISSION.get();
        if (NATIVE.get() || admission == null || admission.consumed || !admission.event.equals(event)) return false;
        admission.consumed = true;
        return true;
    }

    public static void dispatch(Event event, Runnable action) {
        Objects.requireNonNull(event); Objects.requireNonNull(action);
        Admission previous = ADMISSION.get();
        boolean nativeBefore = NATIVE.get();
        ADMISSION.set(new Admission(event)); NATIVE.set(false);
        try { routed(action); }
        finally {
            if (previous == null) ADMISSION.remove(); else ADMISSION.set(previous);
            if (nativeBefore) NATIVE.set(true); else NATIVE.remove();
        }
    }

    /** Captures native provenance across Minecraft's scheduled callback boundary. */
    public static Runnable nativeTask(Runnable action) {
        Objects.requireNonNull(action);
        return () -> {
            Admission previous = ADMISSION.get();
            boolean nativeBefore = NATIVE.get();
            ADMISSION.remove(); NATIVE.set(true);
            try { action.run(); }
            finally {
                if (previous == null) ADMISSION.remove(); else ADMISSION.set(previous);
                if (nativeBefore) NATIVE.set(true); else NATIVE.remove();
            }
        };
    }

    public static <T> T routed(Supplier<T> action) {
        int previous = ROUTED_DEPTH.get();
        ROUTED_DEPTH.set(previous + 1);
        try { return action.get(); }
        finally { if (previous == 0) ROUTED_DEPTH.remove(); else ROUTED_DEPTH.set(previous); }
    }
    public static void routed(Runnable action) { routed(() -> { action.run(); return null; }); }
}
