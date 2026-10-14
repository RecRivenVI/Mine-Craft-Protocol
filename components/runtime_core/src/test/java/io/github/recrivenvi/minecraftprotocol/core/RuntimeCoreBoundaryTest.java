package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeEchoReadProvider;
import io.github.recrivenvi.minecraftprotocol.core.providers.ProbeV2Providers;
import io.github.recrivenvi.minecraftprotocol.probe.api.MinecraftProtocolProvidersV2;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeCoreBoundaryTest {
    @Test
    void sharedCoreRunsWithoutMinecraftOrLoaderClasses() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("net.minecraft.client.Minecraft"));
        assertEquals(
                "io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2",
                io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2.class
                        .getName());
    }

    @Test
    void diagnosticProvidersUseAdapterIdentityWithoutASecondGeneratedConstant() {
        String namespace = "component_boundary_test";
        assertEquals(namespace + ":echo", new ProbeEchoReadProvider(namespace).id());
        ProbeV2Providers.registerAll(namespace);
        var providers = MinecraftProtocolProvidersV2.snapshot();
        assertTrue(
                providers.stream()
                        .anyMatch(p -> p.descriptor().providerId().equals(namespace + ":safe")));
        assertTrue(
                providers.stream()
                        .filter(p -> p.descriptor().providerId().startsWith(namespace + ":"))
                        .allMatch(
                                p ->
                                        p.descriptor()
                                                .snapshotSchema()
                                                .startsWith(namespace + "://")));
    }

    @Test
    void optionalSpikeRoutesRemainAbsentWithoutTheExplicitAdapter(@TempDir Path artifacts)
            throws Exception {
        verifySpikeRoutes(ProbeService.class, artifacts, false);
        verifySpikeRoutes(Phase9AExperimentalService.class, artifacts, true);
    }

    private static void verifySpikeRoutes(
            Class<? extends ProbeService> type, Path artifacts, boolean experimental)
            throws Exception {
        var calls = new AtomicInteger();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                type.getClassLoader(),
                                new Class<?>[] {type},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("artifactRoot")) return artifacts;
                                    if (method.getName().equals("phase9aObserve"))
                                        calls.incrementAndGet();
                                    return CompletableFuture.completedFuture(new JsonObject());
                                });
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String previous = System.getProperty("minecraft.protocol.scopes");
        System.setProperty("minecraft.protocol.scopes", "read");
        try (var transport =
                new ProbeTransport(service, "boundary-token", port, "boundary-target")) {
            transport.startAsync();
            var client = HttpClient.newHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + port + "/v0/diagnostics/phase9a/observe");
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(2))
                            .header("Authorization", "Bearer boundary-token")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build();
            HttpResponse<String> result = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (result == null && System.nanoTime() < deadline) {
                try {
                    result = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (java.io.IOException notStarted) {
                    Thread.sleep(20);
                }
            }
            assertNotNull(result, "Owned synthetic transport failed to start");
            assertEquals(experimental ? 200 : 404, result.statusCode());
            assertEquals(experimental ? 1 : 0, calls.get());
            if (!experimental) assertTrue(result.body().contains("NOT_FOUND"));
        } finally {
            if (previous == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", previous);
        }
    }
}
