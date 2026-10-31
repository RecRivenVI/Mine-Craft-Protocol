package io.github.recrivenvi.minecraftprotocol.probe.extension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Per-Runtime owners. Invalidation is synchronous; native cleanup never holds the owner lock. */
public final class ExtensionResources implements AutoCloseable {
    public static final int MAX_SCOPES = 1024;
    public static final int MAX_RESOURCES_PER_SCOPE = 256;
    public static final int CLEANUP_HISTORY = 128;

    public enum Lifetime {
        PROCESS,
        WORLD,
        CONNECTION,
        SCREEN,
        DEBUG_GRANT,
        INPUT_LEASE
    }

    public record Owner(String runtimeId, String packageId, Lifetime lifetime, String contextId) {
        public Owner {
            if (runtimeId == null
                    || runtimeId.isBlank()
                    || packageId == null
                    || packageId.isBlank()
                    || contextId == null
                    || contextId.isBlank())
                throw new IllegalArgumentException("An extension owner requires complete identity");
            Objects.requireNonNull(lifetime);
        }
    }

    public record Cleanup(String resourceId, String ownerThread, String status, String failure) {}

    public record Report(Owner owner, String reason, List<Cleanup> resources) {
        public Report {
            resources = List.copyOf(resources);
        }

        public boolean released() {
            return resources.stream().allMatch(item -> item.status().equals("released"));
        }
    }

    public record ThreadOwner(String name, Executor executor, BooleanSupplier isCurrent) {
        public ThreadOwner {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Owner name required");
            Objects.requireNonNull(executor);
            Objects.requireNonNull(isCurrent);
        }
    }

    private final String runtimeId;
    private final Duration cleanupTimeout;
    private final Map<Owner, Scope> scopes = new LinkedHashMap<>();
    private final Map<Owner, CompletableFuture<Report>> retired = new LinkedHashMap<>();
    private final List<CompletableFuture<Report>> lateCleanups = new ArrayList<>();
    private boolean closed;

    public ExtensionResources(String runtimeId, Duration cleanupTimeout) {
        this.runtimeId = Objects.requireNonNull(runtimeId);
        if (runtimeId.isBlank() || cleanupTimeout.isNegative() || cleanupTimeout.isZero())
            throw new IllegalArgumentException(
                    "Runtime identity and positive cleanup timeout required");
        this.cleanupTimeout = cleanupTimeout;
    }

    public synchronized Scope open(Owner owner, BooleanSupplier grantValid) {
        if (closed) throw new IllegalStateException("extension_runtime_closed");
        if (!runtimeId.equals(owner.runtimeId()))
            throw new IllegalArgumentException("Wrong Runtime owner");
        if (retired.containsKey(owner)) throw new IllegalStateException("extension_owner_retired");
        Scope existing = scopes.get(owner);
        if (existing != null) {
            existing.requireActive();
            return existing;
        }
        if (scopes.size() + retired.values().stream().filter(value -> !value.isDone()).count()
                >= MAX_SCOPES) throw new IllegalStateException("extension_owner_budget_exhausted");
        Scope scope = new Scope(owner, Objects.requireNonNull(grantValid));
        scope.requireActive();
        scopes.put(owner, scope);
        return scope;
    }

    public CompletableFuture<Report> invalidate(Owner owner, String reason) {
        Scope scope;
        synchronized (this) {
            var previous = retired.get(owner);
            if (previous != null) return previous.copy();
            scope = scopes.remove(owner);
            if (scope == null)
                return CompletableFuture.completedFuture(new Report(owner, reason, List.of()));
            // Publish the terminal future before dispatch: a direct executor may reenter here.
            scope.invalidate(reason);
            retired.put(owner, scope.cleanup);
        }
        scope.dispatchCleanup();
        scope.cleanup.whenComplete((report, error) -> trimHistory());
        return scope.cleanup.copy();
    }

