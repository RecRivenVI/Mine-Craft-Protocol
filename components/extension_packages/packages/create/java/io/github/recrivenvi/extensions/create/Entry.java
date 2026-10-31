package io.github.recrivenvi.extensions.create;

import static io.github.recrivenvi.extensions.create.support.NativeAccess.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.extensions.create.support.ReadProvider;
import io.github.recrivenvi.minecraftprotocol.probe.extension.*;
import java.util.*;

/** Read-only machine diagnostics and ordinary client parts; never invokes assemble/disassemble. */
public final class Entry implements PackageExtension, WorldAdapter {
    private String runtime;

    public void initialize(PackageContext context) {
        var scope = context.processResources();
        runtime = scope.owner().runtimeId();
        io.github.recrivenvi.extensions.create.nativebridge.NativeHooks.open(runtime);
        scope.own(
                "passive-interaction-counter",
                new ExtensionResources.ThreadOwner("detached_cleanup", Runnable::run, () -> true),
                () ->
                        io.github.recrivenvi.extensions.create.nativebridge.NativeHooks.close(
                                runtime));
        ReadProvider.register(
                context,
                "machine",
                "server_thread",
                io.github.recrivenvi.extensions.create.support.NativeAccess::blockQuery,
                query -> machine(context, query));
        if (context.side() == PackageContext.Side.CLIENT) context.worldAdapter(this);
    }

    public Descriptor descriptor() {
        return new Descriptor("create:parts", 1, 250);
    }

    public boolean matches(Frame frame) {
        return frame.parentKind().equals("block");
    }

    public Projection capture(Context context) {
        JsonObject snapshot = context.nativeSnapshot();
        if (!snapshot.get("block").getAsString().equals("create:mechanical_bearing"))
            return new Projection(context.frame(), System.nanoTime(), List.of());
        double x = snapshot.get("x").getAsDouble(),
                y = snapshot.get("y").getAsDouble(),
                z = snapshot.get("z").getAsDouble();
        var part =
                new Part(
                        "bearing_body",
                        "1",
                        "Mechanical bearing",
                        new Bounds(x, y, z, x + 1, y + 1, z + 1),
                        new Point(x + .5, y + .5, z + .5),
                        0,
                        Map.of("coverage", "ordinary_native_parent"));
        return new Projection(context.frame(), System.nanoTime(), List.of(part));
    }

    private JsonObject machine(PackageContext context, JsonObject query) {
        Object entity = loadedBlockEntity(context, query);
        var result = new JsonObject();
        result.addProperty("available", entity != null);
        result.addProperty("chunkLoadRequested", false);
        result.add(
                "nativeInteraction",
                io.github.recrivenvi.extensions.create.nativebridge.NativeHooks.snapshot(runtime));
        result.addProperty("dimension", "minecraft:overworld");
        if (entity == null) return result;
        result.addProperty("nativeType", entity.getClass().getName());
        if (!entity.getClass().getName().startsWith("com.simibubi.create.")) {
            result.addProperty("supported", false);
            return result;
        }
        result.addProperty("supported", true);
        var actors = new JsonArray();
        Object server = context.nativeContext("server_thread");
        for (Object player : (List<?>) call(call(server, "getPlayerList"), "getPlayers")) {
            if (actors.size() >= 8) break;
            var actor = new JsonObject();
            actor.addProperty("uuid", Objects.toString(call(player, "getUUID")));
            actor.addProperty(
                    "handEmpty",
                    Boolean.TRUE.equals(call(call(player, "getMainHandItem"), "isEmpty")));
            actor.addProperty("sneaking", Boolean.TRUE.equals(call(player, "isShiftKeyDown")));
            actor.addProperty("mayBuild", Boolean.TRUE.equals(call(player, "mayBuild")));
            actors.add(actor);
        }
        result.add("actors", actors);
        result.addProperty("speedRpm", ((Number) call(entity, "getSpeed")).doubleValue());
        result.addProperty("overStressed", Boolean.TRUE.equals(call(entity, "isOverStressed")));
        if (entity.getClass().getName().endsWith("MechanicalBearingBlockEntity")) {
            result.addProperty("running", Boolean.TRUE.equals(call(entity, "isRunning")));
            result.addProperty(
                    "angle", ((Number) call(entity, "getInterpolatedAngle", 1F)).doubleValue());
            result.addProperty(
                    "assemblyException",
                    Objects.toString(call(entity, "getLastAssemblyException")));
            Object moved = call(entity, "getMovedContraption");
            result.addProperty("hasMovedContraption", moved != null);
            if (moved != null) {
                result.addProperty("contraptionUuid", Objects.toString(call(moved, "getUUID")));
                Object contraption = call(moved, "getContraption");
                var parts = new JsonArray();
                if (contraption != null)
                    for (var item : ((Map<?, ?>) call(contraption, "getBlocks")).entrySet()) {
                        if (parts.size() >= 64) {
                            result.addProperty("partsTruncated", true);
                            break;
                        }
                        var part = new JsonObject();
                        part.addProperty("localPosition", item.getKey().toString());
                        part.addProperty("state", Objects.toString(call(item.getValue(), "state")));
                        parts.add(part);
                    }
                result.add("contraptionParts", parts);
                result.addProperty("partAuthority", "DEBUG_SERVER_INTERNAL");
            }
        }
        return result;
    }
}
