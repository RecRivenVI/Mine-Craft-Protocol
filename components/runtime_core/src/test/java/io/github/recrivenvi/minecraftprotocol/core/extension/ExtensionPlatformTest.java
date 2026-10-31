package io.github.recrivenvi.minecraftprotocol.core.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.DeepObservationRequestContext;
import io.github.recrivenvi.minecraftprotocol.core.ObservationRevisionTracker;
import io.github.recrivenvi.minecraftprotocol.core.ProviderExecutionEngine;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSchemaRegistry;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionPlatformTest {
    @TempDir Path temporary;

    private static ExtensionPlatform.Environment environment(
            String target, PackageContext.Side side) {
        return new ExtensionPlatform.Environment(
                "product",
                target,
                side,
                Runtime.version().feature(),
                Map.of("upstream", "2.0.0"),
                PackageContext.class.getClassLoader());
    }

    private static JsonObject json(String text) {
        return ExtensionManifest.object(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void derivedLibraryIsReproducibleAndLoaderOnlySeesInertCoreGatedConfigurations()
            throws Exception {
        Path game = temporary.resolve("deterministic"),
                root = game.resolve("minecraft-protocol/extensions");
        Path directory = hookBundle(root, "deterministic", "extensiontest.deterministic");
        approve(root, directory, true);
        var env = environment("26.3-neoforge", PackageContext.Side.CLIENT);
        Path first = game.resolve("one.jar"), second = game.resolve("two.jar");
        ExtensionEarlyStage.write(new ExtensionStore(root), env, first);
        ExtensionEarlyStage.write(new ExtensionStore(root), env, second);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        try (var jar = new java.util.jar.JarFile(first.toFile())) {
            String configs = jar.getManifest().getMainAttributes().getValue("MixinConfigs");
            assertNotNull(configs);
            for (String config : configs.split(",")) {
                JsonObject value;
                try (var input = jar.getInputStream(jar.getJarEntry(config))) {
                    value = ExtensionManifest.object(input.readAllBytes());
                }
                assertEquals(ExtensionEarlyStage.MIXIN_PLUGIN, value.get("plugin").getAsString());
                assertEquals(
                        0,
                        value.getAsJsonArray("mixins").size(),
                        "No author class name is loaded before the core approval gate");
            }
        }
        assertNull(System.getProperty("extension_test.deterministic"));
    }

    @Test
    void earlyStagingChecksNativeBytesWithoutInitializingAnyPackageAndDistinguishesAppliedHooks()
            throws Exception {
        Path game = temporary.resolve("early");
        Path root = game.resolve("minecraft-protocol/extensions");
        Path directory = hookBundle(root, "early", "extensiontest.early");
        approve(root, directory, true);
        Path library = game.resolve("mods").resolve(ExtensionEarlyStage.FILE_NAME);
        ExtensionEarlyStage.write(
                new ExtensionStore(root),
                environment("26.3-neoforge", PackageContext.Side.CLIENT),
                library);
        assertNull(System.getProperty("extension_test.early"));
        try (var nativeLoader =
                new java.net.URLClassLoader(
                        new java.net.URL[] {library.toUri().toURL()},
                        PackageContext.class.getClassLoader())) {
            var env =
                    new ExtensionPlatform.Environment(
                            "product",
                            "26.3-neoforge",
                            PackageContext.Side.CLIENT,
                            Runtime.version().feature(),
                            Map.of("upstream", "2.0.0"),
                            nativeLoader);
            var early =
                    ExtensionEarlyBootstrap.boot(
                            game,
                            env,
                            new ExtensionEarlyBootstrap.Loader() {
                                public void prepare(Path file) {
                                    assertEquals(library, file);
                                }

                                public boolean isClassLoaded(String name) {
                                    return false;
                                }
                            });
            assertEquals(
                    "validated",
                    early.diagnostics().get("state").getAsString(),
                    early.diagnostics().toString());
            assertNull(System.getProperty("extension_test.early"));
            assertEquals(1, early.configurationPaths().size());
            var manifest = ExtensionStore.inspect(directory).manifest();
            assertFalse(early.prepared(manifest, env));
            assertFalse(
                    early.allows(
                            "extensiontest.early.mixin",
                            "extensiontest.early.mixin.Probe",
                            "extensiontest.victim.Target"));
            assertEquals(List.of("Probe"), early.mixins("extensiontest.early.mixin"));
            assertTrue(
                    early.allows(
                            "extensiontest.early.mixin",
                            "extensiontest.early.mixin.Probe",
                            "extensiontest.victim.Target"));
            assertFalse(
                    early.allows(
                            "extensiontest.early.mixin",
                            "extensiontest.early.mixin.Other",
                            "extensiontest.victim.Target"));
            assertFalse(
                    early.allows(
                            "extensiontest.early.mixin",
                            "extensiontest.early.mixin.Probe",
                            "java.lang.String"));
            assertFalse(
                    early.allows(
                            "unknown",
                            "extensiontest.early.mixin.Probe",
                            "extensiontest.victim.Target"));
            assertTrue(early.prepared(manifest, env));
            assertEquals(
                    0,
                    early.diagnostics()
                            .getAsJsonArray("configs")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("appliedTargets")
                            .size(),
                    "Configuration registration must not claim an actual transformation");
            try (var platform = new ExtensionPlatform(new ExtensionStore(root), env, early)) {
                assertEquals(1, platform.snapshot().size());
                assertEquals("initialized", System.getProperty("extension_test.early"));
            }
        } finally {
            System.clearProperty("extension_test.early");
        }
    }

    @Test
    void changedApprovalOrAlreadyLoadedMixinTargetNeverRegistersOrInitializesCode()
            throws Exception {
        for (boolean loaded : List.of(false, true)) {
            Path game = temporary.resolve("reject-early-" + loaded),
                    root = game.resolve("minecraft-protocol/extensions");
            String id = loaded ? "loaded" : "changed";
            Path directory = hookBundle(root, id, "extensiontest." + id);
            approve(root, directory, true);
            Path library = game.resolve("mods").resolve(ExtensionEarlyStage.FILE_NAME);
            ExtensionEarlyStage.write(
                    new ExtensionStore(root),
                    environment("26.3-neoforge", PackageContext.Side.CLIENT),
                    library);
            if (!loaded)
                Files.writeString(
                        root.resolve("approvals.json"), "{\"formatVersion\":1,\"packages\":{}}");
            try (var nativeLoader =
                    new java.net.URLClassLoader(
                            new java.net.URL[] {library.toUri().toURL()},
                            PackageContext.class.getClassLoader())) {
                var env =
                        new ExtensionPlatform.Environment(
                                "product",
                                "26.3-neoforge",
                                PackageContext.Side.CLIENT,
                                Runtime.version().feature(),
                                Map.of(),
                                nativeLoader);
                var early =
                        ExtensionEarlyBootstrap.boot(
                                game,
                                env,
                                new ExtensionEarlyBootstrap.Loader() {
                                    public void prepare(Path file) {}

                                    public boolean isClassLoaded(String name) {
                                        return loaded;
                                    }
                                });
                assertEquals(
                        loaded
                                ? "EXTENSION_MIXIN_TARGET_ALREADY_LOADED"
                                : "EXTENSION_EARLY_CONTENT_CHANGED",
                        early.diagnostics().get("state").getAsString(),
                        early.diagnostics().toString());
                assertTrue(early.configurationPaths().isEmpty());
                assertFalse(
                        early.allows(
                                "extensiontest." + id + ".mixin",
                                "extensiontest." + id + ".mixin.Probe",
                                "extensiontest.victim.Target"));
                assertNull(System.getProperty("extension_test." + id));
            }
        }
    }

    private Path hookBundle(Path root, String id, String namespace) throws Exception {
        Path directory = bundle(root, id, namespace, 3, false, null);
        Path code = directory.resolve("code/adapter.jar");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = new JarOutputStream(bytes);
                var input = new java.util.jar.JarInputStream(Files.newInputStream(code))) {
            for (var entry = input.getNextJarEntry();
                    entry != null;
                    entry = input.getNextJarEntry()) {
                output.putNextEntry(new JarEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
            }
            var writer = new org.objectweb.asm.ClassWriter(0);
            writer.visit(
                    org.objectweb.asm.Opcodes.V17,
                    org.objectweb.asm.Opcodes.ACC_PUBLIC,
                    (namespace + ".mixin.Probe").replace('.', '/'),
                    null,
                    "java/lang/Object",
                    null);
            var annotation = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
            var targets = annotation.visitArray("targets");
            targets.visit(null, "extensiontest.victim.Target");
            targets.visitEnd();
            annotation.visitEnd();
            writer.visitEnd();
            output.putNextEntry(
                    new JarEntry((namespace + ".mixin.Probe").replace('.', '/') + ".class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
        Files.write(code, bytes.toByteArray());
        JsonObject manifest =
                ExtensionManifest.object(Files.readAllBytes(directory.resolve("extension.json")));
        manifest.getAsJsonObject("files")
                .addProperty("code/adapter.jar", ExtensionManifest.digest(bytes.toByteArray()));
        Files.createDirectories(directory.resolve("mixins"));
        byte[] config =
                ("{\"package\":\""
                                + namespace
                                + ".mixin\",\"mixins\":[\"Probe\"],\"required\":true}")
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("mixins/probe.json"), config);
        manifest.getAsJsonObject("files")
                .addProperty("mixins/probe.json", ExtensionManifest.digest(config));
        manifest.getAsJsonObject("variants")
                .getAsJsonObject("26.3-neoforge")
                .add(
                        "mixins",
                        json("{\"hooks\":[{\"config\":\"mixins/probe.json\",\"side\":\"client\"}]}")
                                .getAsJsonArray("hooks"));
        Files.writeString(directory.resolve("extension.json"), manifest.toString() + "\n");
        return directory;
    }

    @Test
    void noCodeRunsWithoutExactContentAndScopeApproval() throws Exception {
        Path root = temporary.resolve("unapproved");
        Path packagePath = bundle(root, "sample", "extensiontest.sample", 7, false, null);
        assertNull(System.getProperty("extension_test.sample"));
        var unapproved =
                new ExtensionPlatform(
                        new ExtensionStore(root),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (manifest, env) -> false);
        assertEquals(0, unapproved.snapshot().size());
        assertTrue(unapproved.catalog().toString().contains("EXTENSION_APPROVAL_REQUIRED"));
        approve(root, packagePath, false);
        var ungranted =
                new ExtensionPlatform(
                        new ExtensionStore(root),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (manifest, env) -> false);
        assertTrue(ungranted.catalog().toString().contains("EXTENSION_APPROVAL_SCOPE_MISSING"));
        assertNull(System.getProperty("extension_test.sample"));
        approve(root, packagePath, true);
        try (var platform =
                new ExtensionPlatform(
                        new ExtensionStore(root),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (manifest, env) -> false)) {
            assertEquals("initialized", System.getProperty("extension_test.sample"));
            assertEquals(7, capture(platform).get("value").getAsInt());
            assertFalse(
                    ProviderSchemaRegistry.contains("minecraft-extension://sample/schemas/value"));
            assertTrue(
                    MinecraftProtocolProvidersV2.snapshot().stream()
                            .noneMatch(
                                    provider ->
                                            provider.descriptor()
                                                    .providerId()
                                                    .equals("sample:state")));
            assertEquals(
                    "sample",
                    platform.document("sample", "overview", false).get("packageId").getAsString());
            assertThrows(
                    ExtensionFailure.class, () -> platform.document("sample", "../outside", false));
        } finally {
            System.clearProperty("extension_test.sample");
        }
    }

    @Test
    void twoRuntimeInstancesUseDistinctCodeAndSchemasForTheSamePackageIdentity() throws Exception {
        Path a = temporary.resolve("runtime_a"), b = temporary.resolve("runtime_b");
        Path first = bundle(a, "isolated", "extensiontest.isolated", 1, false, null);
        Path second = bundle(b, "isolated", "extensiontest.isolated", 2, false, null);
        approve(a, first, true);
        approve(b, second, true);
        try (var left =
                        new ExtensionPlatform(
                                new ExtensionStore(a),
                                environment("26.3-neoforge", PackageContext.Side.CLIENT),
                                (m, e) -> false);
                var right =
                        new ExtensionPlatform(
                                new ExtensionStore(b),
                                environment("26.3-neoforge", PackageContext.Side.CLIENT),
                                (m, e) -> false)) {
            assertEquals(1, capture(left).get("value").getAsInt());
            assertEquals(2, capture(right).get("value").getAsInt());
            String schema = "minecraft-extension://isolated/schemas/value";
            assertTrue(left.validate("isolated:state", schema, json("{\"value\":1}")).valid());
            assertFalse(right.validate("isolated:state", schema, json("{\"value\":1}")).valid());
            left.close();
            assertEquals(2, capture(right).get("value").getAsInt());
        } finally {
            System.clearProperty("extension_test.isolated");
        }
    }

    @Test
    void staticCatalogAndDocumentsUseActualAuthenticatedTransportWithoutWorldDebugPermission()
            throws Exception {
        Path game = temporary.resolve("http");
        Path root = game.resolve("minecraft-protocol/extensions");
        Path source = bundle(root, "catalog", "extensiontest.catalog", 12, false, null);
        approve(root, source, true);
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty("minecraft.protocol.scopes", "read");
        var service =
                (io.github.recrivenvi.minecraftprotocol.core.ProbeService)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {
                                    io.github.recrivenvi.minecraftprotocol.core.ProbeService.class
                                },
                                (proxy, method, args) -> {
                                    if (method.getName().equals("extensionEnvironment"))
                                        return environment(
                                                "26.3-neoforge", PackageContext.Side.CLIENT);
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("extensionEarlyHooks"))
                                        return (ExtensionPlatform.EarlyHooks)
                                                (manifest, env) -> false;
                                    if (method.getName().equals("extensionThreadOwners"))
                                        return java.util.Map.of();
                                    if (method.getName().equals("artifactRoot"))
                                        return game.resolve("artifacts");
                                    return java.util.concurrent.CompletableFuture.completedFuture(
                                            new JsonObject());
                                });
        try (var transport =
                io.github.recrivenvi.minecraftprotocol.core.ProbeTransport.forInstance(
                        service, game, "26.3-neoforge")) {
            transport.startAsync();
            int port = transport.boundPort().get(5, java.util.concurrent.TimeUnit.SECONDS);
            String token = Files.readString(game.resolve("minecraft-protocol/token")).trim();
            var client =
                    java.net.http.HttpClient.newBuilder()
                            .proxy(
                                    new java.net.ProxySelector() {
                                        public java.util.List<java.net.Proxy> select(
                                                java.net.URI uri) {
                                            return java.util.List.of(java.net.Proxy.NO_PROXY);
                                        }

                                        public void connectFailed(
                                                java.net.URI uri,
                                                java.net.SocketAddress address,
                                                java.io.IOException failure) {}
                                    })
                            .build();
            for (String path :
                    java.util.List.of(
                            "/v0/extensions",
                            "/v0/extensions/document?packageId=catalog&documentId=overview")) {
                var request =
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create("http://127.0.0.1:" + port + path))
                                .header("Authorization", "Bearer " + token)
                                .GET()
                                .build();
                var response =
                        client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                assertTrue(response.body().contains("catalog"), response.body());
            }
            var diagnostics =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/v0/extensions/resources"))
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    403,
                    diagnostics.statusCode(),
                    "Static catalog authority cannot read managed Debug resource state");
            var unauthenticated =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:" + port + "/v0/extensions"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthenticated.statusCode());
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
            System.clearProperty("extension_test.catalog");
        }
    }

    @Test
    void productionExecutionEngineUsesLocalSchemaAndStillEnforcesReadAuthority() throws Exception {
        Path root = temporary.resolve("execution");
        Path source = bundle(root, "execution", "extensiontest.execution", 42, false, null);
        approve(root, source, true);
        try (var platform =
                        new ExtensionPlatform(
                                new ExtensionStore(root),
                                environment("26.3-neoforge", PackageContext.Side.CLIENT),
                                (m, e) -> false);
                var engine = new ProviderExecutionEngine(new ObservationRevisionTracker())) {
            engine.installProviders(platform);
            JsonObject request =
                    json(
                            "{\"perspective\":\"client_known\",\"providerIds\":[\"execution:state\"]}");
            var denied =
                    engine.execute(
                                    request,
                                    new JsonArray(),
                                    new DeepObservationRequestContext(
                                            java.util.Set.of("read"),
                                            "test",
                                            "denied",
                                            "test",
                                            0,
                                            event -> {}))
                            .join();
            assertTrue(denied.toString().contains("provider_scope_denied"), denied.toString());
            var accepted =
                    engine.execute(
                                    request,
                                    new JsonArray(),
                                    new DeepObservationRequestContext(
                                            java.util.Set.of("read", "debug.world.read"),
                                            "test",
                                            "allowed",
                                            "test",
                                            0,
                                            event -> {}))
                            .join();
            assertTrue(accepted.toString().contains("42"), accepted.toString());
            assertTrue(accepted.toString().contains("completed"), accepted.toString());
            platform.close();
            assertTrue(
                    engine.providers().stream()
                            .noneMatch(p -> p.descriptor().providerId().equals("execution:state")));
        } finally {
            System.clearProperty("extension_test.execution");
        }
    }

    @Test
    void initializationFailureRollsBackTheWholePackageWithoutBreakingAnotherPackage()
            throws Exception {
        Path root = temporary.resolve("rollback");
        Path broken = bundle(root, "broken", "extensiontest.broken", 1, true, null);
        Path healthy = bundle(root, "healthy", "extensiontest.healthy", 2, false, null);
        approve(root, broken, true);
        approve(root, healthy, true);
        try (var platform =
                new ExtensionPlatform(
                        new ExtensionStore(root),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (m, e) -> false)) {
            assertEquals(1, platform.snapshot().size());
            assertEquals("healthy:state", platform.snapshot().get(0).descriptor().providerId());
            assertFalse(platform.owns("broken:state"));
            assertTrue(platform.catalog().toString().contains("EXTENSION_INITIALIZATION_FAILED"));
            assertTrue(platform.catalog().toString().contains("healthy:state"));
        } finally {
            System.clearProperty("extension_test.broken");
            System.clearProperty("extension_test.healthy");
        }
    }

    @Test
    void sideAndTargetGatesRunBeforeEntryClassInitialization() throws Exception {
        Path root = temporary.resolve("side");
        Path candidate = bundle(root, "client_only", "extensiontest.clientonly", 1, false, null);
        approve(root, candidate, true);
        for (var env :
                List.of(
                        environment("26.3-neoforge", PackageContext.Side.SERVER),
                        environment("1.21.1-neoforge", PackageContext.Side.CLIENT))) {
            try (var platform =
                    new ExtensionPlatform(new ExtensionStore(root), env, (m, e) -> false)) {
                assertTrue(platform.catalog().toString().contains("not_applicable"));
                assertTrue(platform.snapshot().isEmpty());
                assertNull(System.getProperty("extension_test.client_only"));
            }
        }
    }

    @Test
    void dependencyCyclesAndDuplicateClassesFailBeforeAnyEntrypointRuns() throws Exception {
        Path root = temporary.resolve("cycle");
        Path a = bundle(root, "cycle_a", "extensiontest.cycle_a", 1, false, "cycle_b");
        Path b = bundle(root, "cycle_b", "extensiontest.cycle_b", 2, false, "cycle_a");
        approve(root, a, true);
        approve(root, b, true);
        try (var platform =
                new ExtensionPlatform(
                        new ExtensionStore(root),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (m, e) -> false)) {
            assertTrue(platform.catalog().toString().contains("EXTENSION_DEPENDENCY_CYCLE"));
            assertTrue(platform.snapshot().isEmpty());
            assertNull(System.getProperty("extension_test.cycle_a"));
            assertNull(System.getProperty("extension_test.cycle_b"));
        }
        Path duplicate = temporary.resolve("duplicate");
        Path c = bundle(duplicate, "same_a", "extensiontest.same", 1, false, null);
        Path d = bundle(duplicate, "same_b", "extensiontest.same", 2, false, null);
        approve(duplicate, c, true);
        approve(duplicate, d, true);
        try (var platform =
                new ExtensionPlatform(
                        new ExtensionStore(duplicate),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (m, e) -> false)) {
            assertTrue(platform.catalog().toString().contains("EXTENSION_DUPLICATE_CLASS"));
            assertTrue(platform.snapshot().isEmpty());
            assertNull(System.getProperty("extension_test.same_a"));
            assertNull(System.getProperty("extension_test.same_b"));
        }
    }

    @Test
    void zipAndDirectoryIdentitiesMatchAndChangedDocumentsAreNotPublished() throws Exception {
        Path root = temporary.resolve("zip");
        Path directory = bundle(root, "zipped", "extensiontest.zipped", 1, false, null);
        Path zip = temporary.resolve("zipped.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip));
                var walk = Files.walk(directory)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(
                        new ZipEntry(directory.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        assertEquals(
                ExtensionStore.inspect(directory).manifest().contentIdentity(),
                ExtensionStore.inspect(zip).manifest().contentIdentity());
        approve(root, directory, true);
        try (var platform =
                new ExtensionPlatform(
                        new ExtensionStore(root),
                        environment("26.3-neoforge", PackageContext.Side.CLIENT),
                        (m, e) -> false)) {
            Files.writeString(directory.resolve("docs/overview.md"), "changed after approval");
            var failure =
                    assertThrows(
                            ExtensionFailure.class,
                            () -> platform.document("zipped", "overview", false));
            assertEquals("EXTENSION_CONTENT_CHANGED", failure.code());
        } finally {
            System.clearProperty("extension_test.zipped");
        }
        assertThrows(ExtensionFailure.class, () -> ExtensionStore.inspect(directory));
    }

    @Test
    void strictMetadataRejectsDuplicateKeysUnsafePathsUnsupportedGrammarAndWrongNumbers() {
        assertThrows(ExtensionFailure.class, () -> json("{\"sdkApi\":1,\"sdkApi\":2}"));
        for (String path :
                List.of(
                        "../escape",
                        "a/../escape",
                        "/absolute",
                        "a\\b",
                        "C:/drive",
                        "con.txt",
                        "a//b"))
            assertThrows(ExtensionFailure.class, () -> ExtensionManifest.path(path));
        assertTrue(ExtensionVersion.accepts("[2.0,3.0)", "2.4.0"));
        assertFalse(ExtensionVersion.accepts("[2.0,3.0)", "3.0.0"));
        assertTrue(ExtensionVersion.accepts("1.6.0-pre234", "1.6.0-pre234"));
        assertFalse(ExtensionVersion.accepts("[1.6,2.0)", "1.6.0-pre234"));
        assertThrows(ExtensionFailure.class, () -> ExtensionVersion.accepts("^2.0", "2.1"));
    }

    private static JsonObject capture(ExtensionPlatform platform) {
        return platform.snapshot()
                .get(0)
                .capture(
                        new AgentDataProviderV2.ReadContext(
                                new JsonObject(),
                                "client_known",
                                false,
                                System.currentTimeMillis() + 1000,
                                1024))
                .join()
                .getAsJsonObject("data");
    }

    private Path bundle(
            Path root, String id, String namespace, int value, boolean fail, String dependency)
            throws Exception {
        Path compile = Files.createTempDirectory(temporary, "compile-");
        String schema = "minecraft-extension://" + id + "/schemas/value";
        String source =
                """
                package %s;
                import com.google.gson.JsonObject;
                import java.util.List;
                import java.util.concurrent.CompletableFuture;
                import io.github.recrivenvi.minecraftprotocol.probe.api.*;
                import io.github.recrivenvi.minecraftprotocol.probe.extension.*;
                public final class Entry implements PackageExtension {
                    static { System.setProperty("extension_test.%s", "initialized"); }
                    public void initialize(PackageContext context) {
                        context.schema("%s", data -> data.has("value") && data.get("value").getAsInt() == %d
                            ? ProviderSchemaRegistry.ValidationResult.pass() : ProviderSchemaRegistry.ValidationResult.fail("wrong value"));
                        context.schema("%s/query", data -> ProviderSchemaRegistry.ValidationResult.pass());
                        context.provider(new AgentDataProviderV2() {
                            public Descriptor descriptor() {
                                return new Descriptor("%s:state", "1", List.of("snapshot"), "%s", "%s/query",
                                    "detached_provider_worker", List.of("client_known"), "none", "complete", true, false, false, false, false,
                                    "snapshot_change_sequence", "query_view", null, false, "snapshot_diff_delta",
                                    new DebugDeclaration(false, null, null, null, false, false, "none", "none"), List.of("debug.world.read"));
                            }
                            public CompletableFuture<JsonObject> capture(ReadContext context) {
                                JsonObject data = new JsonObject(); data.addProperty("value", %d);
                                JsonObject envelope = new JsonObject(); envelope.addProperty("schemaVersion", "1");
                                envelope.add("data", data);
                                return CompletableFuture.completedFuture(envelope);
                            }
                        });
                        if (%s) throw new IllegalStateException("injected initialization failure");
                    }
                }
                """
                        .formatted(
                                namespace, id, schema, value, schema, id, schema, schema, value,
                                fail);
        Path javaSource = compile.resolve("Entry.java");
        Files.writeString(javaSource, source);
        String classpath =
                Path.of(
                                PackageContext.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        + java.io.File.pathSeparator
                        + Path.of(
                                JsonObject.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI());
        int code =
                ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                null,
                                null,
                                "--release",
                                "17",
                                "-classpath",
                                classpath,
                                "-d",
                                compile.toString(),
                                javaSource.toString());
        assertEquals(0, code);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = new JarOutputStream(bytes);
                var walk = Files.walk(compile)) {
            for (Path file : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
                output.putNextEntry(
                        new JarEntry(compile.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        Path directory = root.resolve("packages").resolve(id);
        Files.createDirectories(directory.resolve("code"));
        Files.createDirectories(directory.resolve("docs"));
        Files.write(directory.resolve("code/adapter.jar"), bytes.toByteArray());
        Files.writeString(directory.resolve("docs/overview.md"), "# Package\n");
        JsonObject manifest = new JsonObject();
        manifest.addProperty("formatVersion", 1);
        manifest.addProperty("sdkApi", 1);
        manifest.addProperty("id", id);
        manifest.addProperty("version", "1.0.0");
        manifest.addProperty("javaNamespace", namespace);
        JsonObject files = new JsonObject();
        files.addProperty("code/adapter.jar", ExtensionManifest.digest(bytes.toByteArray()));
        files.addProperty(
                "docs/overview.md",
                ExtensionManifest.digest(
                        Files.readAllBytes(directory.resolve("docs/overview.md"))));
        manifest.add("files", files);
        JsonObject variants =
                json(
                        "{\"26.3-neoforge\":{\"javaVersion\":17,\"code\":[\"code/adapter.jar\"],\"entrypoints\":{\"client\":[\""
                                + namespace
                                + ".Entry\"]}}}");
        manifest.add("variants", variants);
        manifest.add("capabilities", array(id + ":state"));
        manifest.add("requestedScopes", array("read", "debug.world.read"));
        JsonObject documents = new JsonObject();
        documents.addProperty("overview", "docs/overview.md");
        manifest.add("documents", documents);
        if (dependency != null) {
            JsonObject deps = new JsonObject(), packages = new JsonObject();
            packages.addProperty(dependency, "1.0.0");
            deps.add("packages", packages);
            manifest.add("dependencies", deps);
        }
        Files.writeString(directory.resolve("extension.json"), manifest + "\n");
        return directory;
    }

    private static JsonArray array(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static void approve(Path root, Path directory, boolean scopes) throws Exception {
        Path file = root.resolve("approvals.json");
        JsonObject approvals =
                Files.exists(file)
                        ? ExtensionManifest.object(Files.readAllBytes(file))
                        : json("{\"formatVersion\":1,\"packages\":{}}");
        var manifest = ExtensionStore.inspect(directory).manifest();
        JsonObject approval = new JsonObject();
        approval.addProperty("contentIdentity", manifest.contentIdentity());
        approval.add("scopes", scopes ? array("read", "debug.world.read") : array("read"));
        approvals.getAsJsonObject("packages").add(manifest.id(), approval);
        Files.writeString(file, approvals + "\n");
    }
}
