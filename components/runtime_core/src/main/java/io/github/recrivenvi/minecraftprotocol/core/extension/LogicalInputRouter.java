package io.github.recrivenvi.minecraftprotocol.core.extension;

import io.github.recrivenvi.minecraftprotocol.probe.extension.LogicalInput;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Joins framework delivery without blocking either native or framework owners. */
public final class LogicalInputRouter {
    private final ThreadLocal<List<CompletableFuture<Void>>> pending = new ThreadLocal<>();
    private final ThreadLocal<Boolean> cleaning = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Runnable> authorization = new ThreadLocal<>();
    private final ThreadLocal<LogicalInput.Target> target = new ThreadLocal<>();

    public LogicalInput.Target target() {
        return target.get();
    }

    public void guard(
            com.google.gson.JsonObject guard,
            int guiWidth,
            int guiHeight,
            int framebufferWidth,
            int framebufferHeight) {
        target.remove();
        if (guard == null || !guard.has("node")) return;
        var node = guard.getAsJsonObject("node");
        if (!node.has("semanticState") || !node.has("capabilitySource")) return;
        var state = node.getAsJsonObject("semanticState");
        if (!state.has("extensionTargetKey") || !state.has("extensionTargetRevision")) return;
        target.set(
                new LogicalInput.Target(
                        node.get("capabilitySource").getAsString(),
                        state.get("extensionTargetKey").getAsString(),
                        state.get("extensionTargetRevision").getAsString(),
                        node.get("x").getAsDouble(),
                        node.get("y").getAsDouble(),
                        node.get("width").getAsDouble(),
                        node.get("height").getAsDouble(),
                        guiWidth,
                        guiHeight,
                        framebufferWidth,
                        framebufferHeight,
                        guard.has("textFocusRequired")
                                && guard.get("textFocusRequired").getAsBoolean()));
    }

    public <T> CompletableFuture<T> collect(Supplier<T> callback, Runnable check) {
        var previous = authorization.get();
        authorization.set(check);
        try {
            return collect(callback);
        } finally {
            if (previous == null) authorization.remove();
            else authorization.set(previous);
        }
    }

    public boolean cleanupOnly() {
        return cleaning.get();
    }

    public void await(CompletableFuture<Void> completion) {
        var batch = pending.get();
        if (batch == null || batch.size() >= 256)
            throw new IllegalStateException("No bounded native collection");
        batch.add(completion.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS));
    }

    public <T> CompletableFuture<T> collectCleanup(Supplier<T> callback) {
        boolean previous = cleaning.get();
        cleaning.set(true);
        try {
            return collect(callback);
        } finally {
            if (previous) cleaning.set(true);
            else cleaning.remove();
        }
    }

    public <T> CompletableFuture<T> collect(Supplier<T> callback) {
        var previous = pending.get();
        var previousTarget = target.get();
        target.remove();
        var batch = new ArrayList<CompletableFuture<Void>>();
        pending.set(batch);
        try {
            T result = callback.get();
            return CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new))
                    .exceptionally(
                            error -> {
                                var cause = error.getCause();
                                if (cause instanceof LogicalInput.TargetInvalidated)
                                    throw new io.github.recrivenvi.minecraftprotocol.core
                                            .ProtocolState.ProtocolException(
                                            "UI_TARGET_INVALIDATED", 409, cause.getMessage());
                                throw new java.util.concurrent.CompletionException(error);
                            })
                    .thenApply(ignored -> result);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        } finally {
            if (previous == null) pending.remove();
            else pending.set(previous);
            if (previousTarget == null) target.remove();
            else target.set(previousTarget);
        }
    }

    public void dispatch(
            LogicalInput.Snapshot snapshot,
            BooleanSupplier valid,
            AgentInputContext.Event event,
            Runnable callback) {
        var batch = pending.get();
        var check = authorization.get();
        BooleanSupplier permitted =
                () -> {
                    if (!valid.getAsBoolean()) return false;
                    if (!snapshot.cleanupOnly() && check != null) {
                        try {
                            check.run();
                        } catch (RuntimeException stopped) {
                            return false;
                        }
                    }
                    return true;
                };
        try (var scope =
                new LogicalInput.Binding(
                        snapshot,
                        permitted,
                        AgentInputContext::isAgentRouted,
                        () -> !AgentInputContext.isNativeTask(),
                        future -> {
                            if (batch == null || batch.size() >= 256)
                                throw new IllegalStateException(
                                        "Deferred input requires a bounded collected Runtime callback");
                            batch.add(future);
                        })) {
            AgentInputContext.dispatch(event, callback);
        }
    }
}
