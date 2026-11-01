package io.github.recrivenvi.minecraftprotocol.extensions.littletiles.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded passive structure-call evidence, not an action API or a game-object registry. */
public final class NativeHooks {
    private static final class Trace {
        final AtomicLong sequence = new AtomicLong();
        final ConcurrentLinkedDeque<JsonObject> events = new ConcurrentLinkedDeque<>();
    }

    private static final ConcurrentHashMap<String, Trace> OWNERS = new ConcurrentHashMap<>();

    private NativeHooks() {}

    public static void open(String runtime) {
        OWNERS.putIfAbsent(runtime, new Trace());
    }

    public static void close(String runtime) {
        OWNERS.remove(runtime);
    }

    public static void record(
            String stage, Object level, Object receiver, Object focus, Object nativeResult) {
        if (OWNERS.isEmpty()) return;
        var event = new JsonObject();
        event.addProperty("stage", stage);
        event.addProperty("receiverClass", receiver.getClass().getName());
        event.addProperty("receiverIdentity", System.identityHashCode(receiver));
        try {
            Object side;
            try {
                side = level.getClass().getField("isClientSide").get(level);
            } catch (NoSuchFieldException absent) {
                side = level.getClass().getField("f_46443_").get(level);
            }
            event.addProperty("side", Boolean.TRUE.equals(side) ? "client" : "server");
            for (String name : new String[] {"parent", "tile", "box"})
                event.addProperty(
                        "focus" + name,
                        System.identityHashCode(focus.getClass().getField(name).get(focus)));
            for (String name :
                    new String[] {
                        "rightClick",
                        "activateParent",
                        "disableRightClick",
                        "currentState",
                        "aimedState",
                        "duration"
                    }) {
                for (Class<?> owner = receiver.getClass();
                        owner != null;
                        owner = owner.getSuperclass()) {
                    try {
                        var field = owner.getDeclaredField(name);
                        if (!field.trySetAccessible()) {
                            event.addProperty("diagnosticUnavailable", true);
                            break;
                        }
                        Object value = field.get(receiver);
                        if (value instanceof Number number) event.addProperty(name, number);
                        else if (value instanceof Boolean bool) event.addProperty(name, bool);
                        break;
                    } catch (NoSuchFieldException absent) {
                    }
                }
            }
        } catch (ReflectiveOperationException failure) {
            event.addProperty("diagnosticError", failure.getClass().getSimpleName());
        }
        if (nativeResult != null) {
            String text = String.valueOf(nativeResult);
            event.addProperty("nativeResult", text.substring(0, Math.min(128, text.length())));
        }
        OWNERS.values()
                .forEach(
                        trace -> {
                            var copy = event.deepCopy();
                            copy.addProperty("sequence", trace.sequence.incrementAndGet());
                            trace.events.addLast(copy);
                            while (trace.events.size() > 64) trace.events.pollFirst();
                        });
    }

    public static JsonObject snapshot(String runtime) {
        var result = new JsonObject();
        result.addProperty("scope", "physical_jvm");
        var trace = OWNERS.get(runtime);
        result.addProperty("enabled", trace != null);
        if (trace != null) {
            result.addProperty("sequence", trace.sequence.get());
            var events = new JsonArray();
            trace.events.forEach(event -> events.add(event.deepCopy()));
            result.add("events", events);
        }
        return result;
    }
}
