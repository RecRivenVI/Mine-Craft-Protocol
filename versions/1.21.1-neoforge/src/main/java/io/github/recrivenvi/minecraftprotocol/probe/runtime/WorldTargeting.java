package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.WorldPickInvoker;
import io.github.recrivenvi.minecraftprotocol.safety.WorldAim;
import io.github.recrivenvi.minecraftprotocol.safety.WorldBlockChanges;
import io.github.recrivenvi.minecraftprotocol.safety.WorldBreakTrace;
import io.github.recrivenvi.minecraftprotocol.safety.WorldPlacementReceipt;
import io.github.recrivenvi.minecraftprotocol.safety.WorldTargetRegistry;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client-thread target adapter. No server, storage, chunk load, direct camera or world mutation.
 */
public final class WorldTargeting {
    private static final Map<Object, Long> CHANGES = new WeakHashMap<>();
    private static final WorldBlockChanges BLOCK_CHANGES = new WorldBlockChanges();
    private static final ThreadLocal<WorldBreakTrace> DESTROY = new ThreadLocal<>();

    private record Target(
            WeakReference<Object> world,
            WeakReference<Object> player,
            WeakReference<Object> camera,
            WeakReference<Object> chunk,
            WeakReference<Entity> entity,
            BlockPos block,
            BlockState state,
            long revision) {}

    private record Placement(BlockPos destination, Object chunk, BlockState before, String block) {}

    private static final class Hold {
        final String ref, action;
        final Target target;
        final JsonObject guard, options;
        final long started = System.nanoTime();
        boolean destroyPending, completed, useSeen, useReleased, invoked;
        String error, errorDetail;
        JsonObject errorEvidence;
        WorldBreakTrace destruction;
        int breakChanges;
        Placement placement;
        final WorldPlacementReceipt placementReceipt = new WorldPlacementReceipt();

        Hold(String ref, String action, Target target, JsonObject guard, JsonObject options) {
            this.ref = ref;
            this.action = action;
            this.target = target;
            this.guard = guard.deepCopy();
            this.options = options.deepCopy();
        }
    }

    private Hold hold;

    private final WorldTargetRegistry<Target> targets = new WorldTargetRegistry<>();
    private final WorldTargetRegistry<ItemStack> items = new WorldTargetRegistry<>();

    public static synchronized void chunkChanged(
            Object chunk, BlockPos pos, BlockState before, BlockState after) {
        if (CHANGES.containsKey(chunk)) {
            CHANGES.put(chunk, CHANGES.get(chunk) + 1);
            BLOCK_CHANGES.changed(chunk, pos.asLong());
        }
        WorldBreakTrace trace = DESTROY.get();
        if (trace != null) trace.changed(chunk, pos, before, after);
    }

    public static synchronized void chunkReplaced(Object chunk) {
        if (CHANGES.containsKey(chunk)) {
            // A bulk packet can rewrite the same object without calling setBlockState.
            CHANGES.put(chunk, CHANGES.get(chunk) + 1);
            BLOCK_CHANGES.replaced(chunk);
        }
    }

    private static synchronized long revision(Object chunk) {
        CHANGES.putIfAbsent(chunk, 0L);
        return CHANGES.get(chunk);
    }

    void block(JsonObject value, Object chunk, BlockPos pos, BlockState state) {
        if (state.isAir()) return;
        // Enroll only observed client chunks; native destruction still uses whole-chunk counts.
        revision(chunk);
        Minecraft client = Minecraft.getInstance();
        value.addProperty(
                "targetRef",
                targets.add(
                        new Target(
                                new WeakReference<>(client.level),
                                new WeakReference<>(client.player),
                                new WeakReference<>(client.getCameraEntity()),
                                new WeakReference<>(chunk),
                                new WeakReference<>(null),
                                pos.immutable(),
                                state,
                                BLOCK_CHANGES.snapshot())));
        value.addProperty("targetTtlMs", 15000);
    }

