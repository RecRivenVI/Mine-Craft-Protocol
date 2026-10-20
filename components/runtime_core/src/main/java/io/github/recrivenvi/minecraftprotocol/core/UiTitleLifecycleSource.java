package io.github.recrivenvi.minecraftprotocol.core;

/** Native local title setters change this generation; ordinary countdown ticks do not. */
public interface UiTitleLifecycleSource {
    long minecraftProtocolProbe$titleLifecycleGeneration();
}
