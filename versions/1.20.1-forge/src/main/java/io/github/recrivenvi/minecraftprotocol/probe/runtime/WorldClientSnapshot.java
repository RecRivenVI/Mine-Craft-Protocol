package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.WorldClientModel;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Captures only this client's existing cache, synchronously on its owner thread. */
final class WorldClientSnapshot {
    private WorldClientSnapshot() {}

    static JsonObject capture(String kind, JsonObject query, WorldTargeting targeting) {
        Minecraft client = Minecraft.getInstance();
        JsonObject result =
                WorldClientModel.capture(
                        kind,
                        query,
                        new WorldClientModel.View() {
                            public boolean available() {
                                return client.level != null && client.player != null;
                            }

                            public JsonObject player() {
                                var player = client.player;
                                JsonObject value = new JsonObject();
                                value.addProperty("x", player.getX());
                                value.addProperty("y", player.getY());
                                value.addProperty("z", player.getZ());
                                value.addProperty("yaw", player.getYRot());
                                value.addProperty("pitch", player.getXRot());
                                value.addProperty("health", player.getHealth());
                                var mode =
                                        client.gameMode == null
                                                ? null
                                                : client.gameMode.getPlayerMode();
                                value.addProperty(
                                        "gameMode", mode == null ? "unknown" : mode.getName());
                                value.addProperty("onGround", player.onGround());
                                value.addProperty("flying", player.getAbilities().flying);
                                value.addProperty("maxHealth", player.getMaxHealth());
                                value.addProperty("absorption", player.getAbsorptionAmount());
                                value.addProperty("food", player.getFoodData().getFoodLevel());
                                value.addProperty("air", player.getAirSupply());
                                value.addProperty("usingItem", player.isUsingItem());
                                value.addProperty(
                                        "usingHand",
                                        player.isUsingItem()
                                                ? player.getUsedItemHand()
                                                                == net.minecraft.world
                                                                        .InteractionHand.OFF_HAND
                                                        ? "off_hand"
                                                        : "main_hand"
                                                : "none");
                                value.addProperty("riding", player.isPassenger());
                                value.addProperty("useTicks", player.getTicksUsingItem());
                                value.addProperty("selectedSlot", player.getInventory().selected);
                                value.addProperty(
                                        "dimension",
                                        client.level.dimension().location().toString());
                                return value;
                            }

                            public JsonObject block(int x, int y, int z) {
                                JsonObject value = new JsonObject();
                                // Required=false is essential: neither the empty fallback nor
                                // loading establishes client knowledge.
                                var chunk =
                                        client.level
                                                .getChunkSource()
                                                .getChunk(
                                                        x >> 4,
                                                        z >> 4,
                                                        net.minecraft.world.level.chunk.ChunkStatus
                                                                .FULL,
                                                        false);
                                value.addProperty("available", chunk != null);
                                if (chunk == null) {
                                    value.addProperty("reason", "chunk_not_loaded");
                                    return value;
                                }
                                var state = chunk.getBlockState(new BlockPos(x, y, z));
                                targeting.block(value, chunk, new BlockPos(x, y, z), state);
                                value.addProperty(
                                        "block",
                                        BuiltInRegistries.BLOCK
                                                .getKey(state.getBlock())
                                                .toString());
                                value.addProperty("state", state.toString());
                                return value;
                            }

                            public Iterator<JsonObject> entities() {
                                Iterator<Entity> source =
                                        client.level.entitiesForRendering().iterator();
                                return new Iterator<>() {
                                    public boolean hasNext() {
                                        return source.hasNext();
                                    }

                                    public JsonObject next() {
                                        Entity entity = source.next();
                                        JsonObject value = new JsonObject();
                                        value.addProperty(
                                                "type",
                                                BuiltInRegistries.ENTITY_TYPE
                                                        .getKey(entity.getType())
                                                        .toString());
                                        value.addProperty("name", entity.getName().getString());
                                        value.addProperty("x", entity.getX());
                                        value.addProperty("y", entity.getY());
                                        value.addProperty("z", entity.getZ());
                                        value.addProperty("yaw", entity.getYRot());
                                        value.addProperty("pitch", entity.getXRot());
                                        value.addProperty("invisible", entity.isInvisible());
                                        value.addProperty("onGround", entity.onGround());
                                        if (entity
                                                instanceof
                                                net.minecraft.world.entity.LivingEntity living) {
                                            value.addProperty("hurtAnimation", living.hurtTime > 0);
                                            value.addProperty("baby", living.isBaby());
                                        }
                                        if (entity
                                                instanceof
                                                net.minecraft.world.entity.TamableAnimal animal) {
                                            value.addProperty("tamed", animal.isTame());
                                            value.addProperty("sitting", animal.isInSittingPose());
                                        }
                                        if (entity
                                                instanceof
                                                net.minecraft.world.entity.Mob leashable) {
                                            // Only resolve the ClientLevel cache; absence means
                                            // unknown, not proof of another owner.
                                            Entity holder = leashable.getLeashHolder();
                                            // Resolution can populate Vanilla's cached holder.
                                            value.addProperty("leashed", leashable.isLeashed());
                                            value.addProperty("leashHolderKnown", holder != null);
                                            if (holder != null)
                                                value.addProperty(
                                                        "leashHeldByPlayer",
                                                        holder == client.player);
                                        }
                                        targeting.entity(
                                                value,
                                                entity,
                                                query.has("radius")
                                                        ? query.get("radius").getAsDouble()
                                                        : kind.equals("scene") ? 4 : 16);
                                        return value;
                                    }
                                };
                            }
                        });
        if (kind.equals("scene"))
            result.add("aimPresentation", WorldPresentationSnapshot.capture());
        return result;
    }
}
