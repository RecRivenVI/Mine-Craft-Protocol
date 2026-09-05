package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.KeyboardHandlerInvoker;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.MouseHandlerInvoker;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.MouseHandlerAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;
import javax.imageio.ImageIO;

public final class NeoForgeProbeRuntime implements ProbeService {
    private static final NeoForgeProbeRuntime INSTANCE = new NeoForgeProbeRuntime();
    private static final ScheduledExecutorService WAITS = Executors.newSingleThreadScheduledExecutor(runnable -> {
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
    private final Phase9ASpikeEngine phase9a = new Phase9ASpikeEngine("26.2-neoforge");
    private final Deque<RenderFact> recentRenderFacts = new ArrayDeque<>();
    private final Map<Integer, KeyState> pressedKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Integer> pressedButtons = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Integer, Screen> buttonScreens = new HashMap<>();
    private final Map<Integer, String> buttonFrames = new HashMap<>();
    private volatile Minecraft minecraft;
    private volatile ProbeTransport transport;
    private volatile Screen lastScreen;
    private volatile long clientTick;
    private volatile long screenRevision;
    private volatile long menuRevision;
    private volatile int lastMenuHash;
    private volatile AbstractContainerMenu lastMenu;
    private volatile String lastTraceDetail = "";
    private volatile String lastServerThread = "";
    private volatile String lastRenderFact = "";
    private volatile AgentControlSession controlSession = new AgentControlSession();
    private volatile AgentControlSession.Snapshot controlPresence = this.controlSession.snapshot();
    private final java.util.concurrent.atomic.AtomicLongArray suppressedNative = new java.util.concurrent.atomic.AtomicLongArray(AgentInputContext.Kind.values().length);
    private final io.github.recrivenvi.minecraftprotocol.safety.AgentPointer logicalPointer = new io.github.recrivenvi.minecraftprotocol.safety.AgentPointer();
    private volatile String activeGestureId = "";
    private boolean lastTakeover;
    private long pointerScreenRevision;
    private String pointerViewport = "";
    private final java.util.IdentityHashMap<Object, Long> interactionIdentities = new java.util.IdentityHashMap<>();
    private long interactionIdentitySequence;
    private final AtomicLong nativeRevocations = new AtomicLong();
    private final AtomicLong rejectedHostGrabs = new AtomicLong();
    private final io.github.recrivenvi.minecraftprotocol.safety.ControlChrome operatorChrome = new io.github.recrivenvi.minecraftprotocol.safety.ControlChrome();
    private final io.github.recrivenvi.minecraftprotocol.safety.FrameCaptureQueue evidenceCaptures = new io.github.recrivenvi.minecraftprotocol.safety.FrameCaptureQueue();
    private volatile boolean renderingOperatorChrome;
    private boolean contentFrameReady;
    private final AtomicBoolean windowClosing = new AtomicBoolean();
    private java.util.List<WindowIcon> originalWindowIcons = java.util.List.of();
    private boolean agentPointerInitialized;
    private Boolean originalPauseOnLostFocus;
    private double agentPointerX, agentPointerY;
    private volatile boolean controlChromeApplied;
    private volatile String originalWindowTitle;
    private volatile String vanillaWindowTitle = "Minecraft";
    private String appliedWindowTitle;
    private final AtomicLong controlChromeRenderSequence = new AtomicLong();

    private NeoForgeProbeRuntime() {
        this.phase9a.installProviderDispatcher(this::dispatchProvider);
        this.phase9a.installProviderMutationDispatcher(this::dispatchProviderMutation);
    }

    public static void onClientTick(Minecraft minecraft) {
        INSTANCE.tick(minecraft);
    }


    public static boolean onNativeEscape(long window) {
        return INSTANCE.minecraft != null && window == INSTANCE.minecraft.getWindow().handle()
                && INSTANCE.handleNativeEscape();
    }
    public static boolean suppressNative(AgentInputContext.Kind kind) {
        if (!INSTANCE.controlPresence.agentControlled()) return false;
        INSTANCE.suppressedNative.incrementAndGet(kind.ordinal());
        return true;
    }
    public static boolean handleKeyIngress(long window, int key, int action, boolean agent) {
        var decision = io.github.recrivenvi.minecraftprotocol.safety.ExclusiveInputPolicy.decide(
                INSTANCE.controlPresence.agentControlled(), agent, AgentInputContext.Kind.KEY, key, action);
        if (decision == io.github.recrivenvi.minecraftprotocol.safety.ExclusiveInputPolicy.Decision.MANUAL_REVOKE)
            return onNativeEscape(window);
        return decision == io.github.recrivenvi.minecraftprotocol.safety.ExclusiveInputPolicy.Decision.SUPPRESS
                && suppressNative(AgentInputContext.Kind.KEY);
    }
    public static boolean isAgentKeyDown(int key) { return INSTANCE.pressedKeys.containsKey(key); }
    public static boolean allowHostMouseGrab() {
        if (!INSTANCE.controlPresence.agentControlled()) return true;
        INSTANCE.rejectedHostGrabs.incrementAndGet();
        return false;
    }
    public static boolean handleMouseRelease() {
        if (!INSTANCE.controlPresence.agentControlled()) return false;
        INSTANCE.releaseHostCursor();
        return true;
    }
    public static boolean routedWindowActive() {
        return AgentInputContext.isAgentRouted() && INSTANCE.controlPresence.agentControlled();
    }
    public static boolean routedMouseGrabbed() { return routedWindowActive() && INSTANCE.gameplayViewport(); }
    public static void onHostFocus(boolean focused) {
        if (!focused && INSTANCE.controlPresence.agentControlled()) {
            INSTANCE.releaseHostCursor();
        }
    }
    public static void onVanillaWindowTitle(String title) { INSTANCE.observeVanillaWindowTitle(title); }
    public static boolean isAgentControlActive() { return INSTANCE.controlPresence.agentControlled(); }
    public static void ensureExclusiveOwnerState() {
        if (INSTANCE.controlPresence.agentControlled() && !INSTANCE.lastTakeover)
            INSTANCE.controlPresenceChanged(INSTANCE.controlPresence);
    }
    public static void onVanillaWindowIcon(long window, GLFWImage.Buffer icons) {
        INSTANCE.cacheVanillaIcons(icons);
        if (INSTANCE.controlPresence.agentControlled()) INSTANCE.applyAgentIcon(window);
        else GLFW.glfwSetWindowIcon(window, icons);
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
    public static void beginContentFrame() { INSTANCE.contentFrameReady = false; }
    public static void contentRendered() { INSTANCE.contentFrameReady = true; }
    public static void beforePresent() { INSTANCE.presentOperatorChrome(); }

    @Override
    public void attachControlSession(AgentControlSession session) {
        this.controlSession = session;
        this.controlPresence = session.snapshot();
    }

    @Override
    public void controlPresenceChanged(AgentControlSession.Snapshot snapshot) {
        this.controlPresence = snapshot;
        Minecraft current = this.minecraft;
        if (current == null || this.windowClosing.get()) return;
        Runnable update = () -> {
            if (this.windowClosing.get() || snapshot.transitionSequence() != this.controlPresence.transitionSequence()) return;
            boolean takeover = snapshot.agentControlled();
            if (takeover != this.lastTakeover) {
                if (takeover) {
                    net.minecraft.client.KeyMapping.releaseAll();
                    MouseHandlerAccessor mouse = (MouseHandlerAccessor) current.mouseHandler;
                    mouse.minecraftProtocolProbe$setLeftPressed(false);
                    mouse.minecraftProtocolProbe$setRightPressed(false);
                    mouse.minecraftProtocolProbe$setMiddlePressed(false);
                    mouse.minecraftProtocolProbe$setActiveButton(null);
                    mouse.minecraftProtocolProbe$setMousePressedTime(0);
                }
                if (takeover) this.logicalPointer.reset(); else this.logicalPointer.plane(io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.Plane.HIDDEN, 0, 0);
                this.agentPointerInitialized = false;
                this.pointerScreenRevision = -1;
                this.releaseHostCursor();
                this.lastTakeover = takeover;
            }
            this.syncPointerContext(current);
            this.applyControlPresentation(current);
        };
        if (current.isSameThread()) update.run(); else current.execute(update);
    }

    private boolean hostFocused() {
        Minecraft client = this.minecraft;
        return client != null && !this.windowClosing.get() && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    private boolean gameplayViewport() {
        Minecraft client = this.minecraft;
        return client != null && client.level != null && client.player != null
                && client.gui.screen() == null && client.gui.overlay() == null;
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
        if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        if (client.mouseHandler instanceof MouseHandlerAccessor accessor) {
            accessor.minecraftProtocolProbe$setMouseGrabbed(false);
            accessor.minecraftProtocolProbe$setAccumulatedDX(0);
            accessor.minecraftProtocolProbe$setAccumulatedDY(0);
        }
    }

    private void enforceAgentMousePolicy(Minecraft client) {
        // Grab/warp is blocked at entry; no per-tick grab/release fight.
        this.syncPointerContext(client);
    }

    private void syncPointerContext(Minecraft client) {
        if (!this.controlPresence.agentControlled()) return;
        this.refreshScreen(client);
        boolean gui = client.gui.screen() != null;
        var plane = gui ? io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.Plane.GUI_ABSOLUTE
                : io.github.recrivenvi.minecraftprotocol.safety.AgentPointer.Plane.GAMEPLAY_RELATIVE;
        String viewport = client.getWindow().getScreenWidth() + ":" + client.getWindow().getScreenHeight() + ":" + client.getWindow().getGuiScaledWidth() + ":" + client.getWindow().getGuiScaledHeight() + ":" + client.getWindow().getGuiScale();
        if (this.logicalPointer.plane() != plane || this.pointerScreenRevision != this.screenRevision || !this.pointerViewport.equals(viewport)) {
            this.pointerViewport = viewport;
            this.logicalPointer.plane(plane, client.getWindow().getGuiScaledWidth() / 2.0, client.getWindow().getGuiScaledHeight() / 2.0);
            if (gui) this.logicalPointer.move(client.getWindow().getGuiScaledWidth() / 2.0, client.getWindow().getGuiScaledHeight() / 2.0);
            this.pointerScreenRevision = this.screenRevision;
            this.agentPointerX = client.getWindow().getScreenWidth() / 2.0;
            this.agentPointerY = client.getWindow().getScreenHeight() / 2.0;
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
        return !this.windowClosing.get() && (this.controlPresence.agentControlled() || this.controlSession.hasOperateWork() || current != null && current.agentPresent());
    }

    private void applyControlPresentation(Minecraft client) {
        if (this.windowClosing.get()) return;
        long window = client.getWindow().handle();
        if (this.controlPresence.agentControlled()) {
            if (!this.controlChromeApplied) {
                this.originalWindowTitle = this.vanillaWindowTitle;
                this.originalPauseOnLostFocus = client.options.pauseOnLostFocus;
                client.options.pauseOnLostFocus = false;
                this.applyAgentIcon(window);
                this.controlChromeApplied = true;
            }
        } else if (this.controlChromeApplied) {
            restoreControlPresentation(client);
        }
        String suffix = !agentPresent() ? "" : switch (this.controlPresence.mode()) {
            case READ -> " - 智能体读取中";
            case OPERATE -> " - 智能体操控中";
            case TAKEOVER -> " - 由智能体接管";
        };
        String desired = this.vanillaWindowTitle + suffix;
        if (!desired.equals(this.appliedWindowTitle)) {
            GLFW.glfwSetWindowTitle(window, desired);
            this.appliedWindowTitle = desired;
        }
    }

    private void restoreControlPresentation(Minecraft client) {
        long window = client.getWindow().handle();
        GLFW.glfwSetWindowTitle(window, this.vanillaWindowTitle);
        this.appliedWindowTitle = this.vanillaWindowTitle;
        if (this.controlChromeApplied && !this.originalWindowIcons.isEmpty()) this.setIcons(window, this.originalWindowIcons);
        this.controlChromeApplied = false;
        if (this.originalPauseOnLostFocus != null) client.options.pauseOnLostFocus = this.originalPauseOnLostFocus;
        this.originalPauseOnLostFocus = null;
        this.originalWindowTitle = null;
    }

    private void observeVanillaWindowTitle(String title) {
        if (title != null && !title.isBlank()) this.vanillaWindowTitle = title.replace(" - 由智能体控制", "").replace(" - 由智能体接管", "").replace(" - 智能体读取中", "").replace(" - 智能体操控中", "");
    }

    private record WindowIcon(int width, int height, byte[] rgba) { }

    private void cacheVanillaIcons(GLFWImage.Buffer icons) {
        if (icons == null || icons.remaining() == 0 || icons.remaining() > 16) return;
        java.util.List<WindowIcon> captured = new java.util.ArrayList<>();
        for (int index = icons.position(); index < icons.limit(); index++) {
            GLFWImage icon = icons.get(index);
            int width = icon.width(), height = icon.height();
            if (width < 1 || height < 1 || width > 512 || height > 512) return;
            byte[] rgba = new byte[width * height * 4];
            icon.pixels(rgba.length).get(rgba);
            captured.add(new WindowIcon(width, height, rgba));
        }
        this.originalWindowIcons = java.util.List.copyOf(captured);
    }

    private void setIcons(long window, java.util.List<WindowIcon> images) {
        GLFWImage.Buffer icons = GLFWImage.malloc(images.size());
        java.util.List<ByteBuffer> allocated = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < images.size(); i++) {
                WindowIcon image = images.get(i);
                ByteBuffer pixels = MemoryUtil.memAlloc(image.rgba().length);
                allocated.add(pixels);
                pixels.put(image.rgba()).flip();
                icons.get(i).width(image.width()).height(image.height()).pixels(pixels);
            }
            GLFW.glfwSetWindowIcon(window, icons);
        } finally {
            allocated.forEach(MemoryUtil::memFree);
            icons.free();
        }
    }

    private void applyAgentIcon(long window) {
        try (InputStream stream = NeoForgeProbeRuntime.class.getResourceAsStream("/minecraft_protocol_probe_control.png")) {
            if (stream == null) return;
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() > 512 || image.getHeight() > 512) return;
            int width = image.getWidth(), height = image.getHeight();
            byte[] rgba = new byte[width * height * 4];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int color = image.getRGB(x, y), offset = (y * width + x) * 4;
                rgba[offset] = (byte) (color >> 16); rgba[offset + 1] = (byte) (color >> 8);
                rgba[offset + 2] = (byte) color; rgba[offset + 3] = (byte) (color >>> 24);
            }
            setIcons(window, java.util.List.of(new WindowIcon(width, height, rgba)));
        } catch (IOException ignored) { }
    }

    private void presentOperatorChrome() {
        Minecraft client = this.minecraft;
        if (client == null || this.windowClosing.get() || !this.contentFrameReady) return;
        this.contentFrameReady = false;
        this.evidenceCaptures.beforeOperatorChrome(this::captureContentAtBoundary);
        float alpha = this.operatorChrome.update(this.controlPresence.mode(), this.agentPresent(),
                client.gui.screen() != null, System.nanoTime());
        if (alpha < 0.02F) return;
        this.renderingOperatorChrome = true;
        try {
            var state = client.gameRenderer.gameRenderState().guiRenderState;
            state.reset();
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, 0, 0);
            renderChrome(graphics, alpha);
            var accessor = (io.github.recrivenvi.minecraftprotocol.probe.mixin.GameRendererAccessor) client.gameRenderer;
            accessor.minecraftProtocolProbe$guiRenderer().render();
            this.controlChromeRenderSequence.incrementAndGet();
        } finally { this.renderingOperatorChrome = false; }
    }

