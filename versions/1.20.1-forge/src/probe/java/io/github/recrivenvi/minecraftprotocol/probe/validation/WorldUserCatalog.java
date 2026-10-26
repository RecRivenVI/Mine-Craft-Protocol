package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureElement;

/** Version-bound native registry inventory; not a product capability or gameplay acceptance. */
public final class WorldUserCatalog {
    private static final String TARGET = "1.20.1-forge";

    private WorldUserCatalog() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.worldUserCatalog")
                && "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"));
    }

    public static void capture(Button button) {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null || client.player == null)
            throw new IllegalStateException("Catalog requires the current native client world");
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (!root.endsWith(
                            Path.of(
                                    "validations",
                                    "conformance-world_read",
                                    "instance",
                                    TARGET,
                                    "client"))
                    || !root.toRealPath().equals(root))
                throw new IllegalStateException("Catalog is outside its owned validation copy");
            JsonObject owner =
                    JsonParser.parseString(
                                    Files.readString(
                                            root.resolve("world-read-owner.json"),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (!TARGET.equals(owner.get("target").getAsString())
                    || !"world-read-classification".equals(owner.get("owner").getAsString()))
                throw new IllegalStateException("Catalog ownership mismatch");
            JsonObject report = new JsonObject();
            report.addProperty("target", TARGET);
            report.addProperty("source", "actual_native_registries_and_key_mappings");
            report.addProperty("coverageClaimed", false);
            report.addProperty("worldMutation", false);
            report.add(
                    "items",
                    registry(
                            BuiltInRegistries.ITEM,
                            client,
                            Set.of(
                                    "use",
                                    "useOn",
                                    "interactLivingEntity",
                                    "releaseUsing",
                                    "finishUsingItem",
                                    "onUseTick",
                                    "hurtEnemy",
                                    "mineBlock",
                                    "getUseAnimation",
                                    "getUseDuration")));
            report.add(
                    "blocks",
                    registry(
                            BuiltInRegistries.BLOCK,
                            client,
                            Set.of(
                                    "use",
                                    "useItemOn",
                                    "useWithoutItem",
                                    "attack",
                                    "playerWillDestroy",
                                    "playerDestroy",
                                    "getStateForPlacement")));
            report.add("entities", registry(BuiltInRegistries.ENTITY_TYPE, client, Set.of()));
            JsonArray bindings = new JsonArray();
            for (var mapping : client.options.keyMappings) {
                var key =
                        ((io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor)
                                        mapping)
                                .minecraftProtocolProbe$key();
                JsonObject entry = new JsonObject();
                entry.addProperty("action", mapping.getName());
                entry.addProperty("binding", key.getName());
                entry.addProperty("type", key.getType().toString());
                entry.addProperty("nativeValue", key.getValue());
                bindings.add(entry);
            }
            report.add("bindings", bindings);
            Files.writeString(
                    root.resolve("world-user-catalog.json"), report + "\n", StandardCharsets.UTF_8);
            button.setMessage(Component.literal("Native World Catalog Ready (Fixture)"));
            button.active = false;
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static <T> JsonArray registry(
            Registry<T> registry, Minecraft client, Set<String> hooks) {
        var rows = new TreeMap<String, JsonObject>();
        for (T value : registry) {
            String id = registry.getKey(value).toString();
            if (!id.startsWith("minecraft:")) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("nativeClass", value.getClass().getName());
            if (value instanceof net.minecraft.world.item.Item item) {
                var stack = item.getDefaultInstance();
                row.addProperty("nativeUseAnimation", stack.getUseAnimation().name());
                row.addProperty("nativeUseDuration", stack.getUseDuration());
                row.addProperty("edible", item.isEdible());
            }
            if (value instanceof FeatureElement feature)
                row.addProperty("enabled", feature.isEnabled(client.level.enabledFeatures()));
            JsonArray methods = new JsonArray();
            Set<String> seen = new HashSet<>();
            for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
                var declared = type.getDeclaredMethods();
                Arrays.sort(
                        declared,
                        java.util.Comparator.comparing(java.lang.reflect.Method::toGenericString));
                for (var method : declared) {
                    if (!hooks.contains(method.getName())
                            || method.isBridge()
                            || method.isSynthetic()) continue;
                    String signature =
                            method.getName() + Arrays.toString(method.getParameterTypes());
                    if (!seen.add(signature)) continue;
                    JsonObject entry = new JsonObject();
                    entry.addProperty("signature", signature);
                    entry.addProperty("owner", type.getName());
                    methods.add(entry);
                }
            }
            row.add("nativeHookOwners", methods);
            rows.put(id, row);
        }
        JsonArray result = new JsonArray();
        rows.values().forEach(result::add);
        return result;
    }
}
