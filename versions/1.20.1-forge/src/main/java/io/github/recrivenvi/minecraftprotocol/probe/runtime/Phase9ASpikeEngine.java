package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.minecraftprotocol.core.BoundedTaskExecutor;
import io.github.recrivenvi.minecraftprotocol.core.DebugBlockStateRequest;
import io.github.recrivenvi.minecraftprotocol.core.DebugEntityLifecycleRequest;
import io.github.recrivenvi.minecraftprotocol.core.DebugEnvironmentRequest;
import io.github.recrivenvi.minecraftprotocol.core.DebugItemRequest;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.DebugRelationRequest;
import io.github.recrivenvi.minecraftprotocol.core.DebugTransferRequest;
import io.github.recrivenvi.minecraftprotocol.core.DeepBlockEntityPages;
import io.github.recrivenvi.minecraftprotocol.core.DeepObservationRequestContext;
import io.github.recrivenvi.minecraftprotocol.core.EnvironmentControlRevisions;
import io.github.recrivenvi.minecraftprotocol.core.LoadedChunkMutationScope;
import io.github.recrivenvi.minecraftprotocol.core.ObservationLifecycleTracker;
import io.github.recrivenvi.minecraftprotocol.core.ObservationRevisionTracker;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ProviderExecutionEngine;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.ScheduledTickRead;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.BaseContainerBlockEntityAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.DistanceManagerAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.LevelTicksAccessor;
import io.github.recrivenvi.minecraftprotocol.safety.PersistentWriteSafetyFoundation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;

