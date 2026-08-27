package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.api.ReadProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class ObservationEngine {
    private static final int MAX_FRAME_READS = 32;

    private final ProbeService service;
    private final Map<String, BuiltinProvider> builtins = new LinkedHashMap<>();
    private final AtomicLong providerRevision = new AtomicLong();
    private final AtomicLong stateFrameSequence = new AtomicLong();

    ObservationEngine(ProbeService service) {
        this.service = service;
        this.register("minecraft:client/player", "client_known", "client_thread", "client_live",
                query -> service.playerState());
        this.register("minecraft:client/world/block", "client_known_live", "client_thread", "client_live",
                query -> service.blockState(requiredInt(query, "x"), requiredInt(query, "y"), requiredInt(query, "z")));
        this.register("minecraft:client/world/entities", "client_known_live", "client_thread", "client_live",
                query -> service.entities(doubleValue(query, "radius", 16.0)));
        this.register("minecraft:server/player", "server_authoritative_live", "server_thread", "server_authority_runtime",
                query -> service.serverPlayerState());
        this.register("minecraft:server/world/block", "server_authoritative_live", "server_thread", "server_authority_runtime",
                query -> service.serverBlockState(requiredInt(query, "x"), requiredInt(query, "y"), requiredInt(query, "z")));
        this.register("minecraft:server/world/entities", "server_authoritative_live", "server_thread", "server_authority_runtime",
                query -> service.serverEntities(doubleValue(query, "radius", 16.0)));
        this.register("minecraft:capture/info", "client_render_live", "render_thread", "render_runtime",
                query -> service.captureInfo());
    }

    JsonObject descriptors() {
        JsonArray providers = new JsonArray();
        for (Map.Entry<String, BuiltinProvider> entry : this.builtins.entrySet()) {
            providers.add(descriptor(
                    entry.getKey(), entry.getValue().perspective(), entry.getValue().threadAffinity(),
                    entry.getValue().source(), "runtime", false));
        }
        for (ReadProvider provider : MinecraftProtocolProviders.snapshot()) {
            providers.add(descriptor(
                    provider.id(), provider.perspective(), provider.threadAffinity(),
                    "registered_provider", "untrusted_mod_provider", true));
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "provider.descriptors");
        json.addProperty("dataSource", "LIVE");
        json.addProperty("persistentStorageAvailable", false);
        json.add("providers", providers);
        return json;
    }

    CompletableFuture<JsonObject> read(JsonObject request) {
        if (!request.has("providerId")) {
            return failed("INVALID_PROVIDER_REQUEST", 400, "providerId is required");
        }
        String providerId = request.get("providerId").getAsString();
        JsonObject query = request.has("query") && request.get("query").isJsonObject()
                ? request.getAsJsonObject("query").deepCopy() : new JsonObject();
        BuiltinProvider builtin = this.builtins.get(providerId);
        if (builtin != null) {
            return builtin.reader().apply(query).thenApply(data -> {
                String source = data != null && data.has("source")
                        ? data.get("source").getAsString() : builtin.source();
                return wrap(providerId, builtin.perspective(), builtin.threadAffinity(), source,
                        "runtime", false, query, data);
            });
        }
        ReadProvider provider = MinecraftProtocolProviders.snapshot().stream()
                .filter(candidate -> candidate.id().equals(providerId))
                .findFirst()
                .orElse(null);
        if (provider == null) return failed("PROVIDER_NOT_FOUND", 404, "Unknown provider: " + providerId);
        try {
            return provider.read(query.deepCopy()).thenApply(data -> wrap(
                    provider.id(), provider.perspective(), provider.threadAffinity(), "registered_provider",
                    "untrusted_mod_provider", true, query, data));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    CompletableFuture<JsonObject> stateFrame(JsonObject request) {
        if (!request.has("reads") || !request.get("reads").isJsonArray()) {
            return failed("INVALID_STATE_FRAME", 400, "State Frame requires reads");
        }
        JsonArray reads = request.getAsJsonArray("reads");
        if (reads.isEmpty() || reads.size() > MAX_FRAME_READS) {
            return failed("INVALID_STATE_FRAME", 400, "State Frame supports 1 to 32 reads");
        }
        long startedAtMillis = System.currentTimeMillis();
        String stateFrameId = UUID.randomUUID().toString();
        long sequence = this.stateFrameSequence.incrementAndGet();
        List<CompletableFuture<JsonObject>> futures = new java.util.ArrayList<>();
        for (JsonElement element : reads) {
            if (!element.isJsonObject()) return failed("INVALID_STATE_FRAME", 400, "Every read must be an object");
            futures.add(this.read(element.getAsJsonObject()));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            JsonArray results = new JsonArray();
            for (CompletableFuture<JsonObject> future : futures) results.add(future.join().deepCopy());
            JsonObject json = new JsonObject();
            json.addProperty("type", "state.frame");
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
            JsonObject query,
            JsonObject data) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "provider.read");
        json.addProperty("providerId", providerId);
        json.addProperty("providerRevision", this.providerRevision.incrementAndGet());
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

    private static JsonObject descriptor(
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
        return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(code, status, message));
    }

    private record BuiltinProvider(
            String perspective,
            String threadAffinity,
            String source,
            Function<JsonObject, CompletableFuture<JsonObject>> reader) {
    }
}
