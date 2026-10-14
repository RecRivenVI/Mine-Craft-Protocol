package io.github.recrivenvi.minecraftprotocol.core;

/**
 * Authenticated, owner-thread authorization barrier for one typed Debug mutation. The returned
 * permit remains held until the mutation and its immediate snapshot complete.
 */
public interface DebugMutationAuthorization {
    Permit authorize(
            String currentWorldFingerprint, String sessionEpoch, String domain, String namespace);

    boolean hasScope(String scope);

    String principalId();

    String debugArmId();

    boolean isCancelled();

    @FunctionalInterface
    interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
