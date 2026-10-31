package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Format 1. The raw manifest digest binds every declared byte; it is not a code signature. */
public final class ExtensionManifest {
    public static final int FORMAT = 1;
    public static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final Pattern TARGET =
            Pattern.compile("[0-9]+(?:\\.[0-9]+){1,2}-(?:forge|neoforge|fabric)");
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*(?:[.-][a-z0-9_]+)*");
    private static final Pattern VERSION =
            Pattern.compile(
                    "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");
    private static final Pattern JAVA_NAME =
            Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+");
    private static final Set<String> RESERVED =
            Set.of(
                    "java",
                    "javax",
                    "net.minecraft",
                    "net.neoforged",
                    "net.minecraftforge",
                    "net.fabricmc",
                    "org.spongepowered",
                    "com.google.gson",
                    "io.github.recrivenvi.minecraftprotocol");

    public record Hook(String config, String side) {}

    public record Variant(
            int javaVersion,
            List<String> code,
            Map<String, List<String>> entries,
            List<Hook> mixins,
            Map<String, String> mods) {
        public Variant {
            code = List.copyOf(code);
            entries = Map.copyOf(entries);
            mixins = List.copyOf(mixins);
            mods = Map.copyOf(mods);
        }

        public List<String> entrypoints(PackageContext.Side side) {
            List<String> result = new ArrayList<>(entries.getOrDefault("common", List.of()));
            result.addAll(
                    entries.getOrDefault(
                            side.name().toLowerCase(java.util.Locale.ROOT), List.of()));
            return List.copyOf(result);
        }
    }

    private final JsonObject source;
    private final String contentIdentity;
    private final String id;
    private final String version;
    private final String namespace;
    private final Map<String, String> files;
    private final Map<String, Variant> variants;
    private final Map<String, String> modDependencies;
    private final Map<String, String> packageDependencies;
    private final Set<String> capabilities;
    private final Set<String> scopes;
    private final List<String> exports;
    private final Map<String, String> documents;
    private final Map<String, String> tests;

