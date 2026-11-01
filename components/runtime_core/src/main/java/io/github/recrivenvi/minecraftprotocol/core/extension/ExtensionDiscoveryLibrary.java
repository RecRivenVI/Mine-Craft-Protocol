package io.github.recrivenvi.minecraftprotocol.core.extension;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;

/**
 * A tiny Loader-native service is derived locally; the installed product JAR remains an ordinary
 * mod.
 */
public final class ExtensionDiscoveryLibrary {
    private ExtensionDiscoveryLibrary() {}

    public static void install(Path game, String target, ClassLoader loader) {
        if (target.endsWith("-fabric")) return;
        String service =
                target.endsWith("-forge")
                        ? "net.minecraftforge.forgespi.locating.IDependencyLocator"
                        : "net.neoforged.neoforgespi.locating.IDependencyLocator";
        String sourceType =
                "io.github.recrivenvi.minecraftprotocol.loader.ExtensionDependencyLocator";
        String type =
                "io.github.recrivenvi.minecraftprotocol.extension_discovery.ExtensionDependencyLocator";
        try {
            byte[] code;
            try (var input = loader.getResourceAsStream(sourceType.replace('.', '/') + ".class")) {
                if (input == null) return;
                code = relocate(input.readAllBytes(), type);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var output = new JarOutputStream(bytes)) {
                Manifest manifest = new Manifest();
                var attributes = manifest.getMainAttributes();
                attributes.putValue("Manifest-Version", "1.0");
                attributes.putValue("FMLModType", "LIBRARY");
                attributes.putValue(
                        "Automatic-Module-Name",
                        "io.github.recrivenvi.minecraftprotocol.extension_discovery");
                var data = new ByteArrayOutputStream();
                manifest.write(data);
                put(output, "META-INF/MANIFEST.MF", data.toByteArray());
                put(output, type.replace('.', '/') + ".class", code);
                put(
                        output,
                        "META-INF/services/" + service,
                        (type + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Path file = game.resolve("mods/mine_craft_protocol-extension-discovery.jar");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(file))
                    throw new IOException("Invalid discovery cache");
                if (!java.util.Arrays.equals(Files.readAllBytes(file), bytes.toByteArray()))
                    throw new IOException(
                            "Discovery bridge changed: replace the derived cache while this instance is stopped");
                return;
            }
            Files.createDirectories(file.getParent());
            try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
                bytes.writeTo(output);
            }
        } catch (IOException error) {
            throw new ExtensionFailure(
                    "EXTENSION_DISCOVERY_UNAVAILABLE",
                    "Cannot prepare early discovery for the next restart",
                    error);
        }
    }

    private static byte[] relocate(byte[] source, String type) {
        var input = new org.objectweb.asm.ClassReader(source);
        var output = new org.objectweb.asm.ClassWriter(0);
        // Forge includes service libraries in its module graph. A distinct package prevents
        // the service and product modules from exporting the same package; handles/lambdas must
        // follow the class, not only its declaration.
        var names =
                new org.objectweb.asm.commons.Remapper() {
                    @Override
                    public String map(String name) {
                        return name.equals(input.getClassName()) ? type.replace('.', '/') : name;
                    }
                };
        input.accept(new org.objectweb.asm.commons.ClassRemapper(output, names), 0);
        return output.toByteArray();
    }

    private static void put(JarOutputStream output, String name, byte[] bytes) throws IOException {
        var entry = new JarEntry(name);
        entry.setTime(0);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    /** Invoked in an isolated loader from the current product bytes, not a copied Runtime. */
    public static Path prepare(
            Path game,
            String productId,
            String target,
            String side,
            java.util.Map<String, String> mods)
            throws IOException {
        var env =
                new ExtensionPlatform.Environment(
                        productId,
                        target,
                        io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext.Side
                                .valueOf(side),
                        Runtime.version().feature(),
                        mods,
                        ExtensionDiscoveryLibrary.class.getClassLoader());
        return ExtensionEarlyStage.refresh(game, env);
    }
}
