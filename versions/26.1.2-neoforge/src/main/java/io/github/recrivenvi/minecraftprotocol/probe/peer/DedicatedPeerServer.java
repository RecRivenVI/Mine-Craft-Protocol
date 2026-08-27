package io.github.recrivenvi.minecraftprotocol.probe.peer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class DedicatedPeerServer {
    private static final Gson GSON = new Gson();

    private DedicatedPeerServer() {
    }

    static void handle(PeerPayload payload, ServerPlayer player, Consumer<CustomPacketPayload> reply) {
        JsonObject message;
        try {
            message = JsonParser.parseString(payload.json()).getAsJsonObject();
        } catch (Throwable throwable) {
            return;
        }
        String kind = string(message, "kind", "");
        if (kind.equals("hello")) {
            JsonObject ack = new JsonObject();
            ack.addProperty("kind", "hello_ack");
            ack.addProperty("protocol", "peer-v0");
            ack.addProperty("serverTick", player.level().getServer().getTickCount());
            ack.addProperty("read", true);
            ack.addProperty("fixture", fixtureEnabled() && isOperator(player));
            ack.addProperty("debug", debugEnabled() && isOperator(player));
            reply.accept(new PeerPayload(GSON.toJson(ack)));
            return;
        }
        if (!kind.equals("request") || !message.has("requestId") || !message.has("operation")) return;
        String requestId = message.get("requestId").getAsString();
        String operation = message.get("operation").getAsString();
        JsonObject params = message.has("params") && message.get("params").isJsonObject()
                ? message.getAsJsonObject("params") : new JsonObject();
        JsonObject response = new JsonObject();
        response.addProperty("kind", "response");
        response.addProperty("protocol", "peer-v0");
        response.addProperty("requestId", requestId);
        response.addProperty("serverTick", player.level().getServer().getTickCount());
        try {
            response.addProperty("ok", true);
            response.add("data", execute(operation, params, player));
        } catch (PeerFailure failure) {
            response.addProperty("ok", false);
            response.addProperty("error", failure.code);
            response.addProperty("message", failure.getMessage());
        } catch (Throwable throwable) {
            response.addProperty("ok", false);
            response.addProperty("error", "SERVER_PEER_ERROR");
            response.addProperty("message", throwable.getMessage() == null ? "Peer operation failed" : throwable.getMessage());
        }
        reply.accept(new PeerPayload(GSON.toJson(response)));
    }

    private static JsonObject execute(String operation, JsonObject params, ServerPlayer player) {
        return switch (operation) {
            case "peer.status" -> status(player);
            case "player.get" -> playerState(player);
            case "world.block.get" -> blockState(player, requiredInt(params, "x"), requiredInt(params, "y"), requiredInt(params, "z"));
            case "world.entities.query" -> entities(player, doubleValue(params, "radius", 16.0));
            case "world.fingerprint" -> fingerprint(player);
            case "fixture.player.teleport" -> {
                requireFixture(player);
                yield teleport(player, requiredDouble(params, "x"), requiredDouble(params, "y"), requiredDouble(params, "z"));
            }
            case "debug.player.health" -> {
                requireDebug(player);
                yield setHealth(player, (float) requiredDouble(params, "health"));
            }
            case "debug.world.block" -> {
                requireDebug(player);
                yield setBlock(
                        player,
                        requiredInt(params, "x"), requiredInt(params, "y"), requiredInt(params, "z"),
                        requiredString(params, "blockId"), nullableString(params, "expectedBlockId"));
            }
            default -> throw new PeerFailure("PEER_OPERATION_UNSUPPORTED", "Unsupported Peer operation: " + operation);
        };
    }

    private static JsonObject status(ServerPlayer player) {
        JsonObject json = base(player, "server.peer.capabilities");
        json.addProperty("read", true);
        json.addProperty("fixture", fixtureEnabled() && isOperator(player));
        json.addProperty("debug", debugEnabled() && isOperator(player));
        json.addProperty("operator", isOperator(player));
        return json;
    }

    private static JsonObject playerState(ServerPlayer player) {
        JsonObject json = base(player, "server.player.state");
        json.addProperty("available", true);
        json.addProperty("uuid", player.getUUID().toString());
        json.addProperty("x", player.getX());
        json.addProperty("y", player.getY());
        json.addProperty("z", player.getZ());
        json.addProperty("yaw", player.getYRot());
        json.addProperty("pitch", player.getXRot());
        json.addProperty("health", player.getHealth());
        json.addProperty("maxHealth", player.getMaxHealth());
        json.addProperty("food", player.getFoodData().getFoodLevel());
        json.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        json.addProperty("dimension", player.level().dimension().identifier().toString());
        return json;
    }

    private static JsonObject blockState(ServerPlayer player, int x, int y, int z) {
        ServerLevel level = player.level();
        BlockPos position = new BlockPos(x, y, z);
        JsonObject json = base(player, "server.world.block");
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("chunkLoadRequested", false);
        if (!level.hasChunkAt(position)) {
            json.addProperty("available", false);
            json.addProperty("reason", "chunk_not_loaded");
            return json;
        }
        BlockState state = level.getBlockState(position);
        json.addProperty("available", true);
        json.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        json.addProperty("state", state.toString());
        return json;
    }

    private static JsonObject entities(ServerPlayer player, double radius) {
        ServerLevel level = player.level();
        double boundedRadius = Mth.clamp(radius, 0.0, 128.0);
        JsonArray values = new JsonArray();
        for (Entity entity : level.getEntities(
                player, player.getBoundingBox().inflate(boundedRadius), entity -> true)) {
            if (values.size() >= 128) break;
            JsonObject item = new JsonObject();
            item.addProperty("uuid", entity.getUUID().toString());
            item.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            item.addProperty("x", entity.getX());
            item.addProperty("y", entity.getY());
            item.addProperty("z", entity.getZ());
            values.add(item);
        }
        JsonObject json = base(player, "server.world.entities");
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.addProperty("radius", boundedRadius);
        json.add("entities", values);
        return json;
    }

    private static JsonObject fingerprint(ServerPlayer player) {
        String material = "dedicated-peer|" + player.level().getServer().getWorldData().getLevelName()
                + "|" + player.level().dimension().identifier();
        JsonObject json = base(player, "world.fingerprint");
        json.addProperty("worldFingerprint", sha256(material));
        json.addProperty("sessionBound", true);
        return json;
    }

    private static JsonObject teleport(ServerPlayer player, double x, double y, double z) {
        double beforeX = player.getX();
        double beforeY = player.getY();
        double beforeZ = player.getZ();
        player.teleportTo(x, y, z);
        JsonObject json = mutation(player, "fixture.player.teleport", "FIXTURE", "SERVER_API");
        json.addProperty("beforeX", beforeX);
        json.addProperty("beforeY", beforeY);
        json.addProperty("beforeZ", beforeZ);
        json.addProperty("x", player.getX());
        json.addProperty("y", player.getY());
        json.addProperty("z", player.getZ());
        return json;
    }

    private static JsonObject setHealth(ServerPlayer player, float requested) {
        float before = player.getHealth();
        player.setHealth(Mth.clamp(requested, 0.0F, player.getMaxHealth()));
        JsonObject json = mutation(player, "debug.player.health", "DEBUG_PRIVILEGED", "DIRECT_MUTATION");
        json.addProperty("before", before);
        json.addProperty("requested", requested);
        json.addProperty("applied", player.getHealth());
        return json;
    }

    private static JsonObject setBlock(
            ServerPlayer player, int x, int y, int z, String blockId, String expectedBlockId) {
        ServerLevel level = player.level();
        BlockPos position = new BlockPos(x, y, z);
        if (!level.hasChunkAt(position)) throw new PeerFailure("CHUNK_NOT_LOADED", "Debug block target is not loaded");
        Identifier id = Identifier.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new PeerFailure("UNKNOWN_BLOCK", "Unknown block: " + blockId);
        }
        BlockState before = level.getBlockState(position);
        String beforeId = BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString();
        if (expectedBlockId != null && !expectedBlockId.equals(beforeId)) {
            throw new PeerFailure("PRECONDITION_FAILED", "Expected block " + expectedBlockId + " but found " + beforeId);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        level.setBlockAndUpdate(position, block.defaultBlockState());
        JsonObject json = mutation(player, "debug.world.block", "DEBUG_PRIVILEGED", "DIRECT_MUTATION");
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("before", beforeId);
        json.addProperty("after", BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString());
        return json;
    }

    private static JsonObject base(ServerPlayer player, String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("serverTick", player.level().getServer().getTickCount());
        json.addProperty("thread", Thread.currentThread().getName());
        json.addProperty("perspective", "server_authoritative_live");
        json.addProperty("source", "dedicated_server_peer");
        json.addProperty("authority", "server_authoritative");
        json.addProperty("dataSource", "LIVE");
        json.addProperty("storageAccessed", false);
        json.addProperty("stalePossible", false);
        json.addProperty("peerAuthenticated", true);
        return json;
    }

    private static JsonObject mutation(ServerPlayer player, String type, String mode, String mechanism) {
        JsonObject json = base(player, type);
        json.addProperty("mode", mode);
        json.addProperty("mechanism", mechanism);
        json.addProperty("evidenceContaminated", true);
        json.addProperty("directMutationUsed", mechanism.equals("DIRECT_MUTATION"));
        return json;
    }

    private static void requireFixture(ServerPlayer player) {
        if (!fixtureEnabled() || !isOperator(player)) {
            throw new PeerFailure("PEER_FIXTURE_DENIED", "Server Peer Fixture is disabled or player is not an operator");
        }
    }

    private static void requireDebug(ServerPlayer player) {
        if (!debugEnabled() || !isOperator(player)) {
            throw new PeerFailure("PEER_DEBUG_DENIED", "Server Peer Debug is disabled or player is not an operator");
        }
    }

    private static boolean isOperator(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server.isSingleplayerOwner(player.nameAndId())
                || server.getPlayerList().isOp(player.nameAndId());
    }

    private static boolean fixtureEnabled() {
        return enabled("minecraft.protocol.peer.allowFixture", "MCP_PEER_ALLOW_FIXTURE");
    }

    private static boolean debugEnabled() {
        return enabled("minecraft.protocol.peer.allowDebug", "MCP_PEER_ALLOW_DEBUG");
    }

    private static boolean enabled(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value != null && value.toLowerCase(Locale.ROOT).equals("true");
    }

    private static int requiredInt(JsonObject object, String name) {
        if (!object.has(name)) throw new PeerFailure("INVALID_ARGUMENT", "Missing " + name);
        return object.get(name).getAsInt();
    }

    private static double requiredDouble(JsonObject object, String name) {
        if (!object.has(name)) throw new PeerFailure("INVALID_ARGUMENT", "Missing " + name);
        return object.get(name).getAsDouble();
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name)) throw new PeerFailure("INVALID_ARGUMENT", "Missing " + name);
        return object.get(name).getAsString();
    }

    private static double doubleValue(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }

    private static String nullableString(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : null;
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class PeerFailure extends RuntimeException {
        private final String code;

        private PeerFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
