package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only discovery. Local installation/approval is a separate offline operation. */
public final class ExtensionStore {
    public static final long MAX_PACKAGE_BYTES = 256L * 1024 * 1024;
    public static final int MAX_CODE_BYTES = 64 * 1024 * 1024;
    public static final int MAX_PACKAGES = 128;

    public record Approval(String contentIdentity, Set<String> scopes) {
        public Approval {
            scopes = Set.copyOf(scopes);
        }
    }

    public record Inspection(
            String candidate, String status, String detail, VerifiedPackage verified) {}

    public static final class VerifiedPackage {
        private final Path source;
        private final boolean zipped;
        private final ExtensionManifest manifest;

        private VerifiedPackage(Path source, boolean zipped, ExtensionManifest manifest) {
            this.source = source;
            this.zipped = zipped;
            this.manifest = manifest;
        }

        public ExtensionManifest manifest() {
            return manifest;
        }

        public String sourceName() {
            return source.getFileName().toString();
        }

        /** Recheck approved bytes on use; replacing a package cannot change an active snapshot. */
        public byte[] read(String path, int maxBytes) {
            if (!manifest.files().containsKey(path))
                throw new ExtensionFailure(
                        "EXTENSION_FILE_NOT_FOUND", "File is not indexed by the approved package");
            byte[] bytes = readFile(source, zipped, path, maxBytes);
            if (!ExtensionManifest.digest(bytes).equals(manifest.files().get(path)))
                throw new ExtensionFailure(
                        "EXTENSION_CONTENT_CHANGED", "Approved file content changed: " + path);
            return bytes;
        }
    }

    private final Path directory;

    public ExtensionStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public List<Inspection> inspect() {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Inspection> results = new ArrayList<>();
        try {
            regular(directory, true);
            Path packages = directory.resolve("packages");
            if (!Files.exists(packages, LinkOption.NOFOLLOW_LINKS)) return List.of();
            regular(packages, true);
            Map<String, Approval> approvals = approvals(directory.resolve("approvals.json"));
            List<Path> candidates;
            try (var paths = Files.list(packages)) {
                candidates = paths.sorted().toList();
            }
            if (candidates.size() > MAX_PACKAGES)
                throw new ExtensionFailure(
                        "EXTENSION_PACKAGE_BUDGET", "Too many installed package candidates");
            for (Path candidate : candidates) {
                String name = candidate.getFileName().toString();
                try {
                    VerifiedPackage verified = inspect(candidate);
                    Approval approval = approvals.get(verified.manifest.id());
                    if (approval == null
                            || !approval.contentIdentity()
                                    .equals(verified.manifest.contentIdentity()))
                        throw new ExtensionFailure(
                                "EXTENSION_APPROVAL_REQUIRED",
                                "Approve this exact package content locally before restart");
                    if (!approval.scopes().containsAll(verified.manifest.requestedScopes()))
                        throw new ExtensionFailure(
                                "EXTENSION_APPROVAL_SCOPE_MISSING",
                                "Local approval does not cover the declared remote capability scopes");
                    results.add(new Inspection(name, "verified", "", verified));
                } catch (ExtensionFailure failure) {
                    results.add(new Inspection(name, failure.code(), failure.getMessage(), null));
                } catch (RuntimeException invalid) {
                    results.add(
                            new Inspection(
                                    name,
                                    "EXTENSION_MANIFEST_INVALID",
                                    "Malformed package fields",
                                    null));
                }
            }
        } catch (IOException | RuntimeException failure) {
            String code =
                    failure instanceof ExtensionFailure known
                            ? known.code()
                            : "EXTENSION_STORAGE_UNAVAILABLE";
            results.add(
                    new Inspection(
                            "installation",
                            code,
                            "Cannot safely read the local package installation",
                            null));
        }
        return List.copyOf(results);
    }

