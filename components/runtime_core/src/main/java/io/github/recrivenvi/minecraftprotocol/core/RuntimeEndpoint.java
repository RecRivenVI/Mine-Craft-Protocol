package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

/** Owns one game directory's connection record, not its world or input authority. */
public final class RuntimeEndpoint implements AutoCloseable {
    public static final String INSTANCE_HEADER = "X-MCP-Runtime-Instance";
    public static final int DEFAULT_PORT = 0;
    private static final int AUTOMATIC_BIND_ATTEMPTS = 8;
    // HTTP clients enforce https://fetch.spec.whatwg.org/#port-blocking before connecting.
    private static final Set<Integer> FETCH_BLOCKED_PORTS =
            Set.of(
                    0, 1, 7, 9, 11, 13, 15, 17, 19, 20, 21, 22, 23, 25, 37, 42, 43, 53, 69, 77, 79,
                    87, 95, 101, 102, 103, 104, 109, 110, 111, 113, 115, 117, 119, 123, 135, 137,
                    139, 143, 161, 179, 389, 427, 465, 512, 513, 514, 515, 526, 530, 531, 532, 540,
                    548, 554, 556, 563, 587, 601, 636, 989, 990, 993, 995, 1719, 1720, 1723, 2049,
                    3659, 4045, 4190, 5060, 5061, 6000, 6566, 6665, 6666, 6667, 6668, 6669, 6679,
                    6697, 10080);
    private final Path directory;
    private final Path endpoint;
    private final FileChannel channel;
    private final FileLock lock;
    private final JsonObject identity = new JsonObject();
    private boolean closed;

    private RuntimeEndpoint(Path gameDirectory, String target, String role) throws IOException {
        Path game = gameDirectory.toRealPath();
        this.directory = game.resolve("minecraft-protocol");
        Files.createDirectories(this.directory);
        this.endpoint = this.directory.resolve("endpoint.json");
        this.channel =
                FileChannel.open(
                        this.directory.resolve("instance.lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = this.channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            this.channel.close();
            throw new IOException("Runtime instance already owned: " + game, exception);
        } catch (IOException exception) {
            this.channel.close();
            throw exception;
        }
        if (acquired == null) {
            this.channel.close();
            throw new IOException("Runtime instance already owned: " + game);
        }
        this.lock = acquired;
        // Remove crash residue only after exclusive ownership; never reuse its port or token.
        try {
            Files.deleteIfExists(this.endpoint);
        } catch (IOException exception) {
            this.lock.release();
            this.channel.close();
            throw exception;
        }
        this.identity.addProperty("schemaVersion", 1);
        this.identity.addProperty("runtimeInstanceId", UUID.randomUUID().toString());
        this.identity.addProperty("target", target);
        this.identity.addProperty("deploymentRole", role);
        this.identity.addProperty("gameDirectory", game.toString());
        this.identity.addProperty("pid", ProcessHandle.current().pid());
        this.identity.addProperty(
                "processStartedAt",
                ProcessHandle.current().info().startInstant().orElseThrow().toString());
        this.identity.addProperty("startedAt", Instant.now().toString());
        this.identity.addProperty("tokenFile", "token");
    }

    public static RuntimeEndpoint acquire(Path gameDirectory, String target) {
        return acquire(gameDirectory, target, "CLIENT");
    }

    public static RuntimeEndpoint acquire(Path gameDirectory, String target, String role) {
        if (!Set.of("CLIENT", "DEDICATED_SERVER").contains(role))
            throw new IllegalArgumentException("Invalid Runtime deployment role");
        try {
            return new RuntimeEndpoint(gameDirectory, target, role);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot own Runtime instance directory", exception);
        }
    }

    public static int configuredPort() {
        String value =
                System.getProperty("minecraft.protocol.probe.port", Integer.toString(DEFAULT_PORT));
        try {
            int port = Integer.parseInt(value);
            return requireHttpBindPort(port);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "minecraft.protocol.probe.port must be 0 (automatic) or 1..65535");
        }
    }

    static int requireHttpBindPort(int port) {
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException(
                    "minecraft.protocol.probe.port must be 0 (automatic) or 1..65535");
        if (port != 0 && !httpCompatiblePort(port))
            throw new IllegalArgumentException(
                    "Runtime fixed port " + port + " is blocked by the HTTP Fetch standard");
        return port;
    }

    static boolean httpCompatiblePort(int port) {
        return port > 0 && port <= 65535 && !FETCH_BLOCKED_PORTS.contains(port);
    }

    static int bindHttpCompatiblePort(
            int requestedPort, IntSupplier bindCandidate, Runnable closeCandidate) {
        requireHttpBindPort(requestedPort);
        int attempts = requestedPort == 0 ? AUTOMATIC_BIND_ATTEMPTS : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int actualPort = bindCandidate.getAsInt();
            if (httpCompatiblePort(actualPort)) return actualPort;
            closeCandidate.run();
        }
        throw new IllegalStateException(
                "Cannot bind an HTTP-fetch-compatible Runtime port after "
                        + attempts
                        + " bind candidates");
    }

    public String instanceId() {
        return this.identity.get("runtimeInstanceId").getAsString();
    }

    public synchronized JsonObject snapshot() {
        return this.identity.deepCopy();
    }

    public synchronized void publish(int port) throws IOException {
        if (this.closed) throw new IOException("Runtime endpoint is closed");
        if (!httpCompatiblePort(port))
            throw new IOException("Runtime endpoint port is blocked by the HTTP Fetch standard");
        this.identity.addProperty("baseUrl", "http://127.0.0.1:" + port);
        this.identity.addProperty("port", port);
        this.identity.addProperty("state", "RUNNING");
        Path temporary = this.directory.resolve("endpoint-" + instanceId() + ".tmp");
        try {
            Files.writeString(
                    temporary,
                    new Gson().toJson(this.identity) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            Files.move(
                    temporary,
                    this.endpoint,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;
        try {
            if (Files.isRegularFile(this.endpoint) && Files.size(this.endpoint) <= 16384) {
                JsonObject record =
                        JsonParser.parseString(Files.readString(this.endpoint)).getAsJsonObject();
                if (record.has("runtimeInstanceId")
                        && instanceId().equals(record.get("runtimeInstanceId").getAsString()))
                    Files.delete(this.endpoint);
            }
        } catch (IOException | RuntimeException exception) {
            // A retained record is stale, not authorization. Clients must pin the live identity.
            System.err.println(
                    "Minecraft Protocol Runtime discovery cleanup failed: "
                            + exception.getClass().getSimpleName());
        } finally {
            try {
                this.lock.release();
            } catch (IOException ignored) {
            }
            try {
                this.channel.close();
            } catch (IOException ignored) {
            }
        }
    }
}
