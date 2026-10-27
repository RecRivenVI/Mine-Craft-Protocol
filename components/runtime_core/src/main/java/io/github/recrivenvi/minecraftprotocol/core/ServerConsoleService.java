package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Dedicated operator surfaces; graphical/player input capabilities remain explicitly absent. */
public final class ServerConsoleService extends HeadlessProbeService implements AutoCloseable {
    private final String target;
    private final ServerConsole console;
    private final ConsoleLogCapture capture;
    private final ServerWorldDebug worldDebug;

    public ServerConsoleService(Path directory, String target, ServerConsole.Host host) {
        this(directory, target, host, null);
    }

    public ServerConsoleService(
            Path directory,
            String target,
            ServerConsole.Host host,
            ServerWorldDebug.Host worldHost) {
        super(directory);
        this.target = target;
        this.console = new ServerConsole(host);
        this.capture = new ConsoleLogCapture(console);
        this.worldDebug = worldHost == null ? null : new ServerWorldDebug(worldHost);
    }

    public ServerConsole console() {
        return console;
    }

    @Override
    public CompletableFuture<Boolean> peerAuthorityAvailable() {
        return CompletableFuture.completedFuture(worldDebug != null);
    }

    @Override
    public CompletableFuture<JsonObject> session() {
        JsonObject value = ServerConsole.identity();
        value.addProperty("target", target);
        value.addProperty("minecraft", target.substring(0, target.lastIndexOf('-')));
        value.addProperty("loader", target.substring(target.lastIndexOf('-') + 1));
        value.addProperty("deploymentRole", "DEDICATED_SERVER");
        value.addProperty("type", "session");
        value.addProperty("protocolVersion", "v0");
        value.addProperty("inputSurfaceAvailable", false);
        value.addProperty("graphicalSurfaceAvailable", false);
        value.addProperty("worldDebugImplemented", worldDebug != null);
        value.addProperty("persistentReadImplemented", worldDebug != null);
        value.addProperty("serverReady", true);
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public CompletableFuture<JsonObject> capabilities() {
        return session()
                .thenApply(
                        value -> {
                            value.addProperty("type", "capabilities");
                            value.add("serverConsole", ServerConsole.contract());
                            JsonObject supported = new JsonObject();
                            supported.addProperty("server.console", true);
                            supported.addProperty("input", false);
                            supported.addProperty("capture", false);
                            supported.addProperty("world.debug", worldDebug != null);
                            supported.addProperty("storage.read", worldDebug != null);
                            supported.addProperty("recording", true);
                            supported.addProperty("recording.framebuffer", false);
                            if (worldDebug != null)
                                value.add("worldDebug", worldDebug.capabilities());
                            value.add("capabilities", supported);
                            return value;
                        });
    }

    @Override
    public CompletableFuture<JsonObject> readiness() {
        return session()
                .thenApply(
                        value -> {
                            value.addProperty("ready", true);
                            return value;
                        });
    }

    @Override
    public CompletableFuture<JsonObject> playerState() {
        JsonObject result = ServerConsole.identity();
        result.addProperty("available", false);
        result.addProperty("reason", "no_player_input_surface");
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void close() {
        if (worldDebug != null) worldDebug.close();
        console.close();
        capture.close();
    }

    @Override
    public CompletableFuture<JsonObject> worldFingerprint() {
        return worldDebug == null
                ? unavailable()
                : CompletableFuture.completedFuture(worldDebug.fingerprint());
    }

    @Override
    public CompletableFuture<JsonObject> formalObservationCapabilities() {
        return worldDebug == null
                ? unavailable()
                : CompletableFuture.completedFuture(worldDebug.observationCapabilities());
    }

    @Override
    public CompletableFuture<JsonObject> formalDeepObservation(
            JsonObject request, DeepObservationRequestContext context) {
        return worldDebug == null ? unavailable() : worldDebug.observe(request, context);
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugCapabilities() {
        return worldDebug == null
                ? unavailable()
                : CompletableFuture.completedFuture(worldDebug.capabilities());
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugMutation(
            JsonObject request, DebugMutationAuthorization authorization) {
        return worldDebug == null ? unavailable() : worldDebug.mutate(request, authorization);
    }

    @Override
    public CompletableFuture<JsonObject> phase9aStorageRead(JsonObject request) {
        return worldDebug == null ? unavailable() : worldDebug.storageRead(request);
    }

    public JsonObject consoleSnapshot() {
        return console.read(0, 128);
    }
}
