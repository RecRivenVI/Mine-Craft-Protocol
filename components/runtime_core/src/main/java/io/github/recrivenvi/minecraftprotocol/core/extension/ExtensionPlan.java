package io.github.recrivenvi.minecraftprotocol.core.extension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared metadata/bytecode preflight; constructing a plan never invokes an extension class. */
final class ExtensionPlan {
    final List<Status> statuses = new ArrayList<>();
    final Map<String, Candidate> candidates = new LinkedHashMap<>();
    final List<Candidate> order = new ArrayList<>();

    ExtensionPlan(
            ExtensionStore store,
            ExtensionPlatform.Environment environment,
            boolean checkModDependencies) {
        Map<String, Integer> counts = new HashMap<>();
        List<ExtensionStore.Inspection> inspected = store.inspect();
        for (var inspection : inspected)
            if (inspection.verified() != null)
                counts.merge(inspection.verified().manifest().id(), 1, Integer::sum);
        for (var inspection : inspected) {
            var source = inspection.verified();
            Status status =
                    new Status(
                            inspection.candidate(),
                            source == null ? null : source.manifest(),
                            inspection.status(),
                            inspection.detail());
            statuses.add(status);
            if (source == null) continue;
            var manifest = source.manifest();
            if (counts.get(manifest.id()) != 1 || manifest.id().equals(environment.productId())) {
                status.fail(
                        "EXTENSION_DUPLICATE_OR_RESERVED_ID",
                        "Package identity is duplicated or reserved");
                continue;
            }
            var variant = manifest.variants().get(environment.target());
            if (variant == null || variant.entrypoints(environment.side()).isEmpty()) {
                status.fail("not_applicable", "No entrypoint for this Target and physical side");
                continue;
            }
            if (variant.javaVersion() > environment.javaVersion()) {
                status.fail(
                        "EXTENSION_JAVA_VERSION",
                        "The Target variant needs a newer Java feature version");
                continue;
            }
            boolean dependencies =
                    !checkModDependencies
                            || manifest.modDependencies().entrySet().stream()
                                    .allMatch(
                                            entry ->
                                                    environment.mods().containsKey(entry.getKey())
                                                            && ExtensionVersion.accepts(
                                                                    entry.getValue(),
                                                                    environment
                                                                            .mods()
                                                                            .get(entry.getKey())));
            if (!dependencies) {
                status.fail("EXTENSION_MOD_DEPENDENCY", "A required mod/version is unavailable");
                continue;
            }
            try {
                candidates.put(
                        manifest.id(),
                        new Candidate(
                                source,
                                status,
                                new ExtensionClassSpace(environment.parent(), source, variant)));
            } catch (ExtensionFailure failure) {
                status.fail(failure.code(), failure.getMessage());
            }
        }
        long expanded = 0L;
        for (var candidate : candidates.values()) {
            if (expanded + candidate.loader.expandedBytes() > ExtensionStore.MAX_PACKAGE_BYTES)
                candidate.status.fail(
                        "EXTENSION_RUNTIME_CODE_BUDGET",
                        "Combined expanded extension code exceeds the Runtime budget");
            else expanded += candidate.loader.expandedBytes();
        }
        // Preflight every class identity before running any entrypoint.
        Map<String, Candidate> classOwners = new HashMap<>();
        for (var candidate : candidates.values())
            for (String name : candidate.loader.classNames()) {
                Candidate previous = classOwners.putIfAbsent(name, candidate);
                if (previous != null) {
                    candidate.status.fail("EXTENSION_DUPLICATE_CLASS", name);
                    previous.status.fail("EXTENSION_DUPLICATE_CLASS", name);
                }
            }

        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (var candidate : candidates.values()) resolve(candidate, visiting, visited, order);
    }

    private boolean resolve(
            Candidate candidate, Set<String> visiting, Set<String> visited, List<Candidate> order) {
        String id = candidate.source.manifest().id();
        if (visited.contains(id)) return candidate.status.state.equals("verified");
        if (!candidate.status.state.equals("verified")) return false;
        if (!visiting.add(id)) {
            candidate.status.fail("EXTENSION_DEPENDENCY_CYCLE", "Cyclic package dependencies");
            return false;
        }
        for (var dependency : candidate.source.manifest().packageDependencies().entrySet()) {
            Candidate required = candidates.get(dependency.getKey());
            if (required == null
                    || !ExtensionVersion.accepts(
                            dependency.getValue(), required.source.manifest().version())
                    || !resolve(required, visiting, visited, order)) {
                if (candidate.status.state.equals("verified"))
                    candidate.status.fail(
                            "EXTENSION_PACKAGE_DEPENDENCY",
                            "A package dependency is unavailable or invalid");
            }
        }
        visiting.remove(id);
        visited.add(id);
        if (!candidate.status.state.equals("verified")) return false;
        order.add(candidate);
        return true;
    }

    static final class Candidate {
        final ExtensionStore.VerifiedPackage source;
        final Status status;
        final ExtensionClassSpace loader;

        Candidate(
                ExtensionStore.VerifiedPackage source, Status status, ExtensionClassSpace loader) {
            this.source = source;
            this.status = status;
            this.loader = loader;
        }
    }

    static final class Status {
        final String candidate;
        final ExtensionManifest manifest;
        String state;
        String detail;
        List<String> registered = List.of();

        Status(String candidate, ExtensionManifest manifest, String state, String detail) {
            this.candidate = candidate;
            this.manifest = manifest;
            this.state = state;
            this.detail = detail;
        }

        void fail(String state, String detail) {
            this.state = state;
            this.detail = detail;
        }
    }

    static boolean requiresHooks(
            ExtensionManifest manifest, ExtensionPlatform.Environment environment) {
        return manifest.variants().get(environment.target()).mixins().stream()
                .anyMatch(
                        hook ->
                                hook.side().equals("common")
                                        || hook.side()
                                                .equals(
                                                        environment
                                                                .side()
                                                                .name()
                                                                .toLowerCase(
                                                                        java.util.Locale.ROOT)));
    }
}
