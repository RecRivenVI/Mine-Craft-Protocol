package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.recrivenvi.minecraftprotocol.core.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/**
 * The same Target-native world implementation serves integrated, dedicated and delegated owners.
 */
public final class DedicatedWorldDebug implements ServerWorldDebug.Host {
    private final MinecraftServer server;
    private final Phase9ASpikeEngine engine;
    private final String fingerprint;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pending =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DedicatedWorldDebug(MinecraftServer server, String target) {
        this.server = java.util.Objects.requireNonNull(server);
        this.engine = new Phase9ASpikeEngine(target);
        this.fingerprint =
                ObservationRevisionTracker.fingerprint(new JsonPrimitive(engine.sessionEpoch()));
        engine.observeStorageLifecycle(server, server.isCurrentlySaving(), false);
        engine.installProviderDispatcher(
                (provider, context, affinity) ->
                        "server_thread".equals(affinity)
                                ? owner(
                                        () ->
                                                ProviderExecutionEngine.enter(
                                                        provider, context, server.isSameThread()))
                                : CompletableFuture.completedFuture(
                                        ProviderExecutionEngine.Entry.unsupported(affinity)));
        engine.installProviderMutationDispatcher(
                (provider, context, affinity, authority) ->
                        "server_thread".equals(affinity)
                                ? owner(
                                        () -> {
                                            var permit =
                                                    authority.authorize(
                                                            fingerprint,
                                                            engine.sessionEpoch(),
                                                            "provider",
                                                            "provider");
                                            return ProviderExecutionEngine.enterMutation(
                                                    provider,
                                                    context,
                                                    server.isSameThread(),
                                                    permit);
                                        })
                                : CompletableFuture.completedFuture(
                                        ProviderExecutionEngine.MutationEntry.unsupported(
                                                affinity)));
    }

    @Override
    public JsonObject fingerprint() {
        JsonObject value = new JsonObject();
        value.addProperty("type", "world.fingerprint");
        value.addProperty("worldFingerprint", fingerprint);
        value.addProperty("sessionEpoch", engine.sessionEpoch());
        value.addProperty("fingerprintScope", "server_lifetime_all_existing_dimensions");
        value.addProperty("sessionBound", true);
        value.addProperty("authority", "SERVER_AUTHORITATIVE");
        value.addProperty("dataCategory", "DEBUG");
        return value;
    }

    @Override
    public JsonObject capabilities() {
        JsonObject value = engine.formalDebugCapabilities();
        value.add("resourceInspection", WorldDebugInspection.contract());
        value.addProperty("onlinePlayerRequired", false);
        for (var entry : value.getAsJsonArray("operations")) {
            var operation = entry.getAsJsonObject();
            String id = operation.get("operation").getAsString();
            if (id.startsWith("client.") || id.startsWith("network.")) {
                operation.addProperty("status", "not_applicable");
                operation.addProperty("limitation", "dedicated_server_has_no_client_surface");
            }
        }
        value.addProperty("playerSelection", "actorUuid_required_for_player_and_menu_operations");
        value.addProperty("dimensionSelection", "existing_dimension_only; default_overworld");
        value.addProperty("noAutomaticChunkLoading", true);
        value.getAsJsonObject("deployments").addProperty("dedicatedServer", "available");
        return value;
    }

    @Override
    public JsonObject observationCapabilities() {
        JsonObject value = engine.formalCapabilities();
        com.google.gson.JsonArray perspectives = new com.google.gson.JsonArray();
        perspectives.add("server_authoritative");
        value.add("perspectives", perspectives);
        value.addProperty("onlinePlayerRequired", false);
        value.addProperty("playerSelection", "selector.playerUuid");
        value.addProperty(
                "anchorSelection",
                "selector.anchor; explicit_integer_coordinates; defaults_selected_player_or_origin");
        value.addProperty("dimensionSelection", "selector.dimension; existing_dimensions_only");
        value.addProperty("clientSurfacesAvailable", false);
        return value;
    }

    @Override
    public CompletableFuture<JsonObject> observe(
            JsonObject request, DeepObservationRequestContext context) {
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                owner(
                        () -> {
                            check(context);
                            return engine.withServerSelection(
                                    server,
                                    request,
                                    player -> engine.captureFormal(server, player, request));
                        }),
                canonical ->
                        io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                                engine.prepareFormalServerSnapshot(request, canonical),
                                prepared -> engine.formalize(request, null, prepared, context)));
    }

    private void check(DeepObservationRequestContext context) {
        if (context.isCancelled()
                || (context.deadlineAtMillis() > 0
                        && System.currentTimeMillis() >= context.deadlineAtMillis()))
            throw new java.util.concurrent.CancellationException(
                    "server_read_cancelled_or_expired");
        if (!context.hasScopes(Set.of("read", "debug", "debug.world.read")))
            throw new ProtocolState.ProtocolException(
                    "SCOPE_DENIED", 403, "Independent server DEBUG read scopes required");
    }

    @Override
    public CompletableFuture<JsonObject> mutate(
            JsonObject request, DebugMutationAuthorization authority) {
        if ("provider.mutate"
                .equals(request.has("operation") ? request.get("operation").getAsString() : ""))
            return engine.debugProviderMutation(request, authority);
        return owner(
                () -> {
                    if (authority.isCancelled())
                        throw new java.util.concurrent.CancellationException(
                                "server_debug_cancelled");
                    String operation = request.get("operation").getAsString();
                    String domain = operation.substring(0, operation.indexOf('.'));
                    Map<String, String> domains = new java.util.LinkedHashMap<>();
                    domains.put(domain, domain);
                    if (operation.equals("player.vehicle.set")) domains.put("entity", "entity");
                    try (var permit =
                            authority.authorizeAll(fingerprint, engine.sessionEpoch(), domains)) {
                        return engine.withServerSelection(
                                server,
                                request,
                                player -> {
                                    JsonObject nativeRequest = request.deepCopy();
                                    nativeRequest.remove("actorUuid");
                                    nativeRequest.remove("dimension");
                                    return engine.debugMutation(
                                            server, player, nativeRequest, fingerprint);
                                });
                    }
                });
    }

    @Override
    public CompletableFuture<JsonObject> storageRead(JsonObject request) {
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                owner(
                        () -> {
                            engine.observeStorageLifecycle(
                                    server, server.isCurrentlySaving(), false);
                            return engine.withServerSelection(
                                    server,
                                    request,
                                    player -> engine.storageRequest(server, player, request));
                        }),
                engine::readStorage);
    }

    private <T> CompletableFuture<T> owner(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((value, error) -> pending.remove(result));
        if (closed.get()) {
            result.cancel(false);
            return result;
        }
        try {
            server.execute(
                    () -> {
                        if (result.isDone()) return;
                        try {
                            if (closed.get() || !server.isRunning())
                                throw new java.util.concurrent.CancellationException(
                                        "server_resource_owner_closed");
                            result.complete(action.get());
                        } catch (Throwable error) {
                            result.completeExceptionally(error);
                        }
                    });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (CompletableFuture<?> future : pending) future.cancel(false);
        pending.clear();
        engine.close();
    }
}