    void entity(JsonObject value, Entity entity, double radius) {
        Minecraft client = Minecraft.getInstance();
        if (entity == client.player
                || !entity.isPickable()
                || entity.isRemoved()
                || (entity instanceof net.minecraft.world.entity.LivingEntity living
                        && !living.isAlive())
                || entity.distanceToSqr(client.player) > radius * radius) return;
        value.addProperty(
                "targetRef",
                targets.add(
                        new Target(
                                new WeakReference<>(client.level),
                                new WeakReference<>(client.player),
                                new WeakReference<>(client.getCameraEntity()),
                                new WeakReference<>(null),
                                new WeakReference<>(entity),
                                null,
                                null,
                                0)));
        value.addProperty("targetTtlMs", 15000);
    }

    private Target valid(String ref) {
        Minecraft client = Minecraft.getInstance();
        Target target = targets.get(ref);
        if (target == null
                || client.level == null
                || client.player == null
                || target.world.get() != client.level
                || target.player.get() != client.player
                || target.camera.get() != client.getCameraEntity())
            throw error(
                    "WORLD_TARGET_STALE",
                    "Unknown, expired or replaced world/player/camera target");
        if (client.screen != null
                || client.getOverlay() != null
                || !client.options.getCameraType().isFirstPerson()
                || client.player.isSpectator()
                || !client.player.isAlive()
                || client.getWindow().getWidth() <= 0
                || client.getWindow().getHeight() <= 0)
            throw error(
                    "WORLD_ACTION_CONTEXT_CHANGED",
                    "An active first-person gameplay viewport is required");
        if (target.block != null) {
            var chunk =
                    client.level
                            .getChunkSource()
                            .getChunk(
                                    target.block.getX() >> 4,
                                    target.block.getZ() >> 4,
                                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                    false);
            if (chunk == null
                    || chunk != target.chunk.get()
                    || !BLOCK_CHANGES.unchanged(chunk, target.block.asLong(), target.revision)
                    || chunk.getBlockState(target.block) != target.state)
                throw error(
                        "WORLD_TARGET_STALE",
                        "Block/chunk identity or bounded history changed; rediscover target");
        } else {
            Entity entity = target.entity.get();
            if (entity == null
                    || entity.isRemoved()
                    || (entity instanceof net.minecraft.world.entity.LivingEntity living
                            && !living.isAlive())
                    || !entity.isPickable()
                    || client.level.getEntity(entity.getId()) != entity)
                throw error("WORLD_TARGET_STALE", "Entity lifecycle ended");
        }
        return target;
    }

    void validate(String ref, JsonObject guard) {
        valid(ref);
        if (guard == null) return;
        Minecraft client = Minecraft.getInstance();
        Vec3 position = client.player.position();
        if (position.distanceToSqr(
                        new Vec3(
                                guard.get("x").getAsDouble(),
                                guard.get("y").getAsDouble(),
                                guard.get("z").getAsDouble()))
                > 0.0625)
            throw error("WORLD_ACTION_CONTEXT_CHANGED", "Player moved during semantic gesture");
        if (guard.get("width").getAsInt() != client.getWindow().getWidth()
                || guard.get("height").getAsInt() != client.getWindow().getHeight()
                || guard.get("selectedSlot").getAsInt() != client.player.getInventory().selected)
            throw error("WORLD_ACTION_CONTEXT_CHANGED", "Viewport or selected slot changed");
        ItemStack offhand = items.get(guard.get("offhandRef").getAsString());
        if (offhand == null || !ItemStack.matches(offhand, client.player.getOffhandItem()))
            throw error("WORLD_ACTION_CONTEXT_CHANGED", "Offhand changed or context expired");
        ItemStack expected = items.get(guard.get("itemRef").getAsString());
        if (expected == null || !ItemStack.matches(expected, client.player.getMainHandItem()))
            throw error("WORLD_ACTION_CONTEXT_CHANGED", "Held item changed or context expired");
    }

