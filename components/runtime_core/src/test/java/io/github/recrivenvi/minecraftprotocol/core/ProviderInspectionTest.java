package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderInspectionTest {
    @Test
    void actualPublishedDescriptorIncludesIndependentMutationContractNotOnlyASupportBit() {
        var descriptor = descriptor(true, "provider_revision", "resource", false);
        var published = ProviderInspection.descriptor(descriptor);
        var debug = published.getAsJsonObject("debugDeclaration");
        assertTrue(published.get("debugSupported").getAsBoolean());
        assertEquals(descriptor.revisionSchema(), published.get("revisionSchema").getAsString());
        assertEquals(descriptor.revisionScope(), published.get("revisionScope").getAsString());
        assertEquals(
                descriptor.debugDeclaration().mutationSchema(),
                debug.get("mutationSchema").getAsString());
        assertEquals(
                descriptor.debugDeclaration().resultSchema(),
                debug.get("resultSchema").getAsString());
        assertEquals("debug.provider.extra", debug.get("requiredScope").getAsString());
        assertTrue(debug.get("requiresArm").getAsBoolean());
        assertTrue(debug.get("supportsResourceVersionPrecondition").getAsBoolean());
        assertFalse(debug.get("grantsAuthority").getAsBoolean());
        assertFalse(debug.has("readOnlyReason"));
        assertFalse(debug.has("unavailableReason"));
        debug.addProperty("requiredScope", "forged");
        assertEquals(
                "debug.provider.extra",
                ProviderInspection.descriptor(descriptor)
                        .getAsJsonObject("debugDeclaration")
                        .get("requiredScope")
                        .getAsString());
    }

    @Test
    void declaredSupportIsNotConfusedWithWritableRevisionOrReadPolicy() {
        var readOnly =
                ProviderInspection.descriptor(
                        descriptor(false, "provider_revision", "resource", false));
        assertFalse(readOnly.get("debugSupported").getAsBoolean());
        assertEquals(
                "provider_does_not_declare_typed_mutation",
                readOnly.getAsJsonObject("debugDeclaration").get("readOnlyReason").getAsString());
        var queryView =
                ProviderInspection.descriptor(
                        descriptor(true, "snapshot_change_sequence", "query_view", false));
        assertTrue(queryView.get("debugSupported").getAsBoolean());
        assertEquals(
                "provider_has_no_mutation_eligible_native_resource_revision",
                queryView.getAsJsonObject("debugDeclaration").get("readOnlyReason").getAsString());
        var unavailable =
                ProviderInspection.descriptor(
                        descriptor(true, "provider_revision", "resource", true));
        assertEquals(
                "provider_read_policy_cannot_issue_a_mutation_precondition",
                unavailable
                        .getAsJsonObject("debugDeclaration")
                        .get("unavailableReason")
                        .getAsString());
    }

    private static AgentDataProviderV2.Descriptor descriptor(
            boolean supported, String revisionSource, String revisionScope, boolean load) {
        return new AgentDataProviderV2.Descriptor(
                "inspection:example",
                "1",
                List.of("snapshot"),
                "inspection://snapshot",
                "inspection://query",
                "server_thread",
                List.of("server_authoritative"),
                "none",
                "bounded",
                !load,
                false,
                load,
                false,
                false,
                revisionSource,
                revisionScope,
                "inspection://revision",
                revisionScope.equals("resource"),
                "none",
                new AgentDataProviderV2.DebugDeclaration(
                        supported,
                        "inspection://mutation",
                        "inspection://result",
                        "debug.provider.extra",
                        true,
                        supported,
                        supported ? "must_advance" : "none",
                        "provider_declared"),
                List.of("read", "debug.world.read"));
    }
}
