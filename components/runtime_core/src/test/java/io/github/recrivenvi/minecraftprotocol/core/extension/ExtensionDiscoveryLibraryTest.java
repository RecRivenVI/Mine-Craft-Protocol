package io.github.recrivenvi.minecraftprotocol.core.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionDiscoveryLibraryTest {
    @TempDir Path game;

    public static class DiscoveryCode {
        public static String identity() {
            Supplier<String> name = () -> DiscoveryCode.class.getName();
            return name.get();
        }
    }

    @Test
    void generatedServiceHasItsOwnPackageAndRelocatesActualLambdaHandles() throws Exception {
        byte[] source;
        try (var input =
                DiscoveryCode.class.getResourceAsStream(
                        "ExtensionDiscoveryLibraryTest$DiscoveryCode.class")) {
            assertNotNull(input);
            source = input.readAllBytes();
        }
        var resources =
                new ClassLoader(getClass().getClassLoader()) {
                    @Override
                    public InputStream getResourceAsStream(String name) {
                        return name.equals(
                                        "io/github/recrivenvi/minecraftprotocol/loader/ExtensionDependencyLocator.class")
                                ? new ByteArrayInputStream(source)
                                : super.getResourceAsStream(name);
                    }
                };
        ExtensionDiscoveryLibrary.install(game, "1.20.1-forge", resources);
        Path library = game.resolve("mods/mine_craft_protocol-extension-discovery.jar");
        byte[] first = Files.readAllBytes(library);
        String generated;
        try (var jar = new JarFile(library.toFile())) {
            try (var service =
                    jar.getInputStream(
                            jar.getJarEntry(
                                    "META-INF/services/net.minecraftforge.forgespi.locating.IDependencyLocator"))) {
                generated =
                        new String(service.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                .trim();
            }
            assertNotNull(jar.getJarEntry(generated.replace('.', '/') + ".class"));
            assertNull(
                    jar.getJarEntry(
                            "io/github/recrivenvi/minecraftprotocol/loader/ExtensionDependencyLocator.class"));
        }
        try (var code =
                new URLClassLoader(
                        new java.net.URL[] {library.toUri().toURL()},
                        getClass().getClassLoader())) {
            var relocated = Class.forName(generated, true, code);
            assertNotEquals(DiscoveryCode.class.getPackageName(), relocated.getPackageName());
            assertEquals(generated, relocated.getMethod("identity").invoke(null));
        }
        ExtensionDiscoveryLibrary.install(game, "1.20.1-forge", resources);
        assertArrayEquals(first, Files.readAllBytes(library));
    }
}
