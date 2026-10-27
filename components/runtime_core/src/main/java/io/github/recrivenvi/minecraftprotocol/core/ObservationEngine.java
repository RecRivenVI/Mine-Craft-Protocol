package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.api.ReadProvider;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class ObservationEngine {
    private static final int MAX_FRAME_READS = 32;

    private final ProbeService service;
    private final WorldReadEngine worldReads;
    private final Map<String, BuiltinProvider> builtins = new LinkedHashMap<>();
    private final AtomicLong providerRevision = new AtomicLong();
    private final AtomicLong userProviderRevision = new AtomicLong();
    private final AtomicLong stateFrameSequence = new AtomicLong();
    private final AtomicLong userStateFrameSequence = new AtomicLong();

    ObservationEngine(ProbeService service, WorldReadEngine worldReads) {
        this.service = service;
        this.worldReads = worldReads;
        if (service instanceof ServerConsoleService console) {
            this.register(
                    "minecraft:server/console",
                    "server_console",
                    "server_thread",
                    "server_console_ring",
                    query ->
                            CompletableFuture.completedFuture(
                                    console.console()
                                            .read(
                                                    query.has("after")
                                                            ? query.get("after").getAsLong()
                                                            : 0,
                                                    query.has("limit")
                                                            ? query.get("limit").getAsInt()
                                                            : 128)));
            this.register(
                    "minecraft:server/world/deep",
                    "server_authoritative",
                    "server_thread",
                    "server_authority_runtime",
                    worldReads::deepServer);
            return;
        }
        this.register(
                "minecraft:client/player",
                "client_known",
                "client_thread",
                "client_live",
                query -> service.playerState());
        this.register(
                "minecraft:client/world/block",
                "client_known_live",
                "client_thread",
                "client_live",
                query ->
                        service.blockState(
                                requiredInt(query, "x"),
                                requiredInt(query, "y"),
                                requiredInt(query, "z")));
        this.register(
                "minecraft:client/world/entities",
                "client_known_live",
                "client_thread",
                "client_live",
                query -> service.entities(doubleValue(query, "radius", 16.0)));
        this.register(
                "minecraft:server/player",
                "server_authoritative_live",
                "server_thread",
                "server_authority_runtime",
                query -> service.serverPlayerState());
        this.register(
                "minecraft:server/world/block",
                "server_authoritative_live",
                "server_thread",
                "server_authority_runtime",
                query ->
                        service.serverBlockState(
                                requiredInt(query, "x"),
                                requiredInt(query, "y"),
                                requiredInt(query, "z")));
        this.register(
                "minecraft:server/world/entities",
                "server_authoritative_live",
                "server_thread",
                "server_authority_runtime",
                query -> service.serverEntities(doubleValue(query, "radius", 16.0)));
        this.register(
                "minecraft:client/world/scene",
                "client_known_live",
                "client_thread",
                "bounded_client_world_model",
                query -> service.worldPresentation());
        this.register(
                "minecraft:capture/info",
                "client_render_live",
                "render_thread",
                "render_runtime",
                query -> service.captureInfo());
    }

    JsonObject descriptors() {
        JsonArray providers = new JsonArray();
        for (Map.Entry<String, BuiltinProvider> entry : this.builtins.entrySet()) {
            providers.add(
                    descriptor(
                            entry.getKey(),
                            entry.getValue().perspective(),
                            entry.getValue().threadAffinity(),
                            entry.getValue().source(),
                            "runtime",
                            false));
        }
        for (ReadProvider provider : MinecraftProtocolProviders.snapshot()) {
            providers.add(
                    descriptor(
                            provider.id(),
                            provider.perspective(),
                            provider.threadAffinity(),
                            "registered_provider",
                            "untrusted_mod_provider",
                            true));
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "provider.descriptors");
        json.addProperty("dataSource", "LIVE");
        json.addProperty("persistentStorageAvailable", false);
        json.add("providers", providers);
        return json;
    }

    CompletableFuture<JsonObject> read(JsonObject request) {
        String category = validateRead(request);
        return worldReads.guarded(category, () -> captureRead(request, category));
    }

    private CompletableFuture<JsonObject> captureRead(JsonObject request, String category) {
        if (!request.has("providerId")) {
            return failed("INVALID_PROVIDER_REQUEST", 400, "providerId is required");
        }
        String providerId = request.get("providerId").getAsString();
        JsonObject query =
                request.has("query") && request.get("query").isJsonObject()
                        ? request.getAsJsonObject("query").deepCopy()
                        : new JsonObject();
        BuiltinProvider builtin = this.builtins.get(providerId);
        if (builtin != null) {
            CompletableFuture<JsonObject> captured;
            if (providerId.equals("minecraft:server/console")) {
                Runnable guard = worldReads.consoleGuard(category);
                captured =
                        CancellableWork.map(
                                builtin.reader().apply(query),
                                value -> {
                                    guard.run();
                                    return value;
                                });
            } else if (providerId.equals("minecraft:server/world/deep")) {
                query.addProperty("category", category);
                captured = builtin.reader().apply(query);
            } else if (providerId.equals("minecraft:client/world/scene")) {
                captured = worldReads.scene(category, query);
            } else if (providerId.equals("minecraft:capture/info")) {
                captured = worldReads.guarded(category, () -> builtin.reader().apply(query));
            } else {
                captured =
                        worldReads.read(
                                category,
                                providerId.startsWith("minecraft:client/")
                                        ? "client/"
                                                + providerId.substring(
                                                        providerId.lastIndexOf('/') + 1)
                                        : "server/internal",
                                query,
                                () -> builtin.reader().apply(query));
            }
            return CancellableWork.map(
                    captured,
                    data -> {
                        String source =
                                data != null && data.has("source")
                                        ? data.get("source").getAsString()
                                        : builtin.source();
                        return wrap(
                                providerId,
                                builtin.perspective(),
                                builtin.threadAffinity(),
                                source,
                                "runtime",
                                false,
                                category,
                                query,
                                data);
                    });
        }
        ReadProvider provider =
                MinecraftProtocolProviders.snapshot().stream()
                        .filter(candidate -> candidate.id().equals(providerId))
                        .findFirst()
                        .orElse(null);
        if (provider == null)
            return failed("PROVIDER_NOT_FOUND", 404, "Unknown provider: " + providerId);
        try {
            return CancellableWork.map(
                    worldReads.internal(category, () -> provider.read(query.deepCopy())),
                    data ->
                            wrap(
                                    provider.id(),
                                    provider.perspective(),
                                    provider.threadAffinity(),
                                    "registered_provider",
                                    "untrusted_mod_provider",
                                    true,
                                    category,
                                    query,
                                    data));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    CompletableFuture<JsonObject> stateFrame(JsonObject request) {
        String category = worldReads.category(request).name();
        if (!request.has("reads") || !request.get("reads").isJsonArray()) {
            return failed("INVALID_STATE_FRAME", 400, "State Frame requires reads");
        }
        JsonArray reads = request.getAsJsonArray("reads");
        if (reads.isEmpty() || reads.size() > MAX_FRAME_READS) {
            return failed("INVALID_STATE_FRAME", 400, "State Frame supports 1 to 32 reads");
        }
        List<JsonObject> normalized = new java.util.ArrayList<>();
        for (JsonElement element : reads) {
            if (!element.isJsonObject())
                return failed("INVALID_STATE_FRAME", 400, "Every read must be an object");
            JsonObject read = element.getAsJsonObject().deepCopy();
            if (read.has("category") && !category.equals(worldReads.category(read).name()))
                return failed(
                        "WORLD_CATEGORY_MIXED", 400, "State Frame reads must share one category");
            read.addProperty("category", category);
            validateRead(read);
            normalized.add(read);
        }
        Runnable publication = worldReads.publicationGuard(category);
        long startedAtMillis = System.currentTimeMillis();
        String stateFrameId = UUID.randomUUID().toString();
        long sequence =
                (category.equals("USER") ? userStateFrameSequence : stateFrameSequence)
                        .incrementAndGet();
        List<CompletableFuture<JsonObject>> futures = new java.util.ArrayList<>();
        try {
            for (JsonObject read : normalized) futures.add(this.read(read));
        } catch (Throwable failure) {
            futures.forEach(future -> future.cancel(false));
            publication.run();
            return CompletableFuture.failedFuture(failure);
        }
        CompletableFuture<Void> all = CancellableWork.all(futures);
        return CancellableWork.handle(
                all,
                (ignored, failure) -> {
                    publication.run();
                    if (failure != null)
                        throw new java.util.concurrent.CompletionException(failure);
                    JsonArray results = new JsonArray();
                    for (CompletableFuture<JsonObject> future : futures)
                        results.add(future.join().deepCopy());
                    JsonObject json = new JsonObject();
                    json.addProperty("type", "state.frame");
                    json.addProperty(
                            "domain",
                            normalized.stream()
                                            .allMatch(
                                                    read ->
                                                            "minecraft:server/console"
                                                                    .equals(
                                                                            read.get("providerId")
                                                                                    .getAsString()))
                                    ? "UI"
                                    : "WORLD");
                    json.addProperty("dataCategory", category);
                    json.addProperty("stateFrameId", stateFrameId);
                    json.addProperty("stateFrameSequence", sequence);
                    json.addProperty("consistency", "coordinated_best_effort");
                    json.addProperty("dataSource", "LIVE");
                    json.addProperty("storageAccessed", false);
                    json.addProperty("startedAtMillis", startedAtMillis);
                    json.addProperty("completedAtMillis", System.currentTimeMillis());
                    json.add("reads", results);
                    return json;
                });
    }

    String validateRead(JsonObject request) {
        String category = worldReads.category(request).name();
        if (!request.has("providerId")
                || !request.get("providerId").isJsonPrimitive()
                || !request.getAsJsonPrimitive("providerId").isString())
            throw new ProtocolState.ProtocolException(
                    "INVALID_PROVIDER_REQUEST", 400, "providerId is required");
        String id = request.get("providerId").getAsString();
        if (category.equals("USER") && !supportsUser(id))
            throw new ProtocolState.ProtocolException(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    409,
                    "This Provider has no USER projection; no internal Provider was invoked");
        if (request.has("query") && !request.get("query").isJsonObject())
            throw new ProtocolState.ProtocolException(
                    "INVALID_PROVIDER_REQUEST", 400, "query must be an object");
        JsonObject query =
                request.has("query") ? request.getAsJsonObject("query") : new JsonObject();
        if (id.equals("minecraft:server/console")) worldReads.consoleGuard(category);
        if (category.equals("USER")
                && !id.equals("minecraft:capture/info")
                && !id.equals("minecraft:server/console")) {
            worldReads.validateUser(id.substring(id.lastIndexOf('/') + 1), query);
        }
        if (id.equals("minecraft:client/world/block")
                || id.equals("minecraft:server/world/block")) {
            for (String axis : new String[] {"x", "y", "z"}) {
                try {
                    if (!query.has(axis)
                            || !query.get(axis).isJsonPrimitive()
                            || !query.getAsJsonPrimitive(axis).isNumber())
                        throw new IllegalArgumentException();
                    query.get(axis).getAsBigDecimal().intValueExact();
                } catch (RuntimeException invalid) {
                    throw new ProtocolState.ProtocolException(
                            "INVALID_PROVIDER_REQUEST",
                            400,
                            "Block query requires exact integer coordinates");
                }
            }
        }
        if (!builtins.containsKey(id)
                && MinecraftProtocolProviders.snapshot().stream().noneMatch(p -> p.id().equals(id)))
            throw new ProtocolState.ProtocolException(
                    "PROVIDER_NOT_FOUND", 404, "Unknown Provider");
        return category;
    }

    WorldReadEngine worldReads() {
        return worldReads;
    }

    private void register(
            String id,
            String perspective,
            String threadAffinity,
            String source,
            Function<JsonObject, CompletableFuture<JsonObject>> reader) {
        this.builtins.put(id, new BuiltinProvider(perspective, threadAffinity, source, reader));
    }

    private JsonObject wrap(
            String providerId,
            String perspective,
            String threadAffinity,
            String source,
            String trust,
            boolean thirdParty,
            String category,
            JsonObject query,
            JsonObject data) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "provider.read");
        json.addProperty("domain", providerId.equals("minecraft:server/console") ? "UI" : "WORLD");
        json.addProperty("providerId", providerId);
        json.addProperty(
                "providerRevision",
                (category.equals("USER") ? userProviderRevision : providerRevision)
                        .incrementAndGet());
        json.addProperty("dataCategory", category);
        json.addProperty("querySnapshotId", UUID.randomUUID().toString());
        json.addProperty("perspective", perspective);
        json.addProperty("threadAffinity", threadAffinity);
        json.addProperty("source", source);
        json.addProperty("dataSource", "LIVE");
        json.addProperty("storageAccessed", false);
        json.addProperty("trust", trust);
        json.addProperty("thirdParty", thirdParty);
        json.add("query", query.deepCopy());
        json.add("data", data == null ? new JsonObject() : data.deepCopy());
        return json;
    }

    private boolean supportsUser(String id) {
        return id.equals("minecraft:client/player")
                || id.equals("minecraft:client/world/scene")
                || id.equals("minecraft:client/world/block")
                || id.equals("minecraft:client/world/entities")
                || id.equals("minecraft:capture/info")
                || (id.equals("minecraft:server/console")
                        && service instanceof ServerConsoleService);
    }

    private JsonObject descriptor(
            String id,
            String perspective,
            String threadAffinity,
            String source,
            String trust,
            boolean thirdParty) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("perspective", perspective);
        json.addProperty("threadAffinity", threadAffinity);
        json.addProperty("source", source);
        json.addProperty("dataSource", "LIVE");
        json.addProperty("persistentStorage", false);
        json.addProperty("trust", trust);
        json.addProperty("thirdParty", thirdParty);
        JsonArray categories = new JsonArray();
        if (supportsUser(id)) categories.add("USER");
        if (!id.equals("minecraft:client/world/scene")) categories.add("DEBUG");
        json.add("readCategories", categories);
        // Omitted request category stays USER and fails closed for internal-only Providers.
        if (supportsUser(id)) json.addProperty("defaultReadCategory", "USER");
        json.addProperty("explicitCategoryRequired", !supportsUser(id));
        return json;
    }

    private static int requiredInt(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new ProtocolState.ProtocolException("INVALID_ARGUMENT", 400, "Missing " + name);
        }
        return object.get(name).getAsInt();
    }

    private static double doubleValue(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }

    private static <T> CompletableFuture<T> failed(String code, int status, String message) {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(code, status, message));
    }

    private record BuiltinProvider(
            String perspective,
            String threadAffinity,
            String source,
            Function<JsonObject, CompletableFuture<JsonObject>> reader) {}
}
