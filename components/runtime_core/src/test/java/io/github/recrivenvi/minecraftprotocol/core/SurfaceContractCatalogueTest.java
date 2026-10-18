package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProviders;
import io.github.recrivenvi.minecraftprotocol.probe.api.ReadProvider;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Cross-checks the formal catalogue and the production authorization/descriptor paths. */
class SurfaceContractCatalogueTest {
    private static final Set<String> POLICIES =
            Set.of(
                    "READ_COMPATIBLE",
                    "OPERATE_OR_TAKEOVER",
                    "TAKEOVER_REQUIRED",
                    "MODE_INDEPENDENT",
                    "COMMAND_ROUTE_DEPENDENT");
    private static final Set<String> UI_CONDITIONS =
            Set.of("screen", "ui.exists", "menu", "inventory");
    private static final Set<String> WORLD_CONDITIONS =
            Set.of("player", "block", "entity", "scene", "provider", "event", "recording");

    private static Path repositoryRoot() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null
                && !Files.isRegularFile(
                        root.resolve(
                                "components/protocol_schema/src/main/openapi/minecraft-control-v0.json")))
            root = root.getParent();
        assertNotNull(root, "Tests must run in this repository");
        return root;
    }

    private static JsonObject repositoryJson(String name) throws Exception {
        return JsonParser.parseString(Files.readString(repositoryRoot().resolve(name)))
                .getAsJsonObject();
    }

    @Test
    void retainedDiagnosticTargetListsMatchRegisteredAdaptersAndProductionRouteGuards()
            throws Exception {
        Path root = repositoryRoot();
        var matcher =
                java.util.regex.Pattern.compile("register\\(\"([^\"]+)\"\\)")
                        .matcher(Files.readString(root.resolve("settings.gradle.kts")));
        Set<String> targets = new HashSet<>(), experimental = new HashSet<>();
        while (matcher.find()) targets.add(matcher.group(1));
        assertFalse(targets.isEmpty());
        for (String target : targets) {
            String sourceSet = target.endsWith("-fabric") ? "client" : "main";
            String runtime =
                    target.endsWith("-fabric")
                            ? "FabricProbeRuntime"
                            : target.endsWith("-forge")
                                    ? "ForgeProbeRuntime"
                                    : "NeoForgeProbeRuntime";
            String source =
                    Files.readString(
                            root.resolve(
                                    "versions/"
                                            + target
                                            + "/src/"
                                            + sourceSet
                                            + "/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/"
                                            + runtime
                                            + ".java"));
            if (source.contains("implements Phase9AExperimentalService")) experimental.add(target);
        }
        assertFalse(experimental.isEmpty());
        assertTrue(experimental.size() < targets.size());
        String transport =
                Files.readString(
                        root.resolve(
                                "components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProbeTransport.java"));
        for (var item :
                repositoryJson(
                                "validations/conformance-agent_control/task/control-mode-surface.json")
                        .getAsJsonArray("retainedDiagnostics")) {
            var row = item.getAsJsonObject();
            String path = row.get("path").getAsString();
            int route = transport.indexOf("path.equals(\"" + path + "\")");
            assertTrue(route > 0, path);
            int branch = transport.lastIndexOf("} else if", route);
            assertTrue(branch >= 0, path);
            boolean optional =
                    transport
                            .substring(branch, route)
                            .contains("service instanceof Phase9AExperimentalService");
            Set<String> declared = new HashSet<>();
            for (var target : row.getAsJsonArray("targets"))
                assertTrue(declared.add(target.getAsString()));
            assertEquals(
                    optional ? experimental : targets,
                    declared,
                    path + " must describe actual registered route availability, not old versions");
            assertEquals(
                    path.startsWith("/v0/debug/") ? "OPERATE_OR_TAKEOVER" : "READ_COMPATIBLE",
                    row.get("modeRequirement").getAsString());
        }
    }

    @Test
    void actualProviderDescriptorsAndReadAdmissionKeepInternalSourcesOutOfUser() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReadProvider custom =
                new ReadProvider() {
                    public String id() {
                        return "catalogue:private_world";
                    }

                    public String perspective() {
                        return "server_authoritative";
                    }

                    public String threadAffinity() {
                        return "detached";
                    }

                    public CompletableFuture<JsonObject> read(JsonObject query) {
                        calls.incrementAndGet();
                        var value = new JsonObject();
                        value.addProperty("internal", true);
                        return CompletableFuture.completedFuture(value);
                    }
                };
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, arguments) -> {
                                    throw new AssertionError(
                                            "Unexpected native service read: " + method.getName());
                                });
        MinecraftProtocolProviders.register(custom);
        try (var state =
                new ProtocolState(
                        Set.of("read", "debug", "debug.world.read"),
                        "provider-catalogue",
                        ignored -> {})) {
            var observation = new ObservationEngine(service, new WorldReadEngine(service, state));
            Set<String> user = new HashSet<>();
            for (var item : observation.descriptors().getAsJsonArray("providers")) {
                var descriptor = item.getAsJsonObject();
                String id = descriptor.get("id").getAsString();
                var categories = new HashSet<String>();
                for (var category : descriptor.getAsJsonArray("readCategories"))
                    categories.add(category.getAsString());
                assertFalse(categories.isEmpty(), id);
                if (categories.contains("USER")) user.add(id);
                if (descriptor.get("thirdParty").getAsBoolean()
                        || id.startsWith("minecraft:server/")) {
                    assertEquals(Set.of("DEBUG"), categories, id);
                    var request = new JsonObject();
                    request.addProperty("providerId", id);
                    request.addProperty("category", "USER");
                    assertEquals(
                            "WORLD_USER_PERCEPTION_UNAVAILABLE",
                            assertThrows(
                                            ProtocolState.ProtocolException.class,
                                            () -> observation.read(request))
                                    .code());
                }
            }
            assertEquals(
                    Set.of(
                            "minecraft:client/player",
                            "minecraft:client/world/block",
                            "minecraft:client/world/entities",
                            "minecraft:client/world/scene",
                            "minecraft:capture/info"),
                    user);
            assertEquals(
                    0, calls.get(), "USER denial must occur before invoking a custom provider");
            var request = new JsonObject();
            request.addProperty("providerId", custom.id());
            request.addProperty("category", "DEBUG");
            var result = observation.read(request).get(2, TimeUnit.SECONDS);
            assertEquals("DEBUG", result.get("dataCategory").getAsString());
            assertEquals("untrusted_mod_provider", result.get("trust").getAsString());
            assertEquals(1, calls.get());
            assertEquals("available", state.leaseStatus().get("status").getAsString());
            assertFalse(state.hasScope("debug.write"));
            assertFalse(state.hasScope("input"));
        } finally {
            assertTrue(MinecraftProtocolProviders.unregister(custom.id()));
        }
    }

    @Test
    void allFormalHttpOperationsHaveExactlyOneCurrentModeCatalogueEntry() throws Exception {
        var schema =
                repositoryJson(
                        "components/protocol_schema/src/main/openapi/minecraft-control-v0.json");
        var catalogue =
                repositoryJson(
                        "validations/conformance-agent_control/task/control-mode-surface.json");
        Map<String, String> actual = new TreeMap<>(), expected = new TreeMap<>();
        Set<String> ids = new HashSet<>();
        for (var path : schema.getAsJsonObject("paths").entrySet()) {
            for (var operation : path.getValue().getAsJsonObject().entrySet()) {
                if (!Set.of("get", "post", "put", "patch", "delete", "options", "head", "trace")
                        .contains(operation.getKey())) continue;
                var value = operation.getValue().getAsJsonObject();
                String policy = value.get("x-agent-mode").getAsString();
                assertTrue(POLICIES.contains(policy), path.getKey());
                String id = value.get("operationId").getAsString();
                assertTrue(ids.add(id), "Duplicate operationId: " + id);
                actual.put(
                        operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(),
                        id + ":" + policy);
            }
        }
        for (var item : catalogue.getAsJsonArray("formalHttp")) {
            var value = item.getAsJsonObject();
            String key = value.get("method").getAsString() + " " + value.get("path").getAsString();
            assertNull(
                    expected.put(
                            key,
                            value.get("operationId").getAsString()
                                    + ":"
                                    + value.get("modeRequirement").getAsString()),
                    key);
        }
        assertEquals(
                expected,
                actual,
                "Formal routes cannot silently outgrow or disagree with the catalogue");
        assertEquals(actual.size(), ids.size());
        assertFalse(
                actual.keySet().stream()
                        .anyMatch(
                                key ->
                                        key.contains("storage/write")
                                                || key.contains("storage.write")));
    }

    @Test
    void productionOperationDescriptorsKeepLeaseArmAndEvidenceIndependent() {
        try (var state = new ProtocolState(Set.of("read"), "catalogue", ignored -> {})) {
            Set<String> ids = new HashSet<>();
            for (var item : state.descriptors().getAsJsonArray("operations")) {
                var value = item.getAsJsonObject();
                String id = value.get("id").getAsString(),
                        policy = value.get("modeRequirement").getAsString();
                assertTrue(ids.add(id));
                assertTrue(POLICIES.contains(policy));
                if (value.get("requiresControlLease").getAsBoolean())
                    assertEquals("TAKEOVER_REQUIRED", policy, id);
                if (value.get("requiresDebugArm").getAsBoolean())
                    assertEquals("OPERATE_OR_TAKEOVER", policy, id);
                if (id.startsWith("fixture.") || value.get("requiresDebugArm").getAsBoolean())
                    assertFalse(value.get("requiresControlLease").getAsBoolean(), id);
                if (id.startsWith("debug.evidence.")) {
                    assertEquals("READ_COMPATIBLE", policy, id);
                    assertFalse(value.get("requiresDebugArm").getAsBoolean());
                }
            }
            assertTrue(
                    ids.containsAll(
                            Set.of(
                                    "ui.get_tree",
                                    "ui.resolve",
                                    "ui.action",
                                    "pipeline.execute",
                                    "wait.until",
                                    "assert.that",
                                    "command.player.execute")));
            assertEquals("READ", state.modeStatus().get("mode").getAsString());
            assertEquals("available", state.leaseStatus().get("status").getAsString());
            assertFalse(state.hasScope("input"));
            assertFalse(state.hasScope("debug"));
        }
    }

    @Test
    void everyDeclaredConditionHasItsActualDomainGuardBeforeAnyServiceRead() throws Exception {
        var schema =
                repositoryJson(
                        "components/protocol_schema/src/main/openapi/minecraft-control-v0.json");
        Set<String> types = new HashSet<>();
        for (var value :
                schema.getAsJsonObject("components")
                        .getAsJsonObject("schemas")
                        .getAsJsonObject("AutomationCondition")
                        .getAsJsonObject("properties")
                        .getAsJsonObject("type")
                        .getAsJsonArray("enum")) types.add(value.getAsString());
        Set<String> covered = new HashSet<>(UI_CONDITIONS);
        covered.addAll(WORLD_CONDITIONS);
        covered.add("operation");
        assertEquals(covered, types, "New condition kinds require an explicit domain review");
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, arguments) -> {
                                    throw new AssertionError(
                                            "Authorization called service: " + method.getName());
                                });
        for (var scopes :
                List.of(
                        Set.<String>of(),
                        Set.of("read", "ui", "event"),
                        Set.of(
                                "read",
                                "ui",
                                "event",
                                "debug",
                                "debug.ui.read",
                                "debug.world.read"))) {
            try (var state = new ProtocolState(scopes, "condition-catalogue", ignored -> {});
                    var engine =
                            new ConditionEngine(
                                    service,
                                    state,
                                    null,
                                    null,
                                    null,
                                    new ObservationEngine(
                                            service, new WorldReadEngine(service, state)))) {
                String operationId =
                        state.startOperation(CompletableFuture.completedFuture(new JsonObject()))
                                .get("operationId")
                                .getAsString();
                for (String type : types) {
                    var condition = new JsonObject();
                    condition.addProperty("type", type);
                    condition.addProperty("operationId", operationId);
                    if (scopes.isEmpty()) {
                        assertEquals(
                                "SCOPE_DENIED",
                                assertThrows(
                                                ProtocolState.ProtocolException.class,
                                                () -> engine.authorize(condition))
                                        .code());
                        continue;
                    }
                    assertDoesNotThrow(() -> engine.authorize(condition), type + " USER");
                    condition.addProperty("category", "DEBUG");
                    if (!scopes.contains("debug") && !type.equals("operation"))
                        assertEquals(
                                "SCOPE_DENIED",
                                assertThrows(
                                                ProtocolState.ProtocolException.class,
                                                () -> engine.authorize(condition))
                                        .code(),
                                type);
                    else assertDoesNotThrow(() -> engine.authorize(condition), type + " DEBUG");
                }
            }
        }
    }
}
