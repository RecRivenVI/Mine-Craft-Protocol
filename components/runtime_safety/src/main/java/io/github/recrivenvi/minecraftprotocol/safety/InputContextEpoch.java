package io.github.recrivenvi.minecraftprotocol.safety;

import java.util.UUID;

/** Native owner-thread identities, deliberately independent of Screen, menu and GUI scale. */
public final class InputContextEpoch {
    private Object world;
    private Object player;
    private Object connection;
    private volatile Snapshot snapshot = new Snapshot(0, UUID.randomUUID().toString(), false);

    public record Snapshot(long generation, String opaqueId, boolean worldAvailable) {
        public Snapshot {
            if (generation < 0 || opaqueId == null || opaqueId.isBlank())
                throw new IllegalArgumentException("Invalid native input context");
        }
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    /** Called only by the native client owner, before consuming or dispatching input. */
    public Snapshot observe(Object world, Object player, Object connection) {
        if (this.world == world && this.player == player && this.connection == connection)
            return snapshot;
        this.world = world;
        this.player = player;
        this.connection = connection;
        snapshot =
                new Snapshot(
                        Math.incrementExact(snapshot.generation()),
                        UUID.randomUUID().toString(),
                        world != null && player != null && connection != null);
        return snapshot;
    }
}
