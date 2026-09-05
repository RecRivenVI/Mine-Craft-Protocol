package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

final class RecordingEngine implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final int WRITER_QUEUE_CAPACITY = 64;
    private static final int MAX_SHEET_WIDTH = 8192;
    private static final int MAX_SHEET_HEIGHT = 8192;
    private static final long MAX_SHEET_PIXELS = 33_554_432L;
    private static final long MAX_DECODED_SOURCE_BYTES = 268_435_456L;
    private static final long MAX_ESTIMATED_RAW_BYTES = 134_217_728L;
    private static final long MAX_OUTPUT_BYTES = 268_435_456L;
    private static final long MAX_FRAME_BYTES = 33_554_432L;
    private static final long MAX_STATE_BYTES = 8_388_608L;
    private static final long MAX_RECORDING_BYTES = 536_870_912L;
    private static final long MAX_BUNDLE_SOURCE_BYTES = 805_306_368L;

    private final ProbeService service;
    private final ObservationEngine observation;
    private final Map<String, RecordingSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final ThreadPoolExecutor writer;
    private final ExecutorService finalizer;

    RecordingEngine(ProbeService service, ObservationEngine observation) {
        this.service = service;
        this.observation = observation;
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-recorder");
            thread.setDaemon(true);
            return thread;
        });
        this.writer = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(WRITER_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "minecraft-protocol-recording-writer");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.finalizer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-protocol-artifact-finalizer");
            thread.setDaemon(true);
            return thread;
        });
    }

    JsonObject start(JsonObject request) {
        Config config = Config.from(request);
        String id = UUID.randomUUID().toString();
        Path directory = this.service.artifactRoot().resolve(id);
        try {
            Files.createDirectories(directory.resolve("frames"));
            Files.createDirectories(directory.resolve("state"));
            Files.createDirectories(directory.resolve("timeline"));
            Files.createDirectories(directory.resolve("canonical"));
            Files.createDirectories(directory.resolve("derivatives"));
        } catch (IOException exception) {
            throw new ProtocolState.ProtocolException(
                    "ARTIFACT_CREATE_FAILED", 500, "Unable to create Artifact Bundle");
        }
        RecordingSession session;
        try {
            session = new RecordingSession(id, directory, config);
        } catch (IOException exception) {
            throw new ProtocolState.ProtocolException(
                    "ARTIFACT_CREATE_FAILED", 500, "Unable to initialize recording store");
        }
        this.sessions.put(id, session);
        session.installTimer(this.scheduler.scheduleAtFixedRate(
                () -> this.sample(session), 0L, config.intervalMillis(), TimeUnit.MILLISECONDS));
        this.recordEvent("recording.started", session.statusJson());
        return session.statusJson();
    }

    JsonObject list() {
        JsonArray recordings = new JsonArray();
        this.sessions.values().stream()
                .sorted(Comparator.comparingLong(session -> session.startedAtMillis))
                .forEach(session -> recordings.add(session.statusJson()));
        JsonObject json = new JsonObject();
        json.addProperty("type", "recording.list");
        json.add("recordings", recordings);
        return json;
    }

    JsonObject status(String id) {
        return this.require(id).statusJson();
    }

    JsonObject stop(String id, String reason) {
        RecordingSession session = this.require(id);
        this.finalizeSession(session, reason);
        return session.statusJson();
    }

    CompletableFuture<Path> artifact(String id) {
        RecordingSession session = this.require(id);
        if (!session.status.equals("completed") || !Files.isRegularFile(session.bundlePath())) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "ARTIFACT_NOT_READY", 409, "Recording Artifact is not finalized"));
        }
        return CompletableFuture.completedFuture(session.bundlePath());
    }

    void recordEvent(String category, JsonObject payload) {
        long timestamp = System.currentTimeMillis();
        for (RecordingSession session : this.sessions.values()) {
            if (!session.status.equals("recording")) continue;
            JsonObject event = new JsonObject();
            event.addProperty("category", category);
            event.addProperty("timestampMillis", timestamp);
            event.add("payload", payload.deepCopy());
            this.enqueue(session, () -> session.writeEvent(event), "event");
        }
    }

    void contaminate(String mode, String operation, JsonObject evidence) {
        for (RecordingSession session : this.sessions.values()) {
            if (!session.status.equals("recording")) continue;
            session.evidenceContaminated.set(true);
            JsonObject event = new JsonObject();
            event.addProperty("category", "evidence.contamination");
            event.addProperty("timestampMillis", System.currentTimeMillis());
            event.addProperty("mode", mode);
            event.addProperty("operation", operation);
            event.add("evidence", evidence.deepCopy());
            this.enqueue(session, () -> session.writeEvent(event), "contamination");
        }
    }

    @Override
    public void close() {
        for (RecordingSession session : this.sessions.values()) this.finalizeSession(session, "transport_close");
        this.scheduler.shutdownNow();
        for (RecordingSession session : this.sessions.values()) {
            try {
                session.finalization.get(15L, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException exception) {
                session.status = "failed";
                session.lifecycle = "CLOSE_TIMEOUT";
                session.writerErrors.incrementAndGet();
            } catch (Exception exception) {
                session.status = "failed";
                session.lifecycle = "FAILED";
                session.writerErrors.incrementAndGet();
            }
        }
        this.writer.shutdown();
        try {
            if (!this.writer.awaitTermination(5L, TimeUnit.SECONDS)) this.writer.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.writer.shutdownNow();
        }
        this.finalizer.shutdown();
        try {
            if (!this.finalizer.awaitTermination(5L, TimeUnit.SECONDS)) this.finalizer.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.finalizer.shutdownNow();
        }
    }

    private void sample(RecordingSession session) {
        if (!session.status.equals("recording") || session.captureStopping.get()) return;
        long elapsed = System.currentTimeMillis() - session.startedAtMillis;
        if (elapsed >= session.config.durationMillis()
                || session.sampleSequence.get() >= session.config.maxSamples()) {
            this.finalizeSession(session, "limit_reached");
            return;
        }
        if (session.inFlight.incrementAndGet() > 2) {
            session.inFlight.decrementAndGet();
            session.gapCount.incrementAndGet();
            return;
        }
        long sequence = session.sampleSequence.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        CompletableFuture<byte[]> capture = session.config.captureFrames()
                ? this.service.capturePng()
                : CompletableFuture.completedFuture(null);
        CompletableFuture<JsonObject> state = session.config.stateReads().isEmpty()
                ? CompletableFuture.completedFuture(null)
                : this.observation.stateFrame(stateRequest(session.config.stateReads()));
        CompletableFuture<JsonObject> tree = this.service.uiTree();
        CompletableFuture<JsonObject> input = this.service.inputState();
        CompletableFuture<Void> sampleWork = CompletableFuture.allOf(capture, state, tree, input);
        session.trackCaptureWork(capture);
        session.trackCaptureWork(state);
        session.trackCaptureWork(tree);
        session.trackCaptureWork(input);
        session.trackCaptureWork(sampleWork);
        sampleWork.whenComplete((ignored, error) -> {
            if (error != null) {
                session.inFlight.decrementAndGet();
                session.gapCount.incrementAndGet();
                if (session.captureStopping.get()) session.lastGapTrack = "sample_cancelled_on_finalize";
                return;
            }
            Sample sample = new Sample(
                    sequence, timestamp, capture.join(), state.join(), tree.join(), input.join());
            this.enqueue(session, () -> session.writeSample(sample), "sample");
            session.inFlight.decrementAndGet();
        });
    }

    private void enqueue(RecordingSession session, ThrowingRunnable task, String track) {
        try {
            this.writer.execute(() -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    session.writerErrors.incrementAndGet();
                    if (throwable instanceof ProtocolState.ProtocolException protocolException
                            && protocolException.code().equals("RECORDING_BUDGET_EXCEEDED")) {
                        session.gapCount.incrementAndGet();
                        session.lastGapTrack = track;
                        RecordingEngine.this.finalizeSession(session, "resource_budget");
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            session.gapCount.incrementAndGet();
            session.lastGapTrack = track;
        }
    }

    private void finalizeSession(RecordingSession session, String reason) {
        session.stopPendingCaptureWork();
        if (!session.finalizationStarted.compareAndSet(false, true)) return;
        session.status = "finalizing";
        session.lifecycle = "STOPPING_CAPTURE";
        session.stopReason = reason;
        session.completedAtMillis = System.currentTimeMillis();
        this.finalizer.execute(() -> {
            try {
                while (session.inFlight.get() > 0) Thread.sleep(10L);
                session.lifecycle = "DRAINING_ENCODERS";
                while (this.writer.getActiveCount() > 0 || !this.writer.getQueue().isEmpty()) Thread.sleep(10L);
                session.lifecycle = "FINALIZING";
                session.finalizeBundle();
                session.finalization.complete(null);
            } catch (Throwable exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                session.status = "failed";
                session.lifecycle = "FAILED";
                session.finalization.completeExceptionally(exception);
            }
        });
    }

    private RecordingSession require(String id) {
        RecordingSession session = this.sessions.get(id);
        if (session == null) {
            throw new ProtocolState.ProtocolException("RECORDING_NOT_FOUND", 404, "Unknown recording: " + id);
        }
        return session;
    }

    private static JsonObject stateRequest(JsonArray reads) {
        JsonObject request = new JsonObject();
        request.add("reads", reads.deepCopy());
        return request;
    }

    private final class RecordingSession {
        private final String id;
        private final Path directory;
        private final Config config;
        private final long startedAtMillis = System.currentTimeMillis();
        private final AtomicLong sampleSequence = new AtomicLong();
        private final AtomicLong writtenFrames = new AtomicLong();
        private final AtomicLong writtenStates = new AtomicLong();
        private final AtomicLong gapCount = new AtomicLong();
        private final AtomicLong writerErrors = new AtomicLong();
        private final AtomicLong writtenBytes = new AtomicLong();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicBoolean evidenceContaminated = new AtomicBoolean();
        private final AtomicBoolean captureStopping = new AtomicBoolean();
        private final AtomicBoolean finalizationStarted = new AtomicBoolean();
        private final Set<CompletableFuture<?>> pendingCaptureWork = ConcurrentHashMap.newKeySet();
        private final CompletableFuture<Void> finalization = new CompletableFuture<>();
        private final JsonArray frameIndex = new JsonArray();
        private final JsonArray contactSheets = new JsonArray();
        private final CanonicalStore canonical;
        private final BufferedWriter timeline;
        private volatile String status = "recording";
        private volatile String lifecycle = "RUNNING";
        private volatile String stopReason = "";
        private volatile String lastGapTrack = "";
        private volatile JsonObject finalizationFailure;
        private volatile long completedAtMillis;
        private volatile ScheduledFuture<?> timer;

        private RecordingSession(String id, Path directory, Config config) throws IOException {
            this.id = id;
            this.directory = directory;
            this.config = config;
            this.canonical = new ExperimentalBinaryStore(directory.resolve("canonical/store-v0.bin"));
            this.timeline = Files.newBufferedWriter(
                    directory.resolve("timeline/timeline.ndjson"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }

        private void installTimer(ScheduledFuture<?> scheduled) {
            this.timer = scheduled;
            if (this.captureStopping.get()) scheduled.cancel(false);
        }

        private <T> void trackCaptureWork(CompletableFuture<T> future) {
            this.pendingCaptureWork.add(future);
            future.whenComplete((ignored, error) -> this.pendingCaptureWork.remove(future));
            if (this.captureStopping.get()) future.cancel(false);
        }

        private void stopPendingCaptureWork() {
            this.captureStopping.set(true);
            ScheduledFuture<?> scheduled = this.timer;
            if (scheduled != null) scheduled.cancel(false);
            for (CompletableFuture<?> future : this.pendingCaptureWork) future.cancel(false);
        }

        private void writeSample(Sample sample) throws IOException {
            JsonObject index = new JsonObject();
            index.addProperty("sequence", sample.sequence());
            index.addProperty("timestampMillis", sample.timestampMillis());
            if (sample.tree() != null) {
                copy(sample.tree(), index, "clientTick", "screenRevision", "menuRevision", "screenClass");
            }
            if (sample.input() != null) index.add("input", sample.input().deepCopy());
            if (sample.frame() != null) {
                if (sample.frame().length > MAX_FRAME_BYTES) {
                    throw new ProtocolState.ProtocolException(
                            "RECORDING_BUDGET_EXCEEDED", 413, "Captured frame exceeds per-frame budget");
                }
                String name = String.format("%06d.png", sample.sequence());
                this.reserve(Math.multiplyExact((long) sample.frame().length, 2L));
                Files.write(this.directory.resolve("frames").resolve(name), sample.frame());
                index.addProperty("frame", "frames/" + name);
                this.writtenFrames.incrementAndGet();
                this.canonical.write(1, sample.sequence(), sample.timestampMillis(), sample.frame());
            }
            if (sample.state() != null) {
                String name = String.format("%06d.json", sample.sequence());
                byte[] bytes = GSON.toJson(sample.state()).getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_STATE_BYTES) {
                    throw new ProtocolState.ProtocolException(
                            "RECORDING_BUDGET_EXCEEDED", 413, "State sample exceeds per-sample budget");
                }
                this.reserve(Math.multiplyExact((long) bytes.length, 2L));
                Files.write(this.directory.resolve("state").resolve(name), bytes);
                index.addProperty("state", "state/" + name);
                this.writtenStates.incrementAndGet();
                this.canonical.write(2, sample.sequence(), sample.timestampMillis(), bytes);
            }
            this.frameIndex.add(index);
        }

        private void writeEvent(JsonObject event) throws IOException {
            String encoded = COMPACT_GSON.toJson(event);
            byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
            this.reserve(Math.addExact(Math.multiplyExact((long) bytes.length, 2L), 64L));
            this.timeline.write(encoded);
            this.timeline.newLine();
            this.timeline.flush();
            this.canonical.write(
                    3,
                    this.sampleSequence.get(),
                    event.get("timestampMillis").getAsLong(),
                    bytes);
        }

        private void finalizeBundle() {
            String stage = "source_tracks";
            boolean sourceTracksClosed = false;
            try {
                this.timeline.flush();
                this.timeline.close();
                this.canonical.close();
                sourceTracksClosed = true;
                stage = "frame_index";
                this.lifecycle = "WRITING_MANIFEST";
                Files.writeString(
                        this.directory.resolve("frame-index.json"), GSON.toJson(this.frameIndex), StandardCharsets.UTF_8);
                stage = "contact_sheet";
                if (this.config.contactSheet() && this.writtenFrames.get() > 0) this.composeContactSheet();
                stage = "manifest_checksums";
                Files.writeString(this.directory.resolve("manifest.json"), GSON.toJson(this.manifest()), StandardCharsets.UTF_8);
                Files.writeString(this.directory.resolve("checksums.json"), GSON.toJson(this.checksums()), StandardCharsets.UTF_8);
                stage = "bundle";
                this.zipBundle();
                this.status = "completed";
                this.lifecycle = "CLOSED";
            } catch (Throwable throwable) {
                this.status = "failed";
                this.lifecycle = "FAILED";
                this.writerErrors.incrementAndGet();
                JsonObject failure = new JsonObject();
                failure.addProperty("code", throwable instanceof ProtocolState.ProtocolException protocol
                        ? protocol.code() : "RECORDING_FINALIZATION_FAILED");
                failure.addProperty("stage", stage);
                failure.addProperty("cause", throwable.getClass().getSimpleName());
                failure.addProperty("message", throwable.getMessage() == null ? "Finalization failed"
                        : throwable.getMessage().substring(0, Math.min(1024, throwable.getMessage().length())));
                failure.addProperty("sourceTracksClosed", sourceTracksClosed);
                failure.addProperty("sourceFilesRetained", true);
                failure.addProperty("sourceIntegrity", "not_revalidated");
                failure.addProperty("artifactReady", false);
                this.finalizationFailure = failure;
                try {
                    Files.writeString(this.directory.resolve("finalization-error.json"),
                            GSON.toJson(failure), StandardCharsets.UTF_8);
                } catch (IOException diagnosticFailure) {
                    throwable.addSuppressed(diagnosticFailure);
                }
                if (throwable instanceof ProtocolState.ProtocolException protocol) throw protocol;
                throw new IllegalStateException("Unable to finalize recording at " + stage, throwable);
            }
        }

        private JsonObject manifest() {
            JsonObject json = new JsonObject();
            json.addProperty("artifactVersion", "mcp-artifact-v0");
            json.addProperty("schemaVersion", "0.0.1-phase5");
            json.addProperty("recordingId", this.id);
            json.addProperty("status", this.status.equals("failed") ? "failed" : "completed");
            json.addProperty("startedAtMillis", this.startedAtMillis);
            json.addProperty("completedAtMillis", this.completedAtMillis == 0L
                    ? System.currentTimeMillis() : this.completedAtMillis);
            json.addProperty("stopReason", this.stopReason);
            json.addProperty("writtenFrames", this.writtenFrames.get());
            json.addProperty("writtenStates", this.writtenStates.get());
            json.addProperty("gaps", this.gapCount.get());
            json.addProperty("writerErrors", this.writerErrors.get());
            json.addProperty("writtenBytes", this.writtenBytes.get());
            json.addProperty("lastGapTrack", this.lastGapTrack);
            json.addProperty("backpressurePolicy", "drop_sample_and_record_gap");
            json.addProperty("evidenceContaminated", this.evidenceContaminated.get());
            JsonObject store = new JsonObject();
            store.addProperty("format", "experimental_length_prefixed_binary_v0");
            store.addProperty("frozen", false);
            store.addProperty("path", "canonical/store-v0.bin");
            store.addProperty("readableExport", "timeline/timeline.ndjson");
            json.add("canonicalStore", store);
            JsonObject contactSheet = new JsonObject();
            contactSheet.addProperty("sheetCount", this.contactSheets.size());
            contactSheet.add("sheets", this.contactSheets.deepCopy());
            contactSheet.addProperty("maxSheetWidth", MAX_SHEET_WIDTH);
            contactSheet.addProperty("maxSheetHeight", MAX_SHEET_HEIGHT);
            contactSheet.addProperty("maxSheetPixels", MAX_SHEET_PIXELS);
            contactSheet.addProperty("maxDecodedSourceBytes", MAX_DECODED_SOURCE_BYTES);
            contactSheet.addProperty("maxEstimatedRawBytes", MAX_ESTIMATED_RAW_BYTES);
            contactSheet.addProperty("maxOutputBytes", MAX_OUTPUT_BYTES);
            contactSheet.addProperty("maxFrameBytes", MAX_FRAME_BYTES);
            contactSheet.addProperty("maxStateBytes", MAX_STATE_BYTES);
            contactSheet.addProperty("maxRecordingBytes", MAX_RECORDING_BYTES);
            contactSheet.addProperty("maxBundleSourceBytes", MAX_BUNDLE_SOURCE_BYTES);
            json.add("contactSheetArtifacts", contactSheet);
            json.add("config", this.config.toJson());
            return json;
        }

        private JsonObject statusJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "recording.session");
            json.addProperty("recordingId", this.id);
            json.addProperty("status", this.status);
            json.addProperty("lifecycle", this.lifecycle);
            json.addProperty("startedAtMillis", this.startedAtMillis);
            json.addProperty("completedAtMillis", this.completedAtMillis);
            json.addProperty("samples", this.sampleSequence.get());
            json.addProperty("writtenFrames", this.writtenFrames.get());
            json.addProperty("writtenStates", this.writtenStates.get());
            json.addProperty("gaps", this.gapCount.get());
            json.addProperty("writtenBytes", this.writtenBytes.get());
            json.addProperty("writerQueueDepth", writer.getQueue().size());
            json.addProperty("writerQueueCapacity", WRITER_QUEUE_CAPACITY);
            json.addProperty("evidenceContaminated", this.evidenceContaminated.get());
            json.addProperty("artifactReady", this.status.equals("completed") && Files.isRegularFile(this.bundlePath()));
            JsonObject failure = this.finalizationFailure;
            if (failure != null) json.add("finalizationFailure", failure.deepCopy());
            return json;
        }

        private void composeContactSheet() throws IOException {
            List<Path> frames;
            try (var stream = Files.list(this.directory.resolve("frames"))) {
                frames = stream.filter(path -> path.getFileName().toString().endsWith(".png"))
                        .sorted().toList();
            }
            int requestedColumns = Math.min(this.config.columns(), Math.max(1, frames.size()));
            int columns = Math.min(requestedColumns, maxCells(MAX_SHEET_WIDTH, this.config.cellWidth(), this.config.spacing()));
            int width = sheetDimension(columns, this.config.cellWidth(), this.config.spacing());
            int rowsByHeight = maxCells(MAX_SHEET_HEIGHT, this.config.cellHeight(), this.config.spacing());
            int rowsByPixels = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                    MAX_SHEET_PIXELS / Math.max(1L, (long) width * this.config.cellHeight())));
            int rowsPerSheet = Math.max(1, Math.min(rowsByHeight, rowsByPixels));
            int framesPerSheet = Math.multiplyExact(columns, rowsPerSheet);
            long outputBytes = 0L;
            for (int start = 0, sheetIndex = 0; start < frames.size(); start += framesPerSheet, sheetIndex++) {
                int end = Math.min(frames.size(), start + framesPerSheet);
                int rows = (end - start + columns - 1) / columns;
                int height = sheetDimension(rows, this.config.cellHeight(), this.config.spacing());
                checkedSheetBudget(width, height);
                BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = sheet.createGraphics();
                long decodedBytes = 0L;
                try {
                    graphics.setColor(Color.BLACK);
                    graphics.fillRect(0, 0, width, height);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    for (int index = start; index < end; index++) {
                        Path frame = frames.get(index);
                        ImageDimensions dimensions = imageDimensions(frame);
                        long sourceBytes = Math.multiplyExact(Math.multiplyExact(
                                (long) dimensions.width(), dimensions.height()), 4L);
                        decodedBytes = Math.addExact(decodedBytes, sourceBytes);
                        if (decodedBytes > MAX_DECODED_SOURCE_BYTES) {
                            throw new ProtocolState.ProtocolException(
                                    "RECORDING_BUDGET_EXCEEDED", 413, "Contact sheet decoded_source_bytes=" + decodedBytes
                                            + " exceeds limit=" + MAX_DECODED_SOURCE_BYTES
                                            + "; captured source files retained; bundle not finalized");
                        }
                        BufferedImage source = ImageIO.read(frame.toFile());
                        int localIndex = index - start;
                        int cellX = (localIndex % columns) * (this.config.cellWidth() + this.config.spacing());
                        int cellY = (localIndex / columns) * (this.config.cellHeight() + this.config.spacing());
                        double scale = Math.min(
                                (double) this.config.cellWidth() / source.getWidth(),
                                (double) this.config.cellHeight() / source.getHeight());
                        int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
                        int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
                        int drawX = cellX + (this.config.cellWidth() - drawWidth) / 2;
                        int drawY = cellY + (this.config.cellHeight() - drawHeight) / 2;
                        graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
                        source.flush();
                    }
                } finally {
                    graphics.dispose();
                }
                String name = sheetIndex == 0 ? "contact-sheet.png"
                        : String.format("contact-sheet-%04d.png", sheetIndex + 1);
                Path output = this.directory.resolve("derivatives").resolve(name);
                ImageIO.write(sheet, "PNG", output.toFile());
                sheet.flush();
                outputBytes = Math.addExact(outputBytes, Files.size(output));
                if (outputBytes > MAX_OUTPUT_BYTES) {
                    throw new ProtocolState.ProtocolException(
                            "RECORDING_BUDGET_EXCEEDED", 413, "Contact-sheet output exceeds budget");
                }
                JsonObject item = new JsonObject();
                item.addProperty("path", "derivatives/" + name);
                item.addProperty("frameStart", start + 1);
                item.addProperty("frameEnd", end);
                item.addProperty("columns", columns);
                item.addProperty("rows", rows);
                item.addProperty("width", width);
                item.addProperty("height", height);
                this.contactSheets.add(item);
            }
        }

        private JsonObject checksums() throws IOException {
            JsonObject checksums = new JsonObject();
            try (var stream = Files.walk(this.directory)) {
                for (Path path : stream.filter(Files::isRegularFile)
                        .filter(path -> !path.equals(this.bundlePath()))
                        .sorted().toList()) {
                    checksums.addProperty(
                            this.directory.relativize(path).toString().replace('\\', '/'),
                            sha256(path));
                }
            }
            return checksums;
        }

        private void zipBundle() throws IOException {
            long sourceBytes = 0L;
            try (var sizing = Files.walk(this.directory)) {
                for (Path path : sizing.filter(Files::isRegularFile)
                        .filter(path -> !path.equals(this.bundlePath())).toList()) {
                    try {
                        sourceBytes = Math.addExact(sourceBytes, Files.size(path));
                    } catch (ArithmeticException exception) {
                        throw new ProtocolState.ProtocolException(
                                "RECORDING_BUDGET_EXCEEDED", 413, "Artifact source size overflow");
                    }
                    if (sourceBytes > MAX_BUNDLE_SOURCE_BYTES) {
                        throw new ProtocolState.ProtocolException(
                                "RECORDING_BUDGET_EXCEEDED", 413, "Artifact Bundle source exceeds budget");
                    }
                }
            }
            try (OutputStream output = Files.newOutputStream(
                    this.bundlePath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
                 var stream = Files.walk(this.directory)) {
                for (Path path : stream.filter(Files::isRegularFile)
                        .filter(path -> !path.equals(this.bundlePath()))
                        .sorted().toList()) {
                    String name = this.directory.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }

        private Path bundlePath() {
            return this.directory.resolve("bundle.zip");
        }

        private void reserve(long bytes) {
            long total;
            try {
                total = Math.addExact(this.writtenBytes.get(), bytes);
            } catch (ArithmeticException exception) {
                throw new ProtocolState.ProtocolException(
                        "RECORDING_BUDGET_EXCEEDED", 413, "Recording byte count overflow");
            }
            if (total > MAX_RECORDING_BYTES) {
                throw new ProtocolState.ProtocolException(
                        "RECORDING_BUDGET_EXCEEDED", 413, "Recording Session byte budget exceeded");
            }
            this.writtenBytes.set(total);
        }
    }

    private static int maxCells(int maximumDimension, int cellDimension, int spacing) {
        return Math.max(1, (maximumDimension + spacing) / (cellDimension + spacing));
    }

    private static int sheetDimension(int cells, int cellDimension, int spacing) {
        try {
            return Math.toIntExact(Math.addExact(
                    Math.multiplyExact((long) cells, cellDimension),
                    Math.multiplyExact((long) Math.max(0, cells - 1), spacing)));
        } catch (ArithmeticException exception) {
            throw new ProtocolState.ProtocolException(
                    "RECORDING_BUDGET_EXCEEDED", 413, "Contact-sheet dimensions overflow");
        }
    }

    private static void checkedSheetBudget(int width, int height) {
        try {
            long pixels = Math.multiplyExact((long) width, height);
            long rawBytes = Math.multiplyExact(pixels, 4L);
            if (width > MAX_SHEET_WIDTH || height > MAX_SHEET_HEIGHT
                    || pixels > MAX_SHEET_PIXELS || rawBytes > MAX_ESTIMATED_RAW_BYTES) {
                throw new ProtocolState.ProtocolException(
                        "RECORDING_BUDGET_EXCEEDED", 413, "Contact-sheet aggregate budget exceeded");
            }
        } catch (ArithmeticException exception) {
            throw new ProtocolState.ProtocolException(
                    "RECORDING_BUDGET_EXCEEDED", 413, "Contact-sheet allocation estimate overflow");
        }
    }

    private static ImageDimensions imageDimensions(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Unable to inspect image dimensions");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported frame image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private record ImageDimensions(int width, int height) {
    }

    private interface CanonicalStore extends AutoCloseable {
        void write(int recordType, long sequence, long timestampMillis, byte[] payload) throws IOException;

        @Override
        void close() throws IOException;
    }

    private static final class ExperimentalBinaryStore implements CanonicalStore {
        private final DataOutputStream output;

        private ExperimentalBinaryStore(Path path) throws IOException {
            this.output = new DataOutputStream(Files.newOutputStream(
                    path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
            this.output.writeInt(0x4D435052);
            this.output.writeInt(0);
        }

        @Override
        public void write(int recordType, long sequence, long timestampMillis, byte[] payload) throws IOException {
            this.output.writeByte(recordType);
            this.output.writeLong(sequence);
            this.output.writeLong(timestampMillis);
            this.output.writeInt(payload.length);
            this.output.write(payload);
        }

        @Override
        public void close() throws IOException {
            this.output.flush();
            this.output.close();
        }
    }

    private record Sample(
            long sequence,
            long timestampMillis,
            byte[] frame,
            JsonObject state,
            JsonObject tree,
            JsonObject input) {
    }

    private record Config(
            long intervalMillis,
            long durationMillis,
            long maxSamples,
            boolean captureFrames,
            JsonArray stateReads,
            boolean contactSheet,
            int columns,
            int cellWidth,
            int cellHeight,
            int spacing) {
        private static Config from(JsonObject request) {
            long interval = bounded(longValue(request, "intervalMs", 250L), 50L, 60_000L);
            long duration = bounded(longValue(request, "durationMs", 5_000L), 100L, 300_000L);
            long maxSamples = bounded(longValue(request, "maxSamples", 20L), 1L, 512L);
            boolean capture = bool(request, "captureFrames", true);
            JsonArray reads = request.has("stateReads") && request.get("stateReads").isJsonArray()
                    ? request.getAsJsonArray("stateReads").deepCopy() : defaultReads();
            if (reads.size() > 32) {
                throw new ProtocolState.ProtocolException(
                        "INVALID_RECORDING", 400, "stateReads supports at most 32 entries");
            }
            JsonObject contact = request.has("contactSheet") && request.get("contactSheet").isJsonObject()
                    ? request.getAsJsonObject("contactSheet") : new JsonObject();
            return new Config(
                    interval, duration, maxSamples, capture, reads,
                    bool(contact, "enabled", true),
                    (int) bounded(longValue(contact, "columns", 4L), 1L, 16L),
                    (int) bounded(longValue(contact, "cellWidth", 256L), 16L, 1024L),
                    (int) bounded(longValue(contact, "cellHeight", 144L), 16L, 1024L),
                    (int) bounded(longValue(contact, "spacing", 4L), 0L, 32L));
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("intervalMs", this.intervalMillis);
            json.addProperty("durationMs", this.durationMillis);
            json.addProperty("maxSamples", this.maxSamples);
            json.addProperty("captureFrames", this.captureFrames);
            json.add("stateReads", this.stateReads.deepCopy());
            JsonObject contact = new JsonObject();
            contact.addProperty("enabled", this.contactSheet);
            contact.addProperty("columns", this.columns);
            contact.addProperty("cellWidth", this.cellWidth);
            contact.addProperty("cellHeight", this.cellHeight);
            contact.addProperty("spacing", this.spacing);
            json.add("contactSheet", contact);
            return json;
        }

        private static JsonArray defaultReads() {
            JsonArray reads = new JsonArray();
            JsonObject player = new JsonObject();
            player.addProperty("providerId", "minecraft:client/player");
            reads.add(player);
            JsonObject capture = new JsonObject();
            capture.addProperty("providerId", "minecraft:capture/info");
            reads.add(capture);
            return reads;
        }
    }

    private static long longValue(JsonObject object, String name, long fallback) {
        return object.has(name) ? object.get(name).getAsLong() : fallback;
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }

    private static long bounded(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static void copy(JsonObject source, JsonObject target, String... fields) {
        for (String field : fields) if (source.has(field)) target.add(field, source.get(field).deepCopy());
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest algorithm = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), algorithm)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            byte[] digest = algorithm.digest();
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
