package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Owner-thread, zero-player world inspection and typed block mutation, never console commands. */
public final class ServerWorldDebug implements AutoCloseable {
    public interface Host {
        void schedule(Runnable work);

        JsonObject world();

        JsonObject block(int x, int y, int z);

        void setBlock(int x, int y, int z, String blockId, JsonObject request);
    }

    private final Host host;
    private final ObservationRevisionTracker revisions = new ObservationRevisionTracker();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pending =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final String fingerprint;

    public ServerWorldDebug(Host host) {
        this.host = host;
        // This identity belongs to this live server lifetime, not an online player or a save path.
        fingerprint =
                ObservationRevisionTracker.fingerprint(new JsonPrimitive(revisions.sessionEpoch()));
    }

    public JsonObject fingerprint() {
        JsonObject value = identity("world.fingerprint");
        value.addProperty("worldFingerprint", fingerprint);
        value.addProperty("sessionEpoch", revisions.sessionEpoch());
        value.addProperty("fingerprintScope", "dedicated_server_lifetime_overworld");
        value.addProperty("perspective", "server_authoritative_live");
        value.addProperty("sessionBound", true);
        return value;
    }

    public JsonObject capabilities() {
        JsonObject value = identity("debug.capabilities");
        value.addProperty("schemaVersion", "phase9c-debug-v0");
        value.addProperty("sessionEpoch", revisions.sessionEpoch());
        value.addProperty("formal", true);
        value.addProperty("requiresDebugArm", true);
        value.addProperty("requiresWorldFingerprint", true);
        value.addProperty("requiresResourceVersion", true);
        value.addProperty("persistentStorageMutation", false);
        value.addProperty("arbitraryReflection", false);
        value.addProperty("arbitraryPacketInjection", false);
        JsonObject deployments = new JsonObject();
        deployments.addProperty(
                "dedicatedServer", "local_server_operator_explicit_debug_authority");
        value.add("deployments", deployments);
        JsonArray operations = new JsonArray();
        JsonObject block = new JsonObject();
        block.addProperty("operation", "world.block.set");
        block.addProperty("status", "available");
        block.addProperty("requiredScope", "debug.world");
        block.addProperty("requiresArm", true);
        block.addProperty("resourceVersionPrecondition", true);
        block.addProperty("batch", true);
        operations.add(block);
        value.add("operations", operations);
        value.addProperty("dimension", "minecraft:overworld");
        value.addProperty("onlinePlayerRequired", false);
        value.addProperty("noAutomaticChunkLoading", true);
        JsonObject inspection = WorldDebugInspection.contract();
        JsonArray inspected = new JsonArray();
        for (var entry : inspection.getAsJsonArray("operations"))
            if ("world.block.set".equals(entry.getAsJsonObject().get("operation").getAsString()))
                inspected.add(entry);
        inspection.add("operations", inspected);
        inspection.addProperty("dimension", "minecraft:overworld");
        value.add("resourceInspection", inspection);
        value.addProperty(
                "unsupported",
                "player,entity,block_entity,menu,ui,provider mutations; other dimensions");
        return value;
    }

    public JsonObject observationCapabilities() {
        JsonObject value = identity("deep_observation.capabilities");
        value.addProperty("schemaVersion", "phase9b-observation-v0");
        value.addProperty("formal", true);
        value.addProperty("phase9aDiagnosticsStatus", "unavailable");
        value.add("perspectives", strings("server_authoritative"));
        value.add("domains", strings("world", "blocks"));
        value.add("providers", new JsonArray());
        JsonObject budgets = new JsonObject();
        budgets.addProperty("maxResponseBytes", 524288);
        value.add("budgets", budgets);
        value.addProperty("maxBlocks", 64);
        value.addProperty("dimension", "minecraft:overworld");
        value.addProperty("onlinePlayerRequired", false);
        return value;
    }

