package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.DistanceManagerAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.LevelTicksAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;

/** Experimental Phase 9A evidence collector. It is not a frozen public protocol implementation. */
final class Phase9ASpikeEngine implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int MAX_SNAPSHOTS = 32;
    private static final int MAX_ENTITIES = 128;
    private static final int MAX_BLOCK_ENTITIES = 128;
    private static final int MAX_SELECTED_BLOCKS = 64;

    private final String target;
    private final ObservationRevisionTracker revisions = new ObservationRevisionTracker();
    private final ObservationLifecycleTracker lifecycles = new ObservationLifecycleTracker();
    private final ProviderExecutionEngine providerExecution = new ProviderExecutionEngine(this.revisions);
    private final ExecutorService storageWorker;
    private final BoundedTaskExecutor revisionWorker;
    private final Map<String, JsonObject> snapshots = new LinkedHashMap<>();
    private final Map<String, JsonObject> selectors = new LinkedHashMap<>();
    private final Map<String, JsonObject> deltas = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean ticketHookVerified = new AtomicBoolean();
    private final AtomicBoolean scheduledTickHookVerified = new AtomicBoolean();

    Phase9ASpikeEngine(String target) {
        this.target = target;
        this.storageWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-phase9a-storage");
            thread.setDaemon(true);
            return thread;
        });
        this.revisionWorker = new BoundedTaskExecutor(
                "minecraft-protocol-observation-revisions", 1, 8);
    }

    void installProviderDispatcher(ProviderExecutionEngine.Dispatcher dispatcher) {
        this.providerExecution.installDispatcher(dispatcher);
    }

    JsonObject inventory() {
        JsonObject json = base("phase9a.capability_inventory");
        json.addProperty("phase", "9A");
        json.addProperty("wireProtocolFrozen", false);
        json.addProperty("persistentWriteImplemented", false);
        json.addProperty("forceLoadsChunks", false);
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("deepPlayer", "PARTIAL");
        capabilities.addProperty("deepEntity", "PARTIAL");
        capabilities.addProperty("blockEntity", "PARTIAL");
        capabilities.addProperty("chunkInternals", "PARTIAL");
        capabilities.addProperty("tickets", "REQUIRES_NEW_HOOK");
        capabilities.addProperty("scheduledTicks", "PARTIAL");
        capabilities.addProperty("debugRepresentative", "PASS");
        capabilities.addProperty("persistedChunkRead", "PASS");
        capabilities.addProperty("persistedPlayerRead", "PASS");
        capabilities.addProperty("experimentalKeyframe", "PASS");
        capabilities.addProperty("deltaCapture", "PASS");
        capabilities.addProperty("reconstruction", "PASS");
        json.add("capabilities", capabilities);
        JsonObject targetFacts = new JsonObject();
        targetFacts.addProperty("ticketModel", "DistanceManager -> TicketStorage -> TicketType");
        targetFacts.addProperty("componentModel", "data_components_and_loader_attachments");
        targetFacts.addProperty("storageModel", "RegionStorageInfo + RegionFile + NbtIo");
        targetFacts.addProperty("inventoryModel", "private_inventory_list_plus_container_projection");
        targetFacts.addProperty("scheduledTickModel", "LevelTicks count public; per-tick details require hook");
        json.add("targetFacts", targetFacts);
        return json;
    }

    JsonObject formalCapabilities() {
        JsonObject json = base("deep_observation.capabilities");
        json.addProperty("schemaVersion", "phase9b-observation-v0");
        json.addProperty("formal", true);
        json.addProperty("phase9aDiagnosticsStatus", "experimental_superseded_for_stable_agent_usage");
        JsonObject budgets = new JsonObject();
        budgets.addProperty("maxChunkRadius", 2);
        budgets.addProperty("maxEntities", MAX_ENTITIES);
        budgets.addProperty("maxBlocks", MAX_SELECTED_BLOCKS);
        budgets.addProperty("maxBlockEntities", MAX_BLOCK_ENTITIES);
        budgets.addProperty("maxProviders", 8);
        budgets.addProperty("maxSerializedBytesPerBlockEntity", 16_384);
        budgets.addProperty("maxTotalSerializedBlockEntityBytes", 65_536);
        budgets.addProperty("maxProviderBytes", 16_384);
        budgets.addProperty("maxTotalProviderBytes", 65_536);
        budgets.addProperty("maxResponseBytes", 524_288);
        budgets.addProperty("providerTimeoutMs", 250);
        budgets.addProperty("ownerThreadSoftBudgetMicros", 4_000);
        budgets.addProperty("ownerThreadHardBudgetMicros", 12_000);
        json.add("budgets", budgets);
        JsonArray perspectives = new JsonArray();
        perspectives.add("client_known");
        perspectives.add("server_authoritative");
        perspectives.add("both");
        json.add("perspectives", perspectives);
        JsonArray domains = new JsonArray();
        for (String domain : List.of("player", "entities", "blocks", "block_entities", "chunks", "world", "menu", "providers")) {
            domains.add(domain);
        }
        json.add("domains", domains);
        JsonArray providers = new JsonArray();
        for (AgentDataProviderV2 provider : MinecraftProtocolProvidersV2.snapshot()) {
            providers.add(providerDescriptor(provider.descriptor()));
        }
        json.add("providers", providers);
        json.add("providerRuntime", this.providerExecution.diagnostics());
        json.add("revisionRuntime", this.revisionWorker.diagnostics());
        json.add("lifecycleRuntime", this.lifecycles.diagnostics());
        json.addProperty("providerPolicyVersion", "phase9b1-provider-policy-v0");
        JsonObject hooks = new JsonObject();
        hooks.addProperty("ticketHook", this.ticketHookVerified.get() ? "runtime_verified" : "unverified_until_deep_observation");
        hooks.addProperty("scheduledTickHook", this.scheduledTickHookVerified.get() ? "runtime_verified" : "unverified_until_deep_observation");
        hooks.addProperty("mechanism", "read_only_mixin_accessor");
        hooks.addProperty("changesControlFlow", false);
        hooks.addProperty("fallback", "capability_partial_without_target_diagnostic");
        json.add("hooks", hooks);
        return json;
    }

    JsonObject captureFormal(MinecraftServer server, ServerPlayer player, JsonObject request) {
        JsonObject selector = new JsonObject();
        JsonObject requestedSelector = request.has("selector") && request.get("selector").isJsonObject()
                ? request.getAsJsonObject("selector") : new JsonObject();
        JsonObject budgets = request.has("budgets") && request.get("budgets").isJsonObject()
                ? request.getAsJsonObject("budgets") : new JsonObject();
        selector.addProperty("radiusChunks", bounded(integer(requestedSelector, "chunkRadius", 0), 0, 2));
        selector.addProperty("entityRadius", bounded(integer(requestedSelector, "entityRadius", 16), 0, 64));
        selector.addProperty("entityLimit", bounded(integer(budgets, "maxEntities", 64), 1, MAX_ENTITIES));
        selector.addProperty("blockEntityLimit", bounded(integer(budgets, "maxBlockEntities", 64), 1, MAX_BLOCK_ENTITIES));
        selector.addProperty("maxSerializedBytesPerBlockEntity", bounded(integer(budgets, "maxSerializedBytesPerBlockEntity", 16_384), 256, 16_384));
        selector.addProperty("maxTotalSerializedBlockEntityBytes", bounded(integer(budgets, "maxTotalSerializedBlockEntityBytes", 65_536), 1_024, 65_536));
        selector.addProperty("includeSerializedState", request.has("includeSerializedBlockEntities")
                && request.get("includeSerializedBlockEntities").getAsBoolean());
        selector.add("selectedBlocks", requestedSelector.has("blocks") && requestedSelector.get("blocks").isJsonArray()
                ? requestedSelector.getAsJsonArray("blocks").deepCopy() : new JsonArray());
        selector.add("domains", request.has("domains") && request.get("domains").isJsonArray()
                ? request.getAsJsonArray("domains").deepCopy() : allDomains());
        JsonObject snapshot = captureSnapshot(server, player, normalizedSelector(selector));
        if (snapshot.has("player")) {
            JsonObject playerSnapshot = snapshot.getAsJsonObject("player");
            playerSnapshot.remove("extendedStatus");
            playerSnapshot.remove("menuClass");
        }
        if (snapshot.has("entities")) for (JsonElement element : snapshot.getAsJsonArray("entities")) {
            JsonObject entity = element.getAsJsonObject();
            entity.remove("extensions");
            entity.remove("nonDefaultTrackedValues");
        }
        if (snapshot.has("world")) {
            snapshot.getAsJsonObject("world").remove("scheduledTickDetail");
            snapshot.getAsJsonObject("world").remove("ticketDetail");
        }
        snapshot.remove("providerTrack");
        snapshot.remove("tickets");
        return snapshot;
    }

    CompletableFuture<JsonObject> prepareFormalServerSnapshot(
            JsonObject request, JsonObject canonicalSnapshot) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new CancellationException("observation_runtime_closed"));
        }
        CompletableFuture<JsonObject> submitted = this.revisionWorker.submit(() -> {
            long revisionStarted = System.nanoTime();
            JsonArray revisionRefs = canonicalResourceRevisions(canonicalSnapshot);
            long revisionMicros = (System.nanoTime() - revisionStarted) / 1_000L;
            JsonObject visible = canonicalSnapshot.deepCopy();
            visible.add("_canonicalResourceRevisionRefs", revisionRefs);
            visible.addProperty("_revisionComputationMicros", revisionMicros);
            applyProjection(visible, request);
            visible.addProperty("type", "deep_observation.server_snapshot");
            return visible;
        });
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        submitted.whenComplete((value, error) -> {
            if (error instanceof java.util.concurrent.RejectedExecutionException) {
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "REVISION_BACKPRESSURE", 429,
                        "Detached revision queue is full"));
            } else if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(value);
            }
        });
        return result;
    }

    CompletableFuture<JsonObject> formalize(
            JsonObject request,
            JsonObject client,
            JsonObject server,
            DeepObservationRequestContext requestContext) {
        String perspective = request.has("perspective") ? request.get("perspective").getAsString() : "server_authoritative";
        JsonObject response = base("deep_observation.snapshot");
        response.addProperty("schemaVersion", "phase9b-observation-v0");
        response.addProperty("formal", true);
        response.addProperty("perspective", perspective);
        response.addProperty("sessionEpoch", this.revisions.sessionEpoch());
        response.addProperty("capturedAt", System.currentTimeMillis());
        if (client != null) response.add("client", client.deepCopy());
        JsonObject visibleServer = server == null ? null : server.deepCopy();
        if (visibleServer != null) {
            visibleServer.remove("_canonicalResourceRevisionRefs");
            visibleServer.remove("_revisionComputationMicros");
            response.add("server", visibleServer);
        }
        JsonObject metadata = observationMetadata(client, server);
        metadata.add("revisionTracker", this.revisions.diagnostics());
        metadata.addProperty("revisionComputationMicros",
                server != null && server.has("_revisionComputationMicros")
                        ? server.get("_revisionComputationMicros").getAsLong() : 0L);
        metadata.addProperty("revisionThread", "detached_revision_worker");
        metadata.addProperty("revisionQueueDepth", this.revisionWorker.queueDepth());
        response.add("metadata", metadata);
        JsonArray refs = resourceRevisions(client, server);
        response.add("resourceRevisionRefs", refs);
        if (client != null && server != null) response.add("comparison", compare(client, server));
        JsonArray limitations = new JsonArray();
        limitations.add(limitation("statistics", "PARTIAL", "bounded_projection_not_implemented"));
        limitations.add(limitation("advancements", "PARTIAL", "bounded_projection_not_implemented"));
        limitations.add(limitation("recipes", "PARTIAL", "bounded_projection_not_implemented"));
        limitations.add(limitation("cooldowns", "UNAVAILABLE", "REQUIRES_NEW_HOOK"));
        if (server != null && server.has("world") && !server.getAsJsonObject("world").has("dayTime")) {
            limitations.add(limitation("world.dayTime", "PARTIAL", "target_clock_projection_not_formalized"));
        }
        if (client != null) limitations.add(limitation("client_known.deep_fields", "PARTIAL", "client prediction exposes the synchronized common core; server-only inventory details remain separate"));
        response.add("limitations", limitations);
        if (!request.has("includeProviderData") || !request.get("includeProviderData").getAsBoolean()) {
            response.add("providers", new JsonArray());
            return CompletableFuture.completedFuture(checkedResponse(response, request));
        }
        return this.providerExecution.execute(request, refs, requestContext).thenApply(providerResults -> {
            response.add("providers", providerResults);
            return checkedResponse(response, request);
        });
    }

    JsonObject observe(MinecraftServer server, ServerPlayer player, JsonObject request) {
        JsonObject snapshot = captureSnapshot(server, player, normalizedSelector(request));
        snapshot.addProperty("type", "phase9a.deep_observation");
        return snapshot;
    }

    synchronized JsonObject keyframe(MinecraftServer server, ServerPlayer player, JsonObject request) {
        JsonObject selector = normalizedSelector(request);
        JsonObject snapshot = captureSnapshot(server, player, selector);
        rememberSnapshot(snapshot, selector);
        snapshot.addProperty("type", "phase9a.experimental_keyframe");
        snapshot.addProperty("schemaVersion", "phase9a-keyframe-v0");
        snapshot.addProperty("encodedBytes", encodedBytes(snapshot));
        return snapshot;
    }

    synchronized JsonObject selectorFor(String snapshotId) {
        JsonObject selector = this.selectors.get(snapshotId);
        if (selector == null) throw new ProtocolState.ProtocolException(
                "PHASE9A_SNAPSHOT_NOT_FOUND", 404, "Unknown Phase 9A snapshot: " + snapshotId);
        return selector.deepCopy();
    }

    synchronized JsonObject captureDelta(
            MinecraftServer server, ServerPlayer player, String baseSnapshotId) {
        JsonObject selector = selectorFor(baseSnapshotId);
        return delta(baseSnapshotId, captureSnapshot(server, player, selector));
    }

    synchronized JsonObject delta(String baseSnapshotId, JsonObject current) {
        JsonObject before = this.snapshots.get(baseSnapshotId);
        if (before == null) throw new ProtocolState.ProtocolException(
                "PHASE9A_SNAPSHOT_NOT_FOUND", 404, "Unknown Phase 9A snapshot: " + baseSnapshotId);
        JsonObject selector = this.selectors.get(baseSnapshotId).deepCopy();
        rememberSnapshot(current, selector);
        JsonObject delta = base("phase9a.experimental_delta");
        String deltaId = UUID.randomUUID().toString();
        delta.addProperty("deltaId", deltaId);
        delta.addProperty("baseSnapshotId", baseSnapshotId);
        delta.addProperty("snapshotId", current.get("snapshotId").getAsString());
        delta.addProperty("acquisition", "snapshot_diff");
        delta.addProperty("perspective", "server_authoritative");
        delta.addProperty("completeness", "bounded_projected");
        delta.addProperty("clientTick", -1L);
        delta.addProperty("serverTick", current.get("serverTick").getAsLong());
        delta.addProperty("sequence", current.get("snapshotSequence").getAsLong());
        JsonArray operations = createOperations(before, current);
        delta.add("operations", operations);
        delta.addProperty("operationCount", operations.size());
        delta.addProperty("encodedBytes", encodedBytes(delta));
        this.deltas.put(deltaId, delta.deepCopy());
        trim(this.deltas);
        return delta;
    }

    synchronized JsonObject reconstruct(JsonObject request) {
        String keyframeId = requiredString(request, "keyframeId");
        JsonObject baseSnapshot = this.snapshots.get(keyframeId);
        if (baseSnapshot == null) throw new ProtocolState.ProtocolException(
                "PHASE9A_SNAPSHOT_NOT_FOUND", 404, "Unknown Phase 9A keyframe: " + keyframeId);
        JsonObject reconstructed = baseSnapshot.deepCopy();
        JsonObject authoritative = baseSnapshot;
        JsonArray ids = request.getAsJsonArray("deltaIds");
        if (ids == null || ids.isEmpty() || ids.size() > 64) throw new ProtocolState.ProtocolException(
                "INVALID_PHASE9A_RECONSTRUCTION", 400, "deltaIds must contain 1-64 entries");
        for (JsonElement idElement : ids) {
            JsonObject delta = this.deltas.get(idElement.getAsString());
            if (delta == null) throw new ProtocolState.ProtocolException(
                    "PHASE9A_DELTA_NOT_FOUND", 404, "Unknown Phase 9A delta: " + idElement.getAsString());
            applyOperations(reconstructed, delta.getAsJsonArray("operations"));
            authoritative = this.snapshots.get(delta.get("snapshotId").getAsString());
        }
        boolean exact = authoritative != null && reconstructed.equals(authoritative);
        JsonObject json = base("phase9a.reconstruction");
        json.addProperty("keyframeId", keyframeId);
        json.addProperty("deltaCount", ids.size());
        json.addProperty("classification", exact ? "EXACT" : "FAILED");
        json.addProperty("execution", "detached_json_only");
        json.addProperty("authoritativeSnapshotId", authoritative == null ? "" : authoritative.get("snapshotId").getAsString());
        json.addProperty("reconstructedBytes", encodedBytes(reconstructed));
        JsonArray differences = new JsonArray();
        if (!exact) differences.add("reconstructed snapshot differs from authoritative bounded snapshot");
        json.add("differences", differences);
        return json;
    }

    JsonObject debugAttribute(ServerPlayer player, String attributeId, double requested) {
        if (!"minecraft:max_health".equals(attributeId)) throw new ProtocolState.ProtocolException(
                "UNSUPPORTED_DEBUG_ATTRIBUTE", 400, "Phase 9A supports only minecraft:max_health");
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) throw new ProtocolState.ProtocolException(
                "DEBUG_ATTRIBUTE_UNAVAILABLE", 409, "minecraft:max_health is unavailable");
        double before = instance.getBaseValue();
        instance.setBaseValue(requested);
        return mutation("debug.player.attribute.set", player, "attribute:" + attributeId,
                before, instance.getBaseValue(), "server_attribute_map");
    }

    JsonObject debugEntityState(ServerPlayer player, String entityUuid, String state, boolean requested) {
        if (!"no_gravity".equals(state)) throw new ProtocolState.ProtocolException(
                "UNSUPPORTED_DEBUG_ENTITY_STATE", 400, "Phase 9A supports only no_gravity");
        Entity entity;
        try {
            entity = player.level().getEntity(UUID.fromString(entityUuid));
        } catch (IllegalArgumentException exception) {
            throw new ProtocolState.ProtocolException("INVALID_ENTITY_UUID", 400, "Invalid entity UUID");
        }
        if (entity == null) throw new ProtocolState.ProtocolException(
                "PHASE9A_ENTITY_TARGET_UNAVAILABLE", 409, "Entity is not loaded in the current dimension");
        boolean before = entity.isNoGravity();
        entity.setNoGravity(requested);
        JsonObject result = mutation("debug.entity.state.set", player, "entity:" + entityUuid + ":no_gravity",
                before, entity.isNoGravity(), "server_entity_state");
        result.addProperty("entityUuid", entityUuid);
        return result;
    }

    JsonObject debugScenario(ServerPlayer player, JsonObject request) {
        String action = requiredString(request, "action");
        return switch (action) {
            case "inventory_add_stone" -> {
                boolean added = player.getInventory().add(new ItemStack(Items.STONE, 1));
                player.containerMenu.broadcastChanges();
                yield mutation("debug.phase9a.scenario", player, "player:inventory:stone", false, added,
                        "server_inventory_direct");
            }
            case "inventory_remove_stone" -> {
                boolean removed = false;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(Items.STONE) && !stack.isEmpty()) {
                        stack.shrink(1);
                        removed = true;
                        break;
                    }
                }
                player.containerMenu.broadcastChanges();
                yield mutation("debug.phase9a.scenario", player, "player:inventory:stone", true, !removed,
                        "server_inventory_direct");
            }
            case "entity_spawn_pig" -> {
                Entity pig = EntityType.PIG.spawn(
                        player.level(), player.blockPosition().offset(2, 0, 0), EntitySpawnReason.COMMAND);
                if (pig == null) throw new ProtocolState.ProtocolException(
                        "PHASE9A_ENTITY_SPAWN_FAILED", 409, "Unable to spawn bounded test pig");
                JsonObject result = mutation("debug.phase9a.scenario", player,
                        "entity:" + pig.getUUID(), "absent", "spawned", "server_entity_spawn");
                result.addProperty("spawnedEntityUuid", pig.getUUID().toString());
                yield result;
            }
            case "entity_remove" -> {
                String uuid = requiredString(request, "entityUuid");
                Entity entity;
                try {
                    entity = player.level().getEntity(UUID.fromString(uuid));
                } catch (IllegalArgumentException exception) {
                    throw new ProtocolState.ProtocolException("INVALID_ENTITY_UUID", 400, "Invalid entity UUID");
                }
                if (entity == null) throw new ProtocolState.ProtocolException(
                        "PHASE9A_ENTITY_TARGET_UNAVAILABLE", 409, "Entity is not loaded");
                entity.discard();
                yield mutation("debug.phase9a.scenario", player, "entity:" + uuid, "loaded", "removed",
                        "server_entity_remove");
            }
            default -> throw new ProtocolState.ProtocolException(
                    "UNSUPPORTED_PHASE9A_SCENARIO", 400, "Unsupported Phase 9A scenario action");
        };
    }

    StorageRequest storageRequest(MinecraftServer server, ServerPlayer player, JsonObject request) {
        String domain = requiredString(request, "domain");
        if (!List.of("world", "player", "chunk").contains(domain)) throw new ProtocolState.ProtocolException(
                "INVALID_STORAGE_DOMAIN", 400, "domain must be world, player, or chunk");
        int chunkX = request.has("chunkX") ? request.get("chunkX").getAsInt() : player.chunkPosition().x();
        int chunkZ = request.has("chunkZ") ? request.get("chunkZ").getAsInt() : player.chunkPosition().z();
        ServerLevel level = player.level();
        boolean loaded = level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        return new StorageRequest(
                domain,
                server.getWorldPath(LevelResource.ROOT),
                server.getWorldData().getLevelName(),
                level.dimension(),
                player.getUUID().toString(),
                new ChunkPos(chunkX, chunkZ),
                loaded,
                true,
                sha256(server.getWorldPath(LevelResource.ROOT).toAbsolutePath() + "|" + level.dimension().identifier()));
    }

    CompletableFuture<JsonObject> readStorage(StorageRequest request) {
        if (this.closed.get()) return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException("PHASE9A_STORAGE_CLOSED", 409, "Storage spike worker is closed"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (request.domain()) {
                    case "world" -> readWorldMetadata(request);
                    case "player" -> readPlayerData(request);
                    case "chunk" -> readChunkData(request);
                    default -> throw new IllegalStateException(request.domain());
                };
            } catch (ProtocolState.ProtocolException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new ProtocolState.ProtocolException(
                        "PHASE9A_STORAGE_READ_FAILED", 500, "Typed persisted read failed: " + exception.getClass().getSimpleName());
            }
        }, this.storageWorker);
    }

    private JsonObject checkedResponse(JsonObject response, JsonObject request) {
        JsonObject budgets = request.has("budgets") && request.get("budgets").isJsonObject()
                ? request.getAsJsonObject("budgets") : new JsonObject();
        int maximum = bounded(integer(budgets, "maxResponseBytes", 524_288), 16_384, 524_288);
        int bytes = encodedBytes(response);
        response.addProperty("responseBytes", bytes);
        response.addProperty("maxResponseBytes", maximum);
        if (bytes > maximum) throw new ProtocolState.ProtocolException(
                "QUERY_BUDGET_EXCEEDED", 413, "Deep Observation response exceeds maxResponseBytes");
        return response;
    }

    private JsonObject observationMetadata(JsonObject client, JsonObject server) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("perspective", client != null && server != null ? "both"
                : client != null ? "client_known" : "server_authoritative");
        metadata.addProperty("acquisition", "owner_thread_snapshot");
        metadata.addProperty("completeness", "projected");
        metadata.addProperty("readEffects", "none");
        metadata.addProperty("consistency", client != null && server != null ? "coordinated_best_effort" : "owner_thread_snapshot");
        metadata.addProperty("capturedAt", System.currentTimeMillis());
        metadata.addProperty("snapshotId", UUID.randomUUID().toString());
        metadata.addProperty("sessionEpoch", this.revisions.sessionEpoch());
        metadata.addProperty("clientTick", client != null && client.has("clientTick") ? client.get("clientTick").getAsLong() : -1L);
        metadata.addProperty("serverTick", server != null && server.has("serverTick") ? server.get("serverTick").getAsLong() : -1L);
        metadata.addProperty("alignmentQuality", client != null && server != null ? "best_effort_not_same_tick" : "single_perspective");
        return metadata;
    }

    private JsonArray resourceRevisions(JsonObject client, JsonObject server) {
        JsonArray refs = new JsonArray();
        if (client != null) {
            String uuid = client.has("uuid") ? client.get("uuid").getAsString() : "unavailable";
            String key = uuid + "@client_known";
            refs.add(this.revisions.revision(
                    "player", key, "player:" + key + "@runtime_session",
                    canonicalClientPlayer(client)));
        }
        if (server == null) return refs;
        if (server.has("_canonicalResourceRevisionRefs")) {
            server.getAsJsonArray("_canonicalResourceRevisionRefs").forEach(element -> refs.add(element.deepCopy()));
            return refs;
        }
        canonicalResourceRevisions(server).forEach(element -> refs.add(element.deepCopy()));
        return refs;
    }

    private JsonArray canonicalResourceRevisions(JsonObject server) {
        JsonArray refs = new JsonArray();
        String dimension = server.has("dimension") ? server.get("dimension").getAsString() : "unknown";
        if (server.has("player")) {
            JsonObject player = server.getAsJsonObject("player");
            String key = player.get("uuid").getAsString() + "@server_authoritative";
            refs.add(this.revisions.revision(
                    "player", key, player.get("lifecycleId").getAsString(), player));
        }
        if (server.has("menu")) {
            JsonObject menu = server.getAsJsonObject("menu");
            String key = menu.get("ownerUuid").getAsString()
                    + "@menu:" + menu.get("menuId").getAsString();
            refs.add(this.revisions.revision(
                    "menu", key, menu.get("lifecycleId").getAsString(), menu));
        }
        if (server.has("entities")) for (JsonElement element : server.getAsJsonArray("entities")) {
            JsonObject entity = element.getAsJsonObject();
            refs.add(this.revisions.revision(
                    "entity", entity.get("uuid").getAsString(),
                    entity.get("lifecycleId").getAsString(), entity));
        }
        if (server.has("blocks")) for (JsonElement element : server.getAsJsonArray("blocks")) {
            JsonObject block = element.getAsJsonObject();
            String key = dimension + "@" + block.get("key").getAsString();
            refs.add(this.revisions.revision(
                    "block", key, block.get("lifecycleId").getAsString(), block));
        }
        if (server.has("chunks")) for (JsonElement element : server.getAsJsonArray("chunks")) {
            JsonObject chunk = element.getAsJsonObject();
            String key = dimension + "@" + chunk.get("key").getAsString();
            refs.add(this.revisions.revision(
                    "chunk", key, chunk.get("lifecycleId").getAsString(), chunk));
        }
        if (server.has("blockEntities")) for (JsonElement element : server.getAsJsonArray("blockEntities")) {
            JsonObject blockEntity = element.getAsJsonObject();
            String key = dimension + "@" + blockEntity.get("key").getAsString();
            String lifecycleId = blockEntity.get("lifecycleId").getAsString();
            refs.add(this.revisions.revision(
                    "block_entity", key, lifecycleId, canonicalBlockEntityBase(blockEntity)));
            if (blockEntity.has("serializedState")) {
                refs.add(this.revisions.revision(
                        "block_entity_serialized", key, lifecycleId, blockEntity.get("serializedState")));
            }
        }
        return refs;
    }

    private static JsonObject canonicalClientPlayer(JsonObject client) {
        JsonObject semantic = client.deepCopy();
        for (String field : List.of(
                "type", "target", "clientTick", "perspective", "source", "authority",
                "dataSource", "storageAccessed", "stalePossible", "requestId", "protocolVersion")) {
            semantic.remove(field);
        }
        return semantic;
    }

    private static JsonObject canonicalBlockEntityBase(JsonObject blockEntity) {
        JsonObject semantic = new JsonObject();
        for (String field : List.of("key", "type", "loaded")) {
            if (blockEntity.has(field)) semantic.add(field, blockEntity.get(field).deepCopy());
        }
        return semantic;
    }

    private static JsonObject compare(JsonObject client, JsonObject server) {
        JsonObject result = new JsonObject();
        JsonObject player = server.has("player") ? server.getAsJsonObject("player") : new JsonObject();
        result.add("uuid", comparison(client.get("uuid"), player.get("uuid"), false));
        result.add("dimension", comparison(client.get("dimension"), player.get("dimension"), false));
        result.add("health", comparison(client.get("health"), player.get("health"), true));
        result.add("selectedSlot", comparison(client.get("selectedSlot"), player.get("selectedSlot"), true));
        JsonObject position = player.has("position") ? player.getAsJsonObject("position") : new JsonObject();
        result.add("x", comparison(client.get("x"), position.get("x"), true));
        result.add("y", comparison(client.get("y"), position.get("y"), true));
        result.add("z", comparison(client.get("z"), position.get("z"), true));
        result.add("yaw", comparison(client.get("yaw"), position.get("yaw"), true));
        result.add("pitch", comparison(client.get("pitch"), position.get("pitch"), true));
        result.add("velocityX", comparison(client.get("velocityX"), position.get("velocityX"), true));
        result.add("velocityY", comparison(client.get("velocityY"), position.get("velocityY"), true));
        result.add("velocityZ", comparison(client.get("velocityZ"), position.get("velocityZ"), true));
        result.addProperty("timingAlignment", "best_effort_not_same_tick");
        result.addProperty("consistency", "prediction_difference_is_not_automatically_a_bug");
        return result;
    }

    private static JsonObject comparison(JsonElement client, JsonElement server, boolean numeric) {
        JsonObject json = new JsonObject();
        boolean available = client != null && server != null;
        json.addProperty("available", available);
        if (!available) return json;
        json.add("clientValue", client.deepCopy());
        json.add("serverValue", server.deepCopy());
        boolean agreement = client.equals(server);
        json.addProperty("agreement", agreement);
        if (numeric && client.isJsonPrimitive() && server.isJsonPrimitive()
                && client.getAsJsonPrimitive().isNumber() && server.getAsJsonPrimitive().isNumber()) {
            json.addProperty("delta", client.getAsDouble() - server.getAsDouble());
        }
        return json;
    }

    private static JsonObject limitation(String domain, String status, String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("domain", domain);
        json.addProperty("status", status);
        json.addProperty("reason", reason);
        return json;
    }

    private static JsonObject providerDescriptor(AgentDataProviderV2.Descriptor descriptor) {
        JsonObject json = new JsonObject();
        json.addProperty("providerId", descriptor.providerId());
        json.addProperty("schemaVersion", descriptor.schemaVersion());
        json.addProperty("snapshotSchema", descriptor.snapshotSchema());
        json.addProperty("querySchema", descriptor.querySchema());
        json.addProperty("threadAffinity", descriptor.threadAffinity());
        json.addProperty("readEffects", descriptor.readEffects());
        json.addProperty("snapshotSafe", descriptor.snapshotSafe());
        json.addProperty("mayInitialize", descriptor.mayInitialize());
        json.addProperty("mayLoadData", descriptor.mayLoadData());
        json.addProperty("mayAccessStorage", descriptor.mayAccessStorage());
        json.addProperty("mayMutate", descriptor.mayMutate());
        json.addProperty("revisionSource", descriptor.revisionSource());
        json.addProperty("deltaCapability", descriptor.deltaCapability());
        json.addProperty("debugSupported", descriptor.debugDeclaration().supported());
        JsonArray perspectives = new JsonArray();
        descriptor.perspectives().forEach(perspectives::add);
        json.add("perspectives", perspectives);
        JsonArray requiredScopes = new JsonArray();
        descriptor.requiredScopes().forEach(requiredScopes::add);
        json.add("requiredScopes", requiredScopes);
        json.addProperty("status", "registered");
        return json;
    }

    private static JsonObject providerResult(AgentDataProviderV2.Descriptor descriptor, String status, String reason) {
        JsonObject json = providerDescriptor(descriptor);
        json.addProperty("status", status);
        if (!reason.isEmpty()) json.addProperty("reason", reason);
        return json;
    }

    private static JsonArray allDomains() {
        JsonArray domains = new JsonArray();
        for (String domain : List.of("player", "entities", "blocks", "block_entities", "chunks", "world", "menu")) domains.add(domain);
        return domains;
    }


    private static void applyProjection(JsonObject snapshot, JsonObject request) {
        if (!request.has("projection") || !request.get("projection").isJsonObject()) return;
        JsonObject projection = request.getAsJsonObject("projection");
        if (snapshot.has("player") && projection.has("playerFields") && projection.get("playerFields").isJsonArray()) {
            Set<String> fields = new HashSet<>();
            projection.getAsJsonArray("playerFields").forEach(element -> fields.add(element.getAsString()));
            JsonObject player = snapshot.getAsJsonObject("player");
            Set<String> keep = new HashSet<>(List.of("uuid", "name"));
            if (fields.contains("transform")) keep.add("position");
            if (fields.contains("environment")) keep.addAll(List.of("pose", "onGround", "inWater", "fallFlying"));
            if (fields.contains("vitals")) keep.addAll(List.of("health", "maxHealth", "absorption", "food", "air", "experienceLevel", "totalExperience", "experienceProgress"));
            if (fields.contains("authority")) keep.addAll(List.of("gameMode", "mayFly", "flying", "instabuild"));
            if (fields.contains("inventory")) keep.addAll(List.of("selectedSlot", "inventory", "carriedStack", "equipment"));
            if (fields.contains("attributes")) keep.add("attributes");
            if (fields.contains("effects")) keep.add("effects");
            if (fields.contains("relationships")) keep.addAll(List.of("vehicle", "passengers"));
            if (fields.contains("menu")) keep.addAll(List.of("menuId", "menuRole"));
            if (fields.contains("dimension")) keep.add("dimension");
            if (fields.contains("respawn")) keep.add("respawn");
            new ArrayList<>(player.keySet()).stream().filter(key -> !keep.contains(key)).forEach(player::remove);
        }
        if (snapshot.has("entities") && projection.has("entityFields") && projection.get("entityFields").isJsonArray()) {
            Set<String> fields = new HashSet<>();
            projection.getAsJsonArray("entityFields").forEach(element -> fields.add(element.getAsString()));
            for (JsonElement element : snapshot.getAsJsonArray("entities")) {
                JsonObject entity = element.getAsJsonObject();
                Set<String> keep = new HashSet<>(List.of("uuid", "runtimeId", "type"));
                if (fields.contains("transform")) keep.addAll(List.of("x", "y", "z", "velocityX", "velocityY", "velocityZ", "yaw", "pitch", "pose"));
                if (fields.contains("living")) keep.addAll(List.of("health", "maxHealth"));
                if (fields.contains("equipment")) keep.add("equipment");
                if (fields.contains("effects")) keep.add("effectCount");
                if (fields.contains("attributes")) keep.add("syncableAttributeCount");
                if (fields.contains("relationships")) keep.addAll(List.of("vehicle", "passengers"));
                if (fields.contains("common_state")) keep.add("noGravity");
                new ArrayList<>(entity.keySet()).stream().filter(key -> !keep.contains(key)).forEach(entity::remove);
            }
        }
        snapshot.addProperty("projectionApplied", true);
    }

    private JsonObject captureSnapshot(MinecraftServer server, ServerPlayer player, JsonObject selector) {
        long captureStarted = System.nanoTime();
        ServerLevel level = player.level();
        JsonObject json = base("phase9a.snapshot");
        json.addProperty("snapshotId", UUID.randomUUID().toString());
        json.addProperty("snapshotSequence", server.getTickCount());
        json.addProperty("capturedAtMillis", System.currentTimeMillis());
        json.addProperty("clientTick", -1L);
        json.addProperty("serverTick", server.getTickCount());
        json.addProperty("consistency", "server_thread_bounded");
        json.addProperty("perspective", "server_authoritative");
        json.addProperty("acquisition", "public_api_plus_internal_projection");
        json.addProperty("completeness", "bounded_projected");
        boolean serializedBlockEntities = selector.get("includeSerializedState").getAsBoolean();
        json.addProperty("readEffects", serializedBlockEntities ? "serialization_hooks_invoked" : "none");
        json.addProperty("chunkLoadRequested", false);
        json.addProperty("dataSource", "LIVE");
        json.addProperty("storageAccessed", false);
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.add("selector", selector.deepCopy());
        Set<String> domains = new HashSet<>();
        selector.getAsJsonArray("domains").forEach(element -> domains.add(element.getAsString()));
        if (domains.contains("player")) json.add("player", player(player));
        if (domains.contains("menu")) json.add("menu", menu(player));
        if (domains.contains("entities")) {
            JsonArray entities = entities(level, player, selector.get("entityRadius").getAsDouble(), selector.get("entityLimit").getAsInt());
            json.add("entities", entities);
            JsonObject counts = new JsonObject();
            counts.addProperty("returnedCount", entities.size());
            counts.addProperty("availableCountKnown", false);
            counts.addProperty("truncated", entities.size() >= selector.get("entityLimit").getAsInt());
            counts.addProperty("limit", selector.get("entityLimit").getAsInt());
            json.add("entitiesResult", counts);
        }
        JsonArray chunks = domains.contains("chunks") || domains.contains("block_entities")
                ? chunks(level, player, selector.get("radiusChunks").getAsInt()) : new JsonArray();
        if (domains.contains("chunks")) json.add("chunks", chunks);
        if (domains.contains("block_entities")) json.add("blockEntities", blockEntities(level, chunks, selector));
        if (domains.contains("blocks")) json.add("blocks", blocks(level, player, selector));
        if (domains.contains("world")) json.add("world", world(server, level));
        JsonObject ticketFacts = new JsonObject();
        ticketFacts.addProperty("status", "REQUIRES_NEW_HOOK");
        ticketFacts.addProperty("model", "TicketStorage + normalized loading reason candidate");
        ticketFacts.addProperty("readEffects", "not_observed");
        json.add("tickets", ticketFacts);
        JsonObject provider = new JsonObject();
        provider.addProperty("providerId", "minecraft_protocol_probe:echo");
        provider.addProperty("status", "existing_live_read_spi");
        provider.addProperty("nativeDelta", false);
        json.add("providerTrack", provider);
        long captureMicros = (System.nanoTime() - captureStarted) / 1_000L;
        json.addProperty("ownerThreadCaptureMicros", captureMicros);
        json.addProperty("ownerThreadSoftBudgetMicros", 4_000L);
        json.addProperty("ownerThreadHardBudgetMicros", 12_000L);
        if (captureMicros > 12_000L) {
            json.addProperty("completeness", "partial");
            JsonArray limitations = new JsonArray();
            limitations.add("owner_thread_hard_budget_exceeded");
            json.add("budgetLimitations", limitations);
        }
        return json;
    }

    private JsonObject player(ServerPlayer player) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", player.getUUID().toString());
        json.addProperty("lifecycleId", this.lifecycles.lifecycleId(
                "player", player.getUUID().toString(), player));
        json.addProperty("name", player.getName().getString());
        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        position.addProperty("previousX", player.xo);
        position.addProperty("previousY", player.yo);
        position.addProperty("previousZ", player.zo);
        position.addProperty("velocityX", player.getDeltaMovement().x);
        position.addProperty("velocityY", player.getDeltaMovement().y);
        position.addProperty("velocityZ", player.getDeltaMovement().z);
        position.addProperty("yaw", player.getYRot());
        position.addProperty("pitch", player.getXRot());
        json.add("position", position);
        json.addProperty("pose", player.getPose().toString());
        json.addProperty("onGround", player.onGround());
        json.addProperty("inWater", player.isInWater());
        json.addProperty("fallFlying", player.isFallFlying());
        json.addProperty("health", player.getHealth());
        json.addProperty("maxHealth", player.getMaxHealth());
        json.addProperty("absorption", player.getAbsorptionAmount());
        json.addProperty("food", player.getFoodData().getFoodLevel());
        json.addProperty("air", player.getAirSupply());
        json.addProperty("experienceLevel", player.experienceLevel);
        json.addProperty("totalExperience", player.totalExperience);
        json.addProperty("experienceProgress", player.experienceProgress);
        json.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
        json.addProperty("mayFly", player.getAbilities().mayfly);
        json.addProperty("flying", player.getAbilities().flying);
        json.addProperty("instabuild", player.getAbilities().instabuild);
        json.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            JsonObject item = item(player.getInventory().getItem(slot));
            item.addProperty("slot", slot);
            inventory.add(item);
        }
        json.add("inventory", inventory);
        json.add("carriedStack", item(player.containerMenu.getCarried()));
        JsonObject equipment = new JsonObject();
        for (EquipmentSlot slot : EquipmentSlot.values()) equipment.add(slot.getName(), item(player.getItemBySlot(slot)));
        json.add("equipment", equipment);
        JsonArray attributes = new JsonArray();
        List<JsonObject> normalizedAttributes = new ArrayList<>();
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            JsonObject attribute = new JsonObject();
            attribute.addProperty("id", instance.getAttribute().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("unregistered"));
            attribute.addProperty("base", instance.getBaseValue());
            attribute.addProperty("value", instance.getValue());
            normalizedAttributes.add(attribute);
        }
        normalizedAttributes.sort(Comparator.comparing(
                value -> value.get("id").getAsString()));
        normalizedAttributes.forEach(attributes::add);
        json.add("attributes", attributes);
        JsonArray effects = new JsonArray();
        List<JsonObject> normalizedEffects = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            JsonObject effect = new JsonObject();
            effect.addProperty("id", instance.getEffect().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("unregistered"));
            effect.addProperty("duration", instance.getDuration());
            effect.addProperty("amplifier", instance.getAmplifier());
            normalizedEffects.add(effect);
        }
        normalizedEffects.sort(Comparator.comparing(
                value -> value.get("id").getAsString()));
        normalizedEffects.forEach(effects::add);
        json.add("effects", effects);
        json.addProperty("vehicle", player.getVehicle() == null ? "" : player.getVehicle().getUUID().toString());
        JsonArray passengers = new JsonArray();
        for (Entity passenger : player.getPassengers()) passengers.add(passenger.getUUID().toString());
        json.add("passengers", passengers);
        json.addProperty("menuClass", player.containerMenu.getClass().getName());
        json.addProperty("menuId", player.containerMenu.containerId);
        json.addProperty("menuRole", player.containerMenu.containerId == 0 ? "player_inventory" : "container");
        json.addProperty("dimension", player.level().dimension().identifier().toString());
        json.addProperty("respawn", player.getRespawnConfig() == null ? "unset" : player.getRespawnConfig().toString());
        JsonObject extended = new JsonObject();
        extended.addProperty("statistics", "PARTIAL_counter_available_no_bounded_projection_yet");
        extended.addProperty("advancements", "PARTIAL_manager_available_no_bounded_projection_yet");
        extended.addProperty("recipes", "PARTIAL_recipe_book_available_no_bounded_projection_yet");
        extended.addProperty("cooldowns", "REQUIRES_NEW_HOOK_for_enumeration");
        json.add("extendedStatus", extended);
        return json;
    }


    private JsonObject menu(ServerPlayer player) {
        JsonObject json = new JsonObject();
        json.addProperty("menuId", player.containerMenu.containerId);
        json.addProperty("ownerUuid", player.getUUID().toString());
        json.addProperty("lifecycleId", this.lifecycles.lifecycleId(
                "menu",
                player.getUUID() + ":" + player.containerMenu.containerId,
                player.containerMenu));
        json.addProperty("role", player.containerMenu.containerId == 0 ? "player_inventory" : "container");
        json.addProperty("serverAuthoritative", true);
        json.addProperty("playerInventoryRelationship", true);
        JsonArray slots = new JsonArray();
        for (int index = 0; index < player.containerMenu.slots.size(); index++) {
            JsonObject slot = item(player.containerMenu.slots.get(index).getItem());
            slot.addProperty("slot", index);
            slots.add(slot);
        }
        json.add("slots", slots);
        json.add("carriedStack", item(player.containerMenu.getCarried()));
        return json;
    }

    private JsonArray entities(ServerLevel level, ServerPlayer player, double radius, int limit) {
        JsonArray json = new JsonArray();
        List<Entity> entities = level.getEntities(player, player.getBoundingBox().inflate(radius), ignored -> true);
        entities.stream().sorted(Comparator.comparing(entity -> entity.getUUID().toString())).limit(limit)
                .forEach(entity -> json.add(entity(entity)));
        return json;
    }

    private JsonObject entity(Entity entity) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", entity.getUUID().toString());
        json.addProperty("lifecycleId", this.lifecycles.lifecycleId(
                "entity", entity.getUUID().toString(), entity));
        json.addProperty("runtimeId", entity.getId());
        json.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        json.addProperty("x", entity.getX());
        json.addProperty("y", entity.getY());
        json.addProperty("z", entity.getZ());
        json.addProperty("velocityX", entity.getDeltaMovement().x);
        json.addProperty("velocityY", entity.getDeltaMovement().y);
        json.addProperty("velocityZ", entity.getDeltaMovement().z);
        json.addProperty("yaw", entity.getYRot());
        json.addProperty("pitch", entity.getXRot());
        json.addProperty("pose", entity.getPose().toString());
        json.addProperty("noGravity", entity.isNoGravity());
        json.addProperty("vehicle", entity.getVehicle() == null ? "" : entity.getVehicle().getUUID().toString());
        JsonArray passengers = new JsonArray();
        for (Entity passenger : entity.getPassengers()) passengers.add(passenger.getUUID().toString());
        json.add("passengers", passengers);
        var tracked = entity.getEntityData().getNonDefaultValues();
        json.addProperty("nonDefaultTrackedValues", tracked == null ? 0 : tracked.size());
        if (entity instanceof LivingEntity living) {
            json.addProperty("health", living.getHealth());
            json.addProperty("maxHealth", living.getMaxHealth());
            JsonObject equipment = new JsonObject();
            for (EquipmentSlot slot : EquipmentSlot.values()) equipment.add(slot.getName(), item(living.getItemBySlot(slot)));
            json.add("equipment", equipment);
            json.addProperty("effectCount", living.getActiveEffects().size());
            json.addProperty("syncableAttributeCount", living.getAttributes().getSyncableAttributes().size());
        }
        JsonObject extensions = new JsonObject();
        extensions.addProperty("dataComponents", "TARGET_SPECIFIC_no_stable_entity_component_projection");
        extensions.addProperty("attachments", "TARGET_SPECIFIC_read_skipped_to_avoid_lazy_initialization");
        json.add("extensions", extensions);
        return json;
    }

    private JsonArray chunks(ServerLevel level, ServerPlayer player, int radius) {
        JsonArray json = new JsonArray();
        int centerX = player.chunkPosition().x();
        int centerZ = player.chunkPosition().z();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                JsonObject value = new JsonObject();
                value.addProperty("key", x + "," + z);
                value.addProperty("lifecycleId", this.lifecycles.lifecycleId(
                        "chunk", x + "," + z, chunk));
                value.addProperty("chunkX", x);
                value.addProperty("chunkZ", z);
                value.addProperty("loaded", chunk != null);
                value.addProperty("loadRequested", false);
                if (chunk != null) {
                    int nonEmpty = 0;
                    for (LevelChunkSection section : chunk.getSections()) if (!section.hasOnlyAir()) nonEmpty++;
                    value.addProperty("sectionCount", chunk.getSections().length);
                    value.addProperty("nonEmptySections", nonEmpty);
                    value.addProperty("blockEntityCount", chunk.getBlockEntities().size());
                    value.addProperty("status", chunk.getFullStatus().toString());
                    value.add("loadingSummary", loadingSummary(level, x, z, chunk));
                    value.add("scheduledBlockTicks", scheduledTicks(level.getBlockTicks(), x, z, true));
                    value.add("scheduledFluidTicks", scheduledTicks(level.getFluidTicks(), x, z, false));
                } else {
                    value.addProperty("reason", "NOT_LOADED");
                }
                json.add(value);
            }
        }
        return json;
    }

    private JsonObject loadingSummary(ServerLevel level, int chunkX, int chunkZ, LevelChunk chunk) {
        JsonObject json = new JsonObject();
        long key = ChunkPos.pack(chunkX, chunkZ);
        TicketStorage storage = ((DistanceManagerAccessor)(Object)
                level.getChunkSource().chunkMap.getDistanceManager()).minecraftProtocol$getTicketStorage();
        this.ticketHookVerified.set(true);
        List<Ticket> tickets = storage.getTickets(key);
        JsonArray reasons = new JsonArray();
        JsonArray details = new JsonArray();
        List<JsonObject> normalizedDetails = new ArrayList<>();
        Set<String> normalized = new HashSet<>();
        for (Ticket ticket : tickets) {
            String rawType = String.valueOf(ticket.getType());
            normalized.add(normalizedTicketReason(rawType));
            JsonObject detail = new JsonObject();
            detail.addProperty("type", rawType);
            detail.addProperty("level", ticket.getTicketLevel());
            normalizedDetails.add(detail);
        }
        normalizedDetails.sort(Comparator
                .comparing((JsonObject value) -> value.get("type").getAsString())
                .thenComparingInt(value -> value.get("level").getAsInt()));
        normalizedDetails.forEach(details::add);
        normalized.stream().sorted().forEach(reasons::add);
        json.addProperty("loaded", chunk != null);
        json.addProperty("fullStatus", chunk == null ? "UNLOADED" : chunk.getFullStatus().toString());
        json.addProperty("loadingLevel", storage.getTicketLevelAt(key, false));
        json.addProperty("simulationLevel", storage.getTicketLevelAt(key, true));
        json.addProperty("simulationActive", storage.getTicketLevelAt(key, true) <= 33);
        json.addProperty("sourceCount", tickets.size());
        json.addProperty("holderState", chunk == null ? "absent" : "level_chunk_present");
        json.addProperty("ticketDetailAvailable", true);
        json.add("reasons", reasons);
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("schema", "26.x-ticket-storage-v0");
        diagnostic.addProperty("stability", "target_specific_diagnostic_only");
        diagnostic.add("tickets", details);
        json.add("targetDiagnostic", diagnostic);
        return json;
    }

    private JsonArray scheduledTicks(Object ticks, int chunkX, int chunkZ, boolean block) {
        JsonArray json = new JsonArray();
        Long2ObjectMap<LevelChunkTicks<?>> containers = ((LevelTicksAccessor)(Object)ticks).minecraftProtocol$getAllContainers();
        this.scheduledTickHookVerified.set(true);
        LevelChunkTicks<?> container = containers.get(ChunkPos.pack(chunkX, chunkZ));
        if (container == null) return json;
        container.getAll().limit(64).forEach(raw -> {
            ScheduledTick<?> tick = (ScheduledTick<?>)raw;
            JsonObject value = new JsonObject();
            value.addProperty("x", tick.pos().getX());
            value.addProperty("y", tick.pos().getY());
            value.addProperty("z", tick.pos().getZ());
            value.addProperty("type", block && tick.type() instanceof Block blockType
                    ? BuiltInRegistries.BLOCK.getKey(blockType).toString()
                    : !block && tick.type() instanceof Fluid fluidType
                    ? BuiltInRegistries.FLUID.getKey(fluidType).toString() : String.valueOf(tick.type()));
            value.addProperty("triggerTick", tick.triggerTick());
            value.addProperty("priority", tick.priority().toString());
            value.addProperty("subTickOrder", tick.subTickOrder());
            value.addProperty("chunkX", chunkX);
            value.addProperty("chunkZ", chunkZ);
            json.add(value);
        });
        return json;
    }

    private static String normalizedTicketReason(String raw) {
        String value = raw.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("player")) return "player";
        if (value.contains("forced")) return "forced";
        if (value.contains("portal")) return "portal";
        if (value.contains("light")) return "lighting";
        if (value.contains("start")) return "spawn_or_start";
        return "other";
    }

    private JsonArray blockEntities(ServerLevel level, JsonArray chunks, JsonObject selector) {
        JsonArray json = new JsonArray();
        int count = 0;
        int serializedTotal = 0;
        int limit = selector.get("blockEntityLimit").getAsInt();
        boolean includeSerialized = selector.get("includeSerializedState").getAsBoolean();
        int perEntityBudget = selector.get("maxSerializedBytesPerBlockEntity").getAsInt();
        int totalBudget = selector.get("maxTotalSerializedBlockEntityBytes").getAsInt();
        for (JsonElement element : chunks) {
            JsonObject chunkValue = element.getAsJsonObject();
            if (!chunkValue.get("loaded").getAsBoolean()) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    chunkValue.get("chunkX").getAsInt(), chunkValue.get("chunkZ").getAsInt());
            if (chunk == null) continue;
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                if (count++ >= limit) return json;
                BlockEntity blockEntity = entry.getValue();
                JsonObject value = new JsonObject();
                value.addProperty("key", key(entry.getKey()));
                value.addProperty("lifecycleId", this.lifecycles.lifecycleId(
                        "block_entity", key(entry.getKey()), blockEntity));
                value.addProperty("type", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
                value.addProperty("loaded", true);
                value.addProperty("readEffects", includeSerialized ? "serialization_hooks_invoked" : "none");
                if (includeSerialized) {
                    CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                    JsonElement structured = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
                    int bytes = GSON.toJson(structured).getBytes(StandardCharsets.UTF_8).length;
                    value.addProperty("serializedBytes", bytes);
                    if (bytes > perEntityBudget || serializedTotal + bytes > totalBudget) {
                        value.addProperty("serializedStateStatus", "truncated");
                        value.addProperty("limitation", "serialized_state_byte_budget_exceeded");
                    } else {
                        serializedTotal += bytes;
                        value.addProperty("serializedStateStatus", "complete");
                        value.add("serializedState", structured);
                    }
                }
                json.add(value);
            }
        }
        return json;
    }

    private JsonArray blocks(ServerLevel level, ServerPlayer player, JsonObject selector) {
        JsonArray positions = selector.getAsJsonArray("selectedBlocks");
        JsonArray json = new JsonArray();
        if (positions.isEmpty()) {
            JsonObject position = new JsonObject();
            position.addProperty("x", player.blockPosition().getX());
            position.addProperty("y", player.blockPosition().getY() - 1);
            position.addProperty("z", player.blockPosition().getZ());
            positions.add(position);
        }
        for (JsonElement element : positions) {
            JsonObject position = element.getAsJsonObject();
            BlockPos pos = new BlockPos(position.get("x").getAsInt(), position.get("y").getAsInt(), position.get("z").getAsInt());
            JsonObject value = new JsonObject();
            value.addProperty("key", key(pos));
            value.addProperty("lifecycleId",
                    "block:" + level.dimension().toString() + "@" + key(pos));
            value.addProperty("x", pos.getX());
            value.addProperty("y", pos.getY());
            value.addProperty("z", pos.getZ());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            value.addProperty("available", chunk != null);
            value.addProperty("loadRequested", false);
            if (chunk != null) {
                BlockState state = chunk.getBlockState(pos);
                value.addProperty("blockId", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                JsonObject properties = new JsonObject();
                state.getValues().forEach(propertyValue -> properties.addProperty(
                        propertyValue.property().getName(), String.valueOf(propertyValue.value())));
                value.add("properties", properties);
            } else {
                value.addProperty("reason", "NOT_LOADED");
            }
            json.add(value);
        }
        return json;
    }

    private JsonObject world(MinecraftServer server, ServerLevel level) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.addProperty("gameTime", level.getGameTime());
        json.addProperty("raining", level.isRaining());
        json.addProperty("thundering", level.isThundering());
        json.addProperty("blockScheduledTickCount", level.getBlockTicks().count());
        json.addProperty("fluidScheduledTickCount", level.getFluidTicks().count());
        json.addProperty("scheduledTickDetail", "REQUIRES_NEW_HOOK");
        json.addProperty("ticketDetail", "REQUIRES_NEW_HOOK");
        json.addProperty("serverTick", server.getTickCount());
        return json;
    }

    private JsonObject item(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("empty", stack.isEmpty());
        json.addProperty("id", stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        json.addProperty("componentsHash", stack.isEmpty() ? "" : sha256(stack.getComponents().toString()));
        return json;
    }

    private synchronized void rememberSnapshot(JsonObject snapshot, JsonObject selector) {
        String id = snapshot.get("snapshotId").getAsString();
        this.snapshots.put(id, snapshot.deepCopy());
        this.selectors.put(id, selector.deepCopy());
        trim(this.snapshots);
        trim(this.selectors);
    }

    private JsonArray createOperations(JsonObject before, JsonObject after) {
        JsonArray operations = new JsonArray();
        replaceObject(operations, "player", "player.state_change", before, after);
        replaceObject(operations, "world", "world.metadata_change", before, after);
        diffArray(operations, "entities", "uuid", "entity.spawn", "entity.remove", "entity.state_change", before, after);
        diffArray(operations, "blocks", "key", "block.add", "block.remove", "block.change", before, after);
        diffArray(operations, "blockEntities", "key", "block_entity.add", "block_entity.remove", "block_entity.change", before, after);
        diffArray(operations, "chunks", "key", "chunk.load", "chunk.unload", "chunk.state_change", before, after);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("type", "snapshot.metadata");
        metadata.addProperty("snapshotId", after.get("snapshotId").getAsString());
        metadata.addProperty("snapshotSequence", after.get("snapshotSequence").getAsLong());
        metadata.addProperty("capturedAtMillis", after.get("capturedAtMillis").getAsLong());
        metadata.addProperty("serverTick", after.get("serverTick").getAsLong());
        operations.add(metadata);
        return operations;
    }

    private static void replaceObject(JsonArray operations, String field, String type, JsonObject before, JsonObject after) {
        if (before.get(field).equals(after.get(field))) return;
        JsonObject operation = new JsonObject();
        operation.addProperty("type", type);
        operation.addProperty("domain", field);
        operation.add("after", after.get(field).deepCopy());
        operations.add(operation);
    }

    private static void diffArray(
            JsonArray operations, String field, String keyField,
            String addType, String removeType, String changeType,
            JsonObject before, JsonObject after) {
        Map<String, JsonObject> oldValues = keyed(before.getAsJsonArray(field), keyField);
        Map<String, JsonObject> newValues = keyed(after.getAsJsonArray(field), keyField);
        for (String key : oldValues.keySet()) if (!newValues.containsKey(key)) {
            JsonObject operation = new JsonObject();
            operation.addProperty("type", removeType);
            operation.addProperty("domain", field);
            operation.addProperty("key", key);
            operations.add(operation);
        }
        for (Map.Entry<String, JsonObject> entry : newValues.entrySet()) {
            JsonObject old = oldValues.get(entry.getKey());
            if (old != null && old.equals(entry.getValue())) continue;
            JsonObject operation = new JsonObject();
            operation.addProperty("type", old == null ? addType : changeType);
            operation.addProperty("domain", field);
            operation.addProperty("key", entry.getKey());
            operation.add("after", entry.getValue().deepCopy());
            operations.add(operation);
        }
    }

    private static void applyOperations(JsonObject snapshot, JsonArray operations) {
        for (JsonElement element : operations) {
            JsonObject operation = element.getAsJsonObject();
            String type = operation.get("type").getAsString();
            if (type.equals("snapshot.metadata")) {
                snapshot.addProperty("snapshotId", operation.get("snapshotId").getAsString());
                snapshot.addProperty("snapshotSequence", operation.get("snapshotSequence").getAsLong());
                snapshot.addProperty("capturedAtMillis", operation.get("capturedAtMillis").getAsLong());
                snapshot.addProperty("serverTick", operation.get("serverTick").getAsLong());
            } else if (!operation.has("key")) {
                snapshot.add(operation.get("domain").getAsString(), operation.get("after").deepCopy());
            } else {
                String field = operation.get("domain").getAsString();
                JsonArray values = snapshot.getAsJsonArray(field);
                String keyField = field.equals("entities") ? "uuid" : "key";
                String key = operation.get("key").getAsString();
                for (int index = values.size() - 1; index >= 0; index--) {
                    if (values.get(index).getAsJsonObject().get(keyField).getAsString().equals(key)) values.remove(index);
                }
                if (operation.has("after")) values.add(operation.get("after").deepCopy());
                List<JsonElement> sorted = new ArrayList<>();
                values.forEach(sorted::add);
                sorted.sort(Comparator.comparing(value -> value.getAsJsonObject().get(keyField).getAsString()));
                values = new JsonArray();
                sorted.forEach(values::add);
                snapshot.add(field, values);
            }
        }
    }

    private static Map<String, JsonObject> keyed(JsonArray array, String keyField) {
        Map<String, JsonObject> values = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject value = element.getAsJsonObject();
            values.put(value.get(keyField).getAsString(), value);
        }
        return values;
    }

    private JsonObject readWorldMetadata(StorageRequest request) throws Exception {
        Path file = request.root().resolve("level.dat");
        return persistedResult(request, file, readCompressed(file), "minecraft:NbtIo.readCompressed(level.dat)");
    }

    private JsonObject readPlayerData(StorageRequest request) throws Exception {
        Path file = request.root().resolve("players").resolve("data").resolve(request.playerUuid() + ".dat");
        return persistedResult(request, file, readCompressed(file), "minecraft:NbtIo.readCompressed(playerdata)");
    }

    private JsonObject readChunkData(StorageRequest request) throws Exception {
        Path dimensionRoot = dimensionRoot(request.root(), request.dimension());
        Path regionDir = dimensionRoot.resolve("region");
        Path file = regionDir.resolve("r." + request.chunkPos().getRegionX() + "." + request.chunkPos().getRegionZ() + ".mca");
        CompoundTag tag = null;
        if (Files.isRegularFile(file)) {
            RegionStorageInfo info = new RegionStorageInfo(request.levelName(), request.dimension(), "chunk");
            try (RegionFile region = new RegionFile(info, file, regionDir, false);
                 DataInputStream input = region.getChunkDataInputStream(request.chunkPos())) {
                if (input != null) tag = NbtIo.read(input);
            }
        }
        JsonObject result = persistedResult(request, file, tag, "minecraft:RegionFile+NbtIo");
        result.addProperty("sideEffects", "region_file_api_uses_write_capable_handle; no_write_requested");
        return result;
    }

    private JsonObject persistedResult(StorageRequest request, Path file, CompoundTag tag, String api) throws Exception {
        JsonObject json = base("phase9a.storage.read");
        json.addProperty("domain", request.domain());
        json.addProperty("source", "persistent_storage");
        json.addProperty("dataSource", "PERSISTED");
        json.addProperty("perspective", "persistent_storage");
        json.addProperty("acquisition", "minecraft_storage_api");
        json.addProperty("storageApi", api);
        json.addProperty("worldFingerprint", request.worldFingerprint());
        json.addProperty("dimension", request.dimension().identifier().toString());
        json.addProperty("playerUuid", request.playerUuid());
        json.addProperty("chunkX", request.chunkPos().x());
        json.addProperty("chunkZ", request.chunkPos().z());
        json.addProperty("liveWorldExists", request.liveWorldExists());
        json.addProperty("targetLoaded", request.targetLoaded());
        json.addProperty("consistency", "last_saved_state");
        json.addProperty("stalePossibility", request.liveWorldExists());
        json.addProperty("storageAccessOccurred", true);
        json.addProperty("sideEffects", "none_read_only");
        json.addProperty("writeImplemented", false);
        json.addProperty("fileExists", Files.isRegularFile(file));
        json.addProperty("saveMarker", Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0L);
        json.addProperty("serializedBytes", Files.isRegularFile(file) ? Files.size(file) : 0L);
        json.addProperty("available", tag != null);
        if (tag != null) {
            JsonArray keys = new JsonArray();
            tag.keySet().stream().sorted().forEach(keys::add);
            json.add("rootKeys", keys);
            json.addProperty("decodedCharacters", tag.toString().length());
            json.addProperty("decodedSha256", sha256(tag.toString()));
        }
        return json;
    }

    private static CompoundTag readCompressed(Path path) throws Exception {
        return Files.isRegularFile(path) ? NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()) : null;
    }

    private static Path dimensionRoot(Path root, ResourceKey<Level> dimension) {
        return root.resolve("dimensions")
                .resolve(dimension.identifier().getNamespace())
                .resolve(dimension.identifier().getPath());
    }

    private JsonObject mutation(String type, ServerPlayer player, String resource, Object before, Object after, String mechanism) {
        JsonObject json = base(type);
        json.addProperty("mode", "DEBUG_PRIVILEGED");
        json.addProperty("authority", "runtime_internal");
        json.addProperty("mechanism", mechanism);
        json.addProperty("invariants", "partial_invariants");
        json.addProperty("synchronization", "server_thread_applied");
        json.addProperty("evidence", "diagnostic");
        json.addProperty("gameplayEvidence", false);
        json.addProperty("evidenceContaminated", true);
        json.addProperty("storageAccessed", false);
        json.addProperty("affectedResource", resource);
        json.addProperty("before", String.valueOf(before));
        json.addProperty("after", String.valueOf(after));
        json.addProperty("entityUuid", player.getUUID().toString());
        return json;
    }

    private JsonObject normalizedSelector(JsonObject request) {
        JsonObject selector = new JsonObject();
        selector.addProperty("radiusChunks", bounded(request.has("radiusChunks") ? request.get("radiusChunks").getAsInt() : 1, 0, 2));
        selector.addProperty("entityRadius", bounded(request.has("entityRadius") ? request.get("entityRadius").getAsInt() : 32, 0, 64));
        selector.addProperty("entityLimit", bounded(request.has("entityLimit") ? request.get("entityLimit").getAsInt() : MAX_ENTITIES, 1, MAX_ENTITIES));
        selector.addProperty("blockEntityLimit", bounded(request.has("blockEntityLimit") ? request.get("blockEntityLimit").getAsInt() : MAX_BLOCK_ENTITIES, 1, MAX_BLOCK_ENTITIES));
        selector.addProperty("includeSerializedState", request.has("includeSerializedState") && request.get("includeSerializedState").getAsBoolean());
        selector.addProperty("maxSerializedBytesPerBlockEntity", bounded(
                request.has("maxSerializedBytesPerBlockEntity") ? request.get("maxSerializedBytesPerBlockEntity").getAsInt() : 16_384, 256, 16_384));
        selector.addProperty("maxTotalSerializedBlockEntityBytes", bounded(
                request.has("maxTotalSerializedBlockEntityBytes") ? request.get("maxTotalSerializedBlockEntityBytes").getAsInt() : 65_536, 1_024, 65_536));
        JsonArray blocks = request.has("selectedBlocks") && request.get("selectedBlocks").isJsonArray()
                ? request.getAsJsonArray("selectedBlocks").deepCopy() : new JsonArray();
        if (blocks.size() > MAX_SELECTED_BLOCKS) throw new ProtocolState.ProtocolException(
                "PHASE9A_SELECTOR_TOO_LARGE", 413, "selectedBlocks supports at most " + MAX_SELECTED_BLOCKS);
        selector.add("selectedBlocks", blocks);
        selector.add("domains", request.has("domains") && request.get("domains").isJsonArray()
                ? request.getAsJsonArray("domains").deepCopy() : allDomains());
        return selector;
    }

    private JsonObject base(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("target", this.target);
        json.addProperty("phase", "9A");
        json.addProperty("experimental", true);
        json.addProperty("wireProtocolFrozen", false);
        return json;
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).getAsString().isBlank()) throw new ProtocolState.ProtocolException(
                "INVALID_PHASE9A_REQUEST", 400, "Missing " + name);
        return object.get(name).getAsString();
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int integer(JsonObject object, String name, int fallback) {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static int encodedBytes(JsonObject json) {
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8).length;
    }

    private static String key(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <T> void trim(Map<String, T> values) {
        while (values.size() > MAX_SNAPSHOTS) values.remove(values.keySet().iterator().next());
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.providerExecution.close();
        this.revisionWorker.close();
        this.storageWorker.shutdown();
        try {
            if (!this.storageWorker.awaitTermination(2L, TimeUnit.SECONDS)) this.storageWorker.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.storageWorker.shutdownNow();
        }
    }

    record StorageRequest(
            String domain,
            Path root,
            String levelName,
            ResourceKey<Level> dimension,
            String playerUuid,
            ChunkPos chunkPos,
            boolean targetLoaded,
            boolean liveWorldExists,
            String worldFingerprint) {
    }
}
