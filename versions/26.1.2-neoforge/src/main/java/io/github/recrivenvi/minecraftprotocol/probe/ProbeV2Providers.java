package io.github.recrivenvi.minecraftprotocol.probe;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSchemaRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class ProbeV2Providers {
    static final String SNAPSHOT_SCHEMA = "minecraft_protocol_probe://schemas/provider-test-snapshot-v1";
    static final String QUERY_SCHEMA = "minecraft_protocol_probe://schemas/provider-test-query-v1";
    static final String DEBUG_SCHEMA = "minecraft_protocol_probe://schemas/provider-test-debug-v1";

    private ProbeV2Providers() {
    }

    static void registerAll() {
        registerSchemas();
        MinecraftProtocolProvidersV2.register(new TestProvider("safe", "none", true, false, "detached_provider_worker", List.of("server_authoritative", "client_known"), List.of("read"), Policy.NONE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("lazy", "lazy_initialization", false, true, "detached_provider_worker", List.of("server_authoritative", "client_known"), List.of("read"), Policy.NONE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("scope", "none", true, false, "detached_provider_worker", List.of("server_authoritative"), List.of("read.internal"), Policy.NONE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("server-thread", "none", true, false, "server_thread", List.of("server_authoritative"), List.of("read"), Policy.NONE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("client-thread", "none", true, false, "client_thread", List.of("client_known"), List.of("read"), Policy.NONE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("render-thread", "none", true, false, "render_thread", List.of("client_known"), List.of("read"), Policy.NONE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("load-data", "none", false, false, "detached_provider_worker", List.of("server_authoritative"), List.of("read"), Policy.LOAD, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("storage", "none", false, false, "detached_provider_worker", List.of("server_authoritative"), List.of("read"), Policy.STORAGE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("mutate", "none", false, false, "server_thread", List.of("server_authoritative"), List.of("read"), Policy.MUTATE, "provider_revision"));
        MinecraftProtocolProvidersV2.register(new TestProvider("snapshot-fallback", "none", true, false, "detached_provider_worker", List.of("server_authoritative"), List.of("read"), Policy.NONE, "snapshot_change_sequence"));
        MinecraftProtocolProvidersV2.register(new RevisionContractProvider(
                "fallback-query", RevisionMode.FALLBACK_QUERY));
        MinecraftProtocolProvidersV2.register(new RevisionContractProvider(
                "query-view", RevisionMode.QUERY_VIEW));
        MinecraftProtocolProvidersV2.register(new RevisionContractProvider(
                "native-regression", RevisionMode.NATIVE_REGRESSION));
        MinecraftProtocolProvidersV2.register(new RevisionContractProvider(
                "native-inconsistent", RevisionMode.NATIVE_INCONSISTENT));
        MinecraftProtocolProvidersV2.register(new FailureProvider("failure", FailureMode.THROW));
        MinecraftProtocolProvidersV2.register(new FailureProvider("timeout", FailureMode.TIMEOUT));
        MinecraftProtocolProvidersV2.register(new FailureProvider("late-success", FailureMode.LATE_SUCCESS));
        MinecraftProtocolProvidersV2.register(new FailureProvider("blocking-before-future", FailureMode.BLOCKING_ENTRY));
        MinecraftProtocolProvidersV2.register(new FailureProvider("oversized", FailureMode.OVERSIZED));
        MinecraftProtocolProvidersV2.register(new FailureProvider("invalid", FailureMode.VERSION));
        MinecraftProtocolProvidersV2.register(new FailureProvider("schema-missing", FailureMode.MISSING_FIELD));
        MinecraftProtocolProvidersV2.register(new FailureProvider("schema-type", FailureMode.WRONG_TYPE));
        MinecraftProtocolProvidersV2.register(new FailureProvider("schema-nested", FailureMode.INVALID_NESTED));
    }

    private static void registerSchemas() {
        if (!ProviderSchemaRegistry.contains(SNAPSHOT_SCHEMA)) {
            ProviderSchemaRegistry.register(SNAPSHOT_SCHEMA, ProbeV2Providers::validateSnapshot);
            ProviderSchemaRegistry.register(QUERY_SCHEMA, ProbeV2Providers::validateQuery);
            ProviderSchemaRegistry.register(DEBUG_SCHEMA, value ->
                    value.has("operation") && value.get("operation").isJsonPrimitive()
                            ? ProviderSchemaRegistry.ValidationResult.pass()
                            : ProviderSchemaRegistry.ValidationResult.fail("missing_operation"));
        }
    }

    private static ProviderSchemaRegistry.ValidationResult validateSnapshot(JsonObject value) {
        if (!value.has("temperature") || !value.get("temperature").isJsonPrimitive()
                || !value.getAsJsonPrimitive("temperature").isNumber()) {
            return ProviderSchemaRegistry.ValidationResult.fail("temperature_must_be_number");
        }
        if (!value.has("meta") || !value.get("meta").isJsonObject()) {
            return ProviderSchemaRegistry.ValidationResult.fail("meta_must_be_object");
        }
        JsonObject meta = value.getAsJsonObject("meta");
        if (!meta.has("label") || !meta.get("label").isJsonPrimitive()
                || !meta.getAsJsonPrimitive("label").isString()) {
            return ProviderSchemaRegistry.ValidationResult.fail("meta.label_must_be_string");
        }
        if (!meta.has("details") || !meta.get("details").isJsonObject()) {
            return ProviderSchemaRegistry.ValidationResult.fail("meta.details_must_be_object");
        }
        JsonObject details = meta.getAsJsonObject("details");
        if (!details.has("enabled") || !details.get("enabled").isJsonPrimitive()
                || !details.getAsJsonPrimitive("enabled").isBoolean()) {
            return ProviderSchemaRegistry.ValidationResult.fail("meta.details.enabled_must_be_boolean");
        }
        if (value.has("payload") && (!value.get("payload").isJsonPrimitive()
                || !value.getAsJsonPrimitive("payload").isString())) {
            return ProviderSchemaRegistry.ValidationResult.fail("payload_must_be_string");
        }
        if (value.has("threadName") && (!value.get("threadName").isJsonPrimitive()
                || !value.getAsJsonPrimitive("threadName").isString())) {
            return ProviderSchemaRegistry.ValidationResult.fail("threadName_must_be_string");
        }
        return ProviderSchemaRegistry.ValidationResult.pass();
    }

    private static ProviderSchemaRegistry.ValidationResult validateQuery(JsonObject value) {
        for (String key : value.keySet()) {
            if (!key.equals("probe") && !key.equals("correlation")) {
                return ProviderSchemaRegistry.ValidationResult.fail("unknown_query_field:" + key);
            }
        }
        if (value.has("probe") && (!value.get("probe").isJsonPrimitive()
                || !value.getAsJsonPrimitive("probe").isString())) {
            return ProviderSchemaRegistry.ValidationResult.fail("probe_must_be_string");
        }
        if (value.has("correlation")) {
            if (!value.get("correlation").isJsonObject()) {
                return ProviderSchemaRegistry.ValidationResult.fail("correlation_must_be_object");
            }
            JsonObject correlation = value.getAsJsonObject("correlation");
            if (!correlation.has("id") || !correlation.get("id").isJsonPrimitive()
                    || !correlation.getAsJsonPrimitive("id").isString()) {
                return ProviderSchemaRegistry.ValidationResult.fail("correlation.id_must_be_string");
            }
        }
        return ProviderSchemaRegistry.ValidationResult.pass();
    }

    private static AgentDataProviderV2.Descriptor descriptor(
            String name,
            String effects,
            boolean safe,
            boolean mayInitialize,
            String affinity,
            List<String> perspectives,
            List<String> scopes,
            Policy policy,
            String revisionSource) {
        return descriptor(
                name, effects, safe, mayInitialize, affinity, perspectives, scopes,
                policy, revisionSource, "resource");
    }

    private static AgentDataProviderV2.Descriptor descriptor(
            String name,
            String effects,
            boolean safe,
            boolean mayInitialize,
            String affinity,
            List<String> perspectives,
            List<String> scopes,
            Policy policy,
            String revisionSource,
            String revisionScope) {
        boolean mayMutate = policy == Policy.MUTATE;
        return new AgentDataProviderV2.Descriptor(
                "minecraft_protocol_probe:" + name,
                "1",
                List.of("snapshot"),
                SNAPSHOT_SCHEMA,
                QUERY_SCHEMA,
                affinity,
                perspectives,
                effects,
                "complete",
                safe,
                mayInitialize,
                policy == Policy.LOAD,
                policy == Policy.STORAGE,
                mayMutate,
                revisionSource,
                revisionScope,
                revisionScope.equals("resource") ? SNAPSHOT_SCHEMA : "",
                revisionScope.equals("resource"),
                name.equals("safe") ? "snapshot_diff_delta" : "none",
                new AgentDataProviderV2.DebugDeclaration(
                        mayMutate, mayMutate ? DEBUG_SCHEMA : "", "debug", true),
                scopes);
    }

    private static JsonObject result(AgentDataProviderV2.Descriptor descriptor, long revision) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", descriptor.schemaVersion());
        JsonObject revisionState = new JsonObject();
        revisionState.addProperty("temperature", 20.5D);
        JsonObject meta = new JsonObject();
        meta.addProperty("label", descriptor.providerId());
        JsonObject details = new JsonObject();
        details.addProperty("enabled", true);
        meta.add("details", details);
        revisionState.add("meta", meta);
        JsonObject data = revisionState.deepCopy();
        data.addProperty("threadName", Thread.currentThread().getName());
        result.add("data", data);
        result.add("revisionState", revisionState);
        result.addProperty("providerRevision", revision);
        return result;
    }

    private enum Policy { NONE, LOAD, STORAGE, MUTATE }

    private enum RevisionMode {
        FALLBACK_QUERY, QUERY_VIEW, NATIVE_REGRESSION, NATIVE_INCONSISTENT
    }

    private static final class RevisionContractProvider implements AgentDataProviderV2 {
        private final Descriptor descriptor;
        private final RevisionMode mode;
        private final AtomicLong invocations = new AtomicLong();

        private RevisionContractProvider(String name, RevisionMode mode) {
            this.mode = mode;
            this.descriptor = ProbeV2Providers.descriptor(
                    name, "none", true, false, "detached_provider_worker",
                    List.of("server_authoritative"), List.of("read"), Policy.NONE,
                    mode == RevisionMode.FALLBACK_QUERY || mode == RevisionMode.QUERY_VIEW
                            ? "snapshot_change_sequence" : "provider_revision",
                    mode == RevisionMode.QUERY_VIEW ? "query_view" : "resource");
        }

        @Override
        public Descriptor descriptor() {
            return this.descriptor;
        }

        @Override
        public CompletableFuture<JsonObject> capture(ReadContext context) {
            long invocation = this.invocations.incrementAndGet();
            long revision = this.mode == RevisionMode.NATIVE_REGRESSION
                    ? invocation == 1L ? 10L : 9L
                    : 10L;
            JsonObject result = ProbeV2Providers.result(this.descriptor, revision);
            if (this.mode == RevisionMode.FALLBACK_QUERY || this.mode == RevisionMode.QUERY_VIEW) {
                result.getAsJsonObject("data").addProperty(
                        "view", context.query().has("probe")
                                ? context.query().get("probe").getAsString() : "none");
            }
            if (this.mode == RevisionMode.NATIVE_INCONSISTENT && invocation > 1L) {
                result.getAsJsonObject("data").addProperty("temperature", 21.0D);
                result.getAsJsonObject("revisionState").addProperty("temperature", 21.0D);
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final class TestProvider implements AgentDataProviderV2 {
        private final Descriptor descriptor;
        private final AtomicLong invocations = new AtomicLong();
        private final AtomicLong semanticRevision = new AtomicLong(1L);

        private TestProvider(
                String name,
                String effects,
                boolean safe,
                boolean mayInitialize,
                String affinity,
                List<String> perspectives,
                List<String> scopes,
                Policy policy,
                String revisionSource) {
            this.descriptor = ProbeV2Providers.descriptor(
                    name, effects, safe, mayInitialize, affinity, perspectives, scopes, policy, revisionSource);
        }

        @Override
        public Descriptor descriptor() {
            return this.descriptor;
        }

        @Override
        public CompletableFuture<JsonObject> capture(ReadContext context) {
            this.invocations.incrementAndGet();
            return CompletableFuture.completedFuture(result(this.descriptor, this.semanticRevision.get()));
        }
    }

    private enum FailureMode {
        THROW, TIMEOUT, LATE_SUCCESS, BLOCKING_ENTRY, OVERSIZED,
        VERSION, MISSING_FIELD, WRONG_TYPE, INVALID_NESTED
    }

    private static final class FailureProvider implements AgentDataProviderV2 {
        private final Descriptor descriptor;
        private final FailureMode mode;

        private FailureProvider(String name, FailureMode mode) {
            this.descriptor = ProbeV2Providers.descriptor(
                    name, "none", true, false, "detached_provider_worker",
                    List.of("server_authoritative", "client_known"), List.of("read"),
                    Policy.NONE, "provider_revision");
            this.mode = mode;
        }

        @Override
        public Descriptor descriptor() {
            return this.descriptor;
        }

        @Override
        public CompletableFuture<JsonObject> capture(ReadContext context) {
            return switch (this.mode) {
                case THROW -> CompletableFuture.failedFuture(
                        new IllegalStateException("intentional provider failure"));
                case TIMEOUT -> new CompletableFuture<>();
                case LATE_SUCCESS -> CompletableFuture.supplyAsync(
                        () -> result(this.descriptor, 1L),
                        CompletableFuture.delayedExecutor(300L, TimeUnit.MILLISECONDS));
                case BLOCKING_ENTRY -> {
                    try {
                        Thread.sleep(75L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    yield CompletableFuture.completedFuture(result(this.descriptor, 1L));
                }
                case OVERSIZED -> {
                    JsonObject oversized = result(this.descriptor, 1L);
                    oversized.getAsJsonObject("data").addProperty(
                            "payload", "x".repeat(context.byteBudget() + 1));
                    yield CompletableFuture.completedFuture(oversized);
                }
                case VERSION -> {
                    JsonObject invalid = result(this.descriptor, 1L);
                    invalid.addProperty("schemaVersion", "wrong");
                    yield CompletableFuture.completedFuture(invalid);
                }
                case MISSING_FIELD -> {
                    JsonObject invalid = result(this.descriptor, 1L);
                    invalid.getAsJsonObject("data").remove("temperature");
                    yield CompletableFuture.completedFuture(invalid);
                }
                case WRONG_TYPE -> {
                    JsonObject invalid = result(this.descriptor, 1L);
                    invalid.getAsJsonObject("data").addProperty("temperature", "hello");
                    yield CompletableFuture.completedFuture(invalid);
                }
                case INVALID_NESTED -> {
                    JsonObject invalid = result(this.descriptor, 1L);
                    invalid.getAsJsonObject("data").getAsJsonObject("meta")
                            .addProperty("details", "not-an-object");
                    yield CompletableFuture.completedFuture(invalid);
                }
            };
        }
    }
}
