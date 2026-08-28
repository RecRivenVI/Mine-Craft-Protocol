package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSchemaRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProviderContractHardeningTest {
    @Test
    void missingScopeAndUnsupportedPerspectiveNeverInvokeProvider() {
        Fixture fixture = fixture("policy");
        AtomicInteger invocations = new AtomicInteger();
        AgentDataProviderV2 scoped = provider(
                fixture, "scoped", List.of("read.internal"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                context -> {
                    invocations.incrementAndGet();
                    return CompletableFuture.completedFuture(validResult(1));
                });
        MinecraftProtocolProvidersV2.register(scoped);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject denied = first(engine, request(scoped.descriptor().providerId(), "server_authoritative", false),
                    context(Set.of("read"), fixture.audit));
            assertEquals("permission_denied", denied.get("status").getAsString());
            assertEquals(0, invocations.get());

            AgentDataProviderV2 clientOnly = provider(
                    fixture, "client-only", List.of("read"), List.of("client_known"),
                    "detached_provider_worker", false, false, false, false, true,
                    ignored -> {
                        invocations.incrementAndGet();
                        return CompletableFuture.completedFuture(validResult(1));
                    });
            MinecraftProtocolProvidersV2.register(clientOnly);
            JsonObject skipped = first(engine,
                    request(clientOnly.descriptor().providerId(), "server_authoritative", false),
                    context(Set.of("read"), fixture.audit));
            assertEquals("unsupported_perspective", skipped.get("reason").getAsString());
            assertEquals(0, invocations.get());
        }
    }

    @Test
    void effectPolicyDoesNotTurnIntoLoadStorageOrMutationAuthorization() {
        Fixture fixture = fixture("effects");
        for (String mode : List.of("load", "storage", "mutate")) {
            AtomicInteger invocations = new AtomicInteger();
            AgentDataProviderV2 provider = provider(
                    fixture, mode, List.of("read"), List.of("server_authoritative"),
                    "detached_provider_worker",
                    mode.equals("load"), mode.equals("storage"), mode.equals("mutate"), false, false,
                    ignored -> {
                        invocations.incrementAndGet();
                        return CompletableFuture.completedFuture(validResult(1));
                    });
            MinecraftProtocolProvidersV2.register(provider);
            try (ProviderExecutionEngine engine = engine()) {
                JsonObject result = first(engine,
                        request(provider.descriptor().providerId(), "server_authoritative", true),
                        context(Set.of("read"), fixture.audit));
                assertTrue(List.of("skipped", "permission_denied").contains(result.get("status").getAsString()));
                assertEquals(0, invocations.get());
            }
        }
    }

    @Test
    void realSchemaValidationAndQueryValidationAreEnforcedBeforeRevision() {
        Fixture fixture = fixture("schema");
        AtomicInteger invocations = new AtomicInteger();
        AgentDataProviderV2 invalid = provider(
                fixture, "invalid-nested", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> {
                    invocations.incrementAndGet();
                    JsonObject result = validResult(1);
                    result.getAsJsonObject("data").addProperty("nested", "wrong");
                    return CompletableFuture.completedFuture(result);
                });
        MinecraftProtocolProvidersV2.register(invalid);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject result = first(engine,
                    request(invalid.descriptor().providerId(), "server_authoritative", false),
                    context(Set.of("read"), fixture.audit));
            assertEquals("schema_violation", result.get("reason").getAsString());

            JsonObject request = request(invalid.descriptor().providerId(), "server_authoritative", false);
            request.add("providerQuery", object("invalid", 1));
            JsonObject queryRejected = first(engine, request, context(Set.of("read"), fixture.audit));
            assertEquals("query_schema_violation", queryRejected.get("reason").getAsString());
            assertEquals(1, invocations.get());
        }
    }

    @Test
    void timeoutRetiresUnderlyingAndLateCompletionCannotChangeRevision() throws Exception {
        Fixture fixture = fixture("late");
        AtomicReference<CompletableFuture<JsonObject>> underlying = new AtomicReference<>();
        AgentDataProviderV2 late = provider(
                fixture, "late", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> {
                    CompletableFuture<JsonObject> future = new CompletableFuture<>() {
                        @Override
                        public boolean cancel(boolean mayInterruptIfRunning) {
                            super.cancel(mayInterruptIfRunning);
                            return false;
                        }
                    };
                    underlying.set(future);
                    return future;
                });
        MinecraftProtocolProvidersV2.register(late);
        try (ProviderExecutionEngine engine = engine()) {
            JsonArray revisions = new JsonArray();
            JsonObject request = request(late.descriptor().providerId(), "server_authoritative", false);
            request.getAsJsonObject("budgets").addProperty("providerTimeoutMs", 25);
            JsonObject result = engine.execute(
                    request, revisions, context(Set.of("read"), fixture.audit)).get(2, TimeUnit.SECONDS).get(0).getAsJsonObject();
            assertEquals("timeout", result.get("reason").getAsString());
            assertEquals(0, revisions.size());
            underlying.get().complete(validResult(7));
            Thread.sleep(75L);
            assertEquals(0, revisions.size());
            assertEquals(0, engine.diagnostics().get("pendingInvocations").getAsInt());
        }
    }

    @Test
    void synchronousBlockingEntryIsDetectedAndQuarantined() {
        Fixture fixture = fixture("blocking");
        AtomicInteger invocations = new AtomicInteger();
        AgentDataProviderV2 blocking = provider(
                fixture, "blocking", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> {
                    invocations.incrementAndGet();
                    try {
                        Thread.sleep(30L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return CompletableFuture.completedFuture(validResult(1));
                });
        MinecraftProtocolProvidersV2.register(blocking);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject request = request(blocking.descriptor().providerId(), "server_authoritative", false);
            JsonObject first = first(engine, request, context(Set.of("read"), fixture.audit));
            JsonObject second = first(engine, request, context(Set.of("read"), fixture.audit));
            assertEquals("synchronous_entry_budget_exceeded", first.get("reason").getAsString());
            assertEquals("provider_quarantined", second.get("reason").getAsString());
            assertEquals(1, invocations.get());
        }
    }

    @Test
    void cancellationAndRuntimeCloseRetirePendingProviderWork() throws Exception {
        Fixture fixture = fixture("cancel");
        AtomicReference<CompletableFuture<JsonObject>> underlying = new AtomicReference<>();
        AgentDataProviderV2 pending = provider(
                fixture, "pending", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> {
                    CompletableFuture<JsonObject> future = new CompletableFuture<>();
                    underlying.set(future);
                    return future;
                });
        MinecraftProtocolProvidersV2.register(pending);
        ProviderExecutionEngine engine = engine();
        DeepObservationRequestContext context = context(Set.of("read"), fixture.audit);
        CompletableFuture<JsonArray> result = engine.execute(
                request(pending.descriptor().providerId(), "server_authoritative", false),
                new JsonArray(), context);
        for (int i = 0; i < 50 && underlying.get() == null; i++) Thread.sleep(5L);
        context.cancel("test_cancel");
        assertThrows(Exception.class, () -> result.get(1, TimeUnit.SECONDS));
        assertTrue(underlying.get().isCancelled());
        engine.close();
        assertEquals(0, engine.diagnostics().get("pendingInvocations").getAsInt());
    }

    @Test
    void registrationRejectsUnknownSchemasAndDuplicateIds() {
        Fixture fixture = fixture("registration");
        AgentDataProviderV2 provider = provider(
                fixture, "duplicate", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> CompletableFuture.completedFuture(validResult(1)));
        MinecraftProtocolProvidersV2.register(provider);
        assertThrows(IllegalStateException.class, () -> MinecraftProtocolProvidersV2.register(provider));

        AgentDataProviderV2.Descriptor bad = new AgentDataProviderV2.Descriptor(
                fixture.prefix + ":unknown-schema", "1", List.of("snapshot"),
                fixture.prefix + "://missing", fixture.querySchema,
                "detached_provider_worker", List.of("server_authoritative"),
                "none", "complete", true, false, false, false, false,
                "provider_revision", "resource", fixture.snapshotSchema, true, "none",
                new AgentDataProviderV2.DebugDeclaration(false, "", "debug", true), List.of("read"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftProtocolProvidersV2.register(
                new SimpleProvider(bad, ignored -> CompletableFuture.completedFuture(validResult(1)))));
    }

    @Test
    void providerFallbackRevisionIsQueryShapeIndependentButStateSensitive() throws Exception {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject state = validResult(1).getAsJsonObject("data");
        long one = tracker.revision("provider", "provider", state).get("revision").getAsLong();
        JsonObject reordered = new JsonObject();
        reordered.add("nested", state.get("nested").deepCopy());
        reordered.addProperty("temperature", 20.5D);
        long two = tracker.revision("provider", "provider", reordered).get("revision").getAsLong();
        JsonObject changed = reordered.deepCopy();
        changed.addProperty("temperature", 21.0D);
        long three = tracker.revision("provider", "provider", changed).get("revision").getAsLong();
        assertEquals(one, two);
        assertNotEquals(two, three);
    }

    @Test
    void providerNativeRevisionIsQueryIndependentAndChangesWithProviderState() {
        Fixture fixture = fixture("native_revision");
        AtomicInteger revision = new AtomicInteger(1);
        AgentDataProviderV2 provider = provider(
                fixture, "native", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> CompletableFuture.completedFuture(validResult(revision.get())));
        MinecraftProtocolProvidersV2.register(provider);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject firstRequest = request(provider.descriptor().providerId(), "server_authoritative", false);
            JsonArray firstRefs = new JsonArray();
            engine.execute(firstRequest, firstRefs, context(Set.of("read"), fixture.audit)).join();

            JsonObject secondRequest = request(provider.descriptor().providerId(), "server_authoritative", false);
            secondRequest.add("providerQuery", object("probe", "different-query"));
            JsonArray secondRefs = new JsonArray();
            engine.execute(secondRequest, secondRefs, context(Set.of("read"), fixture.audit)).join();
            assertEquals(
                    firstRefs.get(0).getAsJsonObject().get("revision").getAsLong(),
                    secondRefs.get(0).getAsJsonObject().get("revision").getAsLong());

            revision.incrementAndGet();
            JsonArray changedRefs = new JsonArray();
            engine.execute(secondRequest, changedRefs, context(Set.of("read"), fixture.audit)).join();
            assertNotEquals(
                    secondRefs.get(0).getAsJsonObject().get("revision").getAsLong(),
                    changedRefs.get(0).getAsJsonObject().get("revision").getAsLong());
        }
    }

    @Test
    void fallbackResourceRevisionUsesQueryIndependentRevisionState() {
        Fixture fixture = fixture("fallback_resource");
        AtomicInteger health = new AtomicInteger(10);
        AgentDataProviderV2 provider = providerWithRevision(
                fixture, "fallback", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", "snapshot_change_sequence", "resource", true,
                context -> {
                    JsonObject result = validResult(0);
                    result.getAsJsonObject("data").addProperty(
                            "view", context.query().get("probe").getAsString());
                    result.getAsJsonObject("data").addProperty("temperature", health.get());
                    result.getAsJsonObject("revisionState").addProperty("temperature", health.get());
                    return CompletableFuture.completedFuture(result);
                });
        MinecraftProtocolProvidersV2.register(provider);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject queryA = request(provider.descriptor().providerId(), "server_authoritative", false);
            queryA.add("providerQuery", object("probe", "health-only"));
            JsonArray refsA = new JsonArray();
            JsonObject resultA = engine.execute(
                    queryA, refsA, context(Set.of("read"), fixture.audit)).join().get(0).getAsJsonObject();

            JsonObject queryB = request(provider.descriptor().providerId(), "server_authoritative", false);
            queryB.add("providerQuery", object("probe", "inventory-only"));
            JsonArray refsB = new JsonArray();
            JsonObject resultB = engine.execute(
                    queryB, refsB, context(Set.of("read"), fixture.audit)).join().get(0).getAsJsonObject();
            assertNotEquals(resultA.getAsJsonObject("data"), resultB.getAsJsonObject("data"));
            assertEquals(
                    refsA.get(0).getAsJsonObject().get("revision").getAsLong(),
                    refsB.get(0).getAsJsonObject().get("revision").getAsLong());

            health.incrementAndGet();
            JsonArray changed = new JsonArray();
            engine.execute(queryA, changed, context(Set.of("read"), fixture.audit)).join();
            assertNotEquals(
                    refsB.get(0).getAsJsonObject().get("revision").getAsLong(),
                    changed.get(0).getAsJsonObject().get("revision").getAsLong());
        }
    }

    @Test
    void providerOrderedRevisionArraysRemainOrdered() {
        Fixture fixture = fixture("ordered_array");
        AtomicReference<JsonArray> sequence = new AtomicReference<>(array("a", "b"));
        AgentDataProviderV2 provider = providerWithRevision(
                fixture, "ordered", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", "snapshot_change_sequence", "resource", true,
                ignored -> {
                    JsonObject result = validResult(0);
                    result.getAsJsonObject("revisionState").add("sequence", sequence.get().deepCopy());
                    return CompletableFuture.completedFuture(result);
                });
        MinecraftProtocolProvidersV2.register(provider);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject request = request(provider.descriptor().providerId(), "server_authoritative", false);
            JsonArray first = new JsonArray();
            engine.execute(request, first, context(Set.of("read"), fixture.audit)).join();
            sequence.set(array("b", "a"));
            JsonArray second = new JsonArray();
            engine.execute(request, second, context(Set.of("read"), fixture.audit)).join();
            assertNotEquals(
                    first.get(0).getAsJsonObject().get("revision").getAsLong(),
                    second.get(0).getAsJsonObject().get("revision").getAsLong());
        }
    }

    @Test
    void nativeRevisionRegressionAndInconsistencyQuarantineProvider() {
        assertNativeViolation("provider_revision_regressed", 10, 9, 20.5D, 20.5D);
        assertNativeViolation("provider_revision_inconsistent", 10, 10, 20.5D, 21.0D);
    }

    @Test
    void queryViewRevisionIsExplicitlyScopedAndNotMutationEligible() {
        Fixture fixture = fixture("query_view");
        AgentDataProviderV2 provider = providerWithRevision(
                fixture, "view", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", "snapshot_change_sequence", "query_view", false,
                context -> {
                    JsonObject result = validResult(0);
                    result.getAsJsonObject("data").addProperty(
                            "view", context.query().get("probe").getAsString());
                    return CompletableFuture.completedFuture(result);
                });
        MinecraftProtocolProvidersV2.register(provider);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject a = request(provider.descriptor().providerId(), "server_authoritative", false);
            a.add("providerQuery", object("probe", "a"));
            JsonObject b = request(provider.descriptor().providerId(), "server_authoritative", false);
            b.add("providerQuery", object("probe", "b"));
            JsonArray refsA = new JsonArray();
            JsonArray refsB = new JsonArray();
            engine.execute(a, refsA, context(Set.of("read"), fixture.audit)).join();
            engine.execute(b, refsB, context(Set.of("read"), fixture.audit)).join();
            JsonObject refA = refsA.get(0).getAsJsonObject();
            JsonObject refB = refsB.get(0).getAsJsonObject();
            assertEquals("query_view", refA.get("revisionScope").getAsString());
            assertTrue(!refA.get("mutationPreconditionEligible").getAsBoolean());
            assertNotEquals(refA.get("resourceKey").getAsString(), refB.get("resourceKey").getAsString());
        }
    }

    @Test
    void providerWorkerBackpressureIsControlledAndBounded() throws Exception {
        Fixture fixture = fixture("worker_backpressure");
        CountDownLatch release = new CountDownLatch(1);
        AgentDataProviderV2 provider = provider(
                fixture, "blocking-worker", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> {
                    try {
                        release.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return CompletableFuture.completedFuture(validResult(1));
                });
        MinecraftProtocolProvidersV2.register(provider);
        try (ProviderExecutionEngine engine = engine()) {
            List<CompletableFuture<JsonArray>> requests = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                requests.add(engine.execute(
                        request(provider.descriptor().providerId(), "server_authoritative", false),
                        new JsonArray(),
                        context(Set.of("read"), fixture.audit)));
            }
            Thread.sleep(50L);
            assertEquals(16, engine.diagnostics().getAsJsonObject("worker")
                    .get("queueCapacity").getAsInt());
            assertTrue(engine.diagnostics().getAsJsonObject("worker")
                    .get("rejected").getAsInt() > 0);
            release.countDown();
            int controlledRejections = 0;
            for (CompletableFuture<JsonArray> request : requests) {
                JsonObject result = request.get(3, TimeUnit.SECONDS).get(0).getAsJsonObject();
                if (result.has("reason")
                        && result.get("reason").getAsString().equals("provider_worker_backpressure")) {
                    controlledRejections++;
                }
            }
            assertTrue(controlledRejections > 0);
            assertEquals(0, engine.diagnostics().getAsJsonObject("worker")
                    .get("queueDepth").getAsInt());
        } finally {
            release.countDown();
        }
    }

    private static Fixture fixture(String label) {
        String prefix = "test_" + label + "_" + UUID.randomUUID().toString().replace("-", "");
        String snapshot = prefix + "://snapshot";
        String query = prefix + "://query";
        String debug = prefix + "://debug";
        ProviderSchemaRegistry.register(snapshot, value ->
                value.has("temperature") && value.get("temperature").isJsonPrimitive()
                        && value.getAsJsonPrimitive("temperature").isNumber()
                        && value.has("nested") && value.get("nested").isJsonObject()
                        && value.getAsJsonObject("nested").has("enabled")
                        && value.getAsJsonObject("nested").get("enabled").isJsonPrimitive()
                        && value.getAsJsonObject("nested").getAsJsonPrimitive("enabled").isBoolean()
                        ? ProviderSchemaRegistry.ValidationResult.pass()
                        : ProviderSchemaRegistry.ValidationResult.fail("invalid_nested_payload"));
        ProviderSchemaRegistry.register(query, value ->
                value.keySet().stream().allMatch("probe"::equals)
                        && (!value.has("probe") || value.get("probe").isJsonPrimitive()
                        && value.getAsJsonPrimitive("probe").isString())
                        ? ProviderSchemaRegistry.ValidationResult.pass()
                        : ProviderSchemaRegistry.ValidationResult.fail("invalid_query"));
        ProviderSchemaRegistry.register(debug, value -> ProviderSchemaRegistry.ValidationResult.pass());
        return new Fixture(prefix, snapshot, query, debug, new ArrayList<>());
    }

    private static AgentDataProviderV2 provider(
            Fixture fixture,
            String name,
            List<String> scopes,
            List<String> perspectives,
            String affinity,
            boolean load,
            boolean storage,
            boolean mutate,
            boolean initialize,
            boolean safe,
            Capture capture) {
        return providerWithRevision(
                fixture, name, scopes, perspectives, affinity,
                "provider_revision", "resource", true, capture,
                load, storage, mutate, initialize, safe);
    }

    private static AgentDataProviderV2 providerWithRevision(
            Fixture fixture,
            String name,
            List<String> scopes,
            List<String> perspectives,
            String affinity,
            String revisionSource,
            String revisionScope,
            boolean revisionQueryInvariant,
            Capture capture) {
        return providerWithRevision(
                fixture, name, scopes, perspectives, affinity,
                revisionSource, revisionScope, revisionQueryInvariant, capture,
                false, false, false, false, true);
    }

    private static AgentDataProviderV2 providerWithRevision(
            Fixture fixture,
            String name,
            List<String> scopes,
            List<String> perspectives,
            String affinity,
            String revisionSource,
            String revisionScope,
            boolean revisionQueryInvariant,
            Capture capture,
            boolean load,
            boolean storage,
            boolean mutate,
            boolean initialize,
            boolean safe) {
        AgentDataProviderV2.Descriptor descriptor = new AgentDataProviderV2.Descriptor(
                fixture.prefix + ":" + name, "1", List.of("snapshot"),
                fixture.snapshotSchema, fixture.querySchema, affinity, perspectives,
                initialize ? "lazy_initialization" : "none", "complete", safe,
                initialize, load, storage, mutate,
                revisionSource, revisionScope,
                "resource".equals(revisionScope) ? fixture.snapshotSchema : "",
                revisionQueryInvariant, "none",
                new AgentDataProviderV2.DebugDeclaration(
                        mutate, mutate ? fixture.debugSchema : "", "debug", true), scopes);
        return new SimpleProvider(descriptor, capture);
    }

    private static void assertNativeViolation(
            String expectedReason,
            long firstRevision,
            long secondRevision,
            double firstTemperature,
            double secondTemperature) {
        Fixture fixture = fixture(expectedReason);
        AtomicInteger invocation = new AtomicInteger();
        AgentDataProviderV2 provider = provider(
                fixture, "native", List.of("read"), List.of("server_authoritative"),
                "detached_provider_worker", false, false, false, false, true,
                ignored -> {
                    boolean first = invocation.getAndIncrement() == 0;
                    JsonObject result = validResult(first ? firstRevision : secondRevision);
                    double temperature = first ? firstTemperature : secondTemperature;
                    result.getAsJsonObject("data").addProperty("temperature", temperature);
                    result.getAsJsonObject("revisionState").addProperty("temperature", temperature);
                    return CompletableFuture.completedFuture(result);
                });
        MinecraftProtocolProvidersV2.register(provider);
        try (ProviderExecutionEngine engine = engine()) {
            JsonObject request = request(provider.descriptor().providerId(), "server_authoritative", false);
            assertEquals("completed", first(engine, request, context(Set.of("read"), fixture.audit))
                    .get("status").getAsString());
            JsonObject violation = first(engine, request, context(Set.of("read"), fixture.audit));
            assertEquals(expectedReason, violation.get("reason").getAsString());
            JsonObject quarantined = first(engine, request, context(Set.of("read"), fixture.audit));
            assertEquals("provider_quarantined", quarantined.get("reason").getAsString());
        }
    }

    private static JsonObject first(
            ProviderExecutionEngine engine, JsonObject request, DeepObservationRequestContext context) {
        return engine.execute(request, new JsonArray(), context).join().get(0).getAsJsonObject();
    }

    private static ProviderExecutionEngine engine() {
        return new ProviderExecutionEngine(new ObservationRevisionTracker());
    }

    private static JsonObject request(String providerId, String perspective, boolean allowEffects) {
        JsonObject request = new JsonObject();
        request.addProperty("perspective", perspective);
        request.addProperty("allowReadEffects", allowEffects);
        JsonArray providers = new JsonArray();
        providers.add(providerId);
        request.add("providerIds", providers);
        request.add("providerQuery", object("probe", "contract"));
        JsonObject budgets = new JsonObject();
        budgets.addProperty("maxProviders", 1);
        budgets.addProperty("maxProviderBytes", 16_384);
        budgets.addProperty("maxTotalProviderBytes", 65_536);
        budgets.addProperty("providerTimeoutMs", 100);
        request.add("budgets", budgets);
        return request;
    }

    private static DeepObservationRequestContext context(
            Set<String> scopes, List<DeepObservationRequestContext.ProviderAuditEvent> audit) {
        return new DeepObservationRequestContext(
                scopes, "principal", "request", "connection",
                System.currentTimeMillis() + 5_000L, audit::add);
    }

    private static JsonObject validResult(long revision) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", "1");
        JsonObject data = new JsonObject();
        data.addProperty("temperature", 20.5D);
        JsonObject nested = new JsonObject();
        nested.addProperty("enabled", true);
        data.add("nested", nested);
        result.add("data", data);
        result.add("revisionState", data.deepCopy());
        result.addProperty("providerRevision", revision);
        return result;
    }

    private static JsonObject object(String name, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(name, value);
        return json;
    }

    private static JsonObject object(String name, Number value) {
        JsonObject json = new JsonObject();
        json.addProperty(name, value);
        return json;
    }

    private static JsonArray array(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    @FunctionalInterface
    private interface Capture {
        CompletableFuture<JsonObject> capture(AgentDataProviderV2.ReadContext context);
    }

    private record SimpleProvider(
            AgentDataProviderV2.Descriptor descriptor,
            Capture capture) implements AgentDataProviderV2 {
        @Override
        public CompletableFuture<JsonObject> capture(ReadContext context) {
            return this.capture.capture(context);
        }
    }

    private record Fixture(
            String prefix,
            String snapshotSchema,
            String querySchema,
            String debugSchema,
            List<DeepObservationRequestContext.ProviderAuditEvent> audit) {
    }
}
