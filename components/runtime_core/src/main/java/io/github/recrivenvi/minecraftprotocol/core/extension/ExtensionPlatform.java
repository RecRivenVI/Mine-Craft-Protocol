package io.github.recrivenvi.minecraftprotocol.core.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.api.MinecraftProtocolProvidersV2;
import io.github.recrivenvi.minecraftprotocol.api.ProviderSchemaRegistry;
import io.github.recrivenvi.minecraftprotocol.api.ProviderSource;
import io.github.recrivenvi.minecraftprotocol.api.extension.Extension;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionResources;
import io.github.recrivenvi.minecraftprotocol.api.extension.LogicalInputAdapter;
import io.github.recrivenvi.minecraftprotocol.api.extension.Registration;
import io.github.recrivenvi.minecraftprotocol.api.extension.UiAdapter;
import io.github.recrivenvi.minecraftprotocol.api.extension.WorldAdapter;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlan.Candidate;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlan.Status;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Verified local code feeds existing registries; catalog reads neither initialize nor execute it.
 */
public final class ExtensionPlatform implements ProviderSource, AutoCloseable {
    public record Environment(
            String productId,
            String target,
            ExtensionContext.Side side,
            int javaVersion,
            Map<String, String> mods,
            ClassLoader parent) {
        public Environment {
            java.util.Objects.requireNonNull(productId);
            java.util.Objects.requireNonNull(target);
            java.util.Objects.requireNonNull(side);
            java.util.Objects.requireNonNull(parent);
            mods = Map.copyOf(mods);
        }
    }

    public interface EarlyHooks {
        /**
         * Must verify this exact content/Target/side. Prepared resources alone are not applied
         * hooks.
         */
        boolean prepared(ExtensionManifest manifest, Environment environment);

        default JsonObject diagnostics() {
            JsonObject value = new JsonObject();
            value.addProperty("state", "not_prepared");
            return value;
        }

        default List<String> nativeExports(ExtensionManifest manifest, Environment environment) {
            return List.of();
        }
    }

    private final Environment environment;
    private final EarlyHooks earlyHooks;
    private final List<Status> statuses = new ArrayList<>();
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final List<Transaction> active = new ArrayList<>();
    private final Map<String, AgentDataProviderV2> registeredProviders = new LinkedHashMap<>();
    private final Map<String, WorldAdapter> registeredWorld = new LinkedHashMap<>();
    private final Map<String, UiAdapter> registeredUi = new LinkedHashMap<>();
    private final Map<String, LogicalInputAdapter> registeredInputs = new LinkedHashMap<>();
    private final Map<String, ProviderSchemaRegistry.Validator> registeredSchemas =
            new LinkedHashMap<>();
    private final Set<String> knownProviders = new HashSet<>();
    private final String runtimeId;
    private final ExtensionResources resources;
    private final java.util.concurrent.ScheduledExecutorService resourceExpiry;
    private final Map<String, ExtensionResources.ThreadOwner> threadOwners;
    private final Map<String, java.util.function.Supplier<Object>> nativeContexts;
    private final java.util.function.Supplier<
                    io.github.recrivenvi.minecraftprotocol.api.extension.LifetimeContexts>
            lifetimeContexts;
    private final java.util.function.Supplier<
                    io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession.Snapshot>
            controlState;
    private volatile boolean closed;
    private final java.util.concurrent.ConcurrentLinkedDeque<JsonObject> inputCleanup =
            new java.util.concurrent.ConcurrentLinkedDeque<>();

    private CompletableFuture<Void> trackInputCleanup(LogicalInputAdapter adapter, String reason) {
        var report = new JsonObject();
        report.addProperty("adapterId", adapter.id());
        report.addProperty("reason", reason);
        report.addProperty("startedAtMillis", System.currentTimeMillis());
        CompletableFuture<Void> pending;
        try {
            pending =
                    java.util.Objects.requireNonNull(adapter.reset(reason))
                            .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable failure) {
            pending = CompletableFuture.failedFuture(failure);
        }
        return pending.whenComplete(
                (ignored, error) -> {
                    report.addProperty("status", error == null ? "released" : "failed");
                    report.addProperty(
                            "failure", error == null ? "" : error.getClass().getSimpleName());
                    inputCleanup.addLast(report);
                    while (inputCleanup.size() > 128) inputCleanup.pollFirst();
                });
    }

