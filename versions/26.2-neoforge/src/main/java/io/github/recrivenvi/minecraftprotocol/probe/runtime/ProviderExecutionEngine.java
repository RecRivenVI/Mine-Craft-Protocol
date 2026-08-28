package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Minecraft-independent Provider V2 policy, schema, revision and cancellation executor. */
final class ProviderExecutionEngine implements AutoCloseable {
    private static final Gson GSON = new Gson();
    static final long ENTRY_BUDGET_MICROS = 10_000L;

    private final ObservationRevisionTracker revisions;
    private final ExecutorService worker;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentHashMap<Long, Invocation> pending = new ConcurrentHashMap<>();
    private final Set<String> quarantined = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Dispatcher dispatcher = (provider, context, affinity) ->
            CompletableFuture.completedFuture(Entry.unsupported(affinity));

    ProviderExecutionEngine(ObservationRevisionTracker revisions) {
        this.revisions = revisions;
        this.worker = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-provider-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-provider-timeouts");
            thread.setDaemon(true);
            return thread;
        });
    }

    void installDispatcher(Dispatcher dispatcher) {
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher);
    }

    CompletableFuture<JsonArray> execute(
            JsonObject request,
            JsonArray revisionRefs,
            DeepObservationRequestContext requestContext) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new CancellationException("provider_runtime_closed"));
        }
        JsonObject budgets = object(request, "budgets");
        int providerLimit = bounded(integer(budgets, "maxProviders", 4), 1, 8);
        int perProviderBytes = bounded(integer(budgets, "maxProviderBytes", 16_384), 256, 16_384);
        int totalProviderBytes = bounded(integer(budgets, "maxTotalProviderBytes", 65_536), 1_024, 65_536);
        int timeoutMillis = bounded(integer(budgets, "providerTimeoutMs", 250), 25, 1_000);
        boolean allowReadEffects = request.has("allowReadEffects")
                && request.get("allowReadEffects").getAsBoolean();
        String requestedPerspective = request.has("perspective")
                ? request.get("perspective").getAsString() : "server_authoritative";
        Set<String> selected = new HashSet<>();
        if (request.has("providerIds") && request.get("providerIds").isJsonArray()) {
            request.getAsJsonArray("providerIds").forEach(element -> selected.add(element.getAsString()));
        }
        JsonObject query = object(request, "providerQuery");
        List<CompletableFuture<JsonObject>> captures = new ArrayList<>();
        int considered = 0;
        for (AgentDataProviderV2 provider : MinecraftProtocolProvidersV2.snapshot()) {
            AgentDataProviderV2.Descriptor descriptor = provider.descriptor();
            if (!selected.isEmpty() && !selected.contains(descriptor.providerId())) continue;
            if (considered++ >= providerLimit) break;
            Decision decision = policy(descriptor, requestedPerspective, allowReadEffects, requestContext);
            if (!decision.allowed()) {
                JsonObject result = result(descriptor, decision.status(), decision.reason());
                result.addProperty("resolvedPerspective", decision.perspective());
                audit(requestContext, descriptor, decision.reason(), decision.perspective(), 0L, decision.status());
                captures.add(CompletableFuture.completedFuture(result));
                continue;
            }
            ProviderSchemaRegistry.ValidationResult queryValidation =
                    ProviderSchemaRegistry.validate(descriptor.querySchema(), query);
            if (!queryValidation.valid()) {
                JsonObject result = result(descriptor, "failed", "query_schema_violation");
                result.addProperty("schemaDetail", queryValidation.reason());
                result.addProperty("resolvedPerspective", decision.perspective());
                audit(requestContext, descriptor, "query_schema_violation", decision.perspective(), 0L, "failed");
                captures.add(CompletableFuture.completedFuture(result));
                continue;
            }
            long deadline = Math.min(
                    System.currentTimeMillis() + timeoutMillis,
                    requestContext.deadlineAtMillis() <= 0L
                            ? Long.MAX_VALUE : requestContext.deadlineAtMillis());
            AgentDataProviderV2.ReadContext context = new AgentDataProviderV2.ReadContext(
                    query, decision.perspective(), allowReadEffects, deadline, perProviderBytes);
            captures.add(capture(
                    provider, descriptor, context, requestContext, timeoutMillis, perProviderBytes));
        }
        CompletableFuture<?>[] array = captures.toArray(CompletableFuture[]::new);
        return requestContext.track(CompletableFuture.allOf(array).thenApply(ignored -> {
            JsonArray results = new JsonArray();
            int total = 0;
            for (CompletableFuture<JsonObject> capture : captures) {
                JsonObject value = capture.join();
                int bytes = value.has("bytes") ? value.get("bytes").getAsInt() : 0;
                if (total + bytes > totalProviderBytes) {
                    JsonObject limited = value.deepCopy();
                    limited.addProperty("status", "failed");
                    limited.addProperty("reason", "total_provider_byte_budget_exceeded");
                    limited.remove("data");
                    limited.remove("_revisionRef");
                    results.add(limited);
                    continue;
                }
                total += bytes;
                if (value.has("_revisionRef")) revisionRefs.add(value.remove("_revisionRef"));
                results.add(value);
            }
            return results;
        }));
    }

    JsonObject diagnostics() {
        JsonObject json = new JsonObject();
        json.addProperty("pendingInvocations", this.pending.size());
        json.addProperty("quarantinedProviders", this.quarantined.size());
        json.addProperty("invocationGeneration", this.generation.get());
        return json;
    }

    private CompletableFuture<JsonObject> capture(
            AgentDataProviderV2 provider,
            AgentDataProviderV2.Descriptor descriptor,
            AgentDataProviderV2.ReadContext context,
            DeepObservationRequestContext requestContext,
            int timeoutMillis,
            int byteBudget) {
        long started = System.nanoTime();
        CompletableFuture<Entry> entryFuture = "detached_provider_worker".equals(descriptor.threadAffinity())
                ? CompletableFuture.supplyAsync(() -> enter(provider, context, true), this.worker)
                : this.dispatcher.dispatch(provider, context, descriptor.threadAffinity());
        requestContext.track(entryFuture);
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        requestContext.track(result);
        entryFuture.whenComplete((entry, entryError) -> {
            if (requestContext.isCancelled()) {
                result.completeExceptionally(new CancellationException(requestContext.cancellationReason()));
                return;
            }
            if (entryError != null) {
                fail(result, descriptor, requestContext, context.perspective(), started, "provider_entry_exception", 0L);
                return;
            }
            if (!entry.supported() || !entry.ownerThreadObserved()) {
                fail(result, descriptor, requestContext, context.perspective(), started, "thread_affinity_unavailable", entry.entryDurationMicros());
                return;
            }
            if (entry.entryDurationMicros() > ENTRY_BUDGET_MICROS) {
                this.quarantined.add(descriptor.providerId());
                if (entry.capture() != null) entry.capture().cancel(true);
                fail(result, descriptor, requestContext, context.perspective(), started, "synchronous_entry_budget_exceeded", entry.entryDurationMicros());
                return;
            }
            CompletableFuture<JsonObject> underlying = entry.capture();
            if (underlying == null) {
                fail(result, descriptor, requestContext, context.perspective(), started, "provider_returned_null_future", entry.entryDurationMicros());
                return;
            }
            long invocationGeneration = this.generation.incrementAndGet();
            Invocation invocation = new Invocation(invocationGeneration, underlying, result);
            this.pending.put(invocationGeneration, invocation);
            requestContext.track(underlying);
            long now = System.currentTimeMillis();
            boolean requestDeadlineWins = requestContext.deadlineAtMillis() > 0L
                    && requestContext.deadlineAtMillis() <= now + timeoutMillis;
            long remaining = Math.max(1L, Math.min(
                    timeoutMillis, context.deadlineMillis() - now));
            var timeout = this.scheduler.schedule(
                    () -> retire(
                            invocation,
                            descriptor,
                            requestContext,
                            context.perspective(),
                            started,
                            requestDeadlineWins ? "request_deadline" : "timeout"),
                    remaining, TimeUnit.MILLISECONDS);
            underlying.whenComplete((data, error) -> {
                timeout.cancel(false);
                if (!this.pending.remove(invocationGeneration, invocation)
                        || invocation.retired().get() || requestContext.isCancelled()) return;
                if (error != null) {
                    fail(result, descriptor, requestContext, context.perspective(), started,
                            error instanceof CancellationException ? "cancelled" : "provider_exception",
                            entry.entryDurationMicros());
                    return;
                }
                result.complete(validate(
                        descriptor, data, context.perspective(), started,
                        entry.entryDurationMicros(), byteBudget, requestContext));
            });
        });
        result.whenComplete((value, error) -> {
            if (!result.isCancelled()) return;
            for (Invocation invocation : this.pending.values()) {
                if (invocation.result() == result && this.pending.remove(invocation.generation(), invocation)) {
                    invocation.retired().set(true);
                    invocation.underlying().cancel(true);
                }
            }
        });
        return result;
    }

    private JsonObject validate(
            AgentDataProviderV2.Descriptor descriptor,
            JsonObject payload,
            String perspective,
            long started,
            long entryDurationMicros,
            int byteBudget,
            DeepObservationRequestContext requestContext) {
        long validationStarted = System.nanoTime();
        if (payload == null || !payload.has("schemaVersion")
                || !payload.get("schemaVersion").isJsonPrimitive()
                || !descriptor.schemaVersion().equals(payload.get("schemaVersion").getAsString())) {
            return failure(descriptor, requestContext, perspective, started, "schema_version_mismatch", entryDurationMicros);
        }
        if (!payload.has("data") || !payload.get("data").isJsonObject()) {
            return failure(descriptor, requestContext, perspective, started, "schema_violation", entryDurationMicros);
        }
        ProviderSchemaRegistry.ValidationResult schema =
                ProviderSchemaRegistry.validate(descriptor.snapshotSchema(), payload.getAsJsonObject("data"));
        if (!schema.valid()) {
            JsonObject failed = failure(descriptor, requestContext, perspective, started, "schema_violation", entryDurationMicros);
            failed.addProperty("schemaDetail", schema.reason());
            return failed;
        }
        int bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > byteBudget) {
            return failure(descriptor, requestContext, perspective, started, "provider_byte_budget_exceeded", entryDurationMicros);
        }
        JsonObject result = result(descriptor, "completed", "");
        result.addProperty("resolvedPerspective", perspective);
        result.add("data", payload.getAsJsonObject("data").deepCopy());
        result.addProperty("revisionSource", descriptor.revisionSource());
        if ("provider_revision".equals(descriptor.revisionSource())) {
            if (!payload.has("providerRevision") || !payload.get("providerRevision").isJsonPrimitive()
                    || !payload.getAsJsonPrimitive("providerRevision").isNumber()
                    || payload.get("providerRevision").getAsLong() < 0L) {
                return failure(descriptor, requestContext, perspective, started, "provider_revision_missing_or_invalid", entryDurationMicros);
            }
            long providerRevision = payload.get("providerRevision").getAsLong();
            result.addProperty("providerRevision", providerRevision);
            result.add("_revisionRef", this.revisions.nativeRevision(
                    "provider", descriptor.providerId(), providerRevision, "provider_revision"));
        } else {
            result.add("_revisionRef", this.revisions.revision(
                    "provider", descriptor.providerId(), payload.getAsJsonObject("data")));
        }
        result.addProperty("bytes", bytes);
        result.addProperty("entryDurationMicros", entryDurationMicros);
        result.addProperty("validationDurationMicros", elapsedMicros(validationStarted));
        long duration = elapsedMicros(started);
        result.addProperty("durationMicros", duration);
        audit(requestContext, descriptor, "allowed", perspective, duration, "completed");
        return result;
    }

    private void retire(
            Invocation invocation,
            AgentDataProviderV2.Descriptor descriptor,
            DeepObservationRequestContext requestContext,
            String perspective,
            long started,
            String reason) {
        if (!this.pending.remove(invocation.generation(), invocation)) return;
        invocation.retired().set(true);
        invocation.underlying().cancel(true);
        if ("request_deadline".equals(reason)) {
            long duration = elapsedMicros(started);
            audit(requestContext, descriptor, reason, perspective, duration, "failed");
            invocation.result().completeExceptionally(
                    new TimeoutException("Deep Observation request deadline exceeded during Provider execution"));
            return;
        }
        fail(invocation.result(), descriptor, requestContext, perspective, started, reason, 0L);
    }

    private void fail(
            CompletableFuture<JsonObject> target,
            AgentDataProviderV2.Descriptor descriptor,
            DeepObservationRequestContext requestContext,
            String perspective,
            long started,
            String reason,
            long entryDurationMicros) {
        target.complete(failure(
                descriptor, requestContext, perspective, started, reason, entryDurationMicros));
    }

    private JsonObject failure(
            AgentDataProviderV2.Descriptor descriptor,
            DeepObservationRequestContext requestContext,
            String perspective,
            long started,
            String reason,
            long entryDurationMicros) {
        JsonObject failed = result(descriptor, "failed", reason);
        failed.addProperty("resolvedPerspective", perspective);
        failed.addProperty("entryDurationMicros", entryDurationMicros);
        long duration = elapsedMicros(started);
        failed.addProperty("durationMicros", duration);
        audit(requestContext, descriptor, reason, perspective, duration, "failed");
        return failed;
    }

    private Decision policy(
            AgentDataProviderV2.Descriptor descriptor,
            String requestedPerspective,
            boolean allowReadEffects,
            DeepObservationRequestContext requestContext) {
        Set<String> required = Set.copyOf(descriptor.requiredScopes());
        if (!requestContext.hasScopes(required)) {
            return new Decision(false, "permission_denied", "provider_scope_denied", requestedPerspective);
        }
        String perspective = "both".equals(requestedPerspective)
                ? descriptor.perspectives().contains("server_authoritative")
                        ? "server_authoritative"
                        : descriptor.perspectives().contains("client_known") ? "client_known" : ""
                : descriptor.perspectives().contains(requestedPerspective) ? requestedPerspective : "";
        if (perspective.isEmpty()) return new Decision(false, "skipped", "unsupported_perspective", requestedPerspective);
        if (descriptor.mayMutate()) return new Decision(false, "permission_denied", "mutation_not_allowed_in_observation", perspective);
        if (descriptor.mayAccessStorage()) return new Decision(false, "skipped", "storage_access_not_allowed_in_observation", perspective);
        if (descriptor.mayLoadData()) return new Decision(false, "skipped", "data_loading_not_allowed_in_observation", perspective);
        if (descriptor.mayInitialize() && !allowReadEffects) return new Decision(false, "skipped", "read_effects_not_allowed", perspective);
        if (!descriptor.snapshotSafe() && !descriptor.mayInitialize()) return new Decision(false, "skipped", "provider_not_snapshot_safe", perspective);
        if (this.quarantined.contains(descriptor.providerId())) return new Decision(false, "degraded", "provider_quarantined", perspective);
        return new Decision(true, "completed", "allowed", perspective);
    }

    static Entry enter(
            AgentDataProviderV2 provider,
            AgentDataProviderV2.ReadContext context,
            boolean ownerThreadObserved) {
        long started = System.nanoTime();
        try {
            CompletableFuture<JsonObject> capture = provider.capture(context);
            return new Entry(true, ownerThreadObserved, Thread.currentThread().getName(), elapsedMicros(started), capture);
        } catch (Throwable throwable) {
            return new Entry(true, ownerThreadObserved, Thread.currentThread().getName(), elapsedMicros(started),
                    CompletableFuture.failedFuture(throwable));
        }
    }

    private static JsonObject result(
            AgentDataProviderV2.Descriptor descriptor, String status, String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("providerId", descriptor.providerId());
        json.addProperty("schemaVersion", descriptor.schemaVersion());
        json.addProperty("snapshotSchema", descriptor.snapshotSchema());
        json.addProperty("querySchema", descriptor.querySchema());
        json.addProperty("threadAffinity", descriptor.threadAffinity());
        json.addProperty("readEffects", descriptor.readEffects());
        json.addProperty("snapshotSafe", descriptor.snapshotSafe());
        json.addProperty("mayInitialize", descriptor.mayInitialize());
        json.addProperty("mayLoadData", descriptor.mayLoadData());
        json.addProperty("mayAccessStorage", descriptor.mayAccessStorage());
        json.addProperty("mayMutate", descriptor.mayMutate());
        json.addProperty("revisionSource", descriptor.revisionSource());
        json.addProperty("deltaCapability", descriptor.deltaCapability());
        json.addProperty("debugSupported", descriptor.debugDeclaration().supported());
        JsonArray perspectives = new JsonArray();
        descriptor.perspectives().forEach(perspectives::add);
        json.add("perspectives", perspectives);
        JsonArray scopes = new JsonArray();
        descriptor.requiredScopes().forEach(scopes::add);
        json.add("requiredScopes", scopes);
        json.addProperty("status", status);
        if (!reason.isEmpty()) json.addProperty("reason", reason);
        return json;
    }

    private static void audit(
            DeepObservationRequestContext context,
            AgentDataProviderV2.Descriptor descriptor,
            String decision,
            String perspective,
            long durationMicros,
            String status) {
        context.audit(
                descriptor.providerId(), Set.copyOf(descriptor.requiredScopes()),
                decision, perspective, descriptor.readEffects(), durationMicros, status);
    }

    private static JsonObject object(JsonObject source, String name) {
        return source.has(name) && source.get(name).isJsonObject()
                ? source.getAsJsonObject(name).deepCopy() : new JsonObject();
    }

    private static int integer(JsonObject source, String name, int fallback) {
        return source.has(name) ? source.get(name).getAsInt() : fallback;
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long elapsedMicros(long started) {
        return (System.nanoTime() - started) / 1_000L;
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        for (Invocation invocation : this.pending.values()) {
            invocation.retired().set(true);
            invocation.underlying().cancel(true);
            invocation.result().completeExceptionally(new CancellationException("runtime_close"));
        }
        this.pending.clear();
        this.scheduler.shutdownNow();
        this.worker.shutdownNow();
    }

    @FunctionalInterface
    interface Dispatcher {
        CompletableFuture<Entry> dispatch(
                AgentDataProviderV2 provider,
                AgentDataProviderV2.ReadContext context,
                String affinity);
    }

    record Entry(
            boolean supported,
            boolean ownerThreadObserved,
            String threadName,
            long entryDurationMicros,
            CompletableFuture<JsonObject> capture) {
        static Entry unsupported(String affinity) {
            return new Entry(false, false, affinity, 0L, null);
        }
    }

    private record Decision(boolean allowed, String status, String reason, String perspective) {
    }

    private record Invocation(
            long generation,
            CompletableFuture<JsonObject> underlying,
            CompletableFuture<JsonObject> result,
            AtomicBoolean retired) {
        Invocation(long generation, CompletableFuture<JsonObject> underlying, CompletableFuture<JsonObject> result) {
            this(generation, underlying, result, new AtomicBoolean());
        }
    }
}
