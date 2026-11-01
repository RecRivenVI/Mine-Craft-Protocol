package io.github.recrivenvi.minecraftprotocol.extensions.create.bridge;

import com.google.gson.JsonObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Passive arrival diagnostics, explicitly scoped to this physical JVM and managed package owner.
 */
public final class NativeHooks {
    private static final class Counts {
        final AtomicLong client = new AtomicLong(),
                server = new AtomicLong(),
                clientReturn = new AtomicLong(),
                serverReturn = new AtomicLong();
        volatile String clientResult = "", serverResult = "";
    }

    private static final ConcurrentHashMap<String, Counts> OWNERS = new ConcurrentHashMap<>();

    private NativeHooks() {}

    public static void open(String runtime) {
        OWNERS.putIfAbsent(runtime, new Counts());
    }

    public static void close(String runtime) {
        OWNERS.remove(runtime);
    }

    private static boolean client(Object level) {
        for (String field : new String[] {"isClientSide", "f_46443_"}) {
            try {
                return level.getClass().getField(field).getBoolean(level);
            } catch (NoSuchFieldException missing) {
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Native side unavailable", failure);
            }
        }
        throw new IllegalStateException("Native side field unavailable");
    }

    public static void entered(Object level) {
        if (OWNERS.isEmpty()) return;
        boolean client = client(level);
        OWNERS.values()
                .forEach(counts -> (client ? counts.client : counts.server).incrementAndGet());
    }

    public static void returned(Object level, Object result) {
        if (OWNERS.isEmpty()) return;
        boolean client = client(level);
        String text = String.valueOf(result);
        String bounded = text.substring(0, Math.min(128, text.length()));
        OWNERS.values()
                .forEach(
                        counts -> {
                            (client ? counts.clientReturn : counts.serverReturn).incrementAndGet();
                            if (client) counts.clientResult = bounded;
                            else counts.serverResult = bounded;
                        });
    }

    public static JsonObject snapshot(String runtime) {
        var result = new JsonObject();
        result.addProperty("scope", "physical_jvm");
        var value = OWNERS.get(runtime);
        result.addProperty("enabled", value != null);
        if (value == null) return result;
        result.addProperty("clientCalls", value.client.get());
        result.addProperty("serverCalls", value.server.get());
        result.addProperty("clientReturns", value.clientReturn.get());
        result.addProperty("serverReturns", value.serverReturn.get());
        result.addProperty("clientResult", value.clientResult);
        result.addProperty("serverResult", value.serverResult);
        return result;
    }
}
