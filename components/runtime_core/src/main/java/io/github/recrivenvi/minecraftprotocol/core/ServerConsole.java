package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Bounded, explicitly operator-owned output and native dispatch. No player or terminal takeover.
 */
public final class ServerConsole implements AutoCloseable {
    public interface Host {
        void schedule(Runnable action);

        void dispatch(String command, Consumer<String> feedback, Consumer<JsonObject> result);
    }

    private static final Set<String> ROOTS =
            Set.of("help", "list", "say", "seed", "time", "weather");
    private static final int CAPACITY = 256;
    private final ArrayDeque<JsonObject> output = new ArrayDeque<>();
    private final Host host;
    private final AtomicBoolean closed = new AtomicBoolean();
    private long sequence;

    public ServerConsole(Host host) {
        this.host = host;
    }

    public static JsonObject identity() {
        JsonObject result = new JsonObject();
        result.addProperty("domain", "UI");
        result.addProperty("dataCategory", "USER");
        result.addProperty("authority", "SERVER_CONSOLE");
        result.addProperty("actor", "server_operator");
        result.addProperty("untrustedData", true);
        result.addProperty("chunkLoadRequested", false);
        result.addProperty("storageAccessed", false);
        return result;
    }

    public static JsonObject contract() {
        JsonObject value = identity();
        value.addProperty("implemented", true);
        value.addProperty("readScope", "server_console");
        value.addProperty("commandScope", "server_console");
        value.addProperty("inputLeaseRequired", false);
        value.addProperty("commandMode", "OPERATE");
        value.addProperty("outputCapacity", CAPACITY);
        value.addProperty("textLimit", 4096);
        value.addProperty("pageLimit", 128);
        value.addProperty("commandLengthLimit", 2048);
        value.addProperty(
                "outputSource", "published_info_and_higher_log_events_not_terminal_pixels");
        value.addProperty("historicalFilesRead", false);
        value.addProperty("worldEffectConfirmed", false);
        value.addProperty("fullCommandCoverage", false);
        value.addProperty("controlPlaneCommands", "not_available_in_initial_catalogue");
        JsonArray roots = new JsonArray();
        ROOTS.stream().sorted().forEach(roots::add);
        value.add("supportedCommandRoots", roots);
        return value;
    }

    public synchronized void publish(
            String level, String logger, String thread, String text, String requestId) {
        if (closed.get()) return;
        if (output.size() == CAPACITY) output.removeFirst();
        JsonObject record = identity();
        record.addProperty("sequence", ++sequence);
        record.addProperty("timeMillis", System.currentTimeMillis());
        record.addProperty("level", bounded(level, 16));
        record.addProperty("logger", bounded(logger, 256));
        record.addProperty("thread", bounded(thread, 128));
        record.addProperty("text", bounded(text, 4096));
        record.addProperty("truncated", text != null && text.length() > 4096);
        if (requestId != null) record.addProperty("commandRequestId", requestId);
        output.add(record);
    }

    public synchronized JsonObject read(long after, int limit) {
        requireOpen();
        if (after < 0 || after > sequence || limit < 1 || limit > 128)
            throw new ProtocolState.ProtocolException(
                    "INVALID_CONSOLE_QUERY",
                    400,
                    "after must be 0..latestSequence; limit must be 1..128");
        long oldest =
                output.isEmpty() ? sequence + 1 : output.getFirst().get("sequence").getAsLong();
        JsonObject value = identity();
        JsonArray records = new JsonArray();
        long next = after;
        for (JsonObject record : output)
            if (record.get("sequence").getAsLong() > after && records.size() < limit) {
                records.add(record.deepCopy());
                next = record.get("sequence").getAsLong();
            }
        value.add("records", records);
        value.addProperty("nextSequence", next);
        value.addProperty("latestSequence", sequence);
        value.addProperty("oldestSequence", oldest);
        value.addProperty("gap", after < oldest - 1);
        value.addProperty("hasMore", next < sequence);
        value.addProperty("state", "LIVE");
        return value;
    }

    public CompletableFuture<JsonObject> execute(
            String input,
            String requestId,
            AgentControlSession.OperateWork work,
            Runnable authorization) {
        requireOpen();
        String command = PlayerCommandPolicy.normalize(input);
        String root = command.split("\\s+", 2)[0];
        if (!ROOTS.contains(root))
            throw new ProtocolState.ProtocolException(
                    "CONSOLE_COMMAND_UNSUPPORTED",
                    409,
                    "Command is outside the initial server console catalogue");
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        host.schedule(
                () -> {
                    if (future.isCancelled()) return;
                    try (var guard = work.enter()) {
                        requireOpen();
                        authorization.run();
                        if (future.isCancelled()) return;
                        JsonObject receipt = identity();
                        JsonArray feedback = new JsonArray();
                        JsonObject nativeResult = new JsonObject();
                        Consumer<String> output =
                                text -> {
                                    synchronized (feedback) {
                                        if (feedback.size() < 64) feedback.add(bounded(text, 4096));
                                    }
                                    publish(
                                            "INFO",
                                            "native_command_feedback",
                                            Thread.currentThread().getName(),
                                            text,
                                            requestId);
                                };
                        host.dispatch(
                                command,
                                output,
                                result -> {
                                    synchronized (nativeResult) {
                                        result.entrySet()
                                                .forEach(
                                                        e ->
                                                                nativeResult.add(
                                                                        e.getKey(),
                                                                        e.getValue().deepCopy()));
                                    }
                                });
                        receipt.addProperty("commandRequestId", requestId);
                        receipt.addProperty("route", "NATIVE_SERVER_CONSOLE");
                        receipt.addProperty("dispatchState", "native_dispatch_returned");
                        receipt.addProperty("worldEffectConfirmed", false);
                        receipt.addProperty("evidenceAuthority", "SERVER_OPERATOR_COMMAND");
                        receipt.addProperty("cleanGameplayAcceptance", false);
                        synchronized (feedback) {
                            receipt.add("feedback", feedback.deepCopy());
                        }
                        synchronized (nativeResult) {
                            receipt.add("nativeResult", nativeResult.deepCopy());
                            receipt.addProperty("resultCallbackReceived", nativeResult.size() != 0);
                        }
                        future.complete(receipt);
                    } catch (Throwable failure) {
                        future.completeExceptionally(failure);
                    }
                });
        return future;
    }

    private void requireOpen() {
        if (closed.get())
            throw new ProtocolState.ProtocolException(
                    "RUNTIME_SHUTTING_DOWN", 409, "Server console is closed");
    }

    private static String bounded(String text, int limit) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), limit));
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
