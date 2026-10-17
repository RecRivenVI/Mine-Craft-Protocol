package io.github.recrivenvi.minecraftprotocol.safety;

/** Bounded synchronous native-destroy provenance, never permission to mutate a world. */
public final class WorldBreakTrace {
    public static final int MAX_CHANGES = 256;
    private final Object chunk, position, before, replacement;
    private final long revision;
    private int changes, chunkChanges, targetChanges;
    private boolean invalid;

    public WorldBreakTrace(
            Object chunk, Object position, Object before, Object replacement, long revision) {
        this.chunk = chunk;
        this.position = position;
        this.before = before;
        this.replacement = replacement;
        this.revision = revision;
    }

    public void changed(Object chunk, Object position, Object before, Object after) {
        if (++changes > MAX_CHANGES) invalid = true;
        if (chunk != this.chunk) return;
        chunkChanges++;
        if (this.position.equals(position)) {
            targetChanges++;
            if (this.before != before || this.replacement != after) invalid = true;
        }
    }

    public boolean completed(boolean success, Object chunk, Object state, long revision) {
        return success
                && !invalid
                && chunk == this.chunk
                && state == replacement
                && targetChanges == 1
                && revision - this.revision == chunkChanges;
    }

    public int changes() {
        return changes;
    }
}
