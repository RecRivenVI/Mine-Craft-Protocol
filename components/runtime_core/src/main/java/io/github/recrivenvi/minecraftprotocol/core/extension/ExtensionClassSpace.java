package io.github.recrivenvi.minecraftprotocol.core.extension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Private package classes with explicit dependency exports; no game-loader fallback replacement.
 */
final class ExtensionClassSpace extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    private final ExtensionManifest manifest;
    private final Map<String, byte[]> classes;
    private final Map<String, byte[]> resources;
    private final ProtectionDomain origin;
    private Map<String, ExtensionClassSpace> dependencies = Map.of();

    ExtensionClassSpace(
            ClassLoader parent,
            ExtensionStore.VerifiedPackage source,
            ExtensionManifest.Variant variant) {
        super(parent);
        manifest = source.manifest();
        Map<String, byte[]> code = new LinkedHashMap<>(), data = new LinkedHashMap<>();
        long total = 0;
        try {
            for (String file : variant.code())
                try (JarInputStream jar =
                        new JarInputStream(
                                new ByteArrayInputStream(
                                        source.read(file, ExtensionStore.MAX_CODE_BYTES)))) {
                    for (JarEntry entry; (entry = jar.getNextJarEntry()) != null; ) {
                        if (entry.isDirectory()) continue;
                        String path = entry.getName();
                        if (!path.matches("[A-Za-z0-9_$./-]+")
                                || path.startsWith("/")
                                || List.of(path.split("/")).contains(".."))
                            throw new ExtensionFailure(
                                    "EXTENSION_CODE_INVALID", "Unsafe JAR resource");
                        byte[] bytes = jar.readNBytes(8 * 1024 * 1024 + 1);
                        total += bytes.length;
                        if (bytes.length > 8 * 1024 * 1024
                                || total > ExtensionStore.MAX_CODE_BYTES
                                || data.size() >= 4096)
                            throw new ExtensionFailure(
                                    "EXTENSION_CODE_BUDGET",
                                    "Expanded code/resource budget exceeded");
                        if (path.equals("META-INF/MANIFEST.MF")) continue;
                        if (data.putIfAbsent(path, bytes) != null)
                            throw new ExtensionFailure(
                                    "EXTENSION_DUPLICATE_CLASS_OR_RESOURCE", path);
                        if (path.endsWith(".class")) {
                            String name = path.substring(0, path.length() - 6).replace('/', '.');
                            if (!name.startsWith(manifest.javaNamespace() + "."))
                                throw new ExtensionFailure(
                                        "EXTENSION_CLASS_NAMESPACE",
                                        "Do not embed SDK, game, mod or unrelated library classes: "
                                                + name);
                            if (bytes.length < 8
                                    || bytes[0] != (byte) 0xca
                                    || bytes[1] != (byte) 0xfe
                                    || bytes[2] != (byte) 0xba
                                    || bytes[3] != (byte) 0xbe)
                                throw new ExtensionFailure(
                                        "EXTENSION_CODE_INVALID", "Invalid class file");
                            int major =
                                    (Byte.toUnsignedInt(bytes[6]) << 8)
                                            | Byte.toUnsignedInt(bytes[7]);
                            if (major > variant.javaVersion() + 44)
                                throw new ExtensionFailure(
                                        "EXTENSION_JAVA_VERSION",
                                        "Class exceeds declared Target Java feature");
                            code.put(name, bytes);
                        }
                    }
                }
            for (var hook : variant.mixins())
                data.put(
                        hook.config(),
                        source.read(hook.config(), ExtensionManifest.MAX_MANIFEST_BYTES));
            classes = Map.copyOf(code);
            resources = Map.copyOf(data);
            origin =
                    new ProtectionDomain(
                            new CodeSource(
                                    resourceUrl("code-origin", new byte[0]), (Certificate[]) null),
                            null,
                            this,
                            null);
        } catch (IOException failure) {
            throw new ExtensionFailure(
                    "EXTENSION_CODE_INVALID", "Cannot decode approved code JAR", failure);
        }
    }

    Set<String> classNames() {
        return classes.keySet();
    }

    void dependencies(Map<String, ExtensionClassSpace> values) {
        dependencies = Map.copyOf(values);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> result = findLoadedClass(name);
            if (result == null && classes.containsKey(name)) result = findClass(name);
            if (result == null)
                for (ExtensionClassSpace dependency : dependencies.values()) {
                    if (dependency.manifest.exports().stream()
                            .anyMatch(prefix -> name.startsWith(prefix + "."))) {
                        result = dependency.loadClass(name);
                        break;
                    }
                }
            if (result == null) result = getParent().loadClass(name);
            if (resolve) resolveClass(result);
            return result;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) throw new ClassNotFoundException(name);
        return defineClass(name, bytes, 0, bytes.length, origin);
    }

    @Override
    protected URL findResource(String name) {
        byte[] bytes = resources.get(name);
        return bytes == null ? null : resourceUrl(name, bytes);
    }

    @Override
    protected java.util.Enumeration<URL> findResources(String name) {
        URL found = findResource(name);
        return java.util.Collections.enumeration(found == null ? List.of() : List.of(found));
    }

    private URL resourceUrl(String path, byte[] bytes) {
        try {
            return new URL(
                    null,
                    "minecraft-extension://" + manifest.contentIdentity() + "/" + path,
                    new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL url) {
                            return new URLConnection(url) {
                                @Override
                                public void connect() {}

                                @Override
                                public InputStream getInputStream() {
                                    return new ByteArrayInputStream(bytes);
                                }

                                @Override
                                public int getContentLength() {
                                    return bytes.length;
                                }
                            };
                        }
                    });
        } catch (java.net.MalformedURLException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