    /** Inspection does not execute classes and does not mean local approval has been granted. */
    public static VerifiedPackage inspect(Path source) {
        try {
            boolean directory = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS);
            regular(source, directory);
            boolean zipped = !directory;
            if (zipped
                    && (!source.getFileName().toString().endsWith(".zip")
                            || Files.size(source) > MAX_PACKAGE_BYTES))
                throw new ExtensionFailure(
                        "EXTENSION_PACKAGE_INVALID",
                        "An ordinary package ZIP or directory is required");
            ExtensionManifest manifest =
                    new ExtensionManifest(
                            readFile(
                                    source,
                                    zipped,
                                    "extension.json",
                                    ExtensionManifest.MAX_MANIFEST_BYTES));
            Map<String, String> actual = new LinkedHashMap<>();
            long[] total = {0};
            if (zipped) {
                try (ZipFile zip = new ZipFile(source.toFile())) {
                    var entries = zip.entries();
                    Set<String> names = new HashSet<>();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String path = entry.getName();
                        ExtensionManifest.path(
                                entry.isDirectory() ? path.substring(0, path.length() - 1) : path);
                        if (!names.add(path.toLowerCase(java.util.Locale.ROOT)))
                            throw new ExtensionFailure(
                                    "EXTENSION_PACKAGE_INVALID",
                                    "Duplicate/case-colliding ZIP entry");
                        if (entry.isDirectory()) continue;
                        if (actual.size() >= 4097)
                            throw new ExtensionFailure(
                                    "EXTENSION_PACKAGE_BUDGET", "Too many package entries");
                        try (InputStream input = zip.getInputStream(entry)) {
                            actual.put(path, hash(input, total));
                        }
                    }
                }
            } else {
                Path root = source.toRealPath();
                try (var walk = Files.walk(root)) {
                    var iterator = walk.iterator();
                    while (iterator.hasNext()) {
                        Path file = iterator.next();
                        if (file.equals(root)) continue;
                        boolean folder = Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS);
                        regular(file, folder);
                        String path = root.relativize(file).toString().replace('\\', '/');
                        ExtensionManifest.path(path);
                        if (!file.toRealPath().startsWith(root))
                            throw new ExtensionFailure(
                                    "EXTENSION_PACKAGE_INVALID",
                                    "Linked package content is not allowed");
                        if (folder) continue;
                        if (actual.size() >= 4097)
                            throw new ExtensionFailure(
                                    "EXTENSION_PACKAGE_BUDGET", "Too many package entries");
                        try (InputStream input = Files.newInputStream(file)) {
                            actual.put(path, hash(input, total));
                        }
                    }
                }
            }
            String manifestHash = actual.remove("extension.json");
            if (!manifest.contentIdentity().equals(manifestHash)
                    || !manifest.files().equals(actual))
                throw new ExtensionFailure(
                        "EXTENSION_CONTENT_MISMATCH",
                        "Every file must match the manifest; missing, extra or changed content is rejected");
            return new VerifiedPackage(source.toAbsolutePath().normalize(), zipped, manifest);
        } catch (ExtensionFailure known) {
            throw known;
        } catch (IOException unreadable) {
            throw new ExtensionFailure(
                    "EXTENSION_STORAGE_UNAVAILABLE", "Cannot read package content", unreadable);
        } catch (RuntimeException invalid) {
            throw new ExtensionFailure(
                    "EXTENSION_MANIFEST_INVALID", "Malformed package metadata", invalid);
        }
    }

    public static Map<String, Approval> approvals(Path file) {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        try {
            regular(file, false);
            JsonObject root;
            try (InputStream input = Files.newInputStream(file)) {
                root = ExtensionManifest.object(read(input, ExtensionManifest.MAX_MANIFEST_BYTES));
            }
            ExtensionManifest.keys(root, Set.of("formatVersion", "packages"));
            if (!root.has("formatVersion")
                    || !root.get("formatVersion").isJsonPrimitive()
                    || !root.get("formatVersion").getAsJsonPrimitive().isNumber()
                    || !root.get("formatVersion").getAsString().equals("1"))
                throw new ExtensionFailure(
                        "EXTENSION_APPROVAL_INVALID", "Approval format 1 is required");
            Map<String, Approval> result = new LinkedHashMap<>();
            for (var entry : root.getAsJsonObject("packages").entrySet()) {
                JsonObject item = entry.getValue().getAsJsonObject();
                ExtensionManifest.keys(item, Set.of("contentIdentity", "scopes"));
                String hash = ExtensionManifest.string(item, "contentIdentity");
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new ExtensionFailure(
                            "EXTENSION_APPROVAL_INVALID", "Approval identity must be SHA-256");
                result.put(
                        entry.getKey(),
                        new Approval(
                                hash, ExtensionManifest.strings(item.getAsJsonArray("scopes"))));
            }
            return Map.copyOf(result);
        } catch (IOException | RuntimeException invalid) {
            throw new ExtensionFailure(
                    "EXTENSION_APPROVAL_INVALID", "Local approval metadata is invalid", invalid);
        }
    }

    private static byte[] readFile(Path source, boolean zipped, String path, int maxBytes) {
        try {
            regular(source, !zipped);
            if (zipped)
                try (ZipFile zip = new ZipFile(source.toFile())) {
                    ZipEntry entry = zip.getEntry(path);
                    if (entry == null || entry.isDirectory())
                        throw new ExtensionFailure(
                                "EXTENSION_FILE_NOT_FOUND", "Indexed package file is missing");
                    try (InputStream input = zip.getInputStream(entry)) {
                        return read(input, maxBytes);
                    }
                }
            Path resolved =
                    source.toAbsolutePath().normalize().resolve(ExtensionManifest.path(path));
            Path cursor = resolved;
            while (!cursor.equals(source.toAbsolutePath().normalize())) {
                regular(cursor, !cursor.equals(resolved));
                cursor = cursor.getParent();
            }
            if (!resolved.toRealPath().startsWith(source.toRealPath()))
                throw new ExtensionFailure(
                        "EXTENSION_PACKAGE_INVALID", "Package path escaped its root");
            try (InputStream input = Files.newInputStream(resolved)) {
                return read(input, maxBytes);
            }
        } catch (ExtensionFailure known) {
            throw known;
        } catch (IOException failure) {
            throw new ExtensionFailure(
                    "EXTENSION_STORAGE_UNAVAILABLE",
                    "Cannot read the approved package file",
                    failure);
        }
    }

    private static byte[] read(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit)
            throw new ExtensionFailure(
                    "EXTENSION_PACKAGE_BUDGET", "Package file exceeds its read budget");
        return bytes;
    }

    private static String hash(InputStream input, long[] total) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            for (int length; (length = input.read(buffer)) >= 0; ) {
                total[0] += length;
                if (total[0] > MAX_PACKAGE_BYTES)
                    throw new ExtensionFailure(
                            "EXTENSION_PACKAGE_BUDGET", "Uncompressed package budget exceeded");
                digest.update(buffer, 0, length);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void regular(Path path, boolean directory) throws IOException {
        BasicFileAttributes attrs =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attrs.isSymbolicLink()
                || attrs.isOther()
                || (directory ? !attrs.isDirectory() : !attrs.isRegularFile()))
            throw new ExtensionFailure(
                    "EXTENSION_PACKAGE_INVALID",
                    "Linked or nonregular package storage is not allowed");
    }
}
