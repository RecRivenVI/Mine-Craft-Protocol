package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Offline, derived native-library staging. No extension class is defined or initialized here. */
public final class ExtensionEarlyStage {
    public static final int ABI = 1;
    public static final String FILE_NAME = "minecraft-protocol-extension-libraries.jar";
    public static final String INDEX = "META-INF/minecraft-protocol-extensions/stage.json";
    public static final String MIXIN_PLUGIN =
            "io.github.recrivenvi.minecraftprotocol.probe.loader.LoaderExtensionBridge";
    private static final Set<String> CONFIG_FIELDS =
            Set.of(
                    "required",
                    "minVersion",
                    "package",
                    "compatibilityLevel",
                    "mixins",
                    "client",
                    "server",
                    "refmap",
                    "injectors",
                    "overwrites",
                    "priority",
                    "mixinPriority",
                    "verbose",
                    "setSourceFile");

    private ExtensionEarlyStage() {}

    /** Target/side must come from the selected local instance, not a remote game request. */
    public static JsonObject write(
            ExtensionStore store, ExtensionPlatform.Environment environment, Path destination)
            throws IOException {
        var built = build(store, environment);
        Files.createDirectories(destination.toAbsolutePath().getParent());
        try (var output =
                new JarOutputStream(
                        Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            var manifestBytes = new java.io.ByteArrayOutputStream();
            built.manifest().write(manifestBytes);
            write(output, JarFile.MANIFEST_NAME, manifestBytes.toByteArray());
            for (var entry :
                    built.entries().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
                write(output, entry.getKey(), entry.getValue());
            write(output, INDEX, built.index().toString().getBytes(StandardCharsets.UTF_8));
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "extensions.early_stage");
        result.addProperty("abi", ABI);
        result.addProperty("target", environment.target());
        result.addProperty("side", environment.side().name());
        result.addProperty("packageCount", built.index().getAsJsonArray("packages").size());
        result.addProperty("configCount", built.index().getAsJsonArray("configs").size());
        result.addProperty(
                "librarySha256", ExtensionManifest.digest(Files.readAllBytes(destination)));
        return result;
    }

    record Built(Map<String, byte[]> entries, JsonObject index, Manifest manifest) {}

    static Built build(ExtensionStore store, ExtensionPlatform.Environment environment) {
        // Actual mod dependency availability is rechecked against the Loader at bootstrap.
        // Staging cannot assert that an upstream mod will be present in a future process.
        ExtensionPlan plan = new ExtensionPlan(store, environment, false);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        JsonObject index = new JsonObject();
        index.addProperty("abi", ABI);
        index.addProperty("target", environment.target());
        index.addProperty("side", environment.side().name());
        JsonArray packages = new JsonArray(), configs = new JsonArray();
        Set<String> mixinPackages = new java.util.HashSet<>();
        Set<String> selectedPackages = new java.util.HashSet<>();
        for (var candidate : plan.order)
            if (ExtensionPlan.requiresHooks(candidate.source.manifest(), environment))
                selectDependencies(plan, candidate, selectedPackages);
        for (var candidate :
                plan.order.stream()
                        .sorted(
                                java.util.Comparator.comparing(
                                        value -> value.source.manifest().id()))
                        .toList()) {
            if (!candidate.status.state.equals("verified")
                    || !selectedPackages.contains(candidate.source.manifest().id())) continue;
            var manifest = candidate.source.manifest();
            var variant = manifest.variants().get(environment.target());
            JsonObject item = new JsonObject();
            item.addProperty("id", manifest.id());
            item.addProperty("contentIdentity", manifest.contentIdentity());
            packages.add(item);
            for (var resource : candidate.loader.stagedResources().entrySet()) {
                String name = resource.getKey();
                if (name.equals("fabric.mod.json")
                        || name.equals("META-INF/mods.toml")
                        || name.equals("META-INF/neoforge.mods.toml")
                        || name.startsWith("META-INF/services/")
                        || name.startsWith("META-INF/jarjar/")
                        || name.equals(INDEX))
                    throw new ExtensionFailure(
                            "EXTENSION_EARLY_RESOURCE_INVALID",
                            "Code JAR must not add Loader modules/services: " + name);
                put(entries, name, resource.getValue());
            }
            for (var hook : variant.mixins()) {
                if (!hook.side().equals("common")
                        && !hook.side()
                                .equals(
                                        environment
                                                .side()
                                                .name()
                                                .toLowerCase(java.util.Locale.ROOT))) continue;
                JsonObject original =
                        ExtensionManifest.object(
                                candidate.source.read(
                                        hook.config(), ExtensionManifest.MAX_MANIFEST_BYTES));
                if (original.keySet().stream().anyMatch(key -> !CONFIG_FIELDS.contains(key)))
                    throw new ExtensionFailure(
                            "EXTENSION_MIXIN_CONFIG_INVALID",
                            "Unsupported config field or custom plugin in " + hook.config());
                String namespace = text(original, "package");
                if (!namespace.startsWith(manifest.javaNamespace() + ".")
                        || !mixinPackages.add(namespace))
                    throw new ExtensionFailure(
                            "EXTENSION_MIXIN_NAMESPACE",
                            "Each selected config needs a unique, package-owned Mixin namespace");
                JsonArray selected = new JsonArray();
                append(original, "mixins", namespace, candidate, selected);
                append(
                        original,
                        environment.side().name().toLowerCase(java.util.Locale.ROOT),
                        namespace,
                        candidate,
                        selected);
                if (selected.size() == 0)
                    throw new ExtensionFailure(
                            "EXTENSION_MIXIN_CONFIG_INVALID", "No selected Mixins");
                String generated =
                        "minecraft-protocol-extensions/"
                                + manifest.id()
                                + "/"
                                + manifest.contentIdentity().substring(0, 16)
                                + "/"
                                + configs.size()
                                + ".json";
                JsonObject config = original.deepCopy();
                config.remove("client");
                config.remove("server");
                config.add("mixins", new JsonArray());
                config.addProperty("plugin", MIXIN_PLUGIN);
                if (original.has("refmap")) {
                    String sourceRefmap = text(original, "refmap");
                    byte[] refmap =
                            candidate.source.read(
                                    sourceRefmap, ExtensionManifest.MAX_MANIFEST_BYTES);
                    ExtensionManifest.object(refmap);
                    String stagedRefmap = generated + ".refmap.json";
                    put(entries, stagedRefmap, refmap);
                    config.addProperty("refmap", stagedRefmap);
                }
                put(entries, generated, config.toString().getBytes(StandardCharsets.UTF_8));
                JsonObject descriptor = new JsonObject();
                descriptor.addProperty("packageId", manifest.id());
                descriptor.addProperty("contentIdentity", manifest.contentIdentity());
                descriptor.addProperty("mixinPackage", namespace);
                descriptor.addProperty("source", hook.config());
                descriptor.addProperty("generated", generated);
                descriptor.add("mixins", selected);
                configs.add(descriptor);
            }
        }
        if (configs.size() > 256)
            throw new ExtensionFailure(
                    "EXTENSION_MIXIN_BUDGET", "Too many selected configurations");
        JsonObject hashes = new JsonObject();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                hashes.addProperty(
                                        entry.getKey(),
                                        ExtensionManifest.digest(entry.getValue())));
        index.add("entries", hashes);
        index.add("packages", packages);
        index.add("configs", configs);
        Manifest library = new Manifest();
        var attributes = library.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("FMLModType", "GAMELIBRARY");
        attributes.putValue(
                "Automatic-Module-Name",
                "io.github.recrivenvi.minecraftprotocol.extension_libraries");
        attributes.putValue("MineCraftProtocol-Extension-Library", Integer.toString(ABI));
        // Loader registers these inert configs before Mixin begins iterating. The core-owned
        // plugin supplies class names only after approval/version/side/content verification.
        if (configs.size() > 0)
            attributes.putValue(
                    "MixinConfigs",
                    java.util.stream.StreamSupport.stream(configs.spliterator(), false)
                            .map(value -> value.getAsJsonObject().get("generated").getAsString())
                            .collect(java.util.stream.Collectors.joining(",")));
        return new Built(Map.copyOf(entries), index, library);
    }

    private static void selectDependencies(
            ExtensionPlan plan, ExtensionPlan.Candidate candidate, Set<String> selected) {
        if (!selected.add(candidate.source.manifest().id())) return;
        for (String dependency : candidate.source.manifest().packageDependencies().keySet()) {
            var required = plan.candidates.get(dependency);
            if (required != null && required.status.state.equals("verified"))
                selectDependencies(plan, required, selected);
        }
    }

    private static void append(
            JsonObject config,
            String key,
            String namespace,
            ExtensionPlan.Candidate candidate,
            JsonArray selected) {
        if (!config.has(key)) return;
        for (var element : config.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                throw new ExtensionFailure(
                        "EXTENSION_MIXIN_CONFIG_INVALID", "Mixin names must be strings");
            String name = element.getAsString();
            if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
                    || !candidate.loader.classNames().contains(namespace + "." + name))
                throw new ExtensionFailure(
                        "EXTENSION_MIXIN_CLASS_MISSING",
                        "Mixin class is not in the approved package: " + namespace + "." + name);
            if (selected.asList().stream().anyMatch(value -> value.getAsString().equals(name)))
                throw new ExtensionFailure(
                        "EXTENSION_MIXIN_CONFIG_INVALID", "Duplicate selected Mixin");
            selected.add(name);
        }
    }

    private static String text(JsonObject value, String name) {
        if (!value.has(name)
                || !value.get(name).isJsonPrimitive()
                || !value.getAsJsonPrimitive(name).isString())
            throw new ExtensionFailure(
                    "EXTENSION_MIXIN_CONFIG_INVALID", "String required: " + name);
        return value.get(name).getAsString();
    }

    private static void put(Map<String, byte[]> entries, String name, byte[] bytes) {
        byte[] previous = entries.putIfAbsent(name, bytes);
        if (previous != null && !java.util.Arrays.equals(previous, bytes))
            throw new ExtensionFailure(
                    "EXTENSION_EARLY_RESOURCE_DUPLICATE", "Conflicting native resource: " + name);
    }

    private static void write(JarOutputStream output, String name, byte[] bytes)
            throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }
}
