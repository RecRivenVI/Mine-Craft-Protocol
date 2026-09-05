package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Cancellation follows a detached request across an owner-thread/IO handoff. */
public final class CancellableWork {
    private CancellableWork() { }

    public static <T, U> CompletableFuture<U> compose(
            CompletableFuture<T> first, Function<T, CompletableFuture<U>> continuation) {
        AtomicReference<CompletableFuture<?>> current = new AtomicReference<>(first);
        CompletableFuture<U> result = new CompletableFuture<>() {
            @Override public boolean cancel(boolean interrupt) {
                boolean accepted = super.cancel(interrupt);
                if (accepted) current.get().cancel(interrupt);
                return accepted;
            }
        };
        first.whenComplete((value, failure) -> {
            if (result.isDone()) return;
            if (failure != null) { result.completeExceptionally(failure); return; }
            try {
                CompletableFuture<U> child = continuation.apply(value);
                current.set(child);
                if (result.isCancelled()) child.cancel(false);
                child.whenComplete((output, error) -> {
                    if (error != null) result.completeExceptionally(error);
                    else result.complete(output);
                });
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }
}