    public CompletableFuture<List<Report>> invalidate(
            Lifetime lifetime, String contextId, String reason) {
        List<Owner> owners;
        synchronized (this) {
            owners =
                    scopes.keySet().stream()
                            .filter(
                                    owner ->
                                            owner.lifetime() == lifetime
                                                    && owner.contextId().equals(contextId))
                            .toList();
        }
        List<CompletableFuture<Report>> pending =
                owners.stream().map(owner -> invalidate(owner, reason)).toList();
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> pending.stream().map(CompletableFuture::join).toList());
    }

    public void expireInvalidGrants() {
        List<Owner> invalid;
        List<Scope> candidates;
        synchronized (this) {
            candidates = new ArrayList<>(scopes.values());
        }
        invalid =
                candidates.stream()
                        .filter(scope -> !scope.valid())
                        .map(scope -> scope.owner)
                        .toList();
        invalid.forEach(owner -> invalidate(owner, "grant_invalidated"));
    }

    public synchronized int activeScopes() {
        return scopes.size();
    }

    public synchronized List<CompletableFuture<Report>> cleanupReports() {
        List<CompletableFuture<Report>> results = new ArrayList<>(retired.values());
        results.addAll(lateCleanups);
        return results.stream().map(CompletableFuture::copy).toList();
    }

    private synchronized void trimHistory() {
        var iterator = retired.entrySet().iterator();
        while (retired.size() > CLEANUP_HISTORY && iterator.hasNext()) {
            if (iterator.next().getValue().isDone()) iterator.remove();
        }
        while (lateCleanups.size() > CLEANUP_HISTORY && lateCleanups.get(0).isDone())
            lateCleanups.remove(0);
    }

    @Override
    public void close() {
        List<Owner> owners;
        synchronized (this) {
            if (closed) return;
            closed = true;
            owners = new ArrayList<>(scopes.keySet());
        }
        Collections.reverse(owners);
        owners.forEach(owner -> invalidate(owner, "runtime_close"));
    }

    public final class Scope {
        private final Owner owner;
        private final BooleanSupplier grantValid;
        private final List<Entry> entries = new ArrayList<>();
        private final CompletableFuture<Report> cleanup = new CompletableFuture<>();
        private boolean active = true;
        private String reason;
        private List<Entry> closing;

        private Scope(Owner owner, BooleanSupplier grantValid) {
            this.owner = owner;
            this.grantValid = grantValid;
        }

        public Owner owner() {
            return owner;
        }

        private synchronized boolean valid() {
            if (!active) return false;
            try {
                return grantValid.getAsBoolean();
            } catch (RuntimeException failure) {
                return false;
            }
        }

        public synchronized void requireActive() {
            if (!valid()) throw new IllegalStateException("extension_owner_invalidated");
        }

        /**
         * Copies/publications must complete under this barrier; asynchronous work captures no
         * permit.
         */
        public synchronized <T> T publish(Supplier<T> publication) {
            requireActive();
            return publication.get();
        }

        public void own(String id, ThreadOwner thread, AutoCloseable resource) {
            Entry entry =
                    new Entry(Objects.requireNonNull(id), thread, Objects.requireNonNull(resource));
            synchronized (this) {
                if (id.isBlank() || entries.stream().anyMatch(existing -> existing.id.equals(id)))
                    throw new IllegalArgumentException(
                            "Resource ID must be unique within its owner");
                if (entries.size() >= MAX_RESOURCES_PER_SCOPE)
                    throw new IllegalStateException("extension_resource_budget_exhausted");
                if (valid()) {
                    entries.add(entry);
                    return;
                }
            }
            // Creation may race invalidation. Attempt owner cleanup rather than leak the late
            // resource.
            var attempt =
                    ExtensionResources.this
                            .cleanup(entry)
                            .thenApply(
                                    item -> new Report(owner, "late_registration", List.of(item)));
            synchronized (ExtensionResources.this) {
                lateCleanups.add(attempt);
            }
            attempt.whenComplete((report, error) -> trimHistory());
            throw new IllegalStateException("extension_owner_invalidated");
        }

        private synchronized void invalidate(String cause) {
            active = false;
            reason = cause;
            closing = new ArrayList<>(entries);
            Collections.reverse(closing);
        }

        private void dispatchCleanup() {
            List<CompletableFuture<Cleanup>> results =
                    closing.stream().map(ExtensionResources.this::cleanup).toList();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
                    .thenRun(
                            () ->
                                    cleanup.complete(
                                            new Report(
                                                    owner,
                                                    reason,
                                                    results.stream()
                                                            .map(CompletableFuture::join)
                                                            .toList())));
        }
    }

    private record Entry(String id, ThreadOwner thread, AutoCloseable resource) {
        private Entry {
            Objects.requireNonNull(thread);
        }
    }

    private CompletableFuture<Cleanup> cleanup(Entry entry) {
        CompletableFuture<Cleanup> result = new CompletableFuture<>();
        try {
            entry.thread
                    .executor()
                    .execute(
                            () -> {
                                try {
                                    if (!entry.thread.isCurrent().getAsBoolean())
                                        throw new IllegalStateException(
                                                "extension_cleanup_wrong_owner_thread");
                                    entry.resource.close();
                                    result.complete(
                                            new Cleanup(
                                                    entry.id, entry.thread.name(), "released", ""));
                                } catch (Throwable failure) {
                                    result.complete(
                                            new Cleanup(
                                                    entry.id,
                                                    entry.thread.name(),
                                                    "failed",
                                                    failure.getClass().getSimpleName()));
                                }
                            });
        } catch (RuntimeException rejected) {
            result.complete(
                    new Cleanup(
                            entry.id,
                            entry.thread.name(),
                            "dispatch_rejected",
                            rejected.getClass().getSimpleName()));
        }
        return result.completeOnTimeout(
                new Cleanup(entry.id, entry.thread.name(), "unconfirmed", "cleanup_deadline"),
                cleanupTimeout.toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