    public ExtensionPlatform(ExtensionStore store, Environment environment, EarlyHooks hooks) {
        this(store, environment, hooks, java.util.UUID.randomUUID().toString());
    }

    public ExtensionPlatform(
            ExtensionStore store, Environment environment, EarlyHooks hooks, String runtimeId) {
        this(store, environment, hooks, runtimeId, Map.of());
    }

    public ExtensionPlatform(
            ExtensionStore store,
            Environment environment,
            EarlyHooks hooks,
            String runtimeId,
            Map<String, ExtensionResources.ThreadOwner> threadOwners) {
        this(store, environment, hooks, runtimeId, threadOwners, null);
    }

    public ExtensionPlatform(
            ExtensionStore store,
            Environment environment,
            EarlyHooks hooks,
            String runtimeId,
            Map<String, ExtensionResources.ThreadOwner> threadOwners,
            java.util.function.Supplier<
                            io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession
                                    .Snapshot>
                    controlState) {
        this(store, environment, hooks, runtimeId, threadOwners, controlState, Map.of());
    }

    public ExtensionPlatform(
            ExtensionStore store,
            Environment environment,
            EarlyHooks hooks,
            String runtimeId,
            Map<String, ExtensionResources.ThreadOwner> threadOwners,
            java.util.function.Supplier<
                            io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession
                                    .Snapshot>
                    controlState,
            Map<String, java.util.function.Supplier<Object>> nativeContexts) {
        this(
                store,
                environment,
                hooks,
                runtimeId,
                threadOwners,
                controlState,
                nativeContexts,
                io.github.recrivenvi.minecraftprotocol.api.extension.LifetimeContexts::empty);
    }

