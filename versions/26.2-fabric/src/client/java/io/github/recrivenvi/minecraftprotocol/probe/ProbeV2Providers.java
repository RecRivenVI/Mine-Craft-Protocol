package io.github.recrivenvi.minecraftprotocol.probe;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

final class ProbeV2Providers {
    private ProbeV2Providers() {
    }

    static void registerAll() {
        MinecraftProtocolProvidersV2.register(new TestProvider("safe", "none", true, false));
        MinecraftProtocolProvidersV2.register(new TestProvider("lazy", "lazy_initialization", false, true));
        MinecraftProtocolProvidersV2.register(new FailureProvider("failure", FailureMode.THROW));
        MinecraftProtocolProvidersV2.register(new FailureProvider("timeout", FailureMode.TIMEOUT));
        MinecraftProtocolProvidersV2.register(new FailureProvider("oversized", FailureMode.OVERSIZED));
        MinecraftProtocolProvidersV2.register(new FailureProvider("invalid", FailureMode.INVALID_SCHEMA));
    }

    private static AgentDataProviderV2.Descriptor descriptor(
            String name, String effects, boolean safe, boolean mayInitialize) {
        return new AgentDataProviderV2.Descriptor(
                "minecraft_protocol_probe:" + name,
                "1",
                List.of("snapshot"),
                "minecraft_protocol_probe://schemas/provider-test-v1",
                "detached_provider_worker",
                List.of("server_authoritative", "client_known"),
                effects,
                "complete",
                safe,
                mayInitialize,
                false,
                false,
                false,
                "provider_revision",
                name.equals("safe") ? "snapshot_diff_delta" : "none",
                new AgentDataProviderV2.DebugDeclaration(false, "", "debug", true),
                List.of("read"));
    }

    private static final class TestProvider implements AgentDataProviderV2 {
        private final Descriptor descriptor;
        private final AtomicLong revision = new AtomicLong();

        private TestProvider(String name, String effects, boolean safe, boolean mayInitialize) {
            this.descriptor = ProbeV2Providers.descriptor(name, effects, safe, mayInitialize);
        }

        @Override
        public Descriptor descriptor() {
            return this.descriptor;
        }

        @Override
        public CompletableFuture<JsonObject> capture(ReadContext context) {
            JsonObject data = new JsonObject();
            data.addProperty("schemaVersion", this.descriptor.schemaVersion());
            JsonObject value = new JsonObject();
            value.addProperty("echo", context.query().toString());
            value.addProperty("invocation", this.revision.incrementAndGet());
            data.add("data", value);
            data.addProperty("providerRevision", this.revision.get());
            return CompletableFuture.completedFuture(data);
        }
    }

    private enum FailureMode { THROW, TIMEOUT, OVERSIZED, INVALID_SCHEMA }

    private static final class FailureProvider implements AgentDataProviderV2 {
        private final Descriptor descriptor;
        private final FailureMode mode;

        private FailureProvider(String name, FailureMode mode) {
            this.descriptor = ProbeV2Providers.descriptor(name, "none", true, false);
            this.mode = mode;
        }

        @Override
        public Descriptor descriptor() {
            return this.descriptor;
        }

        @Override
        public CompletableFuture<JsonObject> capture(ReadContext context) {
            return switch (this.mode) {
                case THROW -> CompletableFuture.failedFuture(new IllegalStateException("intentional provider failure"));
                case TIMEOUT -> new CompletableFuture<>();
                case OVERSIZED -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("schemaVersion", "1");
                    JsonObject data = new JsonObject();
                    data.addProperty("payload", "x".repeat(context.byteBudget() + 1));
                    result.add("data", data);
                    result.addProperty("providerRevision", 1L);
                    yield CompletableFuture.completedFuture(result);
                }
                case INVALID_SCHEMA -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("schemaVersion", "wrong");
                    result.addProperty("providerRevision", 1L);
                    yield CompletableFuture.completedFuture(result);
                }
            };
        }
    }
}
