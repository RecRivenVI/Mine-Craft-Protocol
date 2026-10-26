package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;

/**
 * Public declaration, not current authorization or a substitute for provider payload documentation.
 */
public final class ProviderInspection {
    private ProviderInspection() {}

    public static JsonObject descriptor(AgentDataProviderV2.Descriptor descriptor) {
        JsonObject json = new JsonObject();
        json.addProperty("providerId", descriptor.providerId());
        json.addProperty("schemaVersion", descriptor.schemaVersion());
        json.addProperty("snapshotSchema", descriptor.snapshotSchema());
        json.addProperty("querySchema", descriptor.querySchema());
        json.addProperty("threadAffinity", descriptor.threadAffinity());
        json.addProperty("readEffects", descriptor.readEffects());
        json.addProperty("completeness", descriptor.completeness());
        json.addProperty("snapshotSafe", descriptor.snapshotSafe());
        json.addProperty("mayInitialize", descriptor.mayInitialize());
        json.addProperty("mayLoadData", descriptor.mayLoadData());
        json.addProperty("mayAccessStorage", descriptor.mayAccessStorage());
        json.addProperty("mayMutate", descriptor.mayMutate());
        json.addProperty("revisionSource", descriptor.revisionSource());
        json.addProperty("revisionScope", descriptor.revisionScope());
        json.addProperty("revisionSchema", descriptor.revisionSchema());
        json.addProperty("revisionQueryInvariant", descriptor.revisionQueryInvariant());
        json.addProperty("deltaCapability", descriptor.deltaCapability());
        JsonArray capabilities = new JsonArray(),
                perspectives = new JsonArray(),
                scopes = new JsonArray();
        descriptor.capabilities().forEach(capabilities::add);
        descriptor.perspectives().forEach(perspectives::add);
        descriptor.requiredScopes().forEach(scopes::add);
        json.add("capabilities", capabilities);
        json.add("perspectives", perspectives);
        json.add("requiredScopes", scopes);
        var declaration = descriptor.debugDeclaration();
        json.addProperty("debugSupported", declaration.supported());
        JsonObject debug = new JsonObject();
        debug.addProperty("supported", declaration.supported());
        debug.addProperty("mutationSchema", declaration.mutationSchema());
        debug.addProperty("resultSchema", declaration.resultSchema());
        debug.addProperty("requiredScope", declaration.requiredScope());
        debug.addProperty("requiresArm", declaration.requiresArm());
        debug.addProperty(
                "supportsResourceVersionPrecondition",
                declaration.supportsResourceVersionPrecondition());
        debug.addProperty("revisionBehavior", declaration.revisionBehavior());
        debug.addProperty("synchronizationBehavior", declaration.synchronizationBehavior());
        debug.addProperty("grantsAuthority", false);
        if (!declaration.supported())
            debug.addProperty("readOnlyReason", "provider_does_not_declare_typed_mutation");
        else if (!"provider_revision".equals(descriptor.revisionSource())
                || !"resource".equals(descriptor.revisionScope()))
            debug.addProperty(
                    "readOnlyReason", "provider_has_no_mutation_eligible_native_resource_revision");
        else if (descriptor.mayLoadData()
                || descriptor.mayAccessStorage()
                || descriptor.mayMutate())
            debug.addProperty(
                    "unavailableReason",
                    "provider_read_policy_cannot_issue_a_mutation_precondition");
        json.add("debugDeclaration", debug);
        json.addProperty("status", "registered");
        return json;
    }
}
