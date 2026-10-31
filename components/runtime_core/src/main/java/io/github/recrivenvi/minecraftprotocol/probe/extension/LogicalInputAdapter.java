package io.github.recrivenvi.minecraftprotocol.probe.extension;

import java.util.concurrent.CompletableFuture;

/** Explicit framework cleanup; must release/cancel state and must never synthesize a press. */
public interface LogicalInputAdapter {
    String id();

    CompletableFuture<Void> reset(String reason);
}