    JsonObject frame(
            String ref,
            String action,
            JsonObject guard,
            boolean press,
            JsonObject options,
            java.util.function.Function<JsonObject, JsonObject> dispatch) {
        validate(ref, guard);
        Minecraft client = Minecraft.getInstance();
        Target target = valid(ref);
        if (action.equals("mine") && target.block == null)
            throw error("WORLD_BLOCK_TARGET_REQUIRED", "Mining requires a block target");
        if (action.equals("place")
                && (target.block == null
                        || !(client.player.getMainHandItem().getItem() instanceof BlockItem)))
            throw error(
                    "WORLD_PLACE_ITEM_REQUIRED",
                    "Placement requires a block target and held block item");
        if (action.equals("use_hold")) {
            if (useHand(options) == InteractionHand.OFF_HAND
                    && !client.player.getMainHandItem().isEmpty())
                throw error(
                        "WORLD_USE_HAND_CONFLICT",
                        "Off-hand sustained use requires an empty main hand; native hand priority is not bypassed");
            if (client.player.getItemInHand(useHand(options)).getUseDuration(client.player) <= 0)
                throw error(
                        "WORLD_USE_ITEM_REQUIRED",
                        "Selected hand item does not support sustained use");
        }
        if (client.options.smoothCamera)
            throw error(
                    "WORLD_AIM_UNSUPPORTED",
                    "Disable cinematic camera for deterministic semantic aim");
        if (guard == null) {
            // Input sequences are serialized: retain only this gesture's two held-stack copies.
            items.clear();
            guard = new JsonObject();
            guard.addProperty("x", client.player.getX());
            guard.addProperty("y", client.player.getY());
            guard.addProperty("z", client.player.getZ());
            guard.addProperty("itemRef", items.add(client.player.getMainHandItem().copy()));
            guard.addProperty("offhandRef", items.add(client.player.getOffhandItem().copy()));
            guard.addProperty("selectedSlot", client.player.getInventory().selected);
            guard.addProperty("width", client.getWindow().getWidth());
            guard.addProperty("height", client.getWindow().getHeight());
            guard.add("options", options.deepCopy());
            guard.addProperty("action", action);
        }
        Vec3 point = aimPoint(target, options);
        Vec3 delta = point.subtract(client.player.getEyePosition());
        if (delta.lengthSqr() > 32 * 32)
            throw error("WORLD_TARGET_OUT_OF_REACH", "Target left bounded USER radius");
        ((WorldPickInvoker) client.gameRenderer).minecraftProtocolProbe$pick(1.0F);
        HitResult actualHit = actionHit(action);
        boolean hit = matches(target, action, options, actualHit);
        double[] motion =
                WorldAim.delta(
                        delta.x,
                        delta.y,
                        delta.z,
                        client.player.getYRot(),
                        client.player.getXRot(),
                        client.options.sensitivity().get(),
                        false,
                        client.options.invertYMouse().get());
        double desiredYaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double yawError = ((desiredYaw - client.player.getYRot()) % 360 + 540) % 360 - 180;
        double pitchError =
                -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))
                        - client.player.getXRot();
        JsonObject result = new JsonObject();
        result.add("guard", guard.deepCopy());
        result.add(
                "actualHit",
                describeHit(
                        actualHit,
                        action.equals("use_hold")
                                ? "bounded_client_vanilla_ray"
                                : "native_interaction_pick"));
        result.addProperty("onTarget", hit);
        result.addProperty("aimError", Math.hypot(yawError, pitchError));
        result.addProperty("dx", motion[0]);
        result.addProperty("dy", motion[1]);
        result.addProperty("cameraProcessing", "vanilla_sensitivity_inversion");
        result.addProperty("outcome", "native_target_hit");
        result.addProperty(
                "aimValidation",
                action.equals("use_hold")
                        ? "bounded_client_vanilla_ray"
                        : "native_interaction_pick");
        if (press) {
            if (!hit)
                throw notHit(
                        "Fresh native pick must hit the discovered target",
                        actualHit,
                        action.equals("use_hold")
                                ? "bounded_client_vanilla_ray"
                                : "native_interaction_pick");
            if (action.equals("place")) placement(result, options);
            if (action.equals("mine") || action.equals("use_hold") || action.equals("place"))
                this.hold = new Hold(ref, action, target, guard, options);
            var mapping =
                    action.equals("attack") || action.equals("mine")
                            ? client.options.keyAttack
                            : client.options.keyUse;
            if (mapping.getKeyModifier() != net.neoforged.neoforge.client.settings.KeyModifier.NONE)
                throw error(
                        "WORLD_BINDING_UNSUPPORTED",
                        "Modified bindings require an explicit supported chord");
            InputConstants.Key key = ((KeyBindingUiAccessor) mapping).minecraftProtocolProbe$key();
            JsonObject binding = new JsonObject();
            if (key.getType() == InputConstants.Type.MOUSE) {
                int code =
                        switch (key.getValue()) {
                            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
                            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
                            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
                            default -> -1;
                        };
                if (code < 0) throw error("WORLD_BINDING_UNSUPPORTED", "Unsupported mouse binding");
                binding.addProperty("kind", "mouse");
                binding.addProperty("code", code);
            } else if (key.getType() == InputConstants.Type.KEYSYM) {
                int code = key.getValue();
                if (code < 0)
                    throw error("WORLD_BINDING_UNSUPPORTED", "Unsupported keyboard binding");
                binding.addProperty("kind", "key");
                binding.addProperty("code", code);
                binding.addProperty("scanCode", 0);
            } else
                throw error(
                        "WORLD_BINDING_UNSUPPORTED",
                        "Unbound/scancode mapping cannot be routed safely");
            // Validation, native pick and existing routed press share one client-thread admission.
            result.add("input", dispatch.apply(binding));
            result.add("binding", binding);
        }
        return result;
    }

    private static Vec3 aimPoint(Target target, JsonObject options) {
        if (target.block == null) return target.entity.get().getBoundingBox().getCenter();
        if (!options.has("face")) {
            Minecraft client = Minecraft.getInstance();
            Vec3 eye = client.player.getEyePosition();
            var points =
                    outlinePoints(
                            target.block,
                            target.state.getShape(
                                    client.level,
                                    target.block,
                                    CollisionContext.of(client.player)));
            Vec3 selected = null;
            HitResult attempted = null;
            double distance = Double.POSITIVE_INFINITY;
            for (Vec3 point : points) {
                double squared = eye.distanceToSqr(point);
                if (squared > 32 * 32 || squared >= distance) continue;
                var hit =
                        client.level.clip(
                                new ClipContext(
                                        eye,
                                        point,
                                        ClipContext.Block.OUTLINE,
                                        ClipContext.Fluid.NONE,
                                        client.player));
                attempted = hit;
                if (hit.getType() == HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(target.block)) {
                    selected = point;
                    distance = squared;
                }
            }
            if (selected == null)
                throw notHit(
                        "No unobstructed native outline candidate; press refused",
                        attempted,
                        "candidate_outline_ray");
            return selected;
        }
        double u = options.get("u").getAsDouble(), v = options.get("v").getAsDouble();
        double x = target.block.getX(), y = target.block.getY(), z = target.block.getZ();
        return switch (options.get("face").getAsString()) {
            case "up" -> new Vec3(x + u, y + 1, z + v);
            case "down" -> new Vec3(x + u, y, z + v);
            case "north" -> new Vec3(x + u, y + v, z);
            case "south" -> new Vec3(x + u, y + v, z + 1);
            case "west" -> new Vec3(x, y + v, z + u);
            case "east" -> new Vec3(x + 1, y + v, z + u);
            default -> throw error("INVALID_WORLD_ACTION", "Unknown face");
        };
    }

    static List<Vec3> outlinePoints(BlockPos position, VoxelShape shape) {
        List<Vec3> points = new ArrayList<>();
        // Use actual outline boxes, not a block-ID table or the center of a hollow union.
        // Discovery is bounded; the fresh native interaction pick remains authoritative.
        shape.forAllBoxes(
                (x0, y0, z0, x1, y1, z1) -> {
                    double x = (x0 + x1) / 2, y = (y0 + y1) / 2, z = (z0 + z1) / 2;
                    double ex = Math.min(1e-5, (x1 - x0) / 4),
                            ey = Math.min(1e-5, (y1 - y0) / 4),
                            ez = Math.min(1e-5, (z1 - z0) / 4);
                    // Face centers retain legal views over cover where a box center is occluded.
                    // A tiny inward inset gives the native ray a real intersection, not a face
                    // endpoint.
                    for (double[] p :
                            new double[][] {
                                {x, y, z},
                                {x0 + ex, y, z},
                                {x1 - ex, y, z},
                                {x, y0 + ey, z},
                                {x, y1 - ey, z},
                                {x, y, z0 + ez},
                                {x, y, z1 - ez}
                            })
                        if (points.size() < 64)
                            points.add(
                                    new Vec3(
                                            position.getX() + p[0],
                                            position.getY() + p[1],
                                            position.getZ() + p[2]));
                });
        return points;
    }

    private static HitResult actionHit(String action) {
        Minecraft client = Minecraft.getInstance();
        HitResult hit = client.hitResult;
        if (action.equals("use_hold")) {
            Vec3 eye = client.player.getEyePosition(),
                    delta = client.player.getViewVector(1).scale(32);
            HitResult block = client.player.pick(32, 1, false);
            double distance =
                    block.getType() == HitResult.Type.MISS
                            ? 1024
                            : eye.distanceToSqr(block.getLocation());
            var entity =
                    ProjectileUtil.getEntityHitResult(
                            client.player,
                            eye,
                            eye.add(delta),
                            client.player.getBoundingBox().expandTowards(delta).inflate(1),
                            e -> !e.isSpectator() && e.isPickable(),
                            distance);
            hit = entity == null ? block : entity;
        }
        return hit;
    }

    private static boolean matches(
            Target target, String action, JsonObject options, HitResult hit) {
        if (target.block != null) {
            if (!(hit instanceof BlockHitResult block)
                    || block.getType() != HitResult.Type.BLOCK
                    || !target.block.equals(block.getBlockPos())) return false;
            return !options.has("face")
                    || (block.getDirection().getName().equals(options.get("face").getAsString())
                            && block.getLocation().distanceToSqr(aimPoint(target, options))
                                    <= .000625);
        }
        return hit instanceof EntityHitResult entity && entity.getEntity() == target.entity.get();
    }

    private static Placement placement(JsonObject result, JsonObject options) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.hitResult instanceof BlockHitResult block))
            throw notHit(
                    "Placement needs a fresh block hit",
                    client.hitResult,
                    "native_interaction_pick");
        BlockPlaceContext context =
                new BlockPlaceContext(
                        client.player,
                        InteractionHand.MAIN_HAND,
                        client.player.getMainHandItem(),
                        block);
        BlockPos destination = context.getClickedPos();
        var chunk =
                client.level
                        .getChunkSource()
                        .getChunk(
                                destination.getX() >> 4,
                                destination.getZ() >> 4,
                                net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                false);
        if (chunk == null)
            throw error("WORLD_TARGET_STALE", "Placement destination chunk is not loaded");
        BlockState beforeState = chunk.getBlockState(destination);
        JsonObject beforeValue = placementState(beforeState);
        String before = beforeValue.get("block").getAsString();
        if (options.has("expectedDestinationBlockId")
                && !options.get("expectedDestinationBlockId").getAsString().equals(before))
            throw error(
                    "WORLD_PLACE_PRECONDITION_FAILED",
                    "Destination block differs from expected value");
        if (!context.canPlace())
            throw error("WORLD_PLACE_BLOCKED", "Native placement context is not replaceable");
        JsonObject at = new JsonObject();
        at.addProperty("x", destination.getX());
        at.addProperty("y", destination.getY());
        at.addProperty("z", destination.getZ());
        at.addProperty("beforeBlock", before);
        at.add("beforeState", beforeValue);
        result.add("destination", at);
        result.addProperty("requestedFace", options.get("face").getAsString());
        result.addProperty("hitU", options.get("u").getAsDouble());
        result.addProperty("hitV", options.get("v").getAsDouble());
        return new Placement(
                destination.immutable(),
                chunk,
                beforeState,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(((BlockItem) client.player.getMainHandItem().getItem()).getBlock())
                        .toString());
    }

    private static JsonObject placementState(BlockState state) {
        JsonObject value = new JsonObject();
        value.addProperty(
                "block",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(state.getBlock())
                        .toString());
        JsonObject properties = new JsonObject();
        for (Property<?> property : state.getProperties())
            placementProperty(properties, state, property);
        value.add("properties", properties);
        return value;
    }

    private static <T extends Comparable<T>> void placementProperty(
            JsonObject properties, BlockState state, Property<T> property) {
        properties.addProperty(property.getName(), property.getName(state.getValue(property)));
    }

    /** Observes only the first accepted native invocation, never simulates a second placement. */
    public void nativeUseOnResult(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            InteractionResult result) {
        Hold current = this.hold;
        if (current == null
                || !current.action.equals("place")
                || !current.invoked
                || !current.placementReceipt.pending()
                || current.placement == null
                || result == null) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()
                || current.target.world.get() != client.level
                || current.target.player.get() != client.player
                || current.target.camera.get() != client.getCameraEntity()
                || player != client.player
                || hand != InteractionHand.MAIN_HAND
                || !matches(current.target, current.action, current.options, hit)) return;
        Placement placement = current.placement;
        var chunk =
                client.level
                        .getChunkSource()
                        .getChunk(
                                placement.destination.getX() >> 4,
                                placement.destination.getZ() >> 4,
                                net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                false);
        if (chunk == null || chunk != placement.chunk) {
            current.error = "WORLD_TARGET_STALE";
            current.errorDetail = "Placement destination lifecycle changed before native receipt";
            return;
        }
        String nativeResult =
                result == InteractionResult.FAIL
                        ? "fail"
                        : result == InteractionResult.PASS
                                ? "pass"
                                : result == InteractionResult.SUCCESS
                                        ? "success"
                                        : result == InteractionResult.CONSUME ? "consume" : "other";
        current.placementReceipt.record(
                nativeResult,
                result.consumesAction(),
                placementState(chunk.getBlockState(placement.destination)));
    }

    /** Passive owner-thread monitoring; no direct use/destroy/camera mutation. */
    JsonObject progress(String ref, JsonObject guard, boolean released) {
        Hold current = this.hold;
        if (current == null
                || !current.ref.equals(ref)
                || !current.guard.get("itemRef").equals(guard.get("itemRef")))
            throw error("WORLD_GESTURE_CONTEXT_LOST", "Gesture owner ended");
        Minecraft client = Minecraft.getInstance();
        if (current.target.world.get() != client.level
                || current.target.player.get() != client.player
                || current.target.camera.get() != client.getCameraEntity())
            throw error("WORLD_TARGET_STALE", "World/player/camera changed during gesture");
        if (current.error != null)
            throw new ProtocolState.ProtocolException(
                    current.error,
                    409,
                    current.errorDetail == null
                            ? "Native semantic gesture was invalidated; held input will be released"
                            : current.errorDetail,
                    current.errorEvidence == null ? new JsonObject() : current.errorEvidence);
        if (current.action.equals("use_hold")) {
            if (client.player.isUsingItem()) {
                if (client.player.getUsedItemHand() != useHand(current.options))
                    throw error("WORLD_ACTION_CONTEXT_CHANGED", "Unexpected use hand");
                current.useSeen = true;
            } else if (current.useSeen) current.completed = true;
        }
        if (!current.completed && !released) {
            validate(ref, guard);
            ((WorldPickInvoker) client.gameRenderer).minecraftProtocolProbe$pick(1);
            HitResult actualHit = actionHit(current.action);
            if (!matches(current.target, current.action, current.options, actualHit))
                throw notHit(
                        "Aim or line of sight changed while held",
                        actualHit,
                        current.action.equals("use_hold")
                                ? "bounded_client_vanilla_ray"
                                : "native_interaction_pick");
        }
        JsonObject result = new JsonObject();
        result.addProperty("done", current.completed);
        result.addProperty("nativeCompletionObserved", current.completed);
        result.addProperty("nativeInvocationObserved", current.invoked);
        result.addProperty("holdDurationMillis", (System.nanoTime() - current.started) / 1000000);
        if (current.action.equals("mine")) {
            result.addProperty("clientBreakPredicted", current.completed);
            result.addProperty("nativeBreakChanges", current.breakChanges);
            result.addProperty("breakProvenance", "bounded_synchronous_native_destroy");
        }
        if (current.action.equals("use_hold")) {
            result.addProperty("hand", current.options.get("hand").getAsString());
            result.addProperty("nativeUseStarted", current.useSeen);
            result.addProperty("nativeUseReleaseObserved", current.useReleased);
            result.addProperty(
                    "released", released && current.useSeen && !client.player.isUsingItem());
        }
        if (current.action.equals("place") && released && !current.invoked)
            throw error(
                    "WORLD_PLACE_NOT_DISPATCHED", "No native placement invocation was observed");
        if (current.action.equals("place"))
            current.placementReceipt
                    .snapshot()
                    .entrySet()
                    .forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
        return result;
    }

    boolean permit(String kind) {
        Hold current = hold;
        if (current == null) return true;
        if (current.error != null
                || current.completed
                || (current.action.equals("place") && current.invoked)) return false;
        try {
            Minecraft client = Minecraft.getInstance();
            if (current.action.equals("use_hold")
                    && current.useSeen
                    && !client.player.isUsingItem()) {
                current.completed = true;
                return false;
            }
            if (System.nanoTime() - current.started
                    > current.options.get("holdMs").getAsLong() * 1000000L) {
                if (current.action.equals("mine")) current.error = "WORLD_MINE_TIMEOUT";
                return false;
            }
            validate(current.ref, current.guard);
            ((WorldPickInvoker) client.gameRenderer).minecraftProtocolProbe$pick(1);
            HitResult actualHit = actionHit(current.action);
            if (!matches(current.target, current.action, current.options, actualHit))
                throw notHit(
                        "Native target changed",
                        actualHit,
                        current.action.equals("use_hold")
                                ? "bounded_client_vanilla_ray"
                                : "native_interaction_pick");
            if (current.action.equals("mine")) {
                current.invoked = kind.equals("break");
                return current.invoked;
            }
            if (current.action.equals("place")) {
                if (!kind.equals("use_on") || current.invoked) return false;
                current.placement = placement(new JsonObject(), current.options);
                current.placementReceipt.begin(
                        placementState(current.placement.before), current.placement.block);
                current.invoked = true;
                return true;
            }
            if (current.action.equals("use_hold")) {
                if (kind.equals("break") || kind.equals("attack")) return false;
                if (client.player.isUsingItem()) {
                    if (client.player.getUsedItemHand() != useHand(current.options))
                        throw error(
                                "WORLD_ACTION_CONTEXT_CHANGED",
                                "Native use selected a different hand");
                    current.useSeen = true;
                }
                current.invoked = true;
                return true;
            }
            return false;
        } catch (ProtocolState.ProtocolException invalid) {
            current.error = invalid.code();
            current.errorDetail = invalid.getMessage();
            current.errorEvidence = invalid.details();
            return false;
        }
    }

    void beforeDestroy(BlockPos position) {
        Hold current = hold;
        if (current == null
                || !current.action.equals("mine")
                || !position.equals(current.target.block)) return;
        try {
            validate(current.ref, current.guard);
            if (DESTROY.get() != null)
                throw error(
                        "WORLD_GESTURE_CONTEXT_LOST",
                        "Nested native destruction cannot share ownership");
            Minecraft client = Minecraft.getInstance();
            // Vanilla replaces a broken block with its pre-destroy fluid, not necessarily air.
            BlockState replacement = client.level.getFluidState(position).createLegacyBlock();
            current.destruction =
                    new WorldBreakTrace(
                            current.target.chunk.get(),
                            position.immutable(),
                            current.target.state,
                            replacement,
                            revision(current.target.chunk.get()));
            DESTROY.set(current.destruction);
            current.destroyPending = true;
        } catch (ProtocolState.ProtocolException invalid) {
            current.error = invalid.code();
            current.errorDetail = invalid.getMessage();
            current.errorEvidence = invalid.details();
        }
    }

    void afterDestroy(BlockPos position, boolean success) {
        Hold current = hold;
        if (current == null || !current.destroyPending || !position.equals(current.target.block))
            return;
        current.destroyPending = false;
        WorldBreakTrace trace = DESTROY.get();
        DESTROY.remove();
        if (!success) return;
        Minecraft client = Minecraft.getInstance();
        var chunk =
                client.level
                        .getChunkSource()
                        .getChunk(
                                position.getX() >> 4,
                                position.getZ() >> 4,
                                net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                false);
        if (trace == current.destruction
                && chunk != null
                && trace.completed(
                        success, chunk, chunk.getBlockState(position), revision(chunk))) {
            current.completed = true;
            current.breakChanges = trace.changes();
        } else {
            current.error = "WORLD_TARGET_STALE";
            current.errorDetail = "Native destruction did not match the bounded owned transition";
        }
    }

    private static InteractionHand useHand(JsonObject options) {
        return options.has("hand") && options.get("hand").getAsString().equals("off_hand")
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }

    void usingReleased() {
        if (hold != null && hold.action.equals("use_hold")) hold.useReleased = true;
    }

    boolean holdActive() {
        return hold != null;
    }

    void end() {
        hold = null;
        DESTROY.remove();
        items.clear();
    }

    private static JsonObject describeHit(HitResult hit, String source) {
        JsonObject value = new JsonObject();
        value.addProperty("source", source);
        value.addProperty(
                "kind",
                hit == null ? "unknown" : hit.getType().name().toLowerCase(java.util.Locale.ROOT));
        if (hit != null) {
            Vec3 location = hit.getLocation();
            JsonObject position = new JsonObject();
            position.addProperty("x", location.x);
            position.addProperty("y", location.y);
            position.addProperty("z", location.z);
            value.add("position", position);
            value.addProperty(
                    "distance",
                    Minecraft.getInstance().player.getEyePosition().distanceTo(location));
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                JsonObject coordinates = new JsonObject();
                coordinates.addProperty("x", block.getBlockPos().getX());
                coordinates.addProperty("y", block.getBlockPos().getY());
                coordinates.addProperty("z", block.getBlockPos().getZ());
                value.add("block", coordinates);
                value.addProperty("face", block.getDirection().getName());
            }
            if (hit instanceof EntityHitResult entity)
                value.addProperty(
                        "entityType",
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .getKey(entity.getEntity().getType())
                                .toString());
        }
        return io.github.recrivenvi.minecraftprotocol.core.WorldHitEvidence.bounded(value);
    }

    private static ProtocolState.ProtocolException notHit(
            String message, HitResult hit, String source) {
        return new ProtocolState.ProtocolException(
                "WORLD_TARGET_NOT_HIT",
                409,
                message,
                io.github.recrivenvi.minecraftprotocol.core.WorldHitEvidence.details(
                        describeHit(hit, source)));
    }

    private static ProtocolState.ProtocolException error(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }
}
