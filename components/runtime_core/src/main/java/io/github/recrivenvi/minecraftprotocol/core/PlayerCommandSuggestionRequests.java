package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** One connection's bounded Agent suggestion requests, separate from Vanilla's human future. */
public final class PlayerCommandSuggestionRequests<T> implements AutoCloseable {
    public static final long TIMEOUT_MILLIS = 1500;
    public static final long MIN_INTERVAL_MILLIS = 250;

    public record Ticket<T>(int requestId, CompletableFuture<T> future) {}

    private static final class Pending<T> {
        final Ticket<T> ticket;
        final Runnable authorization;
        final long expiresAt;

        Pending(Ticket<T> ticket, Runnable authorization, long expiresAt) {
            this.ticket = ticket;
            this.authorization = authorization;
            this.expiresAt = expiresAt;
        }
    }

    private Pending<T> pending;
    private long nextId = -2;
    private long nextAllowedAt = Long.MIN_VALUE;
    private boolean closed;

    public Ticket<T> begin(long nowMillis, Runnable authorization) {
        Objects.requireNonNull(authorization);
        Pending<T> started;
        synchronized (this) {
            if (closed)
                throw failure("PLAYER_COMMAND_CONTEXT_CHANGED", "Suggestion connection is closed");
            if (pending != null && !pending.ticket.future().isDone())
                throw failure(
                        "PLAYER_COMMAND_COMPLETION_BUSY", "Another Agent completion is pending");
            if (nowMillis < nextAllowedAt) {
                JsonObject details = new JsonObject();
                details.addProperty("retryAfterMillis", nextAllowedAt - nowMillis);
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_COMPLETION_RATE_LIMITED",
                        429,
                        "Agent completion requests are rate limited independently of human input",
                        details);
            }
            if (nextId < Integer.MIN_VALUE)
                throw failure(
                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                        "Suggestion request identity budget exhausted");
            var ticket = new Ticket<T>((int) nextId--, new CompletableFuture<>());
            nextAllowedAt = nowMillis + MIN_INTERVAL_MILLIS;
            started = new Pending<>(ticket, authorization, nowMillis + TIMEOUT_MILLIS);
            pending = started;
        }
        started.ticket
                .future()
                .whenComplete(
                        (value, error) -> {
                            synchronized (PlayerCommandSuggestionRequests.this) {
                                if (pending == started) pending = null;
                            }
                        });
        return started.ticket;
    }

    public void requireActive(Ticket<T> ticket) {
        Runnable authorization;
        synchronized (this) {
            if (closed || pending == null || pending.ticket != ticket || ticket.future().isDone())
                throw failure(
                        "PLAYER_COMMAND_COMPLETION_CANCELLED",
                        "Suggestion request is no longer active");
            authorization = pending.authorization;
        }
        authorization.run();
        synchronized (this) {
            if (closed || pending == null || pending.ticket != ticket || ticket.future().isDone())
                throw failure(
                        "PLAYER_COMMAND_COMPLETION_CANCELLED",
                        "Suggestion request was cancelled before dispatch");
        }
    }

    public boolean receive(int requestId, T suggestions, long nowMillis) {
        Pending<T> current;
        synchronized (this) {
            if (requestId > -2 || requestId <= nextId) return false;
            current = pending;
            if (current == null || current.ticket.requestId() != requestId) return true;
        }
        if (nowMillis >= current.expiresAt) {
            current.ticket.future().completeExceptionally(timeout());
            return true;
        }
        try {
            requireActive(current.ticket);
            current.ticket.future().complete(suggestions);
        } catch (Throwable failure) {
            current.ticket.future().completeExceptionally(failure);
        }
        return true;
    }

    public synchronized boolean owns(int requestId) {
        return requestId <= -2 && requestId > nextId;
    }

    public void dispatch(
            Ticket<T> ticket, Runnable humanPriorityCheck, java.util.function.IntConsumer send) {
        try {
            requireActive(ticket);
            humanPriorityCheck.run();
            synchronized (this) {
                if (closed
                        || pending == null
                        || pending.ticket != ticket
                        || ticket.future().isDone())
                    throw failure(
                            "PLAYER_COMMAND_COMPLETION_CANCELLED",
                            "Suggestion request was cancelled before dispatch");
            }
            send.accept(ticket.requestId());
        } catch (Throwable failure) {
            ticket.future().completeExceptionally(failure);
        }
    }

    public void tick(long nowMillis) {
        Pending<T> current;
        synchronized (this) {
            current = pending;
        }
        if (current == null) return;
        if (nowMillis >= current.expiresAt) {
            current.ticket.future().completeExceptionally(timeout());
            return;
        }
        try {
            requireActive(current.ticket);
        } catch (Throwable failure) {
            current.ticket.future().completeExceptionally(failure);
        }
    }

    @Override
    public void close() {
        Pending<T> current;
        synchronized (this) {
            if (closed) return;
            closed = true;
            current = pending;
            pending = null;
        }
        if (current != null)
            current.ticket
                    .future()
                    .completeExceptionally(
                            failure(
                                    "PLAYER_COMMAND_CONTEXT_CHANGED",
                                    "Suggestion connection or Runtime ended"));
    }

    private static ProtocolState.ProtocolException failure(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }

    private static ProtocolState.ProtocolException timeout() {
        return new ProtocolState.ProtocolException(
                "PLAYER_COMMAND_COMPLETION_TIMEOUT",
                408,
                "Server completion did not respond within the bounded window");
    }
}
