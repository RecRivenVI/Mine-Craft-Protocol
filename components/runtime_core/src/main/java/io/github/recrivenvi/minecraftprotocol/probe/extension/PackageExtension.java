package io.github.recrivenvi.minecraftprotocol.probe.extension;

/** Entry point of locally approved code. Initialization only stages registrations. */
@FunctionalInterface
public interface PackageExtension {
    void initialize(PackageContext context) throws Exception;
}
