package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** One immutable early selection per physical game loader/directory, not a global input owner. */
public final class ExtensionEarlyBootstrap implements ExtensionPlatform.EarlyHooks {
    public interface Loader {
        void prepare(Path library) throws Exception;

        boolean isClassLoaded(String name);
    }

    private record Key(Path directory, String target, String side, ClassLoader loader) {}

    private static final Map<Key, ExtensionEarlyBootstrap> BOOTSTRAPS = new ConcurrentHashMap<>();
    private final ExtensionPlatform.Environment environment;
    private final Map<String, Config> configurations = new LinkedHashMap<>();
    private final Map<String, String> nativePackages = new LinkedHashMap<>();
    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> applied = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean submitted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private String state = "not_prepared", failure = "";

    private record Config(
            String packageId,
            String contentIdentity,
            String path,
            String namespace,
            List<String> mixins,
            Map<String, Set<String>> targets) {}

    public static ExtensionEarlyBootstrap boot(
            Path gameDirectory, ExtensionPlatform.Environment environment, Loader loader) {
        Key key =
                new Key(
                        gameDirectory.toAbsolutePath().normalize(),
                        environment.target(),
                        environment.side().name(),
                        environment.parent());
        return BOOTSTRAPS.computeIfAbsent(
                key, ignored -> new ExtensionEarlyBootstrap(gameDirectory, environment, loader));
    }

    private ExtensionEarlyBootstrap(
            Path gameDirectory, ExtensionPlatform.Environment environment, Loader loader) {
        this.environment = environment;
        Path library = gameDirectory.resolve("mods").resolve(ExtensionEarlyStage.FILE_NAME);
        if (!Files.exists(library, LinkOption.NOFOLLOW_LINKS)) {
            state = "no_staged_library";
            return;
        }
        try {
            if (Files.isSymbolicLink(library)
                    || !Files.isRegularFile(library, LinkOption.NOFOLLOW_LINKS))
                throw new ExtensionFailure(
                        "EXTENSION_EARLY_STORAGE_INVALID",
                        "Staged library must be a regular local file");
            var store = new ExtensionStore(gameDirectory.resolve("minecraft-protocol/extensions"));
            var expected = ExtensionEarlyStage.build(store, environment);
            JsonObject index;
            try (JarFile jar = new JarFile(library.toFile(), true)) {
                if (jar.getManifest() == null
                        || !Integer.toString(ExtensionEarlyStage.ABI)
                                .equals(
                                        jar.getManifest()
                                                .getMainAttributes()
                                                .getValue("MineCraftProtocol-Extension-Library")))
                    throw new ExtensionFailure(
                            "EXTENSION_EARLY_LIBRARY_INVALID",
                            "Not a platform-owned native library");
                var entry = jar.getJarEntry(ExtensionEarlyStage.INDEX);
                if (entry == null)
                    throw new ExtensionFailure(
                            "EXTENSION_EARLY_LIBRARY_INVALID", "Missing stage index");
                try (var input = jar.getInputStream(entry)) {
                    index =
                            ExtensionManifest.object(
                                    input.readNBytes(ExtensionManifest.MAX_MANIFEST_BYTES + 1));
                }
                if (!index.equals(expected.index()))
                    throw new ExtensionFailure(
                            "EXTENSION_EARLY_CONTENT_CHANGED",
                            "Approval/Target/side/content changed; restage offline and restart");
                Set<String> seen = new java.util.HashSet<>();
                for (var entries = jar.entries(); entries.hasMoreElements(); ) {
                    var file = entries.nextElement();
                    if (!seen.add(file.getName()))
                        throw new ExtensionFailure(
                                "EXTENSION_EARLY_LIBRARY_INVALID", "Duplicate staged resource");
                    if (file.getName().equals(JarFile.MANIFEST_NAME)
                            || file.getName().equals(ExtensionEarlyStage.INDEX)) continue;
                    byte[] bytes = expected.entries().get(file.getName());
                    if (file.isDirectory() || bytes == null)
                        throw new ExtensionFailure(
                                "EXTENSION_EARLY_LIBRARY_INVALID", "Unindexed native resource");
                    try (var input = jar.getInputStream(file)) {
                        if (!java.util.Arrays.equals(bytes, input.readNBytes(bytes.length + 1)))
                            throw new ExtensionFailure(
                                    "EXTENSION_EARLY_CONTENT_CHANGED",
                                    "Staged bytes differ from approved package");
                    }
                }
                if (!seen.containsAll(expected.entries().keySet()))
                    throw new ExtensionFailure(
                            "EXTENSION_EARLY_LIBRARY_INVALID", "Missing staged resources");
            }
            loader.prepare(library);
            // Check the actual native resource route before giving Mixin any package class name.
            for (var entry : expected.entries().entrySet()) {
                try (var input = environment.parent().getResourceAsStream(entry.getKey())) {
                    if (input == null
                            || !java.util.Arrays.equals(
                                    entry.getValue(),
                                    input.readNBytes(entry.getValue().length + 1)))
                        throw new ExtensionFailure(
                                "EXTENSION_EARLY_CLASSPATH_UNAVAILABLE",
                                "Native class/resource route does not resolve the approved bytes: "
                                        + entry.getKey());
                }
            }
            ExtensionPlan plan = new ExtensionPlan(store, environment, true);
            for (var item : index.getAsJsonArray("packages")) {
                var value = item.getAsJsonObject();
                String id = value.get("id").getAsString();
                var candidate = plan.candidates.get(id);
                if (candidate != null && candidate.status.state.equals("verified"))
                    nativePackages.put(id, value.get("contentIdentity").getAsString());
            }
            for (var item : index.getAsJsonArray("configs")) {
                var value = item.getAsJsonObject();
                String id = value.get("packageId").getAsString();
                if (!nativePackages.containsKey(id)) continue;
                List<String> mixins = new ArrayList<>();
                for (var name : value.getAsJsonArray("mixins")) mixins.add(name.getAsString());
                String namespace = value.get("mixinPackage").getAsString();
                Map<String, Set<String>> mixinTargets = new LinkedHashMap<>();
                for (String name : mixins) {
                    byte[] bytes =
                            expected.entries()
                                    .get((namespace + "." + name).replace('.', '/') + ".class");
                    Set<String> targets = targets(bytes);
                    mixinTargets.put(namespace + "." + name, Set.copyOf(targets));
                    for (String target : targets)
                        if (loader.isClassLoaded(target))
                            throw new ExtensionFailure(
                                    "EXTENSION_MIXIN_TARGET_ALREADY_LOADED",
                                    "Cannot transform an already loaded target: " + target);
                }
                configurations.put(
                        namespace,
                        new Config(
                                id,
                                value.get("contentIdentity").getAsString(),
                                value.get("generated").getAsString(),
                                namespace,
                                List.copyOf(mixins),
                                Map.copyOf(mixinTargets)));
            }
            state = "validated";
        } catch (Throwable error) {
            configurations.clear();
            nativePackages.clear();
            state =
                    error instanceof ExtensionFailure known
                            ? known.code()
                            : "EXTENSION_EARLY_BOOTSTRAP_FAILED";
            failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            if (failure.length() > 512) failure = failure.substring(0, 512);
            System.getLogger(ExtensionEarlyBootstrap.class.getName())
                    .log(System.Logger.Level.WARNING, state + ": " + failure);
        }
    }

