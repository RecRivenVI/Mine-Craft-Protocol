package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecordingEngineTest {
    @Test
    void closeCancelsStalledCaptureAndFinalizesBundle(@TempDir Path artifactRoot) throws Exception {
        CountDownLatch captureStarted = new CountDownLatch(1);
        AtomicReference<CompletableFuture<byte[]>> stalledCapture = new AtomicReference<>();
        ProbeService service = (ProbeService) Proxy.newProxyInstance(
                ProbeService.class.getClassLoader(),
                new Class<?>[] { ProbeService.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("artifactRoot")) return artifactRoot;
                    if (method.getName().equals("capturePng")) {
                        CompletableFuture<byte[]> future = new CompletableFuture<>();
                        stalledCapture.set(future);
                        captureStarted.countDown();
                        return future;
                    }
                    if (method.getReturnType().equals(CompletableFuture.class)) {
                        return CompletableFuture.completedFuture(new JsonObject());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        RecordingEngine engine = new RecordingEngine(service, new ObservationEngine(service));
        JsonObject request = new JsonObject();
        request.addProperty("intervalMs", 50L);
        request.addProperty("durationMs", 300_000L);
        request.addProperty("maxSamples", 512L);
        request.addProperty("captureFrames", true);
        request.add("stateReads", new JsonArray());
        JsonObject contactSheet = new JsonObject();
        contactSheet.addProperty("enabled", false);
        request.add("contactSheet", contactSheet);

        String recordingId = engine.start(request).get("recordingId").getAsString();
        assertTrue(captureStarted.await(2L, TimeUnit.SECONDS), "capture must be in flight before close");
        assertTimeout(Duration.ofSeconds(5L), engine::close);

        assertTrue(stalledCapture.get().isCancelled(), "shutdown must cancel capture work that cannot finish");
        Path recording = artifactRoot.resolve(recordingId);
        assertTrue(Files.isRegularFile(recording.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(recording.resolve("bundle.zip")));
        JsonObject manifest = JsonParser.parseString(Files.readString(recording.resolve("manifest.json")))
                .getAsJsonObject();
        assertEquals("completed", manifest.get("status").getAsString());
        assertEquals("transport_close", manifest.get("stopReason").getAsString());
        JsonObject status = engine.status(recordingId);
        assertEquals("completed", status.get("status").getAsString());
        assertEquals("CLOSED", status.get("lifecycle").getAsString());
    }
}
