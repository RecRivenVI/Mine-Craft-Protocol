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
        JsonObject provider =
                entry(
                        "provider.mutate",
                        "providers",
                        "provider",
                        "providerId",
                        List.of(),
                        List.of("providerId", "mutation"));
        provider.getAsJsonObject("readRequest").addProperty("includeProviderData", true);
        provider.addProperty(
                "selectorBinding",
                "providerIds selects an actual registered descriptor; match its resource-scoped native revision");
        provider.addProperty(
                "mutationCatalog",
                "provider debugDeclaration references registered executable validators; provider payload documentation is required, not arbitrary object editing");
        provider.addProperty(
                "additionalAuthority",
                "provider debugDeclaration.requiredScope and independent Arm; no registration or discovery grants authority");
        entries.add(provider);
        entries.add(
                entry(
                        "player.transform.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedValue"),
                        List.of("value")));
        JsonObject transfer =
                entry(
                        "entity.transform.set",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedValue", "expectedEntityType"),
                        List.of("entityUuid", "sourceDimension", "value"));
        transfer.addProperty(
                "selectorBinding",
                "selector.entityTargets=[{dimension,entityUuid}] selects existing LIVE loaded identities, including foreign dimensions; no loading");
        transfer.addProperty(
                "destinationConstraint",
                "existing LIVE dimension and loaded entity bounds; detach ride/leash first; native side effects are not rolled back");
        entries.add(transfer);
        entries.add(
                entry(
                        "entity.vehicle.set",
                        "entities",
                        "entity_relationship",
                        "entityUuid",
                        List.of("expectedValue"),
                        List.of("entityUuid", "value", "relatedResourceVersions")));
        entries.add(
                entry(
                        "entity.leash.set",
                        "entities",
                        "entity_relationship",
                        "entityUuid",
                        List.of("expectedValue"),
                        List.of("entityUuid", "value", "relatedResourceVersions")));
        entries.add(
                entry(
                        "entity.owner.set",
                        "entities",
                        "entity_relationship",
                        "entityUuid",
                        List.of("expectedValue"),
                        List.of("entityUuid", "value", "relatedResourceVersions")));
        JsonObject ride =
                entry(
                        "player.vehicle.set",
                        "player",
                        "entity_relationship",
                        "playerUuid",
                        List.of("expectedValue"),
                        List.of("value", "relatedResourceVersions"));
        ride.addProperty("additionalMutationScopeAndArm", "entity");
        entries.add(ride);
        entries.add(
                entry(
                        "world.time.set",
                        "world",
                        "world_time",
                        "clockId",
                        List.of("expectedValue"),
                        List.of("clockId", "value")));
        entries.add(
                entry(
                        "world.weather.set",
                        "world",
                        "world_weather",
                        "server_weather",
                        List.of("expectedValue"),
                        List.of("value")));
        entries.add(
                entry(
                        "world.rule.set",
                        "world",
                        "world_rule",
                        "dimension@ruleId",
                        List.of("expectedValue"),
                        List.of("ruleId", "value")));
        JsonObject spawn =
                entry(
                        "entity.spawn",
                        "entities",
                        "entity_registry",
                        "dimension@entity_chunk:x,z",
                        List.of("expectedEntityCount"),
                        List.of("value"));
        JsonObject chunkSelector = new JsonObject();
        chunkSelector.addProperty("x", 0);
        chunkSelector.addProperty("z", 0);
        spawn.getAsJsonObject("readRequest")
                .getAsJsonObject("selector")
                .add("entityChunk", chunkSelector);
        spawn.addProperty(
                "selectorBinding",
                "selector.entityChunk={x,z} is the destination chunk, complete loaded membership, at most 4096 entities");
        entries.add(spawn);
        entries.add(
                entry(
                        "entity.remove",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedEntityType"),
                        List.of("entityUuid")));
        entries.add(
                entry(
                        "player.effect.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedValue"),
                        List.of("effectId", "value")));
        entries.add(
                entry(
                        "entity.effect.set",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedValue", "expectedEntityType"),
                        List.of("entityUuid", "effectId", "value")));
        entries.add(
                entry(
                        "entity.attribute.set",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedValue", "expectedEntityType"),
                        List.of("entityUuid", "attributeId", "value")));
        entries.add(
                entry(
                        "entity.equipment.set",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedItemId", "expectedCount", "expectedEntityType"),
                        List.of("entityUuid", "equipmentSlot")));
        entries.add(
                entry(
                        "player.state.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedValue"),
                        List.of("value")));
        entries.add(
                entry(
                        "entity.state.set",
                        "entities",
                        "entity",
                        "entityUuid",
                        List.of("expectedValue", "expectedEntityType"),
                        List.of("entityUuid", "value")));
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
        attribute.addProperty("attributeCatalog", "server.player.attributes[].typedBaseWritable");
        attribute.addProperty("valueDomain", "native_attribute_sanitizeValue");
        entries.add(attribute);
        entries.add(
                entry(
                        "player.inventory.slot.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedItemId", "expectedCount"),
                        List.of("slot")));
        entries.add(
                entry(
                        "player.equipment.set",
                        "player",
                        "player",
                        "uuid@server_authoritative",
                        List.of("expectedItemId", "expectedCount"),
                        List.of("equipmentSlot")));
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
        JsonObject blockEntitySlot =
                entry(
                        "block_entity.slot.set",
                        "block_entities",
                        "block_entity_serialized",
                        "dimension@x,y,z",
                        List.of("expectedBlockEntityType", "expectedItemId", "expectedCount"),
                        List.of("x", "y", "z", "slot"));
        blockEntitySlot
                .getAsJsonObject("readRequest")
                .addProperty("includeSerializedBlockEntities", true);
        blockEntitySlot.addProperty("targetConstraint", "loaded_native_container_block_entity");
        entries.add(blockEntitySlot);
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
        if (type.equals("entity_relationship")) {
            domains = new JsonArray();
            domains.add("player");
            domains.add("entities");
            read.add("domains", domains);
            selector.addProperty("entityRadius", 16);
            entry.addProperty(
                    "relatedVersionsField",
                    "resourceRevisionRefs of affected old/new entity_relationship participants");
        }
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