    public CompletableFuture<JsonObject> observe(
            JsonObject request, DeepObservationRequestContext context) {
        JsonObject query = request.deepCopy();
        if (!"server_authoritative".equals(string(query, "perspective")))
            throw unavailable("Dedicated world inspection requires server_authoritative");
        if (!query.has("domains")
                || !query.get("domains").isJsonArray()
                || query.getAsJsonArray("domains").isEmpty()) throw invalid("domains required");
        Set<String> domains = new java.util.LinkedHashSet<>();
        for (var entry : query.getAsJsonArray("domains")) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
                throw invalid("Invalid domain");
            domains.add(entry.getAsString());
        }
        if (!Set.of("world", "blocks").containsAll(domains))
            throw unavailable("Only world and blocks are implemented on this server");
        for (String flag :
                Set.of("includeSerializedBlockEntities", "includeProviderData", "allowReadEffects"))
            if (query.has(flag) && !query.get(flag).equals(new JsonPrimitive(false)))
                throw unavailable(flag + " is not supported");
        if (query.has("projection") || query.has("providerIds") || query.has("providerQuery"))
            throw unavailable("Projection and providers are not supported on this server");
        JsonObject selector =
                query.has("selector") && query.get("selector").isJsonObject()
                        ? query.getAsJsonObject("selector")
                        : new JsonObject();
        if (query.has("selector") && !query.get("selector").isJsonObject())
            throw invalid("selector must be an object");
        if (!Set.of("blocks").containsAll(selector.keySet()))
            throw unavailable(
                    "Server queries require explicit block coordinates, not a player anchor");
        JsonArray blocks =
                selector.has("blocks") && selector.get("blocks").isJsonArray()
                        ? selector.getAsJsonArray("blocks")
                        : new JsonArray();
        if (selector.has("blocks") && !selector.get("blocks").isJsonArray())
            throw invalid("blocks must be an array");
        if (blocks.size() > 64) throw invalid("At most 64 blocks per snapshot");
        if (!domains.contains("blocks") && !blocks.isEmpty())
            throw invalid("blocks selector requires blocks domain");
        for (var block : blocks) {
            if (!block.isJsonObject()
                    || !block.getAsJsonObject().keySet().equals(Set.of("x", "y", "z")))
                throw invalid("Block requires only x,y,z");
            position(block.getAsJsonObject());
        }
        int maxBytes =
                query.has("budgets")
                                && query.get("budgets").isJsonObject()
                                && query.getAsJsonObject("budgets").has("maxResponseBytes")
                        ? integer(
                                query.getAsJsonObject("budgets"), "maxResponseBytes", 16384, 524288)
                        : 524288;
        return scheduled(
                () -> {
                    if (context.isCancelled()
                            || (context.deadlineAtMillis() > 0
                                    && System.currentTimeMillis() >= context.deadlineAtMillis()))
                        throw new java.util.concurrent.CancellationException(
                                "observation_cancelled_or_expired");
                    if (!context.hasScopes(Set.of("read", "debug", "debug.world.read")))
                        throw new ProtocolState.ProtocolException(
                                "SCOPE_DENIED",
                                403,
                                "Independent DEBUG world read scopes required");
                    JsonObject response = identity("deep_observation.snapshot");
                    response.addProperty("schemaVersion", "phase9b-observation-v0");
                    response.addProperty("formal", true);
                    response.addProperty("perspective", "server_authoritative");
                    response.addProperty("sessionEpoch", revisions.sessionEpoch());
                    response.addProperty("capturedAt", System.currentTimeMillis());
                    JsonObject server = new JsonObject();
                    server.addProperty("snapshotId", UUID.randomUUID().toString());
                    JsonObject world = host.world();
                    server.add("serverTick", world.get("serverTick"));
                    if (domains.contains("world")) server.add("world", world);
                    JsonArray values = new JsonArray(), refs = new JsonArray();
                    for (var block : blocks) {
                        int[] pos = position(block.getAsJsonObject());
                        JsonObject value = host.block(pos[0], pos[1], pos[2]);
                        values.add(value);
                        if (value.get("available").getAsBoolean()) refs.add(revision(value));
                    }
                    if (domains.contains("blocks")) server.add("blocks", values);
                    response.add("server", server);
                    response.add("resourceRevisionRefs", refs);
                    JsonObject metadata = new JsonObject();
                    metadata.addProperty("perspective", "server_authoritative");
                    metadata.addProperty("acquisition", "owner_thread_snapshot");
                    metadata.addProperty("completeness", "projected");
                    metadata.addProperty("consistency", "owner_thread_snapshot");
                    metadata.addProperty("readEffects", "none");
                    metadata.addProperty("alignmentQuality", "single_server_owner_snapshot");
                    metadata.add("capturedAt", response.get("capturedAt"));
                    metadata.add("snapshotId", server.get("snapshotId"));
                    metadata.addProperty("sessionEpoch", revisions.sessionEpoch());
                    response.add("metadata", metadata);
                    response.add("providers", new JsonArray());
                    response.add("limitations", new JsonArray());
                    response.addProperty("maxResponseBytes", maxBytes);
                    if (response.toString().getBytes(StandardCharsets.UTF_8).length > maxBytes)
                        throw new ProtocolState.ProtocolException(
                                "RESPONSE_BUDGET_EXCEEDED",
                                413,
                                "Deep observation response exceeds maxResponseBytes");
                    return response;
                });
    }

    public CompletableFuture<JsonObject> mutate(
            JsonObject request, DebugMutationAuthorization authorization) {
        // This contract returns full before/after state; reject before mutation, not at
        // publication.
        for (String scope : Set.of("read", "debug", "debug.world.read"))
            if (!authorization.hasScope(scope))
                throw new ProtocolState.ProtocolException(
                        "SCOPE_DENIED",
                        403,
                        "Typed server mutation receipts require independent scope: " + scope);
        JsonObject mutation = request.deepCopy();
        if (!Set.of(
                        "operation",
                        "worldFingerprint",
                        "expectedResourceVersion",
                        "debugOperationId",
                        "x",
                        "y",
                        "z",
                        "blockId",
                        "properties",
                        "expectedBlockId",
                        "expectedProperties")
                .containsAll(mutation.keySet()))
            throw invalid("Unsupported field for dedicated world.block.set");
        if (!mutation.has("expectedResourceVersion")
                || !mutation.get("expectedResourceVersion").isJsonObject())
            throw new ProtocolState.ProtocolException(
                    "INVALID_RESOURCE_VERSION", 400, "expectedResourceVersion must be an object");
        if (!"world.block.set".equals(string(mutation, "operation")))
            return CompletableFuture.failedFuture(
                    unavailable("Only typed world.block.set is implemented on this server"));
        int[] pos = position(mutation);
        return scheduled(
                () -> {
                    if (authorization.isCancelled())
                        throw new java.util.concurrent.CancellationException("debug_cancelled");
                    try (var permit =
                            authorization.authorize(
                                    fingerprint, revisions.sessionEpoch(), "world", "world")) {
                        if (!fingerprint.equals(string(mutation, "worldFingerprint")))
                            throw new ProtocolState.ProtocolException(
                                    "WORLD_FINGERPRINT_MISMATCH",
                                    409,
                                    "Mutation targets another world lifetime");
                        JsonObject before = host.block(pos[0], pos[1], pos[2]);
                        if (!before.get("available").getAsBoolean())
                            throw new ProtocolState.ProtocolException(
                                    "CHUNK_NOT_LOADED",
                                    409,
                                    "Block is not loaded; no load requested");
                        JsonObject beforeRef = revision(before);
                        ResourceVersionVerifier.verifyAuthorized(
                                mutation.getAsJsonObject("expectedResourceVersion"), beforeRef);
                        if (mutation.has("expectedBlockId")
                                && !mutation.get("expectedBlockId").equals(before.get("blockId")))
                            throw new ProtocolState.ProtocolException(
                                    "VALUE_PRECONDITION_FAILED",
                                    409,
                                    "expectedBlockId does not match");
                        DebugBlockStateRequest.verifyExpected(
                                mutation, before.getAsJsonObject("properties"));
                        try (LoadedChunkMutationScope noLoad = LoadedChunkMutationScope.open()) {
                            try {
                                host.setBlock(
                                        pos[0],
                                        pos[1],
                                        pos[2],
                                        string(mutation, "blockId"),
                                        mutation);
                            } catch (RuntimeException failure) {
                                if (noLoad.blockedAccess() == null) throw failure;
                                JsonObject partial =
                                        receipt(
                                                mutation,
                                                before,
                                                beforeRef,
                                                host.block(pos[0], pos[1], pos[2]));
                                partial.addProperty("nativeEffectsComplete", false);
                                partial.addProperty("mutationMayHaveApplied", true);
                                partial.addProperty("retrySafe", false);
                                partial.add("blockedChunk", noLoad.blockedAccess());
                                JsonObject details = new JsonObject();
                                details.add("partialMutation", partial);
                                throw new ProtocolState.ProtocolException(
                                        "DEBUG_MUTATION_PARTIAL_NOT_LOADED",
                                        409,
                                        "Native effects reached an unloaded dependency; applied effects are not rolled back",
                                        details);
                            }
                        }
                        return receipt(
                                mutation, before, beforeRef, host.block(pos[0], pos[1], pos[2]));
                    }
                });
    }

    private JsonObject receipt(
            JsonObject request, JsonObject before, JsonObject beforeRef, JsonObject after) {
        JsonObject value = identity("debug.mutation.result");
        value.addProperty("schemaVersion", "phase9c-debug-v0");
        value.addProperty(
                "debugOperationId",
                request.has("debugOperationId")
                        ? string(request, "debugOperationId")
                        : UUID.randomUUID().toString());
        value.add("auditReference", value.get("debugOperationId"));
        value.addProperty("operation", "world.block.set");
        value.addProperty("namespace", "world");
        value.addProperty("mechanism", "vanilla_server_level_set_block");
        value.addProperty("invariants", "partial_invariants");
        value.addProperty("synchronization", "normal_network_sync");
        value.addProperty("evidence", "diagnostic");
        value.addProperty("evidenceAuthority", "DEBUG_PRIVILEGED");
        value.addProperty("gameplayEvidence", false);
        value.addProperty("worldFingerprint", fingerprint);
        value.add("before", before);
        value.add("after", after);
        value.add("beforeResourceVersion", ResourceVersionVerifier.token(beforeRef));
        value.add("afterResourceVersion", ResourceVersionVerifier.token(revision(after)));
        JsonObject conditions = new JsonObject();
        conditions.addProperty("resourceVersion", "passed");
        conditions.addProperty("value", "passed");
        conditions.addProperty("ownerThread", "server_thread");
        value.add("preconditions", conditions);
        value.addProperty("cleanup", "caller_must_restore_test_state");
        value.addProperty(
                "sideEffects",
                "native_neighbor_updates_and_block_entity_replacement; no_multi_block_rollback");
        return value;
    }

    private JsonObject revision(JsonObject block) {
        return revisions.revision(
                "block",
                "minecraft:overworld@" + string(block, "key"),
                string(block, "lifecycleId"),
                block);
    }

    private <T> CompletableFuture<T> scheduled(Supplier<T> capture) {
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((value, error) -> pending.remove(result));
        if (closed.get()) {
            result.cancel(false);
            return result;
        }
        try {
            host.schedule(
                    () -> {
                        if (result.isDone()) return;
                        try {
                            if (closed.get())
                                throw new java.util.concurrent.CancellationException(
                                        "server_debug_closed");
                            result.complete(capture.get());
                        } catch (Throwable error) {
                            result.completeExceptionally(error);
                        }
                    });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private static int[] position(JsonObject value) {
        return new int[] {
            integer(value, "x", -29999984, 29999983),
            integer(value, "y", -19999999, 19999999),
            integer(value, "z", -29999984, 29999983)
        };
    }

    private static int integer(JsonObject value, String field, int min, int max) {
        try {
            if (!value.get(field).isJsonPrimitive() || !value.getAsJsonPrimitive(field).isNumber())
                throw invalid(field);
            int number = new java.math.BigDecimal(value.get(field).getAsString()).intValueExact();
            if (number < min || number > max) throw invalid(field);
            return number;
        } catch (ArithmeticException | NullPointerException | NumberFormatException error) {
            throw invalid(field);
        }
    }

    private static String string(JsonObject value, String field) {
        if (!value.has(field)
                || !value.get(field).isJsonPrimitive()
                || !value.getAsJsonPrimitive(field).isString()
                || value.get(field).getAsString().isBlank()) throw invalid(field);
        return value.get(field).getAsString();
    }

    private static JsonArray strings(String... entries) {
        JsonArray values = new JsonArray();
        for (String entry : entries) values.add(entry);
        return values;
    }

    private static JsonObject identity(String type) {
        JsonObject value = new JsonObject();
        value.addProperty("type", type);
        value.addProperty("domain", "WORLD");
        value.addProperty("dataCategory", "DEBUG");
        value.addProperty("authority", "runtime_internal");
        value.addProperty("actor", "server_operator");
        value.addProperty("deploymentRole", "DEDICATED_SERVER");
        value.addProperty("chunkLoadRequested", false);
        value.addProperty("storageAccessed", false);
        return value;
    }

    private static ProtocolState.ProtocolException invalid(String message) {
        return new ProtocolState.ProtocolException("INVALID_DEBUG_REQUEST", 400, message);
    }

    private static ProtocolState.ProtocolException unavailable(String message) {
        return new ProtocolState.ProtocolException("CAPABILITY_UNAVAILABLE", 409, message);
    }

    @Override
    public void close() {
        closed.set(true);
        for (var work : pending) work.cancel(false);
    }
}