    private void renderChrome(GuiGraphicsExtractor graphics, float alpha) {
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.edges(graphics::fill, width, height, this.operatorChrome.edgeAlpha());
        String message = this.operatorChrome.message();
        String previous = this.operatorChrome.previousMessage();
        float mix = this.operatorChrome.textMix();
        int textWidth = Math.round(this.minecraft.font.width(previous) * (1F - mix) + this.minecraft.font.width(message) * mix);
        int barWidth = textWidth + 20, barHeight = 23;
        int left = Math.max(4, (width - barWidth) / 2), top = 8;
        io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.panel(graphics::fill, left, top, barWidth, barHeight, alpha);
        if (mix < 1F) graphics.text(this.minecraft.font, Component.literal(previous), left + 10, top + 8,
                io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.color(255, 0xFFFFFF, alpha * (1F - mix)));
        graphics.text(this.minecraft.font, Component.literal(message), left + 10, top + 8,
                io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.color(255, 0xFFFFFF, alpha * mix));
        if (this.operatorChrome.pointerAlpha() > 0.01F && this.logicalPointer.initialized()) {
            io.github.recrivenvi.minecraftprotocol.safety.ControlChrome.pointer(graphics::fill,
                    (int) Math.round(this.logicalPointer.x()), (int) Math.round(this.logicalPointer.y()), this.operatorChrome.pointerAlpha());
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
        json.addProperty("operatorChromeLayer", "after_content_capture_before_present");
        json.addProperty("captureExcludesOperatorChrome", true);
        json.addProperty("captureFrameSequence", this.evidenceCaptures.frameSequence());
        json.addProperty("capturePending", this.evidenceCaptures.pendingCount());
        json.addProperty("captureReadbacks", this.evidenceCaptures.readbacks());
        json.addProperty("nativeRevocations", this.nativeRevocations.get());
        json.addProperty("nativeCaptureGrants", 0);
        json.addProperty("nativeInputPolicy", "exclusive_except_physical_escape");
        JsonObject suppressed = new JsonObject();
        for (AgentInputContext.Kind kind : AgentInputContext.Kind.values()) suppressed.addProperty(kind.name().toLowerCase(java.util.Locale.ROOT), this.suppressedNative.get(kind.ordinal()));
        json.add("suppressedNativeInput", suppressed);
        json.addProperty("presenceActive", this.agentPresent());
        json.addProperty("presenceBasis", "authenticated_activity_or_observation_work_or_takeover");
        json.addProperty("presenceInactivityTimeoutMs", 15000);
        json.addProperty("hostCursorPolicy", "never_capture_or_warp_during_takeover");
        json.addProperty("activeGestureId", this.activeGestureId);
        json.addProperty("pointerOwnership", !this.activeGestureId.isEmpty() ? "serialized_gesture" : !this.pressedButtons.isEmpty() ? "lease_raw_stream" : "none");
        JsonObject pointer = new JsonObject();
        pointer.addProperty("plane", this.logicalPointer.plane().name());
        pointer.addProperty("x", this.logicalPointer.x()); pointer.addProperty("y", this.logicalPointer.y());
        pointer.addProperty("alpha", this.operatorChrome.pointerAlpha());
        pointer.addProperty("evidencePixels", false);
        json.add("agentPointer", pointer);
        json.addProperty("rejectedHostGrabs", this.rejectedHostGrabs.get());
        json.addProperty("hostFocused", this.hostFocused());
        json.addProperty("hostCursorCaptureGranted", false);
        Minecraft client = this.minecraft;
        if (client != null && !this.windowClosing.get()) json.addProperty("hostCursorCaptured",
                GLFW.glfwGetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED);
        json.addProperty("windowIconState", this.controlChromeApplied ? "agent_placeholder" : "original_minecraft");
        json.addProperty("originalWindowIconCount", this.originalWindowIcons.size());
        if (snapshot.manuallyRevoked()) {
            json.addProperty("controlMessage", snapshot.message());
            json.addProperty("manualRevocationReason", "human_manual_revocation");
        }
    }

    public static void observeScreenSlotClick(int slotId, int mouseButton, String input) {
        INSTANCE.screenSlotClickSequence.incrementAndGet();
        INSTANCE.lastTraceDetail = "screen slot=" + slotId + " button=" + mouseButton + " input=" + input;
    }

    public static void observeMenuDispatch(int containerId, int slotId, String input) {
        INSTANCE.menuDispatchSequence.incrementAndGet();
        INSTANCE.lastTraceDetail = "menu container=" + containerId + " slot=" + slotId + " input=" + input;
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
            default -> {
            }
        }
        INSTANCE.recordRenderFact(category, stateClass, x, y, width, height);
    }

