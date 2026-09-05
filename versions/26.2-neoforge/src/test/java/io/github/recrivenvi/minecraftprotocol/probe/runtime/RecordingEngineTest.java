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
    void failedContactSheetRetainsTypedFailureAndDoesNotClaimReadyBundle(@TempDir Path artifactRoot) throws Exception {
        assertFinalizationFailure(artifactRoot, new byte[] {1, 2, 3, 4});
    }

    @Test
    void contactSheetBudgetReportsDecodedLimitBeforeLargeAllocation(@TempDir Path artifactRoot) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB), "PNG", output);
        byte[] header = output.toByteArray();
        var bytes = java.nio.ByteBuffer.wrap(header);
        bytes.putInt(16, 8193);
        bytes.putInt(20, 8192);
        var crc = new java.util.zip.CRC32();
        crc.update(header, 12, 17);
        bytes.putInt(29, (int) crc.getValue());
        JsonObject failure = assertFinalizationFailure(artifactRoot, header);
        assertEquals("RECORDING_BUDGET_EXCEEDED", failure.get("code").getAsString());
        assertTrue(failure.get("message").getAsString().contains("decoded_source_bytes="));
        assertTrue(failure.get("message").getAsString().contains("limit=268435456"));
    }

    private static JsonObject assertFinalizationFailure(Path artifactRoot, byte[] frame) throws Exception {
        ProbeService service = (ProbeService) Proxy.newProxyInstance(
                ProbeService.class.getClassLoader(), new Class<?>[] { ProbeService.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("artifactRoot")) return artifactRoot;
                    if (method.getName().equals("capturePng")) return CompletableFuture.completedFuture(frame);
                    if (method.getReturnType().equals(CompletableFuture.class)) return CompletableFuture.completedFuture(new JsonObject());
                    throw new UnsupportedOperationException(method.getName());
                });
        try (RecordingEngine engine = new RecordingEngine(service, new ObservationEngine(service))) {
            JsonObject request = new JsonObject();
            request.addProperty("intervalMs", 50);
            request.addProperty("durationMs", 300_000);
            request.addProperty("maxSamples", 1);
            request.addProperty("captureFrames", true);
            request.add("stateReads", new JsonArray());
            String id = engine.start(request).get("recordingId").getAsString();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            JsonObject status;
            do {
                status = engine.status(id);
                if (status.has("finalizationFailure")) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertEquals("failed", status.get("status").getAsString());
            assertTrue(!status.get("artifactReady").getAsBoolean());
            JsonObject failure = status.getAsJsonObject("finalizationFailure");
            assertEquals("contact_sheet", failure.get("stage").getAsString());
            assertTrue(failure.get("sourceTracksClosed").getAsBoolean());
            assertTrue(failure.get("sourceFilesRetained").getAsBoolean());
            assertEquals("not_revalidated", failure.get("sourceIntegrity").getAsString());
            assertTrue(Files.isRegularFile(artifactRoot.resolve(id).resolve("frame-index.json")));
        }
        try (var directories = Files.list(artifactRoot)) {
            Path directory = directories.findFirst().orElseThrow();
            assertTrue(Files.isRegularFile(directory.resolve("finalization-error.json")));
            assertTrue(!Files.exists(directory.resolve("bundle.zip")));
            return JsonParser.parseString(Files.readString(directory.resolve("finalization-error.json"))).getAsJsonObject();
        }
    }

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
