package io.github.recrivenvi.minecraftprotocol.probe.peer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

final class DedicatedPeerServer {
    private static final Gson GSON = new Gson();
    private static final java.util.Map<
                    Object,
                    java.util.concurrent.ConcurrentHashMap<
                            String, java.util.concurrent.CompletableFuture<JsonObject>>>
            ACTIVE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static java.util.concurrent.ConcurrentHashMap<
                    String, java.util.concurrent.CompletableFuture<JsonObject>>
            active(ServerPlayer player) {
        return ACTIVE.computeIfAbsent(
                player.connection, ignored -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    private static final java.util.Map<Object, String> CONNECTIONS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static String connectionId(ServerPlayer player) {
        return CONNECTIONS.computeIfAbsent(
                player.connection, ignored -> java.util.UUID.randomUUID().toString());
    }

    private DedicatedPeerServer() {}

    static void handle(PeerMessage payload, ServerPlayer player, Consumer<PeerMessage> reply) {
        JsonObject message;
        try {
            message = JsonParser.parseString(payload.json()).getAsJsonObject();
        } catch (Throwable throwable) {
            return;
        }
        String kind = string(message, "kind", "");
        if (kind.equals("cancel") && message.has("requestId")) {
            var pending = active(player).remove(message.get("requestId").getAsString());
            if (pending != null) pending.cancel(false);
            return;
        }
        if (kind.equals("hello")) {
            JsonObject ack = new JsonObject();
            ack.addProperty("kind", "hello_ack");
            ack.addProperty("protocol", "peer-v0");
            ack.addProperty("serverTick", player.serverLevel().getServer().getTickCount());
            ack.addProperty("read", true);
            ack.addProperty("authorization", "SERVER_DELEGATION_REQUIRED");
            ack.addProperty("typedDebugSupported", true);
            ack.addProperty("legacyDirectDebugSupported", false);
            ack.addProperty("requiresServerDelegation", true);
            ack.addProperty("connectionId", connectionId(player));
            ack.addProperty("playerUuid", player.getUUID().toString());
            ack.addProperty("fixture", fixtureEnabled() && isOperator(player));
            ack.addProperty("debug", true);
            reply.accept(new PeerMessage(GSON.toJson(ack)));
            return;
        }
        if (!kind.equals("request") || !message.has("requestId") || !message.has("operation"))
            return;
        String requestId = message.get("requestId").getAsString();
        String operation = message.get("operation").getAsString();
        if (requestId.length() > 128
                || active(player).size() >= 64
                || active(player).containsKey(requestId)) return;
        JsonObject params =
                message.has("params") && message.get("params").isJsonObject()
                        ? message.getAsJsonObject("params")
                        : new JsonObject();
        JsonObject response = new JsonObject();
        response.addProperty("kind", "response");
        response.addProperty("protocol", "peer-v0");
        response.addProperty("requestId", requestId);
        response.addProperty("serverTick", player.serverLevel().getServer().getTickCount());
        if (operation.equals("peer.status")) {
            response.addProperty("ok", true);
            response.add("data", status(player));
            reply.accept(new PeerMessage(GSON.toJson(response)));
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        java.util.concurrent.CompletableFuture<JsonObject> operationResult;
        var nativePermission = new java.util.concurrent.atomic.AtomicBoolean(isOperator(player));
        java.util.function.BooleanSupplier permission =
                () -> {
                    if (server.isSameThread())
                        nativePermission.set(
                                server.isRunning()
                                        && server.getPlayerList().getPlayer(player.getUUID())
                                                == player
                                        && isOperator(player));
                    return nativePermission.get();
                };
        params.addProperty("peerRequestId", requestId);
        try {
            operationResult =
                    io.github.recrivenvi.minecraftprotocol.core.ServerPeerReceiver.request(
                            server,
                            player.getUUID().toString(),
                            connectionId(player),
                            permission,
                            operation,
                            params,
                            (authorization, work) -> {
                                java.util.concurrent.CompletableFuture<JsonObject> result =
                                        new java.util.concurrent.CompletableFuture<>();
                                server.execute(
                                        () -> {
                                            if (result.isDone()) return;
                                            try (var permit = work == null ? null : work.enter()) {
                                                authorization.run();
                                                result.complete(execute(operation, params, player));
                                            } catch (Throwable failure) {
                                                result.completeExceptionally(failure);
                                            }
                                        });
                                return result;
                            });
        } catch (Throwable failure) {
            operationResult = java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
        active(player).put(requestId, operationResult);
        operationResult.whenComplete(
                (value, failure) -> {
                    active(player).remove(requestId);
                    server.execute(
                            () -> {
                                if (server.getPlayerList().getPlayer(player.getUUID()) != player)
                                    return;
                                Throwable outcome = failure;
                                try {
                                    io.github.recrivenvi.minecraftprotocol.core.ServerPeerReceiver
                                            .requirePublication(
                                                    server,
                                                    player.getUUID().toString(),
                                                    connectionId(player),
                                                    permission,
                                                    params);
                                } catch (Throwable denied) {
                                    outcome = denied;
                                }
                                if (outcome == null) {
                                    response.addProperty("ok", true);
                                    response.add("data", value);
                                } else {
                                    Throwable cause = outcome;
                                    while (cause.getCause() != null
                                            && (cause
                                                            instanceof
                                                            java.util.concurrent.CompletionException
                                                    || cause
                                                            instanceof
                                                            java.util.concurrent
                                                                    .ExecutionException))
                                        cause = cause.getCause();
                                    response.addProperty("ok", false);
                                    if (cause
                                            instanceof
                                            io.github.recrivenvi.minecraftprotocol.core
                                                            .ProtocolState.ProtocolException
                                                    error) {
                                        response.addProperty("error", error.code());
                                        response.addProperty("httpStatus", error.httpStatus());
                                        response.add("details", error.details());
                                    } else response.addProperty("error", "SERVER_PEER_ERROR");
                                    response.addProperty(
                                            "message",
                                            cause.getMessage() == null
                                                    ? "Peer operation failed"
                                                    : cause.getMessage());
                                }
                                String encoded = GSON.toJson(response);
                                if (encoded.length() > PeerMessage.MAX_JSON_BYTES - 1024
                                        || encoded.getBytes(StandardCharsets.UTF_8).length
                                                > PeerMessage.MAX_JSON_BYTES - 1024) {
                                    response.remove("data");
                                    response.addProperty("ok", false);
                                    response.addProperty("error", "PEER_RESPONSE_BUDGET_EXCEEDED");
                                    response.addProperty("httpStatus", 413);
                                    JsonObject details = new JsonObject();
                                    details.addProperty("executionOutcome", "not_confirmed");
                                    details.addProperty("retrySafe", false);
                                    details.addProperty(
                                            "mutationMayHaveCommitted",
                                            operation.equals("debug.mutate"));
                                    response.add("details", details);
                                    response.addProperty(
                                            "message",
                                            "Peer payload budget is 30 KiB; narrow the query. A submitted mutation may already have committed; inspect before retry.");
                                    response.addProperty(
                                            "mutationMayHaveCommitted",
                                            operation.equals("debug.mutate"));
                                    encoded = GSON.toJson(response);
                                }
                                reply.accept(new PeerMessage(encoded));
                            });
                });
    }

    private static JsonObject execute(String operation, JsonObject params, ServerPlayer player) {
        return switch (operation) {
            case "peer.status" -> status(player);
            case "player.get" -> playerState(player);
            case "world.block.get" ->
                    blockState(
                            player,
                            requiredInt(params, "x"),
                            requiredInt(params, "y"),
                            requiredInt(params, "z"));
            case "world.entities.query" -> entities(player, doubleValue(params, "radius", 16.0));
            case "fixture.player.teleport" -> {
                requireFixture(player);
                yield teleport(
                        player,
                        requiredDouble(params, "x"),
                        requiredDouble(params, "y"),
                        requiredDouble(params, "z"));
            }
            default ->
                    throw new PeerFailure(
                            "PEER_OPERATION_UNSUPPORTED",
                            "Unsupported Peer operation: " + operation);
        };
    }

    private static JsonObject status(ServerPlayer player) {
        JsonObject json = base(player, "server.peer.capabilities");
        json.addProperty("read", true);
        json.addProperty("authorization", "SERVER_DELEGATION_REQUIRED");
        json.addProperty("typedDebugSupported", true);
        json.addProperty("legacyDirectDebugSupported", false);
        json.addProperty("requiresServerDelegation", true);
        json.addProperty("connectionId", connectionId(player));
        json.addProperty("maxResponseBytes", 30 * 1024);
        json.addProperty("fixture", fixtureEnabled() && isOperator(player));
        json.addProperty("debug", true);
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
        json.addProperty("selectedSlot", player.getInventory().selected);
        json.addProperty("dimension", player.serverLevel().dimension().location().toString());
        return json;
    }

    private static JsonObject blockState(ServerPlayer player, int x, int y, int z) {
        ServerLevel level = player.serverLevel();
        BlockPos position = new BlockPos(x, y, z);
        JsonObject json = base(player, "server.world.block");
        json.addProperty("dimension", level.dimension().location().toString());
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
        ServerLevel level = player.serverLevel();
        double boundedRadius = Mth.clamp(radius, 0.0, 128.0);
        JsonArray values = new JsonArray();
        for (Entity entity :
                level.getEntities(
                        player, player.getBoundingBox().inflate(boundedRadius), entity -> true)) {
            if (values.size() >= 128) break;
            JsonObject item = new JsonObject();
            item.addProperty("uuid", entity.getUUID().toString());
            item.addProperty(
                    "type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            item.addProperty("x", entity.getX());
            item.addProperty("y", entity.getY());
            item.addProperty("z", entity.getZ());
            values.add(item);
        }
        JsonObject json = base(player, "server.world.entities");
        json.addProperty("dimension", level.dimension().location().toString());
        json.addProperty("radius", boundedRadius);
        json.add("entities", values);
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

    private static JsonObject base(ServerPlayer player, String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("serverTick", player.serverLevel().getServer().getTickCount());
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

    private static JsonObject mutation(
            ServerPlayer player, String type, String mode, String mechanism) {
        JsonObject json = base(player, type);
        json.addProperty("mode", mode);
        json.addProperty("mechanism", mechanism);
        json.addProperty("evidenceContaminated", true);
        json.addProperty("directMutationUsed", mechanism.equals("DIRECT_MUTATION"));
        return json;
    }

    private static void requireFixture(ServerPlayer player) {
        if (!fixtureEnabled() || !isOperator(player)) {
            throw new PeerFailure(
                    "PEER_FIXTURE_DENIED",
                    "Server Peer Fixture is disabled or player is not an operator");
        }
    }

    private static boolean isOperator(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        return server.isSingleplayerOwner(player.getGameProfile())
                || server.getPlayerList().isOp(player.getGameProfile());
    }

    private static boolean fixtureEnabled() {
        return enabled("minecraft.protocol.peer.allowFixture", "MCP_PEER_ALLOW_FIXTURE");
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

    private static double doubleValue(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private static final class PeerFailure extends RuntimeException {
        private final String code;

        private PeerFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