    public ExtensionManifest(byte[] bytes) {
        source = object(bytes);
        keys(
                source,
                Set.of(
                        "formatVersion",
                        "sdkApi",
                        "id",
                        "version",
                        "javaNamespace",
                        "files",
                        "variants",
                        "dependencies",
                        "capabilities",
                        "requestedScopes",
                        "exports",
                        "documents",
                        "tests"));
        if (integer(source, "formatVersion") != FORMAT
                || integer(source, "sdkApi") != PackageContext.SDK_API)
            throw failure("EXTENSION_FORMAT_UNSUPPORTED", "Unsupported package format or SDK API");
        id = string(source, "id");
        version = string(source, "version");
        namespace = string(source, "javaNamespace");
        if (!ID.matcher(id).matches() || id.length() > 96 || id.equals("minecraft"))
            throw invalid("Invalid or reserved package ID");
        if (!VERSION.matcher(version).matches() || version.length() > 96)
            throw invalid("Package version must be SemVer");
        if (!JAVA_NAME.matcher(namespace).matches()
                || RESERVED.stream()
                        .anyMatch(
                                prefix ->
                                        namespace.equals(prefix)
                                                || namespace.startsWith(prefix + ".")))
            throw invalid("Private Java namespace is invalid or reserved");
        files = stringMap(source.getAsJsonObject("files"));
        if (files.isEmpty() || files.size() > 4096)
            throw invalid("Package file index is empty or too large");
        files.forEach(
                (path, hash) -> {
                    path(path);
                    if (path.equals("extension.json") || !hash.matches("[0-9a-f]{64}"))
                        throw invalid("Invalid file digest: " + path);
                });
        Set<String> folded = new LinkedHashSet<>();
        for (String path : files.keySet())
            if (!folded.add(path.toLowerCase(java.util.Locale.ROOT)))
                throw invalid("Case-colliding package path");
        capabilities = strings(source.getAsJsonArray("capabilities"));
        for (String capability : capabilities)
            if (!capability.matches(Pattern.quote(id) + ":[a-z0-9_./-]+"))
                throw invalid("Capability outside package namespace");
        scopes = strings(source.getAsJsonArray("requestedScopes"));
        if (scopes.stream().anyMatch(scope -> !scope.matches("[a-z][a-z0-9_.-]{0,63}")))
            throw invalid("Invalid requested scope");
        exports =
                List.copyOf(
                        strings(
                                source.has("exports")
                                        ? source.getAsJsonArray("exports")
                                        : new JsonArray()));
        if (exports.stream()
                .anyMatch(value -> !value.startsWith(namespace + ".") && !value.equals(namespace)))
            throw invalid("Exports must belong to the private Java namespace");
        JsonObject dependencies =
                source.has("dependencies")
                        ? source.getAsJsonObject("dependencies")
                        : new JsonObject();
        keys(dependencies, Set.of("mods", "packages"));
        modDependencies =
                stringMap(
                        dependencies.has("mods")
                                ? dependencies.getAsJsonObject("mods")
                                : new JsonObject());
        packageDependencies =
                stringMap(
                        dependencies.has("packages")
                                ? dependencies.getAsJsonObject("packages")
                                : new JsonObject());
        for (var dependency : modDependencies.entrySet())
            dependency(dependency.getKey(), dependency.getValue());
        for (var dependency : packageDependencies.entrySet())
            dependency(dependency.getKey(), dependency.getValue());
        if (packageDependencies.containsKey(id)) throw invalid("Self dependency");
        documents = indexedFiles(source, "documents");
        tests = indexedFiles(source, "tests");
        Map<String, Variant> parsed = new LinkedHashMap<>();
        JsonObject variantsJson = source.getAsJsonObject("variants");
        if (variantsJson == null || variantsJson.size() == 0)
            throw invalid("Target variants required");
        for (var item : variantsJson.entrySet()) {
            if (!TARGET.matcher(item.getKey()).matches())
                throw invalid("Malformed Target: " + item.getKey());
            JsonObject value = item.getValue().getAsJsonObject();
            keys(value, Set.of("javaVersion", "code", "entrypoints", "mixins", "mods"));
            int javaVersion = integer(value, "javaVersion");
            if (javaVersion < 17 || javaVersion > 99)
                throw invalid("Invalid Target Java feature version");
            List<String> code = List.copyOf(strings(value.getAsJsonArray("code")));
            if (code.isEmpty()) throw invalid("Target code required");
            for (String file : code)
                if (!files.containsKey(file) || !file.endsWith(".jar"))
                    throw invalid("Unindexed code JAR");
            JsonObject entriesJson = value.getAsJsonObject("entrypoints");
            keys(entriesJson, Set.of("common", "client", "server"));
            Map<String, List<String>> entries = new LinkedHashMap<>();
            for (var entry : entriesJson.entrySet()) {
                List<String> names = List.copyOf(strings(entry.getValue().getAsJsonArray()));
                for (String name : names)
                    if (!JAVA_NAME.matcher(name).matches() || !name.startsWith(namespace + "."))
                        throw invalid("Entry class outside package namespace");
                entries.put(entry.getKey(), names);
            }
            if (entries.values().stream().allMatch(List::isEmpty))
                throw invalid("At least one side entry is required");
            List<Hook> hooks = new ArrayList<>();
            if (value.has("mixins"))
                for (JsonElement hookElement : value.getAsJsonArray("mixins")) {
                    JsonObject hook = hookElement.getAsJsonObject();
                    keys(hook, Set.of("config", "side"));
                    String config = string(hook, "config"), side = string(hook, "side");
                    if (!files.containsKey(config)
                            || !config.endsWith(".json")
                            || !Set.of("common", "client", "server").contains(side))
                        throw invalid("Invalid Mixin declaration");
                    hooks.add(new Hook(config, side));
                }
            Map<String, String> targetMods =
                    value.has("mods") ? stringMap(value.getAsJsonObject("mods")) : Map.of();
            targetMods.forEach(ExtensionManifest::dependency);
            parsed.put(item.getKey(), new Variant(javaVersion, code, entries, hooks, targetMods));
        }
        variants = Map.copyOf(parsed);
        contentIdentity = digest(bytes);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public String javaNamespace() {
        return namespace;
    }

    public String contentIdentity() {
        return contentIdentity;
    }

    public Map<String, String> files() {
        return files;
    }

    public Map<String, Variant> variants() {
        return variants;
    }

    public Map<String, String> modDependencies() {
        return modDependencies;
    }

    public Map<String, String> modDependencies(String target) {
        Map<String, String> selected = new LinkedHashMap<>(modDependencies);
        var variant = variants.get(target);
        if (variant != null) selected.putAll(variant.mods());
        return Map.copyOf(selected);
    }

    public Map<String, String> packageDependencies() {
        return packageDependencies;
    }

    public Set<String> capabilities() {
        return capabilities;
    }

    public Set<String> requestedScopes() {
        return scopes;
    }

    public List<String> exports() {
        return exports;
    }

    public Map<String, String> documents() {
        return documents;
    }

    public Map<String, String> tests() {
        return tests;
    }

    public JsonObject json() {
        return source.deepCopy();
    }

    private Map<String, String> indexedFiles(JsonObject object, String key) {
        Map<String, String> values =
                stringMap(object.has(key) ? object.getAsJsonObject(key) : new JsonObject());
        values.forEach(
                (id, path) -> {
                    if (!ID.matcher(id).matches() || !files.containsKey(path))
                        throw invalid("Invalid " + key + " index");
                });
        return values;
    }

    static void keys(JsonObject object, Set<String> allowed) {
        if (object == null || !allowed.containsAll(object.keySet()))
            throw invalid("Unknown or missing object field");
    }

    static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw invalid("String required: " + key);
        return value.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        try {
            var value = object.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw invalid("Integer required: " + key);
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (RuntimeException invalid) {
            throw invalid("Integer required: " + key);
        }
    }

    static Set<String> strings(JsonArray array) {
        if (array == null) throw invalid("String array required");
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isString()
                    || item.getAsString().isBlank()
                    || !values.add(item.getAsString()))
                throw invalid("Invalid/duplicate string entry");
        }
        return Set.copyOf(values);
    }

    static Map<String, String> stringMap(JsonObject object) {
        if (object == null) throw invalid("String map required");
        Map<String, String> values = new LinkedHashMap<>();
        object.entrySet()
                .forEach(entry -> values.put(entry.getKey(), string(object, entry.getKey())));
        return Map.copyOf(values);
    }

    private static void dependency(String id, String requirement) {
        if (!ID.matcher(id).matches() || requirement.length() > 128 || requirement.isBlank())
            throw invalid("Invalid dependency");
        ExtensionVersion.accepts(requirement, "0.0.0");
    }

    public static String path(String value) {
        if (value == null
                || value.length() > 240
                || !value.matches("[A-Za-z0-9_./-]+")
                || value.startsWith("/")) throw invalid("Nonportable package path");
        for (String segment : value.split("/", -1))
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.endsWith(".")
                    || segment.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
                throw invalid("Unsafe package path");
        return value;
    }

    public static String digest(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static JsonObject object(byte[] bytes) {
        if (bytes.length > MAX_MANIFEST_BYTES) throw invalid("Metadata budget exceeded");
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8))
                || text.startsWith("\ufeff")) throw invalid("Metadata must be UTF-8 without BOM");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result = read(reader, 0);
            if (!result.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw invalid("One metadata object required");
            return result.getAsJsonObject();
        } catch (IOException | IllegalStateException | NumberFormatException failure) {
            throw new ExtensionFailure(
                    "EXTENSION_MANIFEST_INVALID", "Invalid package JSON", failure);
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 24) throw invalid("Metadata nesting budget exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name) || object.size() >= 4096)
                        throw invalid("Duplicate key or object budget exceeded");
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= 4096) throw invalid("Array budget exceeded");
                    array.add(read(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw invalid("Invalid JSON token");
        };
    }

    static ExtensionFailure invalid(String reason) {
        return failure("EXTENSION_MANIFEST_INVALID", reason);
    }

    static ExtensionFailure failure(String code, String reason) {
        return new ExtensionFailure(code, reason);
    }
}
