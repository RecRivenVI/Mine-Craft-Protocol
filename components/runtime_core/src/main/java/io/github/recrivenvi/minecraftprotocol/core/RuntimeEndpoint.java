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
import java.util.UUID;

/** Owns one game directory's connection record, not its world or input authority. */
public final class RuntimeEndpoint implements AutoCloseable {
    public static final String INSTANCE_HEADER = "X-MCP-Runtime-Instance";
    public static final int DEFAULT_PORT = 0;
    private final Path directory;
    private final Path endpoint;
    private final FileChannel channel;
    private final FileLock lock;
    private final JsonObject identity = new JsonObject();
    private boolean closed;

    private RuntimeEndpoint(Path gameDirectory, String target) throws IOException {
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
        this.identity.addProperty("gameDirectory", game.toString());
        this.identity.addProperty("pid", ProcessHandle.current().pid());
        this.identity.addProperty(
                "processStartedAt",
                ProcessHandle.current().info().startInstant().orElseThrow().toString());
        this.identity.addProperty("startedAt", Instant.now().toString());
        this.identity.addProperty("tokenFile", "token");
    }

    public static RuntimeEndpoint acquire(Path gameDirectory, String target) {
        try {
            return new RuntimeEndpoint(gameDirectory, target);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot own Runtime instance directory", exception);
        }
    }

    public static int configuredPort() {
        String value =
                System.getProperty("minecraft.protocol.probe.port", Integer.toString(DEFAULT_PORT));
        try {
            int port = Integer.parseInt(value);
            if (port < 0 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "minecraft.protocol.probe.port must be 0 (automatic) or 1..65535");
        }
    }

    public String instanceId() {
        return this.identity.get("runtimeInstanceId").getAsString();
    }

    public synchronized JsonObject snapshot() {
        return this.identity.deepCopy();
    }

    public synchronized void publish(int port) throws IOException {
        if (this.closed) throw new IOException("Runtime endpoint is closed");
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
