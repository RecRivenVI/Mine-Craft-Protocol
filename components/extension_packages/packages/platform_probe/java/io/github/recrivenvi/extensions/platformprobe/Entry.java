package io.github.recrivenvi.extensions.platformprobe;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.ProviderSchemaRegistry;
import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageExtension;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A real owner-thread sample, independent of test fixtures and never an input path. */
public final class Entry implements PackageExtension {
    private static final String ROOT = "minecraft-extension://platform_probe/schemas/";
    private long revision;
    private long samples;
    private long sampledAt;
    private boolean running;
    private String observedThread = "";
    private Sampling activeSampling;

    private static final class Sampling {
        final ExtensionResources.Scope grant;
        final java.util.concurrent.atomic.AtomicBoolean queued =
                new java.util.concurrent.atomic.AtomicBoolean();
        final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "extension-platform-sampling");
                            thread.setDaemon(true);
                            return thread;
                        });

        Sampling(ExtensionResources.Scope grant) {
            this.grant = grant;
        }
    }

    @Override
    public void initialize(PackageContext context) {
        ExtensionResources.ThreadOwner owner = context.threadOwners().get("server_thread");
        if (owner == null) throw new IllegalStateException("No server-thread owner");
        context.schema(
                ROOT + "query", value -> value.size() == 0 ? pass() : fail("empty query required"));
        context.schema(
                ROOT + "snapshot",
                value ->
                        value.has("running")
                                        && value.has("samples")
                                        && value.has("sampledAtNanos")
                                        && value.has("ownerThread")
                                ? pass()
                                : fail("sampling snapshot required"));
        context.schema(
                ROOT + "revision",
                value ->
                        value.size() == 1
                                        && value.has("running")
                                        && value.get("running").isJsonPrimitive()
                                        && value.getAsJsonPrimitive("running").isBoolean()
                                ? pass()
                                : fail("running resource required"));
        context.schema(
                ROOT + "mutation",
                value ->
                        value.size() == 1
                                        && value.has("action")
                                        && value.get("action").isJsonPrimitive()
                                        && value.getAsJsonPrimitive("action").isString()
                                        && List.of("start", "stop")
                                                .contains(value.get("action").getAsString())
                                ? pass()
                                : fail("start/stop action required"));
        context.schema(
                ROOT + "result",
                value ->
                        value.has("providerRevisionBefore")
                                        && value.has("providerRevisionAfter")
                                        && value.has("revisionStateBefore")
                                        && value.has("revisionStateAfter")
                                        && value.has("before")
                                        && value.has("after")
                                ? pass()
                                : fail("versioned mutation result required"));
        context.provider(
                new AgentDataProviderV2() {
                    @Override
                    public Descriptor descriptor() {
                        return new Descriptor(
                                "platform_probe:sampling",
                                "1",
                                List.of("snapshot"),
                                ROOT + "snapshot",
                                ROOT + "query",
                                "server_thread",
                                List.of("server_authoritative"),
                                "none",
                                "complete",
                                true,
                                false,
                                false,
                                false,
                                false,
                                "provider_revision",
                                "resource",
                                ROOT + "revision",
                                true,
                                "none",
                                new DebugDeclaration(
                                        true,
                                        ROOT + "mutation",
                                        ROOT + "result",
                                        "debug.provider",
                                        true,
                                        true,
                                        "must_advance",
                                        "owner_thread_managed_sampling"),
                                List.of("debug.world.read"));
                    }

                    @Override
                    public CompletableFuture<JsonObject> capture(ReadContext request) {
                        if (!owner.isCurrent().getAsBoolean())
                            throw new IllegalStateException("wrong sample owner");
                        JsonObject value = new JsonObject();
                        value.addProperty("schemaVersion", "1");
                        value.addProperty("providerRevision", revision);
                        value.add("revisionState", revisionState());
                        value.add("data", snapshot());
                        return CompletableFuture.completedFuture(value);
                    }

                    @Override
                    public CompletableFuture<JsonObject> mutate(DebugContext request) {
                        if (!owner.isCurrent().getAsBoolean())
                            throw new IllegalStateException("wrong mutation owner");
                        long previous = revision;
                        JsonObject before = snapshot(), previousState = revisionState();
                        String action = request.mutation().get("action").getAsString();
                        if (action.equals("start")) {
                            if (running) throw new IllegalStateException("sampling already active");
                            Sampling sampling = new Sampling(request.resources());
                            sampling.grant.requireActive();
                            samples = 0;
                            sampledAt = 0;
                            running = true;
                            activeSampling = sampling;
                            sampling.grant.own(
                                    "scheduler-" + revision,
                                    new ExtensionResources.ThreadOwner(
                                            "detached_cleanup", Runnable::run, () -> true),
                                    sampling.scheduler::shutdownNow);
                            sampling.grant.own("sampling-" + revision, owner, () -> stop(sampling));
                            sampling.scheduler.scheduleWithFixedDelay(
                                    () -> {
                                        try {
                                            sampling.grant.requireActive();
                                            if (!sampling.queued.compareAndSet(false, true)) return;
                                            owner.executor()
                                                    .execute(
                                                            () -> {
                                                                if (!owner.isCurrent()
                                                                        .getAsBoolean()) return;
                                                                try {
                                                                    sampling.grant.publish(
                                                                            () -> {
                                                                                if (running
                                                                                        && activeSampling
                                                                                                == sampling) {
                                                                                    samples++;
                                                                                    sampledAt =
                                                                                            System
                                                                                                    .nanoTime();
                                                                                    observedThread =
                                                                                            Thread
                                                                                                    .currentThread()
                                                                                                    .getName();
                                                                                }
                                                                                return null;
                                                                            });
                                                                } catch (
                                                                        IllegalStateException
                                                                                cancelled) {
                                                                    /* Invalid grants cannot publish. */
                                                                } finally {
                                                                    sampling.queued.set(false);
                                                                }
                                                            });
                                        } catch (RuntimeException cancelled) {
                                            sampling.queued.set(
                                                    false); /* Owner cleanup shuts down this sampling session. */
                                        }
                                    },
                                    0,
                                    50,
                                    TimeUnit.MILLISECONDS);
                        } else stop(activeSampling);
                        revision = Math.max(revision, previous + 1);
                        JsonObject value = new JsonObject();
                        value.addProperty("providerRevisionBefore", previous);
                        value.addProperty("providerRevisionAfter", revision);
                        value.add("revisionStateBefore", previousState);
                        value.add("revisionStateAfter", revisionState());
                        value.add("before", before);
                        value.add("after", snapshot());
                        return CompletableFuture.completedFuture(value);
                    }
                });
    }

    private void stop(Sampling sampling) {
        if (sampling == null) return;
        sampling.scheduler.shutdownNow();
        if (activeSampling != sampling) return;
        activeSampling = null;
        running = false;
        revision++;
    }

    private JsonObject revisionState() {
        JsonObject value = new JsonObject();
        value.addProperty("running", running);
        return value;
    }

    private JsonObject snapshot() {
        JsonObject value = revisionState();
        value.addProperty("samples", samples);
        value.addProperty("sampledAtNanos", sampledAt);
        value.addProperty("ownerThread", observedThread);
        return value;
    }

    private static ProviderSchemaRegistry.ValidationResult pass() {
        return ProviderSchemaRegistry.ValidationResult.pass();
    }

    private static ProviderSchemaRegistry.ValidationResult fail(String reason) {
        return ProviderSchemaRegistry.ValidationResult.fail(reason);
    }
}