/** Experimental Phase 9A evidence collector. It is not a frozen public protocol implementation. */
final class Phase9ASpikeEngine implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int MAX_SNAPSHOTS = 32;
    private static final int MAX_ENTITIES = DebugTransferRequest.MAX_ENTITY_TARGETS;
    private static final int MAX_BLOCK_ENTITIES = 128;
    private static final int MAX_SELECTED_BLOCKS = 64;

    private final String target;
    private final ObservationRevisionTracker revisions = new ObservationRevisionTracker();
    private final ObservationLifecycleTracker lifecycles = new ObservationLifecycleTracker();
    private final ProviderExecutionEngine providerExecution =
            new ProviderExecutionEngine(this.revisions);
    private final PersistentStorageAdapter storageAdapter;
    private final PersistentWriteSafetyFoundation.LifecycleBarrier writeLifecycle =
            new PersistentWriteSafetyFoundation.LifecycleBarrier(this.revisions.sessionEpoch());
    private final BoundedTaskExecutor revisionWorker;
    private final Map<String, JsonObject> snapshots = new LinkedHashMap<>();
    private final Map<String, JsonObject> selectors = new LinkedHashMap<>();
    private final Map<String, JsonObject> deltas = new LinkedHashMap<>();
    private final DeepBlockEntityPages blockEntityPages = new DeepBlockEntityPages();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean ticketHookVerified = new AtomicBoolean();
    private final AtomicBoolean scheduledTickHookVerified = new AtomicBoolean();

    Phase9ASpikeEngine(String target) {
        this.target = target;
        this.storageAdapter = new PersistentStorageAdapter(target);
        this.revisionWorker =
                new BoundedTaskExecutor("minecraft-protocol-observation-revisions", 1, 8);
    }

    void installProviderDispatcher(ProviderExecutionEngine.Dispatcher dispatcher) {
        this.providerExecution.installDispatcher(dispatcher);
    }

    void installProviderMutationDispatcher(ProviderExecutionEngine.MutationDispatcher dispatcher) {
        this.providerExecution.installMutationDispatcher(dispatcher);
    }

    String sessionEpoch() {
        return this.revisions.sessionEpoch();
    }

    JsonObject uiRevision(String key, JsonObject state) {
        return this.revisions.revision("ui_node", key, state);
    }

    void observeWorldLifecycle(Object world) {
        this.storageAdapter.observeWorldLifecycle(world);
        if (world != null) this.writeLifecycle.markRunning();
        else this.writeLifecycle.markUnloading();
    }

    void observeStorageLifecycle(Object world, boolean saving, boolean fullyStopped) {
        this.storageAdapter.observeStorageLifecycle(world, saving, fullyStopped);
        if (world != null) {
            if (saving) this.writeLifecycle.markSaving();
            else this.writeLifecycle.markRunning();
        } else if (fullyStopped) {
            if (this.writeLifecycle.state()
                    != PersistentWriteSafetyFoundation.LifecycleState.STOPPED_OFFLINE) {
                this.writeLifecycle.markShuttingDown();
                this.writeLifecycle.markOffline();
            }
        } else {
            this.writeLifecycle.markUnloading();
        }
    }

    void observeStorageShutdown() {
        this.writeLifecycle.markShuttingDown();
    }

    JsonObject formalDebugCapabilities() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.capabilities");
        json.addProperty("schemaVersion", "phase9c-debug-v0");
        json.addProperty("formal", true);
        json.addProperty("sessionEpoch", this.revisions.sessionEpoch());
        json.addProperty("requiresDebugArm", true);
        json.addProperty("requiresWorldFingerprint", true);
        json.addProperty("requiresResourceVersion", true);
        json.addProperty("persistentStorageMutation", false);
        json.addProperty("arbitraryReflection", false);
        json.addProperty("arbitraryPacketInjection", false);
        JsonArray operations = new JsonArray();
        List<String> declared = new ArrayList<>();
        for (JsonElement descriptor :
                io.github.recrivenvi.minecraftprotocol.core.WorldDebugInspection.contract()
                        .getAsJsonArray("operations"))
            declared.add(descriptor.getAsJsonObject().get("operation").getAsString());
        for (String operation : declared) {
            JsonObject value = new JsonObject();
            value.addProperty("operation", operation);
            value.addProperty("status", "available");
            value.addProperty("requiresArm", true);
            value.addProperty(
                    "requiredScope", "debug." + operation.substring(0, operation.indexOf('.')));
            value.addProperty("resourceVersionPrecondition", true);
            value.addProperty("batch", true);
            operations.add(value);
        }
        for (String domain : List.of("chunk", "client", "network")) {
            JsonObject value = new JsonObject();
            value.addProperty("operation", domain + ".representative");
            value.addProperty("status", "partial");
            value.addProperty(
                    "limitation",
                    domain.equals("network")
                            ? "typed resync observation only; no arbitrary packet injection"
                            : "no stable cross-Target public mutation promoted in Phase 9C");
            operations.add(value);
        }
        json.add("operations", operations);
        JsonObject deployments = new JsonObject();
        deployments.addProperty("integratedServer", "available");
        deployments.addProperty("dedicatedServerPeer", "legacy_typed_health_and_block_only");
        deployments.addProperty("remoteWithoutPeer", "unavailable");
        json.add("deployments", deployments);
        return json;
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
        capabilities.addProperty("persistentReadContract", "PHASE9D0_BOUNDED_FORMAL_STORAGE_READ");
        capabilities.addProperty("persistentReadScope", "storage.read");
        capabilities.addProperty("persistentWrite", "UNAVAILABLE");
        capabilities.addProperty("experimentalKeyframe", "PASS");
        capabilities.addProperty("deltaCapture", "PASS");
        capabilities.addProperty("reconstruction", "PASS");
        json.add("capabilities", capabilities);
        JsonObject targetFacts = new JsonObject();
        targetFacts.addProperty("ticketModel", "DistanceManager sorted Ticket sets");
        targetFacts.addProperty("componentModel", "legacy_NBT_plus_Forge_capabilities");
        targetFacts.addProperty("storageModel", "RegionFile + NbtIo");
        targetFacts.addProperty(
                "inventoryModel", "public compartment lists plus Container projection");
        targetFacts.addProperty(
                "scheduledTickModel", "LevelTicks count public; per-tick details require hook");
        json.add("targetFacts", targetFacts);
        JsonObject storageSafety = new JsonObject();
        storageSafety.addProperty("phase", "9D-2");
        storageSafety.addProperty("lifecycleState", this.writeLifecycle.state().name());
        storageSafety.addProperty(
                "offlineOwnership", "runtime_lifecycle_attested_plus_session_lock");
        storageSafety.addProperty("persistentWriteRoute", "unavailable");
        json.add("persistentWriteSafety", storageSafety);
        return json;
    }

    JsonObject formalCapabilities() {
        JsonObject json = base("deep_observation.capabilities");
        json.addProperty("schemaVersion", "phase9b-observation-v0");
        json.addProperty("formal", true);
        json.addProperty(
                "phase9aDiagnosticsStatus", "experimental_superseded_for_stable_agent_usage");
        JsonObject budgets = new JsonObject();
        budgets.addProperty("maxChunkRadius", 2);
        budgets.addProperty("maxEntities", MAX_ENTITIES);
        budgets.addProperty("maxBlocks", MAX_SELECTED_BLOCKS);
        budgets.addProperty("maxBlockEntities", MAX_BLOCK_ENTITIES);
        budgets.addProperty("maxBlockEntityCandidates", DeepBlockEntityPages.MAX_CANDIDATES);
        json.addProperty("blockEntityPagination", "selector.blockEntityCursor");
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
        for (String domain :
                List.of(
                        "player",
                        "entities",
                        "blocks",
                        "block_entities",
                        "chunks",
                        "world",
                        "menu",
                        "providers")) {
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
        hooks.addProperty(
                "ticketHook",
                this.ticketHookVerified.get()
                        ? "runtime_verified"
                        : "unverified_until_deep_observation");
        hooks.addProperty(
                "scheduledTickHook",
                this.scheduledTickHookVerified.get()
                        ? "runtime_verified"
                        : "unverified_until_deep_observation");
        hooks.addProperty("mechanism", "read_only_mixin_accessor");
        hooks.addProperty("changesControlFlow", false);
        hooks.addProperty("fallback", "capability_partial_without_target_diagnostic");
        json.add("hooks", hooks);
        return json;
    }

    static int formalChunkRadius(JsonObject request) {
        try {
            int radius =
                    io.github.recrivenvi.minecraftprotocol.safety.DeepObservationSelector
                            .chunkRadius(request);
            String pageCursor =
                    io.github.recrivenvi.minecraftprotocol.safety.DeepObservationSelector
                            .blockEntityCursor(request);
            if (request.has("selector")
                    && request.getAsJsonObject("selector").has("entityTargets")) {
                DebugTransferRequest.targets(
                        request.getAsJsonObject("selector").get("entityTargets"));
                boolean entities = !request.has("domains");
                if (request.has("domains"))
                    for (JsonElement domain : request.getAsJsonArray("domains"))
                        entities |= domain.getAsString().equals("entities");
                if (!entities
                        || !request.has("perspective")
                        || !request.get("perspective").getAsString().equals("server_authoritative"))
                    throw new ProtocolState.ProtocolException(
                            "UNSUPPORTED_DEEP_SELECTOR",
                            400,
                            "entityTargets requires entities and explicit server_authoritative perspective");
            }
            if (request.has("selector") && request.getAsJsonObject("selector").has("entityChunk")) {
                JsonElement chunk = request.getAsJsonObject("selector").get("entityChunk");
                if (!chunk.isJsonObject())
                    throw new IllegalArgumentException("selector.entityChunk requires x and z");
                DebugEntityLifecycleRequest.chunk(chunk.getAsJsonObject());
                boolean entities = !request.has("domains");
                if (request.has("domains"))
                    for (JsonElement domain : request.getAsJsonArray("domains"))
                        entities |= domain.getAsString().equals("entities");
                if (!entities
                        || (request.has("perspective")
                                && request.get("perspective").getAsString().equals("client_known")))
                    throw new ProtocolState.ProtocolException(
                            "UNSUPPORTED_DEEP_SELECTOR",
                            400,
                            "entityChunk requires entities and server_authoritative or both");
            }
            if (pageCursor != null
                    && (!requestsBlockEntities(request)
                            || (request.has("perspective")
                                    && request.get("perspective")
                                            .getAsString()
                                            .equals("client_known"))))
                throw new ProtocolState.ProtocolException(
                        "UNSUPPORTED_DEEP_SELECTOR",
                        400,
                        "Block entity continuation requires block_entities and server_authoritative or both");
            if (requestsBlockEntities(request)
                    && request.has("selector")
                    && request.getAsJsonObject("selector").has("blocks")) {
                JsonElement blocks = request.getAsJsonObject("selector").get("blocks");
                if (!blocks.isJsonArray())
                    throw new IllegalArgumentException("selector.blocks must be an array");
                List<BlockPos> positions = selectedBlockPositions(blocks.getAsJsonArray());
                if (!positions.isEmpty()
                        && request.has("perspective")
                        && request.get("perspective").getAsString().equals("client_known"))
                    throw new ProtocolState.ProtocolException(
                            "UNSUPPORTED_DEEP_SELECTOR",
                            400,
                            "Client-known deep observation does not expose block entities; coordinate block-entity reads require server_authoritative or both");
            }
            return radius;
        } catch (IllegalArgumentException invalid) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEEP_SELECTOR", 400, invalid.getMessage());
        }
    }

    private static boolean requestsBlockEntities(JsonObject request) {
        if (!request.has("domains") || !request.get("domains").isJsonArray()) return true;
        for (JsonElement domain : request.getAsJsonArray("domains"))
            if (domain.isJsonPrimitive() && domain.getAsString().equals("block_entities"))
                return true;
        return false;
    }

    static List<BlockPos> selectedBlockPositions(JsonArray positions) {
        if (positions.size() > MAX_SELECTED_BLOCKS)
            throw new ProtocolState.ProtocolException(
                    "PHASE9A_SELECTOR_TOO_LARGE",
                    413,
                    "selectedBlocks supports at most " + MAX_SELECTED_BLOCKS);
        Set<BlockPos> selected = new LinkedHashSet<>();
        try {
            for (JsonElement element : positions) {
                if (!element.isJsonObject()) throw new IllegalArgumentException();
                JsonObject position = element.getAsJsonObject();
                int[] coordinates = new int[3];
                String[] axes = {"x", "y", "z"};
                for (int index = 0; index < axes.length; index++) {
                    JsonElement coordinate = position.get(axes[index]);
                    if (coordinate == null
                            || !coordinate.isJsonPrimitive()
                            || !coordinate.getAsJsonPrimitive().isNumber())
                        throw new IllegalArgumentException();
                    coordinates[index] = coordinate.getAsBigDecimal().intValueExact();
                }
                selected.add(new BlockPos(coordinates[0], coordinates[1], coordinates[2]));
            }
        } catch (RuntimeException invalid) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEEP_SELECTOR",
                    400,
                    "Each selector.blocks position must contain exact integer x, y and z coordinates");
        }
        return List.copyOf(selected);
    }

    JsonObject captureFormal(MinecraftServer server, ServerPlayer player, JsonObject request) {
        int chunkRadius = formalChunkRadius(request);
        JsonObject selector = new JsonObject();
        JsonObject requestedSelector =
                request.has("selector") && request.get("selector").isJsonObject()
                        ? request.getAsJsonObject("selector")
                        : new JsonObject();
        JsonObject budgets =
                request.has("budgets") && request.get("budgets").isJsonObject()
                        ? request.getAsJsonObject("budgets")
                        : new JsonObject();
        selector.addProperty("radiusChunks", chunkRadius);
        selector.addProperty(
                "entityRadius", bounded(integer(requestedSelector, "entityRadius", 16), 0, 64));
        selector.addProperty(
                "entityLimit", bounded(integer(budgets, "maxEntities", 64), 1, MAX_ENTITIES));
        selector.addProperty(
                "blockEntityLimit",
                bounded(integer(budgets, "maxBlockEntities", 64), 1, MAX_BLOCK_ENTITIES));
        selector.addProperty(
                "maxSerializedBytesPerBlockEntity",
                bounded(integer(budgets, "maxSerializedBytesPerBlockEntity", 16_384), 256, 16_384));
        selector.addProperty(
                "maxTotalSerializedBlockEntityBytes",
                bounded(
                        integer(budgets, "maxTotalSerializedBlockEntityBytes", 65_536),
                        1_024,
                        65_536));
        selector.addProperty(
                "includeSerializedState",
                request.has("includeSerializedBlockEntities")
                        && request.get("includeSerializedBlockEntities").getAsBoolean());
        selector.add(
                "selectedBlocks",
                requestedSelector.has("blocks") && requestedSelector.get("blocks").isJsonArray()
                        ? requestedSelector.getAsJsonArray("blocks").deepCopy()
                        : new JsonArray());
        selector.add(
                "domains",
                request.has("domains") && request.get("domains").isJsonArray()
                        ? request.getAsJsonArray("domains").deepCopy()
                        : allDomains());
        if (requestedSelector.has("blockEntityCursor"))
            selector.add(
                    "blockEntityCursor", requestedSelector.get("blockEntityCursor").deepCopy());
        if (requestedSelector.has("entityChunk"))
            selector.add("entityChunk", requestedSelector.get("entityChunk").deepCopy());
        if (requestedSelector.has("entityTargets"))
            selector.add("entityTargets", requestedSelector.get("entityTargets").deepCopy());
        JsonObject snapshot = captureSnapshot(server, player, normalizedSelector(selector));
        if (snapshot.has("player")) {
            JsonObject playerSnapshot = snapshot.getAsJsonObject("player");
            playerSnapshot.remove("extendedStatus");
            playerSnapshot.remove("menuClass");
        }
        if (snapshot.has("entities"))
            for (JsonElement element : snapshot.getAsJsonArray("entities")) {
                JsonObject entity = element.getAsJsonObject();
                entity.remove("extensions");
                entity.remove("nonDefaultTrackedValues");
            }
        snapshot.remove("providerTrack");
        snapshot.remove("tickets");
        return snapshot;
    }

    CompletableFuture<JsonObject> prepareFormalServerSnapshot(
            JsonObject request, JsonObject canonicalSnapshot) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(
                    new CancellationException("observation_runtime_closed"));
        }
        CompletableFuture<JsonObject> submitted =
                this.revisionWorker.submit(
                        () -> {
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
        submitted.whenComplete(
                (value, error) -> {
                    if (error instanceof java.util.concurrent.RejectedExecutionException) {
                        result.completeExceptionally(
                                new ProtocolState.ProtocolException(
                                        "REVISION_BACKPRESSURE",
                                        429,
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
        String perspective =
                request.has("perspective")
                        ? request.get("perspective").getAsString()
                        : "server_authoritative";
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
        metadata.addProperty(
                "revisionComputationMicros",
                server != null && server.has("_revisionComputationMicros")
                        ? server.get("_revisionComputationMicros").getAsLong()
                        : 0L);
        metadata.addProperty("revisionThread", "detached_revision_worker");
        metadata.addProperty("revisionQueueDepth", this.revisionWorker.queueDepth());
        response.add("metadata", metadata);
        JsonArray refs = resourceRevisions(client, server);
        response.add("resourceRevisionRefs", refs);
        if (client != null && server != null) response.add("comparison", compare(client, server));
        JsonArray limitations = new JsonArray();
        limitations.add(limitation("statistics", "PARTIAL", "bounded_projection_not_implemented"));
        limitations.add(
                limitation("advancements", "PARTIAL", "bounded_projection_not_implemented"));
        limitations.add(limitation("recipes", "PARTIAL", "bounded_projection_not_implemented"));
        limitations.add(limitation("cooldowns", "UNAVAILABLE", "REQUIRES_NEW_HOOK"));
        if (server != null
                && server.has("world")
                && !server.getAsJsonObject("world").has("dayTime")) {
            limitations.add(
                    limitation(
                            "world.dayTime", "PARTIAL", "target_clock_projection_not_formalized"));
        }
        if (client != null)
            limitations.add(
                    limitation(
                            "client_known.deep_fields",
                            "PARTIAL",
                            "client prediction exposes the synchronized common core; server-only inventory details remain separate"));
        response.add("limitations", limitations);
        if (!request.has("includeProviderData")
                || !request.get("includeProviderData").getAsBoolean()) {
            response.add("providers", new JsonArray());
            return CompletableFuture.completedFuture(checkedResponse(response, request));
        }
        return this.providerExecution
                .execute(request, refs, requestContext)
                .thenApply(
                        providerResults -> {
                            response.add("providers", providerResults);
                            return checkedResponse(response, request);
                        });
    }

    JsonObject observe(MinecraftServer server, ServerPlayer player, JsonObject request) {
        JsonObject snapshot = captureSnapshot(server, player, normalizedSelector(request));
        snapshot.addProperty("type", "phase9a.deep_observation");
        return snapshot;
    }

    synchronized JsonObject keyframe(
            MinecraftServer server, ServerPlayer player, JsonObject request) {
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
        if (selector == null)
            throw new ProtocolState.ProtocolException(
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
        if (before == null)
            throw new ProtocolState.ProtocolException(
                    "PHASE9A_SNAPSHOT_NOT_FOUND",
                    404,
                    "Unknown Phase 9A snapshot: " + baseSnapshotId);
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
        if (baseSnapshot == null)
            throw new ProtocolState.ProtocolException(
                    "PHASE9A_SNAPSHOT_NOT_FOUND", 404, "Unknown Phase 9A keyframe: " + keyframeId);
        JsonObject reconstructed = baseSnapshot.deepCopy();
        JsonObject authoritative = baseSnapshot;
        JsonArray ids = request.getAsJsonArray("deltaIds");
        if (ids == null || ids.isEmpty() || ids.size() > 64)
            throw new ProtocolState.ProtocolException(
                    "INVALID_PHASE9A_RECONSTRUCTION", 400, "deltaIds must contain 1-64 entries");
        for (JsonElement idElement : ids) {
            JsonObject delta = this.deltas.get(idElement.getAsString());
            if (delta == null)
                throw new ProtocolState.ProtocolException(
                        "PHASE9A_DELTA_NOT_FOUND",
                        404,
                        "Unknown Phase 9A delta: " + idElement.getAsString());
            applyOperations(reconstructed, delta.getAsJsonArray("operations"));
            authoritative = this.snapshots.get(delta.get("snapshotId").getAsString());
        }
        boolean exact = authoritative != null && reconstructed.equals(authoritative);
        JsonObject json = base("phase9a.reconstruction");
        json.addProperty("keyframeId", keyframeId);
        json.addProperty("deltaCount", ids.size());
        json.addProperty("classification", exact ? "EXACT" : "FAILED");
        json.addProperty("execution", "detached_json_only");
        json.addProperty(
                "authoritativeSnapshotId",
                authoritative == null ? "" : authoritative.get("snapshotId").getAsString());
        json.addProperty("reconstructedBytes", encodedBytes(reconstructed));
        JsonArray differences = new JsonArray();
        if (!exact)
            differences.add("reconstructed snapshot differs from authoritative bounded snapshot");
        json.add("differences", differences);
        return json;
    }

    JsonObject debugAttribute(ServerPlayer player, String attributeId, double requested) {
        if (!"minecraft:max_health".equals(attributeId))
            throw new ProtocolState.ProtocolException(
                    "UNSUPPORTED_DEBUG_ATTRIBUTE",
                    400,
                    "Phase 9A supports only minecraft:max_health");
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_ATTRIBUTE_UNAVAILABLE", 409, "minecraft:max_health is unavailable");
        double before = instance.getBaseValue();
        instance.setBaseValue(requested);
        return mutation(
                "debug.player.attribute.set",
                player,
                "attribute:" + attributeId,
                before,
                instance.getBaseValue(),
                "server_attribute_map");
    }

    JsonObject debugEntityState(
            ServerPlayer player, String entityUuid, String state, boolean requested) {
        if (!"no_gravity".equals(state))
            throw new ProtocolState.ProtocolException(
                    "UNSUPPORTED_DEBUG_ENTITY_STATE", 400, "Phase 9A supports only no_gravity");
        Entity entity;
        try {
            entity = player.serverLevel().getEntity(UUID.fromString(entityUuid));
        } catch (IllegalArgumentException exception) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_ENTITY_UUID", 400, "Invalid entity UUID");
        }
        if (entity == null)
            throw new ProtocolState.ProtocolException(
                    "PHASE9A_ENTITY_TARGET_UNAVAILABLE",
                    409,
                    "Entity is not loaded in the current dimension");
        boolean before = entity.isNoGravity();
        entity.setNoGravity(requested);
        JsonObject result =
                mutation(
                        "debug.entity.state.set",
                        player,
                        "entity:" + entityUuid + ":no_gravity",
                        before,
                        entity.isNoGravity(),
                        "server_entity_state");
        result.addProperty("entityUuid", entityUuid);
        return result;
    }

    JsonObject debugScenario(ServerPlayer player, JsonObject request) {
        String action = requiredString(request, "action");
        return switch (action) {
            case "inventory_add_stone" -> {
                boolean added = player.getInventory().add(new ItemStack(Items.STONE, 1));
                player.containerMenu.broadcastChanges();
                yield mutation(
                        "debug.phase9a.scenario",
                        player,
                        "player:inventory:stone",
                        false,
                        added,
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
                yield mutation(
                        "debug.phase9a.scenario",
                        player,
                        "player:inventory:stone",
                        true,
                        !removed,
                        "server_inventory_direct");
            }
            case "entity_spawn_pig" -> {
                Entity pig =
                        EntityType.PIG.spawn(
                                player.serverLevel(),
                                player.blockPosition().offset(2, 0, 0),
                                MobSpawnType.COMMAND);
                if (pig == null)
                    throw new ProtocolState.ProtocolException(
                            "PHASE9A_ENTITY_SPAWN_FAILED", 409, "Unable to spawn bounded test pig");
                JsonObject result =
                        mutation(
                                "debug.phase9a.scenario",
                                player,
                                "entity:" + pig.getUUID(),
                                "absent",
                                "spawned",
                                "server_entity_spawn");
                result.addProperty("spawnedEntityUuid", pig.getUUID().toString());
                yield result;
            }
            case "entity_remove" -> {
                String uuid = requiredString(request, "entityUuid");
                Entity entity;
                try {
                    entity = player.serverLevel().getEntity(UUID.fromString(uuid));
                } catch (IllegalArgumentException exception) {
                    throw new ProtocolState.ProtocolException(
                            "INVALID_ENTITY_UUID", 400, "Invalid entity UUID");
                }
                if (entity == null)
                    throw new ProtocolState.ProtocolException(
                            "PHASE9A_ENTITY_TARGET_UNAVAILABLE", 409, "Entity is not loaded");
                entity.discard();
                yield mutation(
                        "debug.phase9a.scenario",
                        player,
                        "entity:" + uuid,
                        "loaded",
                        "removed",
                        "server_entity_remove");
            }
            default ->
                    throw new ProtocolState.ProtocolException(
                            "UNSUPPORTED_PHASE9A_SCENARIO",
                            400,
                            "Unsupported Phase 9A scenario action");
        };
    }

    JsonObject debugMutation(
            MinecraftServer server,
            ServerPlayer player,
            JsonObject request,
            String currentWorldFingerprint) {
        String operation = requiredDebugString(request, "operation");
        String expectedFingerprint = requiredDebugString(request, "worldFingerprint");
        if (!expectedFingerprint.equals(currentWorldFingerprint)) {
            throw new ProtocolState.ProtocolException(
                    "WORLD_FINGERPRINT_MISMATCH", 409, "Debug mutation belongs to another world");
        }
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject()) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_RESOURCE_VERSION", 400, "expectedResourceVersion is required");
        }
        return switch (operation) {
            case "world.time.set" -> this.debugWorldTime(server, request);
            case "world.weather.set" -> this.debugWorldWeather(server, request);
            case "world.rule.set" -> this.debugWorldRule(server, player.serverLevel(), request);
            case "entity.spawn" -> this.debugEntitySpawn(player, request);
            case "entity.remove" -> this.debugEntityRemove(server, player, request);
            case "player.health.set" -> this.debugPlayerHealth(player, request);
            case "player.state.set" -> this.debugPlayerState(player, request);
            case "entity.state.set" -> this.debugEntityState(player, request);
            case "entity.attribute.set" -> this.debugEntityAttribute(player, request);
            case "entity.equipment.set" -> this.debugEntityEquipment(player, request);
            case "player.effect.set" -> this.debugEffect(player, request, true);
            case "player.transform.set" -> this.debugTransfer(server, player, request, true);
            case "entity.transform.set" -> this.debugTransfer(server, player, request, false);
            case "player.vehicle.set" -> this.debugRelation(player, request, "vehicle", true);
            case "entity.vehicle.set" -> this.debugRelation(player, request, "vehicle", false);
            case "entity.leash.set" -> this.debugRelation(player, request, "leash", false);
            case "entity.owner.set" -> this.debugRelation(player, request, "owner", false);
            case "entity.effect.set" -> this.debugEffect(player, request, false);
            case "player.attribute.set" -> this.debugPlayerAttribute(player, request);
            case "player.inventory.slot.set" -> this.debugPlayerInventorySlot(player, request);
            case "player.equipment.set" -> this.debugPlayerEquipment(player, request);
            case "entity.no_gravity.set" -> this.debugEntityNoGravity(player, request);
            case "world.block.set" -> this.debugWorldBlock(player, request);
            case "block_entity.custom_name.set" -> this.debugBlockEntityCustomName(player, request);
            case "block_entity.slot.set" -> this.debugBlockEntitySlot(player, request);
            case "menu.slot.set" -> this.debugMenuSlot(player, request);
            default ->
                    throw new ProtocolState.ProtocolException(
                            "CAPABILITY_UNAVAILABLE",
                            409,
                            "Unsupported typed Phase 9C Debug operation: " + operation);
        };
    }

    CompletableFuture<JsonObject> debugProviderMutation(
            JsonObject request, DebugMutationAuthorization authorization) {
        String providerId = requiredDebugString(request, "providerId");
        if (!request.has("mutation") || !request.get("mutation").isJsonObject()) {
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "PROVIDER_DEBUG_SCHEMA_VIOLATION",
                            400,
                            "provider.mutate requires a typed mutation object"));
        }
        if (!request.has("expectedResourceVersion")
                || !request.get("expectedResourceVersion").isJsonObject()) {
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "INVALID_RESOURCE_VERSION",
                            400,
                            "provider.mutate requires expectedResourceVersion"));
        }
        return this.providerExecution.debugMutate(
                providerId,
                request.getAsJsonObject("mutation"),
                request.getAsJsonObject("expectedResourceVersion"),
                requiredDebugString(request, "worldFingerprint"),
                request.has("debugOperationId")
                        ? request.get("debugOperationId").getAsString()
                        : UUID.randomUUID().toString(),
                authorization,
                request.has("timeoutMs") ? request.get("timeoutMs").getAsInt() : 250,
                request.has("resultByteBudget")
                        ? request.get("resultByteBudget").getAsInt()
                        : 16_384);
    }

    private JsonObject debugPlayerHealth(ServerPlayer player, JsonObject request) {
        JsonObject before = player(player);
        JsonObject beforeRef = this.playerRevision(before);
        verifyExpected(request, beforeRef);
        double requested = requiredFiniteDouble(request, "health", 0.0D, 2_048.0D);
        if (request.has("expectedHealth")
                && Double.compare(request.get("expectedHealth").getAsDouble(), player.getHealth())
                        != 0) {
            throw valueMismatch("expectedHealth", player.getHealth());
        }
        if (Double.compare(requested, player.getHealth()) == 0) throw noStateChange();
        player.setHealth((float) requested);
        JsonObject after = player(player);
        return mutationResult(
                request,
                "player",
                "server_player_set_health",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.playerRevision(after));
    }

    private JsonObject debugPlayerState(ServerPlayer player, JsonObject request) {
        JsonObject before = player(player), beforeRef = this.playerRevision(before);
        verifyExpected(request, beforeRef);
        JsonObject value =
                io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.parse(request, true);
        JsonObject current = debugStateProjection(before, true);
        io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.verifyValues(
                request, current);
        if (value.has("air") && value.get("air").getAsInt() > player.getMaxAirSupply())
            throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                    "air exceeds native maxAirSupply");
        if (value.has("flying")
                && value.get("flying").getAsBoolean()
                && !player.getAbilities().mayfly)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_PLAYER_ABILITY_DENIED",
                    409,
                    "Native player abilities do not permit flying; no ability or permission is granted");
        if (value.has("experiencePoints")
                && value.get("experiencePoints").getAsInt() >= player.getXpNeededForNextLevel())
            throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                    "experiencePoints must be below the current native level requirement");
        if (io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.unchanged(value, current))
            throw noStateChange();
        if (value.has("food")) player.getFoodData().setFoodLevel(value.get("food").getAsInt());
        if (value.has("saturation"))
            player.getFoodData().setSaturation(value.get("saturation").getAsFloat());
        if (value.has("air")) player.setAirSupply(value.get("air").getAsInt());
        if (value.has("absorption"))
            player.setAbsorptionAmount(value.get("absorption").getAsFloat());
        if (value.has("experienceLevel"))
            player.setExperienceLevels(value.get("experienceLevel").getAsInt());
        if (value.has("experiencePoints"))
            player.setExperiencePoints(value.get("experiencePoints").getAsInt());
        if (value.has("flying")) {
            player.getAbilities().flying = value.get("flying").getAsBoolean();
            player.onUpdateAbilities();
        }
        if (value.has("velocity")) {
            applyDebugVelocity(player, value.getAsJsonObject("velocity"));
            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(
                            player));
        }
        JsonObject after = player(player);
        return mutationResult(
                request,
                "player",
                "native_player_state_setters",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.playerRevision(after));
    }

    private JsonObject debugEntityState(ServerPlayer player, JsonObject request) {
        Entity target = loadedEntity(player, requiredDebugString(request, "entityUuid"));
        if (target instanceof ServerPlayer)
            throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                    "Player state requires its own player namespace and operation");
        JsonObject before = entity(target), beforeRef = entityRevision(before);
        verifyExpected(request, beforeRef);
        JsonObject value =
                io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.parse(request, false);
        JsonObject current = debugStateProjection(before, false);
        for (String field : value.keySet())
            if (!current.has(field))
                throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                        "State field is not applicable to this native entity: " + field);
        io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.verifyValues(
                request, current);
        if (request.has("expectedEntityType")
                && !request.get("expectedEntityType").equals(before.get("type")))
            throw valueMismatch("expectedEntityType", before.get("type").getAsString());
        if (value.has("air") && value.get("air").getAsInt() > target.getMaxAirSupply())
            throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                    "air exceeds native maxAirSupply");
        if (io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.unchanged(value, current))
            throw noStateChange();
        if (value.has("health"))
            ((LivingEntity) target).setHealth(value.get("health").getAsFloat());
        if (value.has("absorption"))
            ((LivingEntity) target).setAbsorptionAmount(value.get("absorption").getAsFloat());
        if (value.has("air")) target.setAirSupply(value.get("air").getAsInt());
        if (value.has("silent")) target.setSilent(value.get("silent").getAsBoolean());
        if (value.has("noGravity")) target.setNoGravity(value.get("noGravity").getAsBoolean());
        if (value.has("glowing")) target.setGlowingTag(value.get("glowing").getAsBoolean());
        if (value.has("noAi"))
            ((net.minecraft.world.entity.Mob) target).setNoAi(value.get("noAi").getAsBoolean());
        if (value.has("velocity")) applyDebugVelocity(target, value.getAsJsonObject("velocity"));
        JsonObject after = entity(target);
        return mutationResult(
                request,
                "entity",
                "native_entity_state_setters",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                entityRevision(after));
    }

    private JsonObject relationship(Entity target) {
        JsonObject value = new JsonObject();
        String uuid = target.getUUID().toString();
        value.addProperty("uuid", uuid);
        value.addProperty(
                "type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        value.addProperty(
                "lifecycleId", this.lifecycles.lifecycleId("entity_relationship", uuid, target));
        value.addProperty("removed", target.isRemoved());
        value.add(
                "vehicle",
                target.getVehicle() == null
                        ? com.google.gson.JsonNull.INSTANCE
                        : new com.google.gson.JsonPrimitive(
                                target.getVehicle().getUUID().toString()));
        JsonArray passengers = new JsonArray();
        target.getPassengers().forEach(entity -> passengers.add(entity.getUUID().toString()));
        value.add("passengers", passengers);
        JsonObject leash = new JsonObject();
        leash.addProperty("supported", target instanceof Mob);
        if (target instanceof Mob mob) {
            var access = (io.github.recrivenvi.minecraftprotocol.probe.mixin.MobLeashAccessor) mob;
            Entity holder = access.minecraftProtocol$getLeashHolder();
            var saved = access.minecraftProtocol$getLeashInfoTag();
            leash.addProperty(
                    "resolution", holder != null ? "resolved" : saved != null ? "pending" : "none");
            leash.add(
                    "holderUuid",
                    holder == null
                            ? com.google.gson.JsonNull.INSTANCE
                            : new com.google.gson.JsonPrimitive(holder.getUUID().toString()));
            if (saved != null) leash.addProperty("pendingReference", saved.toString());
        } else leash.addProperty("readOnlyReason", "native_entity_is_not_leashable");
        value.add("leash", leash);
        JsonObject owner = new JsonObject();
        owner.addProperty(
                "supported",
                target instanceof net.minecraft.world.entity.TamableAnimal
                        || target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse);
        if (target instanceof net.minecraft.world.entity.TamableAnimal animal) {
            var reference = animal.getOwnerUUID();
            owner.add(
                    "ownerUuid",
                    reference == null
                            ? com.google.gson.JsonNull.INSTANCE
                            : new com.google.gson.JsonPrimitive(reference.toString()));
            owner.addProperty("tamed", animal.isTame());
        } else if (target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
            var reference = horse.getOwnerUUID();
            owner.add(
                    "ownerUuid",
                    reference == null
                            ? com.google.gson.JsonNull.INSTANCE
                            : new com.google.gson.JsonPrimitive(reference.toString()));
            owner.addProperty("tamed", horse.isTamed());
        } else owner.addProperty("readOnlyReason", "native_type_has_no_approved_tame_owner_setter");
        value.add("owner", owner);
        return value;
    }

    private JsonObject relationshipRevision(JsonObject value) {
        return this.revisions.revision(
                "entity_relationship",
                value.get("uuid").getAsString(),
                value.get("lifecycleId").getAsString(),
                value);
    }

    private void verifyRelationshipParticipants(
            JsonObject request, Map<String, Entity> participants) {
        JsonElement values = request.get("relatedResourceVersions");
        if (values == null || !values.isJsonArray() || values.getAsJsonArray().size() > 3)
            throw DebugRelationRequest.invalid(
                    "relatedResourceVersions must explicitly cover affected old/new participants");
        Map<String, JsonObject> supplied = new LinkedHashMap<>();
        for (JsonElement element : values.getAsJsonArray()) {
            if (!element.isJsonObject())
                throw DebugRelationRequest.invalid("Participant versions must be objects");
            JsonObject ref = element.getAsJsonObject();
            ResourceVersionVerifier.token(ref);
            String key = ref.get("resourceKey").getAsString();
            if (supplied.put(key, ref) != null)
                throw DebugRelationRequest.invalid("Duplicate participant version");
        }
        if (!supplied.keySet().equals(participants.keySet()))
            throw DebugRelationRequest.invalid(
                    "Participant versions must match the affected old/new identities exactly");
        participants.forEach(
                (uuid, entity) ->
                        ResourceVersionVerifier.verifyAuthorized(
                                supplied.get(uuid), relationshipRevision(relationship(entity))));
    }

    private JsonObject debugRelation(
            ServerPlayer player, JsonObject request, String kind, boolean playerSource) {
        Entity source =
                playerSource
                        ? player
                        : loadedEntity(player, requiredDebugString(request, "entityUuid"));
        if (!playerSource && source instanceof ServerPlayer)
            throw DebugRelationRequest.invalid("Player relationships require the player namespace");
        JsonObject before = relationship(source), beforeRef = relationshipRevision(before);
        verifyExpected(request, beforeRef);
        JsonElement value = request.get("value"), expectedCurrent;
        String targetUuid;
        if (kind.equals("owner")) {
            JsonObject owner = DebugRelationRequest.owner(value);
            if (request.has("expectedValue"))
                DebugRelationRequest.owner(request.get("expectedValue"));
            targetUuid = DebugRelationRequest.uuid(owner.get("ownerUuid"));
            expectedCurrent = before.getAsJsonObject("owner").deepCopy();
            expectedCurrent.getAsJsonObject().remove("supported");
            expectedCurrent.getAsJsonObject().remove("readOnlyReason");
            if (!before.getAsJsonObject("owner").get("supported").getAsBoolean())
                throw new ProtocolState.ProtocolException(
                        "DEBUG_RELATION_UNSUPPORTED",
                        409,
                        "Native type has no approved owner setter");
        } else {
            targetUuid = DebugRelationRequest.uuid(value);
            if (request.has("expectedValue"))
                DebugRelationRequest.uuid(request.get("expectedValue"));
            expectedCurrent =
                    kind.equals("vehicle")
                            ? before.get("vehicle")
                            : before.getAsJsonObject("leash").get("holderUuid");
            if (kind.equals("leash")
                    && (!(source instanceof Mob)
                            || before.getAsJsonObject("leash")
                                    .get("resolution")
                                    .getAsString()
                                    .equals("pending")))
                throw new ProtocolState.ProtocolException(
                        "DEBUG_RELATION_UNSUPPORTED",
                        409,
                        "Leash type is unsupported or native saved relation remains unresolved");
        }
        DebugRelationRequest.expected(request, expectedCurrent);
        if (value.equals(expectedCurrent)) throw noStateChange();
        Entity target = targetUuid == null ? null : loadedEntity(player, targetUuid);
        if (target == source)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_RELATION_REJECTED", 409, "An entity cannot relate to itself");
        Map<String, Entity> participants = new LinkedHashMap<>();
        if (target != null) participants.put(targetUuid, target);
        if ((kind.equals("vehicle") || (kind.equals("leash") && target != null))
                && source.getVehicle() != null)
            participants.put(source.getVehicle().getUUID().toString(), source.getVehicle());
        if (kind.equals("leash")
                || (kind.equals("vehicle") && target != null && source instanceof Mob)) {
            Entity previous =
                    ((io.github.recrivenvi.minecraftprotocol.probe.mixin.MobLeashAccessor) source)
                            .minecraftProtocol$getLeashHolder();
            if (previous != null) participants.put(previous.getUUID().toString(), previous);
        }
        verifyRelationshipParticipants(request, participants);
        if (target != null && !target.isAlive())
            throw new ProtocolState.ProtocolException(
                    "DEBUG_RELATION_REJECTED", 409, "New native participant is not alive");
        if (kind.equals("owner") && target != null && !(target instanceof ServerPlayer))
            throw new ProtocolState.ProtocolException(
                    "DEBUG_RELATION_REJECTED",
                    409,
                    "Approved tame ownership uses a loaded real player");
        if (kind.equals("leash") && target != null) {
            Set<UUID> visited = new HashSet<>();
            Entity cursor = target;
            while (cursor != null) {
                if (cursor == source || !visited.add(cursor.getUUID()))
                    throw new ProtocolState.ProtocolException(
                            "DEBUG_RELATION_REJECTED",
                            409,
                            "Leash relationship would preserve or create a cycle");
                if (cursor instanceof Mob linked) {
                    var link =
                            (io.github.recrivenvi.minecraftprotocol.probe.mixin.MobLeashAccessor)
                                    linked;
                    if (link.minecraftProtocol$getLeashInfoTag() != null
                            && link.minecraftProtocol$getLeashHolder() == null)
                        throw new ProtocolState.ProtocolException(
                                "DEBUG_RELATION_UNSUPPORTED",
                                409,
                                "Participant leash remains unresolved");
                    cursor = link.minecraftProtocol$getLeashHolder();
                } else cursor = null;
            }
            if (!((Mob) source).canBeLeashed(player))
                throw new ProtocolState.ProtocolException(
                        "DEBUG_RELATION_REJECTED",
                        409,
                        "Native leash predicate rejected this attachment");
        }
        ServerLevel level = (ServerLevel) source.level();
        requireEntityBoundsLoaded(level, source.getBoundingBox());
        if (target != null) requireEntityBoundsLoaded(level, target.getBoundingBox());
        if ((kind.equals("vehicle") || (kind.equals("leash") && target != null))
                && source.getVehicle() != null) {
            Entity vehicle = source.getVehicle();
            // Vanilla searches adjacent dismount blocks and collision escape vectors derived
            // from both widths. Check their loaded envelope before native stopRiding can read it.
            double margin = vehicle.getBbWidth() * Math.sqrt(2) + source.getBbWidth() + 2;
            requireEntityBoundsLoaded(
                    level,
                    vehicle.getBoundingBox().minmax(source.getBoundingBox()).inflate(margin));
        }
        JsonObject beforeEntity = entity(source);
        boolean accepted = true;
        if (kind.equals("vehicle")) {
            if (target == null) source.stopRiding();
            else accepted = source.startRiding(target);
        } else if (kind.equals("leash")) {
            if (target == null) ((Mob) source).dropLeash(true, false);
            else ((Mob) source).setLeashedTo(target, true);
        } else {
            boolean tamed = value.getAsJsonObject().get("tamed").getAsBoolean();
            if (source instanceof net.minecraft.world.entity.TamableAnimal animal) {
                animal.setOwnerUUID(target == null ? null : target.getUUID());
                animal.setTame(tamed);
            } else {
                var horse = (net.minecraft.world.entity.animal.horse.AbstractHorse) source;
                horse.setOwnerUUID(target == null ? null : target.getUUID());
                horse.setTamed(tamed);
            }
        }
        JsonObject after = relationship(source), afterRef = relationshipRevision(after);
        if (!accepted) {
            JsonObject details = new JsonObject();
            details.addProperty("nativeAccepted", false);
            details.addProperty("relationshipChanged", !before.equals(after));
            throw new ProtocolState.ProtocolException(
                    "DEBUG_RELATION_REJECTED",
                    409,
                    "Native non-forced riding rejected the relationship",
                    details);
        }
        JsonObject result =
                mutationResult(
                        request,
                        playerSource ? "player" : "entity",
                        "native_" + kind + "_relationship",
                        "native_relationship_sync",
                        before,
                        after,
                        beforeRef,
                        afterRef);
        result.add("beforeEntity", beforeEntity);
        result.add("afterEntity", entity(source));
        JsonArray related = new JsonArray();
        participants
                .values()
                .forEach(
                        participant -> {
                            JsonObject state = relationship(participant);
                            state.add(
                                    "resourceVersion",
                                    ResourceVersionVerifier.token(relationshipRevision(state)));
                            related.add(state);
                        });
        result.add("relatedAfter", related);
        result.addProperty(
                "cleanup", "reinspect_and_restore_relationship_and_any_native_state_side_effects");
        return result;
    }

    private JsonObject debugWorldTime(MinecraftServer server, JsonObject request) {
        ServerLevel level = server.overworld();
        String id = requiredDebugString(request, "clockId");
        if (!id.equals(level.dimension().location().toString()))
            throw DebugEnvironmentRequest.invalid(
                    "Unknown independent native clockId; derived dimension daytime is not a writable clock");
        JsonObject before = clock(level), beforeRef = clockRevision(before);
        verifyExpected(request, beforeRef);
        long value = DebugEnvironmentRequest.ticks(request.get("value"));
        if (request.has("expectedValue"))
            DebugEnvironmentRequest.ticks(request.get("expectedValue"));
        DebugEnvironmentRequest.expected(request, before.get("totalTicks"));
        if (value == before.get("totalTicks").getAsLong()) throw noStateChange();
        level.setDayTime(value);
        server.getPlayerList()
                .broadcastAll(
                        new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                                level.getGameTime(),
                                level.getDayTime(),
                                level.getGameRules()
                                        .getBoolean(
                                                net.minecraft.world.level.GameRules.RULE_DAYLIGHT)),
                        level.dimension());
        JsonObject after = clock(level);
        return mutationResult(
                request,
                "world",
                "native_overworld_setDayTime",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                clockRevision(after));
    }

    private JsonObject debugWorldWeather(MinecraftServer server, JsonObject request) {
        ServerLevel level = server.overworld();
        JsonObject before = weather(server), beforeRef = weatherRevision(before);
        verifyExpected(request, beforeRef);
        JsonObject value = DebugEnvironmentRequest.weather(request.get("value"));
        if (request.has("expectedValue"))
            DebugEnvironmentRequest.weather(request.get("expectedValue"));
        JsonObject current = weatherValue(before);
        DebugEnvironmentRequest.expected(request, current);
        if (current.equals(value)) throw noStateChange();
        level.setWeatherParameters(
                value.get("clearWeatherTime").getAsInt(),
                value.get("rainTime").getAsInt(),
                value.get("raining").getAsBoolean(),
                value.get("thundering").getAsBoolean());
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData())
                .setThunderTime(value.get("thunderTime").getAsInt());
        JsonObject after = weather(server);
        return mutationResult(
                request,
                "world",
                "native_weather_parameters_and_timer_setters",
                "normal_weather_simulation_sync",
                before,
                after,
                beforeRef,
                weatherRevision(after));
    }

    private JsonObject debugWorldRule(
            MinecraftServer server, ServerLevel level, JsonObject request) {
        String id = requiredDebugString(request, "ruleId");
        JsonObject[] result = {null};
        net.minecraft.world.level.GameRules.visitGameRuleTypes(
                new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
                    @Override
                    public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(
                            net.minecraft.world.level.GameRules.Key<T> key,
                            net.minecraft.world.level.GameRules.Type<T> type) {
                        if (key.getId().equals(id) && level.getGameRules().getRule(key) != null)
                            result[0] = setGameRule(server, level, key, type, request);
                    }
                });
        if (result[0] == null)
            throw DebugEnvironmentRequest.invalid("Unknown available native ruleId");
        return result[0];
    }

    private <T extends net.minecraft.world.level.GameRules.Value<T>> JsonObject setGameRule(
            MinecraftServer server,
            ServerLevel level,
            net.minecraft.world.level.GameRules.Key<T> key,
            net.minecraft.world.level.GameRules.Type<T> type,
            JsonObject request) {
        var nativeRule = level.getGameRules().getRule(key);
        JsonObject before = gameRule(level, key, type), beforeRef = gameRuleRevision(before);
        verifyExpected(request, beforeRef);
        JsonElement value = DebugEnvironmentRequest.rule(request.get("value"));
        if (request.has("expectedValue"))
            DebugEnvironmentRequest.rule(request.get("expectedValue"));
        DebugEnvironmentRequest.expected(request, before.get("value"));
        if (!before.get("typedWritable").getAsBoolean()
                || value.getAsJsonPrimitive().isBoolean()
                        != (nativeRule instanceof net.minecraft.world.level.GameRules.BooleanValue))
            throw DebugEnvironmentRequest.invalid(
                    "Value does not match the native boolean/integer rule type");
        Object parsed;
        try {
            var reader = new com.mojang.brigadier.StringReader(value.toString());
            parsed = type.createArgument("value").getType().parse(reader);
            if (reader.canRead())
                throw DebugEnvironmentRequest.invalid("Trailing rule value input");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw DebugEnvironmentRequest.invalid(
                    "Value violates the native game rule argument domain");
        }
        if (value.equals(before.get("value"))) throw noStateChange();
        if (nativeRule instanceof net.minecraft.world.level.GameRules.BooleanValue booleanRule)
            booleanRule.set((Boolean) parsed, server);
        else
            ((net.minecraft.world.level.GameRules.IntegerValue) nativeRule)
                    .set((Integer) parsed, server);
        JsonObject after = gameRule(level, key, type);
        return mutationResult(
                request,
                "world",
                "native_game_rule_set",
                "native_rule_callback_and_sync",
                before,
                after,
                beforeRef,
                gameRuleRevision(after));
    }

    private JsonObject clock(ServerLevel level) {
        JsonObject value = new JsonObject();
        String id = level.dimension().location().toString();
        value.addProperty("clockId", id);
        value.addProperty("key", id);
        value.addProperty("model", "native_overworld_daytime");
        value.addProperty("totalTicks", level.getDayTime());
        value.addProperty("lifecycleId", this.lifecycles.lifecycleId("world_time", id, level));
        value.addProperty("controlRevision", EnvironmentControlRevisions.time(level));
        value.addProperty("revisionCoverage", "named_native_clock_edits_not_natural_progression");
        value.addProperty("playbackControl", "read_only_simulation_diagnostic");
        return value;
    }

    private JsonObject clockRevision(JsonObject value) {
        return this.revisions.nativeRevision(
                "world_time",
                value.get("key").getAsString(),
                value.get("lifecycleId").getAsString(),
                value.get("controlRevision").getAsLong(),
                "runtime_event_sequence");
    }

    private JsonObject weather(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        JsonObject value = new JsonObject();
        value.addProperty("key", "server_weather");
        value.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId("world_weather", "server_weather", level));
        value.addProperty("clearWeatherTime", data.getClearWeatherTime());
        value.addProperty("rainTime", data.getRainTime());
        value.addProperty("thunderTime", data.getThunderTime());
        value.addProperty("raining", data.isRaining());
        value.addProperty("thundering", data.isThundering());
        value.addProperty("controlRevision", EnvironmentControlRevisions.weather(level));
        value.addProperty(
                "revisionCoverage",
                "named_native_weather_edits_and_current_flags_not_timer_countdown");
        value.addProperty("scope", "native_overworld_weather");
        return value;
    }

    private static JsonObject weatherValue(JsonObject state) {
        JsonObject result = new JsonObject();
        for (String field :
                List.of("clearWeatherTime", "rainTime", "thunderTime", "raining", "thundering"))
            result.add(field, state.get(field).deepCopy());
        return result;
    }

    private JsonObject weatherRevision(JsonObject value) {
        JsonObject control = new JsonObject();
        for (String field : List.of("controlRevision", "raining", "thundering"))
            control.add(field, value.get(field).deepCopy());
        return this.revisions.revision(
                "world_weather",
                value.get("key").getAsString(),
                value.get("lifecycleId").getAsString(),
                control);
    }

    private <T extends net.minecraft.world.level.GameRules.Value<T>> JsonObject gameRule(
            ServerLevel level,
            net.minecraft.world.level.GameRules.Key<T> key,
            net.minecraft.world.level.GameRules.Type<T> type) {
        var nativeRule = level.getGameRules().getRule(key);
        JsonObject value = new JsonObject();
        String id = key.getId();
        value.addProperty("ruleId", id);
        value.addProperty("key", level.dimension().location() + "@" + id);
        value.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId(
                        "world_rule", value.get("key").getAsString(), level.getGameRules()));
        boolean writable =
                nativeRule instanceof net.minecraft.world.level.GameRules.BooleanValue
                        || nativeRule instanceof net.minecraft.world.level.GameRules.IntegerValue;
        if (nativeRule instanceof net.minecraft.world.level.GameRules.BooleanValue flag)
            value.addProperty("value", flag.get());
        else if (nativeRule instanceof net.minecraft.world.level.GameRules.IntegerValue number)
            value.addProperty("value", number.get());
        else value.addProperty("value", nativeRule.serialize());
        value.addProperty(
                "valueType",
                nativeRule instanceof net.minecraft.world.level.GameRules.BooleanValue
                        ? "boolean"
                        : nativeRule instanceof net.minecraft.world.level.GameRules.IntegerValue
                                ? "integer"
                                : nativeRule.getClass().getSimpleName());
        value.addProperty("typedWritable", writable);
        if (!writable) value.addProperty("readOnlyReason", "unsupported_native_rule_value_type");
        if (type.createArgument("value").getType()
                instanceof com.mojang.brigadier.arguments.IntegerArgumentType domain) {
            value.addProperty("minimum", domain.getMinimum());
            value.addProperty("maximum", domain.getMaximum());
        }
        value.addProperty("scope", "native_level_game_rules");
        return value;
    }

    private JsonObject gameRuleRevision(JsonObject value) {
        JsonObject ref =
                this.revisions.revision(
                        "world_rule",
                        value.get("key").getAsString(),
                        value.get("lifecycleId").getAsString(),
                        value);
        if (!value.get("typedWritable").getAsBoolean())
            ref.addProperty("mutationPreconditionEligible", false);
        return ref;
    }

    private JsonObject environment(MinecraftServer server, ServerLevel level) {
        JsonObject value = new JsonObject();
        JsonArray clocks = new JsonArray();
        clocks.add(clock(server.overworld()));
        value.add("clocks", clocks);
        value.add("weather", weather(server));
        List<JsonObject> rules = new ArrayList<>();
        net.minecraft.world.level.GameRules.visitGameRuleTypes(
                new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
                    @Override
                    public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(
                            net.minecraft.world.level.GameRules.Key<T> key,
                            net.minecraft.world.level.GameRules.Type<T> type) {
                        if (level.getGameRules().getRule(key) != null)
                            rules.add(gameRule(level, key, type));
                    }
                });
        JsonArray array = new JsonArray();
        rules.stream()
                .sorted(Comparator.comparing(rule -> rule.get("ruleId").getAsString()))
                .forEach(array::add);
        value.add("rules", array);
        value.addProperty("chunkLoadRequested", false);
        value.addProperty("storageAccessed", false);
        return value;
    }

    private static ServerLevel existingDimension(MinecraftServer server, String id) {
        ServerLevel level =
                server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id)));
        if (level == null)
            throw new ProtocolState.ProtocolException(
                    "DIMENSION_UNAVAILABLE",
                    409,
                    "Dimension has no current LIVE ServerLevel; no creation requested");
        return level;
    }

    private JsonArray selectedEntities(MinecraftServer server, JsonElement selector) {
        JsonArray entries = new JsonArray();
        for (var target : DebugTransferRequest.targets(selector)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("dimension", target.dimension());
            entry.addProperty("entityUuid", target.entityUuid().toString());
            entry.addProperty("chunkLoadRequested", false);
            ServerLevel level =
                    server.getLevel(
                            ResourceKey.create(
                                    Registries.DIMENSION,
                                    ResourceLocation.parse(target.dimension())));
            Entity entity = level == null ? null : level.getEntity(target.entityUuid());
            boolean available =
                    entity != null
                            && !entity.isRemoved()
                            && level.getChunkSource()
                                            .getChunkNow(
                                                    entity.chunkPosition().x,
                                                    entity.chunkPosition().z)
                                    != null;
            entry.addProperty("available", available);
            if (available) {
                JsonObject state = entity(entity);
                state.remove("extensions");
                state.remove("nonDefaultTrackedValues");
                entry.add("state", state);
            } else
                entry.addProperty(
                        "reason", level == null ? "dimension_unavailable" : "entity_not_loaded");
            entries.add(entry);
        }
        return entries;
    }

    private static JsonObject transferValue(Entity entity) {
        JsonObject value = new JsonObject(), at = new JsonObject();
        at.addProperty("x", entity.getX());
        at.addProperty("y", entity.getY());
        at.addProperty("z", entity.getZ());
        value.addProperty("dimension", entity.level().dimension().location().toString());
        value.add("position", at);
        value.addProperty("yaw", entity.getYRot());
        value.addProperty("pitch", entity.getXRot());
        return value;
    }

    private JsonObject debugTransfer(
            MinecraftServer server, ServerPlayer player, JsonObject request, boolean ownPlayer) {
        ServerLevel source =
                ownPlayer
                        ? player.serverLevel()
                        : existingDimension(
                                server, DebugTransferRequest.dimension(request, "sourceDimension"));
        Entity target =
                ownPlayer
                        ? player
                        : loadedEntity(source, requiredDebugString(request, "entityUuid"));
        if (target == null
                || target.isRemoved()
                || source.getChunkSource()
                                .getChunkNow(target.chunkPosition().x, target.chunkPosition().z)
                        == null) throw targetNotLoaded("transfer source entity");
        if (!ownPlayer && target instanceof ServerPlayer)
            throw DebugTransferRequest.invalid(
                    "Players require their own current-player namespace");
        JsonObject before = ownPlayer ? player(player) : entity(target);
        JsonObject beforeRef = ownPlayer ? playerRevision(before) : entityRevision(before);
        verifyExpected(request, beforeRef);
        if (!ownPlayer
                && request.has("expectedEntityType")
                && !request.get("expectedEntityType").equals(before.get("type")))
            throw valueMismatch("expectedEntityType", before.get("type").getAsString());
        var destination = DebugTransferRequest.destination(request);
        if (request.has("expectedValue")) {
            JsonObject expected = new JsonObject();
            expected.add("value", request.get("expectedValue"));
            DebugTransferRequest.destination(expected);
            io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.verifyValues(
                    request, transferValue(target));
        }
        ServerLevel level = existingDimension(server, destination.dimension());
        float yaw = destination.yaw() == null ? target.getYRot() : destination.yaw();
        float pitch = destination.pitch() == null ? target.getXRot() : destination.pitch();
        if (source == level
                && target.getX() == destination.x()
                && target.getY() == destination.y()
                && target.getZ() == destination.z()
                && target.getYRot() == yaw
                && target.getXRot() == pitch) throw noStateChange();
        if (!target.isAlive())
            throw DebugTransferRequest.invalid(
                    "Removed or dead actors require the native lifecycle, not transform");
        JsonObject relation = relationship(target), leash = relation.getAsJsonObject("leash");
        if (target.isPassenger()
                || target.isVehicle()
                || leash.has("resolution") && !leash.get("resolution").getAsString().equals("none"))
            throw new ProtocolState.ProtocolException(
                    "DEBUG_TRANSFER_RELATION_CONFLICT",
                    409,
                    "Detach ride/leash relationships with their explicit versioned operations before transfer");
        if (source != level)
            for (ServerPlayer observer : source.players())
                if (observer != target && observer.getCamera() == target)
                    throw new ProtocolState.ProtocolException(
                            "DEBUG_TRANSFER_RELATION_CONFLICT",
                            409,
                            "A native spectator follows this actor; detach that camera before cross-dimension transfer");
        if (!Level.isInSpawnableBounds(
                BlockPos.containing(destination.x(), destination.y(), destination.z())))
            throw DebugTransferRequest.invalid("Position is outside native teleport bounds");
        requireEntityBoundsLoaded(
                level,
                target.getBoundingBox()
                        .move(
                                destination.x() - target.getX(),
                                destination.y() - target.getY(),
                                destination.z() - target.getZ()));
        boolean accepted;
        try (LoadedChunkMutationScope noLoad = LoadedChunkMutationScope.open()) {
            try {
                accepted =
                        target.teleportTo(
                                level,
                                destination.x(),
                                destination.y(),
                                destination.z(),
                                Set.of(),
                                yaw,
                                pitch);
            } catch (RuntimeException failure) {
                JsonObject blocked = noLoad.blockedAccess();
                if (blocked == null) throw failure;
                Entity actual = level.getEntity(target.getUUID());
                if (actual == null) actual = source.getEntity(target.getUUID());
                if (actual == null) actual = target;
                JsonObject after = ownPlayer ? player(player) : entity(actual);
                JsonObject ref = ownPlayer ? playerRevision(after) : entityRevision(after);
                if (actual.isRemoved()) ref.addProperty("mutationPreconditionEligible", false);
                JsonObject partial =
                        mutationReceipt(
                                request,
                                ownPlayer ? "player" : "entity",
                                "native_entity_teleportTo",
                                "partial_native_transfer",
                                before,
                                after,
                                beforeRef,
                                ref);
                partial.addProperty("nativeEffectsComplete", false);
                partial.addProperty("mutationMayHaveApplied", true);
                partial.addProperty("retrySafe", false);
                partial.add("blockedChunk", blocked);
                partial.addProperty(
                        "destinationEntityPresent", level.getEntity(target.getUUID()) != null);
                JsonObject details = new JsonObject();
                details.add("partialMutation", partial);
                throw new ProtocolState.ProtocolException(
                        "DEBUG_MUTATION_PARTIAL_NOT_LOADED",
                        409,
                        "Native transfer reached an unloaded dependency; effects are not rolled back",
                        details);
            }
        }
        if (!accepted)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_TRANSFER_REJECTED",
                    409,
                    "Native entity teleport rejected the transition");
        Entity actual = java.util.Objects.requireNonNull(level.getEntity(target.getUUID()));
        JsonObject after = ownPlayer ? player(player) : entity(actual);
        JsonObject result =
                mutationResult(
                        request,
                        ownPlayer ? "player" : "entity",
                        "native_entity_teleportTo",
                        "native_position_or_dimension_packets",
                        before,
                        after,
                        beforeRef,
                        ownPlayer ? playerRevision(after) : entityRevision(after));
        result.addProperty("identityRebuilt", actual != target);
        result.addProperty("crossDimension", source != level);
        result.addProperty("requiresFreshWorldFingerprintAndArm", ownPlayer && source != level);
        result.addProperty("nativeSerializationHooks", !ownPlayer && source != level);
        result.addProperty(
                "sideEffects",
                "native velocity/camera/item-use and dimension triggers; no rollback; not a player input");
        return result;
    }

    private JsonObject debugEntitySpawn(ServerPlayer player, JsonObject request) {
        var factory = DebugEntityLifecycleRequest.spawn(request);
        ServerLevel level = player.serverLevel();
        JsonObject before = entityRegistry(level, factory.chunk());
        requireEntityRegistry(before);
        JsonObject beforeRef = entityRegistryRevision(before);
        verifyExpected(request, beforeRef);
        if (before.get("count").getAsInt() >= DebugEntityLifecycleRequest.MAX_CHUNK_MEMBERS)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_ENTITY_REGISTRY_LIMIT_EXCEEDED",
                    413,
                    "Insertion would exceed the complete chunk membership bound");
        if (request.has("expectedEntityCount")
                && DebugEntityLifecycleRequest.integer(
                                request,
                                "expectedEntityCount",
                                0,
                                DebugEntityLifecycleRequest.MAX_CHUNK_MEMBERS)
                        != before.get("count").getAsInt())
            throw valueMismatch("expectedEntityCount", before.get("count").getAsString());
        ResourceLocation identifier = ResourceLocation.tryParse(factory.entityType());
        var type =
                identifier == null
                        ? null
                        : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
        if (type == null) throw DebugEntityLifecycleRequest.invalid("Unknown native entity type");
        BlockPos position = BlockPos.containing(factory.x(), factory.y(), factory.z());
        if (!Level.isInSpawnableBounds(position))
            throw DebugEntityLifecycleRequest.invalid("Position is outside native spawn bounds");
        double halfWidth = type.getWidth() / 2.0;
        requireEntityBoundsLoaded(
                level,
                new net.minecraft.world.phys.AABB(
                        factory.x() - halfWidth,
                        factory.y(),
                        factory.z() - halfWidth,
                        factory.x() + halfWidth,
                        factory.y() + type.getHeight(),
                        factory.z() + halfWidth));
        Entity created = type.create(level);
        if (created == null)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_ENTITY_FACTORY_UNAVAILABLE",
                    409,
                    "Native entity type has no standalone factory; no entity was inserted");
        created.moveTo(factory.x(), factory.y(), factory.z(), factory.yaw(), factory.pitch());
        requireEntityBoundsLoaded(level, created.getBoundingBox());
        if (!level.addFreshEntity(created))
            throw new ProtocolState.ProtocolException(
                    "DEBUG_ENTITY_INSERTION_REJECTED",
                    409,
                    "Native server rejected entity insertion");
        JsonObject after = entityRegistry(level, factory.chunk());
        JsonObject result =
                mutationResult(
                        request,
                        "entity",
                        "native_entity_factory_and_addFreshEntity",
                        "normal_network_sync",
                        before,
                        after,
                        beforeRef,
                        entityRegistryRevision(after));
        JsonObject state = entity(created);
        result.add("createdEntity", state);
        result.add(
                "createdEntityResourceVersion",
                ResourceVersionVerifier.token(entityRevision(state)));
        result.addProperty("factorySemantics", "native_factory_defaults_no_nbt_no_finalize_spawn");
        result.addProperty("cleanup", "remove_only_the_returned_created_entity_uuid");
        return result;
    }

    private JsonObject debugEntityRemove(
            MinecraftServer server, ServerPlayer player, JsonObject request) {
        ServerLevel level =
                request.has("sourceDimension")
                        ? existingDimension(
                                server, DebugTransferRequest.dimension(request, "sourceDimension"))
                        : player.serverLevel();
        Entity target = loadedEntity(level, requiredDebugString(request, "entityUuid"));
        if (target instanceof ServerPlayer)
            throw DebugEntityLifecycleRequest.invalid(
                    "Players cannot be removed through entity namespace");
        JsonObject before = entity(target), beforeRef = entityRevision(before);
        verifyExpected(request, beforeRef);
        if (request.has("expectedEntityType")
                && !request.get("expectedEntityType").equals(before.get("type")))
            throw valueMismatch("expectedEntityType", before.get("type").getAsString());
        var chunk =
                new DebugEntityLifecycleRequest.Chunk(
                        target.chunkPosition().x, target.chunkPosition().z);
        entityRegistry(level, chunk);
        target.discard();
        JsonObject after = entity(target), afterRef = entityRevision(after);
        afterRef.addProperty("mutationPreconditionEligible", false);
        JsonObject result =
                mutationResult(
                        request,
                        "entity",
                        "native_entity_discard",
                        "normal_network_sync",
                        before,
                        after,
                        beforeRef,
                        afterRef);
        entityRegistryRevision(entityRegistry(level, chunk));
        result.addProperty("cleanup", "irreversible_discard_native_cleanup_no_identity_restore");
        return result;
    }

    private static void requireEntityBoundsLoaded(
            ServerLevel level, net.minecraft.world.phys.AABB box) {
        int minX = ((int) Math.floor(box.minX)) >> 4,
                maxX = ((int) Math.floor(Math.nextDown(box.maxX))) >> 4;
        int minZ = ((int) Math.floor(box.minZ)) >> 4,
                maxZ = ((int) Math.floor(Math.nextDown(box.maxZ))) >> 4;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null)
                    throw new ProtocolState.ProtocolException(
                            "TARGET_NOT_LOADED",
                            409,
                            "Entity bounds require an unloaded chunk; no load requested");
    }

    private static void requireEntityRegistry(JsonObject registry) {
        if (!registry.get("available").getAsBoolean())
            throw new ProtocolState.ProtocolException(
                    "TARGET_NOT_LOADED",
                    409,
                    "Entity destination chunk is not loaded; no load requested");
    }

    private JsonObject entityRegistry(
            ServerLevel level, DebugEntityLifecycleRequest.Chunk position) {
        JsonObject result = new JsonObject();
        result.addProperty("chunkX", position.x());
        result.addProperty("chunkZ", position.z());
        result.addProperty(
                "key",
                level.dimension().location()
                        + "@entity_chunk:"
                        + position.x()
                        + ","
                        + position.z());
        result.addProperty("chunkLoadRequested", false);
        LevelChunk chunk = level.getChunkSource().getChunkNow(position.x(), position.z());
        result.addProperty("available", chunk != null);
        if (chunk == null) {
            result.addProperty("reason", "chunk_not_loaded");
            return result;
        }
        result.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId(
                        "entity_registry", result.get("key").getAsString(), chunk));
        List<Entity> members = new ArrayList<>();
        double x = position.x() * 16.0, z = position.z() * 16.0;
        level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class),
                new net.minecraft.world.phys.AABB(x, -20_000_000, z, x + 16, 20_000_000, z + 16),
                candidate ->
                        !candidate.isRemoved()
                                && candidate.chunkPosition().x == position.x()
                                && candidate.chunkPosition().z == position.z(),
                members,
                DebugEntityLifecycleRequest.MAX_CHUNK_MEMBERS + 1);
        if (members.size() > DebugEntityLifecycleRequest.MAX_CHUNK_MEMBERS)
            throw new ProtocolState.ProtocolException(
                    "DEBUG_ENTITY_REGISTRY_LIMIT_EXCEEDED",
                    413,
                    "Loaded chunk entity membership exceeds 4096; no partial mutation precondition");
        JsonArray uuids = new JsonArray();
        members.stream().map(e -> e.getUUID().toString()).sorted().forEach(uuids::add);
        result.add("members", uuids);
        result.addProperty("count", uuids.size());
        return result;
    }

    private JsonObject entityRegistryRevision(JsonObject state) {
        return this.revisions.revision(
                "entity_registry",
                state.get("key").getAsString(),
                state.get("lifecycleId").getAsString(),
                state);
    }

    private JsonObject entityRevision(JsonObject state) {
        return this.revisions.revision(
                "entity",
                state.get("uuid").getAsString(),
                state.get("lifecycleId").getAsString(),
                canonicalDebugEntity(state));
    }

    private static JsonObject debugStateProjection(JsonObject state, boolean player) {
        JsonObject result = new JsonObject();
        for (String field :
                player
                        ? List.of(
                                "food",
                                "saturation",
                                "air",
                                "absorption",
                                "experienceLevel",
                                "experiencePoints",
                                "flying")
                        : List.of(
                                "health",
                                "air",
                                "absorption",
                                "silent",
                                "noGravity",
                                "glowing",
                                "noAi")) if (state.has(field)) result.add(field, state.get(field));
        JsonObject motion = player ? state.getAsJsonObject("position") : state;
        JsonObject velocity = new JsonObject();
        for (String axis : List.of("x", "y", "z"))
            velocity.add(axis, motion.get("velocity" + axis.toUpperCase(java.util.Locale.ROOT)));
        result.add("velocity", velocity);
        return result;
    }

    private static void applyDebugVelocity(Entity target, JsonObject velocity) {
        target.setDeltaMovement(
                velocity.get("x").getAsDouble(),
                velocity.get("y").getAsDouble(),
                velocity.get("z").getAsDouble());
    }

    private JsonObject debugEffect(
            ServerPlayer player, JsonObject request, boolean playerResource) {
        LivingEntity target = playerResource ? player : debugLivingEntity(player, request);
        JsonObject before = playerResource ? player(player) : entity(target);
        JsonObject beforeRef = playerResource ? playerRevision(before) : entityRevision(before);
        verifyExpected(request, beforeRef);
        if (!playerResource) verifyDebugEntityType(request, target);
        var parsed = io.github.recrivenvi.minecraftprotocol.core.DebugEffectRequest.parse(request);
        ResourceLocation id = ResourceLocation.tryParse(parsed.effectId());
        if (id == null
                || !id.getNamespace().equals("minecraft")
                || !BuiltInRegistries.MOB_EFFECT.containsKey(id))
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE", 409, "Unknown or unapproved native effect");
        var holder = BuiltInRegistries.MOB_EFFECT.get(id);
        MobEffectInstance current = target.getEffect(holder);
        JsonObject observed = current == null ? null : effectValue(current);
        if (observed != null && !observed.remove("hiddenChainComplete").getAsBoolean())
            throw new ProtocolState.ProtocolException(
                    "DEBUG_EFFECT_CHAIN_INCOMPLETE",
                    409,
                    "Current native hidden chain exceeds the typed restoration budget; no change applied");
        parsed.verifyExpected(observed);
        if (io.github.recrivenvi.minecraftprotocol.core.DebugEffectRequest.json(parsed.value())
                .equals(
                        io.github.recrivenvi.minecraftprotocol.core.DebugEffectRequest.json(
                                observed))) throw noStateChange();
        if (parsed.value() == null) target.removeEffect(holder);
        else target.forceAddEffect(buildEffect(holder, parsed.value()), player);
        JsonObject after = playerResource ? player(player) : entity(target);
        return mutationResult(
                request,
                playerResource ? "player" : "entity",
                "native_effect_replace_or_remove",
                "normal_network_sync_and_native_effect_callbacks",
                before,
                after,
                beforeRef,
                playerResource ? playerRevision(after) : entityRevision(after));
    }

    private static MobEffectInstance buildEffect(
            net.minecraft.world.effect.MobEffect effect, JsonObject value) {
        MobEffectInstance hidden = null;
        JsonArray chain = value.getAsJsonArray("hidden");
        for (int index = chain.size() - 1; index >= 0; index--)
            hidden = effectLeaf(effect, chain.get(index).getAsJsonObject(), hidden);
        return effectLeaf(effect, value, hidden);
    }

    private static MobEffectInstance effectLeaf(
            net.minecraft.world.effect.MobEffect effect,
            JsonObject value,
            MobEffectInstance hidden) {
        return new MobEffectInstance(
                effect,
                value.get("duration").getAsInt(),
                value.get("amplifier").getAsInt(),
                value.get("ambient").getAsBoolean(),
                value.get("showParticles").getAsBoolean(),
                value.get("showIcon").getAsBoolean(),
                hidden,
                effect.createFactorData());
    }

    private static JsonObject effectLeafValue(MobEffectInstance instance) {
        JsonObject result = new JsonObject();
        result.addProperty("duration", instance.getDuration());
        result.addProperty("amplifier", instance.getAmplifier());
        result.addProperty("ambient", instance.isAmbient());
        result.addProperty("showParticles", instance.isVisible());
        result.addProperty("showIcon", instance.showIcon());
        return result;
    }

    private static MobEffectInstance hiddenEffect(MobEffectInstance instance) {
        return ((io.github.recrivenvi.minecraftprotocol.probe.mixin.MobEffectInstanceAccessor)
                        (Object) instance)
                .minecraftProtocol$getHiddenEffect();
    }

    private static JsonObject effectValue(MobEffectInstance instance) {
        JsonObject result = effectLeafValue(instance);
        JsonArray chain = new JsonArray();
        MobEffectInstance hidden = hiddenEffect(instance);
        while (hidden != null
                && chain.size()
                        < io.github.recrivenvi.minecraftprotocol.core.DebugEffectRequest
                                .MAX_HIDDEN_EFFECTS) {
            chain.add(effectLeafValue(hidden));
            hidden = hiddenEffect(hidden);
        }
        result.add("hidden", chain);
        result.addProperty("hiddenChainComplete", hidden == null);
        return result;
    }

    private static JsonArray nativeEffects(LivingEntity living) {
        List<JsonObject> entries = new ArrayList<>();
        for (MobEffectInstance instance : living.getActiveEffects()) {
            JsonObject value = effectValue(instance);
            value.addProperty(
                    "id", BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).toString());
            entries.add(value);
        }
        entries.sort(Comparator.comparing(v -> v.get("id").getAsString()));
        JsonArray result = new JsonArray();
        entries.forEach(result::add);
        return result;
    }

    private JsonObject debugPlayerAttribute(ServerPlayer player, JsonObject request) {
        return debugAttribute(player, request, true);
    }

    private JsonObject debugEntityAttribute(ServerPlayer player, JsonObject request) {
        return debugAttribute(player, request, false);
    }

    private JsonObject debugAttribute(
            ServerPlayer player, JsonObject request, boolean playerResource) {
        LivingEntity target = playerResource ? player : debugLivingEntity(player, request);
        JsonObject before = playerResource ? player(player) : entity(target);
        JsonObject beforeRef = playerResource ? playerRevision(before) : entityRevision(before);
        verifyExpected(request, beforeRef);
        if (!playerResource) verifyDebugEntityType(request, target);
        String attributeId = requiredDebugString(request, "attributeId");
        ResourceLocation id =
                attributeId.equals("minecraft:max_health")
                        ? BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH)
                        : ResourceLocation.tryParse(attributeId);
        if (id == null
                || !id.getNamespace().equals("minecraft")
                || !BuiltInRegistries.ATTRIBUTE.containsKey(id))
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE",
                    409,
                    "Unknown or unapproved native attribute: " + attributeId);
        var holder = BuiltInRegistries.ATTRIBUTE.get(id);
        if (!target.getAttributes().hasAttribute(holder))
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE",
                    409,
                    "This native entity does not have the requested attribute");
        double current = target.getAttributes().getBaseValue(holder);
        double requested = requiredFiniteDouble(request, "value", -1.0E12, 1.0E12);
        if (Double.compare(holder.sanitizeValue(requested), requested) != 0)
            throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                    "value is outside the actual native attribute domain");
        if (request.has("expectedValue")
                && Double.compare(
                                requiredFiniteDouble(request, "expectedValue", -1.0E12, 1.0E12),
                                current)
                        != 0) throw valueMismatch("expectedValue", current);
        if (Double.compare(requested, current) == 0) throw noStateChange();
        target.getAttribute(holder).setBaseValue(requested);
        JsonObject after = playerResource ? player(player) : entity(target);
        return mutationResult(
                request,
                playerResource ? "player" : "entity",
                "native_attribute_base_setter",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                playerResource ? playerRevision(after) : entityRevision(after));
    }

    private static LivingEntity debugLivingEntity(ServerPlayer player, JsonObject request) {
        Entity target = loadedEntity(player, requiredDebugString(request, "entityUuid"));
        if (!(target instanceof LivingEntity living) || target instanceof ServerPlayer)
            throw io.github.recrivenvi.minecraftprotocol.core.DebugStateRequest.invalid(
                    "This operation requires a loaded non-player LivingEntity");
        return living;
    }

    private static void verifyDebugEntityType(JsonObject request, LivingEntity target) {
        if (request.has("expectedEntityType")
                && !request.get("expectedEntityType")
                        .getAsString()
                        .equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()))
            throw valueMismatch(
                    "expectedEntityType",
                    BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
    }

    private JsonObject debugEntityEquipment(ServerPlayer player, JsonObject request) {
        LivingEntity target = debugLivingEntity(player, request);
        JsonObject before = entity(target), beforeRef = entityRevision(before);
        verifyExpected(request, beforeRef);
        verifyDebugEntityType(request, target);
        EquipmentSlot slot = debugEquipmentSlot(request, false);
        ItemStack current = target.getItemBySlot(slot);
        verifyExpectedItem(request, current);
        ItemStack replacement = debugReplacementItem(request);
        if (ItemStack.matches(current, replacement)) throw noStateChange();
        target.setItemSlot(slot, replacement);
        JsonObject after = entity(target);
        return mutationResult(
                request,
                "entity",
                "native_entity_equipment_setter",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                entityRevision(after));
    }

    private static EquipmentSlot debugEquipmentSlot(JsonObject request, boolean playerResource) {
        String name = requiredDebugString(request, "equipmentSlot");
        if (!playerResource
                || Set.of("mainhand", "offhand", "head", "chest", "legs", "feet").contains(name))
            for (EquipmentSlot slot : EquipmentSlot.values())
                if (slot.getName().equals(name)) return slot;
        throw new ProtocolState.ProtocolException(
                "INVALID_DEBUG_ITEM", 400, "Unknown or unsupported native equipmentSlot");
    }

    private static JsonArray nativeAttributes(LivingEntity living) {
        JsonArray result = new JsonArray();
        var instances =
                ((io.github.recrivenvi.minecraftprotocol.probe.mixin.AttributeMapAccessor)
                                (Object) living.getAttributes())
                        .minecraftProtocol$getAttributes();
        List<JsonObject> entries = new ArrayList<>();
        for (var attribute : BuiltInRegistries.ATTRIBUTE) {
            var holder = attribute;
            if (!living.getAttributes().hasAttribute(holder)) continue;
            JsonObject value = new JsonObject();
            var id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
            value.addProperty("id", id.toString());
            value.addProperty("base", living.getAttributes().getBaseValue(holder));
            value.addProperty("value", living.getAttributes().getValue(holder));
            value.addProperty("typedBaseWritable", id.getNamespace().equals("minecraft"));
            if (attribute
                    instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute range) {
                value.addProperty("minimum", range.getMinValue());
                value.addProperty("maximum", range.getMaxValue());
            }
            List<JsonObject> modifiers = new ArrayList<>();
            AttributeInstance instance = instances.get(holder);
            if (instance != null)
                for (var modifier : instance.getModifiers()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", modifier.getId().toString());
                    entry.addProperty("amount", modifier.getAmount());
                    entry.addProperty("operation", modifier.getOperation().toString());
                    modifiers.add(entry);
                }
            modifiers.sort(Comparator.comparing(v -> v.get("id").getAsString()));
            JsonArray normalized = new JsonArray();
            modifiers.forEach(normalized::add);
            value.add("modifiers", normalized);
            entries.add(value);
        }
        entries.sort(Comparator.comparing(v -> v.get("id").getAsString()));
        entries.forEach(result::add);
        return result;
    }

    private JsonObject debugEntityNoGravity(ServerPlayer player, JsonObject request) {
        String uuid = requiredDebugString(request, "entityUuid");
        Entity target = loadedEntity(player, uuid);
        JsonObject before = entity(target);
        JsonObject beforeRef =
                this.revisions.revision(
                        "entity",
                        uuid,
                        before.get("lifecycleId").getAsString(),
                        canonicalDebugEntity(before));
        verifyExpected(request, beforeRef);
        if (request.has("expectedEntityType")
                && !request.get("expectedEntityType")
                        .getAsString()
                        .equals(before.get("type").getAsString())) {
            throw valueMismatch("expectedEntityType", before.get("type").getAsString());
        }
        boolean requested = requiredDebugBoolean(request, "value");
        if (request.has("expectedNoGravity")
                && request.get("expectedNoGravity").getAsBoolean() != target.isNoGravity()) {
            throw valueMismatch("expectedNoGravity", target.isNoGravity());
        }
        if (requested == target.isNoGravity()) throw noStateChange();
        target.setNoGravity(requested);
        JsonObject after = entity(target);
        JsonObject afterRef =
                this.revisions.revision(
                        "entity",
                        uuid,
                        after.get("lifecycleId").getAsString(),
                        canonicalDebugEntity(after));
        return mutationResult(
                request,
                "entity",
                "server_entity_state",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                afterRef);
    }

    private JsonObject debugWorldBlock(ServerPlayer player, JsonObject request) {
        ServerLevel level = player.serverLevel();
        BlockPos position = requiredPosition(request);
        JsonObject before = blockSnapshot(level, position);
        if (!before.get("available").getAsBoolean()) throw targetNotLoaded("block");
        JsonObject beforeRef = this.blockRevision(level, before);
        verifyExpected(request, beforeRef);
        if (request.has("expectedBlockId")
                && !request.get("expectedBlockId")
                        .getAsString()
                        .equals(before.get("blockId").getAsString())) {
            throw valueMismatch("expectedBlockId", before.get("blockId").getAsString());
        }
        String blockId = requiredDebugString(request, "blockId");
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new ProtocolState.ProtocolException(
                    "UNKNOWN_BLOCK", 400, "Unknown block: " + blockId);
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        DebugBlockStateRequest.verifyExpected(request, before.getAsJsonObject("properties"));
        BlockState current = level.getBlockState(position);
        BlockState requested = current.is(block) ? current : block.defaultBlockState();
        for (var entry : DebugBlockStateRequest.properties(request, "properties").entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                throw new ProtocolState.ProtocolException(
                        "UNKNOWN_BLOCK_PROPERTY", 400, "Unknown property: " + entry.getKey());
            }
            requested = setDebugProperty(requested, property, entry.getValue());
        }
        if (requested == current) throw noStateChange();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbor = position.relative(direction);
            if (level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                    == null) throw targetNotLoaded("native block update neighbor");
        }
        try (LoadedChunkMutationScope noLoad = LoadedChunkMutationScope.open()) {
            try {
                level.setBlockAndUpdate(position, requested);
            } catch (RuntimeException failure) {
                JsonObject blocked = noLoad.blockedAccess();
                if (blocked == null) throw failure;
                JsonObject partialAfter = blockSnapshot(level, position);
                JsonObject partial =
                        mutationReceipt(
                                request,
                                "world",
                                "vanilla_server_level_set_block",
                                "partial_native_neighbor_updates",
                                before,
                                partialAfter,
                                beforeRef,
                                this.blockRevision(level, partialAfter));
                partial.addProperty("nativeEffectsComplete", false);
                partial.addProperty("mutationMayHaveApplied", true);
                partial.addProperty("retrySafe", false);
                partial.add("blockedChunk", blocked);
                JsonObject details = new JsonObject();
                details.add("partialMutation", partial);
                details.addProperty("retrySafe", false);
                throw new ProtocolState.ProtocolException(
                        "DEBUG_MUTATION_PARTIAL_NOT_LOADED",
                        409,
                        "Native propagation reached an unloaded dependency; applied effects are not rolled back",
                        details);
            }
        }
        JsonObject after = blockSnapshot(level, position);
        JsonObject result =
                mutationResult(
                        request,
                        "world",
                        "vanilla_server_level_set_block",
                        "normal_network_sync",
                        before,
                        after,
                        beforeRef,
                        this.blockRevision(level, after));
        result.addProperty(
                "sideEffects",
                "native_neighbor_updates_and_block_entity_replacement; secondary_changes_or_drops_possible; no_multi_block_rollback");
        return result;
    }

    static <T extends Comparable<T>> T parseDebugProperty(Property<T> property, String value) {
        return property.getValue(value)
                .orElseThrow(
                        () ->
                                new ProtocolState.ProtocolException(
                                        "INVALID_BLOCK_PROPERTY_VALUE",
                                        400,
                                        "Invalid value for property: " + property.getName()));
    }

    private static <T extends Comparable<T>> BlockState setDebugProperty(
            BlockState state, Property<T> property, String value) {
        return state.setValue(property, parseDebugProperty(property, value));
    }

    private static <T extends Comparable<T>> String debugPropertyName(
            BlockState state, Property<T> property) {
        return serializeDebugProperty(property, state.getValue(property));
    }

    static <T extends Comparable<T>> String serializeDebugProperty(Property<T> property, T value) {
        return property.getName(value);
    }

    private static JsonObject debugBlockProperties(BlockState state) {
        // Enum.toString() is not the native wire name (for example NoteBlockInstrument).
        JsonObject properties = new JsonObject();
        state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .forEach(
                        property ->
                                properties.addProperty(
                                        property.getName(), debugPropertyName(state, property)));
        return properties;
    }

    private JsonObject debugBlockEntityCustomName(ServerPlayer player, JsonObject request) {
        ServerLevel level = player.serverLevel();
        BlockPos position = requiredPosition(request);
        JsonObject before = blockEntitySnapshot(level, position, true);
        if (!before.get("available").getAsBoolean()) throw targetNotLoaded("block_entity");
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof BaseContainerBlockEntity container)) {
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE",
                    409,
                    "custom_name.set requires a loaded container Block Entity");
        }
        JsonObject beforeRef = this.blockEntitySerializedRevision(level, before);
        verifyExpected(request, beforeRef);
        if (request.has("expectedBlockEntityType")
                && !request.get("expectedBlockEntityType")
                        .getAsString()
                        .equals(before.get("type").getAsString())) {
            throw valueMismatch("expectedBlockEntityType", before.get("type").getAsString());
        }
        String requested =
                request.has("customName") && !request.get("customName").isJsonNull()
                        ? request.get("customName").getAsString()
                        : null;
        if (requested != null && requested.length() > 128) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "customName is limited to 128 characters");
        }
        String current =
                container.getCustomName() == null ? null : container.getCustomName().getString();
        if (request.has("expectedCustomName")) {
            String expected =
                    request.get("expectedCustomName").isJsonNull()
                            ? null
                            : request.get("expectedCustomName").getAsString();
            if (!java.util.Objects.equals(expected, current)) {
                throw valueMismatch("expectedCustomName", current);
            }
        }
        if (java.util.Objects.equals(requested, current)) throw noStateChange();
        ((BaseContainerBlockEntityAccessor) container)
                .minecraftProtocol$setCustomName(
                        requested == null ? null : Component.literal(requested));
        container.setChanged();
        BlockState state = level.getBlockState(position);
        level.sendBlockUpdated(position, state, state, 3);
        JsonObject after = blockEntitySnapshot(level, position, true);
        return mutationResult(
                request,
                "block_entity",
                "typed_block_entity_custom_name",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.blockEntitySerializedRevision(level, after));
    }

    private static ItemStack debugReplacementItem(JsonObject request) {
        DebugItemRequest data = DebugItemRequest.parse(request);
        if (data.count() == 0) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(data.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id))
            throw new ProtocolState.ProtocolException(
                    "UNKNOWN_ITEM", 400, "Unknown item: " + data.itemId());
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), data.count());
        if (stack.isEmpty() || data.count() > stack.getMaxStackSize())
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEBUG_ITEM", 400, "count exceeds the native item stack size");
        if (data.customName() != null) stack.setHoverName(Component.literal(data.customName()));
        if (data.damage() != null) {
            if (!stack.isDamageableItem() || data.damage() > stack.getMaxDamage())
                throw new ProtocolState.ProtocolException(
                        "INVALID_DEBUG_ITEM", 400, "damage is outside the native item domain");
            stack.setDamageValue(data.damage());
        }
        return stack;
    }

    private static void verifyExpectedItem(JsonObject request, ItemStack current) {
        String id = BuiltInRegistries.ITEM.getKey(current.getItem()).toString();
        if (request.has("expectedItemId")
                && !request.get("expectedItemId").getAsString().equals(id))
            throw valueMismatch("expectedItemId", id);
        if (request.has("expectedCount")
                && DebugItemRequest.integer(request, "expectedCount") != current.getCount())
            throw valueMismatch("expectedCount", current.getCount());
    }

    private JsonObject debugPlayerInventorySlot(ServerPlayer player, JsonObject request) {
        JsonObject before = player(player), beforeRef = this.playerRevision(before);
        verifyExpected(request, beforeRef);
        int slot = DebugItemRequest.integer(request, "slot");
        if (slot < 0 || slot >= player.getInventory().getContainerSize())
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED",
                    400,
                    "slot is outside the actual player inventory");
        ItemStack current = player.getInventory().getItem(slot);
        verifyExpectedItem(request, current);
        ItemStack replacement = debugReplacementItem(request);
        if (ItemStack.matches(current, replacement)) throw noStateChange();
        player.getInventory().setItem(slot, replacement);
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        JsonObject after = player(player);
        return mutationResult(
                request,
                "player",
                "native_player_inventory_slot_set",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.playerRevision(after));
    }

    private JsonObject debugPlayerEquipment(ServerPlayer player, JsonObject request) {
        JsonObject before = player(player), beforeRef = this.playerRevision(before);
        verifyExpected(request, beforeRef);
        EquipmentSlot slot = debugEquipmentSlot(request, true);
        ItemStack current = player.getItemBySlot(slot);
        verifyExpectedItem(request, current);
        ItemStack replacement = debugReplacementItem(request);
        if (ItemStack.matches(current, replacement)) throw noStateChange();
        player.setItemSlot(slot, replacement);
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        JsonObject after = player(player);
        return mutationResult(
                request,
                "player",
                "native_player_equipment_set",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.playerRevision(after));
    }

    private JsonObject debugBlockEntitySlot(ServerPlayer player, JsonObject request) {
        ServerLevel level = player.serverLevel();
        BlockPos position = requiredPosition(request);
        JsonObject before = blockEntitySnapshot(level, position, true);
        if (!before.get("available").getAsBoolean()) throw targetNotLoaded("block_entity");
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof Container container))
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE",
                    409,
                    "slot.set requires a loaded native Container Block Entity");
        JsonObject beforeRef = this.blockEntitySerializedRevision(level, before);
        verifyExpected(request, beforeRef);
        if (request.has("expectedBlockEntityType")
                && !request.get("expectedBlockEntityType")
                        .getAsString()
                        .equals(before.get("type").getAsString()))
            throw valueMismatch("expectedBlockEntityType", before.get("type").getAsString());
        int slot = DebugItemRequest.integer(request, "slot");
        if (slot < 0 || slot >= container.getContainerSize())
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED",
                    400,
                    "slot is outside the actual native Container");
        DebugItemRequest.requireResolvedContainer(before.getAsJsonObject("serializedState"));
        ItemStack current = container.getItem(slot);
        verifyExpectedItem(request, current);
        ItemStack replacement = debugReplacementItem(request);
        if (ItemStack.matches(current, replacement)) throw noStateChange();
        container.setItem(slot, replacement);
        blockEntity.setChanged();
        player.containerMenu.broadcastChanges();
        BlockState state = level.getBlockState(position);
        level.sendBlockUpdated(position, state, state, 3);
        JsonObject after = blockEntitySnapshot(level, position, true);
        return mutationResult(
                request,
                "block_entity",
                "native_block_entity_container_slot_set",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.blockEntitySerializedRevision(level, after));
    }

    private JsonObject debugMenuSlot(ServerPlayer player, JsonObject request) {
        JsonObject before = menu(player);
        JsonObject beforeRef = this.menuRevision(before);
        verifyExpected(request, beforeRef);
        int slotIndex = request.has("slot") ? request.get("slot").getAsInt() : -1;
        if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "slot is outside the active Menu");
        }
        if (request.has("expectedMenuId")
                && request.get("expectedMenuId").getAsInt() != player.containerMenu.containerId) {
            throw valueMismatch("expectedMenuId", player.containerMenu.containerId);
        }
        ItemStack current = player.containerMenu.slots.get(slotIndex).getItem();
        String currentId = BuiltInRegistries.ITEM.getKey(current.getItem()).toString();
        if (request.has("expectedItemId")
                && !request.get("expectedItemId").getAsString().equals(currentId)) {
            throw valueMismatch("expectedItemId", currentId);
        }
        if (request.has("expectedCount")
                && request.get("expectedCount").getAsInt() != current.getCount()) {
            throw valueMismatch("expectedCount", current.getCount());
        }
        int count = request.has("count") ? request.get("count").getAsInt() : 0;
        if (count < 0 || count > 64)
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "count must be 0 to 64");
        ItemStack replacement = ItemStack.EMPTY;
        if (count > 0) {
            String itemId = requiredDebugString(request, "itemId");
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                throw new ProtocolState.ProtocolException(
                        "UNKNOWN_ITEM", 400, "Unknown item: " + itemId);
            }
            replacement = new ItemStack(BuiltInRegistries.ITEM.get(id), count);
        }
        if (ItemStack.matches(current, replacement)) throw noStateChange();
        player.containerMenu.slots.get(slotIndex).set(replacement);
        player.containerMenu.broadcastChanges();
        JsonObject after = menu(player);
        return mutationResult(
                request,
                "menu",
                "server_menu_slot_set",
                "normal_network_sync",
                before,
                after,
                beforeRef,
                this.menuRevision(after));
    }

    private JsonObject mutationResult(
            JsonObject request,
            String domain,
            String mechanism,
            String synchronization,
            JsonObject before,
            JsonObject after,
            JsonObject beforeRef,
            JsonObject afterRef) {
        if (beforeRef.get("revision").getAsLong() == afterRef.get("revision").getAsLong()) {
            throw new ProtocolState.ProtocolException(
                    "DEBUG_REVISION_DID_NOT_ADVANCE",
                    409,
                    "Typed Debug mutation did not advance its resource revision");
        }
        return mutationReceipt(
                request, domain, mechanism, synchronization, before, after, beforeRef, afterRef);
    }

    private JsonObject mutationReceipt(
            JsonObject request,
            String domain,
            String mechanism,
            String synchronization,
            JsonObject before,
            JsonObject after,
            JsonObject beforeRef,
            JsonObject afterRef) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "debug.mutation.result");
        json.addProperty("schemaVersion", "phase9c-debug-v0");
        json.addProperty(
                "debugOperationId",
                request.has("debugOperationId")
                        ? request.get("debugOperationId").getAsString()
                        : UUID.randomUUID().toString());
        json.addProperty("auditReference", json.get("debugOperationId").getAsString());
        json.addProperty("operation", request.get("operation").getAsString());
        json.addProperty("namespace", domain);
        json.addProperty("authority", "runtime_internal");
        json.addProperty("mechanism", mechanism);
        json.addProperty("invariants", "partial_invariants");
        json.addProperty("synchronization", synchronization);
        json.addProperty("evidence", "diagnostic");
        json.addProperty("gameplayEvidence", false);
        json.addProperty("evidenceContaminated", true);
        json.addProperty("storageAccessed", false);
        json.addProperty("worldFingerprint", request.get("worldFingerprint").getAsString());
        json.add("before", before.deepCopy());
        json.add("after", after.deepCopy());
        json.add("beforeResourceVersion", ResourceVersionVerifier.token(beforeRef));
        json.add("afterResourceVersion", ResourceVersionVerifier.token(afterRef));
        JsonObject preconditions = new JsonObject();
        preconditions.addProperty("resourceVersion", "passed");
        preconditions.addProperty("value", "passed");
        preconditions.addProperty("ownerThread", "server_thread");
        json.add("preconditions", preconditions);
        json.addProperty("resync", synchronization);
        json.addProperty("cleanup", "caller_must_restore_test_state");
        return json;
    }

    private JsonObject playerRevision(JsonObject state) {
        String key = state.get("uuid").getAsString() + "@server_authoritative";
        return this.revisions.revision(
                "player", key, state.get("lifecycleId").getAsString(), canonicalDebugPlayer(state));
    }

    private static JsonObject canonicalDebugPlayer(JsonObject state) {
        JsonObject canonical = state.deepCopy();
        canonical.remove("extendedStatus");
        canonical.remove("menuClass");
        return canonical;
    }

    private static JsonObject canonicalDebugEntity(JsonObject state) {
        JsonObject canonical = state.deepCopy();
        canonical.remove("extensions");
        canonical.remove("nonDefaultTrackedValues");
        return canonical;
    }

    private JsonObject menuRevision(JsonObject state) {
        String key =
                state.get("ownerUuid").getAsString() + "@menu:" + state.get("menuId").getAsString();
        return this.revisions.revision("menu", key, state.get("lifecycleId").getAsString(), state);
    }

    private JsonObject blockRevision(ServerLevel level, JsonObject state) {
        String key = level.dimension().location() + "@" + state.get("key").getAsString();
        return this.revisions.revision("block", key, state.get("lifecycleId").getAsString(), state);
    }

    private JsonObject blockEntitySerializedRevision(ServerLevel level, JsonObject state) {
        String key = level.dimension().location() + "@" + state.get("key").getAsString();
        return this.revisions.revision(
                "block_entity_serialized",
                key,
                state.get("lifecycleId").getAsString(),
                state.get("serializedState"));
    }

    private static void verifyExpected(JsonObject request, JsonObject current) {
        ResourceVersionVerifier.verifyAuthorized(
                request.getAsJsonObject("expectedResourceVersion"), current);
    }

    private JsonObject blockSnapshot(ServerLevel level, BlockPos position) {
        JsonObject value = new JsonObject();
        value.addProperty("key", key(position));
        value.addProperty(
                "lifecycleId", "block:" + level.dimension().toString() + "@" + key(position));
        value.addProperty("x", position.getX());
        value.addProperty("y", position.getY());
        value.addProperty("z", position.getZ());
        LevelChunk chunk =
                level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        value.addProperty("available", chunk != null);
        value.addProperty("loadRequested", false);
        if (chunk != null) {
            BlockState state = chunk.getBlockState(position);
            value.addProperty(
                    "blockId", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            value.add("properties", debugBlockProperties(state));
        }
        return value;
    }

    private JsonObject blockEntitySnapshot(
            ServerLevel level, BlockPos position, boolean includeSerialized) {
        JsonObject value = new JsonObject();
        value.addProperty("key", key(position));
        LevelChunk chunk =
                level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        BlockEntity blockEntity = chunk == null ? null : chunk.getBlockEntity(position);
        value.addProperty("available", blockEntity != null);
        value.addProperty("loadRequested", false);
        if (blockEntity == null) return value;
        value.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId("block_entity", key(position), blockEntity));
        value.addProperty(
                "type",
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
        value.addProperty("loaded", true);
        value.addProperty(
                "readEffects", includeSerialized ? "serialization_hooks_invoked" : "none");
        if (includeSerialized) {
            CompoundTag tag = blockEntity.saveWithFullMetadata();
            JsonElement structured = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
            int bytes = GSON.toJson(structured).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 16_384)
                throw new ProtocolState.ProtocolException(
                        "DEBUG_VALUE_BUDGET_EXCEEDED",
                        413,
                        "Block Entity serialized state exceeds 16 KiB");
            value.addProperty("serializedBytes", bytes);
            value.add("serializedState", structured);
        }
        return value;
    }

    private static Entity loadedEntity(ServerPlayer player, String uuid) {
        return loadedEntity(player.serverLevel(), uuid);
    }

    private static Entity loadedEntity(ServerLevel level, String uuid) {
        try {
            Entity entity = level.getEntity(UUID.fromString(uuid));
            if (entity != null && !entity.isRemoved()) return entity;
        } catch (IllegalArgumentException exception) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_ENTITY_UUID", 400, "Invalid entity UUID");
        }
        throw new ProtocolState.ProtocolException(
                "TARGET_NOT_LOADED", 409, "Entity is not loaded in the current dimension");
    }

    private static BlockPos requiredPosition(JsonObject request) {
        if (!request.has("x") || !request.has("y") || !request.has("z")) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "x, y and z are required");
        }
        return new BlockPos(
                request.get("x").getAsInt(),
                request.get("y").getAsInt(),
                request.get("z").getAsInt());
    }

    private static String requiredDebugString(JsonObject request, String name) {
        if (!request.has(name)
                || !request.get(name).isJsonPrimitive()
                || request.get(name).getAsString().isBlank()) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "Missing " + name);
        }
        return request.get(name).getAsString();
    }

    private static boolean requiredDebugBoolean(JsonObject request, String name) {
        if (!request.has(name)
                || !request.get(name).isJsonPrimitive()
                || !request.getAsJsonPrimitive(name).isBoolean()) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "Missing boolean " + name);
        }
        return request.get(name).getAsBoolean();
    }

    private static double requiredFiniteDouble(
            JsonObject request, String name, double minimum, double maximum) {
        if (!request.has(name)
                || !request.get(name).isJsonPrimitive()
                || !request.getAsJsonPrimitive(name).isNumber()) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "Missing numeric " + name);
        }
        double value = request.get(name).getAsDouble();
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED",
                    400,
                    name + " must be finite and between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static ProtocolState.ProtocolException valueMismatch(String field, Object actual) {
        return new ProtocolState.ProtocolException(
                "VALUE_PRECONDITION_FAILED",
                409,
                field + " does not match current value " + String.valueOf(actual));
    }

    private static ProtocolState.ProtocolException targetNotLoaded(String target) {
        return new ProtocolState.ProtocolException(
                "TARGET_NOT_LOADED",
                409,
                target + " target is not loaded; no chunk load was requested");
    }

    private static ProtocolState.ProtocolException noStateChange() {
        return new ProtocolState.ProtocolException(
                "DEBUG_NO_STATE_CHANGE", 409, "Requested value already matches the current state");
    }

    StorageRequest storageRequest(MinecraftServer server, ServerPlayer player, JsonObject request) {
        String domain = requiredString(request, "domain");
        if (!List.of("world", "player", "chunk").contains(domain))
            throw new ProtocolState.ProtocolException(
                    "INVALID_STORAGE_DOMAIN", 400, "domain must be world, player, or chunk");
        int chunkX =
                request.has("chunkX") ? request.get("chunkX").getAsInt() : player.chunkPosition().x;
        int chunkZ =
                request.has("chunkZ") ? request.get("chunkZ").getAsInt() : player.chunkPosition().z;
        ServerLevel level = player.serverLevel();
        boolean loaded = level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        return new StorageRequest(
                domain,
                server.getWorldPath(LevelResource.ROOT),
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR),
                server.getWorldData().getLevelName(),
                level.dimension(),
                player.getUUID().toString(),
                new ChunkPos(chunkX, chunkZ),
                loaded,
                true,
                server.isCurrentlySaving(),
                this.revisions.sessionEpoch(),
                this.storageAdapter.lifecycleEpoch(),
                sha256(
                        server.getWorldPath(LevelResource.ROOT).toAbsolutePath()
                                + "|"
                                + level.dimension().location()));
    }

    void rememberStorageContext(StorageRequest request) {
        this.storageAdapter.rememberContext(request);
    }

    CompletableFuture<JsonObject> readSavedStorage(JsonObject query) {
        return this.storageAdapter.readSaved(query);
    }

    CompletableFuture<JsonObject> readStorage(StorageRequest request) {
        this.storageAdapter.rememberContext(request);
        return this.storageAdapter.read(request);
    }

    private JsonObject checkedResponse(JsonObject response, JsonObject request) {
        JsonObject budgets =
                request.has("budgets") && request.get("budgets").isJsonObject()
                        ? request.getAsJsonObject("budgets")
                        : new JsonObject();
        int maximum = bounded(integer(budgets, "maxResponseBytes", 524_288), 16_384, 524_288);
        int bytes = encodedBytes(response);
        response.addProperty("responseBytes", bytes);
        response.addProperty("maxResponseBytes", maximum);
        if (bytes > maximum)
            throw new ProtocolState.ProtocolException(
                    "QUERY_BUDGET_EXCEEDED",
                    413,
                    "Deep Observation response exceeds maxResponseBytes");
        return response;
    }

    private JsonObject observationMetadata(JsonObject client, JsonObject server) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty(
                "perspective",
                client != null && server != null
                        ? "both"
                        : client != null ? "client_known" : "server_authoritative");
        metadata.addProperty("acquisition", "owner_thread_snapshot");
        metadata.addProperty("completeness", "projected");
        metadata.addProperty(
                "readEffects", server == null ? "none" : server.get("readEffects").getAsString());
        metadata.addProperty(
                "consistency",
                client != null && server != null
                        ? "coordinated_best_effort"
                        : "owner_thread_snapshot");
        metadata.addProperty("capturedAt", System.currentTimeMillis());
        metadata.addProperty("snapshotId", UUID.randomUUID().toString());
        metadata.addProperty("sessionEpoch", this.revisions.sessionEpoch());
        metadata.addProperty(
                "clientTick",
                client != null && client.has("clientTick")
                        ? client.get("clientTick").getAsLong()
                        : -1L);
        metadata.addProperty(
                "serverTick",
                server != null && server.has("serverTick")
                        ? server.get("serverTick").getAsLong()
                        : -1L);
        metadata.addProperty(
                "alignmentQuality",
                client != null && server != null
                        ? "best_effort_not_same_tick"
                        : "single_perspective");
        return metadata;
    }

    private JsonArray resourceRevisions(JsonObject client, JsonObject server) {
        JsonArray refs = new JsonArray();
        if (client != null) {
            String uuid = client.has("uuid") ? client.get("uuid").getAsString() : "unavailable";
            String key = uuid + "@client_known";
            refs.add(
                    this.revisions.revision(
                            "player",
                            key,
                            "player:" + key + "@runtime_session",
                            canonicalClientPlayer(client)));
        }
        if (server == null) return refs;
        if (server.has("_canonicalResourceRevisionRefs")) {
            server.getAsJsonArray("_canonicalResourceRevisionRefs")
                    .forEach(element -> refs.add(element.deepCopy()));
            return refs;
        }
        canonicalResourceRevisions(server).forEach(element -> refs.add(element.deepCopy()));
        return refs;
    }

    private JsonArray canonicalResourceRevisions(JsonObject server) {
        JsonArray refs = new JsonArray();
        String dimension =
                server.has("dimension") ? server.get("dimension").getAsString() : "unknown";
        if (server.has("player")) {
            JsonObject player = server.getAsJsonObject("player");
            if (player.has("relationships"))
                refs.add(relationshipRevision(player.getAsJsonObject("relationships")));
            String key = player.get("uuid").getAsString() + "@server_authoritative";
            refs.add(
                    this.revisions.revision(
                            "player", key, player.get("lifecycleId").getAsString(), player));
        }
        if (server.has("menu")) {
            JsonObject menu = server.getAsJsonObject("menu");
            String key =
                    menu.get("ownerUuid").getAsString()
                            + "@menu:"
                            + menu.get("menuId").getAsString();
            refs.add(
                    this.revisions.revision(
                            "menu", key, menu.get("lifecycleId").getAsString(), menu));
        }
        if (server.has("entities"))
            for (JsonElement element : server.getAsJsonArray("entities")) {
                JsonObject entity = element.getAsJsonObject();
                if (entity.has("relationships"))
                    refs.add(relationshipRevision(entity.getAsJsonObject("relationships")));
                refs.add(
                        this.revisions.revision(
                                "entity",
                                entity.get("uuid").getAsString(),
                                entity.get("lifecycleId").getAsString(),
                                entity));
            }
        if (server.has("entityRegistry")
                && server.getAsJsonObject("entityRegistry").get("available").getAsBoolean())
            refs.add(entityRegistryRevision(server.getAsJsonObject("entityRegistry")));
        if (server.has("entityTargets"))
            for (JsonElement element : server.getAsJsonArray("entityTargets")) {
                JsonObject target = element.getAsJsonObject();
                if (target.get("available").getAsBoolean())
                    refs.add(entityRevision(target.getAsJsonObject("state")));
            }
        if (server.has("world") && server.getAsJsonObject("world").has("environment")) {
            JsonObject environment = server.getAsJsonObject("world").getAsJsonObject("environment");
            for (JsonElement clock : environment.getAsJsonArray("clocks"))
                refs.add(clockRevision(clock.getAsJsonObject()));
            refs.add(weatherRevision(environment.getAsJsonObject("weather")));
            for (JsonElement rule : environment.getAsJsonArray("rules"))
                refs.add(gameRuleRevision(rule.getAsJsonObject()));
        }
        if (server.has("blocks"))
            for (JsonElement element : server.getAsJsonArray("blocks")) {
                JsonObject block = element.getAsJsonObject();
                String key = dimension + "@" + block.get("key").getAsString();
                refs.add(
                        this.revisions.revision(
                                "block", key, block.get("lifecycleId").getAsString(), block));
            }
        if (server.has("chunks"))
            for (JsonElement element : server.getAsJsonArray("chunks")) {
                JsonObject chunk = element.getAsJsonObject();
                String key = dimension + "@" + chunk.get("key").getAsString();
                refs.add(
                        this.revisions.revision(
                                "chunk", key, chunk.get("lifecycleId").getAsString(), chunk));
            }
        if (server.has("blockEntities"))
            for (JsonElement element : server.getAsJsonArray("blockEntities")) {
                JsonObject blockEntity = element.getAsJsonObject();
                String key = dimension + "@" + blockEntity.get("key").getAsString();
                String lifecycleId = blockEntity.get("lifecycleId").getAsString();
                refs.add(
                        this.revisions.revision(
                                "block_entity",
                                key,
                                lifecycleId,
                                canonicalBlockEntityBase(blockEntity)));
                if (blockEntity.has("serializedState")) {
                    refs.add(
                            this.revisions.revision(
                                    "block_entity_serialized",
                                    key,
                                    lifecycleId,
                                    blockEntity.get("serializedState")));
                }
            }
        return refs;
    }

    private static JsonObject canonicalClientPlayer(JsonObject client) {
        JsonObject semantic = client.deepCopy();
        for (String field :
                List.of(
                        "type",
                        "target",
                        "clientTick",
                        "perspective",
                        "source",
                        "authority",
                        "dataSource",
                        "storageAccessed",
                        "stalePossible",
                        "requestId",
                        "protocolVersion")) {
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
        JsonObject player =
                server.has("player") ? server.getAsJsonObject("player") : new JsonObject();
        result.add("uuid", comparison(client.get("uuid"), player.get("uuid"), false));
        result.add(
                "dimension", comparison(client.get("dimension"), player.get("dimension"), false));
        result.add("health", comparison(client.get("health"), player.get("health"), true));
        result.add(
                "selectedSlot",
                comparison(client.get("selectedSlot"), player.get("selectedSlot"), true));
        JsonObject position =
                player.has("position") ? player.getAsJsonObject("position") : new JsonObject();
        result.add("x", comparison(client.get("x"), position.get("x"), true));
        result.add("y", comparison(client.get("y"), position.get("y"), true));
        result.add("z", comparison(client.get("z"), position.get("z"), true));
        result.add("yaw", comparison(client.get("yaw"), position.get("yaw"), true));
        result.add("pitch", comparison(client.get("pitch"), position.get("pitch"), true));
        result.add(
                "velocityX", comparison(client.get("velocityX"), position.get("velocityX"), true));
        result.add(
                "velocityY", comparison(client.get("velocityY"), position.get("velocityY"), true));
        result.add(
                "velocityZ", comparison(client.get("velocityZ"), position.get("velocityZ"), true));
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
        if (numeric
                && client.isJsonPrimitive()
                && server.isJsonPrimitive()
                && client.getAsJsonPrimitive().isNumber()
                && server.getAsJsonPrimitive().isNumber()) {
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
        return io.github.recrivenvi.minecraftprotocol.core.ProviderInspection.descriptor(
                descriptor);
    }

    private static JsonObject providerResult(
            AgentDataProviderV2.Descriptor descriptor, String status, String reason) {
        JsonObject json = providerDescriptor(descriptor);
        json.addProperty("status", status);
        if (!reason.isEmpty()) json.addProperty("reason", reason);
        return json;
    }

    private static JsonArray allDomains() {
        JsonArray domains = new JsonArray();
        for (String domain :
                List.of(
                        "player",
                        "entities",
                        "blocks",
                        "block_entities",
                        "chunks",
                        "world",
                        "menu")) domains.add(domain);
        return domains;
    }

    private static void applyProjection(JsonObject snapshot, JsonObject request) {
        if (!request.has("projection") || !request.get("projection").isJsonObject()) return;
        JsonObject projection = request.getAsJsonObject("projection");
        if (snapshot.has("player")
                && projection.has("playerFields")
                && projection.get("playerFields").isJsonArray()) {
            Set<String> fields = new HashSet<>();
            projection
                    .getAsJsonArray("playerFields")
                    .forEach(element -> fields.add(element.getAsString()));
            JsonObject player = snapshot.getAsJsonObject("player");
            Set<String> keep = new HashSet<>(List.of("uuid", "name"));
            if (fields.contains("transform")) keep.add("position");
            if (fields.contains("environment"))
                keep.addAll(List.of("pose", "onGround", "inWater", "fallFlying"));
            if (fields.contains("vitals"))
                keep.addAll(
                        List.of(
                                "health",
                                "maxHealth",
                                "absorption",
                                "food",
                                "saturation",
                                "air",
                                "maxAirSupply",
                                "experienceLevel",
                                "totalExperience",
                                "experienceProgress",
                                "experiencePoints"));
            if (fields.contains("authority"))
                keep.addAll(List.of("gameMode", "mayFly", "flying", "instabuild"));
            if (fields.contains("inventory"))
                keep.addAll(List.of("selectedSlot", "inventory", "carriedStack", "equipment"));
            if (fields.contains("attributes")) keep.add("attributes");
            if (fields.contains("effects")) keep.add("effects");
            if (fields.contains("relationships"))
                keep.addAll(List.of("vehicle", "passengers", "relationships"));
            if (fields.contains("menu")) keep.addAll(List.of("menuId", "menuRole"));
            if (fields.contains("dimension")) keep.add("dimension");
            if (fields.contains("respawn")) keep.add("respawn");
            new ArrayList<>(player.keySet())
                    .stream().filter(key -> !keep.contains(key)).forEach(player::remove);
        }
        if (snapshot.has("entities")
                && projection.has("entityFields")
                && projection.get("entityFields").isJsonArray()) {
            Set<String> fields = new HashSet<>();
            projection
                    .getAsJsonArray("entityFields")
                    .forEach(element -> fields.add(element.getAsString()));
            JsonArray projectedEntities = new JsonArray();
            snapshot.getAsJsonArray("entities").forEach(projectedEntities::add);
            if (snapshot.has("entityTargets"))
                for (JsonElement target : snapshot.getAsJsonArray("entityTargets"))
                    if (target.getAsJsonObject().get("available").getAsBoolean())
                        projectedEntities.add(target.getAsJsonObject().get("state"));
            for (JsonElement element : projectedEntities) {

                JsonObject entity = element.getAsJsonObject();
                Set<String> keep =
                        new HashSet<>(
                                List.of(
                                        "uuid",
                                        "runtimeId",
                                        "type",
                                        "lifecycleId",
                                        "alive",
                                        "removed",
                                        "removalReason"));
                if (fields.contains("transform"))
                    keep.addAll(
                            List.of(
                                    "x",
                                    "y",
                                    "z",
                                    "velocityX",
                                    "velocityY",
                                    "velocityZ",
                                    "yaw",
                                    "pitch",
                                    "pose"));
                if (fields.contains("dimension")) keep.add("dimension");
                if (fields.contains("living"))
                    keep.addAll(List.of("health", "maxHealth", "absorption"));
                if (fields.contains("equipment"))
                    keep.addAll(List.of("equipment", "nativeEquipmentSlots"));
                if (fields.contains("effects")) keep.addAll(List.of("effectCount", "effects"));
                if (fields.contains("attributes"))
                    keep.addAll(List.of("syncableAttributeCount", "attributes"));
                if (fields.contains("relationships"))
                    keep.addAll(List.of("vehicle", "passengers", "relationships"));
                if (fields.contains("common_state"))
                    keep.addAll(
                            List.of(
                                    "noGravity",
                                    "silent",
                                    "glowing",
                                    "air",
                                    "maxAirSupply",
                                    "noAi",
                                    "aiTargetUuid"));
                new ArrayList<>(entity.keySet())
                        .stream().filter(key -> !keep.contains(key)).forEach(entity::remove);
            }
        }
        snapshot.addProperty("projectionApplied", true);
    }

    private JsonObject captureSnapshot(
            MinecraftServer server, ServerPlayer player, JsonObject selector) {
        long captureStarted = System.nanoTime();
        ServerLevel level = player.serverLevel();
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
        json.addProperty("readEffects", "none");
        json.addProperty("chunkLoadRequested", false);
        json.addProperty("dataSource", "LIVE");
        json.addProperty("storageAccessed", false);
        json.addProperty("dimension", level.dimension().location().toString());
        json.add("selector", selector.deepCopy());
        Set<String> domains = new HashSet<>();
        selector.getAsJsonArray("domains").forEach(element -> domains.add(element.getAsString()));
        if (domains.contains("player")) json.add("player", player(player));
        if (domains.contains("menu")) json.add("menu", menu(player));
        if (domains.contains("entities")) {
            JsonArray entities =
                    selector.has("entityTargets")
                            ? new JsonArray()
                            : entities(
                                    level,
                                    player,
                                    selector.get("entityRadius").getAsDouble(),
                                    selector.get("entityLimit").getAsInt());
            json.add("entities", entities);
            JsonObject counts = new JsonObject();
            counts.addProperty("returnedCount", entities.size());
            counts.addProperty("availableCountKnown", false);
            counts.addProperty(
                    "truncated", entities.size() >= selector.get("entityLimit").getAsInt());
            counts.addProperty("limit", selector.get("entityLimit").getAsInt());
            json.add("entitiesResult", counts);
            if (selector.has("entityTargets")) {
                if (selector.getAsJsonArray("entityTargets").size()
                        > selector.get("entityLimit").getAsInt())
                    throw new ProtocolState.ProtocolException(
                            "QUERY_BUDGET_EXCEEDED",
                            413,
                            "Explicit entity identities exceed maxEntities; no silent truncation");
                json.add("entityTargets", selectedEntities(server, selector.get("entityTargets")));
            }
            if (selector.has("entityChunk"))
                json.add(
                        "entityRegistry",
                        entityRegistry(
                                level,
                                DebugEntityLifecycleRequest.chunk(
                                        selector.getAsJsonObject("entityChunk"))));
        }
        JsonArray chunks =
                domains.contains("chunks") || domains.contains("block_entities")
                        ? chunks(level, player, selector.get("radiusChunks").getAsInt())
                        : new JsonArray();
        if (domains.contains("chunks")) json.add("chunks", chunks);
        if (domains.contains("block_entities")) {
            JsonObject page = blockEntities(level, player, chunks, selector);
            json.add("blockEntities", page.get("entries"));
            json.add("blockEntitiesResult", page.get("page"));
            if (serializedBlockEntities && !page.getAsJsonArray("entries").isEmpty())
                json.addProperty("readEffects", "serialization_hooks_invoked");
        }
        if (domains.contains("blocks")) json.add("blocks", blocks(level, player, selector));
        if (domains.contains("world")) json.add("world", world(server, level));
        JsonObject ticketFacts = new JsonObject();
        ticketFacts.addProperty("status", "REQUIRES_NEW_HOOK");
        ticketFacts.addProperty("model", "TicketStorage + normalized loading reason candidate");
        ticketFacts.addProperty("readEffects", "not_observed");
        json.add("tickets", ticketFacts);
        JsonObject provider = new JsonObject();
        provider.addProperty("providerId", MOD_ID + ":echo");
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
        json.add("relationships", relationship(player));
        json.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId("player", player.getUUID().toString(), player));
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
        json.addProperty("saturation", player.getFoodData().getSaturationLevel());
        json.addProperty("air", player.getAirSupply());
        json.addProperty("maxAirSupply", player.getMaxAirSupply());
        json.addProperty("experienceLevel", player.experienceLevel);
        json.addProperty("totalExperience", player.totalExperience);
        json.addProperty("experienceProgress", player.experienceProgress);
        json.addProperty(
                "experiencePoints",
                Math.round(player.experienceProgress * player.getXpNeededForNextLevel()));
        json.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
        json.addProperty("mayFly", player.getAbilities().mayfly);
        json.addProperty("flying", player.getAbilities().flying);
        json.addProperty("instabuild", player.getAbilities().instabuild);
        json.addProperty("selectedSlot", player.getInventory().selected);
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            JsonObject item = item(player.getInventory().getItem(slot));
            item.addProperty("slot", slot);
            inventory.add(item);
        }
        json.add("inventory", inventory);
        json.add("carriedStack", item(player.containerMenu.getCarried()));
        JsonObject equipment = new JsonObject();
        for (EquipmentSlot slot : EquipmentSlot.values())
            equipment.add(slot.getName(), item(player.getItemBySlot(slot)));
        json.add("equipment", equipment);
        json.add("attributes", nativeAttributes(player));
        json.add("effects", nativeEffects(player));
        json.addProperty(
                "vehicle",
                player.getVehicle() == null ? "" : player.getVehicle().getUUID().toString());
        JsonArray passengers = new JsonArray();
        for (Entity passenger : player.getPassengers())
            passengers.add(passenger.getUUID().toString());
        json.add("passengers", passengers);
        json.addProperty("menuClass", player.containerMenu.getClass().getName());
        json.addProperty("menuId", player.containerMenu.containerId);
        json.addProperty(
                "menuRole",
                player.containerMenu.containerId == 0 ? "player_inventory" : "container");
        json.addProperty("dimension", player.level().dimension().location().toString());
        json.addProperty(
                "respawn",
                player.getRespawnPosition() == null
                        ? "unset"
                        : player.getRespawnDimension().location()
                                + "@"
                                + player.getRespawnPosition());
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
        json.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId(
                        "menu",
                        player.getUUID() + ":" + player.containerMenu.containerId,
                        player.containerMenu));
        json.addProperty(
                "role", player.containerMenu.containerId == 0 ? "player_inventory" : "container");
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
        List<Entity> entities =
                level.getEntities(player, player.getBoundingBox().inflate(radius), ignored -> true);
        entities.stream()
                .sorted(Comparator.comparing(entity -> entity.getUUID().toString()))
                .limit(limit)
                .forEach(entity -> json.add(entity(entity)));
        return json;
    }

    private JsonObject entity(Entity entity) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", entity.level().dimension().location().toString());
        json.addProperty("uuid", entity.getUUID().toString());
        json.add("relationships", relationship(entity));
        json.addProperty(
                "lifecycleId",
                this.lifecycles.lifecycleId("entity", entity.getUUID().toString(), entity));
        json.addProperty("runtimeId", entity.getId());
        json.addProperty("alive", entity.isAlive());
        json.addProperty("removed", entity.isRemoved());
        json.addProperty(
                "removalReason",
                entity.getRemovalReason() == null ? "" : entity.getRemovalReason().name());
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
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            json.addProperty("noAi", mob.isNoAi());
            json.addProperty(
                    "aiTargetUuid",
                    mob.getTarget() == null ? "" : mob.getTarget().getUUID().toString());
        }
        json.addProperty("silent", entity.isSilent());
        json.addProperty("glowing", entity.hasGlowingTag());
        json.addProperty("air", entity.getAirSupply());
        json.addProperty("maxAirSupply", entity.getMaxAirSupply());
        json.addProperty(
                "vehicle",
                entity.getVehicle() == null ? "" : entity.getVehicle().getUUID().toString());
        JsonArray passengers = new JsonArray();
        for (Entity passenger : entity.getPassengers())
            passengers.add(passenger.getUUID().toString());
        json.add("passengers", passengers);
        var tracked = entity.getEntityData().getNonDefaultValues();
        json.addProperty("nonDefaultTrackedValues", tracked == null ? 0 : tracked.size());
        if (entity instanceof LivingEntity living) {
            json.addProperty("health", living.getHealth());
            json.addProperty("maxHealth", living.getMaxHealth());
            json.addProperty("absorption", living.getAbsorptionAmount());
            JsonObject equipment = new JsonObject();
            for (EquipmentSlot slot : EquipmentSlot.values())
                equipment.add(slot.getName(), item(living.getItemBySlot(slot)));
            json.add("equipment", equipment);
            JsonArray nativeSlots = new JsonArray();
            for (EquipmentSlot slot : EquipmentSlot.values()) nativeSlots.add(slot.getName());
            json.add("nativeEquipmentSlots", nativeSlots);
            json.add("attributes", nativeAttributes(living));
            json.addProperty("effectCount", living.getActiveEffects().size());
            json.add("effects", nativeEffects(living));
            json.addProperty(
                    "syncableAttributeCount",
                    living.getAttributes().getSyncableAttributes().size());
        }
        JsonObject extensions = new JsonObject();
        extensions.addProperty(
                "dataComponents", "TARGET_SPECIFIC_no_stable_entity_component_projection");
        extensions.addProperty(
                "attachments", "TARGET_SPECIFIC_read_skipped_to_avoid_lazy_initialization");
        json.add("extensions", extensions);
        return json;
    }

    private JsonArray chunks(ServerLevel level, ServerPlayer player, int radius) {
        JsonArray json = new JsonArray();
        int centerX = player.chunkPosition().x;
        int centerZ = player.chunkPosition().z;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                JsonObject value = new JsonObject();
                value.addProperty("key", x + "," + z);
                value.addProperty(
                        "lifecycleId", this.lifecycles.lifecycleId("chunk", x + "," + z, chunk));
                value.addProperty("chunkX", x);
                value.addProperty("chunkZ", z);
                value.addProperty("loaded", chunk != null);
                value.addProperty("loadRequested", false);
                if (chunk != null) {
                    int nonEmpty = 0;
                    for (LevelChunkSection section : chunk.getSections())
                        if (!section.hasOnlyAir()) nonEmpty++;
                    value.addProperty("sectionCount", chunk.getSections().length);
                    value.addProperty("nonEmptySections", nonEmpty);
                    value.addProperty("blockEntityCount", chunk.getBlockEntities().size());
                    value.addProperty("status", chunk.getFullStatus().toString());
                    value.add("loadingSummary", loadingSummary(level, x, z, chunk));
                    scheduledTicks(value, "scheduledBlockTicks", level.getBlockTicks(), x, z, true);
                    scheduledTicks(
                            value, "scheduledFluidTicks", level.getFluidTicks(), x, z, false);
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
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> ticketMap =
                ((DistanceManagerAccessor)
                                (Object) level.getChunkSource().chunkMap.getDistanceManager())
                        .minecraftProtocol$getTickets();
        this.ticketHookVerified.set(true);
        SortedArraySet<Ticket<?>> tickets = ticketMap.get(key);
        JsonArray reasons = new JsonArray();
        JsonArray details = new JsonArray();
        List<JsonObject> normalizedDetails = new ArrayList<>();
        Set<String> normalized = new HashSet<>();
        int loadingLevel = 34;
        if (tickets != null)
            for (Ticket<?> ticket : tickets) {
                String rawType = String.valueOf(ticket.getType());
                normalized.add(normalizedTicketReason(rawType));
                loadingLevel = Math.min(loadingLevel, ticket.getTicketLevel());
                JsonObject detail = new JsonObject();
                detail.addProperty("type", rawType);
                detail.addProperty("level", ticket.getTicketLevel());
                normalizedDetails.add(detail);
            }
        normalizedDetails.sort(
                Comparator.comparing((JsonObject value) -> value.get("type").getAsString())
                        .thenComparingInt(value -> value.get("level").getAsInt()));
        normalizedDetails.forEach(details::add);
        normalized.stream().sorted().forEach(reasons::add);
        json.addProperty("loaded", chunk != null);
        json.addProperty(
                "fullStatus", chunk == null ? "UNLOADED" : chunk.getFullStatus().toString());
        json.addProperty("loadingLevel", loadingLevel);
        json.addProperty(
                "simulationActive",
                chunk != null
                        && chunk.getFullStatus()
                                .isOrAfter(
                                        net.minecraft.server.level.FullChunkStatus.BLOCK_TICKING));
        json.addProperty("sourceCount", tickets == null ? 0 : tickets.size());
        json.addProperty("holderState", chunk == null ? "absent" : "level_chunk_present");
        json.addProperty("ticketDetailAvailable", true);
        json.add("reasons", reasons);
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("schema", "legacy-distance-manager-ticket-v0");
        diagnostic.addProperty("stability", "target_specific_diagnostic_only");
        diagnostic.add("tickets", details);
        json.add("targetDiagnostic", diagnostic);
        return json;
    }

    private void scheduledTicks(
            JsonObject chunk, String field, Object ticks, int chunkX, int chunkZ, boolean block) {
        JsonArray json = new JsonArray();
        Long2ObjectMap<LevelChunkTicks<?>> containers =
                ((LevelTicksAccessor) (Object) ticks).minecraftProtocol$getAllContainers();
        this.scheduledTickHookVerified.set(true);
        LevelChunkTicks<?> container = containers.get(ChunkPos.asLong(chunkX, chunkZ));
        long queuedCount = container == null ? 0 : container.getAll().count();
        if (container != null)
            container
                    .getAll()
                    .limit(ScheduledTickRead.MAX_PER_CHUNK)
                    .forEach(
                            raw -> {
                                ScheduledTick<?> tick = (ScheduledTick<?>) raw;
                                JsonObject value = new JsonObject();
                                value.addProperty("x", tick.pos().getX());
                                value.addProperty("y", tick.pos().getY());
                                value.addProperty("z", tick.pos().getZ());
                                value.addProperty(
                                        "type",
                                        block && tick.type() instanceof Block blockType
                                                ? BuiltInRegistries.BLOCK
                                                        .getKey(blockType)
                                                        .toString()
                                                : !block && tick.type() instanceof Fluid fluidType
                                                        ? BuiltInRegistries.FLUID
                                                                .getKey(fluidType)
                                                                .toString()
                                                        : String.valueOf(tick.type()));
                                value.addProperty("triggerTick", tick.triggerTick());
                                value.addProperty("priority", tick.priority().toString());
                                value.addProperty("subTickOrder", tick.subTickOrder());
                                value.addProperty("chunkX", chunkX);
                                value.addProperty("chunkZ", chunkZ);
                                json.add(value);
                            });
        chunk.add(field, json);
        chunk.add(
                field + "Result",
                ScheduledTickRead.metadata(
                        container == null ? 0 : container.count(), queuedCount, json.size()));
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

    private JsonObject blockEntities(
            ServerLevel level, ServerPlayer player, JsonArray chunks, JsonObject selector) {
        JsonArray json = new JsonArray();
        int serializedTotal = 0;
        int limit = selector.get("blockEntityLimit").getAsInt();
        boolean includeSerialized = selector.get("includeSerializedState").getAsBoolean();
        int perEntityBudget = selector.get("maxSerializedBytesPerBlockEntity").getAsInt();
        int totalBudget = selector.get("maxTotalSerializedBlockEntityBytes").getAsInt();
        List<BlockPos> positions =
                selectedBlockPositions(selector.getAsJsonArray("selectedBlocks"));
        Map<BlockPos, BlockEntity> candidates = new LinkedHashMap<>();
        if (!positions.isEmpty()) {
            for (BlockPos position : positions) {
                LevelChunk chunk =
                        level.getChunkSource()
                                .getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                if (chunk == null) continue;
                BlockEntity blockEntity = chunk.getBlockEntities().get(position);
                if (blockEntity == null) continue;
                candidates.put(position, blockEntity);
            }
        } else {
            for (JsonElement element : chunks) {
                JsonObject chunkValue = element.getAsJsonObject();
                if (!chunkValue.get("loaded").getAsBoolean()) continue;
                LevelChunk chunk =
                        level.getChunkSource()
                                .getChunkNow(
                                        chunkValue.get("chunkX").getAsInt(),
                                        chunkValue.get("chunkZ").getAsInt());
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    candidates.put(entry.getKey(), entry.getValue());
                    if (candidates.size() > DeepBlockEntityPages.MAX_CANDIDATES)
                        throw new ProtocolState.ProtocolException(
                                "DEEP_PAGE_SCAN_LIMIT_EXCEEDED",
                                413,
                                "Loaded block entity candidate bound exceeded; narrow selector.chunkRadius");
                }
            }
        }
        Map<String, Map.Entry<BlockPos, BlockEntity>> keyed = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockEntity> entry : candidates.entrySet())
            keyed.put(key(entry.getKey()), entry);
        JsonObject binding = selector.deepCopy();
        binding.remove("blockEntityCursor");
        String context = player.chunkPosition() + "/" + binding;
        String cursor =
                selector.has("blockEntityCursor")
                        ? selector.get("blockEntityCursor").getAsString()
                        : null;
        DeepBlockEntityPages.Page page =
                this.blockEntityPages.select(
                        level, player, context, cursor, new ArrayList<>(keyed.keySet()), limit);
        for (String selected : page.keys()) {
            Map.Entry<BlockPos, BlockEntity> entry = keyed.get(selected);
            BlockEntity blockEntity = entry.getValue();
            JsonObject value = new JsonObject();
            value.addProperty("key", key(entry.getKey()));
            value.addProperty(
                    "lifecycleId",
                    this.lifecycles.lifecycleId("block_entity", key(entry.getKey()), blockEntity));
            value.addProperty(
                    "type",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
            value.addProperty("loaded", true);
            value.addProperty(
                    "readEffects", includeSerialized ? "serialization_hooks_invoked" : "none");
            if (includeSerialized) {
                CompoundTag tag = blockEntity.saveWithFullMetadata();
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
        JsonObject result = new JsonObject();
        result.add("entries", json);
        result.add("page", page.metadata());
        return result;
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
            BlockPos pos =
                    new BlockPos(
                            position.get("x").getAsInt(),
                            position.get("y").getAsInt(),
                            position.get("z").getAsInt());
            JsonObject value = new JsonObject();
            value.addProperty("key", key(pos));
            value.addProperty(
                    "lifecycleId", "block:" + level.dimension().toString() + "@" + key(pos));
            value.addProperty("x", pos.getX());
            value.addProperty("y", pos.getY());
            value.addProperty("z", pos.getZ());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            value.addProperty("available", chunk != null);
            value.addProperty("loadRequested", false);
            if (chunk != null) {
                BlockState state = chunk.getBlockState(pos);
                value.addProperty(
                        "blockId", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                value.add("properties", debugBlockProperties(state));
            } else {
                value.addProperty("reason", "NOT_LOADED");
            }
            json.add(value);
        }
        return json;
    }

    private JsonObject world(MinecraftServer server, ServerLevel level) {
        JsonObject json = new JsonObject();
        JsonArray dimensions = new JsonArray();
        for (ServerLevel existing : server.getAllLevels()) {
            JsonObject entry = new JsonObject();
            String id = existing.dimension().location().toString();
            entry.addProperty("dimension", id);
            entry.addProperty(
                    "lifecycleId", this.lifecycles.lifecycleId("dimension", id, existing));
            entry.addProperty("currentPlayerDimension", existing == level);
            entry.addProperty("readOnly", true);
            entry.addProperty("chunkLoadRequested", false);
            dimensions.add(entry);
        }
        json.add("dimensions", dimensions);
        json.add("environment", environment(server, level));
        json.addProperty("dimension", level.dimension().location().toString());
        json.addProperty("gameTime", level.getGameTime());
        json.addProperty("dayTime", level.getDayTime());
        json.addProperty("raining", level.isRaining());
        json.addProperty("thundering", level.isThundering());
        json.addProperty("blockScheduledTickCount", level.getBlockTicks().count());
        json.addProperty("fluidScheduledTickCount", level.getFluidTicks().count());
        json.add("simulation", simulation(server));
        json.addProperty("serverTick", server.getTickCount());
        return json;
    }

    private JsonObject simulation(MinecraftServer server) {
        JsonObject value = new JsonObject();
        value.addProperty("readOnly", true);
        value.addProperty("readOnlyReason", "simulation_control_not_in_approved_mutation_catalog");
        if (server instanceof net.minecraft.client.server.IntegratedServer integrated)
            value.addProperty(
                    "integratedPaused",
                    ((io.github.recrivenvi.minecraftprotocol.probe.mixin.IntegratedPauseAccessor)
                                    integrated)
                            .minecraftProtocol$isPaused());
        value.addProperty("tickRateControlAvailable", false);
        value.addProperty("nativeAbsentReason", "native_tick_rate_manager_absent");
        value.addProperty("isPerformanceMeasurement", false);
        return value;
    }

    private JsonObject item(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("empty", stack.isEmpty());
        json.addProperty(
                "id",
                stack.isEmpty()
                        ? "minecraft:air"
                        : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (!stack.hasCustomHoverName()) json.add("customName", com.google.gson.JsonNull.INSTANCE);
        else json.addProperty("customName", stack.getHoverName().getString());
        json.addProperty("damage", stack.getDamageValue());
        json.addProperty("maxDamage", stack.getMaxDamage());
        json.addProperty(
                "componentsHash",
                stack.isEmpty() || !stack.hasTag() ? "" : sha256(stack.getTag().toString()));
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
        diffArray(
                operations,
                "entities",
                "uuid",
                "entity.spawn",
                "entity.remove",
                "entity.state_change",
                before,
                after);
        diffArray(
                operations,
                "blocks",
                "key",
                "block.add",
                "block.remove",
                "block.change",
                before,
                after);
        diffArray(
                operations,
                "blockEntities",
                "key",
                "block_entity.add",
                "block_entity.remove",
                "block_entity.change",
                before,
                after);
        diffArray(
                operations,
                "chunks",
                "key",
                "chunk.load",
                "chunk.unload",
                "chunk.state_change",
                before,
                after);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("type", "snapshot.metadata");
        metadata.addProperty("snapshotId", after.get("snapshotId").getAsString());
        metadata.addProperty("snapshotSequence", after.get("snapshotSequence").getAsLong());
        metadata.addProperty("capturedAtMillis", after.get("capturedAtMillis").getAsLong());
        metadata.addProperty("serverTick", after.get("serverTick").getAsLong());
        operations.add(metadata);
        return operations;
    }

    private static void replaceObject(
            JsonArray operations, String field, String type, JsonObject before, JsonObject after) {
        if (before.get(field).equals(after.get(field))) return;
        JsonObject operation = new JsonObject();
        operation.addProperty("type", type);
        operation.addProperty("domain", field);
        operation.add("after", after.get(field).deepCopy());
        operations.add(operation);
    }

    private static void diffArray(
            JsonArray operations,
            String field,
            String keyField,
            String addType,
            String removeType,
            String changeType,
            JsonObject before,
            JsonObject after) {
        Map<String, JsonObject> oldValues = keyed(before.getAsJsonArray(field), keyField);
        Map<String, JsonObject> newValues = keyed(after.getAsJsonArray(field), keyField);
        for (String key : oldValues.keySet())
            if (!newValues.containsKey(key)) {
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
                snapshot.addProperty(
                        "snapshotSequence", operation.get("snapshotSequence").getAsLong());
                snapshot.addProperty(
                        "capturedAtMillis", operation.get("capturedAtMillis").getAsLong());
                snapshot.addProperty("serverTick", operation.get("serverTick").getAsLong());
            } else if (!operation.has("key")) {
                snapshot.add(
                        operation.get("domain").getAsString(), operation.get("after").deepCopy());
            } else {
                String field = operation.get("domain").getAsString();
                JsonArray values = snapshot.getAsJsonArray(field);
                String keyField = field.equals("entities") ? "uuid" : "key";
                String key = operation.get("key").getAsString();
                for (int index = values.size() - 1; index >= 0; index--) {
                    if (values.get(index).getAsJsonObject().get(keyField).getAsString().equals(key))
                        values.remove(index);
                }
                if (operation.has("after")) values.add(operation.get("after").deepCopy());
                List<JsonElement> sorted = new ArrayList<>();
                values.forEach(sorted::add);
                sorted.sort(
                        Comparator.comparing(
                                value -> value.getAsJsonObject().get(keyField).getAsString()));
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

    private JsonObject mutation(
            String type,
            ServerPlayer player,
            String resource,
            Object before,
            Object after,
            String mechanism) {
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
        selector.addProperty(
                "radiusChunks",
                bounded(
                        request.has("radiusChunks") ? request.get("radiusChunks").getAsInt() : 1,
                        0,
                        2));
        selector.addProperty(
                "entityRadius",
                bounded(
                        request.has("entityRadius") ? request.get("entityRadius").getAsInt() : 32,
                        0,
                        64));
        selector.addProperty(
                "entityLimit",
                bounded(
                        request.has("entityLimit")
                                ? request.get("entityLimit").getAsInt()
                                : MAX_ENTITIES,
                        1,
                        MAX_ENTITIES));
        selector.addProperty(
                "blockEntityLimit",
                bounded(
                        request.has("blockEntityLimit")
                                ? request.get("blockEntityLimit").getAsInt()
                                : MAX_BLOCK_ENTITIES,
                        1,
                        MAX_BLOCK_ENTITIES));
        selector.addProperty(
                "includeSerializedState",
                request.has("includeSerializedState")
                        && request.get("includeSerializedState").getAsBoolean());
        selector.addProperty(
                "maxSerializedBytesPerBlockEntity",
                bounded(
                        request.has("maxSerializedBytesPerBlockEntity")
                                ? request.get("maxSerializedBytesPerBlockEntity").getAsInt()
                                : 16_384,
                        256,
                        16_384));
        selector.addProperty(
                "maxTotalSerializedBlockEntityBytes",
                bounded(
                        request.has("maxTotalSerializedBlockEntityBytes")
                                ? request.get("maxTotalSerializedBlockEntityBytes").getAsInt()
                                : 65_536,
                        1_024,
                        65_536));
        JsonArray blocks =
                request.has("selectedBlocks") && request.get("selectedBlocks").isJsonArray()
                        ? request.getAsJsonArray("selectedBlocks").deepCopy()
                        : new JsonArray();
        if (blocks.size() > MAX_SELECTED_BLOCKS)
            throw new ProtocolState.ProtocolException(
                    "PHASE9A_SELECTOR_TOO_LARGE",
                    413,
                    "selectedBlocks supports at most " + MAX_SELECTED_BLOCKS);
        selector.add("selectedBlocks", blocks);
        selector.add(
                "domains",
                request.has("domains") && request.get("domains").isJsonArray()
                        ? request.getAsJsonArray("domains").deepCopy()
                        : allDomains());
        if (request.has("blockEntityCursor"))
            selector.add("blockEntityCursor", request.get("blockEntityCursor").deepCopy());
        if (request.has("entityTargets"))
            selector.add("entityTargets", request.get("entityTargets").deepCopy());
        if (request.has("entityChunk"))
            selector.add("entityChunk", request.get("entityChunk").deepCopy());
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
        if (!object.has(name) || object.get(name).getAsString().isBlank())
            throw new ProtocolState.ProtocolException(
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
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
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
        this.blockEntityPages.clear();
        this.providerExecution.close();
        this.revisionWorker.close();
        this.storageAdapter.close();
        this.writeLifecycle.close();
    }

    record StorageRequest(
            String domain,
            Path root,
            Path playerDataRoot,
            String levelName,
            ResourceKey<Level> dimension,
            String playerUuid,
            ChunkPos chunkPos,
            boolean targetLoaded,
            boolean liveWorldExists,
            boolean saveInProgress,
            String sessionEpoch,
            long lifecycleEpoch,
            String worldFingerprint) {}
}