    private static Set<String> targets(byte[] bytes) {
        if (bytes == null)
            throw new ExtensionFailure(
                    "EXTENSION_MIXIN_CLASS_MISSING", "No approved Mixin bytecode");
        ClassNode node = new ClassNode();
        new ClassReader(bytes)
                .accept(
                        node,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Set<String> result = new java.util.LinkedHashSet<>();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        for (var annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) {
                String key = String.valueOf(annotation.values.get(i));
                if (!key.equals("value") && !key.equals("targets")) continue;
                if (!(annotation.values.get(i + 1) instanceof List<?> values))
                    throw new ExtensionFailure(
                            "EXTENSION_MIXIN_TARGET_INVALID", "Target annotation is not a list");
                for (Object value : values)
                    result.add(
                            value instanceof Type type
                                    ? type.getClassName()
                                    : String.valueOf(value));
            }
        }
        if (result.isEmpty()
                || result.stream()
                        .anyMatch(
                                name ->
                                        !name.matches(
                                                "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+")))
            throw new ExtensionFailure(
                    "EXTENSION_MIXIN_TARGET_INVALID", "No valid static Mixin targets");
        return result;
    }

    public void submitConfigurations(java.util.function.Consumer<String> submit) {
        if (submitted.compareAndSet(false, true)) configurationPaths().forEach(submit);
    }

    public List<String> configurationPaths() {
        return configurations.values().stream().map(Config::path).toList();
    }

    public boolean extensionNamespace(String namespace) {
        return configurations.containsKey(namespace);
    }

    public List<String> mixins(String namespace) {
        Config config = configurations.get(namespace);
        if (config == null) return List.of();
        registered.add(namespace);
        return config.mixins();
    }

    public boolean allows(String namespace, String mixin, String target) {
        Config config = configurations.get(namespace);
        return config != null
                && registered.contains(namespace)
                && config.targets()
                        .getOrDefault(mixin, Set.of())
                        .contains(target.replace('/', '.'));
    }

    public void applied(String namespace, String mixin, String target) {
        if (allows(namespace, mixin, target))
            applied.computeIfAbsent(namespace, ignored -> ConcurrentHashMap.newKeySet())
                    .add(mixin + " -> " + target);
    }

    @Override
    public boolean prepared(ExtensionManifest manifest, ExtensionPlatform.Environment env) {
        if (!sameEnvironment(env)
                || !manifest.contentIdentity().equals(nativePackages.get(manifest.id())))
            return false;
        return configurations.values().stream()
                .filter(config -> config.packageId().equals(manifest.id()))
                .allMatch(config -> registered.contains(config.namespace()));
    }

    @Override
    public List<String> nativeExports(
            ExtensionManifest manifest, ExtensionPlatform.Environment env) {
        return sameEnvironment(env)
                        && manifest.contentIdentity().equals(nativePackages.get(manifest.id()))
                ? manifest.exports()
                : List.of();
    }

    private boolean sameEnvironment(ExtensionPlatform.Environment env) {
        return env.parent() == environment.parent()
                && env.target().equals(environment.target())
                && env.side() == environment.side();
    }

    public JsonObject diagnostics() {
        JsonObject value = new JsonObject();
        value.addProperty("state", state);
        value.addProperty("failure", failure);
        value.addProperty("target", environment.target());
        value.addProperty("side", environment.side().name());
        value.addProperty("libraryAbi", ExtensionEarlyStage.ABI);
        value.addProperty("globalFallbackReplaced", false);
        JsonArray configs = new JsonArray();
        configurations.forEach(
                (namespace, config) -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("packageId", config.packageId());
                    item.addProperty("mixinPackage", namespace);
                    item.addProperty("registered", registered.contains(namespace));
                    JsonArray targets = new JsonArray();
                    applied.getOrDefault(namespace, Set.of()).stream()
                            .sorted()
                            .forEach(targets::add);
                    item.add("appliedTargets", targets);
                    configs.add(item);
                });
        value.add("configs", configs);
        return value;
    }
}