    public ExtensionPlatform(
            ExtensionStore store,
            Environment environment,
            EarlyHooks hooks,
            String runtimeId,
            Map<String, ExtensionResources.ThreadOwner> threadOwners,
            java.util.function.Supplier<
                            io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession
                                    .Snapshot>
                    controlState,
            Map<String, java.util.function.Supplier<Object>> nativeContexts,
            java.util.function.Supplier<
                            io.github.recrivenvi.minecraftprotocol.api.extension.LifetimeContexts>
                    lifetimeContexts) {
        this.environment = environment;
        this.controlState = controlState;
        this.earlyHooks = hooks;
        this.threadOwners = Map.copyOf(threadOwners);
        this.nativeContexts = Map.copyOf(nativeContexts);
        this.lifetimeContexts = java.util.Objects.requireNonNull(lifetimeContexts);
        this.runtimeId = runtimeId;
        this.resources = new ExtensionResources(runtimeId, java.time.Duration.ofSeconds(2));
        this.resourceExpiry =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                        action -> {
                            Thread thread =
                                    new Thread(action, "mine_craft_protocol-extension-resources");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.resourceExpiry.scheduleWithFixedDelay(
                resources::expireInvalidGrants, 50, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        ExtensionPlan plan = new ExtensionPlan(store, environment, true);
        ExtensionPackSelection.startup(store, environment);
        statuses.addAll(plan.statuses);
        candidates.putAll(plan.candidates);
        for (Candidate candidate : plan.order) {
            var manifest = candidate.source.manifest();
            if (ExtensionPlan.requiresHooks(manifest, environment)
                    && !hooks.prepared(manifest, environment)) {
                candidate.status.fail(
                        "EXTENSION_EARLY_HOOK_UNAVAILABLE",
                        "Approved hooks were not registered before class transformation; run the offline installer and restart");
            }
            if (candidate.status.state.equals("verified"))
                candidate.loader.nativeExports(hooks.nativeExports(manifest, environment));
        }
        for (Candidate candidate : plan.order) initialize(candidate);
    }

    private static String initializationDetail(Throwable failure) {
        String message = failure.getMessage();
        String detail =
                failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return detail.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(detail.length(), 512));
    }

    private void initialize(Candidate candidate) {
        if (!candidate.status.state.equals("verified")) return;
        Map<String, ExtensionClassSpace> dependencies = new LinkedHashMap<>();
        for (String dependency : candidate.source.manifest().packageDependencies().keySet()) {
            Candidate required = candidates.get(dependency);
            if (!required.status.state.equals("active")) {
                candidate.status.fail(
                        "EXTENSION_PACKAGE_DEPENDENCY", "A dependency failed initialization");
                return;
            }
            dependencies.put(dependency, required.loader);
        }
        candidate.loader.dependencies(dependencies);
        Transaction transaction = new Transaction(candidate);
        try {
            for (String entry :
                    candidate
                            .source
                            .manifest()
                            .variants()
                            .get(environment.target())
                            .entrypoints(environment.side())) {
                Class<?> type = Class.forName(entry, false, candidate.loader);
                if (type.getClassLoader() != candidate.loader
                        || !Extension.class.isAssignableFrom(type))
                    throw new ExtensionFailure(
                            "EXTENSION_ENTRYPOINT_INVALID",
                            "Entry must implement the shared SDK, without embedding another copy");
                ((Extension) type.getConstructor().newInstance()).initialize(transaction);
            }
            transaction.commit();
            active.add(transaction);
            candidate.status.state = "active";
            var registered = new ArrayList<>(transaction.providers.keySet());
            registered.addAll(transaction.ui.keySet());
            registered.addAll(transaction.inputs.keySet());
            registered.addAll(transaction.world.keySet());
            candidate.status.registered = List.copyOf(registered);
        } catch (Throwable failure) {
            transaction.close();
            Throwable cause =
                    failure instanceof java.lang.reflect.InvocationTargetException wrapped
                            ? wrapped.getCause()
                            : failure;
            candidate.status.fail(
                    cause instanceof ExtensionFailure known
                            ? known.code()
                            : "EXTENSION_INITIALIZATION_FAILED",
                    initializationDetail(cause));
        }
    }

    @Override
    public synchronized List<AgentDataProviderV2> snapshot() {
        return List.copyOf(registeredProviders.values());
    }

    public WorldExtensionEngine worldEngine() {
        return new WorldExtensionEngine(
                runtimeId,
                () -> {
                    synchronized (this) {
                        return List.copyOf(registeredWorld.values());
                    }
                });
    }

    public UiExtensionEngine uiEngine() {
        return new UiExtensionEngine(
                runtimeId,
                () -> {
                    synchronized (this) {
                        return List.copyOf(registeredUi.values());
                    }
                });
    }

    public CompletableFuture<Void> resetInput(String reason) {
        List<LogicalInputAdapter> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(registeredInputs.values());
        }
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (var adapter : snapshot) {
            pending.add(trackInputCleanup(adapter, reason));
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .handle(
                        (ignored, error) -> {
                            if (error != null)
                                throw new io.github.recrivenvi.minecraftprotocol.core.ProtocolState
                                        .ProtocolException(
                                        "CONTROL_INPUT_CLEANUP_FAILED",
                                        409,
                                        "Extension input cleanup failed; inspect /v0/extensions/resources for the adapter identity");
                            return null;
                        });
    }

    @Override
    public synchronized boolean owns(String providerId) {
        return knownProviders.contains(providerId);
    }

    @Override
    public ProviderSchemaRegistry.ValidationResult validate(
            String providerId, String schemaId, JsonObject value) {
        ProviderSchemaRegistry.Validator validator;
        synchronized (this) {
            if (closed || !registeredProviders.containsKey(providerId))
                return ProviderSchemaRegistry.ValidationResult.fail("extension_provider_closed");
            validator = registeredSchemas.get(schemaId);
        }
        return validator != null
                ? ProviderSchemaRegistry.validateValue(validator, value)
                : ProviderSchemaRegistry.validate(schemaId, value);
    }

    /** Static, authenticated metadata only; neither a world snapshot nor a Debug grant. */
    public synchronized JsonObject catalog() {
        JsonObject result = new JsonObject();
        result.addProperty("type", "extensions.catalog");
        result.addProperty("sdkApi", ExtensionContext.SDK_API);
        result.addProperty("formatVersion", ExtensionManifest.FORMAT);
        result.addProperty("target", environment.target());
        result.addProperty("side", environment.side().name());
        result.addProperty("trustedLocalCode", true);
        result.addProperty("jvmSandbox", false);
        result.addProperty("codeUpdateRequiresRestart", true);
        result.addProperty("closed", closed);
        result.add("earlyHooks", earlyHooks.diagnostics().deepCopy());
        JsonArray packages = new JsonArray();
        for (Status status : statuses) {
            JsonObject item = new JsonObject();
            item.addProperty("candidate", status.candidate);
            item.addProperty(
                    "status", closed && status.state.equals("active") ? "closed" : status.state);
            item.addProperty("reason", status.detail);
            if (status.manifest != null) {
                item.addProperty("packageId", status.manifest.id());
                item.addProperty("version", status.manifest.version());
                item.addProperty("contentIdentity", status.manifest.contentIdentity());
                JsonArray declared = new JsonArray();
                status.manifest.capabilities().stream().sorted().forEach(declared::add);
                item.add("declaredCapabilities", declared);
                JsonArray registered = new JsonArray();
                status.registered.stream()
                        .filter(
                                id ->
                                        registeredProviders.containsKey(id)
                                                || registeredUi.containsKey(id)
                                                || registeredInputs.containsKey(id)
                                                || registeredWorld.containsKey(id))
                        .sorted()
                        .forEach(registered::add);
                item.add("registeredCapabilities", registered);
                JsonArray details = new JsonArray();
                for (String id : status.registered.stream().sorted().toList()) {
                    String kind =
                            registeredProviders.containsKey(id)
                                    ? "provider"
                                    : registeredUi.containsKey(id)
                                            ? "ui_projection"
                                            : registeredWorld.containsKey(id)
                                                    ? "world_parts"
                                                    : registeredInputs.containsKey(id)
                                                            ? "logical_input"
                                                            : null;
                    if (kind == null) continue;
                    JsonObject detail = new JsonObject();
                    detail.addProperty("id", id);
                    detail.addProperty("kind", kind);
                    detail.addProperty("grantsAuthority", false);
                    if (registeredUi.containsKey(id)) {
                        JsonArray views = new JsonArray();
                        registeredUi.get(id).descriptor().views().stream()
                                .map(Enum::name)
                                .sorted()
                                .forEach(views::add);
                        detail.add("views", views);
                        detail.addProperty("actionRoute", "existing_game_routed_input");
                    }
                    if (registeredWorld.containsKey(id)) {
                        detail.add("views", new com.google.gson.Gson().toJsonTree(List.of("USER")));
                        detail.addProperty(
                                "actionRoute", "existing_game_routed_native_parent_pick");
                    }
                    details.add(detail);
                }
                item.add("capabilityDetails", details);
                JsonObject docs = new JsonObject();
                status.manifest
                        .documents()
                        .forEach(
                                (id, path) ->
                                        docs.addProperty(id, status.manifest.files().get(path)));
                item.add("documents", docs);
                JsonObject tests = new JsonObject();
                status.manifest
                        .tests()
                        .forEach(
                                (id, path) ->
                                        tests.addProperty(id, status.manifest.files().get(path)));
                item.add("tests", tests);
            }
            packages.add(item);
        }
        result.add("packages", packages);
        return result;
    }

    public synchronized JsonObject document(String packageId, String documentId, boolean test) {
        Candidate candidate = candidates.get(packageId);
        if (candidate == null || !candidate.status.state.equals("active") || closed)
            throw new ExtensionFailure(
                    "EXTENSION_PACKAGE_UNAVAILABLE",
                    "No active approved package with this identity");
        var index =
                test
                        ? candidate.source.manifest().tests()
                        : candidate.source.manifest().documents();
        String path = index.get(documentId);
        if (path == null)
            throw new ExtensionFailure(
                    "EXTENSION_DOCUMENT_NOT_FOUND", "No declared document/test ID");
        byte[] bytes = candidate.source.read(path, ExtensionManifest.MAX_MANIFEST_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8)))
            throw new ExtensionFailure("EXTENSION_DOCUMENT_INVALID", "Document is not UTF-8");
        JsonObject result = new JsonObject();
        result.addProperty("type", test ? "extension.test" : "extension.document");
        result.addProperty("packageId", packageId);
        result.addProperty("documentId", documentId);
        result.addProperty("contentIdentity", candidate.source.manifest().contentIdentity());
        result.addProperty("sha256", ExtensionManifest.digest(bytes));
        result.addProperty("text", text);
        result.addProperty("contentIsData", true);
        return result;
    }

    public synchronized ExtensionResources.Scope providerResources(
            String providerId, String grantId, java.util.function.BooleanSupplier valid) {
        if (!registeredProviders.containsKey(providerId) || closed)
            throw new ExtensionFailure(
                    "EXTENSION_CAPABILITY_UNAVAILABLE",
                    "Provider registration is no longer active");
        var candidate =
                candidates.values().stream()
                        .filter(item -> item.status.registered.contains(providerId))
                        .findFirst()
                        .orElseThrow();
        return resources.open(
                new ExtensionResources.Owner(
                        runtimeId,
                        candidate.source.manifest().id(),
                        ExtensionResources.Lifetime.DEBUG_GRANT,
                        grantId),
                () -> !closed && valid.getAsBoolean());
    }

    public JsonObject resourceDiagnostics() {
        JsonObject value = new JsonObject();
        value.addProperty("type", "extensions.resources");
        value.addProperty("runtimeInstanceId", runtimeId);
        value.addProperty("activeScopes", resources.activeScopes());
        JsonArray reports = new JsonArray();
        for (var pending : resources.cleanupReports()) {
            JsonObject report = new JsonObject();
            report.addProperty("complete", pending.isDone());
            var completed = pending.getNow(null);
            if (completed != null) {
                report.addProperty("packageId", completed.owner().packageId());
                report.addProperty("lifetime", completed.owner().lifetime().name());
                report.addProperty("contextId", completed.owner().contextId());
                report.addProperty("reason", completed.reason());
                report.addProperty("released", completed.released());
                JsonArray entries = new JsonArray();
                for (var item : completed.resources()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("resourceId", item.resourceId());
                    entry.addProperty("ownerThread", item.ownerThread());
                    entry.addProperty("status", item.status());
                    entry.addProperty("failure", item.failure());
                    entries.add(entry);
                }
                report.add("resources", entries);
            }
            reports.add(report);
        }
        value.add("cleanup", reports);
        var inputs = new JsonArray();
        inputCleanup.forEach(report -> inputs.add(report.deepCopy()));
        value.add("inputCleanup", inputs);
        return value;
    }

    public ExtensionResources resources() {
        return resources;
    }

    @Override
    public void close() {
        List<Transaction> closing;
        synchronized (this) {
            if (closed) return;
            closed = true;
            closing = new ArrayList<>(active);
        }
        Collections.reverse(closing);
        resources.close();
        resourceExpiry.shutdownNow();
        closing.forEach(Transaction::close);
    }

    private final class Transaction implements ExtensionContext, AutoCloseable {
        private final Candidate candidate;
        private final Map<String, ProviderSchemaRegistry.Validator> schemas = new LinkedHashMap<>();
        private final Map<String, AgentDataProviderV2> providers = new LinkedHashMap<>();
        private final Map<String, WorldAdapter> world = new LinkedHashMap<>();
        private final Map<String, UiAdapter> ui = new LinkedHashMap<>();
        private final Map<String, LogicalInputAdapter> inputs = new LinkedHashMap<>();
        private final List<Handle> handles = new ArrayList<>();
        private boolean sealed;
        private volatile boolean disposed;

        Transaction(Candidate candidate) {
            this.candidate = candidate;
        }

        @Override
        public String packageId() {
            return candidate.source.manifest().id();
        }

        @Override
        public String contentIdentity() {
            return candidate.source.manifest().contentIdentity();
        }

        @Override
        public String target() {
            return environment.target();
        }

        @Override
        public Side side() {
            return environment.side();
        }

        @Override
        public ControlState controlState() {
            if (ExtensionPlatform.this.controlState == null)
                return new ControlState("READ", runtimeId, 0, false);
            var value = ExtensionPlatform.this.controlState.get();
            return new ControlState(
                    value.mode().name(),
                    value.controlSessionId(),
                    value.transitionSequence(),
                    value.reconsentRequired());
        }

        @Override
        public Map<String, ExtensionResources.ThreadOwner> threadOwners() {
            return threadOwners;
        }

        @Override
        public Object nativeContext(String ownerId) {
            if (closed || disposed || !candidate.status.state.equals("active"))
                throw new ExtensionFailure(
                        "EXTENSION_CAPABILITY_UNAVAILABLE", "Package context is not active");
            var owner = threadOwners.get(ownerId);
            var context = nativeContexts.get(ownerId);
            if (owner == null || context == null)
                throw new ExtensionFailure(
                        "EXTENSION_NATIVE_CONTEXT_UNAVAILABLE",
                        "No native context for this runtime/side owner");
            if (!owner.isCurrent().getAsBoolean())
                throw new ExtensionFailure(
                        "EXTENSION_WRONG_THREAD_OWNER",
                        "Native context requires its current owner thread");
            Object value = context.get();
            if (value == null)
                throw new ExtensionFailure(
                        "EXTENSION_NATIVE_CONTEXT_UNAVAILABLE",
                        "Native context is unavailable or closing");
            return value;
        }

        @Override
        public ExtensionResources.Scope processResources() {
            return resources.open(
                    new ExtensionResources.Owner(
                            runtimeId,
                            packageId(),
                            ExtensionResources.Lifetime.PROCESS,
                            contentIdentity()),
                    () -> !closed && !disposed);
        }

        @Override
        public io.github.recrivenvi.minecraftprotocol.api.extension.LifetimeContexts
                lifetimeContexts() {
            var values =
                    new java.util.EnumMap<ExtensionResources.Lifetime, String>(
                            ExtensionResources.Lifetime.class);
            if (!closed && !disposed) {
                values.putAll(ExtensionPlatform.this.lifetimeContexts.get().identities());
                var control = controlState();
                if (control.takeoverActive())
                    values.put(
                            ExtensionResources.Lifetime.INPUT_LEASE,
                            control.controlSessionId()
                                    + ":"
                                    + control.generation()
                                    + ":"
                                    + values.getOrDefault(ExtensionResources.Lifetime.WORLD, "")
                                    + ":"
                                    + values.getOrDefault(
                                            ExtensionResources.Lifetime.CONNECTION, ""));
            }
            return new io.github.recrivenvi.minecraftprotocol.api.extension.LifetimeContexts(
                    values);
        }

        @Override
        public ExtensionResources.Scope contextResources(ExtensionResources.Lifetime lifetime) {
            if (!Set.of(
                            ExtensionResources.Lifetime.WORLD,
                            ExtensionResources.Lifetime.CONNECTION,
                            ExtensionResources.Lifetime.SCREEN,
                            ExtensionResources.Lifetime.INPUT_LEASE)
                    .contains(lifetime))
                throw new IllegalArgumentException(
                        "Use processResources or the authorized DebugContext for this lifetime");
            String id = lifetimeContexts().identity(lifetime);
            if (id == null)
                throw new ExtensionFailure(
                        "EXTENSION_CONTEXT_UNAVAILABLE",
                        "Native context is unavailable: " + lifetime);
            return resources.open(
                    new ExtensionResources.Owner(runtimeId, packageId(), lifetime, id),
                    () -> id.equals(lifetimeContexts().identity(lifetime)));
        }

        @Override
        public synchronized Registration schema(
                String id, ProviderSchemaRegistry.Validator validator) {
            requireStaging();
            if (!id.startsWith("minecraft-extension://" + packageId() + "/schemas/")
                    || schemas.putIfAbsent(id, java.util.Objects.requireNonNull(validator)) != null)
                throw new ExtensionFailure(
                        "EXTENSION_SCHEMA_INVALID",
                        "Schema identity must be unique and owned by its package");
            Handle handle =
                    new Handle(
                            this,
                            () -> schemas.remove(id),
                            () -> {
                                synchronized (ExtensionPlatform.this) {
                                    if (registeredSchemas.get(id) == validator)
                                        registeredSchemas.remove(id);
                                }
                            });
            handles.add(handle);
            return handle;
        }

        @Override
        public synchronized Registration provider(AgentDataProviderV2 provider) {
            requireStaging();
            var descriptor = java.util.Objects.requireNonNull(provider.descriptor());
            if (!candidate.source.manifest().capabilities().contains(descriptor.providerId())
                    || !candidate
                            .source
                            .manifest()
                            .requestedScopes()
                            .containsAll(descriptor.requiredScopes())
                    || descriptor.debugDeclaration().supported()
                            && !candidate
                                    .source
                                    .manifest()
                                    .requestedScopes()
                                    .contains(descriptor.debugDeclaration().requiredScope()))
                throw new ExtensionFailure(
                        "EXTENSION_CAPABILITY_UNDECLARED",
                        "Provider/scopes are outside the approved declaration");
            Handle handle =
                    new Handle(this, () -> providers.remove(descriptor.providerId()), () -> {});
            AgentDataProviderV2 guarded =
                    new AgentDataProviderV2() {
                        @Override
                        public Descriptor descriptor() {
                            return descriptor;
                        }

                        @Override
                        public CompletableFuture<JsonObject> capture(ReadContext context) {
                            checkLive(handle);
                            return provider.capture(context)
                                    .thenApply(
                                            value -> {
                                                checkLive(handle);
                                                return value.deepCopy();
                                            });
                        }

                        @Override
                        public CompletableFuture<JsonObject> mutate(DebugContext context) {
                            checkLive(handle);
                            return provider.mutate(context)
                                    .thenApply(
                                            value -> {
                                                checkLive(handle);
                                                return value.deepCopy();
                                            });
                        }
                    };
            if (providers.putIfAbsent(descriptor.providerId(), guarded) != null)
                throw new ExtensionFailure(
                        "EXTENSION_CAPABILITY_DUPLICATE", "Duplicate capability registration");
            handle.remove =
                    () -> {
                        synchronized (ExtensionPlatform.this) {
                            if (registeredProviders.get(descriptor.providerId()) == guarded)
                                registeredProviders.remove(descriptor.providerId());
                        }
                    };
            handles.add(handle);
            return handle;
        }

        @Override
        public synchronized Registration uiAdapter(UiAdapter adapter) {
            requireStaging();
            var descriptor = java.util.Objects.requireNonNull(adapter.descriptor());
            if (environment.side() != Side.CLIENT
                    || !candidate.source.manifest().requestedScopes().contains("ui")
                    || !candidate.source.manifest().capabilities().contains(descriptor.id())
                    || !descriptor.id().startsWith(packageId() + ":"))
                throw new ExtensionFailure(
                        "EXTENSION_UI_UNDECLARED",
                        "Client UI capability must be declared and package-owned");
            Handle handle = new Handle(this, () -> ui.remove(descriptor.id()), () -> {});
            UiAdapter guarded =
                    new UiAdapter() {
                        @Override
                        public Descriptor descriptor() {
                            return descriptor;
                        }

                        @Override
                        public boolean matches(Frame frame) {
                            checkLive(handle);
                            return adapter.matches(frame);
                        }

                        @Override
                        public Projection capture(Context context) {
                            checkLive(handle);
                            var result = adapter.capture(context);
                            checkLive(handle);
                            return result;
                        }
                    };
            if (ui.putIfAbsent(descriptor.id(), guarded) != null
                    || providers.containsKey(descriptor.id()))
                throw new ExtensionFailure(
                        "EXTENSION_CAPABILITY_DUPLICATE", "Duplicate UI capability registration");
            handle.remove =
                    () -> {
                        synchronized (ExtensionPlatform.this) {
                            if (registeredUi.get(descriptor.id()) == guarded)
                                registeredUi.remove(descriptor.id());
                        }
                    };
            handles.add(handle);
            return handle;
        }

        @Override
        public synchronized Registration worldAdapter(WorldAdapter adapter) {
            requireStaging();
            var descriptor = java.util.Objects.requireNonNull(adapter.descriptor());
            String id = descriptor.id();
            if (environment.side() != Side.CLIENT
                    || !candidate.source.manifest().requestedScopes().contains("read")
                    || !candidate.source.manifest().capabilities().contains(id)
                    || !id.startsWith(packageId() + ":"))
                throw new ExtensionFailure(
                        "EXTENSION_WORLD_UNDECLARED",
                        "Client world adapter must be declared and package-owned");
            Handle handle = new Handle(this, () -> world.remove(id), () -> {});
            WorldAdapter guarded =
                    new WorldAdapter() {
                        public Descriptor descriptor() {
                            return descriptor;
                        }

                        public boolean matches(Frame frame) {
                            checkLive(handle);
                            return adapter.matches(frame);
                        }

                        public Projection capture(Context context) {
                            checkLive(handle);
                            var result = adapter.capture(context);
                            checkLive(handle);
                            return result;
                        }
                    };
            if (world.putIfAbsent(id, guarded) != null)
                throw new ExtensionFailure(
                        "EXTENSION_CAPABILITY_DUPLICATE", "Duplicate world adapter");
            handle.remove =
                    () -> {
                        synchronized (ExtensionPlatform.this) {
                            if (registeredWorld.get(id) == guarded) registeredWorld.remove(id);
                        }
                    };
            handles.add(handle);
            return handle;
        }

        @Override
        public synchronized Registration inputAdapter(LogicalInputAdapter adapter) {
            requireStaging();
            String id = java.util.Objects.requireNonNull(adapter.id());
            if (environment.side() != Side.CLIENT
                    || !candidate.source.manifest().requestedScopes().contains("input")
                    || !candidate.source.manifest().capabilities().contains(id)
                    || !id.startsWith(packageId() + ":"))
                throw new ExtensionFailure(
                        "EXTENSION_INPUT_UNDECLARED",
                        "Client polling adapter must be declared and package-owned");
            Handle handle = new Handle(this, () -> inputs.remove(id), () -> {});
            LogicalInputAdapter guarded =
                    new LogicalInputAdapter() {
                        @Override
                        public String id() {
                            return id;
                        }

                        @Override
                        public CompletableFuture<Void> reset(String reason) {
                            checkLive(handle);
                            return adapter.reset(reason);
                        }
                    };
            if (inputs.putIfAbsent(id, guarded) != null
                    || ui.containsKey(id)
                    || providers.containsKey(id))
                throw new ExtensionFailure(
                        "EXTENSION_CAPABILITY_DUPLICATE", "Duplicate polling adapter");
            handle.remove =
                    () -> {
                        synchronized (ExtensionPlatform.this) {
                            if (registeredInputs.get(id) == guarded) registeredInputs.remove(id);
                        }
                    };
            handles.add(handle);
            return handle;
        }

        private void requireStaging() {
            if (sealed) throw new IllegalStateException("extension_registration_sealed");
        }

        private void checkLive(Handle handle) {
            if (closed) throw new IllegalStateException("extension_runtime_closed");
            handle.check();
        }

        synchronized void commit() {
            sealed = true;
            Set<String> nativeIds = new HashSet<>();
            MinecraftProtocolProvidersV2.snapshot()
                    .forEach(provider -> nativeIds.add(provider.descriptor().providerId()));
            synchronized (ExtensionPlatform.this) {
                if (registeredUi.size() + ui.size() > 64
                        || registeredInputs.size() + inputs.size() > 64
                        || registeredWorld.size() + world.size() > 64)
                    throw new ExtensionFailure(
                            "EXTENSION_REGISTRATION_BUDGET", "Client adapter registry is full");
                if (registeredProviders.size() + providers.size() + nativeIds.size()
                        > MinecraftProtocolProvidersV2.MAX_REGISTERED_PROVIDERS)
                    throw new ExtensionFailure(
                            "EXTENSION_PROVIDER_BUDGET", "Runtime provider registry is full");
                var occupied = new HashSet<String>(nativeIds);
                occupied.addAll(registeredProviders.keySet());
                occupied.addAll(registeredUi.keySet());
                occupied.addAll(registeredInputs.keySet());
                occupied.addAll(registeredWorld.keySet());
                for (var contribution :
                        List.of(providers.keySet(), ui.keySet(), inputs.keySet(), world.keySet()))
                    for (String id : contribution)
                        if (!occupied.add(id))
                            throw new ExtensionFailure(
                                    "EXTENSION_REGISTRATION_DUPLICATE",
                                    "Capability collides with an existing contribution");
                if (schemas.keySet().stream()
                        .anyMatch(
                                id ->
                                        registeredSchemas.containsKey(id)
                                                || ProviderSchemaRegistry.contains(id)))
                    throw new ExtensionFailure(
                            "EXTENSION_REGISTRATION_DUPLICATE",
                            "Schema collides with an existing contribution");
                providers
                        .values()
                        .forEach(
                                provider ->
                                        MinecraftProtocolProvidersV2.validateDescriptor(
                                                provider.descriptor(),
                                                id ->
                                                        schemas.containsKey(id)
                                                                || registeredSchemas.containsKey(id)
                                                                || ProviderSchemaRegistry.contains(
                                                                        id)));
                registeredSchemas.putAll(schemas);
                registeredProviders.putAll(providers);
                registeredUi.putAll(ui);
                registeredInputs.putAll(inputs);
                registeredWorld.putAll(world);
                knownProviders.addAll(providers.keySet());
                handles.forEach(handle -> handle.committed = true);
            }
        }

        @Override
        public synchronized void close() {
            sealed = true;
            disposed = true;
            resources.invalidate(
                    ExtensionResources.Lifetime.PROCESS, contentIdentity(), "package_closed");
            List<Handle> closing = new ArrayList<>(handles);
            Collections.reverse(closing);
            closing.forEach(Handle::close);
        }
    }

    private static final class Handle implements Registration {
        private volatile boolean active = true;
        private volatile boolean committed;
        private final Object lock;
        private final Runnable discard;
        private Runnable remove;

        Handle(Object lock, Runnable discard, Runnable remove) {
            this.lock = lock;
            this.discard = discard;
            this.remove = remove;
        }

        void check() {
            if (!active) throw new IllegalStateException("extension_registration_closed");
        }

        @Override
        public boolean active() {
            return active && committed;
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (!active) return;
                active = false;
                if (committed) remove.run();
                else discard.run();
            }
        }
    }
}
