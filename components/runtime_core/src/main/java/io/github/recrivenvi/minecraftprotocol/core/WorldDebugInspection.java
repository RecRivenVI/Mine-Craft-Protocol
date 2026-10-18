package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Discovery for existing typed mutations; references are observations, never authority grants. */
public final class WorldDebugInspection {
    private WorldDebugInspection() {}

    public static JsonObject contract() {
        JsonObject result = new JsonObject();
        result.addProperty("type", "debug.resource_inspection.contract");
        result.addProperty("path", "/v0/observe/deep");
        result.addProperty("method", "POST");
        result.addProperty("dataCategory", "DEBUG");
        result.addProperty("perspective", "server_authoritative");
        result.addProperty("resourceVersionsField", "resourceRevisionRefs");
        result.addProperty("mutationPreconditionField", "expectedResourceVersion");
        result.addProperty("requiresMutationPreconditionEligible", true);
        result.addProperty("requiredRevisionScope", "resource");
        result.addProperty("requiredReadScopes", "read debug debug.world.read");
        result.addProperty("inspectionRequiresArm", false);
        result.addProperty("grantsMutationPermission", false);
        result.addProperty("readWriteAtomic", false);
        result.addProperty("staleRecovery", "reinspect_then_reconsider_explicit_value_conditions");
        result.addProperty("automaticRetry", false);
        result.addProperty("requiresCurrentWorldFingerprint", true);
        result.addProperty("mutationRequiresIndependentScopeAndArm", true);
        JsonArray entries = new JsonArray();
        entries.add(
                entry(
                        "player.health.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedHealth"),
                        List.of()));
        JsonObject attribute =
                entry(
                        "player.attribute.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedValue"),
                        List.of("attributeId"));
        attribute.addProperty("supportedAttribute", "minecraft:max_health");
        entries.add(attribute);
        entries.add(
                entry(
                        "entity.no_gravity.set",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedEntityType", "expectedNoGravity"),
                        List.of("entityUuid")));
        entries.add(
                entry(
                        "world.block.set",
                        "blocks",
                        "block",
                        "dimension@x,y,z",
                        List.of("expectedBlockId", "expectedProperties"),
                        List.of("x", "y", "z")));
        JsonObject blockEntity =
                entry(
                        "block_entity.custom_name.set",
                        "block_entities",
                        "block_entity_serialized",
                        "dimension@x,y,z",
                        List.of("expectedBlockEntityType", "expectedCustomName"),
                        List.of("x", "y", "z"));
        blockEntity
                .getAsJsonObject("readRequest")
                .addProperty("includeSerializedBlockEntities", true);
        blockEntity.addProperty(
                "targetConstraint", "loaded_container_block_entity_in_selected_chunks");
        entries.add(blockEntity);
        entries.add(
                entry(
                        "menu.slot.set",
                        "menu",
                        "menu",
                        "ownerUuid@menu:menuId",
                        List.of("expectedMenuId", "expectedItemId", "expectedCount"),
                        List.of("slot")));
        result.add("operations", entries);
        return result;
    }

    private static JsonObject entry(
            String operation,
            String domain,
            String type,
            String key,
            List<String> values,
            List<String> targetFields) {
        JsonObject entry = new JsonObject();
        entry.addProperty("operation", operation);
        entry.addProperty("resourceType", type);
        entry.addProperty("resourceKeyFormat", key);
        entry.addProperty(
                "requiredMutationScope", "debug." + operation.substring(0, operation.indexOf('.')));
        JsonObject read = new JsonObject();
        read.addProperty("category", "DEBUG");
        read.addProperty("perspective", "server_authoritative");
        JsonArray domains = new JsonArray();
        domains.add(domain);
        read.add("domains", domains);
        JsonObject selector = new JsonObject();
        if (domain.equals("blocks")) {
            entry.addProperty("selectorBinding", "selector.blocks contains the target {x,y,z}");
        } else if (domain.equals("entities")) {
            selector.addProperty("entityRadius", 16);
            entry.addProperty(
                    "selectorBinding", "match entityUuid within the bounded returned entities");
        } else if (domain.equals("block_entities")) {
            selector.addProperty("chunkRadius", 0);
            entry.addProperty(
                    "selectorBinding", "match {x,y,z} within the bounded returned blockEntities");
        }
        read.add("selector", selector);
        entry.add("readRequest", read);
        JsonArray fields = new JsonArray();
        targetFields.forEach(fields::add);
        entry.add("targetFields", fields);
        JsonArray conditions = new JsonArray();
        values.forEach(conditions::add);
        entry.add("optionalValuePreconditions", conditions);
        entry.addProperty("missingTarget", "unavailable_or_outside_query_not_a_write_permit");
        return entry;
    }
}
