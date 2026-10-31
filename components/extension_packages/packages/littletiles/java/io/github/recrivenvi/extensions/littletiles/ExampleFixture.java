package io.github.recrivenvi.extensions.littletiles;

import static io.github.recrivenvi.extensions.littletiles.support.NativeAccess.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.*;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit DEBUG arrangement only: upstream example into an empty hotbar in a single-player
 * fixture.
 */
final class ExampleFixture implements AgentDataProviderV2 {
    private static final String ID = "littletiles:example_fixture",
            ROOT = "minecraft-extension://littletiles/schemas/example_fixture/";
    private final PackageContext context;
    private final io.github.recrivenvi.extensions.littletiles.support.OwnerTasks tasks;
    private long revision;
    private JsonObject previous;

    ExampleFixture(PackageContext context) {
        this.context = context;
        tasks =
                new io.github.recrivenvi.extensions.littletiles.support.OwnerTasks(
                        context, "example-fixture");
        context.schema(
                ROOT + "query",
                q -> q.size() == 0 ? pass() : fail("Empty fixed-fixture query required"));
        context.schema(
                ROOT + "state",
                q ->
                        q.has("available")
                                        && q.get("available").isJsonPrimitive()
                                        && q.getAsJsonPrimitive("available").isBoolean()
                                ? pass()
                                : fail("Fixture availability required"));
        context.schema(
                ROOT + "mutation",
                q ->
                        q.keySet().equals(Set.of("operation", "variant"))
                                        && q.get("operation")
                                                .getAsString()
                                                .equals("prepare_example")
                                        && Set.of(
                                                        "original",
                                                        "right_click_enabled",
                                                        "standalone_axis")
                                                .contains(q.get("variant").getAsString())
                                ? pass()
                                : fail("Fixed example operation/variant required"));
        context.schema(
                ROOT + "result",
                q ->
                        q.has("providerRevisionBefore")
                                        && q.has("providerRevisionAfter")
                                        && q.has("revisionStateBefore")
                                        && q.has("revisionStateAfter")
                                        && q.has("before")
                                        && q.has("after")
                                ? pass()
                                : fail("Versioned fixture result required"));
        context.provider(this);
    }

    private static ProviderSchemaRegistry.ValidationResult pass() {
        return ProviderSchemaRegistry.ValidationResult.pass();
    }

    private static ProviderSchemaRegistry.ValidationResult fail(String reason) {
        return ProviderSchemaRegistry.ValidationResult.fail(reason);
    }

    public Descriptor descriptor() {
        return new Descriptor(
                ID,
                "1",
                List.of("snapshot"),
                ROOT + "state",
                ROOT + "query",
                "server_thread",
                List.of("server_authoritative"),
                "none",
                "fixed_single_player_example",
                true,
                false,
                false,
                false,
                false,
                "provider_revision",
                "resource",
                ROOT + "state",
                true,
                "none",
                new DebugDeclaration(
                        true,
                        ROOT + "mutation",
                        ROOT + "result",
                        "debug.provider",
                        true,
                        true,
                        "must_advance",
                        "native_inventory_sync"),
                List.of("debug.world.read"));
    }

    private Object player() {
        Object server = context.nativeContext("server_thread");
        var players = (List<?>) call(call(server, "getPlayerList"), "getPlayers");
        return players.size() == 1 ? players.get(0) : null;
    }

    private JsonObject state() {
        var value = new JsonObject();
        Object player = player();
        value.addProperty("available", player != null);
        if (player != null) {
            Object stack = call(call(player, "getInventory"), "getItem", 0);
            value.addProperty("playerUuid", Objects.toString(call(player, "getUUID")));
            value.addProperty("slotEmpty", Boolean.TRUE.equals(call(stack, "isEmpty")));
            value.addProperty("item", stack.toString());
            value.addProperty("stackIdentity", System.identityHashCode(stack));
        }
        return value;
    }

    private JsonObject observe() {
        var value = state();
        if (previous != null && !previous.equals(value)) revision++;
        previous = value.deepCopy();
        return value;
    }

    public CompletableFuture<JsonObject> capture(ReadContext request) {
        return tasks.submit("server_thread", request.deadlineMillis(), this::snapshot);
    }

