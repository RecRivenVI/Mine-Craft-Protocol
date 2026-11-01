package io.github.recrivenvi.minecraftprotocol.core.extension;

import java.nio.file.Path;
import java.util.*;

/** Local settings, not a remote grant. Desired selection never changes an active class space. */
public final class ExtensionPackSelection {
    private static final Map<Path, Map<String, String>> STARTUP =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final ExtensionStore store;
    private final ExtensionPlatform.Environment environment;
    private volatile List<Entry> current = List.of();

    public record Entry(
            ExtensionStore.VerifiedExtensionPack source,
            String id,
            String title,
            String description,
            boolean approved,
            boolean enabled,
            boolean available,
            boolean pending,
            String detail) {}

    public ExtensionPackSelection(Path game, ExtensionPlatform.Environment environment) {
        this.store = new ExtensionStore(game);
        this.environment = environment;
    }

    public static void startup(ExtensionStore store, ExtensionPlatform.Environment environment) {
        Map<String, String> active = new LinkedHashMap<>();
        for (var inspected : store.inspect()) {
            if (inspected.verified() == null || !inspected.status().equals("verified")) continue;
            var source = inspected.verified();
            var manifest = source.manifest();
            var variant = manifest.variants().get(environment.target());
            if (variant == null || variant.entrypoints(environment.side()).isEmpty()) continue;
            if (!available(manifest, environment)) {
                store.select(source, false, false);
                continue;
            }
            active.put(source.manifest().id(), source.manifest().contentIdentity());
        }
        STARTUP.putIfAbsent(store.directory(), Map.copyOf(active));
    }

    private static boolean available(
            ExtensionManifest manifest, ExtensionPlatform.Environment env) {
        var variant = manifest.variants().get(env.target());
        return variant != null
                && !variant.entrypoints(env.side()).isEmpty()
                && variant.javaVersion() <= env.javaVersion()
                && manifest.modDependencies(env.target()).entrySet().stream()
                        .allMatch(
                                dep ->
                                        env.mods().containsKey(dep.getKey())
                                                && ExtensionVersion.accepts(
                                                        dep.getValue(),
                                                        env.mods().get(dep.getKey())));
    }

    public List<Entry> entries() {
        Map<String, String> active = STARTUP.getOrDefault(store.directory(), Map.of());
        List<Entry> result = new ArrayList<>();
        for (var inspected : store.inspect()) {
            var source = inspected.verified();
            if (source == null) continue;
            var manifest = source.manifest();
            boolean approved = Set.of("verified", "disabled").contains(inspected.status());
            boolean enabled = inspected.status().equals("verified");
            boolean available = available(manifest, environment);
            boolean wasActive = manifest.contentIdentity().equals(active.get(manifest.id()));
            result.add(
                    new Entry(
                            source,
                            manifest.id(),
                            manifest.title(),
                            manifest.description(),
                            approved,
                            enabled,
                            available,
                            enabled != wasActive,
                            available ? (approved ? "重启后生效" : "首次启用需要真人批准") : "所需模组、版本或运行端不匹配"));
        }
        result.sort(
                Comparator.comparing(Entry::title, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Entry::id));
        current = List.copyOf(result);
        return current;
    }

    public Entry current(String id) {
        return current.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    public void select(String id, boolean enabled, boolean physicalHuman) {
        var entry =
                entries().stream()
                        .filter(value -> value.id().equals(id))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ExtensionFailure(
                                                "EXTENSION_NOT_FOUND",
                                                "Extensionpack changed; reopen the list"));
        if (enabled && !entry.available())
            throw new ExtensionFailure("EXTENSION_MOD_DEPENDENCY", entry.detail());
        store.select(entry.source(), enabled, physicalHuman);
        entries();
    }

    public Path directory() {
        return store.directory();
    }
}
