package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Cancellation follows a detached request across an owner-thread/IO handoff. */
public final class CancellableWork {
    private CancellableWork() {}

    public static <T, U> CompletableFuture<U> map(
            CompletableFuture<T> work, Function<T, U> transform) {
        return compose(work, value -> CompletableFuture.completedFuture(transform.apply(value)));
    }

    public static <T, U> CompletableFuture<U> handle(
            CompletableFuture<T> work, java.util.function.BiFunction<T, Throwable, U> transform) {
        CompletableFuture<U> result =
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        boolean accepted = super.cancel(interrupt);
                        if (accepted) work.cancel(interrupt);
                        return accepted;
                    }
                };
        work.whenComplete(
                (value, failure) -> {
                    if (result.isDone()) return;
                    try {
                        result.complete(transform.apply(value, failure));
                    } catch (Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
        return result;
    }

    public static CompletableFuture<Void> all(java.util.List<? extends CompletableFuture<?>> work) {
        CompletableFuture<Void> result =
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        boolean accepted = super.cancel(interrupt);
                        if (accepted) work.forEach(future -> future.cancel(interrupt));
                        return accepted;
                    }
                };
        var remaining = new java.util.concurrent.atomic.AtomicInteger(work.size());
        if (work.isEmpty()) result.complete(null);
        work.forEach(
                future ->
                        future.whenComplete(
                                (value, error) -> {
                                    if (error != null) {
                                        if (result.completeExceptionally(error))
                                            work.forEach(child -> child.cancel(false));
                                    } else if (remaining.decrementAndGet() == 0)
                                        result.complete(null);
                                }));
        return result;
    }

    public static <T, U> CompletableFuture<U> compose(
            CompletableFuture<T> first, Function<T, CompletableFuture<U>> continuation) {
        AtomicReference<CompletableFuture<?>> current = new AtomicReference<>(first);
        CompletableFuture<U> result =
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        boolean accepted = super.cancel(interrupt);
                        if (accepted) current.get().cancel(interrupt);
                        return accepted;
                    }
                };
        first.whenComplete(
                (value, failure) -> {
                    if (result.isDone()) return;
                    if (failure != null) {
                        result.completeExceptionally(failure);
                        return;
                    }
                    try {
                        CompletableFuture<U> child = continuation.apply(value);
                        current.set(child);
                        if (result.isCancelled()) child.cancel(false);
                        child.whenComplete(
                                (output, error) -> {
                                    if (error != null) result.completeExceptionally(error);
                                    else result.complete(output);
                                });
                    } catch (Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
        return result;
    }
}