    private void tick(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.enforceAgentMousePolicy(minecraft);
        this.applyControlPresentation(minecraft);
        DedicatedPeerClient.tick(minecraft);
        this.clientTick++;
        if (Boolean.getBoolean("minecraft.protocol.probe.disablePauseOnLostFocus")) {
            minecraft.options.pauseOnLostFocus = false;
        }
        if (this.initialized.compareAndSet(false, true)) {
            String token = RuntimeToken.resolve(minecraft.gameDirectory.toPath());
            int port = Integer.getInteger("minecraft.protocol.probe.port", 25582);
            this.transport = new ProbeTransport(this, token, port);
            this.transport.startAsync();
            if (this.shutdownRegistered.compareAndSet(false, true)) {
                Runtime.getRuntime().addShutdownHook(new Thread(
                        this::shutdown, "minecraft-protocol-shutdown"));
            }
        }

        this.refreshScreen(minecraft);
        this.refreshMenu(minecraft);
        MinecraftServer singleplayerServer = minecraft.getSingleplayerServer();
        this.phase9a.observeStorageLifecycle(
                minecraft.level,
                singleplayerServer != null && singleplayerServer.isCurrentlySaving(),
                minecraft.level == null && singleplayerServer == null && minecraft.getConnection() == null);
        if (minecraft.level != null && minecraft.getSingleplayerServer() != null && this.clientTick % 20L == 0L) {
            JsonObject storageQuery = new JsonObject();
            storageQuery.addProperty("domain", "world");
            this.onIntegratedServer((server, player) -> {
                this.phase9a.rememberStorageContext(this.phase9a.storageRequest(server, player, storageQuery));
                return null;
            });
        }
    }

    private void shutdown() {
        if (!this.shutdownStarted.compareAndSet(false, true)) return;
        this.evidenceCaptures.close();
        this.phase9a.observeStorageShutdown();
        ProbeTransport current = this.transport;
        if (current != null) current.close();
        this.phase9a.close();
    }

