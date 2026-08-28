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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Minecraft-independent Provider V2 policy, schema, revision and cancellation executor. */
final class ProviderExecutionEngine implements AutoCloseable {
    private static final Gson GSON = new Gson();
    static final long ENTRY_BUDGET_MICROS = 10_000L;
    static final int WORKER_THREADS = 2;
    static final int WORKER_QUEUE_CAPACITY = 16;
    private static final int MAX_NATIVE_REVISION_STATES = 256;

    private final ObservationRevisionTracker revisions;
    private final BoundedTaskExecutor worker;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentHashMap<Long, Invocation> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MutationInvocation> pendingMutations = new ConcurrentHashMap<>();
    private final Set<String> quarantined = ConcurrentHashMap.newKeySet();
    private final Map<String, NativeRevisionState> nativeRevisionStates =
            new LinkedHashMap<>(64, 0.75F, true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Dispatcher dispatcher = (provider, context, affinity) ->
            CompletableFuture.completedFuture(Entry.unsupported(affinity));
    private volatile MutationDispatcher mutationDispatcher = (provider, context, affinity, authorization) ->
            CompletableFuture.completedFuture(MutationEntry.unsupported(affinity));

    ProviderExecutionEngine(ObservationRevisionTracker revisions) {
        this.revisions = revisions;
        this.worker = new BoundedTaskExecutor(
                "minecraft-protocol-provider-worker", WORKER_THREADS, WORKER_QUEUE_CAPACITY);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-provider-timeouts");
            thread.setDaemon(true);
            return thread;
        });
    }

    void installDispatcher(Dispatcher dispatcher) {
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher);
    }

    void installMutationDispatcher(MutationDispatcher dispatcher) {
        this.mutationDispatcher = java.util.Objects.requireNonNull(dispatcher);
    }

    CompletableFuture<JsonObject> debugMutate(
            String providerId,
            JsonObject mutation,
            JsonObject expectedResourceVersion,
            String worldFingerprint,
            String requestId,
            DebugMutationAuthorization authorization,
            int timeoutMillis,
            int byteBudget) {
        AgentDataProviderV2 provider = MinecraftProtocolProvidersV2.snapshot().stream()
                .filter(candidate -> candidate.descriptor().providerId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new ProtocolState.ProtocolException(
                        "PROVIDER_NOT_FOUND", 404, "Unknown provider: " + providerId));
        AgentDataProviderV2.Descriptor descriptor = provider.descriptor();
        AgentDataProviderV2.DebugDeclaration debug = descriptor.debugDeclaration();
        if (!debug.supported()) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE", 409,
                    "Provider does not expose typed Debug mutation: " + providerId));
        }
        if (!authorization.hasScope(debug.requiredScope())) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "DEBUG_SCOPE_DENIED", 403,
                    "Provider Debug requires scope: " + debug.requiredScope()));
        }
        if (!"provider_revision".equals(descriptor.revisionSource())
                || !"resource".equals(descriptor.revisionScope())) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE", 409,
                    "Provider Debug currently requires a native resource revision"));
        }
        ProviderSchemaRegistry.ValidationResult mutationSchema =
                ProviderSchemaRegistry.validate(debug.mutationSchema(), mutation);
        if (!mutationSchema.valid()) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "PROVIDER_DEBUG_SCHEMA_VIOLATION", 400, mutationSchema.reason()));
        }
        if (this.quarantined.contains(providerId)) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE", 409, "Provider is quarantined: " + providerId));
        }
        String providerLifecycle = "provider:" + providerId + "@runtime_registration";
        synchronized (this.nativeRevisionStates) {
            NativeRevisionState current = this.nativeRevisionStates.get(providerId);
            if (current == null) {
                return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                        "PROVIDER_RESOURCE_VERSION_UNAVAILABLE", 409,
                        "Observe the Provider resource before submitting a Debug mutation"));
            }
            ResourceVersionVerifier.verify(expectedResourceVersion, this.revisions.nativeRevision(
                    "provider", providerId, providerLifecycle,
                    current.revision(), "provider_revision"));
        }
        int boundedTimeout = bounded(timeoutMillis, 25, 1_000);
        int boundedBytes = bounded(byteBudget, 256, 16_384);
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        AgentDataProviderV2.DebugContext context = new AgentDataProviderV2.DebugContext(
                mutation, expectedResourceVersion, worldFingerprint,
                System.currentTimeMillis() + boundedTimeout, boundedBytes,
                requestId, () -> result.isCancelled() || authorization.isCancelled());
        long started = System.nanoTime();
        CompletableFuture<MutationEntry> entryFuture = this.mutationDispatcher.dispatch(
                provider, context, descriptor.threadAffinity(), authorization);
        entryFuture.whenComplete((entry, entryError) -> {
            if (entryError != null) {
                result.completeExceptionally(entryError);
                return;
            }
            if (!entry.supported() || !entry.ownerThreadObserved()) {
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "CAPABILITY_UNAVAILABLE", 409,
                        "Provider Debug thread affinity is unavailable"));
                return;
            }
            if (entry.entryDurationMicros() > ENTRY_BUDGET_MICROS) {
                entry.permit().close();
                this.quarantined.add(providerId);
                if (entry.mutation() != null) entry.mutation().cancel(true);
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "PROVIDER_DEBUG_ENTRY_BUDGET_EXCEEDED", 409,
                        "Provider Debug entry exceeded its synchronous budget"));
                return;
            }
            CompletableFuture<JsonObject> underlying = entry.mutation();
            if (underlying == null) {
                entry.permit().close();
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "PROVIDER_DEBUG_CONTRACT_VIOLATION", 409,
                        "Provider returned a null Debug future"));
                return;
            }
            long invocationGeneration = this.generation.incrementAndGet();
            MutationInvocation invocation = new MutationInvocation(
                    invocationGeneration, underlying, result, entry.permit());
            this.pendingMutations.put(invocationGeneration, invocation);
            var timeout = this.scheduler.schedule(() -> {
                if (!this.pendingMutations.remove(invocationGeneration, invocation)) return;
                invocation.retired().set(true);
                invocation.underlying().cancel(true);
                invocation.permit().close();
                invocation.result().completeExceptionally(new ProtocolState.ProtocolException(
                        "PROVIDER_DEBUG_TIMEOUT", 408, "Provider Debug mutation timed out"));
            }, boundedTimeout, TimeUnit.MILLISECONDS);
            underlying.whenComplete((payload, error) -> {
                timeout.cancel(false);
                if (!this.pendingMutations.remove(invocationGeneration, invocation)
                        || invocation.retired().get()) return;
                invocation.permit().close();
                if (error != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if ("provider_stale_resource_revision".equals(cause.getMessage())) {
                        result.completeExceptionally(new ProtocolState.ProtocolException(
                                "STALE_RESOURCE_REVISION", 409,
                                "Provider resource changed before mutation"));
                    } else {
                        result.completeExceptionally(error);
                    }
                    return;
                }
                try {
                    result.complete(this.validateDebugResult(
                            descriptor, payload, expectedResourceVersion,
                            requestId, authorization, boundedBytes, started));
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        });
        result.whenComplete((value, error) -> {
            if (!result.isCancelled()) return;
            for (MutationInvocation invocation : this.pendingMutations.values()) {
                if (invocation.result() == result
                        && this.pendingMutations.remove(invocation.generation(), invocation)) {
                    invocation.retired().set(true);
                    invocation.underlying().cancel(true);
                    invocation.permit().close();
                }
            }
        });
        return result;
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
        json.addProperty("pendingDebugMutations", this.pendingMutations.size());
        json.addProperty("quarantinedProviders", this.quarantined.size());
        json.addProperty("invocationGeneration", this.generation.get());
        json.add("worker", this.worker.diagnostics());
        synchronized (this.nativeRevisionStates) {
            json.addProperty("nativeRevisionStateEntries", this.nativeRevisionStates.size());
            json.addProperty("nativeRevisionStateBound", MAX_NATIVE_REVISION_STATES);
        }
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
                ? this.worker.submit(() -> enter(provider, context, true))
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
                fail(result, descriptor, requestContext, context.perspective(), started,
                        entryError instanceof RejectedExecutionException
                                ? "provider_worker_backpressure" : "provider_entry_exception",
                        0L);
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
                        descriptor, data, context.query(), context.perspective(), started,
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
            JsonObject query,
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
        JsonObject revisionState = payload.has("revisionState") && payload.get("revisionState").isJsonObject()
                ? payload.getAsJsonObject("revisionState") : null;
        if ("resource".equals(descriptor.revisionScope())) {
            if (revisionState == null) {
                return quarantineFailure(
                        descriptor, requestContext, perspective, started,
                        "provider_revision_state_missing", entryDurationMicros);
            }
            ProviderSchemaRegistry.ValidationResult revisionSchema =
                    ProviderSchemaRegistry.validate(descriptor.revisionSchema(), revisionState);
            if (!revisionSchema.valid()) {
                JsonObject failed = quarantineFailure(
                        descriptor, requestContext, perspective, started,
                        "provider_revision_state_schema_violation", entryDurationMicros);
                failed.addProperty("schemaDetail", revisionSchema.reason());
                return failed;
            }
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
        result.addProperty("revisionScope", descriptor.revisionScope());
        result.addProperty("revisionQueryInvariant", descriptor.revisionQueryInvariant());
        String providerLifecycle = "provider:" + descriptor.providerId() + "@runtime_registration";
        if ("provider_revision".equals(descriptor.revisionSource())) {
            if (!payload.has("providerRevision") || !payload.get("providerRevision").isJsonPrimitive()
                    || !payload.getAsJsonPrimitive("providerRevision").isNumber()
                    || payload.get("providerRevision").getAsLong() < 0L) {
                return failure(descriptor, requestContext, perspective, started, "provider_revision_missing_or_invalid", entryDurationMicros);
            }
            long providerRevision = payload.get("providerRevision").getAsLong();
            String revisionFingerprint = ObservationRevisionTracker.fingerprint(revisionState);
            String integrityFailure = validateNativeRevision(
                    descriptor.providerId(), providerRevision, revisionFingerprint);
            if (integrityFailure != null) {
                return quarantineFailure(
                        descriptor, requestContext, perspective, started,
                        integrityFailure, entryDurationMicros);
            }
            result.addProperty("providerRevision", providerRevision);
            result.add("_revisionRef", this.revisions.nativeRevision(
                    "provider", descriptor.providerId(), providerLifecycle,
                    providerRevision, "provider_revision"));
        } else if ("resource".equals(descriptor.revisionScope())) {
            result.add("_revisionRef", this.revisions.revision(
                    "provider", descriptor.providerId(), providerLifecycle, revisionState));
        } else {
            String queryFingerprint = ObservationRevisionTracker.fingerprint(query);
            result.addProperty("queryFingerprint", queryFingerprint);
            result.add("_revisionRef", this.revisions.queryViewRevision(
                    "provider", descriptor.providerId(), providerLifecycle,
                    queryFingerprint, payload.getAsJsonObject("data")));
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

    private JsonObject quarantineFailure(
            AgentDataProviderV2.Descriptor descriptor,
            DeepObservationRequestContext requestContext,
            String perspective,
            long started,
            String reason,
            long entryDurationMicros) {
        this.quarantined.add(descriptor.providerId());
        return failure(
                descriptor, requestContext, perspective, started, reason, entryDurationMicros);
    }

    private String validateNativeRevision(
            String providerId, long revision, String revisionStateFingerprint) {
        synchronized (this.nativeRevisionStates) {
            NativeRevisionState previous = this.nativeRevisionStates.get(providerId);
            if (previous != null) {
                if (revision < previous.revision()) return "provider_revision_regressed";
                if (revision == previous.revision()
                        && !revisionStateFingerprint.equals(previous.fingerprint())) {
                    return "provider_revision_inconsistent";
                }
            }
            this.nativeRevisionStates.put(
                    providerId, new NativeRevisionState(revision, revisionStateFingerprint));
            return null;
        }
    }

    private JsonObject validateDebugResult(
            AgentDataProviderV2.Descriptor descriptor,
            JsonObject payload,
            JsonObject expectedResourceVersion,
            String requestId,
            DebugMutationAuthorization authorization,
            int byteBudget,
            long started) {
        if (payload == null) throw new ProtocolState.ProtocolException(
                "PROVIDER_DEBUG_CONTRACT_VIOLATION", 409, "Provider returned null Debug result");
        ProviderSchemaRegistry.ValidationResult resultSchema = ProviderSchemaRegistry.validate(
                descriptor.debugDeclaration().resultSchema(), payload);
        if (!resultSchema.valid()) {
            this.quarantined.add(descriptor.providerId());
            throw new ProtocolState.ProtocolException(
                    "PROVIDER_DEBUG_RESULT_SCHEMA_VIOLATION", 409, resultSchema.reason());
        }
        int bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > byteBudget) throw new ProtocolState.ProtocolException(
                "DEBUG_VALUE_BUDGET_EXCEEDED", 413, "Provider Debug result exceeds its byte budget");
        long beforeRevision = payload.get("providerRevisionBefore").getAsLong();
        long afterRevision = payload.get("providerRevisionAfter").getAsLong();
        if (afterRevision <= beforeRevision) {
            this.quarantined.add(descriptor.providerId());
            throw new ProtocolState.ProtocolException(
                    "PROVIDER_DEBUG_REVISION_DID_NOT_ADVANCE", 409,
                    "Provider Debug mutation must advance native revision");
        }
        JsonObject beforeState = payload.getAsJsonObject("revisionStateBefore");
        JsonObject afterState = payload.getAsJsonObject("revisionStateAfter");
        for (JsonObject state : List.of(beforeState, afterState)) {
            ProviderSchemaRegistry.ValidationResult revisionSchema =
                    ProviderSchemaRegistry.validate(descriptor.revisionSchema(), state);
            if (!revisionSchema.valid()) {
                this.quarantined.add(descriptor.providerId());
                throw new ProtocolState.ProtocolException(
                        "PROVIDER_DEBUG_REVISION_STATE_INVALID", 409, revisionSchema.reason());
            }
        }
        String lifecycle = "provider:" + descriptor.providerId() + "@runtime_registration";
        JsonObject beforeRef = this.revisions.nativeRevision(
                "provider", descriptor.providerId(), lifecycle,
                beforeRevision, "provider_revision");
        ResourceVersionVerifier.verify(expectedResourceVersion, beforeRef);
        String beforeIntegrity = validateNativeRevision(
                descriptor.providerId(), beforeRevision,
                ObservationRevisionTracker.fingerprint(beforeState));
        if (beforeIntegrity != null) {
            this.quarantined.add(descriptor.providerId());
            throw new ProtocolState.ProtocolException(
                    "PROVIDER_DEBUG_REVISION_INTEGRITY_FAILURE", 409, beforeIntegrity);
        }
        String afterIntegrity = validateNativeRevision(
                descriptor.providerId(), afterRevision,
                ObservationRevisionTracker.fingerprint(afterState));
        if (afterIntegrity != null) {
            this.quarantined.add(descriptor.providerId());
            throw new ProtocolState.ProtocolException(
                    "PROVIDER_DEBUG_REVISION_INTEGRITY_FAILURE", 409, afterIntegrity);
        }
        JsonObject afterRef = this.revisions.nativeRevision(
                "provider", descriptor.providerId(), lifecycle,
                afterRevision, "provider_revision");
        JsonObject result = new JsonObject();
        result.addProperty("type", "debug.mutation.result");
        result.addProperty("schemaVersion", "phase9c-debug-v0");
        result.addProperty("debugOperationId", requestId);
        result.addProperty("auditReference", requestId);
        result.addProperty("operation", "provider.mutate");
        result.addProperty("namespace", "provider");
        result.addProperty("providerId", descriptor.providerId());
        result.addProperty("authority", "runtime_internal");
        result.addProperty("mechanism", "registered_provider_typed_mutation");
        result.addProperty("invariants", "provider_schema_validated");
        result.addProperty("synchronization", descriptor.debugDeclaration().synchronizationBehavior());
        result.addProperty("evidence", "diagnostic");
        result.addProperty("gameplayEvidence", false);
        result.addProperty("evidenceContaminated", true);
        result.addProperty("storageAccessed", false);
        result.addProperty("principalId", authorization.principalId());
        result.addProperty("debugArmId", authorization.debugArmId());
        result.add("before", payload.get("before").deepCopy());
        result.add("after", payload.get("after").deepCopy());
        result.add("beforeResourceVersion", ResourceVersionVerifier.token(beforeRef));
        result.add("afterResourceVersion", ResourceVersionVerifier.token(afterRef));
        result.addProperty("durationMicros", elapsedMicros(started));
        return result;
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
        json.addProperty("revisionScope", descriptor.revisionScope());
        json.addProperty("revisionSchema", descriptor.revisionSchema());
        json.addProperty("revisionQueryInvariant", descriptor.revisionQueryInvariant());
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
        for (MutationInvocation invocation : this.pendingMutations.values()) {
            invocation.retired().set(true);
            invocation.underlying().cancel(true);
            invocation.permit().close();
            invocation.result().completeExceptionally(new CancellationException("runtime_close"));
        }
        this.pendingMutations.clear();
        this.scheduler.shutdownNow();
        this.worker.close();
    }

    @FunctionalInterface
    interface Dispatcher {
        CompletableFuture<Entry> dispatch(
                AgentDataProviderV2 provider,
                AgentDataProviderV2.ReadContext context,
                String affinity);
    }

    @FunctionalInterface
    interface MutationDispatcher {
        CompletableFuture<MutationEntry> dispatch(
                AgentDataProviderV2 provider,
                AgentDataProviderV2.DebugContext context,
                String affinity,
                DebugMutationAuthorization authorization);
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

    static MutationEntry enterMutation(
            AgentDataProviderV2 provider,
            AgentDataProviderV2.DebugContext context,
            boolean ownerThreadObserved,
            DebugMutationAuthorization.Permit permit) {
        long started = System.nanoTime();
        try {
            CompletableFuture<JsonObject> mutation = provider.mutate(context);
            return new MutationEntry(
                    true, ownerThreadObserved, Thread.currentThread().getName(),
                    elapsedMicros(started), mutation, permit);
        } catch (Throwable throwable) {
            return new MutationEntry(
                    true, ownerThreadObserved, Thread.currentThread().getName(),
                    elapsedMicros(started), CompletableFuture.failedFuture(throwable), permit);
        }
    }

    record MutationEntry(
            boolean supported,
            boolean ownerThreadObserved,
            String threadName,
            long entryDurationMicros,
            CompletableFuture<JsonObject> mutation,
            DebugMutationAuthorization.Permit permit) {
        static MutationEntry unsupported(String affinity) {
            return new MutationEntry(false, false, affinity, 0L, null, () -> { });
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

    private record MutationInvocation(
            long generation,
            CompletableFuture<JsonObject> underlying,
            CompletableFuture<JsonObject> result,
            DebugMutationAuthorization.Permit permit,
            AtomicBoolean retired) {
        MutationInvocation(
                long generation,
                CompletableFuture<JsonObject> underlying,
                CompletableFuture<JsonObject> result,
                DebugMutationAuthorization.Permit permit) {
            this(generation, underlying, result, permit, new AtomicBoolean());
        }
    }

    private record NativeRevisionState(long revision, String fingerprint) {
    }
}
