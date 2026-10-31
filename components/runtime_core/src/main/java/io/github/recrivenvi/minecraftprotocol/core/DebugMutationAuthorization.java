package io.github.recrivenvi.minecraftprotocol.core;

/**
 * Authenticated, owner-thread authorization barrier for one typed Debug mutation. The returned
 * permit remains held until the mutation and its immediate snapshot complete.
 */
public interface DebugMutationAuthorization {
    Permit authorize(
            String currentWorldFingerprint, String sessionEpoch, String domain, String namespace);

    /** One cancellation barrier for all directly mutated namespaces; never nested permits. */
    default Permit authorizeAll(
            String currentWorldFingerprint,
            String sessionEpoch,
            java.util.Map<String, String> domains) {
        if (domains.size() == 1) {
            var entry = domains.entrySet().iterator().next();
            return authorize(
                    currentWorldFingerprint, sessionEpoch, entry.getKey(), entry.getValue());
        }
        throw new ProtocolState.ProtocolException(
                "CAPABILITY_UNAVAILABLE",
                409,
                "Authorization implementation does not support a combined namespace permit");
    }

    boolean hasScope(String scope);

    String principalId();

    String debugArmId();

    boolean isCancelled();

    default io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources.Scope
            resources(String providerId) {
        throw new UnsupportedOperationException("No installed extension resource owner");
    }

    @FunctionalInterface
    interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
