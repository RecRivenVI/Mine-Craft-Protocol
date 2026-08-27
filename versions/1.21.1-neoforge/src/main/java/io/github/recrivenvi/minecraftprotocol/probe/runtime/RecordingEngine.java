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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

final class RecordingEngine implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final int WRITER_QUEUE_CAPACITY = 64;

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
        session.timer = this.scheduler.scheduleAtFixedRate(
                () -> this.sample(session), 0L, config.intervalMillis(), TimeUnit.MILLISECONDS);
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

    CompletableFuture<byte[]> artifact(String id) {
        RecordingSession session = this.require(id);
        if (!session.status.equals("completed") || !Files.isRegularFile(session.bundlePath())) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "ARTIFACT_NOT_READY", 409, "Recording Artifact is not finalized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readAllBytes(session.bundlePath());
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read Artifact Bundle", exception);
            }
        }, this.finalizer);
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
        this.writer.shutdown();
        this.finalizer.shutdown();
    }

    private void sample(RecordingSession session) {
        if (!session.status.equals("recording")) return;
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
        CompletableFuture.allOf(capture, state, tree, input).whenComplete((ignored, error) -> {
            if (error != null) {
                session.inFlight.decrementAndGet();
                session.gapCount.incrementAndGet();
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
                }
            });
        } catch (RejectedExecutionException exception) {
            session.gapCount.incrementAndGet();
            session.lastGapTrack = track;
        }
    }

    private void finalizeSession(RecordingSession session, String reason) {
        if (!session.finalizationStarted.compareAndSet(false, true)) return;
        session.status = "finalizing";
        session.stopReason = reason;
        session.completedAtMillis = System.currentTimeMillis();
        if (session.timer != null) session.timer.cancel(false);
        this.finalizer.execute(() -> {
            try {
                while (session.inFlight.get() > 0) Thread.sleep(10L);
                this.writer.getQueue().put(() -> session.finalizeBundle());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                session.status = "failed";
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
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicBoolean evidenceContaminated = new AtomicBoolean();
        private final AtomicBoolean finalizationStarted = new AtomicBoolean();
        private final JsonArray frameIndex = new JsonArray();
        private final CanonicalStore canonical;
        private final BufferedWriter timeline;
        private volatile String status = "recording";
        private volatile String stopReason = "";
        private volatile String lastGapTrack = "";
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

        private void writeSample(Sample sample) throws IOException {
            JsonObject index = new JsonObject();
            index.addProperty("sequence", sample.sequence());
            index.addProperty("timestampMillis", sample.timestampMillis());
            if (sample.tree() != null) {
                copy(sample.tree(), index, "clientTick", "screenRevision", "menuRevision", "screenClass");
            }
            if (sample.input() != null) index.add("input", sample.input().deepCopy());
            if (sample.frame() != null) {
                String name = String.format("%06d.png", sample.sequence());
                Files.write(this.directory.resolve("frames").resolve(name), sample.frame());
                index.addProperty("frame", "frames/" + name);
                this.writtenFrames.incrementAndGet();
                this.canonical.write(1, sample.sequence(), sample.timestampMillis(), sample.frame());
            }
            if (sample.state() != null) {
                String name = String.format("%06d.json", sample.sequence());
                byte[] bytes = GSON.toJson(sample.state()).getBytes(StandardCharsets.UTF_8);
                Files.write(this.directory.resolve("state").resolve(name), bytes);
                index.addProperty("state", "state/" + name);
                this.writtenStates.incrementAndGet();
                this.canonical.write(2, sample.sequence(), sample.timestampMillis(), bytes);
            }
            this.frameIndex.add(index);
        }

        private void writeEvent(JsonObject event) throws IOException {
            this.timeline.write(COMPACT_GSON.toJson(event));
            this.timeline.newLine();
            this.timeline.flush();
            this.canonical.write(
                    3,
                    this.sampleSequence.get(),
                    event.get("timestampMillis").getAsLong(),
                    COMPACT_GSON.toJson(event).getBytes(StandardCharsets.UTF_8));
        }

        private void finalizeBundle() {
            try {
                this.timeline.flush();
                this.timeline.close();
                this.canonical.close();
                Files.writeString(
                        this.directory.resolve("frame-index.json"), GSON.toJson(this.frameIndex), StandardCharsets.UTF_8);
                if (this.config.contactSheet() && this.writtenFrames.get() > 0) this.composeContactSheet();
                Files.writeString(this.directory.resolve("manifest.json"), GSON.toJson(this.manifest()), StandardCharsets.UTF_8);
                Files.writeString(this.directory.resolve("checksums.json"), GSON.toJson(this.checksums()), StandardCharsets.UTF_8);
                this.zipBundle();
                this.status = "completed";
            } catch (Throwable throwable) {
                this.status = "failed";
                this.writerErrors.incrementAndGet();
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
            json.addProperty("lastGapTrack", this.lastGapTrack);
            json.addProperty("backpressurePolicy", "drop_sample_and_record_gap");
            json.addProperty("evidenceContaminated", this.evidenceContaminated.get());
            JsonObject store = new JsonObject();
            store.addProperty("format", "experimental_length_prefixed_binary_v0");
            store.addProperty("frozen", false);
            store.addProperty("path", "canonical/store-v0.bin");
            store.addProperty("readableExport", "timeline/timeline.ndjson");
            json.add("canonicalStore", store);
            json.add("config", this.config.toJson());
            return json;
        }

        private JsonObject statusJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "recording.session");
            json.addProperty("recordingId", this.id);
            json.addProperty("status", this.status);
            json.addProperty("startedAtMillis", this.startedAtMillis);
            json.addProperty("completedAtMillis", this.completedAtMillis);
            json.addProperty("samples", this.sampleSequence.get());
            json.addProperty("writtenFrames", this.writtenFrames.get());
            json.addProperty("writtenStates", this.writtenStates.get());
            json.addProperty("gaps", this.gapCount.get());
            json.addProperty("writerQueueDepth", writer.getQueue().size());
            json.addProperty("writerQueueCapacity", WRITER_QUEUE_CAPACITY);
            json.addProperty("evidenceContaminated", this.evidenceContaminated.get());
            json.addProperty("artifactReady", this.status.equals("completed") && Files.isRegularFile(this.bundlePath()));
            return json;
        }

        private void composeContactSheet() throws IOException {
            List<Path> frames;
            try (var stream = Files.list(this.directory.resolve("frames"))) {
                frames = stream.filter(path -> path.getFileName().toString().endsWith(".png"))
                        .sorted().toList();
            }
            int columns = Math.min(this.config.columns(), Math.max(1, frames.size()));
            int rows = (frames.size() + columns - 1) / columns;
            int width = columns * this.config.cellWidth() + Math.max(0, columns - 1) * this.config.spacing();
            int height = rows * this.config.cellHeight() + Math.max(0, rows - 1) * this.config.spacing();
            BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = sheet.createGraphics();
            try {
                graphics.setColor(Color.BLACK);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                for (int index = 0; index < frames.size(); index++) {
                    BufferedImage source = ImageIO.read(frames.get(index).toFile());
                    int cellX = (index % columns) * (this.config.cellWidth() + this.config.spacing());
                    int cellY = (index / columns) * (this.config.cellHeight() + this.config.spacing());
                    double scale = Math.min(
                            (double) this.config.cellWidth() / source.getWidth(),
                            (double) this.config.cellHeight() / source.getHeight());
                    int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
                    int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
                    int drawX = cellX + (this.config.cellWidth() - drawWidth) / 2;
                    int drawY = cellY + (this.config.cellHeight() - drawHeight) / 2;
                    graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
                }
            } finally {
                graphics.dispose();
            }
            ImageIO.write(sheet, "PNG", this.directory.resolve("derivatives/contact-sheet.png").toFile());
        }

        private JsonObject checksums() throws IOException {
            JsonObject checksums = new JsonObject();
            try (var stream = Files.walk(this.directory)) {
                for (Path path : stream.filter(Files::isRegularFile)
                        .filter(path -> !path.equals(this.bundlePath()))
                        .sorted().toList()) {
                    checksums.addProperty(
                            this.directory.relativize(path).toString().replace('\\', '/'),
                            sha256(Files.readAllBytes(path)));
                }
            }
            return checksums;
        }

        private void zipBundle() throws IOException {
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

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
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

