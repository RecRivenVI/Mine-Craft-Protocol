package io.github.recrivenvi.minecraftprotocol.api.extension;

/** Entry point of locally approved code. Initialization only stages registrations. */
@FunctionalInterface
public interface Extension {
    void initialize(ExtensionContext context) throws Exception;
}
