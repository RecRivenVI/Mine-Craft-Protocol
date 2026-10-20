package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.recrivenvi.minecraftprotocol.core.AutomationEngine;
import io.github.recrivenvi.minecraftprotocol.core.DebugMutationAuthorization;
import io.github.recrivenvi.minecraftprotocol.core.DeepObservationRequestContext;
import io.github.recrivenvi.minecraftprotocol.core.Phase9AExperimentalService;
import io.github.recrivenvi.minecraftprotocol.core.ProbeTransport;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ProviderExecutionEngine;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyboardHandlerInvoker;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.MouseHandlerAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.MouseHandlerInvoker;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.system.MemoryUtil;

public final class NeoForgeProbeRuntime implements Phase9AExperimentalService {
    private static final NeoForgeProbeRuntime INSTANCE = new NeoForgeProbeRuntime();
    private static final ScheduledExecutorService WAITS =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "minecraft-protocol-probe-waits");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean shutdownRegistered = new AtomicBoolean();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean captureVerified = new AtomicBoolean();
    private final AtomicLong inputDispatchSequence = new AtomicLong();
    private final AtomicLong screenSlotClickSequence = new AtomicLong();
    private final AtomicLong menuDispatchSequence = new AtomicLong();
    private final AtomicLong containerPacketSequence = new AtomicLong();
    private final AtomicLong serverContainerPacketSequence = new AtomicLong();
    private final AtomicLong renderElementSequence = new AtomicLong();
    private final AtomicLong renderItemSequence = new AtomicLong();
    private final AtomicLong renderTextSequence = new AtomicLong();
    private final AtomicLong renderPictureSequence = new AtomicLong();
    private final AtomicLong renderFactSequence = new AtomicLong();
    private final Phase9ASpikeEngine phase9a = new Phase9ASpikeEngine("26.3-neoforge");
    private final Deque<RenderFact> recentRenderFacts = new ArrayDeque<>();
    private final Map<Integer, KeyState> pressedKeys =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Integer> pressedButtons = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final io.github.recrivenvi.minecraftprotocol.safety.NativeInputOwnership<
                    net.minecraft.client.KeyMapping>
            nativeInputOwnership =
                    new io.github.recrivenvi.minecraftprotocol.safety.NativeInputOwnership<>();
    private final Map<Integer, Screen> buttonScreens = new HashMap<>();
    private final Map<Integer, String> buttonFrames = new HashMap<>();
    private volatile Minecraft minecraft;
    private volatile ProbeTransport transport;
    private volatile Screen lastScreen;
    private volatile long clientTick;
    private volatile long screenRevision;
    private volatile long menuRevision;
    private volatile String lastMenuHash = "";
    private final UiItemDataGuard itemDataGuard = new UiItemDataGuard();
    private final Map<Slot, UiItemDataGuard.Content> slotContents =
            new java.util.IdentityHashMap<>();
    private UiItemDataGuard.Content carriedContent = UiItemDataGuard.Content.unavailable();
    private boolean menuContentIncomplete;
    private volatile AbstractContainerMenu lastMenu;
    private volatile String lastTraceDetail = "";
    private volatile String lastServerThread = "";
    private volatile String lastRenderFact = "";
    private volatile AgentControlSession controlSession = new AgentControlSession();
    private final io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch inputContext =
            new io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch();
    private io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch.Snapshot
            publishedInputContext;
    private io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch.Snapshot
            nativeEntityAttackContext;
    private Object nativeEntityAttackWorld, nativeEntityAttackPlayer, nativeEntityAttackConnection;
    private long nativeEntityAttackCount;
    private long nativeEntityAttackControlGeneration = -1;
    private final io.github.recrivenvi.minecraftprotocol.safety.WorldRegionPages regionPages =
            new io.github.recrivenvi.minecraftprotocol.safety.WorldRegionPages();
    private volatile io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate commandGate;
    private volatile AgentControlSession.Snapshot controlPresence = this.controlSession.snapshot();
    private final java.util.concurrent.atomic.AtomicLongArray suppressedNative =
            new java.util.concurrent.atomic.AtomicLongArray(AgentInputContext.Kind.values().length);
    private final io.github.recrivenvi.minecraftprotocol.safety.AgentPointer logicalPointer =
            new io.github.recrivenvi.minecraftprotocol.safety.AgentPointer();
    private volatile String activeGestureId = "";
    private boolean lastTakeover;
    private long pointerScreenRevision;
    private String pointerViewport = "";
    private final io.github.recrivenvi.minecraftprotocol.safety.UiInteractionIdentities
            interactionIdentities =
                    new io.github.recrivenvi.minecraftprotocol.safety.UiInteractionIdentities();
    private final AtomicLong nativeRevocations = new AtomicLong();
    private final AtomicLong rejectedHostGrabs = new AtomicLong();
    private final io.github.recrivenvi.minecraftprotocol.safety.ControlChrome operatorChrome =
            new io.github.recrivenvi.minecraftprotocol.safety.ControlChrome();
    private final io.github.recrivenvi.minecraftprotocol.safety.FrameCaptureQueue evidenceCaptures =
            new io.github.recrivenvi.minecraftprotocol.safety.FrameCaptureQueue();
    private volatile boolean renderingOperatorChrome;
    private boolean contentFrameReady;
    private final AtomicBoolean windowClosing = new AtomicBoolean();
    private java.util.List<WindowIcon> originalWindowIcons = java.util.List.of();
    private java.util.List<WindowIcon> decoratedWindowIcons = java.util.List.of();
    private boolean iconBadgeApplied;
    private String windowDecorationError = "";
    private boolean agentPointerInitialized;
    private Boolean originalPauseOnLostFocus;
    private double agentPointerX, agentPointerY;
    private volatile boolean controlChromeApplied;
    private volatile String vanillaWindowTitle = "Minecraft";
    private String appliedWindowTitle;
    private final AtomicLong controlChromeRenderSequence = new AtomicLong();

    private NeoForgeProbeRuntime() {
        this.phase9a.installProviderDispatcher(this::dispatchProvider);
        this.phase9a.installProviderMutationDispatcher(this::dispatchProviderMutation);
    }

    public static boolean allowSemanticWorldInput(String kind) {
        return INSTANCE.worldTargeting.holdActive()
                ? INSTANCE.controlPresence.agentControlled() && INSTANCE.worldTargeting.permit(kind)
                : true;
    }

    public static boolean allowPlayerCommand(
            String command, boolean packet, net.minecraft.network.Connection source) {
        return allowPlayerCommand(command, packet, null, source);
    }

    public static boolean allowPlayerCommandPacket(
            net.minecraft.network.protocol.Packet<?> packet,
            net.minecraft.network.Connection source) {
        var configuration = NativePlayerCommands.configuration(packet);
        if (configuration != null)
            return allowPlayerCommand(configuration.command(), true, configuration.kind(), source);
        String command = NativePlayerCommands.submittedCommand(packet);
        return command == null || allowPlayerCommand(command, true, source);
    }

    private static boolean allowPlayerCommand(
            String command,
            boolean packet,
            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate.ConfigurationKind
                    configuration,
            net.minecraft.network.Connection source) {
        var gate = INSTANCE.commandGate;
        boolean routed = AgentInputContext.isAgentRouted();
        if (gate == null) return !routed && !INSTANCE.controlPresence.agentControlled();
        if (!gate.requiresInspection(routed)) return true;
        Minecraft client = INSTANCE.minecraft;
        if (client == null
                || !client.isSameThread()
                || client.getConnection() == null
                || !source.isConnected()
                || client.getConnection().getConnection() != source)
            return gate.reject(
                    "PLAYER_COMMAND_CONTEXT_CHANGED",
                    "Command connection no longer belongs to the current player");
        return configuration == null
                ? gate.allow(
                        command, routed, packet, () -> NativePlayerCommands.parse(client, command))
                : gate.allowConfiguration(
                        command,
                        routed,
                        configuration,
                        () ->
                                NativePlayerCommands.parseConfiguration(
                                        client, configuration, command));
    }

    public static void beforeSemanticDestroy(net.minecraft.core.BlockPos pos) {
        INSTANCE.worldTargeting.beforeDestroy(pos);
    }

    public static void afterSemanticDestroy(net.minecraft.core.BlockPos pos, boolean success) {
        INSTANCE.worldTargeting.afterDestroy(pos, success);
    }

    public static void semanticUseReleased() {
        INSTANCE.worldTargeting.usingReleased();
    }

    public static void semanticUseOnResult(
            net.minecraft.client.player.LocalPlayer player,
            net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit,
            net.minecraft.world.InteractionResult result) {
        INSTANCE.worldTargeting.nativeUseOnResult(player, hand, hit, result);
    }

    public static void nativeEntityAttackCompleted(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.Entity target) {
        INSTANCE.recordNativeEntityAttack(player, target);
    }

    private void recordNativeEntityAttack(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.Entity target) {
        Minecraft client = this.minecraft;
        var context = this.inputContext.snapshot();
        ProbeTransport current = this.transport;
        if (client == null
                || !client.isSameThread()
                || !this.controlPresence.agentControlled()
                || !AgentInputContext.isAgentRouted()
                || context != this.nativeEntityAttackContext
                || !context.worldAvailable()
                || this.controlPresence.transitionSequence()
                        != this.nativeEntityAttackControlGeneration
                || client.level != this.nativeEntityAttackWorld
                || client.player != this.nativeEntityAttackPlayer
                || client.getConnection() != this.nativeEntityAttackConnection
                || player != client.player
                || target == null
                || target.level() != client.level
                || current == null
                || !current.nativeInputLeaseActive(context)) return;
        this.nativeEntityAttackCount++;
    }

    public static void onClientTick(Minecraft minecraft) {
        INSTANCE.tick(minecraft);
    }

    public static boolean onNativeEscape(long window) {
        return INSTANCE.minecraft != null
                && window == INSTANCE.minecraft.getWindow().handle()
                && INSTANCE.handleNativeEscape();
    }

    public static boolean suppressNative(AgentInputContext.Kind kind) {
        if (!INSTANCE.controlPresence.agentControlled()) return false;
        INSTANCE.suppressedNative.incrementAndGet(kind.ordinal());
        return true;
    }

    public static boolean handleKeyIngress(long window, int key, int action, boolean agent) {
        var decision =
                io.github.recrivenvi.minecraftprotocol.safety.ExclusiveInputPolicy.decide(
                        INSTANCE.controlPresence.agentControlled(),
                        agent,
                        AgentInputContext.Kind.KEY,
                        key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE ? 256 : -1,
                        action);
        if (decision
                == io.github.recrivenvi.minecraftprotocol.safety.ExclusiveInputPolicy.Decision
                        .MANUAL_REVOKE) return onNativeEscape(window);
        return decision
                        == io.github.recrivenvi.minecraftprotocol.safety.ExclusiveInputPolicy
                                .Decision.SUPPRESS
                && suppressNative(AgentInputContext.Kind.KEY);
    }

    public static boolean isAgentKeyDown(int key) {
        return INSTANCE.pressedKeys.keySet().stream()
                .anyMatch(wire -> NativeInputMapping.key(wire) == key);
    }

    public static boolean allowHostMouseGrab() {
        if (!INSTANCE.controlPresence.agentControlled()) return true;
        INSTANCE.rejectedHostGrabs.incrementAndGet();
        // The game's logical capture request is independent of host focus. Host
        // capture remains blocked, including first world entry in the background.
        INSTANCE.requestGameCapture(true);
        return false;
    }

    public static boolean handleMouseRelease() {
        if (!INSTANCE.controlPresence.agentControlled()) return false;
        INSTANCE.requestGameCapture(false);
        INSTANCE.releaseHostCursor();
        return true;
    }

    public static boolean onGameCursorRequest(
            long window, boolean captured, boolean visible, double rawX, double rawY) {
        if (!INSTANCE.controlPresence.agentControlled()) return false;
        Minecraft client = INSTANCE.minecraft;
        if (client != null
                && !INSTANCE.windowClosing.get()
                && client.isSameThread()
                && Double.isFinite(rawX)
                && Double.isFinite(rawY)
                && client.getWindow().getScreenWidth() > 0
                && client.getWindow().getScreenHeight() > 0
                && window == client.getWindow().handle()) {
            INSTANCE.logicalPointer.viewport(
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight());
            if (INSTANCE.logicalPointer.gameRequest(
                    captured,
                    visible,
                    rawX
                            * client.getWindow().getGuiScaledWidth()
                            / client.getWindow().getScreenWidth(),
                    rawY
                            * client.getWindow().getGuiScaledHeight()
                            / client.getWindow().getScreenHeight()))
                INSTANCE.gameCursorChanged(client, "game_position_or_mode");
        }
        return true;
    }

    public static boolean handleMouseResync() {
        if (!INSTANCE.controlPresence.agentControlled()) return false;
        Minecraft client = INSTANCE.minecraft;
        if (client != null && !INSTANCE.windowClosing.get() && client.isSameThread()) {
            INSTANCE.syncPointerContext(client);
            INSTANCE.restoreAgentPointer(client);
        }
        return true;
    }

    public static boolean routedWindowActive() {
        return AgentInputContext.isAgentRouted() && INSTANCE.controlPresence.agentControlled();
    }

    public static boolean routedMouseGrabbed() {
        return routedWindowActive()
                && INSTANCE.gameplayViewport()
                && INSTANCE.logicalPointer.gameCaptured();
    }

    public static void onHostFocus(boolean focused) {
        if (!focused && INSTANCE.controlPresence.agentControlled()) {
            INSTANCE.releaseHostCursor();
        }
    }

    public static boolean onVanillaWindowTitle(long window, CharSequence title) {
        INSTANCE.vanillaWindowTitle = title.toString();
        return INSTANCE.publishWindowTitle(window, true);
    }

    public static boolean isAgentControlActive() {
        return INSTANCE.controlPresence.agentControlled();
    }

    public static void ensureExclusiveOwnerState() {
        if (INSTANCE.minecraft != null) INSTANCE.refreshInputContext(INSTANCE.minecraft);
        if (INSTANCE.controlPresence.agentControlled() && !INSTANCE.lastTakeover)
            INSTANCE.controlPresenceChanged(INSTANCE.controlPresence);
    }

    public static void beforeClientInput(Minecraft client) {
        INSTANCE.minecraft = client;
        INSTANCE.refreshInputContext(client);
    }

    public static void beforePlayerInput() {
        beforeClientInput(Minecraft.getInstance());
    }

    private void refreshInputContext(Minecraft client) {
        var connection = client.getConnection();
        boolean connected = connection != null && connection.getConnection().isConnected();
        var context =
                this.inputContext.observe(
                        connected ? client.level : null,
                        connected ? client.player : null,
                        connected ? connection : null);
        this.refreshNativeEntityAttackContext(client, context, connection, connected);
        ProbeTransport current = this.transport;
        if (current == null || context == this.publishedInputContext) return;
        current.nativeInputContextChanged(context);
        this.discardContextInput(client);
        this.publishedInputContext = context;
    }

    private void refreshNativeEntityAttackContext(
            Minecraft client,
            io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch.Snapshot context,
            Object connection,
            boolean connected) {
        long controlGeneration = this.controlPresence.transitionSequence();
        if (context == this.nativeEntityAttackContext
                && controlGeneration == this.nativeEntityAttackControlGeneration) return;
        this.nativeEntityAttackCount = 0;
        this.nativeEntityAttackContext = context;
        this.nativeEntityAttackControlGeneration = controlGeneration;
        this.nativeEntityAttackWorld = connected ? client.level : null;
        this.nativeEntityAttackPlayer = connected ? client.player : null;
        this.nativeEntityAttackConnection = connected ? connection : null;
    }

    private JsonObject discardContextInput(Minecraft client) {
        int releasedKeys = this.pressedKeys.size(), releasedButtons = this.pressedButtons.size();
        this.worldTargeting.end();
        this.finishNativeInputCleanup("native_input_context_changed");
        for (var mapping : client.options.keyMappings) {
            var access =
                    (io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor)
                            mapping;
            var key = access.minecraftProtocolProbe$key();
            boolean ownedKey =
                    key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD
                            && this.pressedKeys.containsKey(
                                    NativeInputMapping.wireKey(key.getValue()));
            boolean ownedButton =
                    key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                            && this.pressedButtons.stream()
                                    .anyMatch(
                                            button ->
                                                    NativeInputMapping.button(button)
                                                            == key.getValue());
            if (ownedKey || ownedButton || mapping == this.commandChatOpening)
                access.minecraftProtocolProbe$release();
        }
        // Do not dispatch an old Screen's mouse-up into its replacement.
        if (releasedButtons > 0) this.clearMouseButtonState(client);
        this.pressedKeys.clear();
        this.pressedButtons.clear();
        this.logicalPointer.releaseButtons();
        this.buttonScreens.clear();
        this.buttonFrames.clear();
        this.commandChatOpening = null;
        this.commandChat.clear();
        this.commandFeedback.clear();
        JsonObject result = base("input.release_all");
        result.addProperty("reason", "native_input_context_changed");
        result.addProperty("releasedKeys", releasedKeys);
        result.addProperty("releasedButtons", releasedButtons);
        return result;
    }

    public static boolean onVanillaWindowIcon(long window, SDL_Surface icon) {
        if (INSTANCE.cacheVanillaIcons(icon)
                && INSTANCE.controlPresence.agentControlled()
                && !INSTANCE.windowClosing.get()
                && INSTANCE.publishWindowIcons(window, true)) return true;
        boolean applied = SDLVideo.SDL_SetWindowIcon(window, icon);
        if (applied) INSTANCE.iconBadgeApplied = false;
        return applied;
    }

    /** Drain while Minecraft resources and the Loader class loader still exist. */
    public static void beforeClientClose() {
        beforeWindowClose();
        INSTANCE.shutdown();
    }

    public static void beforeWindowClose() {
        if (!INSTANCE.windowClosing.compareAndSet(false, true)) return;
        Minecraft client = INSTANCE.minecraft;
        if (client != null) {
            INSTANCE.releaseHostCursor();
            INSTANCE.restoreControlPresentation(client);
        }
        INSTANCE.evidenceCaptures.close();
    }

    public static void beginContentFrame() {
        INSTANCE.contentFrameReady = false;
    }

    public static void contentRendered() {
        INSTANCE.contentFrameReady = true;
    }

    public static void beforePresent() {
        INSTANCE.presentOperatorChrome();
    }

    @Override
    public void attachControlSession(AgentControlSession session) {
        this.controlSession = session;
        this.controlPresence = session.snapshot();
    }

    @Override
    public void attachPlayerCommandGate(
            io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate gate) {
        this.commandGate = gate;
    }

    @Override
    public void controlPresenceChanged(AgentControlSession.Snapshot snapshot) {
        this.controlPresence = snapshot;
        Minecraft current = this.minecraft;
        if (current == null || this.windowClosing.get()) return;
        Runnable update =
                () -> {
                    if (this.windowClosing.get()
                            || snapshot.transitionSequence()
                                    != this.controlPresence.transitionSequence()) return;
                    boolean takeover = snapshot.agentControlled();
                    if (takeover != this.lastTakeover) {
                        if (takeover) {
                            net.minecraft.client.KeyMapping.releaseAll();
                            MouseHandlerAccessor mouse =
                                    (MouseHandlerAccessor) current.mouseHandler;
                            mouse.minecraftProtocolProbe$setLeftPressed(false);
                            mouse.minecraftProtocolProbe$setRightPressed(false);
                            mouse.minecraftProtocolProbe$setMiddlePressed(false);
                            mouse.minecraftProtocolProbe$setActiveButton(null);
                            mouse.minecraftProtocolProbe$setMousePressedTime(0);
                        }
                        if (takeover) {
                            this.logicalPointer.reset();
                            this.logicalPointer.beginGameControl(current.gui.screen() == null);
                        } else
                            this.logicalPointer.plane(
                                    io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.Plane
                                            .HIDDEN,
                                    0,
                                    0);
                        this.agentPointerInitialized = false;
                        this.pointerScreenRevision = -1;
                        this.releaseHostCursor();
                        this.lastTakeover = takeover;
                    }
                    this.syncPointerContext(current);
                    this.applyControlPresentation(current);
                };
        if (current.isSameThread()) update.run();
        else current.execute(update);
    }

    private boolean hostFocused() {
        Minecraft client = this.minecraft;
        return client != null && !this.windowClosing.get() && client.getWindow().isFocused();
    }

    private boolean gameplayViewport() {
        Minecraft client = this.minecraft;
        return client != null
                && client.level != null
                && client.player != null
                && client.gui.screen() == null
                && client.gui.overlay() == null;
    }

    private boolean handleNativeEscape() {
        ProbeTransport current = this.transport;
        if (current == null || !this.controlPresence.agentControlled()) return false;
        if (!current.revokeHumanControl()) return false;
        this.nativeRevocations.incrementAndGet();
        this.releaseHostCursor();
        return true;
    }

    private void releaseHostCursor() {
        Minecraft client = this.minecraft;
        if (client == null || this.windowClosing.get()) return;
        long window = client.getWindow().handle();
        // Vanilla releaseMouse warps the host cursor to the window centre. Release
        // capture without moving the user's pointer, including on focus loss.
        if (SDLMouse.SDL_GetWindowRelativeMouseMode(window)) {
            SDLMouse.SDL_SetWindowRelativeMouseMode(window, false);
        }
        if (client.mouseHandler instanceof MouseHandlerAccessor accessor) {
            accessor.minecraftProtocolProbe$setMouseGrabbed(false);
            accessor.minecraftProtocolProbe$setAccumulatedDX(0);
            accessor.minecraftProtocolProbe$setAccumulatedDY(0);
        }
    }

    private void requestGameCapture(boolean captured) {
        Minecraft client = this.minecraft;
        if (client == null || this.windowClosing.get() || !client.isSameThread()) return;
        this.logicalPointer.viewport(
                client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        if (this.logicalPointer.gameCapture(
                captured,
                client.getWindow().getGuiScaledWidth() / 2.0,
                client.getWindow().getGuiScaledHeight() / 2.0))
            this.gameCursorChanged(client, captured ? "game_capture" : "game_release");
    }

    private void gameCursorChanged(Minecraft client, String reason) {
        this.pointerScreenRevision = -1;
        this.syncPointerContext(client);
        ProbeTransport current = this.transport;
        if (current == null) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "control.pointer.game_request");
        event.addProperty("category", "control");
        event.addProperty("operatorMetadata", true);
        event.addProperty("inputOrigin", "GAME");
        event.addProperty("reason", reason);
        event.addProperty("gameCursorRevision", this.logicalPointer.gameCursorRevision());
        event.addProperty("captured", this.logicalPointer.gameCaptured());
        event.addProperty("visible", this.logicalPointer.gameCursorVisible());
        event.addProperty("x", this.logicalPointer.x());
        event.addProperty("y", this.logicalPointer.y());
        current.broadcast(event);
    }

    private void enforceAgentMousePolicy(Minecraft client) {
        // Grab/warp is blocked at entry; no per-tick grab/release fight.
        this.syncPointerContext(client);
    }

    private void syncPointerContext(Minecraft client) {
        if (!this.controlPresence.agentControlled()) return;
        this.refreshScreen(client);
        boolean gui = client.gui.screen() != null;
        var plane =
                gui
                        ? io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.Plane
                                .GUI_ABSOLUTE
                        : io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.Plane
                                .GAMEPLAY_RELATIVE;
        String viewport =
                client.getWindow().getScreenWidth()
                        + ":"
                        + client.getWindow().getScreenHeight()
                        + ":"
                        + client.getWindow().getGuiScaledWidth()
                        + ":"
                        + client.getWindow().getGuiScaledHeight()
                        + ":"
                        + client.getWindow().getGuiScale();
        if (this.logicalPointer.plane() != plane
                || this.pointerScreenRevision != this.screenRevision
                || !this.pointerViewport.equals(viewport)) {
            this.pointerViewport = viewport;
            this.logicalPointer.viewport(
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight());
            this.logicalPointer.plane(
                    plane,
                    client.getWindow().getGuiScaledWidth() / 2.0,
                    client.getWindow().getGuiScaledHeight() / 2.0);
            this.pointerScreenRevision = this.screenRevision;
            this.agentPointerX =
                    gui
                            ? this.logicalPointer.x()
                                    * client.getWindow().getScreenWidth()
                                    / client.getWindow().getGuiScaledWidth()
                            : client.getWindow().getScreenWidth() / 2.0;
            this.agentPointerY =
                    gui
                            ? this.logicalPointer.y()
                                    * client.getWindow().getScreenHeight()
                                    / client.getWindow().getGuiScaledHeight()
                            : client.getWindow().getScreenHeight() / 2.0;
            this.agentPointerInitialized = true;
            MouseHandlerAccessor mouse = (MouseHandlerAccessor) client.mouseHandler;
            mouse.minecraftProtocolProbe$setXpos(this.agentPointerX);
            mouse.minecraftProtocolProbe$setYpos(this.agentPointerY);
            mouse.minecraftProtocolProbe$setAccumulatedDX(0);
            mouse.minecraftProtocolProbe$setAccumulatedDY(0);
            mouse.minecraftProtocolProbe$setIgnoreFirstMove(false);
        }
    }

    private boolean agentPresent() {
        ProbeTransport current = this.transport;
        return !this.windowClosing.get()
                && (this.controlPresence.agentControlled()
                        || this.controlSession.hasOperateWork()
                        || current != null && current.agentPresent());
    }

    private void applyControlPresentation(Minecraft client) {
        if (this.windowClosing.get()) return;
        long window = client.getWindow().handle();
        if (this.controlPresence.agentControlled()) {
            if (!this.controlChromeApplied) {
                this.originalPauseOnLostFocus = client.options.pauseOnLostFocus;
                client.options.pauseOnLostFocus = false;
                this.publishWindowIcons(window, true);
                this.controlChromeApplied = true;
            }
        } else if (this.controlChromeApplied) {
            restoreControlPresentation(client);
        }
        this.publishWindowTitle(window, true);
    }

    private void restoreControlPresentation(Minecraft client) {
        long window = client.getWindow().handle();
        this.publishWindowTitle(window, false);
        if (this.iconBadgeApplied) this.publishWindowIcons(window, false);
        this.controlChromeApplied = false;
        if (this.originalPauseOnLostFocus != null)
            client.options.pauseOnLostFocus = this.originalPauseOnLostFocus;
        this.originalPauseOnLostFocus = null;
    }

    private boolean publishWindowTitle(long window, boolean decorate) {
        String desired =
                io.github.recrivenvi.minecraftprotocol.safety.WindowDecoration.title(
                        this.vanillaWindowTitle,
                        this.controlPresence.mode(),
                        decorate && this.agentPresent());
        if (desired.equals(this.appliedWindowTitle)) return true;
        if (!SDLVideo.SDL_SetWindowTitle(window, desired)) {
            this.windowDecorationError = "title_application_failed";
            return false;
        }
        this.appliedWindowTitle = desired;
        return true;
    }

    private record WindowIcon(int width, int height, int format, int pitch, byte[] rgba) {}

    private boolean cacheVanillaIcons(SDL_Surface icon) {
        this.originalWindowIcons = java.util.List.of();
        this.decoratedWindowIcons = java.util.List.of();
        if (icon == null) return false;
        org.lwjgl.PointerBuffer images = SDLSurface.SDL_GetSurfaceImages(icon);
        if (images == null) return false;
        try {
            if (images.remaining() < 1 || images.remaining() > 16) return false;
            java.util.List<WindowIcon> originals = new java.util.ArrayList<>();
            java.util.List<WindowIcon> decorated = new java.util.ArrayList<>();
            for (int index = images.position(); index < images.limit(); index++) {
                SDL_Surface source = SDL_Surface.create(images.get(index));
                int width = source.w(), height = source.h(), pitch = source.pitch();
                if (width < 1
                        || height < 1
                        || width > 512
                        || height > 512
                        || pitch < 1
                        || pitch > 4096) return false;
                if (!SDLSurface.SDL_LockSurface(source)) return false;
                try {
                    byte[] pixels = new byte[pitch * height];
                    source.pixels().get(pixels);
                    originals.add(new WindowIcon(width, height, source.format(), pitch, pixels));
                } finally {
                    SDLSurface.SDL_UnlockSurface(source);
                }
                SDL_Surface converted =
                        SDLSurface.SDL_ConvertSurface(source, SDLPixels.SDL_PIXELFORMAT_RGBA32);
                if (converted == null) return false;
                try {
                    if (!SDLSurface.SDL_LockSurface(converted)) return false;
                    try {
                        byte[] rgba = new byte[width * height * 4];
                        ByteBuffer pixels = converted.pixels();
                        for (int y = 0; y < height; y++)
                            pixels.get(y * converted.pitch(), rgba, y * width * 4, width * 4);
                        byte[] badged =
                                io.github.recrivenvi.minecraftprotocol.safety.WindowDecoration.icon(
                                        width, height, rgba);
                        decorated.add(
                                new WindowIcon(
                                        width,
                                        height,
                                        SDLPixels.SDL_PIXELFORMAT_RGBA32,
                                        width * 4,
                                        badged));
                    } finally {
                        SDLSurface.SDL_UnlockSurface(converted);
                    }
                } finally {
                    SDLSurface.SDL_DestroySurface(converted);
                }
            }
            this.originalWindowIcons = java.util.List.copyOf(originals);
            this.decoratedWindowIcons = java.util.List.copyOf(decorated);
            this.windowDecorationError = "";
            return true;
        } finally {
            org.lwjgl.sdl.SDLStdinc.SDL_free(images);
        }
    }

    private boolean publishWindowIcons(long window, boolean decorate) {
        java.util.List<WindowIcon> images =
                decorate ? this.decoratedWindowIcons : this.originalWindowIcons;
        if (images.isEmpty()) return false;
        java.util.List<ByteBuffer> allocated = new java.util.ArrayList<>();
        java.util.List<SDL_Surface> surfaces = new java.util.ArrayList<>();
        try {
            for (WindowIcon image : images) {
                ByteBuffer pixels = MemoryUtil.memAlloc(image.rgba().length);
                allocated.add(pixels);
                pixels.put(image.rgba()).flip();
                SDL_Surface surface =
                        SDLSurface.SDL_CreateSurfaceFrom(
                                image.width(),
                                image.height(),
                                image.format(),
                                pixels,
                                image.pitch());
                if (surface == null) throw new IllegalStateException("surface_allocation_failed");
                surfaces.add(surface);
                if (surfaces.size() > 1
                        && !SDLSurface.SDL_AddSurfaceAlternateImage(surfaces.getFirst(), surface))
                    throw new IllegalStateException("alternate_icon_failed");
            }
            if (!SDLVideo.SDL_SetWindowIcon(window, surfaces.getFirst()))
                throw new IllegalStateException("icon_application_failed");
            this.iconBadgeApplied = decorate;
            this.windowDecorationError = "";
            return true;
        } catch (RuntimeException failure) {
            this.windowDecorationError = failure.getMessage();
            return false;
        } finally {
            for (int i = surfaces.size() - 1; i >= 0; i--)
                SDLSurface.SDL_DestroySurface(surfaces.get(i));
            allocated.forEach(MemoryUtil::memFree);
        }
    }

    private void presentOperatorChrome() {
        Minecraft client = this.minecraft;
        if (client == null || this.windowClosing.get() || !this.contentFrameReady) return;
        this.contentFrameReady = false;
        this.evidenceCaptures.beforeOperatorChrome(this::captureContentAtBoundary);
        // Initial reload has not installed text shader sources yet; compiling here can crash.
        if (client.gui.overlay() instanceof net.minecraft.client.gui.screens.LoadingOverlay) return;
        float alpha =
                this.operatorChrome.update(
                        this.controlPresence.mode(),
                        this.agentPresent(),
                        client.gui.screen() != null && this.logicalPointer.gameCursorVisible(),
                        System.nanoTime());
        if (alpha < 0.02F) return;
        this.renderingOperatorChrome = true;
        try {
            var state = client.gameRenderer.gameRenderState().guiRenderState;
            state.reset();
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, 0, 0);
            renderChrome(graphics, alpha);
            var accessor =
                    (io.github.recrivenvi.minecraftprotocol.probe.mixin.GameRendererAccessor)
                            client.gameRenderer;
            accessor.minecraftProtocolProbe$guiRenderer().render();
            this.controlChromeRenderSequence.incrementAndGet();
        } finally {
            this.renderingOperatorChrome = false;
        }
    }

    private void renderChrome(GuiGraphicsExtractor graphics, float alpha) {
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.edges(
                graphics::fill,
                width,
                height,
                this.operatorChrome.edgeAlpha(),
                this.operatorChrome.edgeWidth(),
                this.minecraft.getWindow().getGuiScale());
        int textWidth = Math.round(this.operatorChrome.textWidth(this.minecraft.font::width));
        int barWidth = textWidth + 20, barHeight = 23;
        int left = Math.max(4, (width - barWidth) / 2), top = 8;
        io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.panel(
                graphics::fill, left, top, barWidth, barHeight, alpha);
        this.operatorChrome.text(
                new io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.TextRenderer() {
                    public void enableScissor(int x0, int y0, int x1, int y1) {
                        graphics.enableScissor(x0, y0, x1, y1);
                    }

                    public void disableScissor() {
                        graphics.disableScissor();
                    }

                    public void text(String message, int x, int y, int color) {
                        graphics.text(
                                NeoForgeProbeRuntime.this.minecraft.font,
                                Component.literal(message),
                                x,
                                y,
                                color);
                    }
                },
                left,
                top,
                barWidth,
                barHeight,
                alpha);
        if (this.operatorChrome.pointerAlpha() > 0.01F && this.logicalPointer.hasVisualPosition()) {
            var windowState = this.minecraft.gameRenderer.gameRenderState().windowRenderState;
            io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.pointer(
                    graphics::fill,
                    new io.github.recrivenvi.minecraftprotocol.safety.ControlChrome
                            .PointerTransform() {
                        public void push(double x, double y) {
                            graphics.pose().pushMatrix();
                            graphics.pose().translate((float) x, (float) y);
                        }

                        public void pop() {
                            graphics.pose().popMatrix();
                        }
                    },
                    this.logicalPointer.x(),
                    this.logicalPointer.y(),
                    width,
                    height,
                    windowState.width / (float) windowState.guiScale,
                    windowState.height / (float) windowState.guiScale,
                    this.operatorChrome.pointerAlpha(),
                    this.logicalPointer.pressScale(System.nanoTime()));
        }
    }

    private void addControlPresence(JsonObject json) {
        AgentControlSession.Snapshot snapshot = this.controlPresence;
        json.addProperty("controlState", snapshot.state().name());
        json.addProperty("reconsentRequired", snapshot.reconsentRequired());
        json.addProperty("reconsentScope", "TAKEOVER_ONLY");
        json.addProperty("controlTransitionSequence", snapshot.transitionSequence());
        json.addProperty("mode", snapshot.mode().name());
        json.addProperty("takeoverActive", snapshot.agentControlled());
        json.addProperty("modeTransitionReason", snapshot.reason());
        JsonObject version = new JsonObject();
        version.addProperty("controlSessionId", snapshot.controlSessionId());
        version.addProperty("generation", snapshot.transitionSequence());
        json.add("modeVersion", version);
        json.addProperty("modeContract", "explicit_intent_separate_authorization_v1");
        json.addProperty("controlChromeRenderedFrames", this.controlChromeRenderSequence.get());
        json.addProperty("controlChromeAlpha", this.operatorChrome.alpha());
        json.addProperty("controlChromeEdgeAlpha", this.operatorChrome.edgeAlpha());
        json.addProperty("controlChromeEdgeWidth", this.operatorChrome.edgeWidth());
        json.addProperty("operatorChromeLayer", "after_content_capture_before_present");
        json.addProperty("captureExcludesOperatorChrome", true);
        json.addProperty("captureFrameSequence", this.evidenceCaptures.frameSequence());
        json.addProperty("capturePending", this.evidenceCaptures.pendingCount());
        json.addProperty("captureReadbacks", this.evidenceCaptures.readbacks());
        json.addProperty("nativeRevocations", this.nativeRevocations.get());
        json.addProperty("nativeCaptureGrants", 0);
        json.addProperty("nativeInputPolicy", "exclusive_except_physical_escape");
        JsonObject suppressed = new JsonObject();
        for (AgentInputContext.Kind kind : AgentInputContext.Kind.values())
            suppressed.addProperty(
                    kind.name().toLowerCase(java.util.Locale.ROOT),
                    this.suppressedNative.get(kind.ordinal()));
        json.add("suppressedNativeInput", suppressed);
        json.addProperty("presenceActive", this.agentPresent());
        json.addProperty(
                "presenceBasis", "bounded_task_or_activity_grace_or_observation_work_or_takeover");
        json.addProperty("presenceInactivityTimeoutMs", 15000);
        json.addProperty("hostCursorPolicy", "never_capture_or_warp_during_takeover");
        json.addProperty("activeGestureId", this.activeGestureId);
        json.addProperty(
                "pointerOwnership",
                !this.activeGestureId.isEmpty()
                        ? "serialized_gesture"
                        : !this.pressedButtons.isEmpty() ? "lease_raw_stream" : "none");
        JsonObject pointer = new JsonObject();
        pointer.addProperty("plane", this.logicalPointer.plane().name());
        pointer.addProperty("x", this.logicalPointer.x());
        pointer.addProperty("y", this.logicalPointer.y());
        pointer.addProperty("alpha", this.operatorChrome.pointerAlpha());
        pointer.addProperty("gameCaptured", this.logicalPointer.gameCaptured());
        pointer.addProperty("gameCursorVisible", this.logicalPointer.gameCursorVisible());
        pointer.addProperty("gameCursorRevision", this.logicalPointer.gameCursorRevision());
        pointer.addProperty("evidencePixels", false);
        json.add("agentPointer", pointer);
        json.addProperty("rejectedHostGrabs", this.rejectedHostGrabs.get());
        json.addProperty("hostFocused", this.hostFocused());
        json.addProperty("hostCursorCaptureGranted", false);
        Minecraft client = this.minecraft;
        if (client != null && !this.windowClosing.get())
            json.addProperty(
                    "hostCursorCaptured",
                    SDLMouse.SDL_GetWindowRelativeMouseMode(client.getWindow().handle()));
        json.addProperty(
                "windowIconState",
                this.iconBadgeApplied ? "original_with_agent_pointer" : "original_minecraft");
        json.addProperty("originalWindowIconCount", this.originalWindowIcons.size());
        json.addProperty("windowBaseTitle", this.vanillaWindowTitle);
        json.addProperty("windowTitle", this.appliedWindowTitle);
        json.addProperty("windowDecorationError", this.windowDecorationError);
        if (snapshot.manuallyRevoked()) {
            json.addProperty("controlMessage", snapshot.message());
            json.addProperty("manualRevocationReason", "human_manual_revocation");
        }
    }

    public static void observeScreenSlotClick(int slotId, int mouseButton, String input) {
        INSTANCE.screenSlotClickSequence.incrementAndGet();
        INSTANCE.lastTraceDetail =
                "screen slot=" + slotId + " button=" + mouseButton + " input=" + input;
    }

    public static void observeMenuDispatch(int containerId, int slotId, String input) {
        INSTANCE.menuDispatchSequence.incrementAndGet();
        INSTANCE.lastTraceDetail =
                "menu container=" + containerId + " slot=" + slotId + " input=" + input;
    }

    public static void observeContainerPacket(String packetClass) {
        INSTANCE.containerPacketSequence.incrementAndGet();
        INSTANCE.lastTraceDetail = "client packet=" + packetClass;
    }

    public static void observeServerContainerPacket(String packetClass) {
        INSTANCE.serverContainerPacketSequence.incrementAndGet();
        INSTANCE.lastTraceDetail = "server packet=" + packetClass;
        INSTANCE.lastServerThread = Thread.currentThread().getName();
    }

    public static void observeRenderFact(
            String category, String stateClass, int x, int y, int width, int height) {
        if (INSTANCE.renderingOperatorChrome) return;
        switch (category) {
            case "element" -> INSTANCE.renderElementSequence.incrementAndGet();
            case "item" -> INSTANCE.renderItemSequence.incrementAndGet();
            case "text" -> INSTANCE.renderTextSequence.incrementAndGet();
            case "picture" -> INSTANCE.renderPictureSequence.incrementAndGet();
            default -> {}
        }
        INSTANCE.recordRenderFact(category, stateClass, x, y, width, height);
    }

    private void tick(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.enforceAgentMousePolicy(minecraft);
        this.applyControlPresentation(minecraft);
        DedicatedPeerClient.tick(minecraft);
        NativeCommandSuggestions.tick(minecraft);
        this.clientTick++;
        if (Boolean.getBoolean("minecraft.protocol.probe.disablePauseOnLostFocus")) {
            minecraft.options.pauseOnLostFocus = false;
        }
        if (this.initialized.compareAndSet(false, true)) {
            this.transport =
                    ProbeTransport.forInstance(
                            this, minecraft.gameDirectory.toPath(), "26.3-neoforge");
            this.refreshInputContext(minecraft);
            this.transport.startAsync();
            if (this.shutdownRegistered.compareAndSet(false, true)) {
                Runtime.getRuntime()
                        .addShutdownHook(new Thread(this::shutdown, "minecraft-protocol-shutdown"));
            }
        }

        this.refreshScreen(minecraft);
        this.refreshMenu(minecraft);
        this.refreshInputContext(minecraft);
        if (this.transport != null
                && this.publishedInputContext != null
                && this.publishedInputContext.worldAvailable()) {
            var player = minecraft.player;
            var velocity = player.getDeltaMovement();
            this.transport.publishPlayerState(
                    this.publishedInputContext,
                    new io.github.recrivenvi.minecraftprotocol.core.PlayerStateStream.Sample(
                            this.clientTick,
                            System.currentTimeMillis(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            player.getYRot(),
                            player.getXRot(),
                            player.onGround(),
                            velocity.x,
                            velocity.y,
                            velocity.z));
        }
        MinecraftServer singleplayerServer = minecraft.getSingleplayerServer();
        this.phase9a.observeStorageLifecycle(
                minecraft.level,
                singleplayerServer != null && singleplayerServer.isCurrentlySaving(),
                minecraft.level == null
                        && singleplayerServer == null
                        && minecraft.getConnection() == null);
        if (minecraft.level != null
                && minecraft.getSingleplayerServer() != null
                && this.clientTick % 20L == 0L) {
            JsonObject storageQuery = new JsonObject();
            storageQuery.addProperty("domain", "world");
            this.onIntegratedServer(
                    (server, player) -> {
                        this.phase9a.rememberStorageContext(
                                this.phase9a.storageRequest(server, player, storageQuery));
                        return null;
                    });
        }
    }

    private void shutdown() {
        if (!this.shutdownStarted.compareAndSet(false, true)) return;
        NativeCommandSuggestions.close(this.minecraft);
        this.evidenceCaptures.close();
        this.phase9a.observeStorageShutdown();
        ProbeTransport current = this.transport;
        if (current != null) current.close();
        this.regionPages.close();
        this.phase9a.close();
    }

    @Override
    public CompletableFuture<JsonObject> session() {
        return this.onClient(
                () -> {
                    Minecraft client = requireClient();
                    Screen screen = this.refreshScreen(client);
                    this.refreshMenu(client);
                    JsonObject json = base("session");
                    json.addProperty("target", "26.3-neoforge");
                    json.addProperty("minecraft", "26.3");
                    json.addProperty("loader", "neoforge");
                    json.addProperty("inWorld", client.level != null && client.player != null);
                    json.addProperty(
                            "screenClass", screen == null ? "" : screen.getClass().getName());
                    json.addProperty("screenRevision", this.screenRevision);
                    json.addProperty("menuRevision", this.menuRevision);
                    json.addProperty("thread", Thread.currentThread().getName());
                    this.addControlPresence(json);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> capabilities() {
        return this.onClient(
                () -> {
                    JsonObject json = base("capabilities");
                    json.add(
                            "uiContract",
                            io.github.recrivenvi.minecraftprotocol.safety.UiDataView.contract());
                    JsonObject capabilities = new JsonObject();
                    json.add(
                            "worldContract",
                            io.github.recrivenvi.minecraftprotocol.safety.WorldDataView.contract());
                    capabilities.addProperty("ui.interaction_tree", "runtime_verified");
                    capabilities.addProperty("ui.native_containers", "recursive_bounded");
                    capabilities.addProperty("ui.list_items", "native_clipped_bounds");
                    capabilities.addProperty("ui.text_input", "game_routed_unicode");
                    capabilities.addProperty("ui.slider_value", "game_routed_normalized");
                    capabilities.addProperty("ui.render_only_controls", "partial_typed_adapters");
                    capabilities.addProperty("ui.selector", "runtime_verified");
                    capabilities.addProperty("ui.coordinate_generation", "runtime_verified");
                    capabilities.addProperty("ui.vision_fallback", "runtime_verified");
                    capabilities.addProperty("ui.render_facts", "runtime_verified");
                    capabilities.addProperty("input.game_routed_raw", "runtime_hooked");
                    capabilities.addProperty("input.game_routed_screen", "path_observable");
                    capabilities.addProperty("input.game_routed_keymapping", "path_observable");
                    capabilities.addProperty(
                            "input.context_epoch", "native_world_player_connection");
                    capabilities.addProperty("world.user_region", "bounded_cached_section_pages");
                    capabilities.addProperty(
                            "event.player_state", "explicit_client_tick_subscription");
                    capabilities.addProperty("capture.composite", "gpu_abstraction_callback");
                    capabilities.addProperty("capture.opengl", "target_verified");
                    capabilities.addProperty("capture.vulkan", "target_verified");
                    capabilities.addProperty("input.pipeline", "runtime_verified");
                    capabilities.addProperty(
                            "control.agent_presence",
                            this.evidenceCaptures.frameSequence() > 0
                                    ? "runtime_verified"
                                    : "unverified_until_present");
                    capabilities.addProperty(
                            "control.native_escape_revoke",
                            this.nativeRevocations.get() > 0
                                    ? "runtime_verified"
                                    : "unverified_until_native_escape");
                    // Supported standard-path restriction, not a current capture/grant or
                    // human-verification claim.
                    capabilities.addProperty(
                            "input.host_cursor_capture", "blocked_during_takeover");
                    capabilities.addProperty("input.multi_key", "runtime_verified");
                    capabilities.addProperty("input.drag_scroll", "runtime_verified");
                    capabilities.addProperty(
                            "command.player.execute", "normal_network_current_permissions");
                    capabilities.addProperty(
                            "command.player.native", "native_current_player_without_input_lease");
                    capabilities.addProperty(
                            "command.player.configuration",
                            "native_command_blocks_and_jigsaw_final_state");
                    capabilities.addProperty(
                            "command.player.jigsaw_generation",
                            "unavailable_unresolved_template_effects");
                    capabilities.addProperty(
                            "command.player.structure_configuration",
                            "native_parameter_update_and_scan");
                    capabilities.addProperty(
                            "command.player.structure_template",
                            "unavailable_storage_or_unresolved_template_effects");
                    capabilities.addProperty("wait.assert", "runtime_verified");
                    capabilities.addProperty(
                            "fixture.standard_mod_gui", "runtime_verified_contaminated");
                    capabilities.addProperty(
                            "ui.standard_mod_gui_extended", "runtime_verified_fixture");
                    capabilities.addProperty("diagnostics.hook_manifest", "runtime_self_test");
                    capabilities.addProperty(
                            "input.virtual_pointer", "gui_absolute_gameplay_relative");
                    capabilities.addProperty(
                            "input.exclusive_takeover", "standard_native_callbacks_and_polling");
                    capabilities.addProperty("input.gesture_queue", "serialized_bounded_16");
                    capabilities.addProperty(
                            "input.origin", "single_use_event_ticket_and_scheduled_native_origin");
                    capabilities.addProperty(
                            "operator.chrome", "pixel_top_center_three_intentions");
                    capabilities.addProperty("world.client_live", "runtime_verified_when_in_world");
                    capabilities.addProperty(
                            "server.integrated_authoritative", "runtime_verified_when_integrated");
                    capabilities.addProperty("state.frame", "runtime_verified");
                    capabilities.addProperty("provider.read_spi", "available_live_only");
                    capabilities.addProperty("storage.persistent", "unavailable");
                    capabilities.addProperty(
                            "storage.persistent.read",
                            "runtime_verified_bounded_requires_storage.read");
                    capabilities.addProperty("storage.persistent.write", "unavailable");
                    capabilities.addProperty("server.peer.transport", "optional_peer_v0");
                    capabilities.addProperty("server.peer.read", "runtime_negotiated");
                    capabilities.addProperty(
                            "server.peer.fixture", "server_flag_and_operator_gated");
                    capabilities.addProperty(
                            "server.peer.debug", "server_flag_operator_scope_and_arm_gated");
                    capabilities.addProperty("capture.input_concurrent", "runtime_verified");
                    capabilities.addProperty("recording.basic", "available_bounded_async");
                    capabilities.addProperty("artifact.bundle", "available_experimental_v0");
                    capabilities.addProperty("fixture.player.teleport", "scope_gated");
                    capabilities.addProperty("debug.arm", "scope_gated_disabled_by_default");
                    capabilities.addProperty("debug.player.health", "scope_and_arm_gated");
                    capabilities.addProperty("debug.world.block", "scope_and_arm_gated");
                    json.add("capabilities", capabilities);
                    json.add("serverPeer", DedicatedPeerClient.status());
                    this.addControlPresence(json);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> readiness() {
        return this.onClient(
                () -> {
                    Minecraft client = requireClient();
                    JsonObject json = base("readiness");
                    JsonObject hooks = new JsonObject();
                    boolean clientTickHook = this.clientTick > 0;
                    boolean mouseInvoker = client.mouseHandler instanceof MouseHandlerInvoker;
                    boolean keyboardInvoker =
                            client.keyboardHandler instanceof KeyboardHandlerInvoker;
                    boolean screenObservation = this.screenRevision > 0;
                    hooks.addProperty("clientTickHook", status(clientTickHook));
                    hooks.addProperty("mouseHandlerInvoker", status(mouseInvoker));
                    hooks.addProperty("keyboardHandlerInvoker", status(keyboardInvoker));
                    hooks.addProperty("screenObservation", status(screenObservation));
                    hooks.addProperty(
                            "containerScreenHook", observed(this.screenSlotClickSequence.get()));
                    hooks.addProperty(
                            "containerPacketHook", observed(this.containerPacketSequence.get()));
                    hooks.addProperty(
                            "serverValidationHook",
                            observed(this.serverContainerPacketSequence.get()));
                    hooks.addProperty(
                            "compositeCapture",
                            this.captureVerified.get()
                                    ? "runtime_verified"
                                    : "unverified_until_capture");
                    hooks.addProperty(
                            "renderFacts",
                            this.renderElementSequence.get() + this.renderTextSequence.get() > 0
                                    ? "runtime_verified"
                                    : "unverified_until_render");
                    json.add("hooks", hooks);
                    json.addProperty(
                            "overall",
                            clientTickHook && mouseInvoker && keyboardInvoker && screenObservation
                                    ? "ready"
                                    : "degraded");
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> hookManifest() {
        return this.onClient(
                () -> {
                    Minecraft client = requireClient();
                    boolean tick = this.clientTick > 0;
                    boolean mouse = client.mouseHandler instanceof MouseHandlerInvoker;
                    boolean keyboard = client.keyboardHandler instanceof KeyboardHandlerInvoker;
                    boolean screen = this.screenRevision > 0;
                    JsonArray hooks = new JsonArray();
                    hooks.add(
                            hook(
                                    "gui_completed_presentation_frame",
                                    "MIXIN_INJECT",
                                    "Gui.extractRenderState",
                                    "HEAD/RETURN",
                                    "read_observation",
                                    client.gui
                                                    instanceof
                                                    UiPresentationSnapshot.GuiPresentationSource
                                            ? "runtime_hooked"
                                            : "unavailable",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_crosshair_and_attack_pixels",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractCrosshair/extractItemHotbar",
                                    "blitSprite",
                                    "read_observation",
                                    "unverified_until_crosshair_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_camera_texture",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractTextureOverlay/extractConfusionOverlay/extractVignette",
                                    "blit",
                                    "read_observation",
                                    "unverified_until_camera_overlay_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_spyglass_and_mask",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractSpyglassOverlay",
                                    "blit/fill",
                                    "read_observation",
                                    "unverified_until_spyglass_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_portal_and_sleep",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractPortalOverlay/extractSleepOverlay",
                                    "blitSprite/fill",
                                    "read_observation",
                                    "unverified_until_portal_or_sleep_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_boss_native_sprites",
                                    "MIXIN_REDIRECT",
                                    "BossHealthOverlay.extractBar",
                                    "blitSprite",
                                    "read_observation",
                                    "unverified_until_boss_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "gui_inventory_effect_text",
                                    "MIXIN_REDIRECT",
                                    "EffectsInInventory.extractText",
                                    "text",
                                    "read_observation",
                                    "unverified_until_inventory_effect_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "gui_native_tooltip_text",
                                    "MIXIN_REDIRECT",
                                    "ClientTextTooltip.extractText",
                                    "text",
                                    "read_observation",
                                    "unverified_until_tooltip_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "gui_native_f3_text",
                                    "MIXIN_REDIRECT",
                                    "DebugScreenOverlay.extractLines",
                                    "text",
                                    "read_observation",
                                    "unverified_until_debug_text_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "gui_native_chat_text",
                                    "MIXIN_REDIRECT",
                                    "ChatComponent.DrawingBackgroundGraphicsAccess/DrawingFocusedGraphicsAccess.handleMessage",
                                    "ActiveTextCollector.accept",
                                    "read_observation",
                                    "unverified_until_chat_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "gui_native_subtitle_text",
                                    "MIXIN_REDIRECT",
                                    "SubtitleOverlay.extractRenderState",
                                    "text",
                                    "read_observation",
                                    "unverified_until_subtitle_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "gui_native_message_text",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractOverlayMessage/extractSavingIndicator/extractDemoOverlay",
                                    "textWithBackdrop",
                                    "read_observation",
                                    "unverified_until_native_message_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "toast_resident_draw_context",
                                    "MIXIN_REDIRECT",
                                    "ToastManager.ToastInstance.extractRenderState",
                                    "Toast.extractRenderState",
                                    "read_observation",
                                    "unverified_until_resident_toast_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "toast_friend_and_music_draw",
                                    "MIXIN_REDIRECT",
                                    "FriendToast.extractRenderState/NowPlayingToast.extractToast",
                                    "blitSprite/text",
                                    "read_observation",
                                    "unverified_until_friend_or_music_draw",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_title_lifecycle_revision",
                                    "MIXIN_INJECT",
                                    "Hud.setTitle/setSubtitle/setTimes/resetTitleTimes/clearTitles",
                                    "HEAD",
                                    "observe_title_lifecycle_generation",
                                    "unverified_until_native_setter",
                                    "ui.debug"));
                    hooks.add(
                            hook(
                                    "hud_item_presentation_frame",
                                    "MIXIN_INJECT",
                                    "Hud.extractRenderState",
                                    "HEAD/RETURN",
                                    "read_observation",
                                    client.gui.hud
                                                    instanceof
                                                    UiPresentationSnapshot.HudPresentationSource
                                            ? "runtime_hooked"
                                            : "unavailable",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_selected_item_name",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractSelectedItemName",
                                    "textWithBackdrop",
                                    "read_observation",
                                    "unverified_until_selected_name_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_item_durability_background",
                                    "MIXIN_REDIRECT",
                                    "GuiGraphicsExtractor.itemBar",
                                    "fill:0",
                                    "read_observation",
                                    "unverified_until_item_bar_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_item_durability_foreground",
                                    "MIXIN_REDIRECT",
                                    "GuiGraphicsExtractor.itemBar",
                                    "fill:1",
                                    "read_observation",
                                    "unverified_until_item_bar_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_item_cooldown",
                                    "MIXIN_REDIRECT",
                                    "GuiGraphicsExtractor.itemCooldown",
                                    "fill",
                                    "read_observation",
                                    "unverified_until_item_cooldown_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_experience_level",
                                    "MIXIN_REDIRECT",
                                    "Hud.extractExperienceLevel",
                                    "ContextualBar.extractExperienceLevel",
                                    "read_observation",
                                    "unverified_until_experience_level_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_contextual_background",
                                    "MIXIN_REDIRECT",
                                    "ExperienceBar/JumpableVehicleBar.extractBackground",
                                    "blitSprite:background",
                                    "read_observation",
                                    "unverified_until_contextual_bar_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_contextual_progress",
                                    "MIXIN_REDIRECT",
                                    "ExperienceBar/JumpableVehicleBar.extractBackground",
                                    "blitSprite:cropped",
                                    "read_observation",
                                    "unverified_until_contextual_bar_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_locator_background_arrow",
                                    "MIXIN_REDIRECT",
                                    "LocatorBar.extractBackground/lambda$extractRenderState$1",
                                    "blitSprite:backgroundOrArrow",
                                    "read_observation",
                                    "unverified_until_locator_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "hud_locator_marker",
                                    "MIXIN_REDIRECT",
                                    "LocatorBar.lambda$extractRenderState$1",
                                    "blitSprite:marker",
                                    "read_observation",
                                    "unverified_until_locator_marker_render",
                                    "ui.presentation"));
                    hooks.add(
                            hook(
                                    "world_placement_receipt",
                                    "MIXIN_INJECT",
                                    "MultiPlayerGameMode.useItemOn",
                                    "RETURN",
                                    "read_observation",
                                    "runtime_hooked",
                                    "world.action"));
                    hooks.add(
                            hook(
                                    "world_entity_attack_consumption",
                                    "MIXIN_INJECT",
                                    "MultiPlayerGameMode.attack",
                                    "RETURN",
                                    "read_observation",
                                    observed(this.nativeEntityAttackCount),
                                    "input.mouse"));
                    JsonObject scene = WorldPresentationSnapshot.capture();
                    hooks.add(
                            hook(
                                    "world_presentation_frame",
                                    "MIXIN_INJECT",
                                    "GameRenderer.render",
                                    "HEAD/TAIL",
                                    "read_observation",
                                    scene.get("available").getAsBoolean()
                                            ? "runtime_verified"
                                            : "unverified_until_world_render",
                                    "world.presentation"));
                    hooks.add(
                            hook(
                                    "world_target_outline",
                                    "MIXIN_INJECT",
                                    "LevelRenderer.submitHitOutline",
                                    "RETURN",
                                    "read_observation",
                                    scene.getAsJsonArray("nodes").isEmpty()
                                            ? "unverified_until_outline"
                                            : "runtime_verified",
                                    "world.presentation"));
                    hooks.add(
                            hook(
                                    "runtime_pre_loader_shutdown",
                                    "MIXIN_INJECT",
                                    "Minecraft.close",
                                    "HEAD",
                                    "bounded_runtime_drain",
                                    "unverified_until_shutdown",
                                    "runtime.lifecycle"));
                    hooks.add(
                            hook(
                                    "client_tick",
                                    "MIXIN_INJECT",
                                    "Minecraft.tick",
                                    "TAIL",
                                    "read_observation",
                                    status(tick),
                                    "system.session"));
                    hooks.add(
                            hook(
                                    "mouse_input",
                                    "MIXIN_INVOKER",
                                    "MouseHandler",
                                    "PRIVATE_INPUT_CALLBACK",
                                    "game_routed_input",
                                    status(mouse),
                                    "input.mouse"));
                    hooks.add(
                            hook(
                                    "keyboard_input",
                                    "MIXIN_INVOKER",
                                    "KeyboardHandler",
                                    "PRIVATE_INPUT_CALLBACK",
                                    "game_routed_input",
                                    status(keyboard),
                                    "input.key"));
                    hooks.add(
                            hook(
                                    "container_screen",
                                    "MIXIN_INJECT",
                                    "AbstractContainerScreen.slotClicked",
                                    "HEAD",
                                    "read_observation",
                                    observed(this.screenSlotClickSequence.get()),
                                    "ui.container_trace"));
                    hooks.add(
                            hook(
                                    "container_client_packet",
                                    "MIXIN_INJECT",
                                    "Connection.send",
                                    "HEAD",
                                    "read_observation",
                                    observed(this.containerPacketSequence.get()),
                                    "ui.container_trace"));
                    hooks.add(
                            hook(
                                    "container_server_validation",
                                    "MIXIN_INJECT",
                                    "ServerGamePacketListenerImpl.handleContainerClick",
                                    "HEAD",
                                    "read_observation",
                                    observed(this.serverContainerPacketSequence.get()),
                                    "ui.container_trace"));
                    hooks.add(
                            hook(
                                    "render_facts",
                                    "MIXIN_INJECT",
                                    "GuiRenderState.add*",
                                    "HEAD",
                                    "read_observation",
                                    this.renderElementSequence.get() + this.renderTextSequence.get()
                                                    > 0
                                            ? "runtime_verified"
                                            : "unverified_until_render",
                                    "ui.render_facts"));
                    hooks.add(
                            hook(
                                    "composite_capture",
                                    "RUNTIME_CALLBACK",
                                    "GpuTexture",
                                    "READBACK_CALLBACK",
                                    "capture",
                                    this.captureVerified.get()
                                            ? "runtime_verified"
                                            : "unverified_until_capture",
                                    "capture.composite"));
                    hooks.add(
                            operatorHook(
                                    "native_escape_revoke",
                                    "KeyboardHandler.keyPress",
                                    true,
                                    false,
                                    observed(this.nativeRevocations.get())));
                    hooks.add(
                            operatorHook(
                                    "exclusive_native_button",
                                    "MouseHandler.onButton",
                                    true,
                                    false,
                                    "configured_pending_human_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "host_grab_guard",
                                    "MouseHandler.grabMouse",
                                    true,
                                    false,
                                    status(mouse)));
                    hooks.add(
                            operatorHook(
                                    "host_release_no_warp",
                                    "MouseHandler.releaseMouse",
                                    true,
                                    false,
                                    status(mouse)));
                    hooks.add(
                            operatorHook(
                                    "virtual_mouse_grab",
                                    "MouseHandler.isMouseGrabbed",
                                    true,
                                    false,
                                    status(mouse)));
                    hooks.add(
                            operatorHook(
                                    "virtual_keymapping_consumption",
                                    "Minecraft.handleKeybinds",
                                    false,
                                    true,
                                    status(tick)));
                    hooks.add(
                            hook(
                                    "input_context_tick",
                                    "MIXIN_INJECT",
                                    "Minecraft.tick",
                                    "HEAD",
                                    "input_context_fence",
                                    status(tick),
                                    "input.context_epoch"));
                    hooks.add(
                            hook(
                                    "input_context_player",
                                    "MIXIN_INJECT",
                                    "LocalPlayer.aiStep",
                                    "HEAD",
                                    "input_context_fence",
                                    status(tick),
                                    "input.context_epoch"));
                    hooks.add(
                            operatorHook(
                                    "virtual_input_focus",
                                    "Minecraft.isWindowActive",
                                    true,
                                    false,
                                    status(tick)));
                    hooks.add(
                            operatorHook(
                                    "control_window_title",
                                    "Window.setTitle",
                                    false,
                                    true,
                                    status(tick)));
                    hooks.add(
                            operatorHook(
                                    "restore_actual_window_icons",
                                    "Window.setIcon",
                                    false,
                                    true,
                                    this.originalWindowIcons.isEmpty()
                                            ? "unverified_until_icon"
                                            : "runtime_verified"));
                    hooks.add(
                            operatorHook(
                                    "operator_chrome_final_present",
                                    "Minecraft.renderFrame",
                                    false,
                                    false,
                                    observed(this.evidenceCaptures.frameSequence())));
                    hooks.add(
                            operatorHook(
                                    "native_keyboard_ingress",
                                    "SDLEventHandler.handleKeyEvent/handleTextInputEvent",
                                    false,
                                    true,
                                    "configured_pending_unified_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "native_mouse_ingress",
                                    "SDLEventHandler.handleMouseMotionEvent/handleMouseButtonEvent",
                                    false,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "exclusive_character",
                                    "KeyboardHandler.charTyped",
                                    true,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "exclusive_preedit",
                                    "KeyboardHandler.textInput/textEditing/resubmitLastPreeditEvent",
                                    true,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "exclusive_move_scroll_drop",
                                    "MouseHandler.onMove/onScroll/onDrop",
                                    true,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "virtual_key_polling",
                                    "InputConstants.isKeyDown",
                                    true,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    hooks.add(
                            operatorHook(
                                    "host_cursor_warp_guard",
                                    "InputConstants.grabMouse/releaseMouse",
                                    true,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    JsonObject hostCursorShape =
                            hook(
                                    "host_cursor_shape_guard",
                                    "MIXIN_MODIFY_VARIABLE",
                                    "Window.selectCursor(CursorType)",
                                    "HEAD_ARGUMENT",
                                    "default_host_shape_during_takeover",
                                    "configured_pending_unified_acceptance",
                                    "control.agent_presence");
                    hostCursorShape.addProperty("plane", "OPERATOR_CONTROL");
                    hostCursorShape.addProperty("replacement", true);
                    hostCursorShape.addProperty("cancellable", false);
                    hostCursorShape.addProperty("intentionalControlFlow", true);
                    hooks.add(hostCursorShape);
                    JsonObject json = base("diagnostics.hook_manifest");
                    json.addProperty("policy", "capability_fidelity_first");
                    json.addProperty("overwriteCount", 0);
                    hooks.add(
                            operatorHook(
                                    "logical_cursor_resync",
                                    "MouseHandler.resyncMousePosition",
                                    true,
                                    false,
                                    "configured_pending_unified_acceptance"));
                    json.addProperty("cancellableInjectionCount", 26);
                    hooks.add(
                            operatorHook(
                                    "owned_server_command_completion",
                                    "ClientPacketListener.handleCommandSuggestions",
                                    true,
                                    false,
                                    "configured_owned_transaction_ids_human_priority"));
                    hooks.add(
                            hook(
                                    "system_toast_reset_revision",
                                    "MIXIN_INJECT",
                                    "SystemToast.reset",
                                    "HEAD",
                                    "passive_native_reset_revision",
                                    "configured_pending_native_reset",
                                    "debug.typed_ui_mutation"));
                    hooks.add(
                            operatorHook(
                                    "player_command_entry_guard",
                                    "ClientPacketListener command and link methods",
                                    true,
                                    false,
                                    "configured_origin_and_effect_guard"));
                    hooks.add(
                            operatorHook(
                                    "player_command_packet_guard",
                                    "Connection native command packet send",
                                    true,
                                    false,
                                    "configured_origin_and_effect_guard"));
                    hooks.add(
                            operatorHook(
                                    "world_semantic_gesture_guard",
                                    "MultiPlayerGameMode native actions",
                                    true,
                                    false,
                                    "bounded_owned_gesture_guard"));
                    JsonObject vitalDraw =
                            hook(
                                    "hud_vital_native_draw",
                                    "MIXIN_REDIRECT",
                                    "native health/armor/food/air/mount HUD draws",
                                    "AFTER_UNCHANGED_ORIGINAL_DRAW",
                                    "passive_draw_observation",
                                    "unverified_until_exercised",
                                    "ui.presentation");
                    vitalDraw.addProperty("plane", "OBSERVATION");
                    vitalDraw.addProperty("replacement", true);
                    vitalDraw.addProperty("originalInvocationCount", 1);
                    vitalDraw.addProperty("originalArguments", "unchanged");
                    hooks.add(vitalDraw);
                    json.addProperty("replacementInjectionCount", 48);
                    json.addProperty("operatorReplacementCount", 5);
                    json.addProperty("passiveDrawDelegationCount", 43);
                    json.addProperty(
                            "controlFlowPolicy", "operator_control_and_exact_hud_draw_delegation");
                    json.addProperty("thirdPartyTargetCount", 0);
                    json.addProperty("runtimeSelfTest", true);
                    json.addProperty(
                            "overall", tick && mouse && keyboard && screen ? "ready" : "degraded");
                    json.add("hooks", hooks);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> trace() {
        return this.onClient(
                () -> {
                    JsonObject json = base("trace");
                    json.addProperty("screenSlotClickSequence", this.screenSlotClickSequence.get());
                    json.addProperty("menuDispatchSequence", this.menuDispatchSequence.get());
                    json.addProperty("containerPacketSequence", this.containerPacketSequence.get());
                    json.addProperty(
                            "serverContainerPacketSequence",
                            this.serverContainerPacketSequence.get());
                    json.addProperty("lastDetail", this.lastTraceDetail);
                    json.addProperty("lastServerThread", this.lastServerThread);
                    json.addProperty("screenRevision", this.screenRevision);
                    json.addProperty("menuRevision", this.menuRevision);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> renderFacts() {
        return this.onClient(
                () -> {
                    JsonObject json = base("render.facts");
                    json.addProperty("elements", this.renderElementSequence.get());
                    json.addProperty("items", this.renderItemSequence.get());
                    json.addProperty("texts", this.renderTextSequence.get());
                    json.addProperty("pictures", this.renderPictureSequence.get());
                    json.addProperty("lastFact", this.lastRenderFact);
                    json.addProperty("coverage", "render_primitives");
                    json.addProperty("semanticInference", false);
                    JsonArray facts = new JsonArray();
                    synchronized (this.recentRenderFacts) {
                        for (RenderFact fact : this.recentRenderFacts) {
                            JsonObject item = new JsonObject();
                            item.addProperty("sequence", fact.sequence());
                            item.addProperty("clientTick", fact.clientTick());
                            item.addProperty("screenRevision", fact.screenRevision());
                            item.addProperty("category", fact.category());
                            item.addProperty("class", fact.stateClass());
                            item.addProperty("x", fact.x());
                            item.addProperty("y", fact.y());
                            item.addProperty("width", fact.width());
                            item.addProperty("height", fact.height());
                            facts.add(item);
                        }
                    }
                    json.add("facts", facts);
                    json.addProperty("factCount", facts.size());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> uiTree() {
        return this.onClient(
                () ->
                        io.github.recrivenvi.minecraftprotocol.safety.UiDataView.tree(
                                this.snapshotUiTree(),
                                io.github.recrivenvi.minecraftprotocol.safety.UiDataView.Category
                                        .USER));
    }

    @Override
    public CompletableFuture<JsonObject> debugUiTree() {
        return this.onClient(
                () ->
                        io.github.recrivenvi.minecraftprotocol.safety.UiDataView.tree(
                                this.snapshotUiTree(),
                                io.github.recrivenvi.minecraftprotocol.safety.UiDataView.Category
                                        .DEBUG));
    }

    private JsonObject snapshotUiTree() {
        Minecraft client = requireClient();
        Screen screen = this.refreshScreen(client);
        this.refreshMenu(client);
        JsonObject root = base("ui.tree");
        root.addProperty("screenRevision", this.screenRevision);
        root.addProperty("gameCursorRevision", this.logicalPointer.gameCursorRevision());
        root.addProperty("menuRevision", this.menuRevision);
        root.addProperty("screenIdentity", screen == null ? 0 : this.interactionIdentity(screen));
        root.addProperty(
                "overlayIdentity",
                client.gui.overlay() == null ? 0 : this.interactionIdentity(client.gui.overlay()));
        root.addProperty("width", client.getWindow().getGuiScaledWidth());
        root.addProperty("height", client.getWindow().getGuiScaledHeight());
        root.addProperty("windowWidth", client.getWindow().getScreenWidth());
        root.addProperty("windowHeight", client.getWindow().getScreenHeight());
        root.addProperty("guiScale", client.getWindow().getGuiScale());
        JsonArray children = new JsonArray();
        if (screen == null) {
            root.addProperty("screenClass", "");
            children = SpectatorUiTree.capture(client, root, this::interactionIdentity);
            root.add("children", children);
            root.add("presentation", UiPresentationSnapshot.capture(client));
            UiDebugEngine.describe(
                    root, this.phase9a, screen, this::interactionIdentity, this.itemDataGuard);
            return root;
        }

        root.addProperty("screenClass", screen.getClass().getName());
        root.addProperty("title", screen.getTitle().getString());
        root.addProperty("width", screen.width);
        root.addProperty("height", screen.height);
        root.addProperty("coverage", "semantic_native");
        JsonArray fallbacks = new JsonArray();
        fallbacks.add("render_primitives");
        fallbacks.add("screenshot_vision");
        root.add("fallbacks", fallbacks);
        children =
                new VanillaUiTree(this.screenRevision, this::interactionIdentity)
                        .capture(screen, root);

        if (screen instanceof AbstractContainerScreen<?> container) {
            int left = container.getLeftPos();
            int top = container.getTopPos();
            AbstractContainerMenu menu = container.getMenu();
            long menuIdentity = this.interactionIdentity(menu);
            root.addProperty("menuIdentity", menuIdentity);
            ItemStack carried = menu.getCarried();
            JsonObject carriedSnapshot = new JsonObject();
            carriedSnapshot.addProperty("empty", carried.isEmpty());
            carriedSnapshot.addProperty(
                    "id", BuiltInRegistries.ITEM.getKey(carried.getItem()).toString());
            carriedSnapshot.addProperty("count", carried.getCount());
            addItemContent(carriedSnapshot, carriedContent, "carried:" + menuIdentity);
            root.add("carriedStack", carriedSnapshot);
            int slotBudget = Math.max(0, 2048 - root.get("treeNodeCount").getAsInt());
            int capturedSlots = Math.min(menu.slots.size(), slotBudget);
            if (capturedSlots < menu.slots.size()) root.addProperty("treeTruncated", true);
            for (int viewIndex = 0; viewIndex < capturedSlots; viewIndex++) {
                Slot slot = menu.slots.get(viewIndex);
                JsonObject node = new JsonObject();
                addSlotIdentity(
                        node,
                        slot,
                        viewIndex,
                        this.screenRevision,
                        menuIdentity,
                        this.interactionIdentity(slot));
                node.addProperty("class", slot.getClass().getName());
                node.addProperty("role", "slot");
                addSlotOwner(
                        node, slot, client.player == null ? null : client.player.getInventory());
                node.addProperty("nodeRevision", this.menuRevision);
                node.addProperty("coverage", "semantic_native");
                addSlotBounds(node, left + slot.x, top + slot.y);
                node.addProperty("active", slot.isActive());
                if (menu instanceof CrafterMenu crafter && viewIndex < 9) {
                    // Disabled Crafter cells remain clickable: the native click enables them.
                    node.addProperty("slotDisabled", crafter.isSlotDisabled(viewIndex));
                }
                boolean visible = addSlotVisibility(node, screen.width, screen.height);
                ItemStack stack = slot.getItem();
                node.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                node.addProperty("count", stack.getCount());
                addItemContent(
                        node,
                        slotContents.getOrDefault(slot, UiItemDataGuard.Content.unavailable()),
                        "slot:" + menuIdentity + ":" + this.interactionIdentity(slot));
                addInteractionMetadata(node, "slot", slot.isActive() && visible);
                children.add(node);
            }
            root.addProperty("treeNodeCount", root.get("treeNodeCount").getAsInt() + capturedSlots);
            root.addProperty("menuId", menu.containerId);
        }
        VanillaUiTree.markOccluded(children);
        root.add("children", children);
        root.add("presentation", UiPresentationSnapshot.capture(client));
        UiDebugEngine.describe(
                root, this.phase9a, screen, this::interactionIdentity, this.itemDataGuard);
        return root;
    }

    @Override
    public CompletableFuture<JsonObject> mouseMove(double guiX, double guiY) {
        return this.mouseMoveGuarded(guiX, guiY, null);
    }

    @Override
    public CompletableFuture<JsonObject> mouseMoveGuarded(
            double guiX, double guiY, JsonObject guard) {
        return this.onControlledClient(
                () -> this.movePointerNow(requireClient(), guiX, guiY, guard));
    }

    private JsonObject movePointerNow(
            Minecraft client, double guiX, double guiY, JsonObject guard) {
        this.checkPointerGuard(guard);
        this.syncPointerContext(client);
        if (!Double.isFinite(guiX) || !Double.isFinite(guiY))
            throw new ProtocolState.ProtocolException(
                    "INVALID_POINTER_COORDINATE", 400, "Pointer coordinates must be finite");
        if (client.gui.screen() != null
                && (guiX < 0
                        || guiY < 0
                        || guiX >= client.getWindow().getGuiScaledWidth()
                        || guiY >= client.getWindow().getGuiScaledHeight()))
            throw new ProtocolState.ProtocolException(
                    "POINTER_OUT_OF_BOUNDS", 409, "Pointer target is outside the GUI");
        double rawX =
                guiX * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
        double rawY =
                guiY
                        * client.getWindow().getScreenHeight()
                        / client.getWindow().getGuiScaledHeight();
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) client.mouseHandler;
        if (this.agentPointerInitialized) {
            accessor.minecraftProtocolProbe$setXpos(this.agentPointerX);
            accessor.minecraftProtocolProbe$setYpos(this.agentPointerY);
        }
        accessor.minecraftProtocolProbe$setIgnoreFirstMove(false);
        long gameCursorRevision = this.logicalPointer.gameCursorRevision();
        AgentInputContext.dispatch(
                AgentInputContext.Event.point(
                        AgentInputContext.Kind.MOVE, client.getWindow().handle(), rawX, rawY),
                () -> {
                    ((MouseHandlerInvoker) client.mouseHandler)
                            .minecraftProtocolProbe$onMove(
                                    client.getWindow().handle(),
                                    rawX,
                                    rawY,
                                    rawX - this.agentPointerX,
                                    rawY - this.agentPointerY);
                    client.mouseHandler.handleAccumulatedMovement();
                });
        if (gameCursorRevision != this.logicalPointer.gameCursorRevision()) {
            this.restoreAgentPointer(client);
            throw new ProtocolState.ProtocolException(
                    "UI_TARGET_INVALIDATED", 409, "Game cursor changed during pointer dispatch");
        }
        this.agentPointerX = rawX;
        this.agentPointerY = rawY;
        this.agentPointerInitialized = true;
        this.logicalPointer.move(guiX, guiY);
        if (guard != null && guard.has("trackDrag"))
            AutomationEngine.advanceDragGuard(guard, this.snapshotUiTree());
        JsonObject evidence = inputEvidence("GAME_ROUTED_RAW", client.gui.screen() != null, false);
        evidence.addProperty("pointerPlane", this.logicalPointer.plane().name());
        evidence.addProperty("guiX", guiX);
        evidence.addProperty("guiY", guiY);
        this.gestureEvent(this.activeGestureId, "pointer_move", evidence);
        return evidence;
    }

    @Override
    public CompletableFuture<JsonObject> mouseDelta(double dx, double dy) {
        return this.onControlledClient(() -> this.mouseDeltaNow(dx, dy));
    }

    private JsonObject mouseDeltaNow(double dx, double dy) {
        Minecraft client = requireClient();
        if (!this.gameplayViewport())
            throw new ProtocolState.ProtocolException(
                    "GAMEPLAY_POINTER_REQUIRED",
                    409,
                    "Relative input requires gameplay without Screen/Overlay");
        if (!Double.isFinite(dx)
                || !Double.isFinite(dy)
                || Math.abs(dx) > 8192
                || Math.abs(dy) > 8192)
            throw new ProtocolState.ProtocolException(
                    "INVALID_POINTER_DELTA", 400, "Relative delta exceeds its finite pixel budget");
        this.syncPointerContext(client);
        if (!this.logicalPointer.gameCaptured())
            throw new ProtocolState.ProtocolException(
                    "GAMEPLAY_POINTER_REQUIRED",
                    409,
                    "Relative input requires the game's logical capture request; host focus is not required");
        double x =
                (this.agentPointerX + dx)
                        * client.getWindow().getGuiScaledWidth()
                        / client.getWindow().getScreenWidth();
        double y =
                (this.agentPointerY + dy)
                        * client.getWindow().getGuiScaledHeight()
                        / client.getWindow().getScreenHeight();
        JsonObject result = this.movePointerNow(client, x, y, null);
        result.addProperty("type", "input.mouse.delta");
        result.addProperty("dx", dx);
        result.addProperty("dy", dy);
        result.addProperty("cameraProcessing", "vanilla_sensitivity_inversion");
        return result;
    }

    private long interactionIdentity(Object object) {
        return this.interactionIdentities.id(object);
    }

    private void checkPointerGuard(JsonObject guard) {
        if (guard != null) AutomationEngine.validatePointerGuard(guard, this.snapshotUiTree());
        // Encoding item data may take time; an effect cancelled meanwhile must not reach input.
        var dispatch = this.commandGate.dispatch();
        if (dispatch != null) dispatch.checkEffect();
    }

    @Override
    public CompletableFuture<JsonObject> pointerState() {
        return this.onClient(() -> this.pointerStateNow(false));
    }

    @Override
    public CompletableFuture<JsonObject> inputPacingState() {
        return this.onClient(() -> this.pointerStateNow(true));
    }

    private JsonObject pointerStateNow(boolean pacingOnly) {
        Minecraft client = requireClient();
        this.syncPointerContext(client);
        JsonObject result =
                pacingOnly && this.gameplayViewport()
                        ? this.gameplayInputFrame(client)
                        : this.snapshotUiTree();
        result.addProperty(
                "x",
                this.logicalPointer.initialized()
                        ? this.logicalPointer.x()
                        : client.getWindow().getGuiScaledWidth() / 2.0);
        result.addProperty(
                "y",
                this.logicalPointer.initialized()
                        ? this.logicalPointer.y()
                        : client.getWindow().getGuiScaledHeight() / 2.0);
        result.addProperty("guiAbsolute", client.gui.screen() != null);
        result.addProperty("pressedButtonCount", this.pressedButtons.size());
        return result;
    }

    private JsonObject gameplayInputFrame(Minecraft client) {
        this.refreshScreen(client);
        JsonObject frame = base("input.pacing_state");
        frame.addProperty("screenClass", "");
        frame.addProperty("screenIdentity", 0);
        frame.addProperty("overlayIdentity", 0);
        frame.addProperty("screenRevision", this.screenRevision);
        frame.addProperty("gameCursorRevision", this.logicalPointer.gameCursorRevision());
        frame.addProperty("width", client.getWindow().getGuiScaledWidth());
        frame.addProperty("height", client.getWindow().getGuiScaledHeight());
        frame.addProperty("windowWidth", client.getWindow().getScreenWidth());
        frame.addProperty("windowHeight", client.getWindow().getScreenHeight());
        frame.addProperty("guiScale", client.getWindow().getGuiScale());
        return frame;
    }

    @Override
    public void gestureEvent(String id, String phase, JsonObject detail) {
        if (phase.equals("started")) this.activeGestureId = id;
        if (phase.equals("drained") && this.activeGestureId.equals(id)) this.activeGestureId = "";
        ProbeTransport current = this.transport;
        if (current == null) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "control.gesture." + phase);
        event.addProperty("category", "control");
        event.addProperty("gestureId", id);
        event.addProperty("mode", this.controlPresence.mode().name());
        event.addProperty("operatorMetadata", true);
        event.addProperty("inputOrigin", "AGENT_ROUTED");
        if (detail != null) event.add("detail", detail.deepCopy());
        current.broadcast(event);
    }

    private String pointerFrameStamp(Minecraft client) {
        this.refreshScreen(client);
        return this.screenRevision
                + ":"
                + (client.gui.overlay() == null
                        ? 0
                        : this.interactionIdentity(client.gui.overlay()))
                + ":"
                + client.getWindow().getScreenWidth()
                + ":"
                + client.getWindow().getScreenHeight()
                + ":"
                + client.getWindow().getGuiScaledWidth()
                + ":"
                + client.getWindow().getGuiScaledHeight()
                + ":"
                + client.getWindow().getGuiScale()
                + ":"
                + this.logicalPointer.gameCursorRevision();
    }

    private void clearMouseButtonState(Minecraft client) {
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) client.mouseHandler;
        mouse.minecraftProtocolProbe$setLeftPressed(false);
        mouse.minecraftProtocolProbe$setRightPressed(false);
        mouse.minecraftProtocolProbe$setMiddlePressed(false);
        mouse.minecraftProtocolProbe$setActiveButton(null);
        mouse.minecraftProtocolProbe$setMousePressedTime(0);
        for (int button : this.pressedButtons)
            net.minecraft.client.KeyMapping.set(
                    com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(
                            NativeInputMapping.button(button)),
                    false);
    }

    private void restoreAgentPointer(Minecraft client) {
        if (!this.agentPointerInitialized) return;
        // Native movement may arrive between a queued move and click/scroll.
        // Reuse the existing routed coordinate; never reposition the OS cursor.
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) client.mouseHandler;
        accessor.minecraftProtocolProbe$setXpos(this.agentPointerX);
        accessor.minecraftProtocolProbe$setYpos(this.agentPointerY);
    }

    @Override
    public CompletableFuture<JsonObject> mouseButton(int button, int action, int modifiers) {
        return this.mouseButtonGuarded(button, action, modifiers, null);
    }

    @Override
    public CompletableFuture<JsonObject> mouseButtonGuarded(
            int button, int action, int modifiers, JsonObject guard) {
        return this.onControlledClient(
                () -> this.mouseButtonGuardedNow(button, action, modifiers, guard));
    }

    private JsonObject mouseButtonGuardedNow(
            int button, int action, int modifiers, JsonObject guard) {
        this.checkPointerGuard(guard);
        Minecraft client = requireClient();
        this.restoreAgentPointer(client);
        Screen screen = client.gui.screen();
        long beforeMenu = this.menuDispatchSequence.get();
        long beforePacket = this.containerPacketSequence.get();
        if (action == 1) this.buttonFrames.put(button, this.pointerFrameStamp(client));
        if (action == 1) this.buttonScreens.put(button, client.gui.screen());
        if (action == 0
                && this.buttonScreens.containsKey(button)
                && (!java.util.Objects.equals(
                                this.buttonFrames.get(button), this.pointerFrameStamp(client))
                        || this.buttonScreens.get(button) != client.gui.screen())) {
            this.clearMouseButtonState(client);
        } else {
            this.trackNativeInput(
                    client,
                    () ->
                            AgentInputContext.dispatch(
                                    AgentInputContext.Event.button(
                                            client.getWindow().handle(),
                                            NativeInputMapping.button(button),
                                            action,
                                            NativeInputMapping.modifiers(modifiers)),
                                    () ->
                                            ((MouseHandlerInvoker) client.mouseHandler)
                                                    .minecraftProtocolProbe$onButton(
                                                            client.getWindow().handle(),
                                                            new MouseButtonInfo(
                                                                    NativeInputMapping.button(
                                                                            button),
                                                                    NativeInputMapping.modifiers(
                                                                            modifiers)),
                                                            action)));
        }
        if (action == 0) {
            this.buttonScreens.remove(button);
            this.buttonFrames.remove(button);
        }
        if (action == 1) this.pressedButtons.add(button);
        else if (action == 0) this.pressedButtons.remove(button);
        if (action == 0 || action == 1)
            this.logicalPointer.button(button, action == 1, System.nanoTime());
        this.refreshScreen(client);
        this.refreshMenu(client);
        if (action == 1 && guard != null && guard.has("trackClickSelection"))
            AutomationEngine.advanceClickSelectionGuard(guard, this.snapshotUiTree());
        if (guard != null && guard.has("trackDrag"))
            AutomationEngine.advanceDragGuard(guard, this.snapshotUiTree());
        JsonObject evidence =
                inputEvidence(
                        "GAME_ROUTED_RAW",
                        screen != null,
                        screen instanceof AbstractContainerScreen<?>);
        evidence.addProperty(
                "normalMenuProcessingObserved", this.menuDispatchSequence.get() > beforeMenu);
        evidence.addProperty(
                "normalPacketObserved", this.containerPacketSequence.get() > beforePacket);
        return evidence;
    }

    @Override
    public CompletableFuture<JsonObject> mouseScroll(double xOffset, double yOffset) {
        return this.mouseScrollGuarded(xOffset, yOffset, null);
    }

    @Override
    public CompletableFuture<JsonObject> mouseScrollGuarded(
            double xOffset, double yOffset, JsonObject guard) {
        return this.onControlledClient(
                () -> {
                    this.checkPointerGuard(guard);
                    Minecraft client = requireClient();
                    this.restoreAgentPointer(client);
                    AgentInputContext.dispatch(
                            AgentInputContext.Event.point(
                                    AgentInputContext.Kind.SCROLL,
                                    client.getWindow().handle(),
                                    xOffset,
                                    yOffset),
                            () ->
                                    ((MouseHandlerInvoker) client.mouseHandler)
                                            .minecraftProtocolProbe$onScroll(
                                                    client.getWindow().handle(), xOffset, yOffset));
                    Screen screen = client.gui.screen();
                    return inputEvidence(
                            "GAME_ROUTED_RAW",
                            screen != null,
                            screen instanceof AbstractContainerScreen<?>);
                });
    }

    @Override
    public CompletableFuture<JsonObject> key(int key, int scanCode, int action, int modifiers) {
        return this.keyGuarded(key, scanCode, action, modifiers, null);
    }

    @Override
    public CompletableFuture<JsonObject> keyGuarded(
            int key, int scanCode, int action, int modifiers, JsonObject guard) {
        return this.onControlledClient(
                () -> this.keyGuardedNow(key, scanCode, action, modifiers, guard));
    }

    private JsonObject keyGuardedNow(
            int key, int scanCode, int action, int modifiers, JsonObject guard) {
        if (action != 0) this.checkPointerGuard(guard);
        Minecraft client = requireClient();
        KeyEvent nativeEvent = NativeInputMapping.event(key, modifiers);
        this.trackNativeInput(
                client,
                () ->
                        AgentInputContext.dispatch(
                                AgentInputContext.Event.key(
                                        client.getWindow().handle(),
                                        nativeEvent.key(),
                                        nativeEvent.keycode(),
                                        action,
                                        nativeEvent.modifiers()),
                                () ->
                                        ((KeyboardHandlerInvoker) client.keyboardHandler)
                                                .minecraftProtocolProbe$keyPress(
                                                        client.getWindow().handle(),
                                                        action,
                                                        nativeEvent)));
        if (action == 1 || action == 2)
            this.pressedKeys.put(key, new KeyState(scanCode, modifiers));
        else if (action == 0) this.pressedKeys.remove(key);
        this.refreshScreen(client);
        this.refreshMenu(client);
        Screen screen = client.gui.screen();
        JsonObject evidence =
                inputEvidence(
                        "GAME_ROUTED_RAW",
                        screen != null,
                        screen instanceof AbstractContainerScreen<?>);
        evidence.addProperty("keyMappingPath", screen == null);
        return evidence;
    }

    @Override
    public CompletableFuture<JsonObject> character(int codePoint, JsonObject guard) {
        return this.onControlledClient(() -> this.characterNow(codePoint, guard));
    }

    private JsonObject characterNow(int codePoint, JsonObject guard) {
        this.checkPointerGuard(guard);
        Minecraft client = requireClient();
        if (client.gui.screen() == null
                || !Character.isValidCodePoint(codePoint)
                || (codePoint >= 0xD800 && codePoint <= 0xDFFF)
                || Character.isISOControl(codePoint)) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_UI_TEXT", 400, "Only valid GUI characters are accepted");
        }
        if (guard != null && guard.has("selector")) {
            JsonObject node =
                    AutomationEngine.resolveTree(
                                    this.snapshotUiTree(), guard.getAsJsonObject("selector"))
                            .getAsJsonObject("node");
            if (!node.has("focused") || !node.get("focused").getAsBoolean())
                throw new ProtocolState.ProtocolException(
                        "UI_FOCUS_CHANGED", 409, "Text target is no longer focused");
        }
        long window = client.getWindow().handle();
        AgentInputContext.dispatch(
                new AgentInputContext.Event(
                        AgentInputContext.Kind.CHARACTER, window, codePoint, 0, 0, 0),
                () ->
                        client.keyboardHandler.charTyped(
                                window, new net.minecraft.client.input.CharacterEvent(codePoint)));
        return inputEvidence("GAME_ROUTED_RAW", true, false);
    }

    private final WorldTargeting worldTargeting = new WorldTargeting();

    @Override
    public CompletableFuture<JsonObject> worldAimDelta(
            String targetRef, JsonObject guard, double dx, double dy) {
        return onControlledClient(
                () -> {
                    worldTargeting.validate(targetRef, guard);
                    return mouseDeltaNow(dx, dy);
                });
    }

    @Override
    public CompletableFuture<JsonObject> worldActionFrame(
            String targetRef, String action, JsonObject guard, boolean press) {
        return worldActionFrame(targetRef, action, guard, press, new JsonObject());
    }

    @Override
    public CompletableFuture<JsonObject> worldActionFrame(
            String targetRef, String action, JsonObject guard, boolean press, JsonObject options) {
        return onControlledClient(
                () ->
                        worldTargeting.frame(
                                targetRef,
                                action,
                                guard,
                                press,
                                options,
                                binding ->
                                        binding.get("kind").getAsString().equals("mouse")
                                                ? mouseButtonGuardedNow(
                                                        binding.get("code").getAsInt(), 1, 0, null)
                                                : keyGuardedNow(
                                                        binding.get("code").getAsInt(),
                                                        binding.get("scanCode").getAsInt(),
                                                        1,
                                                        0,
                                                        null)));
    }

    @Override
    public CompletableFuture<JsonObject> worldActionProgress(
            String ref, JsonObject guard, boolean released) {
        return onControlledClient(() -> worldTargeting.progress(ref, guard, released), false);
    }

    @Override
    public CompletableFuture<JsonObject> worldActionEnd() {
        return onClientCleanup(
                () -> {
                    worldTargeting.end();
                    return new JsonObject();
                });
    }

    @Override
    public CompletableFuture<JsonObject> worldPresentation() {
        return clientWorldRead("scene", new JsonObject());
    }

    @Override
    public CompletableFuture<JsonObject> clientWorldRead(String kind, JsonObject query) {
        JsonObject detached = query.deepCopy();
        return onClient(
                () -> {
                    JsonObject result =
                            base(kind.equals("player") ? "player.state" : "world." + kind);
                    WorldClientSnapshot.capture(kind, detached, this.worldTargeting)
                            .entrySet()
                            .forEach(e -> result.add(e.getKey(), e.getValue()));
                    return result;
                });
    }

    @Override
    public CompletableFuture<JsonObject> clientWorldRegion(
            String action, JsonObject query, String trustedOwner, Runnable authorization) {
        JsonObject detached = query.deepCopy();
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        Minecraft client = requireClient();
        client.execute(
                () -> {
                    if (result.isDone()) return;
                    try {
                        this.refreshInputContext(client);
                        Runnable guard =
                                () -> {
                                    if (result.isDone() || this.shutdownStarted.get())
                                        throw new java.util.concurrent.CancellationException(
                                                "Region page cancelled");
                                    authorization.run();
                                };
                        guard.run();
                        JsonObject value;
                        if (action.equals("cancel")) {
                            value = new JsonObject();
                            value.addProperty("type", "world.region.cancel");
                            value.addProperty("dataCategory", "USER");
                            value.addProperty(
                                    "cancelled",
                                    this.regionPages.cancel(
                                            detached.get("cursor").getAsString(), trustedOwner));
                        } else
                            value =
                                    this.regionPages.read(
                                            detached,
                                            trustedOwner,
                                            WorldRegionSnapshot.view(
                                                    client,
                                                    this.publishedInputContext,
                                                    () -> this.clientTick),
                                            guard);
                        guard.run();
                        result.complete(value);
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                });
        return result;
    }

    @Override
    public CompletableFuture<JsonObject> playerState() {

        return this.onClient(
                () -> {
                    Minecraft client = requireClient();
                    JsonObject json = base("player.state");
                    addLiveMetadata(json, "client_known", "client_live", "client_observed");
                    Player player = client.player;
                    if (player == null) {
                        json.addProperty("available", false);
                        return json;
                    }
                    json.addProperty("available", true);
                    json.addProperty("uuid", player.getUUID().toString());
                    json.addProperty("x", player.getX());
                    json.addProperty("y", player.getY());
                    json.addProperty("z", player.getZ());
                    json.addProperty("yaw", player.getYRot());
                    json.addProperty("pitch", player.getXRot());
                    json.addProperty("health", player.getHealth());
                    json.addProperty("maxHealth", player.getMaxHealth());
                    json.addProperty("absorption", player.getAbsorptionAmount());
                    json.addProperty("food", player.getFoodData().getFoodLevel());
                    json.addProperty("air", player.getAirSupply());
                    json.addProperty("velocityX", player.getDeltaMovement().x);
                    json.addProperty("velocityY", player.getDeltaMovement().y);
                    json.addProperty("velocityZ", player.getDeltaMovement().z);
                    json.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
                    json.addProperty(
                            "dimension", player.level().dimension().identifier().toString());
                    return json;
                });
    }

    private final io.github.recrivenvi.minecraftprotocol.core.PlayerCommandChat commandChat =
            new io.github.recrivenvi.minecraftprotocol.core.PlayerCommandChat();
    private final io.github.recrivenvi.minecraftprotocol.core.PlayerCommandFeedback
            commandFeedback =
                    new io.github.recrivenvi.minecraftprotocol.core.PlayerCommandFeedback();

    private boolean commandFeedbackReadable() {
        return requireClient().options.chatVisibility().get()
                != net.minecraft.world.entity.player.ChatVisiblity.HIDDEN;
    }

    private java.util.List<
                    io.github.recrivenvi.minecraftprotocol.core.PlayerCommandFeedback.Message>
            commandFeedbackMessages() {
        var access =
                (io.github.recrivenvi.minecraftprotocol.probe.mixin.CommandFeedbackChatAccessor)
                        requireClient().gui.hud.getChat();
        boolean readable = commandFeedbackReadable();
        return access.minecraftProtocolProbe$messages().stream()
                .limit(100)
                .map(
                        message ->
                                new io.github.recrivenvi.minecraftprotocol.core
                                        .PlayerCommandFeedback.Message(
                                        message,
                                        readable
                                                        && access.minecraftProtocolProbe$visibleFilter()
                                                                .test(message)
                                                ? message.content().getString()
                                                : null))
                .toList();
    }

    private net.minecraft.client.KeyMapping commandChatOpening;

    private net.minecraft.client.KeyMapping requireIdleChatMapping() {
        var mapping = requireClient().options.keyChat;
        if (((io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor) mapping)
                        .minecraftProtocolProbe$pendingClicks()
                != 0)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_INPUT_PENDING",
                    409,
                    "Wait for the preceding Chat key action before starting a new command");
        return mapping;
    }

    private io.github.recrivenvi.minecraftprotocol.core.PlayerCommandChat.Frame commandChatFrame() {
        Minecraft client = requireClient();
        if (client.gui.overlay() != null)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_CONTEXT_CHANGED", 409, "An Overlay prevents native Chat input");
        Screen screen = client.gui.screen();
        net.minecraft.client.gui.components.EditBox input = null;
        boolean chat = screen instanceof net.minecraft.client.gui.screens.ChatScreen;
        if (chat) {
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.EditBox edit) {
                    if (input != null)
                        throw new ProtocolState.ProtocolException(
                                "PLAYER_COMMAND_CONTEXT_CHANGED", 409, "Ambiguous Chat input");
                    input = edit;
                }
            }
        }
        return new io.github.recrivenvi.minecraftprotocol.core.PlayerCommandChat.Frame(
                client.level,
                client.player,
                client.getConnection(),
                screen,
                input,
                chat,
                input == null ? "" : input.getValue(),
                input != null && input.isFocused(),
                input == null ? 0 : input.getCursorPosition(),
                input == null ? "" : input.getHighlighted());
    }

    private JsonObject commandChatBinding() {
        var mapping = requireClient().options.keyChat;
        if (mapping.getKeyModifier() != net.neoforged.neoforge.client.settings.KeyModifier.NONE)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BINDING_UNSUPPORTED",
                    409,
                    "Modified Chat bindings require an explicit chord");
        var key =
                ((io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor) mapping)
                        .minecraftProtocolProbe$key();
        JsonObject binding = new JsonObject();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD) {
            int code = NativeInputMapping.wireKey(key.getValue());
            if (code < 0)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BINDING_UNSUPPORTED", 409, "Unsupported Chat key binding");
            binding.addProperty("kind", "key");
            binding.addProperty("code", code);
        } else if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            int code =
                    switch (key.getValue()) {
                        case com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT -> 0;
                        case com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT -> 1;
                        case com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
                        default -> -1;
                    };
            if (code < 0)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BINDING_UNSUPPORTED",
                        409,
                        "Unsupported Chat mouse binding");
            binding.addProperty("kind", "mouse");
            binding.addProperty("code", code);
        } else
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BINDING_UNSUPPORTED", 409, "Unbound Chat key cannot be routed");
        return binding;
    }

    @Override
    public CompletableFuture<JsonObject> discoverPlayerCommands(
            String input, int cursor, int maxResults, Runnable authorization) {
        var query =
                new io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery.Query(
                        input, cursor, maxResults);
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.onClient(
                        () -> NativePlayerCommands.discover(requireClient(), query, authorization)),
                future -> future);
    }

    @Override
    public CompletableFuture<JsonObject> completePlayerCommands(
            String input, int cursor, int maxResults, Runnable authorization) {
        var query =
                new io.github.recrivenvi.minecraftprotocol.core.PlayerCommandDiscovery.Query(
                        input, cursor, maxResults);
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.onClient(
                        () -> NativePlayerCommands.complete(requireClient(), query, authorization)),
                future -> future);
    }

    @Override
    public CompletableFuture<JsonObject> nativePlayerCommandBatch(
            java.util.List<String> commands,
            AgentControlSession.OperateWork work,
            Runnable authorization,
            String requestId) {
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.onOperatingClient(
                        work,
                        () -> {
                            Runnable bound =
                                    NativePlayerCommands.contextGuard(
                                            requireClient(), authorization);
                            return io.github.recrivenvi.minecraftprotocol.core.PlayerCommandBatch
                                    .execute(
                                            commands,
                                            requestId,
                                            bound,
                                            (index, command, guard) ->
                                                    this.nativePlayerCommand(
                                                            command,
                                                            work,
                                                            guard,
                                                            requestId + ":" + index));
                        }),
                future -> future);
    }

    @Override
    public CompletableFuture<JsonObject> nativePlayerCommand(
            String command,
            AgentControlSession.OperateWork work,
            Runnable authorization,
            String requestId) {
        return this.onOperatingClient(
                work,
                () -> {
                    authorization.run();
                    Minecraft client = requireClient();
                    var player = client.player;
                    var level = client.level;
                    var connection = client.getConnection();
                    if (player == null
                            || level == null
                            || connection == null
                            || !connection.getConnection().isConnected())
                        throw new ProtocolState.ProtocolException(
                                "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                                409,
                                "A connected current player is required");
                    if (command.length() + 1 > net.minecraft.SharedConstants.MAX_CHAT_LENGTH)
                        throw new ProtocolState.ProtocolException(
                                "PLAYER_COMMAND_TOO_LONG",
                                400,
                                "Command exceeds the current native command length budget");
                    Runnable guard =
                            () -> {
                                authorization.run();
                                if (work.isCancelled())
                                    throw new ProtocolState.ProtocolException(
                                            "OPERATE_WORK_CANCELLED",
                                            409,
                                            "Native command was cancelled");
                                if (client.player != player
                                        || client.level != level
                                        || client.getConnection() != connection
                                        || !connection.getConnection().isConnected())
                                    throw new ProtocolState.ProtocolException(
                                            "PLAYER_COMMAND_CONTEXT_CHANGED",
                                            409,
                                            "Player or connection changed before native command submission");
                            };
                    try (var dispatch = this.commandGate.enterNative(command, guard, requestId)) {
                        guard.run();
                        connection.sendCommand(command);
                        JsonObject result = base("command.player.execute");
                        dispatch.receipt()
                                .entrySet()
                                .forEach(entry -> result.add(entry.getKey(), entry.getValue()));
                        result.addProperty("requiresControlLease", false);
                        result.addProperty("inputDispatched", false);
                        result.addProperty("feedbackAvailable", false);
                        result.addProperty(
                                "feedbackReason", "native_submission_without_chat_sampling");
                        return result;
                    }
                });
    }

    @Override
    public CompletableFuture<JsonObject> playerCommand(String command, String phase) {
        return this.onControlledClient(
                () -> {
                    var dispatch = this.commandGate.dispatch();
                    JsonObject result = base("command.player." + phase);
                    switch (phase) {
                        case "prepare" -> {
                            if (!this.pressedKeys.isEmpty() || !this.pressedButtons.isEmpty())
                                throw new ProtocolState.ProtocolException(
                                        "PLAYER_COMMAND_INPUT_HELD",
                                        409,
                                        "Release existing held input before a command gesture");
                            requireIdleChatMapping();
                            commandChatBinding();
                            this.commandChat.prepare(
                                    commandChatFrame(),
                                    command,
                                    dispatch.gestureId(),
                                    net.minecraft.SharedConstants.MAX_CHAT_LENGTH);
                            this.commandGate.allow(
                                    command,
                                    true,
                                    false,
                                    () -> NativePlayerCommands.parse(requireClient(), command));
                            dispatch.check();
                        }
                        case "open" -> {
                            this.commandChat.beforeOpen(
                                    commandChatFrame(), command, dispatch.gestureId());
                            this.commandChatOpening = requireIdleChatMapping();
                            var binding = commandChatBinding();
                            int code = binding.get("code").getAsInt();
                            if (binding.get("kind").getAsString().equals("key")) {
                                keyGuardedNow(code, 0, 1, 0, null);
                                keyGuardedNow(code, 0, 0, 0, null);
                            } else {
                                mouseButtonGuardedNow(code, 1, 0, null);
                                mouseButtonGuardedNow(code, 0, 0, null);
                            }
                        }
                        case "ready" ->
                                result.addProperty(
                                        "ready",
                                        this.commandChat.ready(
                                                commandChatFrame(), command, dispatch.gestureId()));
                        case "type" -> {
                            String chunk =
                                    this.commandChat.nextChunk(
                                            commandChatFrame(), command, dispatch.gestureId());
                            for (int cp : chunk.codePoints().toArray()) {
                                this.commandChat.requireDraft(
                                        commandChatFrame(), command, dispatch.gestureId());
                                characterNow(cp, null);
                                dispatch.checkEffect();
                                this.commandChat.acceptCharacter(
                                        commandChatFrame(), command, dispatch.gestureId(), cp);
                            }
                            result.addProperty(
                                    "typed",
                                    this.commandChat
                                            .nextChunk(
                                                    commandChatFrame(),
                                                    command,
                                                    dispatch.gestureId())
                                            .isEmpty());
                        }
                        case "submit" -> {
                            this.commandChat.beforeSubmit(
                                    commandChatFrame(), command, dispatch.gestureId());
                            this.commandFeedback.begin(
                                    commandFeedbackMessages(), commandFeedbackReadable());
                            keyGuardedNow(257, 0, 1, 0, null);
                            keyGuardedNow(257, 0, 0, 0, null);
                            for (var entry : dispatch.receipt().entrySet())
                                result.add(entry.getKey(), entry.getValue());
                            result.addProperty("type", "command.player.execute");
                        }
                        case "feedback" -> {
                            var client = requireClient();
                            result =
                                    this.commandChat.matchesWorld(
                                                    client.level,
                                                    client.player,
                                                    client.getConnection(),
                                                    command,
                                                    dispatch.gestureId())
                                            ? this.commandFeedback.poll(
                                                    commandFeedbackMessages(),
                                                    commandFeedbackReadable())
                                            : this.commandFeedback.finishContextChanged();
                            if (result.get("complete").getAsBoolean()) {
                                this.commandChat.clear();
                                this.commandFeedback.clear();
                            }
                        }
                        default ->
                                throw new IllegalArgumentException(
                                        "Unknown internal command phase");
                    }
                    return result;
                },
                !phase.equals("prepare") && !phase.equals("ready") && !phase.equals("feedback"));
    }

    @Override
    public CompletableFuture<JsonObject> blockState(int x, int y, int z) {
        return this.onClient(
                () -> {
                    Minecraft client = requireClient();
                    JsonObject json = base("world.block");
                    addLiveMetadata(json, "client_known_live", "client_live", "client_observed");
                    json.addProperty("x", x);
                    json.addProperty("y", y);
                    json.addProperty("z", z);
                    if (client.level == null) {
                        json.addProperty("available", false);
                        return json;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    // hasChunkAt may accept the empty fallback chunk; only a cached chunk
                    // establishes CLIENT_KNOWN state. false never loads or generates it.
                    if (client.level
                                    .getChunkSource()
                                    .getChunk(
                                            x >> 4,
                                            z >> 4,
                                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                            false)
                            == null) {
                        json.addProperty("chunkLoadRequested", false);
                        json.addProperty("available", false);
                        json.addProperty("reason", "chunk_not_loaded");
                        return json;
                    }
                    BlockState state = client.level.getBlockState(position);
                    json.addProperty("available", true);
                    json.addProperty(
                            "block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    json.addProperty("state", state.toString());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> entities(double radius) {
        return this.onClient(
                () -> {
                    Minecraft client = requireClient();
                    JsonObject json = base("world.entities");
                    addLiveMetadata(json, "client_known_live", "client_live", "client_observed");
                    JsonArray entities = new JsonArray();
                    if (client.level != null && client.player != null) {
                        double boundedRadius = Mth.clamp(radius, 0.0, 128.0);
                        for (Entity entity :
                                client.level.getEntities(
                                        client.player,
                                        client.player.getBoundingBox().inflate(boundedRadius),
                                        entity -> true)) {
                            if (entities.size() >= 128) break;
                            entities.add(entityState(entity));
                        }
                        json.addProperty("radius", boundedRadius);
                    }
                    json.add("entities", entities);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> serverPlayerState() {
        if (this.shouldUsePeer()) return this.peerRequest("player.get", new JsonObject());
        return this.onIntegratedServer(
                (server, player) -> {
                    JsonObject json = base("server.player.state");
                    addLiveMetadata(
                            json,
                            "server_authoritative_live",
                            "integrated_server_live",
                            "server_authoritative");
                    json.addProperty("available", true);
                    json.addProperty("serverTick", server.getTickCount());
                    json.addProperty("thread", Thread.currentThread().getName());
                    addPlayerFields(json, player);
                    json.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
                    json.addProperty(
                            "dimension", player.level().dimension().identifier().toString());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> serverBlockState(int x, int y, int z) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("x", x);
            params.addProperty("y", y);
            params.addProperty("z", z);
            return this.peerRequest("world.block.get", params);
        }
        return this.onIntegratedServer(
                (server, player) -> {
                    ServerLevel level = player.level();
                    BlockPos position = new BlockPos(x, y, z);
                    JsonObject json = base("server.world.block");
                    addLiveMetadata(
                            json,
                            "server_authoritative_live",
                            "integrated_server_live",
                            "server_authoritative");
                    json.addProperty("serverTick", server.getTickCount());
                    json.addProperty("dimension", level.dimension().identifier().toString());
                    json.addProperty("x", x);
                    json.addProperty("y", y);
                    json.addProperty("z", z);
                    json.addProperty("chunkLoadRequested", false);
                    if (!level.hasChunkAt(position)) {
                        json.addProperty("available", false);
                        json.addProperty("reason", "chunk_not_loaded");
                        return json;
                    }
                    BlockState state = level.getBlockState(position);
                    json.addProperty("available", true);
                    json.addProperty(
                            "block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    json.addProperty("state", state.toString());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> serverEntities(double radius) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("radius", radius);
            return this.peerRequest("world.entities.query", params);
        }
        return this.onIntegratedServer(
                (server, player) -> {
                    ServerLevel level = player.level();
                    double boundedRadius = Mth.clamp(radius, 0.0, 128.0);
                    JsonArray values = new JsonArray();
                    for (Entity entity :
                            level.getEntities(
                                    player,
                                    player.getBoundingBox().inflate(boundedRadius),
                                    entity -> true)) {
                        if (values.size() >= 128) break;
                        values.add(entityState(entity));
                    }
                    JsonObject json = base("server.world.entities");
                    addLiveMetadata(
                            json,
                            "server_authoritative_live",
                            "integrated_server_live",
                            "server_authoritative");
                    json.addProperty("serverTick", server.getTickCount());
                    json.addProperty("dimension", level.dimension().identifier().toString());
                    json.addProperty("radius", boundedRadius);
                    json.add("entities", values);
                    return json;
                });
    }

    @Override
    public CompletableFuture<byte[]> capturePng() {
        if (this.minecraft == null)
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "RUNTIME_NOT_READY", 409, "Client is not ready"));
        return this.evidenceCaptures.request();
    }

    /** Invoked only at the final frame boundary before the Operator UI pass. */
    private CompletableFuture<byte[]> captureContentAtBoundary() {
        Minecraft client = requireClient();
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        try {
            Screenshot.takeScreenshot(
                    client.gameRenderer.mainRenderTarget(),
                    image -> Util.ioPool().execute(() -> encodeScreenshot(image, result)));
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    @Override
    public CompletableFuture<JsonObject> captureInfo() {
        return this.onClient(
                () -> {
                    JsonObject json = base("capture.info");
                    String description = RenderSystem.getDevice().getDeviceInfo().backendName();
                    json.addProperty(
                            "backend",
                            description.toLowerCase(java.util.Locale.ROOT).contains("vulkan")
                                    ? "vulkan"
                                    : "opengl");
                    json.addProperty("backendDescription", description);
                    json.addProperty("mode", "COMPOSITE");
                    json.addProperty("format", "PNG");
                    json.addProperty("captureVerified", this.captureVerified.get());
                    json.addProperty("encodingThread", "io_pool");
                    json.addProperty("inputConcurrent", true);
                    return json;
                });
    }

    @Override
    public Path artifactRoot() {
        return requireClient()
                .gameDirectory
                .toPath()
                .resolve("minecraft-protocol")
                .resolve("artifacts");
    }

    @Override
    public CompletableFuture<JsonObject> worldFingerprint() {
        if (this.shouldUsePeer()) return this.peerRequest("world.fingerprint", new JsonObject());
        return this.onIntegratedServer(
                (server, player) -> {
                    String material =
                            "26.3-neoforge|"
                                    + server.getWorldData().getLevelName()
                                    + "|"
                                    + player.level().dimension().identifier();
                    JsonObject json = base("world.fingerprint");
                    json.addProperty("worldFingerprint", sha256(material));
                    json.addProperty("perspective", "server_authoritative_live");
                    json.addProperty("sessionBound", true);
                    json.addProperty("storageAccessed", false);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> fixtureTeleport(
            double x, double y, double z, AgentControlSession.OperateWork work) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("x", x);
            params.addProperty("y", y);
            params.addProperty("z", z);
            return this.operatingPeerRequest(work, "fixture.player.teleport", params);
        }
        return this.onOperatingServer(
                work,
                (server, player) -> {
                    double beforeX = player.getX();
                    double beforeY = player.getY();
                    double beforeZ = player.getZ();
                    player.teleportTo(x, y, z);
                    JsonObject json =
                            mutationEvidence(
                                    "fixture.player.teleport", "FIXTURE", "SERVER_API", true);
                    json.addProperty("beforeX", beforeX);
                    json.addProperty("beforeY", beforeY);
                    json.addProperty("beforeZ", beforeZ);
                    json.addProperty("x", player.getX());
                    json.addProperty("y", player.getY());
                    json.addProperty("z", player.getZ());
                    json.addProperty("serverTick", server.getTickCount());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> debugSetHealth(
            float health, AgentControlSession.OperateWork work) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("health", health);
            return this.operatingPeerRequest(work, "debug.player.health", params);
        }
        return this.onOperatingServer(
                work,
                (server, player) -> {
                    float before = player.getHealth();
                    float applied = Mth.clamp(health, 0.0F, player.getMaxHealth());
                    player.setHealth(applied);
                    JsonObject json =
                            mutationEvidence(
                                    "debug.player.health",
                                    "DEBUG_PRIVILEGED",
                                    "DIRECT_MUTATION",
                                    true);
                    json.addProperty("before", before);
                    json.addProperty("requested", health);
                    json.addProperty("applied", player.getHealth());
                    json.addProperty("serverTick", server.getTickCount());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> debugSetBlock(
            int x,
            int y,
            int z,
            String blockId,
            String expectedBlockId,
            AgentControlSession.OperateWork work) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("x", x);
            params.addProperty("y", y);
            params.addProperty("z", z);
            params.addProperty("blockId", blockId);
            if (expectedBlockId != null) params.addProperty("expectedBlockId", expectedBlockId);
            return this.operatingPeerRequest(work, "debug.world.block", params);
        }
        return this.onOperatingServer(
                work,
                (server, player) -> {
                    ServerLevel level = player.level();
                    BlockPos position = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(position)) {
                        throw new ProtocolState.ProtocolException(
                                "CHUNK_NOT_LOADED", 409, "Debug block target is not loaded");
                    }
                    Identifier id = Identifier.tryParse(blockId);
                    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                        throw new ProtocolState.ProtocolException(
                                "UNKNOWN_BLOCK", 400, "Unknown block: " + blockId);
                    }
                    BlockState before = level.getBlockState(position);
                    String beforeId = BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString();
                    if (expectedBlockId != null && !expectedBlockId.equals(beforeId)) {
                        throw new ProtocolState.ProtocolException(
                                "PRECONDITION_FAILED",
                                409,
                                "Expected block " + expectedBlockId + " but found " + beforeId);
                    }
                    Block block = BuiltInRegistries.BLOCK.getValue(id);
                    level.setBlockAndUpdate(position, block.defaultBlockState());
                    JsonObject json =
                            mutationEvidence(
                                    "debug.world.block",
                                    "DEBUG_PRIVILEGED",
                                    "DIRECT_MUTATION",
                                    true);
                    json.addProperty("x", x);
                    json.addProperty("y", y);
                    json.addProperty("z", z);
                    json.addProperty("before", beforeId);
                    json.addProperty(
                            "after",
                            BuiltInRegistries.BLOCK
                                    .getKey(level.getBlockState(position).getBlock())
                                    .toString());
                    json.addProperty("serverTick", server.getTickCount());
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> phase9aInventory() {
        return CompletableFuture.completedFuture(this.phase9a.inventory());
    }

    @Override
    public CompletableFuture<JsonObject> phase9aObserve(JsonObject request) {
        return this.onIntegratedServer(
                (server, player) -> this.phase9a.observe(server, player, request));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aStorageRead(JsonObject request) {
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.onClient(
                        () -> {
                            Minecraft client = requireClient();
                            return client.level == null
                                    && client.getSingleplayerServer() == null
                                    && client.getConnection() == null;
                        }),
                offline ->
                        offline
                                ? this.phase9a.readSavedStorage(request)
                                : io.github.recrivenvi.minecraftprotocol.safety.CancellableWork
                                        .compose(
                                                this.onIntegratedServer(
                                                        (server, player) ->
                                                                this.phase9a.storageRequest(
                                                                        server, player, request)),
                                                this.phase9a::readStorage));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugAttribute(
            String attributeId, double value, AgentControlSession.OperateWork work) {
        return this.onOperatingServer(
                work, (server, player) -> this.phase9a.debugAttribute(player, attributeId, value));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugEntityState(
            String entityUuid, String state, boolean value, AgentControlSession.OperateWork work) {
        return this.onOperatingServer(
                work,
                (server, player) ->
                        this.phase9a.debugEntityState(player, entityUuid, state, value));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugScenario(
            JsonObject request, AgentControlSession.OperateWork work) {
        return this.onOperatingServer(
                work, (server, player) -> this.phase9a.debugScenario(player, request));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aKeyframe(JsonObject request) {
        return this.onIntegratedServer(
                (server, player) -> this.phase9a.keyframe(server, player, request));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDelta(String baseSnapshotId) {
        return this.onIntegratedServer(
                (server, player) -> this.phase9a.captureDelta(server, player, baseSnapshotId));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aReconstruct(JsonObject request) {
        return CompletableFuture.completedFuture(this.phase9a.reconstruct(request));
    }

    @Override
    public CompletableFuture<JsonObject> formalObservationCapabilities() {
        return CompletableFuture.completedFuture(this.phase9a.formalCapabilities());
    }

    @Override
    public CompletableFuture<JsonObject> formalDeepObservation(
            JsonObject request, DeepObservationRequestContext requestContext) {
        Phase9ASpikeEngine.formalChunkRadius(request);
        String perspective =
                request.has("perspective")
                        ? request.get("perspective").getAsString()
                        : "server_authoritative";
        if (perspective.equals("client_known")) {
            return this.playerState()
                    .thenCompose(
                            client ->
                                    this.phase9a.formalize(request, client, null, requestContext));
        }
        CompletableFuture<JsonObject> server =
                this.onIntegratedServer(
                                (minecraftServer, player) ->
                                        this.phase9a.captureFormal(
                                                minecraftServer, player, request))
                        .thenCompose(
                                canonical ->
                                        this.phase9a.prepareFormalServerSnapshot(
                                                request, canonical));
        if (perspective.equals("server_authoritative")) {
            return server.thenCompose(
                    value -> this.phase9a.formalize(request, null, value, requestContext));
        }
        if (!perspective.equals("both")) {
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "INVALID_PERSPECTIVE",
                            400,
                            "perspective must be client_known, server_authoritative, or both"));
        }
        CompletableFuture<JsonObject> client = this.playerState();
        return client.thenCombine(
                        server,
                        (clientValue, serverValue) -> new JsonObject[] {clientValue, serverValue})
                .thenCompose(
                        values ->
                                this.phase9a.formalize(
                                        request, values[0], values[1], requestContext));
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugCapabilities() {
        JsonObject result = this.phase9a.formalDebugCapabilities();
        result.add(
                "resourceInspection",
                io.github.recrivenvi.minecraftprotocol.core.WorldDebugInspection.contract());
        for (String operation : UiDebugEngine.OPERATIONS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("operation", operation);
            entry.addProperty("status", "available");
            entry.addProperty("authority", "CLIENT_UI");
            entry.addProperty("requiredScope", "debug.ui");
            entry.addProperty("requiresArm", true);
            entry.addProperty("resourceVersionPrecondition", true);
            entry.addProperty("batch", true);
            result.getAsJsonArray("operations").add(entry);
        }
        result.addProperty("uiRequiresWorldFingerprint", false);
        result.addProperty("uiRequiresSessionContext", true);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<JsonObject> uiDebugContext() {
        return this.onClient(
                () -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("uiContextId", "ui-session:" + this.phase9a.sessionEpoch());
                    result.addProperty("sessionEpoch", this.phase9a.sessionEpoch());
                    return result;
                });
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugMutation(
            JsonObject request, DebugMutationAuthorization authorization) {
        if (request.has("operation") && request.get("operation").getAsString().startsWith("ui.")) {
            return this.onClient(
                    () -> {
                        JsonObject result =
                                UiDebugEngine.mutate(
                                        requireClient().gui.screen(),
                                        this.snapshotUiTree(),
                                        request,
                                        this::interactionIdentity,
                                        authorization,
                                        this.itemDataGuard);
                        JsonObject after =
                                UiDebugEngine.findNode(
                                        this.snapshotUiTree(), result.get("nodeId").getAsString());
                        if (after != null) {
                            result.add("after", after);
                            result.add("afterResourceVersion", after.get("resourceVersion"));
                        } else {
                            result.addProperty("postMutationContextChanged", true);
                            result.add("after", com.google.gson.JsonNull.INSTANCE);
                            result.add("afterResourceVersion", com.google.gson.JsonNull.INSTANCE);
                        }
                        return result;
                    });
        }
        if (this.shouldUsePeer()) {
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "CAPABILITY_UNAVAILABLE",
                            409,
                            "Phase 9C resource-version Debug requires Integrated Server authority; "
                                    + "the Dedicated Peer currently retains only legacy typed health/block Debug"));
        }
        String operation = request.has("operation") ? request.get("operation").getAsString() : "";
        if (operation.equals("provider.mutate")) {
            return this.phase9a.debugProviderMutation(request, authorization);
        }
        return this.onIntegratedServer(
                (server, player) -> {
                    String domain =
                            operation.contains(".")
                                    ? operation.substring(0, operation.indexOf('.'))
                                    : "unknown";
                    String fingerprint = this.phase9cWorldFingerprint(server, player);
                    try (DebugMutationAuthorization.Permit ignored =
                            authorization.authorize(
                                    fingerprint, this.phase9a.sessionEpoch(), domain, domain)) {
                        return this.phase9a.debugMutation(server, player, request, fingerprint);
                    }
                });
    }

    @Override
    public CompletableFuture<JsonObject> peerStatus() {
        return this.onClient(
                () -> {
                    JsonObject json = DedicatedPeerClient.status();
                    json.addProperty("target", "26.3-neoforge");
                    json.addProperty("clientTick", this.clientTick);
                    json.addProperty(
                            "integratedServer", requireClient().getSingleplayerServer() != null);
                    return json;
                });
    }

    @Override
    public CompletableFuture<JsonObject> peerProbe() {
        return this.peerRequest("peer.status", new JsonObject());
    }

    private static void encodeScreenshot(NativeImage image, CompletableFuture<byte[]> result) {
        Path path = null;
        try (image) {
            path = Files.createTempFile("minecraft-protocol-probe-", ".png");
            image.writeToFile(path);
            result.complete(Files.readAllBytes(path));
            INSTANCE.captureVerified.set(true);
        } catch (IOException exception) {
            result.completeExceptionally(exception);
        } finally {
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public CompletableFuture<JsonObject> waitForScreen(String classContains, long timeoutMillis) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        long boundedTimeout = Math.max(1L, Math.min(timeoutMillis, 60_000L));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundedTimeout);
        Runnable check =
                new Runnable() {
                    @Override
                    public void run() {
                        if (result.isDone()) return;
                        session()
                                .whenComplete(
                                        (json, error) -> {
                                            if (result.isDone()) return;
                                            if (error != null) result.completeExceptionally(error);
                                            else if (json.get("screenClass")
                                                    .getAsString()
                                                    .contains(classContains)) {
                                                json.addProperty(
                                                        "condition", "screen_class_contains");
                                                result.complete(json);
                                            } else if (System.nanoTime() >= deadline) {
                                                result.completeExceptionally(
                                                        new ProtocolState.ProtocolException(
                                                                "WAIT_TIMEOUT",
                                                                408,
                                                                "wait.until timed out"));
                                            } else {
                                                WAITS.schedule(this, 25L, TimeUnit.MILLISECONDS);
                                            }
                                        });
                    }
                };
        check.run();
        return result;
    }

    @Override
    public CompletableFuture<JsonObject> validatePreconditions(
            Long expectedScreenRevision, Long expectedMenuRevision) {
        return this.onClient(
                () -> {
                    this.checkInputPreconditions(expectedScreenRevision, expectedMenuRevision);
                    JsonObject json = base("preconditions");
                    json.addProperty("status", "satisfied");
                    return json;
                });
    }

    private void checkInputPreconditions(Long expectedScreenRevision, Long expectedMenuRevision) {
        Minecraft client = requireClient();
        this.refreshScreen(client);
        if (expectedMenuRevision != null) this.refreshMenu(client);
        if (expectedScreenRevision != null && expectedScreenRevision != this.screenRevision)
            throw new ProtocolState.ProtocolException(
                    "STALE_SCREEN_REVISION", 409, "Screen revision changed before input dispatch");
        if (expectedMenuRevision != null && expectedMenuRevision != this.menuRevision)
            throw new ProtocolState.ProtocolException(
                    "STALE_MENU_REVISION", 409, "Menu revision changed before input dispatch");
    }

    @Override
    public CompletableFuture<JsonObject> inputState() {
        return this.onClient(
                () -> {
                    JsonObject json = base("input.state");
                    JsonArray keys = new JsonArray();
                    this.pressedKeys.keySet().stream().sorted().forEach(keys::add);
                    JsonArray buttons = new JsonArray();
                    this.pressedButtons.stream().sorted().forEach(buttons::add);
                    json.add("pressedKeys", keys);
                    json.add("pressedButtons", buttons);
                    json.addProperty("pressedKeyCount", this.pressedKeys.size());
                    json.addProperty("pressedButtonCount", this.pressedButtons.size());
                    json.addProperty("inputDispatchSequence", this.inputDispatchSequence.get());
                    this.addNativeEntityAttackState(json);
                    this.addControlPresence(json);
                    return json;
                });
    }

    private void addNativeEntityAttackState(JsonObject json) {
        var context = this.nativeEntityAttackContext;
        ProbeTransport current = this.transport;
        json.addProperty("nativeEntityAttackCount", this.nativeEntityAttackCount);
        json.addProperty("nativeEntityAttackScope", "native_multiplayer_game_mode_attack_return");
        json.addProperty(
                "nativeEntityAttackCounterUnits",
                "native_entity_attack_invocations_not_damage_or_swing");
        json.addProperty("nativeEntityAttackContextId", context == null ? "" : context.opaqueId());
        json.addProperty(
                "nativeEntityAttackContextGeneration", context == null ? 0 : context.generation());
        json.addProperty(
                "nativeEntityAttackControlGeneration", this.nativeEntityAttackControlGeneration);
        json.addProperty(
                "nativeEntityAttackLeaseActive",
                current != null && current.nativeInputLeaseActive(context));
        json.addProperty("nativeEntityAttackServerEffectConfirmed", false);
    }

    @Override
    public CompletableFuture<JsonObject> threadProbe(String affinity) {
        if (affinity.equals("client") || affinity.equals("render")) {
            return this.onClient(
                    () -> {
                        Minecraft client = requireClient();
                        JsonObject json = base("diagnostics.thread");
                        json.addProperty("requestedAffinity", affinity);
                        json.addProperty("thread", Thread.currentThread().getName());
                        json.addProperty(
                                "ownerThreadObserved",
                                affinity.equals("render")
                                        ? RenderSystem.isOnRenderThread()
                                        : client.isSameThread());
                        return json;
                    });
        }
        if (!affinity.equals("server")) {
            return CompletableFuture.failedFuture(
                    new ProtocolState.ProtocolException(
                            "INVALID_THREAD_AFFINITY",
                            400,
                            "Affinity must be client, render or server"));
        }
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        Minecraft client = this.minecraft;
        if (client == null) {
            result.completeExceptionally(
                    new ProtocolState.ProtocolException(
                            "RUNTIME_NOT_READY", 409, "Client runtime is not ready"));
            return result;
        }
        client.execute(
                () -> {
                    MinecraftServer server = client.getSingleplayerServer();
                    if (server == null) {
                        result.completeExceptionally(
                                new ProtocolState.ProtocolException(
                                        "SERVER_UNAVAILABLE",
                                        409,
                                        "No integrated server is active"));
                        return;
                    }
                    server.execute(
                            () -> {
                                JsonObject json = base("diagnostics.thread");
                                json.addProperty("requestedAffinity", "server");
                                json.addProperty("thread", Thread.currentThread().getName());
                                json.addProperty("ownerThreadObserved", server.isSameThread());
                                result.complete(json);
                            });
                });
        return result;
    }

    @Override
    public CompletableFuture<JsonObject> openAutomationProbeScreen(
            AgentControlSession.OperateWork work) {
        return this.onOperatingClient(
                work,
                () -> {
                    Minecraft client = requireClient();
                    client.setScreenAndShow(new AutomationProbeScreen());
                    Screen screen = this.refreshScreen(client);
                    JsonObject json = base("fixture.ui.opened");
                    json.addProperty(
                            "screenClass", screen == null ? "" : screen.getClass().getName());
                    json.addProperty("mechanism", "DIRECT");
                    json.addProperty("perspective", "fixture");
                    json.addProperty("evidenceContaminated", true);
                    return json;
                });
    }

    private static int pendingNativeClicks(net.minecraft.client.KeyMapping mapping) {
        return ((io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor) mapping)
                .minecraftProtocolProbe$pendingClicks();
    }

    private void trackNativeInput(Minecraft client, Runnable dispatch) {
        this.nativeInputOwnership.track(
                java.util.Arrays.asList(client.options.keyMappings),
                NeoForgeProbeRuntime::pendingNativeClicks,
                net.minecraft.client.KeyMapping::isDown,
                dispatch);
    }

    private void finishNativeInputCleanup(String reason) {
        // Mouse-up clears isDown, not Vanilla's queued clickCount. A stop must clear both,
        // while successful completion may still have an already submitted native click.
        if ("pipeline_completed".equals(reason)) {
            this.nativeInputOwnership.retireConsumed(
                    NeoForgeProbeRuntime::pendingNativeClicks,
                    net.minecraft.client.KeyMapping::isDown);
        } else {
            this.nativeInputOwnership.releaseOwned(
                    mapping ->
                            ((io.github.recrivenvi.minecraftprotocol.probe.mixin
                                                    .KeyBindingUiAccessor)
                                            mapping)
                                    .minecraftProtocolProbe$release());
        }
    }

    @Override
    public CompletableFuture<JsonObject> releaseAllInput(String reason) {
        return this.onClientCleanup(
                () -> {
                    Minecraft client = requireClient();
                    if (this.pressedKeys.containsKey(
                                    NativeInputMapping.wireKey(
                                            com.mojang.blaze3d.platform.InputConstants.KEY_F3))
                            && client.gui.screen()
                                    instanceof
                                    net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen)
                        client.gui.setScreen(null);
                    if (reason.equals("native_input_context_changed"))
                        return this.discardContextInput(client);
                    this.worldTargeting.end();
                    if (this.commandChatOpening != null) {
                        // Drop only this gesture's queued native Chat key action, never all key
                        // mappings.
                        ((io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyBindingUiAccessor)
                                        this.commandChatOpening)
                                .minecraftProtocolProbe$release();
                        this.commandChatOpening = null;
                    }
                    this.commandChat.clear();
                    this.commandFeedback.clear();
                    int releasedKeys = this.pressedKeys.size();
                    int releasedButtons = this.pressedButtons.size();
                    for (Map.Entry<Integer, KeyState> entry :
                            new HashMap<>(this.pressedKeys).entrySet()) {
                        KeyState state = entry.getValue();
                        KeyEvent nativeEvent =
                                NativeInputMapping.event(entry.getKey(), state.modifiers());
                        AgentInputContext.dispatch(
                                AgentInputContext.Event.key(
                                        client.getWindow().handle(),
                                        nativeEvent.key(),
                                        nativeEvent.keycode(),
                                        0,
                                        nativeEvent.modifiers()),
                                () ->
                                        ((KeyboardHandlerInvoker) client.keyboardHandler)
                                                .minecraftProtocolProbe$keyPress(
                                                        client.getWindow().handle(),
                                                        0,
                                                        nativeEvent));
                    }
                    for (int button : new HashSet<>(this.pressedButtons)) {
                        if (this.buttonScreens.containsKey(button)
                                && (!java.util.Objects.equals(
                                                this.buttonFrames.get(button),
                                                this.pointerFrameStamp(client))
                                        || this.buttonScreens.get(button) != client.gui.screen())) {
                            this.clearMouseButtonState(client);
                            continue;
                        }
                        AgentInputContext.dispatch(
                                AgentInputContext.Event.button(
                                        client.getWindow().handle(),
                                        NativeInputMapping.button(button),
                                        0,
                                        0),
                                () ->
                                        ((MouseHandlerInvoker) client.mouseHandler)
                                                .minecraftProtocolProbe$onButton(
                                                        client.getWindow().handle(),
                                                        new MouseButtonInfo(
                                                                NativeInputMapping.button(button),
                                                                0),
                                                        0));
                    }
                    this.finishNativeInputCleanup(reason);
                    this.pressedKeys.clear();
                    this.pressedButtons.clear();
                    if (releasedButtons > 0) this.logicalPointer.releaseButtons();
                    this.buttonScreens.clear();
                    this.buttonFrames.clear();
                    this.activeGestureId = "";
                    long inputSequence = this.inputDispatchSequence.incrementAndGet();
                    JsonObject json = base("input.release_all");
                    json.addProperty("reason", reason);
                    json.addProperty("releasedKeys", releasedKeys);
                    json.addProperty("releasedButtons", releasedButtons);
                    json.addProperty("inputDispatchSequence", inputSequence);
                    return json;
                });
    }

    private CompletableFuture<ProviderExecutionEngine.Entry> dispatchProvider(
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2 provider,
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2.ReadContext
                    context,
            String affinity) {
        return switch (affinity) {
            case "client_thread" ->
                    this.onClient(
                            () ->
                                    ProviderExecutionEngine.enter(
                                            provider, context, requireClient().isSameThread()));
            case "server_thread" ->
                    this.onIntegratedServer(
                            (server, player) ->
                                    ProviderExecutionEngine.enter(
                                            provider, context, server.isSameThread()));
            case "render_thread" ->
                    this.onClient(
                            () ->
                                    ProviderExecutionEngine.enter(
                                            provider,
                                            context,
                                            com.mojang.blaze3d.systems.RenderSystem
                                                    .isOnRenderThread()));
            default ->
                    CompletableFuture.completedFuture(
                            ProviderExecutionEngine.Entry.unsupported(affinity));
        };
    }

    private CompletableFuture<ProviderExecutionEngine.MutationEntry> dispatchProviderMutation(
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2 provider,
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2.DebugContext
                    context,
            String affinity,
            DebugMutationAuthorization authorization) {
        if (!"server_thread".equals(affinity)) {
            return CompletableFuture.completedFuture(
                    ProviderExecutionEngine.MutationEntry.unsupported(affinity));
        }
        return this.onIntegratedServer(
                (server, player) -> {
                    String fingerprint = this.phase9cWorldFingerprint(server, player);
                    DebugMutationAuthorization.Permit permit =
                            authorization.authorize(
                                    fingerprint,
                                    this.phase9a.sessionEpoch(),
                                    "provider",
                                    "provider");
                    return ProviderExecutionEngine.enterMutation(
                            provider, context, server.isSameThread(), permit);
                });
    }

    private <T> CompletableFuture<T> onOperatingClient(
            AgentControlSession.OperateWork work, Supplier<T> supplier) {
        return this.onClient(() -> work.call(supplier));
    }

    private <T> CompletableFuture<T> onOperatingServer(
            AgentControlSession.OperateWork work,
            BiFunction<MinecraftServer, ServerPlayer, T> operation) {
        return this.onIntegratedServer(
                (server, player) -> work.call(() -> operation.apply(server, player)));
    }

    private CompletableFuture<JsonObject> operatingPeerRequest(
            AgentControlSession.OperateWork work, String operation, JsonObject params) {
        AgentControlSession.Guard guard = work.enter();
        CompletableFuture<JsonObject> source;
        try {
            source = this.peerRequest(operation, params);
        } catch (Throwable error) {
            guard.close();
            throw error;
        }
        // Retain admission until native completion even if the HTTP caller cancels.
        source.whenComplete((value, error) -> guard.close());
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        source.whenComplete(
                (value, error) -> {
                    if (error == null) response.complete(value);
                    else response.completeExceptionally(error);
                });
        return response;
    }

    private <T> CompletableFuture<T> onControlledClient(Supplier<T> supplier) {
        return this.onControlledClient(supplier, true);
    }

    private <T> CompletableFuture<T> onControlledClient(Supplier<T> supplier, boolean input) {
        AgentControlSession.Snapshot accepted = this.controlPresence;
        var permit = io.github.recrivenvi.minecraftprotocol.core.InputDispatchContext.capture();
        return this.onClient(
                () -> {
                    this.refreshInputContext(requireClient());
                    AgentControlSession.Snapshot current = this.controlPresence;
                    if (!current.agentControlled()
                            || !accepted.agentControlled()
                            || current.transitionSequence() != accepted.transitionSequence()) {
                        throw new ProtocolState.ProtocolException(
                                current.manuallyRevoked()
                                        ? "USER_MANUALLY_ENDED_CONTROL"
                                        : "CONTROL_LEASE_REQUIRED",
                                409,
                                current.manuallyRevoked()
                                        ? "用户手动结束控制"
                                        : "Control session ended before dispatch");
                    }
                    ensureExclusiveOwnerState();
                    return this.controlSession.withTakeover(
                            accepted,
                            () -> {
                                try (var dispatch = this.commandGate.enter(accepted, permit)) {
                                    if (input && permit != null)
                                        permit.beforeInput(this::checkInputPreconditions);
                                    T result = supplier.get();
                                    dispatch.check();
                                    return result;
                                }
                            });
                });
    }

    private <T> CompletableFuture<T> onClientCleanup(Supplier<T> supplier) {
        Minecraft client = this.minecraft;
        if (client != null && client.isSameThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable error) {
                return CompletableFuture.failedFuture(error);
            }
        }
        return this.onClient(supplier);
    }

    private <T> CompletableFuture<T> onClient(Supplier<T> supplier) {
        Minecraft client = this.minecraft;
        CompletableFuture<T> result = new CompletableFuture<>();
        if (client == null) {
            result.completeExceptionally(new IllegalStateException("Client runtime is not ready"));
            return result;
        }
        client.execute(
                () -> {
                    if (result.isDone()) return;
                    try {
                        this.refreshInputContext(client);
                        if (result.isDone()) return;
                        result.complete(supplier.get());
                    } catch (Throwable throwable) {
                        result.completeExceptionally(throwable);
                    }
                });
        return result;
    }

    private boolean shouldUsePeer() {
        Minecraft client = this.minecraft;
        return client != null
                && client.getConnection() != null
                && (client.getSingleplayerServer() == null || peerForceEnabled());
    }

    private static boolean peerForceEnabled() {
        String environment = System.getenv("MCP_PEER_FORCE");
        return Boolean.getBoolean("minecraft.protocol.peer.force")
                || "1".equals(environment)
                || "true".equalsIgnoreCase(environment);
    }

    private CompletableFuture<JsonObject> peerRequest(String operation, JsonObject params) {
        return DedicatedPeerClient.request(operation, params)
                .thenApply(
                        json -> {
                            json.addProperty("target", "26.3-neoforge");
                            json.addProperty("clientTick", this.clientTick);
                            return json;
                        });
    }

    private <T> CompletableFuture<T> onIntegratedServer(
            BiFunction<MinecraftServer, ServerPlayer, T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Minecraft client = this.minecraft;
        if (client == null) {
            result.completeExceptionally(
                    new ProtocolState.ProtocolException(
                            "RUNTIME_NOT_READY", 409, "Client runtime is not ready"));
            return result;
        }
        client.execute(
                () -> {
                    MinecraftServer server = client.getSingleplayerServer();
                    java.util.UUID playerId =
                            client.player == null ? null : client.player.getUUID();
                    if (server == null || playerId == null) {
                        result.completeExceptionally(
                                new ProtocolState.ProtocolException(
                                        "SERVER_AUTHORITATIVE_UNAVAILABLE",
                                        409,
                                        "Integrated Server authority is not available"));
                        return;
                    }
                    server.execute(
                            () -> {
                                try {
                                    ServerPlayer player =
                                            server.getPlayerList().getPlayer(playerId);
                                    if (player == null) {
                                        throw new ProtocolState.ProtocolException(
                                                "SERVER_PLAYER_UNAVAILABLE",
                                                409,
                                                "The current client player is not active on the Integrated Server");
                                    }
                                    result.complete(operation.apply(server, player));
                                } catch (Throwable throwable) {
                                    result.completeExceptionally(throwable);
                                }
                            });
                });
        return result;
    }

    private String phase9cWorldFingerprint(MinecraftServer server, ServerPlayer player) {
        String material =
                "26.3-neoforge|"
                        + server.getWorldData().getLevelName()
                        + "|"
                        + player.level().dimension().identifier();
        return sha256(material);
    }

    private Minecraft requireClient() {
        Minecraft client = this.minecraft;
        if (client == null) throw new IllegalStateException("Client runtime is not ready");
        return client;
    }

    private JsonObject base(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("target", "26.3-neoforge");
        json.addProperty("clientTick", this.clientTick);
        return json;
    }

    private static void addLiveMetadata(
            JsonObject json, String perspective, String source, String authority) {
        json.addProperty("perspective", perspective);
        json.addProperty("source", source);
        json.addProperty("authority", authority);
        json.addProperty("dataSource", "LIVE");
        json.addProperty("storageAccessed", false);
        json.addProperty("stalePossible", authority.equals("client_observed"));
    }

    private static void addPlayerFields(JsonObject json, Player player) {
        json.addProperty("uuid", player.getUUID().toString());
        json.addProperty("x", player.getX());
        json.addProperty("y", player.getY());
        json.addProperty("z", player.getZ());
        json.addProperty("yaw", player.getYRot());
        json.addProperty("pitch", player.getXRot());
        json.addProperty("health", player.getHealth());
        json.addProperty("maxHealth", player.getMaxHealth());
        json.addProperty("absorption", player.getAbsorptionAmount());
        json.addProperty("food", player.getFoodData().getFoodLevel());
        json.addProperty("air", player.getAirSupply());
        json.addProperty("velocityX", player.getDeltaMovement().x);
        json.addProperty("velocityY", player.getDeltaMovement().y);
        json.addProperty("velocityZ", player.getDeltaMovement().z);
    }

    private static JsonObject entityState(Entity entity) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", entity.getUUID().toString());
        json.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        json.addProperty("x", entity.getX());
        json.addProperty("y", entity.getY());
        json.addProperty("z", entity.getZ());
        json.addProperty("velocityX", entity.getDeltaMovement().x);
        json.addProperty("velocityY", entity.getDeltaMovement().y);
        json.addProperty("velocityZ", entity.getDeltaMovement().z);
        return json;
    }

    private JsonObject mutationEvidence(
            String type, String mode, String mechanism, boolean contaminated) {
        JsonObject json = base(type);
        json.addProperty("mode", mode);
        json.addProperty("perspective", "server_authoritative_live");
        json.addProperty("mechanism", mechanism);
        json.addProperty("evidenceContaminated", contaminated);
        json.addProperty("directMutationUsed", mechanism.equals("DIRECT_MUTATION"));
        json.addProperty("storageAccessed", false);
        return json;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private JsonObject inputEvidence(String entryLayer, boolean screen, boolean menu) {
        JsonObject json = base("input.result");
        long inputSequence = this.inputDispatchSequence.incrementAndGet();
        json.addProperty("entryLayer", entryLayer);
        json.addProperty("gestureId", this.activeGestureId);
        json.addProperty("inputOrigin", "AGENT_ROUTED");
        json.addProperty("hostCursorUsed", false);
        json.addProperty("screenObserved", screen);
        json.addProperty("menuObserved", menu);
        json.addProperty("normalPacketObserved", false);
        json.addProperty("serverValidationObserved", false);
        json.addProperty("directBusinessCallUsed", false);
        json.addProperty("directMutationUsed", false);
        json.addProperty("screenRevision", this.screenRevision);
        json.addProperty("menuRevision", this.menuRevision);
        json.addProperty("inputDispatchSequence", inputSequence);
        return json;
    }

    static void addSlotIdentity(
            JsonObject node,
            Slot slot,
            int viewIndex,
            long screenRevision,
            long menuIdentity,
            long elementIdentity) {
        // Creative wrappers may all have index=0. Only the screen menu's ordered view is unique.
        node.addProperty("slot", viewIndex);
        node.addProperty("nativeSlotIndex", slot.index);
        node.addProperty("elementIdentity", elementIdentity);
        node.addProperty(
                "nodeId",
                "slot:"
                        + screenRevision
                        + ":"
                        + menuIdentity
                        + ":"
                        + elementIdentity
                        + ":"
                        + viewIndex);
    }

    static void addSlotOwner(
            JsonObject node, Slot slot, net.minecraft.world.Container playerInventory) {
        node.addProperty(
                "slotOwner",
                playerInventory == null || slot.container == null
                        ? "unknown"
                        : slot.container == playerInventory ? "player_inventory" : "menu");
    }

    static boolean addSlotVisibility(JsonObject node, int width, int height) {
        boolean visible =
                node.get("x").getAsInt() < width
                        && node.get("y").getAsInt() < height
                        && node.get("x").getAsInt() + node.get("width").getAsInt() > 0
                        && node.get("y").getAsInt() + node.get("height").getAsInt() > 0;
        node.addProperty("visible", visible);
        return visible;
    }

    static void addSlotBounds(JsonObject node, int itemX, int itemY) {
        // Vanilla's 18x18 hit box surrounds the 16x16 item, not its top-left origin.
        node.addProperty("x", itemX - 1);
        node.addProperty("y", itemY - 1);
        node.addProperty("width", 18);
        node.addProperty("height", 18);
    }

    static void addInteractionMetadata(JsonObject node, String role, boolean actionable) {
        if (AutomationEngine.hasBounds(node)) {
            JsonObject point = AutomationEngine.interactionPoint(node);
            node.add("interactionX", point.get("x"));
            node.add("interactionY", point.get("y"));
        }
        JsonArray actions = new JsonArray();
        if (actionable) {
            actions.add("click");
            actions.add("mouse_down");
            actions.add("mouse_up");
            if (role.equals("slot")) {
                actions.add("right_click");
                actions.add("shift_click");
            }
            if (role.equals("list") || role.equals("slider")) actions.add("scroll");
        }
        node.add("actions", actions);
    }

    private synchronized Screen refreshScreen(Minecraft client) {
        Screen screen = client.gui.screen();
        if (screen != this.lastScreen) {
            this.lastScreen = screen;
            this.screenRevision++;
            synchronized (this.recentRenderFacts) {
                this.recentRenderFacts.clear();
            }
            ProbeTransport currentTransport = this.transport;
            if (currentTransport != null) {
                JsonObject event = base("event.screen.changed");
                event.addProperty("screenClass", screen == null ? "" : screen.getClass().getName());
                event.addProperty("screenRevision", this.screenRevision);
                currentTransport.broadcast(event);
            }
        }
        return screen;
    }

    private void recordRenderFact(
            String category, String stateClass, int x, int y, int width, int height) {
        long sequence = this.renderFactSequence.incrementAndGet();
        this.lastRenderFact =
                category
                        + " class="
                        + stateClass
                        + " bounds="
                        + x
                        + ","
                        + y
                        + ","
                        + width
                        + ","
                        + height;
        synchronized (this.recentRenderFacts) {
            this.recentRenderFacts.addLast(
                    new RenderFact(
                            sequence,
                            this.clientTick,
                            this.screenRevision,
                            category,
                            stateClass,
                            x,
                            y,
                            width,
                            height));
            while (this.recentRenderFacts.size() > 256) this.recentRenderFacts.removeFirst();
        }
    }

    private synchronized void refreshMenu(Minecraft client) {
        AbstractContainerMenu menu =
                client.gui.screen() instanceof AbstractContainerScreen<?> container
                        ? container.getMenu()
                        : client.player == null ? null : client.player.containerMenu;
        String menuHash = menuHash(menu);
        if (menu != this.lastMenu) {
            this.lastMenu = menu;
            this.lastMenuHash = menuHash;
            this.menuRevision++;
            return;
        }
        if (menuContentIncomplete || !menuHash.equals(this.lastMenuHash)) {
            this.lastMenuHash = menuHash;
            this.menuRevision++;
        }
    }

    private String menuHash(AbstractContainerMenu menu) {
        slotContents.clear();
        menuContentIncomplete = false;
        carriedContent = itemDataGuard.empty();
        if (menu == null) return "";
        var budget = new UiItemDataGuard.Budget();
        StringBuilder data =
                new StringBuilder().append(menu.containerId).append(':').append(menu.slots.size());
        int limit = Math.min(menu.slots.size(), UiItemDataGuard.MAX_SLOTS);
        menuContentIncomplete = limit != menu.slots.size();
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            var content = itemContent(stack, budget);
            slotContents.put(slot, content);
            menuContentIncomplete |= !content.available();
            if (menu instanceof CrafterMenu crafter && i < 9)
                data.append(":disabled:").append(crafter.isSlotDisabled(i));
            data.append(':')
                    .append(this.interactionIdentity(slot))
                    .append(':')
                    .append(slot.x)
                    .append(':')
                    .append(slot.y)
                    .append(':')
                    .append(stack.getCount())
                    .append(':')
                    .append(content.fingerprint());
        }
        carriedContent = itemContent(menu.getCarried(), budget);
        menuContentIncomplete |= !carriedContent.available();
        data.append(":carried:")
                .append(menu.getCarried().getCount())
                .append(':')
                .append(carriedContent.fingerprint());
        return itemDataGuard.signText(data.toString());
    }

    private UiItemDataGuard.Content itemContent(ItemStack stack, UiItemDataGuard.Budget budget) {
        if (stack.isEmpty()) return itemDataGuard.empty();
        var connection = requireClient().getConnection();
        if (connection == null) return UiItemDataGuard.Content.unavailable();
        return itemDataGuard.capture(
                budget,
                buffer ->
                        ItemStack.STREAM_CODEC.encode(
                                new net.minecraft.network.RegistryFriendlyByteBuf(
                                        buffer, connection.registryAccess()),
                                stack.copyWithCount(1)));
    }

    private void addItemContent(JsonObject node, UiItemDataGuard.Content content, String context) {
        node.addProperty("contentGuardAvailable", content.available());
        node.addProperty(
                "contentRevision",
                itemDataGuard.revision(context + ":" + node.get("count").getAsInt(), content));
        // Only the internal pointer snapshot/DEBUG graph retains the cross-slot fingerprint.
        // UiDataView filters it out of USER trees and action results.
        node.addProperty("contentFingerprint", content.fingerprint());
    }

    private static JsonObject operatorHook(
            String id, String target, boolean cancellable, boolean replacement, String observed) {
        JsonObject result =
                hook(
                        id,
                        replacement ? "MIXIN_REDIRECT" : "MIXIN_INJECT",
                        target,
                        "EXPLICIT_OPERATOR_BOUNDARY",
                        "operator_control",
                        observed,
                        "control.agent_presence");
        result.addProperty("plane", "OPERATOR_CONTROL");
        result.addProperty("cancellable", cancellable);
        result.addProperty("replacement", replacement);
        result.addProperty("intentionalControlFlow", cancellable || replacement);
        return result;
    }

    private static JsonObject hook(
            String id,
            String mechanism,
            String target,
            String injectionPoint,
            String behavior,
            String runtimeStatus,
            String failureCapability) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("mechanism", mechanism);
        json.addProperty("target", target);
        json.addProperty("injectionPoint", injectionPoint);
        json.addProperty("behavior", behavior);
        json.addProperty("cancellable", false);
        json.addProperty("overwrite", false);
        json.addProperty("thirdPartyTarget", false);
        json.addProperty("runtimeStatus", runtimeStatus);
        json.addProperty("failureMode", "capability_degraded_or_unavailable");
        json.addProperty("failureCapability", failureCapability);
        return json;
    }

    private static String status(boolean available) {
        return available ? "runtime_verified" : "hook_failed";
    }

    private static String observed(long sequence) {
        return sequence > 0 ? "runtime_verified" : "unverified_until_exercised";
    }

    private record KeyState(int scanCode, int modifiers) {}

    private record RenderFact(
            long sequence,
            long clientTick,
            long screenRevision,
            String category,
            String stateClass,
            int x,
            int y,
            int width,
            int height) {}
}
