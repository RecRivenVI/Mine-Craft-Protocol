package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated authorization/lifecycle boundary; the Target owns all Minecraft resource semantics. */
public final class ServerWorldDebug implements AutoCloseable {
    public interface Host extends AutoCloseable {
        default io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform.Environment
                extensionEnvironment(String target) {
            return null;
        }

        default java.util.Map<
                        String,
                        io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources
                                .ThreadOwner>
                extensionThreadOwners() {
            return java.util.Map.of();
        }

        default io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform.EarlyHooks
                extensionEarlyHooks() {
            return (manifest, environment) -> false;
        }

        default void attachExtensionProviders(
                io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSource source) {
            throw new UnsupportedOperationException(
                    "World host has no extension Provider dispatcher");
        }

        JsonObject fingerprint();

        JsonObject capabilities();

        JsonObject observationCapabilities();

        CompletableFuture<JsonObject> observe(
                JsonObject request, DeepObservationRequestContext context);

        CompletableFuture<JsonObject> mutate(
                JsonObject request, DebugMutationAuthorization authorization);

        CompletableFuture<JsonObject> storageRead(JsonObject request);

        @Override
        void close();
    }

    private static final Set<String> READ_SCOPES = Set.of("read", "debug", "debug.world.read");
    private final Host host;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pending =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ServerWorldDebug(Host host) {
        this.host = java.util.Objects.requireNonNull(host);
    }

    public io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform.Environment
            extensionEnvironment(String target) {
        requireOpen();
        return host.extensionEnvironment(target);
    }

    public java.util.Map<
                    String,
                    io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources
                            .ThreadOwner>
            extensionThreadOwners() {
        requireOpen();
        return host.extensionThreadOwners();
    }

    public io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform.EarlyHooks
            extensionEarlyHooks() {
        requireOpen();
        return host.extensionEarlyHooks();
    }

    public void attachExtensionProviders(
            io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSource source) {
        requireOpen();
        host.attachExtensionProviders(source);
    }

    public JsonObject fingerprint() {
        requireOpen();
        return host.fingerprint();
    }

    public JsonObject capabilities() {
        requireOpen();
        return host.capabilities();
    }

    public JsonObject observationCapabilities() {
        requireOpen();
        return host.observationCapabilities();
    }

    public CompletableFuture<JsonObject> observe(
            JsonObject request, DeepObservationRequestContext context) {
        requireOpen();
        checkRead(context);
        if (request.has("category") && !"DEBUG".equals(string(request, "category")))
            throw new ProtocolState.ProtocolException(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    409,
                    "Dedicated world internals require explicit DEBUG, not a player projection");
        if (!request.has("domains")
                || !request.get("domains").isJsonArray()
                || request.getAsJsonArray("domains").isEmpty())
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEEP_SELECTOR", 400, "Explicit nonempty server domains required");
        for (var domain : request.getAsJsonArray("domains"))
            if (!domain.isJsonPrimitive()
                    || !domain.getAsJsonPrimitive().isString()
                    || !Set.of(
                                    "world",
                                    "blocks",
                                    "entities",
                                    "block_entities",
                                    "chunks",
                                    "player",
                                    "menu",
                                    "providers")
                            .contains(domain.getAsString()))
                throw new ProtocolState.ProtocolException(
                        "INVALID_DEEP_SELECTOR", 400, "Unknown server domain");
        if (request.has("perspective")
                && !"server_authoritative".equals(string(request, "perspective")))
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE",
                    409,
                    "Dedicated resources have no client-known player perspective");
        var result = track(host.observe(request.deepCopy(), context), () -> checkRead(context));
        result.whenComplete(
                (value, error) -> {
                    if (result.isCancelled()) context.cancel("server_resource_cancelled");
                });
        return context.track(result);
    }

    public CompletableFuture<JsonObject> mutate(
            JsonObject request, DebugMutationAuthorization authorization) {
        requireOpen();
        for (String scope : READ_SCOPES)
            if (!authorization.hasScope(scope))
                throw new ProtocolState.ProtocolException(
                        "SCOPE_DENIED",
                        403,
                        "Typed server receipts require independent scope: " + scope);
        if (authorization.isCancelled())
            return CompletableFuture.failedFuture(
                    new java.util.concurrent.CancellationException("server_debug_cancelled"));
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject())
            throw new ProtocolState.ProtocolException(
                    "INVALID_RESOURCE_VERSION", 400, "expectedResourceVersion must be an object");
        return track(
                host.mutate(request.deepCopy(), authorization),
                () -> {
                    if (authorization.isCancelled())
                        throw new java.util.concurrent.CancellationException(
                                "server_debug_cancelled");
                    for (String scope : READ_SCOPES)
                        if (!authorization.hasScope(scope))
                            throw new ProtocolState.ProtocolException(
                                    "SCOPE_DENIED", 403, "Server receipt read authority revoked");
                });
    }

    public CompletableFuture<JsonObject> storageRead(JsonObject request) {
        requireOpen();
        return track(host.storageRead(request.deepCopy()), () -> {});
    }

    private void checkRead(DeepObservationRequestContext context) {
        if (context.isCancelled()
                || (context.deadlineAtMillis() > 0
                        && System.currentTimeMillis() >= context.deadlineAtMillis()))
            throw new java.util.concurrent.CancellationException(
                    "server_observation_cancelled_or_expired");
        if (!context.hasScopes(READ_SCOPES))
            throw new ProtocolState.ProtocolException(
                    "SCOPE_DENIED", 403, "Independent DEBUG world read scopes required");
    }

    private CompletableFuture<JsonObject> track(
            CompletableFuture<JsonObject> source, Runnable publication) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete(
                (value, error) -> {
                    pending.remove(result);
                    if (result.isCancelled()) source.cancel(false);
                });
        if (closed.get()) result.cancel(false);
        source.whenComplete(
                (value, failure) -> {
                    if (result.isDone()) return;
                    try {
                        requireOpen();
                        publication.run();
                        if (failure == null) result.complete(value);
                        else result.completeExceptionally(failure);
                    } catch (Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
        return result;
    }

    private void requireOpen() {
        if (closed.get())
            throw new ProtocolState.ProtocolException(
                    "RUNTIME_SHUTTING_DOWN", 409, "Dedicated world resource access is closed");
    }

    private static String string(JsonObject value, String key) {
        return value.has(key)
                        && value.get(key).isJsonPrimitive()
                        && value.getAsJsonPrimitive(key).isString()
                ? value.get(key).getAsString()
                : "";
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (CompletableFuture<?> future : pending) future.cancel(false);
        pending.clear();
        host.close();
    }
}
