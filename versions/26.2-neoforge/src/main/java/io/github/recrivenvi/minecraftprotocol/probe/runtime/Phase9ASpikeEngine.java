package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.LevelResource;

/** Experimental Phase 9A evidence collector. It is not a frozen public protocol implementation. */
final class Phase9ASpikeEngine implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int MAX_SNAPSHOTS = 32;
    private static final int MAX_ENTITIES = 128;
    private static final int MAX_BLOCK_ENTITIES = 128;
    private static final int MAX_SELECTED_BLOCKS = 64;

    private final String target;
    private final ExecutorService storageWorker;
    private final Map<String, JsonObject> snapshots = new LinkedHashMap<>();
    private final Map<String, JsonObject> selectors = new LinkedHashMap<>();
    private final Map<String, JsonObject> deltas = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    Phase9ASpikeEngine(String target) {
        this.target = target;
        this.storageWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-phase9a-storage");
            thread.setDaemon(true);
            return thread;
        });
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
                Entity pig = EntityTypes.PIG.spawn(
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

    private JsonObject captureSnapshot(MinecraftServer server, ServerPlayer player, JsonObject selector) {
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
        json.addProperty("readEffects", "none_except_block_entity_serialization_hooks");
        json.addProperty("chunkLoadRequested", false);
        json.addProperty("dataSource", "LIVE");
        json.addProperty("storageAccessed", false);
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.add("selector", selector.deepCopy());
        json.add("player", player(player));
        json.add("entities", entities(level, player, selector.get("entityRadius").getAsDouble()));
        JsonArray chunks = chunks(level, player, selector.get("radiusChunks").getAsInt());
        json.add("chunks", chunks);
        json.add("blockEntities", blockEntities(level, chunks));
        json.add("blocks", blocks(level, player, selector));
        json.add("world", world(server, level));
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
        return json;
    }

    private JsonObject player(ServerPlayer player) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", player.getUUID().toString());
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
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            JsonObject attribute = new JsonObject();
            attribute.addProperty("id", instance.getAttribute().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("unregistered"));
            attribute.addProperty("base", instance.getBaseValue());
            attribute.addProperty("value", instance.getValue());
            attributes.add(attribute);
        }
        json.add("attributes", attributes);
        JsonArray effects = new JsonArray();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            JsonObject effect = new JsonObject();
            effect.addProperty("id", instance.getEffect().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("unregistered"));
            effect.addProperty("duration", instance.getDuration());
            effect.addProperty("amplifier", instance.getAmplifier());
            effects.add(effect);
        }
        json.add("effects", effects);
        json.addProperty("vehicle", player.getVehicle() == null ? "" : player.getVehicle().getUUID().toString());
        JsonArray passengers = new JsonArray();
        for (Entity passenger : player.getPassengers()) passengers.add(passenger.getUUID().toString());
        json.add("passengers", passengers);
        json.addProperty("menuClass", player.containerMenu.getClass().getName());
        json.addProperty("menuId", player.containerMenu.containerId);
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

    private JsonArray entities(ServerLevel level, ServerPlayer player, double radius) {
        JsonArray json = new JsonArray();
        List<Entity> entities = level.getEntities(player, player.getBoundingBox().inflate(radius), ignored -> true);
        entities.stream().sorted(Comparator.comparing(entity -> entity.getUUID().toString())).limit(MAX_ENTITIES)
                .forEach(entity -> json.add(entity(entity)));
        return json;
    }

    private JsonObject entity(Entity entity) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", entity.getUUID().toString());
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
                } else {
                    value.addProperty("reason", "NOT_LOADED");
                }
                json.add(value);
            }
        }
        return json;
    }

    private JsonArray blockEntities(ServerLevel level, JsonArray chunks) {
        JsonArray json = new JsonArray();
        int count = 0;
        for (JsonElement element : chunks) {
            JsonObject chunkValue = element.getAsJsonObject();
            if (!chunkValue.get("loaded").getAsBoolean()) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    chunkValue.get("chunkX").getAsInt(), chunkValue.get("chunkZ").getAsInt());
            if (chunk == null) continue;
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                if (count++ >= MAX_BLOCK_ENTITIES) return json;
                BlockEntity blockEntity = entry.getValue();
                CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                JsonObject value = new JsonObject();
                value.addProperty("key", key(entry.getKey()));
                value.addProperty("type", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
                value.addProperty("serializedCharacters", tag.toString().length());
                value.addProperty("serializedSha256", sha256(tag.toString()));
                value.addProperty("readEffects", "serialization_hooks_invoked");
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
        json.addProperty("dayTime", "TARGET_SPECIFIC_clock_projection_requires_spike");
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
        JsonArray blocks = request.has("selectedBlocks") && request.get("selectedBlocks").isJsonArray()
                ? request.getAsJsonArray("selectedBlocks").deepCopy() : new JsonArray();
        if (blocks.size() > MAX_SELECTED_BLOCKS) throw new ProtocolState.ProtocolException(
                "PHASE9A_SELECTOR_TOO_LARGE", 413, "selectedBlocks supports at most " + MAX_SELECTED_BLOCKS);
        selector.add("selectedBlocks", blocks);
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
