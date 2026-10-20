package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Observes one ordinary pause save; the bounded barrier arranges time, not saving state or UI. */
public final class UiSavingIndicatorFixture {
    private static final String TARGET = "26.3-fabric";
    private static final long MAXIMUM_BARRIER_MILLIS = 3000;
    private static final ThreadLocal<State> SAVING_EXTRACTION = new ThreadLocal<>();
    private static volatile State state;

    private UiSavingIndicatorFixture() {}

    static void publish(Path temporary, Path destination) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AccessDeniedException busy) {
                // Windows readers can briefly deny replacement; retain the complete old record.
                if (attempt == 7) throw busy;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Native pause-save publication interrupted", interrupted);
                }
            }
        }
    }

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.uiSavingIndicatorFixture")
                && "conformance-vanilla_ui".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"));
    }

    public static boolean prepare() {
        if (!enabled()) return false;
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null
                || client.level == null
                || client.player == null
                || client.getConnection() == null) return false;
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        Path targetRoot = root.getParent();
        if (!root.getFileName().toString().equals("client")
                || targetRoot == null
                || !targetRoot.getFileName().toString().equals(TARGET)
                || !targetRoot.getParent().getFileName().toString().equals("instance")
                || !targetRoot
                        .getParent()
                        .getParent()
                        .getFileName()
                        .toString()
                        .equals("conformance-vanilla_ui")
                || !targetRoot
                        .getParent()
                        .getParent()
                        .getParent()
                        .getFileName()
                        .toString()
                        .equals("validations")) return false;
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!world.startsWith(root.resolve("saves"))
                || !server.getWorldData().getLevelSettings().levelName().equals("UI-Conformance"))
            return false;
        try {
            if (!root.toRealPath().equals(root) || !world.toRealPath().equals(world)) return false;
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "The owned pause-save world could not be resolved", failure);
        }
        State current = state;
        if (current == null) {
            current = new State(client, server);
            state = current;
        }
        if (current.armed || !current.clientMatches(client)) return false;
        current.report();
        return true;
    }

    public static void arm() {
        State owner = state;
        Minecraft client = Minecraft.getInstance();
        if (!enabled()
                || owner == null
                || owner.armed
                || !owner.clientMatches(client)
                || !owner.server.isPaused()
                || owner.server.isCurrentlySaving()
                || !owner.failure.isEmpty()
                || !Boolean.TRUE.equals(client.options.showAutosaveIndicator().get()))
            throw new IllegalStateException(
                    "Wait for the owned probe menu to finish its native pause save before arming");
        owner.armed = true;
        owner.readyForArm = false;
        owner.report();
    }

    /** Runs on the actual integrated-server owner, before its next native pause decision. */
    public static void observeServerTick(IntegratedServer server) {
        State owner = state;
        if (!enabled() || owner == null || owner.server != server) return;
        if (!owner.serverMatches()) {
            owner.failure = "owned_server_context_changed";
            owner.nativeDraw.countDown();
            owner.report();
            return;
        }
        boolean changed = owner.serverPaused != server.isPaused();
        owner.serverPaused = server.isPaused();
        boolean ready = !owner.armed && owner.serverPaused && !server.isCurrentlySaving();
        changed |= owner.readyForArm != ready;
        owner.readyForArm = ready;
        if (owner.armed && !owner.resumeObserved && !owner.serverPaused) {
            owner.resumeObserved = true;
            changed = true;
        }
        if (changed) owner.report();
    }

    /** This hook belongs only to IntegratedServer's !wasPaused && paused native save call. */
    public static void beforeNativePauseSave(IntegratedServer server) {
        State owner = state;
        if (!enabled()
                || owner == null
                || owner.server != server
                || !owner.armed
                || !owner.resumeObserved
                || owner.selectedNativePauseSaveCalls != 0
                || !owner.failure.isEmpty()) return;
        if (!owner.serverMatches() || !server.isPaused()) return;
        owner.selectedNativePauseSaveCalls = 1;
        owner.report();
    }

    /** Called strictly after Vanilla's first isSaving=true store, not instead of that store. */
    public static void afterNativeSavingFlag(
            MinecraftServer server, boolean suppressLog, boolean flush, boolean forced) {
        State owner = state;
        if (!enabled()
                || owner == null
                || owner.server != server
                || owner.selectedNativePauseSaveCalls != 1
                || owner.nativeSaveStarted
                || !owner.failure.isEmpty()) return;
        if (!owner.serverMatches()
                || !server.isCurrentlySaving()
                || suppressLog
                || flush
                || forced) {
            owner.failure = "native_ordinary_pause_save_entry_mismatch";
            owner.report();
            return;
        }
        owner.nativeSaveStarted = true;
        owner.nativeSaveSuppressedLog = suppressLog;
        owner.nativeSaveFlush = flush;
        owner.nativeSaveForced = forced;
        owner.barrierStartedNanos = System.nanoTime();
        owner.barrierDeadlineNanos =
                owner.barrierStartedNanos + TimeUnit.MILLISECONDS.toNanos(MAXIMUM_BARRIER_MILLIS);
        owner.barrierActive = true;
        try {
            owner.report();
            long remaining = owner.barrierDeadlineNanos - System.nanoTime();
            boolean released =
                    remaining > 0 && owner.nativeDraw.await(remaining, TimeUnit.NANOSECONDS);
            if (!released) owner.barrierTimedOut = true;
        } catch (InterruptedException interrupted) {
            owner.failure = "diagnostic_barrier_interrupted";
            Thread.currentThread().interrupt();
        } finally {
            owner.barrierElapsedMillis =
                    (System.nanoTime() - owner.barrierStartedNanos) / 1_000_000.0;
            owner.barrierActive = false;
            owner.report();
        }
    }

    public static void afterNativeSaveReturned(MinecraftServer server, boolean result) {
        State owner = state;
        if (!enabled()
                || owner == null
                || owner.server != server
                || !owner.nativeSaveStarted
                || owner.nativeSaveReturned) return;
        if (Thread.currentThread() != server.getRunningThread())
            throw new IllegalStateException("Save returned outside its native owner thread");
        owner.nativeSaveResult = result;
        owner.nativeSaveReturned = true;
        owner.nativeSavingFlagCleared = !server.isCurrentlySaving();
        owner.report();
    }

    public static void beginSavingExtraction() {
        State owner = state;
        if (enabled() && owner != null && owner.armed) SAVING_EXTRACTION.set(owner);
    }

    public static void endSavingExtraction() {
        SAVING_EXTRACTION.remove();
    }

    /** Observes the completed native text call, including the product's existing redirect. */
    public static void observeNativeText(Component text, int color, int x, int y, int width) {
        State owner = SAVING_EXTRACTION.get();
        if (owner == null
                || owner != state
                || !owner.barrierActive
                || owner.nativeTextDrawObserved
                || System.nanoTime() >= owner.barrierDeadlineNanos
                || (color >>> 24) == 0
                || !(text.getContents() instanceof TranslatableContents contents)
                || !contents.getKey().equals("menu.savingLevel")) return;
        Minecraft client = Minecraft.getInstance();
        if (!owner.clientMatches(client)
                || !(client.gui.screen() instanceof PauseScreen)
                || !owner.server.isCurrentlySaving()) return;
        owner.nativeTextDrawObserved = true;
        owner.nativeTextLabel = text.getString();
        owner.nativeTextOpacity = (color >>> 24) / 255.0;
        owner.nativeTextX = x;
        owner.nativeTextY = y;
        owner.nativeTextWidth = width;
        owner.nativeScreenClass = client.gui.screen().getClass().getName();
        owner.drawWhileActualSaving = true;
        owner.report();
        owner.nativeDraw.countDown();
    }

    private static final class State {
        private final String fixtureId = UUID.randomUUID().toString();
        private final IntegratedServer server;
        private final ClientLevel level;
        private final LocalPlayer player;
        private final ClientPacketListener clientConnection;
        private final Connection connection;
        private final UUID playerId;
        private final CountDownLatch nativeDraw = new CountDownLatch(1);
        private volatile boolean armed,
                readyForArm,
                serverPaused,
                resumeObserved,
                nativeSaveStarted,
                nativeSaveReturned;
        private volatile boolean nativeSaveResult,
                nativeSavingFlagCleared,
                nativeSaveSuppressedLog,
                nativeSaveFlush,
                nativeSaveForced;
        private volatile boolean barrierActive,
                barrierTimedOut,
                nativeTextDrawObserved,
                drawWhileActualSaving;
        private volatile long barrierStartedNanos, barrierDeadlineNanos;
        private volatile int selectedNativePauseSaveCalls,
                nativeTextX,
                nativeTextY,
                nativeTextWidth;
        private volatile double barrierElapsedMillis, nativeTextOpacity;
        private volatile String failure = "", nativeTextLabel = "", nativeScreenClass = "";

        private State(Minecraft client, IntegratedServer server) {
            this.server = server;
            level = client.level;
            player = client.player;
            clientConnection = client.getConnection();
            connection = clientConnection.getConnection();
            playerId = player.getUUID();
        }

        private boolean clientMatches(Minecraft client) {
            return client.getSingleplayerServer() == server
                    && client.level == level
                    && client.player == player
                    && client.getConnection() == clientConnection
                    && connection.isConnected();
        }

        private boolean serverMatches() {
            return Thread.currentThread() == server.getRunningThread()
                    && !server.isPublished()
                    && server.getPlayerList().getPlayerCount() == 1
                    && server.getPlayerList().getPlayer(playerId) != null
                    && server.getWorldData()
                            .getLevelSettings()
                            .levelName()
                            .equals("UI-Conformance");
        }

        private synchronized void report() {
            JsonObject value = new JsonObject();
            value.addProperty("probeOnly", true);
            value.addProperty("arrangeAuthority", "DIAGNOSTIC_bounded_native_pause_save_timing");
            value.addProperty("target", TARGET);
            value.addProperty("processId", ProcessHandle.current().pid());
            value.addProperty("fixtureId", fixtureId);
            value.addProperty("armed", armed);
            value.addProperty("readyForArm", readyForArm);
            value.addProperty("resumeObserved", resumeObserved);
            value.addProperty("selectedNativePauseSaveCalls", selectedNativePauseSaveCalls);
            value.addProperty("nativeSaveStarted", nativeSaveStarted);
            value.addProperty("nativeSaveReturned", nativeSaveReturned);
            value.addProperty("nativeSaveResult", nativeSaveResult);
            value.addProperty("nativeSavingFlagCleared", nativeSavingFlagCleared);
            value.addProperty("nativeSaveSuppressedLog", nativeSaveSuppressedLog);
            value.addProperty("nativeSaveFlush", nativeSaveFlush);
            value.addProperty("nativeSaveForced", nativeSaveForced);
            value.addProperty("barrierMaximumMillis", MAXIMUM_BARRIER_MILLIS);
            value.addProperty("barrierActive", barrierActive);
            value.addProperty("barrierTimedOut", barrierTimedOut);
            value.addProperty("barrierElapsedMillis", barrierElapsedMillis);
            value.addProperty("nativeTextDrawObserved", nativeTextDrawObserved);
            value.addProperty("drawWhileActualSaving", drawWhileActualSaving);
            value.addProperty("nativeTextLabel", nativeTextLabel);
            value.addProperty("nativeTextOpacity", nativeTextOpacity);
            value.addProperty("nativeTextX", nativeTextX);
            value.addProperty("nativeTextY", nativeTextY);
            value.addProperty("nativeTextWidth", nativeTextWidth);
            value.addProperty("nativeScreenClass", nativeScreenClass);
            value.addProperty("failure", failure);
            value.addProperty("savingStateWrittenByProbe", false);
            value.addProperty("indicatorAlphaWrittenByProbe", false);
            value.addProperty("additionalSaveInvokedByProbe", false);
            value.addProperty("naturalSaveDurationValidated", false);
            value.addProperty("performanceValidated", false);
            value.addProperty("worldFilesTouched", "owned_validation_world_only");
            value.addProperty("persistentWriteApiCalls", 0);
            try {
                Path directory = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(directory);
                Path destination = directory.resolve(TARGET + "-saving-indicator-probe.json");
                Path temporary = directory.resolve(TARGET + "-saving-indicator-probe.pending");
                Files.writeString(temporary, value + "\n", StandardCharsets.UTF_8);
                publish(temporary, destination);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "The actual native pause-save observations could not be recorded", failure);
            }
        }
    }
}
