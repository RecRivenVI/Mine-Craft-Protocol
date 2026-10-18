package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/** Sequential native submissions; receipts are neither a transaction nor server feedback. */
public final class PlayerCommandBatch {
    @FunctionalInterface
    public interface Sender {
        CompletableFuture<JsonObject> submit(
                int commandIndex, String command, Runnable dispatchGuard);
    }

    private PlayerCommandBatch() {}

    public static CompletableFuture<JsonObject> execute(
            List<String> commands, String requestId, Runnable authorization, Sender sender) {
        List<String> normalized = normalize(commands);
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(authorization);
        Objects.requireNonNull(sender);
        return new Submission(normalized, requestId, authorization, sender).start();
    }

    public static List<String> normalize(List<String> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > 32)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BATCH_BUDGET_EXCEEDED",
                    413,
                    "A native batch requires 1..32 commands");
        List<String> values = new ArrayList<>(commands.size());
        int characters = 0;
        for (String command : commands) {
            String normalized = PlayerCommandPolicy.normalize(command);
            if (normalized.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF))
                throw new ProtocolState.ProtocolException(
                        "INVALID_PLAYER_COMMAND",
                        400,
                        "Command contains an invalid Unicode scalar");
            characters += normalized.length();
            if (characters > 32768)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BATCH_BUDGET_EXCEEDED",
                        413,
                        "Batch exceeds 32768 text characters");
            values.add(normalized);
        }
        return List.copyOf(values);
    }

    private static final class Submission {
        private final List<String> commands;
        private final String requestId;
        private final Runnable authorization;
        private final Sender sender;
        private final CompletableFuture<JsonObject> result = new CompletableFuture<>();
        private final AtomicReference<CompletableFuture<JsonObject>> current =
                new AtomicReference<>();
        private final JsonArray items = new JsonArray();
        private int submitted;

        Submission(List<String> commands, String requestId, Runnable authorization, Sender sender) {
            this.commands = commands;
            this.requestId = requestId;
            this.authorization = authorization;
            this.sender = sender;
            result.whenComplete(
                    (value, error) -> {
                        if (result.isCancelled()) {
                            var pending = current.get();
                            if (pending != null) pending.cancel(false);
                        }
                    });
        }

        CompletableFuture<JsonObject> start() {
            next(0);
            return result;
        }

        void check() {
            if (result.isDone())
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BATCH_CANCELLED",
                        409,
                        "Native command batch is no longer active");
            authorization.run();
        }

        void next(int index) {
            if (result.isDone()) return;
            if (index == commands.size()) {
                finish("submitted_unconfirmed", null);
                return;
            }
            JsonObject item = item(index);
            try {
                check();
            } catch (Throwable failure) {
                stop(index, item, failure, false);
                return;
            }
            CompletableFuture<JsonObject> pending;
            try {
                pending =
                        Objects.requireNonNull(
                                sender.submit(index, commands.get(index), this::check));
            } catch (Throwable failure) {
                stop(index, item, failure, true);
                return;
            }
            current.set(pending);
            if (result.isDone()) {
                pending.cancel(false);
                return;
            }
            pending.whenComplete(
                    (receipt, error) -> {
                        current.compareAndSet(pending, null);
                        if (result.isDone()) return;
                        if (error != null) {
                            stop(index, item, error, true);
                            return;
                        }
                        if (receipt == null) {
                            stop(
                                    index,
                                    item,
                                    new IllegalStateException(
                                            "Native submission returned no receipt"),
                                    true);
                            return;
                        }
                        item.addProperty("status", "submitted_unconfirmed");
                        item.addProperty("submissionMayHaveOccurred", true);
                        item.add("receipt", receipt.deepCopy());
                        items.add(item);
                        submitted++;
                        next(index + 1);
                    });
        }

        JsonObject item(int index) {
            JsonObject item = new JsonObject();
            item.addProperty("commandIndex", index);
            item.addProperty("commandRequestId", requestId + ":" + index);
            item.addProperty("command", commands.get(index));
            item.addProperty("dataCategory", "USER");
            item.addProperty("effectConfirmed", false);
            item.addProperty("retrySafe", false);
            return item;
        }

        void stop(int index, JsonObject item, Throwable failure, boolean attempted) {
            if (result.isDone()) return;
            while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                    && failure.getCause() != null) failure = failure.getCause();
            JsonObject error = new JsonObject();
            error.addProperty(
                    "code",
                    failure instanceof ProtocolState.ProtocolException protocol
                            ? protocol.code()
                            : failure instanceof CancellationException
                                    ? "REQUEST_CANCELLED"
                                    : "PLAYER_COMMAND_SUBMISSION_FAILED");
            String message = failure.getMessage();
            if (message != null)
                error.addProperty("message", message.substring(0, Math.min(512, message.length())));
            item.addProperty("status", attempted ? "submission_unconfirmed" : "not_submitted");
            item.addProperty("submissionMayHaveOccurred", attempted);
            item.add("error", error);
            items.add(item);
            for (int remaining = index + 1; remaining < commands.size(); remaining++) {
                var skipped = item(remaining);
                skipped.addProperty("status", "not_attempted");
                skipped.addProperty("submissionMayHaveOccurred", false);
                items.add(skipped);
            }
            finish(
                    attempted
                            ? "not_confirmed"
                            : submitted == 0 ? "rejected" : "partial_submission",
                    index);
        }

        void finish(String outcome, Integer stoppedAt) {
            JsonObject value = new JsonObject();
            value.addProperty("type", "command.player.batch");
            value.addProperty("requestId", requestId);
            value.addProperty("route", "NATIVE");
            value.addProperty("dataCategory", "USER");
            value.addProperty("mechanism", "NORMAL_NETWORK");
            value.addProperty("submissionStatus", outcome);
            value.addProperty("commandCount", commands.size());
            value.addProperty("submittedCount", submitted);
            if (stoppedAt != null) value.addProperty("stoppedAtCommandIndex", stoppedAt);
            value.addProperty("requiresControlLease", false);
            value.addProperty("inputDispatched", false);
            value.addProperty("effectConfirmed", false);
            value.addProperty("serverValidationObserved", false);
            value.addProperty("serverFeedbackCorrelated", false);
            value.addProperty("feedbackCorrelation", "submission_request_and_index_only");
            value.addProperty("atomic", false);
            value.addProperty("rollbackAttempted", false);
            value.addProperty("retrySafe", false);
            value.addProperty("gameplayAcceptanceEligible", false);
            value.add("commands", items);
            result.complete(value);
        }
    }
}