    private JsonObject snapshot() {
        var data = observe();
        var result = new JsonObject();
        result.addProperty("schemaVersion", "1");
        result.addProperty("providerRevision", revision);
        result.add("revisionState", data.deepCopy());
        result.add("data", data);
        return result;
    }

    public CompletableFuture<JsonObject> mutate(DebugContext request) {
        try {
            JsonObject before = observe();
            long old = revision;
            if (request.expectedResourceVersion().get("revision").getAsLong() != old)
                throw new IllegalStateException("provider_stale_resource_revision");
            if (!before.get("available").getAsBoolean() || !before.get("slotEmpty").getAsBoolean())
                throw new IllegalStateException(
                        "Example fixture requires exactly one player and an empty hotbar slot 0");
            if (request.cancellationRequested().getAsBoolean()
                    || System.currentTimeMillis() >= request.deadlineMillis())
                throw new java.util.concurrent.CancellationException();
            var grant = request.resources();
            grant.requireActive();
            Object player = player();
            Class<?> registry = type(player, "team.creative.littletiles.LittleTilesRegistry");
            Object holder = field(registry, "BLUEPRINT");
            Object item =
                    holder instanceof java.util.function.Supplier<?> supplier
                            ? supplier.get()
                            : call(holder, "value");
            Object stack = call(item, "getDefaultInstance");
            String asset =
                    (context.target().equals("1.20.1-forge") ? "data" : "assets")
                            + "/littletiles/example/double_door.struct";
            byte[] bytes;
            try (var input = item.getClass().getClassLoader().getResourceAsStream(asset)) {
                if (input == null) throw new IllegalStateException("Upstream example unavailable");
                bytes = input.readNBytes(16385);
                if (bytes.length > 16384)
                    throw new IllegalStateException("Upstream example exceeds fixture budget");
            }
            Object tag =
                    call(
                            type(player, "net.minecraft.nbt.TagParser"),
                            "parseTag",
                            new String(bytes, StandardCharsets.UTF_8));
            Class<?> legacy =
                    type(
                            player,
                            "team.creative.littletiles.common.convertion.OldLittleTilesDataParser");
            Object group =
                    Boolean.TRUE.equals(call(legacy, "isOld", tag))
                            ? call(legacy, "load", tag)
                            : call(
                                    type(
                                            player,
                                            "team.creative.littletiles.common.block.little.tile.group.LittleGroup"),
                                    "load",
                                    tag);
            String variant = request.mutation().get("variant").getAsString();
            if (variant.equals("standalone_axis")) {
                // Isolate the shipped child door from its legacy activator's signal wiring.
                group = call(call(field(group, "children"), "getChild", 0), "copy");
                call(call(group, "getStructureTag"), "putBoolean", "actP", false);
            }
            if (!variant.equals("original")) {
                Object structure = call(group, "getStructureTag");
                call(structure, "putBoolean", "hand", true);
            }
            call(item, "saveTiles", stack, group);
            if (request.cancellationRequested().getAsBoolean()
                    || System.currentTimeMillis() >= request.deadlineMillis())
                throw new java.util.concurrent.CancellationException();
            grant.requireActive();
            Object current = player();
            Object currentStack = call(call(player, "getInventory"), "getItem", 0);
            if (current != player || !Boolean.TRUE.equals(call(currentStack, "isEmpty")))
                throw new IllegalStateException("provider_stale_resource_revision");
            Object inventory = call(player, "getInventory");
            call(inventory, "setItem", 0, stack);
            call(inventory, "setChanged");
            JsonObject after = state();
            revision++;
            previous = after.deepCopy();
            var result = new JsonObject();
            result.addProperty("providerRevisionBefore", old);
            result.addProperty("providerRevisionAfter", revision);
            result.add("revisionStateBefore", before.deepCopy());
            result.add("revisionStateAfter", after.deepCopy());
            result.add("before", before);
            result.add("after", after);
            result.addProperty("fixture", true);
            result.addProperty("upstreamAsset", asset);
            result.addProperty(
                    "upstreamAssetSha256",
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(bytes)));
            result.addProperty("variant", request.mutation().get("variant").getAsString());
            return CompletableFuture.completedFuture(result);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