    @Override
    public CompletableFuture<JsonObject> session() {
        return this.onClient(() -> {
            Minecraft client = requireClient();
            Screen screen = this.refreshScreen(client);
            this.refreshMenu(client);
            JsonObject json = base("session");
            json.addProperty("target", "26.2-neoforge");
            json.addProperty("minecraft", "26.2");
            json.addProperty("loader", "neoforge");
            json.addProperty("inWorld", client.level != null && client.player != null);
            json.addProperty("screenClass", screen == null ? "" : screen.getClass().getName());
            json.addProperty("screenRevision", this.screenRevision);
            json.addProperty("menuRevision", this.menuRevision);
            json.addProperty("thread", Thread.currentThread().getName());
            this.addControlPresence(json);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> capabilities() {
        return this.onClient(() -> {
            JsonObject json = base("capabilities");
            JsonObject capabilities = new JsonObject();
            capabilities.addProperty("ui.interaction_tree", "runtime_verified");
            capabilities.addProperty("ui.selector", "runtime_verified");
            capabilities.addProperty("ui.coordinate_generation", "runtime_verified");
            capabilities.addProperty("ui.vision_fallback", "runtime_verified");
            capabilities.addProperty("ui.render_facts", "runtime_verified");
            capabilities.addProperty("input.game_routed_raw", "runtime_hooked");
            capabilities.addProperty("input.game_routed_screen", "path_observable");
            capabilities.addProperty("input.game_routed_keymapping", "path_observable");
            capabilities.addProperty("capture.composite", "gpu_abstraction_callback");
            capabilities.addProperty("capture.opengl", "target_verified");
            capabilities.addProperty("capture.vulkan", "target_verified");
            capabilities.addProperty("input.pipeline", "runtime_verified");
            capabilities.addProperty("control.agent_presence", this.evidenceCaptures.frameSequence() > 0 ? "runtime_verified" : "unverified_until_present");
            capabilities.addProperty("control.native_escape_revoke", this.nativeRevocations.get() > 0 ? "runtime_verified" : "unverified_until_native_escape");
            capabilities.addProperty("input.host_cursor_capture", "agent_gated_native_click");
            capabilities.addProperty("input.multi_key", "runtime_verified");
            capabilities.addProperty("input.drag_scroll", "runtime_verified");
            capabilities.addProperty("command.player.execute", "normal_network_current_permissions");
            capabilities.addProperty("wait.assert", "runtime_verified");
            capabilities.addProperty("fixture.standard_mod_gui", "runtime_verified_contaminated");
            capabilities.addProperty("ui.standard_mod_gui_extended", "runtime_verified_fixture");
            capabilities.addProperty("diagnostics.hook_manifest", "runtime_self_test");
            capabilities.addProperty("input.virtual_pointer", "gui_absolute_gameplay_relative");
            capabilities.addProperty("input.exclusive_takeover", "standard_native_callbacks_and_polling");
            capabilities.addProperty("input.gesture_queue", "serialized_bounded_16");
            capabilities.addProperty("input.origin", "single_use_event_ticket_and_scheduled_native_origin");
            capabilities.addProperty("operator.chrome", "pixel_top_center_three_intentions");
            capabilities.addProperty("world.client_live", "runtime_verified_when_in_world");
            capabilities.addProperty("server.integrated_authoritative", "runtime_verified_when_integrated");
            capabilities.addProperty("state.frame", "runtime_verified");
            capabilities.addProperty("provider.read_spi", "available_live_only");
            capabilities.addProperty("storage.persistent", "unavailable");
            capabilities.addProperty("storage.persistent.read", "runtime_verified_bounded_requires_storage.read");
            capabilities.addProperty("storage.persistent.write", "unavailable");
            capabilities.addProperty("server.peer.transport", "optional_peer_v0");
            capabilities.addProperty("server.peer.read", "runtime_negotiated");
            capabilities.addProperty("server.peer.fixture", "server_flag_and_operator_gated");
            capabilities.addProperty("server.peer.debug", "server_flag_operator_scope_and_arm_gated");
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
        return this.onClient(() -> {
            Minecraft client = requireClient();
            JsonObject json = base("readiness");
            JsonObject hooks = new JsonObject();
            boolean clientTickHook = this.clientTick > 0;
            boolean mouseInvoker = client.mouseHandler instanceof MouseHandlerInvoker;
            boolean keyboardInvoker = client.keyboardHandler instanceof KeyboardHandlerInvoker;
            boolean screenObservation = this.screenRevision > 0;
            hooks.addProperty("clientTickHook", status(clientTickHook));
            hooks.addProperty("mouseHandlerInvoker", status(mouseInvoker));
            hooks.addProperty("keyboardHandlerInvoker", status(keyboardInvoker));
            hooks.addProperty("screenObservation", status(screenObservation));
            hooks.addProperty("containerScreenHook", observed(this.screenSlotClickSequence.get()));
            hooks.addProperty("containerPacketHook", observed(this.containerPacketSequence.get()));
            hooks.addProperty("serverValidationHook", observed(this.serverContainerPacketSequence.get()));
            hooks.addProperty("compositeCapture", this.captureVerified.get() ? "runtime_verified" : "unverified_until_capture");
            hooks.addProperty("renderFacts", this.renderElementSequence.get() + this.renderTextSequence.get() > 0
                    ? "runtime_verified" : "unverified_until_render");
            json.add("hooks", hooks);
            json.addProperty("overall", clientTickHook && mouseInvoker && keyboardInvoker && screenObservation ? "ready" : "degraded");
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> hookManifest() {
        return this.onClient(() -> {
            Minecraft client = requireClient();
            boolean tick = this.clientTick > 0;
            boolean mouse = client.mouseHandler instanceof MouseHandlerInvoker;
            boolean keyboard = client.keyboardHandler instanceof KeyboardHandlerInvoker;
            boolean screen = this.screenRevision > 0;
            JsonArray hooks = new JsonArray();
            hooks.add(hook("runtime_pre_loader_shutdown", "MIXIN_INJECT", "Minecraft.close", "HEAD",
                    "bounded_runtime_drain", "unverified_until_shutdown", "runtime.lifecycle"));
            hooks.add(hook("client_tick", "MIXIN_INJECT", "Minecraft.tick", "TAIL",
                    "read_observation", status(tick), "system.session"));
            hooks.add(hook("mouse_input", "MIXIN_INVOKER", "MouseHandler", "PRIVATE_INPUT_CALLBACK",
                    "game_routed_input", status(mouse), "input.mouse"));
            hooks.add(hook("keyboard_input", "MIXIN_INVOKER", "KeyboardHandler", "PRIVATE_INPUT_CALLBACK",
                    "game_routed_input", status(keyboard), "input.key"));
            hooks.add(hook("container_screen", "MIXIN_INJECT", "AbstractContainerScreen.slotClicked", "HEAD",
                    "read_observation", observed(this.screenSlotClickSequence.get()), "ui.container_trace"));
            hooks.add(hook("container_client_packet", "MIXIN_INJECT", "Connection.send", "HEAD",
                    "read_observation", observed(this.containerPacketSequence.get()), "ui.container_trace"));
            hooks.add(hook("container_server_validation", "MIXIN_INJECT", "ServerGamePacketListenerImpl.handleContainerClick", "HEAD",
                    "read_observation", observed(this.serverContainerPacketSequence.get()), "ui.container_trace"));
            hooks.add(hook("render_facts", "MIXIN_INJECT", "GuiRenderState.add*", "HEAD",
                    "read_observation", this.renderElementSequence.get() + this.renderTextSequence.get() > 0
                            ? "runtime_verified" : "unverified_until_render", "ui.render_facts"));
            hooks.add(hook("composite_capture", "RUNTIME_CALLBACK", "GpuTexture", "READBACK_CALLBACK",
                    "capture", this.captureVerified.get() ? "runtime_verified" : "unverified_until_capture", "capture.composite"));
            hooks.add(operatorHook("native_escape_revoke", "KeyboardHandler.keyPress", true, false, observed(this.nativeRevocations.get())));
            hooks.add(operatorHook("exclusive_native_button", "MouseHandler.onButton", true, false, "configured_pending_human_acceptance"));
            hooks.add(operatorHook("host_grab_guard", "MouseHandler.grabMouse", true, false, status(mouse)));
            hooks.add(operatorHook("host_release_no_warp", "MouseHandler.releaseMouse", true, false, status(mouse)));
            hooks.add(operatorHook("virtual_mouse_grab", "MouseHandler.isMouseGrabbed", true, false, status(mouse)));
            hooks.add(operatorHook("virtual_keymapping_consumption", "Minecraft.handleKeybinds", false, true, status(tick)));
            hooks.add(operatorHook("virtual_input_focus", "Minecraft.isWindowActive", true, false, status(tick)));
            hooks.add(operatorHook("control_window_title", "Window.setTitle", true, false, status(tick)));
            hooks.add(operatorHook("restore_actual_window_icons", "Window.setIcon", false, true, this.originalWindowIcons.isEmpty() ? "unverified_until_icon" : "runtime_verified"));
            hooks.add(operatorHook("operator_chrome_final_present", "Minecraft.renderFrame", false, false, observed(this.evidenceCaptures.frameSequence())));
            hooks.add(operatorHook("native_keyboard_ingress", "KeyboardHandler.setup", false, true, "configured_pending_unified_acceptance"));
            hooks.add(operatorHook("native_mouse_ingress", "MouseHandler.setup", false, true, "configured_pending_unified_acceptance"));
            hooks.add(operatorHook("exclusive_character", "KeyboardHandler.charTyped", true, false, "configured_pending_unified_acceptance"));
            hooks.add(operatorHook("exclusive_preedit", "KeyboardHandler.preeditCallback/resubmitLastPreeditEvent", true, false, "configured_pending_unified_acceptance"));
            hooks.add(operatorHook("exclusive_move_scroll_drop", "MouseHandler.onMove/onScroll/onDrop", true, false, "configured_pending_unified_acceptance"));
            hooks.add(operatorHook("virtual_key_polling", "InputConstants.isKeyDown", true, false, "configured_pending_unified_acceptance"));
            hooks.add(operatorHook("host_cursor_warp_guard", "InputConstants.grabOrReleaseMouse", true, false, "configured_pending_unified_acceptance"));
            JsonObject json = base("diagnostics.hook_manifest");
            json.addProperty("policy", "capability_fidelity_first");
            json.addProperty("overwriteCount", 0);
            json.addProperty("cancellableInjectionCount", 15);
            json.addProperty("replacementInjectionCount", 4);
            json.addProperty("controlFlowPolicy", "operator_control_hooks_only");
            json.addProperty("thirdPartyTargetCount", 0);
            json.addProperty("runtimeSelfTest", true);
            json.addProperty("overall", tick && mouse && keyboard && screen ? "ready" : "degraded");
            json.add("hooks", hooks);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> trace() {
        return this.onClient(() -> {
            JsonObject json = base("trace");
            json.addProperty("screenSlotClickSequence", this.screenSlotClickSequence.get());
            json.addProperty("menuDispatchSequence", this.menuDispatchSequence.get());
            json.addProperty("containerPacketSequence", this.containerPacketSequence.get());
            json.addProperty("serverContainerPacketSequence", this.serverContainerPacketSequence.get());
            json.addProperty("lastDetail", this.lastTraceDetail);
            json.addProperty("lastServerThread", this.lastServerThread);
            json.addProperty("screenRevision", this.screenRevision);
            json.addProperty("menuRevision", this.menuRevision);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> renderFacts() {
        return this.onClient(() -> {
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
        return this.onClient(this::snapshotUiTree);
    }

    private JsonObject snapshotUiTree() {
            Minecraft client = requireClient();
            Screen screen = this.refreshScreen(client);
            this.refreshMenu(client);
            JsonObject root = base("ui.tree");
            root.addProperty("screenRevision", this.screenRevision);
            root.addProperty("menuRevision", this.menuRevision);
            root.addProperty("screenIdentity", screen == null ? 0 : this.interactionIdentity(screen));
            root.addProperty("overlayIdentity", client.gui.overlay() == null ? 0 : this.interactionIdentity(client.gui.overlay()));
            root.addProperty("width", client.getWindow().getGuiScaledWidth());
            root.addProperty("height", client.getWindow().getGuiScaledHeight());
            root.addProperty("windowWidth", client.getWindow().getScreenWidth());
            root.addProperty("windowHeight", client.getWindow().getScreenHeight());
            root.addProperty("guiScale", client.getWindow().getGuiScale());
            JsonArray children = new JsonArray();
            if (screen == null) {
                root.addProperty("screenClass", "");
                root.add("children", children);
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
            int index = 0;
            for (GuiEventListener child : screen.children()) {
                JsonObject node = new JsonObject();
                node.addProperty("nodeId", "widget:" + this.screenRevision + ":" + index++);
                node.addProperty("class", child.getClass().getName());
                node.addProperty("elementIdentity", this.interactionIdentity(child));
                String role = semanticRole(child);
                node.addProperty("role", role);
                node.addProperty("nodeRevision", this.screenRevision);
                node.addProperty("coverage", "semantic_native");
                if (child instanceof AbstractWidget widget) {
                    node.addProperty("label", widget.getMessage().getString());
                    node.addProperty("x", widget.getX());
                    node.addProperty("y", widget.getY());
                    node.addProperty("width", widget.getWidth());
                    node.addProperty("height", widget.getHeight());
                    node.addProperty("active", widget.active);
                    node.addProperty("visible", widget.visible);
                    node.addProperty("hovered", widget.isHovered());
                    addInteractionMetadata(node, role, widget.active && widget.visible);
                }
                children.add(node);
            }

            if (screen instanceof AbstractContainerScreen<?> container) {
                int left = container.getLeftPos();
                int top = container.getTopPos();
                AbstractContainerMenu menu = container.getMenu();
                for (Slot slot : menu.slots) {
                    JsonObject node = new JsonObject();
                    node.addProperty("nodeId", "slot:" + this.screenRevision + ":" + slot.index);
                    node.addProperty("class", slot.getClass().getName());
                    node.addProperty("elementIdentity", this.interactionIdentity(slot));
                    node.addProperty("role", "slot");
                    node.addProperty("nodeRevision", this.menuRevision);
                    node.addProperty("coverage", "semantic_native");
                    node.addProperty("slot", slot.index);
                    node.addProperty("x", left + slot.x);
                    node.addProperty("y", top + slot.y);
                    node.addProperty("width", 18);
                    node.addProperty("height", 18);
                    node.addProperty("active", slot.isActive());
                    node.addProperty("visible", true);
                    ItemStack stack = slot.getItem();
                    node.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    node.addProperty("count", stack.getCount());
                    addInteractionMetadata(node, "slot", slot.isActive());
                    children.add(node);
                }
                root.addProperty("menuId", menu.containerId);
            }
            root.add("children", children);
            return root;

    }

    @Override
    public CompletableFuture<JsonObject> mouseMove(double guiX, double guiY) {
        return this.mouseMoveGuarded(guiX, guiY, null);
    }

    @Override
    public CompletableFuture<JsonObject> mouseMoveGuarded(double guiX, double guiY, JsonObject guard) {
        return this.onControlledClient(() -> this.movePointerNow(requireClient(), guiX, guiY, guard));
    }

    private JsonObject movePointerNow(Minecraft client, double guiX, double guiY, JsonObject guard) {
        this.checkPointerGuard(guard);
        this.syncPointerContext(client);
        if (!Double.isFinite(guiX) || !Double.isFinite(guiY)) throw new ProtocolState.ProtocolException(
                "INVALID_POINTER_COORDINATE", 400, "Pointer coordinates must be finite");
        if (client.gui.screen() != null && (guiX < 0 || guiY < 0 || guiX >= client.getWindow().getGuiScaledWidth() || guiY >= client.getWindow().getGuiScaledHeight()))
            throw new ProtocolState.ProtocolException("POINTER_OUT_OF_BOUNDS", 409, "Pointer target is outside the GUI");
        double rawX = guiX * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
        double rawY = guiY * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) client.mouseHandler;
        if (this.agentPointerInitialized) {
            accessor.minecraftProtocolProbe$setXpos(this.agentPointerX);
            accessor.minecraftProtocolProbe$setYpos(this.agentPointerY);
        }
        accessor.minecraftProtocolProbe$setIgnoreFirstMove(false);
        AgentInputContext.dispatch(AgentInputContext.Event.point(AgentInputContext.Kind.MOVE, client.getWindow().handle(), rawX, rawY), () -> {
            ((MouseHandlerInvoker) client.mouseHandler).minecraftProtocolProbe$onMove(client.getWindow().handle(), rawX, rawY);
            client.mouseHandler.handleAccumulatedMovement();
        });
        this.agentPointerX = rawX; this.agentPointerY = rawY; this.agentPointerInitialized = true;
        this.logicalPointer.move(guiX, guiY);
        JsonObject evidence = inputEvidence("GAME_ROUTED_RAW", client.gui.screen() != null, false);
        evidence.addProperty("pointerPlane", this.logicalPointer.plane().name());
        evidence.addProperty("guiX", guiX); evidence.addProperty("guiY", guiY);
        this.gestureEvent(this.activeGestureId, "pointer_move", evidence);
        return evidence;
    }

    @Override
    public CompletableFuture<JsonObject> mouseDelta(double dx, double dy) {
        return this.onControlledClient(() -> {
            Minecraft client = requireClient();
            if (!this.gameplayViewport()) throw new ProtocolState.ProtocolException(
                    "GAMEPLAY_POINTER_REQUIRED", 409, "Relative input requires gameplay without Screen/Overlay");
            if (!Double.isFinite(dx) || !Double.isFinite(dy) || Math.abs(dx) > 8192 || Math.abs(dy) > 8192)
                throw new ProtocolState.ProtocolException("INVALID_POINTER_DELTA", 400, "Relative delta exceeds its finite pixel budget");
            this.syncPointerContext(client);
            double x = (this.agentPointerX + dx) * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth();
            double y = (this.agentPointerY + dy) * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight();
            JsonObject result = this.movePointerNow(client, x, y, null);
            result.addProperty("type", "input.mouse.delta");
            result.addProperty("dx", dx); result.addProperty("dy", dy);
            result.addProperty("cameraProcessing", "vanilla_sensitivity_inversion");
            return result;
        });
    }

    private long interactionIdentity(Object object) {
        if (this.interactionIdentities.size() >= 2048) this.interactionIdentities.clear();
        return this.interactionIdentities.computeIfAbsent(object, ignored -> ++this.interactionIdentitySequence);
    }
    private void checkPointerGuard(JsonObject guard) {
        if (guard != null) AutomationEngine.validatePointerGuard(guard, this.snapshotUiTree());
    }
    @Override
    public CompletableFuture<JsonObject> pointerState() {
        return this.onClient(() -> {
            Minecraft client = requireClient();
            this.syncPointerContext(client);
            JsonObject result = this.snapshotUiTree();
            result.addProperty("x", this.logicalPointer.initialized() ? this.logicalPointer.x() : client.getWindow().getGuiScaledWidth() / 2.0);
            result.addProperty("y", this.logicalPointer.initialized() ? this.logicalPointer.y() : client.getWindow().getGuiScaledHeight() / 2.0);
            result.addProperty("guiAbsolute", client.gui.screen() != null);
            result.addProperty("pressedButtonCount", this.pressedButtons.size());
            return result;
        });
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
        return this.screenRevision + ":" + (client.gui.overlay() == null ? 0 : this.interactionIdentity(client.gui.overlay()))
                + ":" + client.getWindow().getScreenWidth() + ":" + client.getWindow().getScreenHeight()
                + ":" + client.getWindow().getGuiScaledWidth() + ":" + client.getWindow().getGuiScaledHeight()
                + ":" + client.getWindow().getGuiScale();
    }
    private void clearMouseButtonState(Minecraft client) {
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) client.mouseHandler;
        mouse.minecraftProtocolProbe$setLeftPressed(false);
        mouse.minecraftProtocolProbe$setRightPressed(false);
        mouse.minecraftProtocolProbe$setMiddlePressed(false);
        mouse.minecraftProtocolProbe$setActiveButton(null);
        mouse.minecraftProtocolProbe$setMousePressedTime(0);
        for (int button : this.pressedButtons)
            net.minecraft.client.KeyMapping.set(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button), false);
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
    public CompletableFuture<JsonObject> mouseButtonGuarded(int button, int action, int modifiers, JsonObject guard) {
        return this.onControlledClient(() -> {
            this.checkPointerGuard(guard);
            Minecraft client = requireClient();
            this.restoreAgentPointer(client);
            Screen screen = client.gui.screen();
            long beforeMenu = this.menuDispatchSequence.get();
            long beforePacket = this.containerPacketSequence.get();
            if (action == 1) this.buttonFrames.put(button, this.pointerFrameStamp(client));
            if (action == 1) this.buttonScreens.put(button, client.gui.screen());
            if (action == 0 && this.buttonScreens.containsKey(button) && (!java.util.Objects.equals(this.buttonFrames.get(button), this.pointerFrameStamp(client)) || this.buttonScreens.get(button) != client.gui.screen())) {
                this.clearMouseButtonState(client);
            } else {
            AgentInputContext.dispatch(AgentInputContext.Event.button(client.getWindow().handle(), button, action, modifiers), () -> ((MouseHandlerInvoker) client.mouseHandler).minecraftProtocolProbe$onButton(
                    client.getWindow().handle(), new MouseButtonInfo(button, modifiers), action));
            }
            if (action == 0) { this.buttonScreens.remove(button); this.buttonFrames.remove(button); }
            if (action == 1) this.pressedButtons.add(button);
            else if (action == 0) this.pressedButtons.remove(button);
            this.refreshScreen(client);
            this.refreshMenu(client);
            JsonObject evidence = inputEvidence("GAME_ROUTED_RAW", screen != null, screen instanceof AbstractContainerScreen<?>);
            evidence.addProperty("normalMenuProcessingObserved", this.menuDispatchSequence.get() > beforeMenu);
            evidence.addProperty("normalPacketObserved", this.containerPacketSequence.get() > beforePacket);
            return evidence;
        });
    }

    @Override
    public CompletableFuture<JsonObject> mouseScroll(double xOffset, double yOffset) {
        return this.mouseScrollGuarded(xOffset, yOffset, null);
    }
    @Override
    public CompletableFuture<JsonObject> mouseScrollGuarded(double xOffset, double yOffset, JsonObject guard) {
        return this.onControlledClient(() -> {
            this.checkPointerGuard(guard);
            Minecraft client = requireClient();
            this.restoreAgentPointer(client);
            AgentInputContext.dispatch(AgentInputContext.Event.point(AgentInputContext.Kind.SCROLL, client.getWindow().handle(), xOffset, yOffset), () -> ((MouseHandlerInvoker) client.mouseHandler).minecraftProtocolProbe$onScroll(client.getWindow().handle(), xOffset, yOffset));
            Screen screen = client.gui.screen();
            return inputEvidence("GAME_ROUTED_RAW", screen != null, screen instanceof AbstractContainerScreen<?>);
        });
    }

    @Override
    public CompletableFuture<JsonObject> key(int key, int scanCode, int action, int modifiers) {
        return this.onControlledClient(() -> {
            Minecraft client = requireClient();
            AgentInputContext.dispatch(AgentInputContext.Event.key(client.getWindow().handle(), key, scanCode, action, modifiers), () -> ((KeyboardHandlerInvoker) client.keyboardHandler).minecraftProtocolProbe$keyPress(
                    client.getWindow().handle(), action, new KeyEvent(key, scanCode, modifiers)));
            if (action == 1 || action == 2) this.pressedKeys.put(key, new KeyState(scanCode, modifiers));
            else if (action == 0) this.pressedKeys.remove(key);
            this.refreshScreen(client);
            this.refreshMenu(client);
            Screen screen = client.gui.screen();
            JsonObject evidence = inputEvidence("GAME_ROUTED_RAW", screen != null, screen instanceof AbstractContainerScreen<?>);
            evidence.addProperty("keyMappingPath", screen == null);
            return evidence;
        });
    }

    @Override
    public CompletableFuture<JsonObject> playerState() {
        return this.onClient(() -> {
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
            json.addProperty("dimension", player.level().dimension().identifier().toString());
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> playerCommand(String rawCommand) {
        return this.onControlledClient(() -> {
            Minecraft client = requireClient();
            if (client.player == null || client.getConnection() == null) {
                throw new ProtocolState.ProtocolException(
                        "PLAYER_UNAVAILABLE", 409, "A connected current player is required");
            }
            String command = rawCommand == null ? "" : rawCommand.trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank() || command.length() > 2048
                    || command.chars().anyMatch(character -> character == 10 || character == 13)) {
                throw new ProtocolState.ProtocolException(
                        "INVALID_PLAYER_COMMAND", 400, "Command must be one non-empty line of at most 2048 characters");
            }
            client.getConnection().sendCommand(command);
            JsonObject json = base("command.player.execute");
            json.addProperty("accepted", true);
            json.addProperty("perspective", "current_player");
            json.addProperty("mode", "PLAYTEST");
            json.addProperty("mechanism", "NORMAL_NETWORK");
            json.addProperty("normalPacket", true);
            json.addProperty("serverValidation", true);
            json.addProperty("permissionEscalated", false);
            json.addProperty("evidenceContaminated", false);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> blockState(int x, int y, int z) {
        return this.onClient(() -> {
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
            if (client.level.getChunkSource().getChunk(x >> 4, z >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) {
                json.addProperty("chunkLoadRequested", false);
                json.addProperty("available", false);
                json.addProperty("reason", "chunk_not_loaded");
                return json;
            }
            BlockState state = client.level.getBlockState(position);
            json.addProperty("available", true);
            json.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            json.addProperty("state", state.toString());
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> entities(double radius) {
        return this.onClient(() -> {
            Minecraft client = requireClient();
            JsonObject json = base("world.entities");
            addLiveMetadata(json, "client_known_live", "client_live", "client_observed");
            JsonArray entities = new JsonArray();
            if (client.level != null && client.player != null) {
                double boundedRadius = Mth.clamp(radius, 0.0, 128.0);
                for (Entity entity : client.level.getEntities(
                        client.player, client.player.getBoundingBox().inflate(boundedRadius), entity -> true)) {
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
        return this.onIntegratedServer((server, player) -> {
            JsonObject json = base("server.player.state");
            addLiveMetadata(json, "server_authoritative_live", "integrated_server_live", "server_authoritative");
            json.addProperty("available", true);
            json.addProperty("serverTick", server.getTickCount());
            json.addProperty("thread", Thread.currentThread().getName());
            addPlayerFields(json, player);
            json.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
            json.addProperty("dimension", player.level().dimension().identifier().toString());
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
        return this.onIntegratedServer((server, player) -> {
            ServerLevel level = player.level();
            BlockPos position = new BlockPos(x, y, z);
            JsonObject json = base("server.world.block");
            addLiveMetadata(json, "server_authoritative_live", "integrated_server_live", "server_authoritative");
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
            json.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
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
        return this.onIntegratedServer((server, player) -> {
            ServerLevel level = player.level();
            double boundedRadius = Mth.clamp(radius, 0.0, 128.0);
            JsonArray values = new JsonArray();
            for (Entity entity : level.getEntities(
                    player, player.getBoundingBox().inflate(boundedRadius), entity -> true)) {
                if (values.size() >= 128) break;
                values.add(entityState(entity));
            }
            JsonObject json = base("server.world.entities");
            addLiveMetadata(json, "server_authoritative_live", "integrated_server_live", "server_authoritative");
            json.addProperty("serverTick", server.getTickCount());
            json.addProperty("dimension", level.dimension().identifier().toString());
            json.addProperty("radius", boundedRadius);
            json.add("entities", values);
            return json;
        });
    }

    @Override
    public CompletableFuture<byte[]> capturePng() {
        if (this.minecraft == null) return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException("RUNTIME_NOT_READY", 409, "Client is not ready"));
        return this.evidenceCaptures.request();
    }

    /** Invoked only at the final frame boundary before the Operator UI pass. */
    private CompletableFuture<byte[]> captureContentAtBoundary() {
        Minecraft client = requireClient();
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        try {
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image ->
                    Util.ioPool().execute(() -> encodeScreenshot(image, result)));
        } catch (Throwable error) { result.completeExceptionally(error); }
        return result;
    }

    @Override
    public CompletableFuture<JsonObject> captureInfo() {
        return this.onClient(() -> {
            JsonObject json = base("capture.info");
            String description = RenderSystem.getDevice().getDeviceInfo().backendName();
            json.addProperty("backend", description.toLowerCase(java.util.Locale.ROOT).contains("vulkan")
                    ? "vulkan" : "opengl");
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
        return requireClient().gameDirectory.toPath().resolve("minecraft-protocol").resolve("artifacts");
    }

    @Override
    public CompletableFuture<JsonObject> worldFingerprint() {
        if (this.shouldUsePeer()) return this.peerRequest("world.fingerprint", new JsonObject());
        return this.onIntegratedServer((server, player) -> {
            String material = "26.2-neoforge|" + server.getWorldData().getLevelName()
                    + "|" + player.level().dimension().identifier();
            JsonObject json = base("world.fingerprint");
            json.addProperty("worldFingerprint", sha256(material));
            json.addProperty("perspective", "server_authoritative_live");
            json.addProperty("sessionBound", true);
            json.addProperty("storageAccessed", false);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> fixtureTeleport(double x, double y, double z, AgentControlSession.OperateWork work) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("x", x);
            params.addProperty("y", y);
            params.addProperty("z", z);
            return this.operatingPeerRequest(work, "fixture.player.teleport", params);
        }
        return this.onOperatingServer(work, (server, player) -> {
            double beforeX = player.getX();
            double beforeY = player.getY();
            double beforeZ = player.getZ();
            player.teleportTo(x, y, z);
            JsonObject json = mutationEvidence("fixture.player.teleport", "FIXTURE", "SERVER_API", true);
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
    public CompletableFuture<JsonObject> debugSetHealth(float health, AgentControlSession.OperateWork work) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("health", health);
            return this.operatingPeerRequest(work, "debug.player.health", params);
        }
        return this.onOperatingServer(work, (server, player) -> {
            float before = player.getHealth();
            float applied = Mth.clamp(health, 0.0F, player.getMaxHealth());
            player.setHealth(applied);
            JsonObject json = mutationEvidence("debug.player.health", "DEBUG_PRIVILEGED", "DIRECT_MUTATION", true);
            json.addProperty("before", before);
            json.addProperty("requested", health);
            json.addProperty("applied", player.getHealth());
            json.addProperty("serverTick", server.getTickCount());
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> debugSetBlock(
            int x, int y, int z, String blockId, String expectedBlockId, AgentControlSession.OperateWork work) {
        if (this.shouldUsePeer()) {
            JsonObject params = new JsonObject();
            params.addProperty("x", x);
            params.addProperty("y", y);
            params.addProperty("z", z);
            params.addProperty("blockId", blockId);
            if (expectedBlockId != null) params.addProperty("expectedBlockId", expectedBlockId);
            return this.operatingPeerRequest(work, "debug.world.block", params);
        }
        return this.onOperatingServer(work, (server, player) -> {
            ServerLevel level = player.level();
            BlockPos position = new BlockPos(x, y, z);
            if (!level.hasChunkAt(position)) {
                throw new ProtocolState.ProtocolException("CHUNK_NOT_LOADED", 409, "Debug block target is not loaded");
            }
            Identifier id = Identifier.tryParse(blockId);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new ProtocolState.ProtocolException("UNKNOWN_BLOCK", 400, "Unknown block: " + blockId);
            }
            BlockState before = level.getBlockState(position);
            String beforeId = BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString();
            if (expectedBlockId != null && !expectedBlockId.equals(beforeId)) {
                throw new ProtocolState.ProtocolException(
                        "PRECONDITION_FAILED", 409,
                        "Expected block " + expectedBlockId + " but found " + beforeId);
            }
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            level.setBlockAndUpdate(position, block.defaultBlockState());
            JsonObject json = mutationEvidence("debug.world.block", "DEBUG_PRIVILEGED", "DIRECT_MUTATION", true);
            json.addProperty("x", x);
            json.addProperty("y", y);
            json.addProperty("z", z);
            json.addProperty("before", beforeId);
            json.addProperty("after", BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString());
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
        return this.onIntegratedServer((server, player) -> this.phase9a.observe(server, player, request));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aStorageRead(JsonObject request) {
        return io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                this.onClient(() -> {
                    Minecraft client = requireClient();
                    return client.level == null && client.getSingleplayerServer() == null && client.getConnection() == null;
                }),
                offline -> offline ? this.phase9a.readSavedStorage(request)
                        : io.github.recrivenvi.minecraftprotocol.safety.CancellableWork.compose(
                                this.onIntegratedServer((server, player) -> this.phase9a.storageRequest(server, player, request)),
                                this.phase9a::readStorage));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugAttribute(String attributeId, double value, AgentControlSession.OperateWork work) {
        return this.onOperatingServer(work, (server, player) -> this.phase9a.debugAttribute(player, attributeId, value));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugEntityState(String entityUuid, String state, boolean value, AgentControlSession.OperateWork work) {
        return this.onOperatingServer(work, (server, player) ->
                this.phase9a.debugEntityState(player, entityUuid, state, value));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugScenario(JsonObject request, AgentControlSession.OperateWork work) {
        return this.onOperatingServer(work, (server, player) -> this.phase9a.debugScenario(player, request));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aKeyframe(JsonObject request) {
        return this.onIntegratedServer((server, player) -> this.phase9a.keyframe(server, player, request));
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDelta(String baseSnapshotId) {
        return this.onIntegratedServer((server, player) ->
                this.phase9a.captureDelta(server, player, baseSnapshotId));
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
        String perspective = request.has("perspective")
                ? request.get("perspective").getAsString() : "server_authoritative";
        if (perspective.equals("client_known")) {
            return this.playerState().thenCompose(client ->
                    this.phase9a.formalize(request, client, null, requestContext));
        }
        CompletableFuture<JsonObject> server = this.onIntegratedServer((minecraftServer, player) ->
                this.phase9a.captureFormal(minecraftServer, player, request)).thenCompose(canonical ->
                this.phase9a.prepareFormalServerSnapshot(request, canonical));
        if (perspective.equals("server_authoritative")) {
            return server.thenCompose(value -> this.phase9a.formalize(request, null, value, requestContext));
        }
        if (!perspective.equals("both")) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "INVALID_PERSPECTIVE", 400, "perspective must be client_known, server_authoritative, or both"));
        }
        CompletableFuture<JsonObject> client = this.playerState();
        return client.thenCombine(server, (clientValue, serverValue) ->
                new JsonObject[] { clientValue, serverValue }).thenCompose(values ->
                this.phase9a.formalize(request, values[0], values[1], requestContext));
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugCapabilities() {
        return CompletableFuture.completedFuture(this.phase9a.formalDebugCapabilities());
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugMutation(
            JsonObject request, DebugMutationAuthorization authorization) {
        if (this.shouldUsePeer()) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE", 409,
                    "Phase 9C resource-version Debug requires Integrated Server authority; "
                            + "the Dedicated Peer currently retains only legacy typed health/block Debug"));
        }
        String operation = request.has("operation")
                    ? request.get("operation").getAsString() : "";
        if (operation.equals("provider.mutate")) {
            return this.phase9a.debugProviderMutation(request, authorization);
        }
        return this.onIntegratedServer((server, player) -> {
            String domain = operation.contains(".")
                    ? operation.substring(0, operation.indexOf('.')) : "unknown";
            String fingerprint = this.phase9cWorldFingerprint(server, player);
            try (DebugMutationAuthorization.Permit ignored = authorization.authorize(
                    fingerprint, this.phase9a.sessionEpoch(), domain, domain)) {
                return this.phase9a.debugMutation(server, player, request, fingerprint);
            }
        });
    }

    @Override
    public CompletableFuture<JsonObject> peerStatus() {
        return this.onClient(() -> {
            JsonObject json = DedicatedPeerClient.status();
            json.addProperty("target", "26.2-neoforge");
            json.addProperty("clientTick", this.clientTick);
            json.addProperty("integratedServer", requireClient().getSingleplayerServer() != null);
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
        Runnable check = new Runnable() {
            @Override
            public void run() {
                if (result.isDone()) return;
                session().whenComplete((json, error) -> {
                    if (result.isDone()) return;
                    if (error != null) result.completeExceptionally(error);
                    else if (json.get("screenClass").getAsString().contains(classContains)) {
                        json.addProperty("condition", "screen_class_contains");
                        result.complete(json);
                    } else if (System.nanoTime() >= deadline) {
                        result.completeExceptionally(new ProtocolState.ProtocolException(
                                "WAIT_TIMEOUT", 408, "wait.until timed out"));
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
    public CompletableFuture<JsonObject> validatePreconditions(Long expectedScreenRevision, Long expectedMenuRevision) {
        return this.onClient(() -> {
            Minecraft client = requireClient();
            this.refreshScreen(client);
            this.refreshMenu(client);
            if (expectedScreenRevision != null && expectedScreenRevision != this.screenRevision) {
                throw new ProtocolState.ProtocolException(
                        "STALE_SCREEN_REVISION", 409,
                        "Expected screen revision " + expectedScreenRevision + " but found " + this.screenRevision);
            }
            if (expectedMenuRevision != null && expectedMenuRevision != this.menuRevision) {
                throw new ProtocolState.ProtocolException(
                        "STALE_MENU_REVISION", 409,
                        "Expected menu revision " + expectedMenuRevision + " but found " + this.menuRevision);
            }
            JsonObject json = base("preconditions");
            json.addProperty("status", "satisfied");
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> inputState() {
        return this.onClient(() -> {
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
            this.addControlPresence(json);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> threadProbe(String affinity) {
        if (affinity.equals("client") || affinity.equals("render")) {
            return this.onClient(() -> {
                Minecraft client = requireClient();
                JsonObject json = base("diagnostics.thread");
                json.addProperty("requestedAffinity", affinity);
                json.addProperty("thread", Thread.currentThread().getName());
                json.addProperty("ownerThreadObserved",
                        affinity.equals("render") ? RenderSystem.isOnRenderThread() : client.isSameThread());
                return json;
            });
        }
        if (!affinity.equals("server")) {
            return CompletableFuture.failedFuture(new ProtocolState.ProtocolException(
                    "INVALID_THREAD_AFFINITY", 400, "Affinity must be client, render or server"));
        }
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        Minecraft client = this.minecraft;
        if (client == null) {
            result.completeExceptionally(new ProtocolState.ProtocolException(
                    "RUNTIME_NOT_READY", 409, "Client runtime is not ready"));
            return result;
        }
        client.execute(() -> {
            MinecraftServer server = client.getSingleplayerServer();
            if (server == null) {
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "SERVER_UNAVAILABLE", 409, "No integrated server is active"));
                return;
            }
            server.execute(() -> {
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
    public CompletableFuture<JsonObject> openAutomationProbeScreen(AgentControlSession.OperateWork work) {
        return this.onOperatingClient(work, () -> {
            Minecraft client = requireClient();
            client.setScreenAndShow(new AutomationProbeScreen());
            Screen screen = this.refreshScreen(client);
            JsonObject json = base("fixture.ui.opened");
            json.addProperty("screenClass", screen == null ? "" : screen.getClass().getName());
            json.addProperty("mechanism", "DIRECT");
            json.addProperty("perspective", "fixture");
            json.addProperty("evidenceContaminated", true);
            return json;
        });
    }

    @Override
    public CompletableFuture<JsonObject> releaseAllInput(String reason) {
        return this.onClientCleanup(() -> {
            Minecraft client = requireClient();
            int releasedKeys = this.pressedKeys.size();
            int releasedButtons = this.pressedButtons.size();
            for (Map.Entry<Integer, KeyState> entry : new HashMap<>(this.pressedKeys).entrySet()) {
                KeyState state = entry.getValue();
                AgentInputContext.dispatch(AgentInputContext.Event.key(client.getWindow().handle(), entry.getKey(), state.scanCode(), 0, state.modifiers()), () -> ((KeyboardHandlerInvoker) client.keyboardHandler).minecraftProtocolProbe$keyPress(
                        client.getWindow().handle(), 0, new KeyEvent(entry.getKey(), state.scanCode(), state.modifiers())));
            }
            for (int button : new HashSet<>(this.pressedButtons)) {
                if (this.buttonScreens.containsKey(button) && (!java.util.Objects.equals(this.buttonFrames.get(button), this.pointerFrameStamp(client)) || this.buttonScreens.get(button) != client.gui.screen())) {
                    this.clearMouseButtonState(client);
                    continue;
                }
                AgentInputContext.dispatch(AgentInputContext.Event.button(client.getWindow().handle(), button, 0, 0), () -> ((MouseHandlerInvoker) client.mouseHandler).minecraftProtocolProbe$onButton(
                        client.getWindow().handle(), new MouseButtonInfo(button, 0), 0));
            }
            this.pressedKeys.clear();
            this.pressedButtons.clear();
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
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2.ReadContext context,
            String affinity) {
        return switch (affinity) {
            case "client_thread" -> this.onClient(() -> ProviderExecutionEngine.enter(
                    provider, context, requireClient().isSameThread()));
            case "server_thread" -> this.onIntegratedServer((server, player) ->
                    ProviderExecutionEngine.enter(provider, context, server.isSameThread()));
            case "render_thread" -> this.onClient(() -> ProviderExecutionEngine.enter(
                    provider, context,
                    com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()));
            default -> CompletableFuture.completedFuture(
                    ProviderExecutionEngine.Entry.unsupported(affinity));
        };
    }

    private CompletableFuture<ProviderExecutionEngine.MutationEntry> dispatchProviderMutation(
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2 provider,
            io.github.recrivenvi.minecraftprotocol.probe.api.AgentDataProviderV2.DebugContext context,
            String affinity,
            DebugMutationAuthorization authorization) {
        if (!"server_thread".equals(affinity)) {
            return CompletableFuture.completedFuture(
                    ProviderExecutionEngine.MutationEntry.unsupported(affinity));
        }
        return this.onIntegratedServer((server, player) -> {
            String fingerprint = this.phase9cWorldFingerprint(server, player);
            DebugMutationAuthorization.Permit permit = authorization.authorize(
                    fingerprint, this.phase9a.sessionEpoch(), "provider", "provider");
            return ProviderExecutionEngine.enterMutation(
                    provider, context, server.isSameThread(), permit);
        });
    }

    private <T> CompletableFuture<T> onOperatingClient(
            AgentControlSession.OperateWork work, Supplier<T> supplier) {
        return this.onClient(() -> work.call(supplier));
    }

    private <T> CompletableFuture<T> onOperatingServer(
            AgentControlSession.OperateWork work, BiFunction<MinecraftServer, ServerPlayer, T> operation) {
        return this.onIntegratedServer((server, player) -> work.call(() -> operation.apply(server, player)));
    }

    private CompletableFuture<JsonObject> operatingPeerRequest(
            AgentControlSession.OperateWork work, String operation, JsonObject params) {
        AgentControlSession.Guard guard = work.enter();
        CompletableFuture<JsonObject> source;
        try { source = this.peerRequest(operation, params); }
        catch (Throwable error) { guard.close(); throw error; }
        // Retain admission until native completion even if the HTTP caller cancels.
        source.whenComplete((value, error) -> guard.close());
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            if (error == null) response.complete(value);
            else response.completeExceptionally(error);
        });
        return response;
    }

    private <T> CompletableFuture<T> onControlledClient(Supplier<T> supplier) {
        AgentControlSession.Snapshot accepted = this.controlPresence;
        return this.onClient(() -> {
            AgentControlSession.Snapshot current = this.controlPresence;
            if (!current.agentControlled() || !accepted.agentControlled()
                    || current.transitionSequence() != accepted.transitionSequence()) {
                throw new ProtocolState.ProtocolException(current.manuallyRevoked()
                        ? "USER_MANUALLY_ENDED_CONTROL" : "CONTROL_LEASE_REQUIRED", 409,
                        current.manuallyRevoked() ? "用户手动结束控制" : "Control session ended before dispatch");
            }
            ensureExclusiveOwnerState();
            return this.controlSession.withTakeover(accepted, supplier);
        });
    }

    private <T> CompletableFuture<T> onClientCleanup(Supplier<T> supplier) {
        Minecraft client = this.minecraft;
        if (client != null && client.isSameThread()) {
            try { return CompletableFuture.completedFuture(supplier.get()); }
            catch (Throwable error) { return CompletableFuture.failedFuture(error); }
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
        client.execute(() -> {
            if (result.isDone()) return;
            try {
                result.complete(supplier.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    private boolean shouldUsePeer() {
        Minecraft client = this.minecraft;
        return client != null && client.getConnection() != null
                && (client.getSingleplayerServer() == null || peerForceEnabled());
    }

    private static boolean peerForceEnabled() {
        String environment = System.getenv("MCP_PEER_FORCE");
        return Boolean.getBoolean("minecraft.protocol.peer.force")
                || "1".equals(environment)
                || "true".equalsIgnoreCase(environment);
    }

    private CompletableFuture<JsonObject> peerRequest(String operation, JsonObject params) {
        return DedicatedPeerClient.request(operation, params).thenApply(json -> {
            json.addProperty("target", "26.2-neoforge");
            json.addProperty("clientTick", this.clientTick);
            return json;
        });
    }

    private <T> CompletableFuture<T> onIntegratedServer(
            BiFunction<MinecraftServer, ServerPlayer, T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Minecraft client = this.minecraft;
        if (client == null) {
            result.completeExceptionally(new ProtocolState.ProtocolException(
                    "RUNTIME_NOT_READY", 409, "Client runtime is not ready"));
            return result;
        }
        client.execute(() -> {
            MinecraftServer server = client.getSingleplayerServer();
            java.util.UUID playerId = client.player == null ? null : client.player.getUUID();
            if (server == null || playerId == null) {
                result.completeExceptionally(new ProtocolState.ProtocolException(
                        "SERVER_AUTHORITATIVE_UNAVAILABLE", 409,
                        "Integrated Server authority is not available"));
                return;
            }
            server.execute(() -> {
                try {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) {
                        throw new ProtocolState.ProtocolException(
                                "SERVER_PLAYER_UNAVAILABLE", 409,
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
        String material = "26.2-neoforge|" + server.getWorldData().getLevelName()
                + "|" + player.level().dimension().identifier();
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
        json.addProperty("target", "26.2-neoforge");
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
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

    private static String semanticRole(GuiEventListener child) {
        String name = child.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("button")) return "button";
        if (name.contains("editbox") || name.contains("textfield")) return "text_field";
        if (name.contains("checkbox")) return "checkbox";
        if (name.contains("slider")) return "slider";
        if (name.contains("list")) return "list";
        if (name.contains("stringwidget") || name.contains("textwidget")) return "text";
        return child instanceof AbstractWidget ? "widget" : "listener";
    }

    private static void addInteractionMetadata(JsonObject node, String role, boolean actionable) {
        if (node.has("x") && node.has("y") && node.has("width") && node.has("height")) {
            node.addProperty("interactionX", node.get("x").getAsDouble() + node.get("width").getAsDouble() / 2.0);
            node.addProperty("interactionY", node.get("y").getAsDouble() + node.get("height").getAsDouble() / 2.0);
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

    private void recordRenderFact(String category, String stateClass, int x, int y, int width, int height) {
        long sequence = this.renderFactSequence.incrementAndGet();
        this.lastRenderFact = category + " class=" + stateClass
                + " bounds=" + x + "," + y + "," + width + "," + height;
        synchronized (this.recentRenderFacts) {
            this.recentRenderFacts.addLast(new RenderFact(
                    sequence, this.clientTick, this.screenRevision, category, stateClass, x, y, width, height));
            while (this.recentRenderFacts.size() > 256) this.recentRenderFacts.removeFirst();
        }
    }

    private synchronized void refreshMenu(Minecraft client) {
        AbstractContainerMenu menu = client.player == null ? null : client.player.containerMenu;
        int menuHash = menuHash(client);
        if (menu != this.lastMenu) {
            this.lastMenu = menu;
            this.lastMenuHash = menuHash;
            this.menuRevision++;
            return;
        }
        if (menuHash != this.lastMenuHash) {
            this.lastMenuHash = menuHash;
            this.menuRevision++;
        }
    }

    private static int menuHash(Minecraft client) {
        if (client.player == null || client.player.containerMenu == null) return 0;
        int hash = client.player.containerMenu.containerId;
        for (Slot slot : client.player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            hash = 31 * hash + BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            hash = 31 * hash + stack.getCount();
        }
        return hash;
    }

    private static JsonObject operatorHook(String id, String target, boolean cancellable, boolean replacement, String observed) {
        JsonObject result = hook(id, replacement ? "MIXIN_REDIRECT" : "MIXIN_INJECT", target,
                "EXPLICIT_OPERATOR_BOUNDARY", "operator_control", observed, "control.agent_presence");
        result.addProperty("plane", "OPERATOR_CONTROL");
        result.addProperty("cancellable", cancellable);
        result.addProperty("replacement", replacement);
        result.addProperty("intentionalControlFlow", cancellable || replacement);
        return result;
    }

    private static JsonObject hook(
            String id, String mechanism, String target, String injectionPoint,
            String behavior, String runtimeStatus, String failureCapability) {
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

    private record KeyState(int scanCode, int modifiers) {
    }

    private record RenderFact(
            long sequence,
            long clientTick,
            long screenRevision,
            String category,
            String stateClass,
            int x,
            int y,
            int width,
            int height) {
    }
}
