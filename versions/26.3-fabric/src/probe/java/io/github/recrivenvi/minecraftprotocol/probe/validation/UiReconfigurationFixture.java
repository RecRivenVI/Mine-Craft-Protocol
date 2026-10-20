package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.validation.mixin.UiReconfigurationScreenProbeAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.DirectoryLock;
import net.minecraft.world.level.storage.LevelResource;

/** Requests the native reconfiguration transition of one owned local validation player. */
public final class UiReconfigurationFixture {
    private static final String TARGET = "26.3-fabric";
    private static State state;

    private UiReconfigurationFixture() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.uiNavigationFixture")
                && "conformance-vanilla_ui".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"))
                && Boolean.getBoolean("minecraft.protocol.uiReconfigurationFixture");
    }

    public static boolean prepare() {
        if (!enabled()) return false;
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null
                || client.player == null
                || client.level == null
                || client.getConnection() == null) return false;
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        Path targetRoot = root.getParent();
        if (!root.getFileName().toString().equals("client")
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
                    "The owned reconfiguration world could not be resolved", failure);
        }
        Connection connection = client.getConnection().getConnection();
        if (state == null || state.clientConnection != connection || state.switchRequested) {
            state = new State(server, connection, client.player.getUUID(), world);
        }
        state.report();
        return true;
    }

    public static void arrange() {
        if (!enabled() || state == null || state.switchRequested)
            throw new IllegalStateException(
                    "Native reconfiguration was not prepared or already requested");
        State owner = state;
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != owner.server
                || client.getConnection() == null
                || client.getConnection().getConnection() != owner.clientConnection
                || client.player == null
                || !client.player.getUUID().equals(owner.playerId))
            throw new IllegalStateException("The prepared local player context changed");
        owner.sequence++;
        owner.switchRequested = true;
        owner.report();
        owner.server.execute(
                () -> {
                    try {
                        var player = owner.server.getPlayerList().getPlayer(owner.playerId);
                        owner.serverOwnerThread =
                                owner.server.getRunningThread() == Thread.currentThread();
                        owner.singlePlayerOnly = owner.server.getPlayerList().getPlayerCount() == 1;
                        owner.unpublished = !owner.server.isPublished();
                        if (state != owner
                                || !owner.serverOwnerThread
                                || !owner.singlePlayerOnly
                                || !owner.unpublished
                                || player == null
                                || !owner.server
                                        .getWorldData()
                                        .getLevelSettings()
                                        .levelName()
                                        .equals("UI-Conformance"))
                            throw new IllegalStateException(
                                    "Native reconfiguration requires the single unpublished owned player");
                        var matches =
                                owner.server.getConnection().getConnections().stream()
                                        .filter(
                                                connection ->
                                                        connection.getPacketListener()
                                                                == player.connection)
                                        .toList();
                        if (matches.size() != 1)
                            throw new IllegalStateException(
                                    "The owned server listener must identify exactly one native connection");
                        owner.serverConnection = matches.getFirst();
                        if (!owner.serverConnection.isConnected()
                                || !owner.clientConnection.isConnected())
                            throw new IllegalStateException("The owned connection already closed");
                        player.connection.switchToConfig();
                        owner.switchReturned = true;
                    } catch (Exception failure) {
                        owner.failure = failure.toString();
                    }
                    owner.report();
                });
    }

    public static void observeNativeScreen(ServerReconfigScreen screen) {
        State owner = state;
        if (!enabled() || owner == null || !owner.switchRequested) return;
        var nativeState = (UiReconfigurationScreenProbeAccessor) screen;
        if (nativeState.minecraftProtocolProbe$connection() != owner.clientConnection) return;
        owner.nativeScreen = screen;
        int ticks = nativeState.minecraftProtocolProbe$delayTicker();
        boolean active = nativeState.minecraftProtocolProbe$disconnectButton().active;
        boolean changed = false;
        if (!owner.nativeScreenObserved) {
            owner.connectionIdentityBound = true;
            owner.firstNativeDelayTicker = ticks;
            owner.disconnectInitiallyDisabled = !active;
            owner.nativeScreenObserved = true;
            changed = true;
        }
        if (ticks >= 600 && active && !owner.disconnectReadyObserved) {
            owner.readyNativeDelayTicker = ticks;
            owner.disconnectReadyObserved = true;
            changed = true;
        }
        if (changed) owner.report();
    }

    public static void observeNativeDisconnect(ServerReconfigScreen screen) {
        State owner = state;
        if (!enabled() || owner == null || owner.nativeScreen != screen) return;
        owner.nativeDisconnectHandlerReturned = true;
        owner.report();
    }

    public static void observeClientTick() {
        State owner = state;
        if (!enabled() || owner == null || !owner.switchRequested) return;
        boolean changed = false;
        if (!owner.configurationAcknowledged
                && owner.serverConnection != null
                && owner.serverConnection.getPacketListener()
                        instanceof ServerConfigurationPacketListenerImpl) {
            owner.configurationAcknowledged = true;
            changed = true;
        }
        if (!owner.nativeClientConnectionClosed && !owner.clientConnection.isConnected()) {
            owner.nativeClientConnectionClosed = true;
            changed = true;
        }
        if (!owner.nativeServerConnectionClosed
                && owner.serverConnection != null
                && !owner.serverConnection.isConnected()) {
            owner.nativeServerConnectionClosed = true;
            changed = true;
        }
        if (!owner.integratedServerStopped
                && owner.server.isStopped()
                && !owner.server.getRunningThread().isAlive()) {
            owner.integratedServerStopped = true;
            try {
                owner.sourceDirectoryLockReleased = !DirectoryLock.isLocked(owner.world);
            } catch (IOException | RuntimeException failure) {
                owner.failure = failure.toString();
            }
            changed = true;
        }
        if (changed) owner.report();
    }

    private static final class State {
        private final String fixtureId = UUID.randomUUID().toString();
        private final IntegratedServer server;
        private final Connection clientConnection;
        private final UUID playerId;
        private final Path world;
        private volatile Connection serverConnection;
        private volatile ServerReconfigScreen nativeScreen;
        private volatile int sequence;
        private volatile boolean switchRequested;
        private volatile boolean switchReturned;
        private volatile boolean serverOwnerThread;
        private volatile boolean singlePlayerOnly;
        private volatile boolean unpublished;
        private volatile boolean configurationAcknowledged;
        private volatile boolean nativeScreenObserved;
        private volatile boolean connectionIdentityBound;
        private volatile boolean disconnectInitiallyDisabled;
        private volatile int firstNativeDelayTicker = -1;
        private volatile boolean disconnectReadyObserved;
        private volatile int readyNativeDelayTicker = -1;
        private volatile boolean nativeDisconnectHandlerReturned;
        private volatile boolean nativeClientConnectionClosed;
        private volatile boolean nativeServerConnectionClosed;
        private volatile boolean integratedServerStopped;
        private volatile boolean sourceDirectoryLockReleased;
        private volatile String failure = "";

        private State(
                IntegratedServer server, Connection clientConnection, UUID playerId, Path world) {
            this.server = server;
            this.clientConnection = clientConnection;
            this.playerId = playerId;
            this.world = world;
        }

        private synchronized void report() {
            JsonObject value = new JsonObject();
            value.addProperty("probeOnly", true);
            value.addProperty("arrangeAuthority", "DIAGNOSTIC_native_reconfiguration");
            value.addProperty("target", TARGET);
            value.addProperty("fixtureId", fixtureId);
            value.addProperty("processId", ProcessHandle.current().pid());
            value.addProperty("sequence", sequence);
            value.addProperty("sourceWorldName", "UI-Conformance");
            value.addProperty("sourceLevelId", world.getFileName().toString());
            value.addProperty("worldFilesTouched", "owned_validation_working_world");
            for (String field :
                    new String[] {
                        "immutableBaselineTouched",
                        "dailyInstancesTouched",
                        "externalServerUsed",
                        "serverConfigStartInvokedByProbe",
                        "configurationTaskInjected",
                        "customProtocolPacketsSent",
                        "configurationSuccessClaimed"
                    }) value.addProperty(field, false);
            value.addProperty("switchRequested", switchRequested);
            value.addProperty("switchReturned", switchReturned);
            value.addProperty("serverOwnerThread", serverOwnerThread);
            value.addProperty("singlePlayerOnly", singlePlayerOnly);
            value.addProperty("unpublished", unpublished);
            value.addProperty("configurationAcknowledged", configurationAcknowledged);
            value.addProperty(
                    "nativeClass", nativeScreen == null ? "" : nativeScreen.getClass().getName());
            value.addProperty("nativeScreenObserved", nativeScreenObserved);
            value.addProperty("connectionIdentityBound", connectionIdentityBound);
            value.addProperty("disconnectInitiallyDisabled", disconnectInitiallyDisabled);
            value.addProperty("firstNativeDelayTicker", firstNativeDelayTicker);
            value.addProperty("disconnectReadyObserved", disconnectReadyObserved);
            value.addProperty("readyNativeDelayTicker", readyNativeDelayTicker);
            value.addProperty("nativeDisconnectHandlerReturned", nativeDisconnectHandlerReturned);
            value.addProperty("nativeClientConnectionClosed", nativeClientConnectionClosed);
            value.addProperty("nativeServerConnectionClosed", nativeServerConnectionClosed);
            value.addProperty("integratedServerStopped", integratedServerStopped);
            value.addProperty("sourceDirectoryLockReleased", sourceDirectoryLockReleased);
            Minecraft client = Minecraft.getInstance();
            value.addProperty("playerAbsent", client.player == null);
            value.addProperty("connectionAbsent", client.getConnection() == null);
            value.addProperty(
                    "returnedScreenClass",
                    client.gui.screen() == null ? "" : client.gui.screen().getClass().getName());
            if (!failure.isEmpty()) value.addProperty("failure", failure);
            try {
                Path output = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(output);
                Path result = output.resolve(TARGET + "-reconfiguration-ui-probe.json");
                Path temporary = output.resolve(TARGET + "-reconfiguration-ui-probe.json.tmp");
                Files.writeString(temporary, value + "\n", StandardCharsets.UTF_8);
                UiSavingIndicatorFixture.publish(temporary, result);
            } catch (IOException problem) {
                throw new IllegalStateException(
                        "Native reconfiguration facts were not published", problem);
            }
        }
    }
}
