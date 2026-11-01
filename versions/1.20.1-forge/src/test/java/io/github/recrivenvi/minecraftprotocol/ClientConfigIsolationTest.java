package io.github.recrivenvi.minecraftprotocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

final class ClientConfigIsolationTest {
    @Test
    void commonEntrypointLinksWithoutAnyClientClasses() throws Exception {
        String entrypoint = ProtocolIdentity.class.getPackageName() + ".ForgeProtocolMod";
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader dedicated =
                new ClassLoader(parent) {
                    @Override
                    protected Class<?> loadClass(String name, boolean resolve)
                            throws ClassNotFoundException {
                        if (name.startsWith("net.minecraft.client.")
                                || name.startsWith("net.minecraftforge.client.")
                                || name.startsWith("net.neoforged.neoforge.client.")
                                || name.startsWith(
                                        "io.github.recrivenvi.minecraftprotocol.client."))
                            throw new ClassNotFoundException(
                                    "Client class unavailable on dedicated server: " + name);
                        if (!name.equals(entrypoint)) return super.loadClass(name, resolve);
                        synchronized (getClassLoadingLock(name)) {
                            Class<?> loaded = findLoadedClass(name);
                            if (loaded == null) {
                                try (InputStream input =
                                        parent.getResourceAsStream(
                                                name.replace('.', '/') + ".class")) {
                                    if (input == null) throw new ClassNotFoundException(name);
                                    byte[] bytes = input.readAllBytes();
                                    loaded = defineClass(name, bytes, 0, bytes.length);
                                } catch (IOException error) {
                                    throw new ClassNotFoundException(name, error);
                                }
                            }
                            if (resolve) resolveClass(loaded);
                            return loaded;
                        }
                    }
                };
        assertTrue(
                Class.forName(entrypoint, false, dedicated).getDeclaredConstructors().length > 0);
    }
}
