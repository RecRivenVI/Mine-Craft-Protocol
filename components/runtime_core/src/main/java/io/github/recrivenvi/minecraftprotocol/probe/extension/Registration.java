package io.github.recrivenvi.minecraftprotocol.probe.extension;

/** Owns one contribution, not loaded bytecode. Closing a handle cannot uninstall a Mixin. */
public interface Registration extends AutoCloseable {
    boolean active();

    @Override
    void close();
}
