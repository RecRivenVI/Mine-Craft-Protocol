package io.github.recrivenvi.minecraftprotocol.core;

/** Passive native reset identity, independent of the Toast's subsequent lifetime clock. */
public interface UiToastResetSource {
    long minecraftProtocolProbe$resetGeneration();
}
