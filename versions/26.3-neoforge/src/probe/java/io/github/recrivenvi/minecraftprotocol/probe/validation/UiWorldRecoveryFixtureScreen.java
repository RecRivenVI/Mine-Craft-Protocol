package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.probe.validation.mixin.UiWorldRecoveryOptimizeProbeAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.validation.mixin.UiWorldRecoverySavedDataProbeAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.validation.mixin.UiWorldRecoveryWorkerProbeAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.RecoverWorldDataScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;

/** Real native recovery/optimization of exclusive auxiliary copies; never the source world. */
public final class UiWorldRecoveryFixtureScreen extends Screen {
    private static final String TARGET = "26.3-neoforge";
    private static final String AUX = "world-recovery-aux";
    private static IntegratedServer sourceServer;
    private static UiWorldRecoveryFixtureScreen activeOwner;
    private final Screen previous;
    private final String fixtureId = UUID.randomUUID().toString();
    private final Path clientRoot;
    private final Path auxiliaryRoot;
    private final String sourceLevelId;
    private int sequence;
    private int optimizeAttempts;
    private int recoverAttempts;
    private String activeCase = "";
    private String nativeClass = "";
    private String manifestHash = "";
    private String callbackAction = "none";
    private String failure = "";
    private boolean returned;
    private boolean storageOpened;
    private boolean storageClosed;
    private boolean lockReacquired;
    private boolean primaryHealthy;
    private boolean fallbackHealthy;
    private boolean restoreActive;
    private boolean nativeCancelReturned;
    private boolean workerJoined;
    private boolean nativeRemovedCompleted;
    private boolean savedDataClosed;
    private boolean finishedBeforeCancel;
    private int nativeCancelCalls;
    private Screen nativeScreen;
    private WorldUpgrader upgrader;
    private LevelStorageSource storage;
    private LevelStorageSource.LevelStorageAccess access;

    public UiWorldRecoveryFixtureScreen(Screen previous) {
        super(Component.literal("World Recovery (Fixture)"));
        if (!enabled())
            throw new IllegalStateException(
                    "World recovery requires explicit validation fixture enablement");
        this.previous = previous;
        clientRoot = ownedClientRoot(Minecraft.getInstance());
        auxiliaryRoot = clientRoot.getParent().resolve(AUX);
        sourceLevelId =
                sourceServer == null
                        ? "UI-Conformance"
                        : sourceServer
                                .getWorldPath(LevelResource.ROOT)
                                .toAbsolutePath()
                                .normalize()
                                .getFileName()
                                .toString();
    }

    public static boolean enabled() {
        return UiNavigationFixtureScreen.enabled()
                && TARGET.equals(System.getProperty("validation.target"))
                && Boolean.getBoolean("minecraft.protocol.uiWorldRecoveryFixture");
    }

    /** Captures only the actual integrated server of the owned fixed validation world. */
    public static void observeClientTick() {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        IntegratedServer current = client.getSingleplayerServer();
        if (current == null) return;
        Path root = ownedClientRoot(client);
        Path world = current.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (world.startsWith(root.resolve("saves"))
                && "UI-Conformance".equals(current.getWorldData().getLevelSettings().levelName())) {
            sourceServer = current;
        }
    }

    private static Path ownedClientRoot(Minecraft client) {
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        Path target = root.getParent();
        if (!"client".equals(root.getFileName().toString())
                || target == null
                || !TARGET.equals(target.getFileName().toString())
                || !"instance".equals(target.getParent().getFileName().toString())
                || !"conformance-vanilla_ui"
                        .equals(target.getParent().getParent().getFileName().toString())
                || !"validations"
                        .equals(
                                target.getParent()
                                        .getParent()
                                        .getParent()
                                        .getFileName()
                                        .toString())) {
            throw new IllegalStateException("World recovery may only use its validation client");
        }
        return root;
    }

    private boolean sourceStopped() {
        return sourceServer != null
                && sourceServer.isStopped()
                && !sourceServer.getRunningThread().isAlive()
                && !sourceServer.isCurrentlySaving()
                && sourceServer
                        .getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize()
                        .equals(clientRoot.resolve("saves").resolve(sourceLevelId));
    }

    private void requireDisconnected() {
        if (minecraft.level != null
                || minecraft.player != null
                || minecraft.getConnection() != null
                || minecraft.getSingleplayerServer() != null) {
            throw new IllegalStateException(
                    "Normal world shutdown must precede auxiliary world recovery");
        }
    }

    @Override
    protected void init() {
        requireDisconnected();
        returned =
                !activeCase.isEmpty()
                        && minecraft.gui.screen() == this
                        && !callbackAction.equals("none");
        if (returned) closeAuxiliaryAccess();
        int top = height / 2 - 36;
        Button recover =
                Button.builder(
                                Component.literal("Healthy Recovery (Fixture)"),
                                button -> launch("recover"))
                        .bounds(width / 2 - 100, top, 200, 20)
                        .build();
        recover.active = recoverAttempts == 0 && access == null;
        addRenderableWidget(recover);
        Button optimize =
                Button.builder(
                                Component.literal("Optimize Aux World (Fixture)"),
                                button -> launch("optimize"))
                        .bounds(width / 2 - 100, top + 24, 200, 20)
                        .build();
        optimize.active = optimizeAttempts == 0 && access == null;
        addRenderableWidget(optimize);
        addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                        .bounds(width / 2 - 100, top + 48, 200, 20)
                        .build());
        report();
    }

    private void launch(String name) {
        requireDisconnected();
        if (!sourceStopped() || access != null)
            throw new IllegalStateException(
                    "The saved source and auxiliary ownership must be idle");
        if (name.equals("recover") ? recoverAttempts++ != 0 : optimizeAttempts++ != 0)
            throw new IllegalStateException("Each auxiliary native page is attempted once");
        activeCase = name;
        sequence++;
        returned = storageOpened = storageClosed = lockReacquired = false;
        primaryHealthy = fallbackHealthy = restoreActive = false;
        nativeCancelReturned =
                workerJoined =
                        nativeRemovedCompleted = savedDataClosed = finishedBeforeCancel = false;
        nativeCancelCalls = 0;
        callbackAction = "none";
        failure = "";
        nativeScreen = null;
        upgrader = null;
        activeOwner = this;
        try {
            validateAuxiliaryCopy(name);
            storage = LevelStorageSource.createDefault(auxiliaryRoot.resolve("worlds"));
            access = storage.validateAndCreateAccess(name);
            storageOpened = true;
            if (name.equals("recover")) {
                access.collectIssues(false);
                primaryHealthy = true;
                access.collectIssues(true);
                fallbackHealthy = true;
                nativeScreen =
                        new RecoverWorldDataScreen(
                                minecraft, accepted -> complete("recover_back", accepted), access);
            } else {
                nativeScreen =
                        OptimizeWorldScreen.create(
                                minecraft,
                                accepted ->
                                        complete(
                                                accepted
                                                        ? "optimize_finished"
                                                        : "optimize_cancelled",
                                                accepted),
                                minecraft.getFixerUpper(),
                                access,
                                false);
                if (nativeScreen == null)
                    throw new IllegalStateException(
                            "Native optimizer creation failed; no optimizer success is inferred");
                upgrader =
                        ((UiWorldRecoveryOptimizeProbeAccessor) nativeScreen)
                                .minecraftProtocolProbe$upgrader();
            }
            nativeClass = nativeScreen.getClass().getName();
            report();
            minecraft.gui.setScreen(nativeScreen);
            if (name.equals("recover")) {
                // Native disabled buttons add an inactive style without changing their translation
                // content.
                Button restore =
                        nativeScreen.children().stream()
                                .filter(child -> child instanceof Button)
                                .map(child -> (Button) child)
                                .filter(
                                        button ->
                                                button.getMessage()
                                                        .getContents()
                                                        .equals(
                                                                Component.translatable(
                                                                                "recover_world.restore")
                                                                        .getContents()))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Actual native Restore button is absent"));
                restoreActive = restore.active;
                if (restoreActive)
                    throw new IllegalStateException(
                            "A healthy source cannot expose a writable recovery operation");
                report();
            }
        } catch (Exception problem) {
            failure = problem.toString();
            report();
            throw new IllegalStateException(
                    "Auxiliary native world UI could not be arranged", problem);
        }
    }

    private void complete(String action, boolean accepted) {
        if (!callbackAction.equals("none"))
            throw new IllegalStateException("Native auxiliary callback was already consumed");
        if (activeCase.equals("recover") && accepted)
            throw new IllegalStateException(
                    "The healthy recovery fixture does not authorize Restore");
        callbackAction = action;
        finishedBeforeCancel |= action.equals("optimize_finished");
        minecraft.gui.setScreen(this);
    }

    public static void observeNativeCancelStarting(WorldUpgrader worker) {
        UiWorldRecoveryFixtureScreen owner = activeOwner;
        if (!enabled() || owner == null || owner.upgrader != worker || owner.nativeCancelCalls != 0)
            return;
        owner.finishedBeforeCancel |= worker.isFinished();
    }

    public static void observeNativeCancel(WorldUpgrader worker) {
        UiWorldRecoveryFixtureScreen owner = activeOwner;
        if (!enabled() || owner == null || owner.upgrader != worker) return;
        owner.nativeCancelCalls++;
        owner.nativeCancelReturned = true;
        owner.workerJoined =
                !((UiWorldRecoveryWorkerProbeAccessor) worker)
                        .minecraftProtocolProbe$thread()
                        .isAlive();
    }

    public static void observeNativeRemoved(OptimizeWorldScreen screen) {
        UiWorldRecoveryFixtureScreen owner = activeOwner;
        if (!enabled() || owner == null || owner.nativeScreen != screen || owner.upgrader == null)
            return;
        owner.nativeRemovedCompleted = true;
        owner.savedDataClosed =
                ((UiWorldRecoverySavedDataProbeAccessor)
                                ((UiWorldRecoveryWorkerProbeAccessor) owner.upgrader)
                                        .minecraftProtocolProbe$savedData())
                        .minecraftProtocolProbe$closed();
    }

    private void closeAuxiliaryAccess() {
        if (access == null) return;
        try {
            if (upgrader != null
                    && (!nativeRemovedCompleted
                            || !nativeCancelReturned
                            || !workerJoined
                            || !savedDataClosed
                            || ((UiWorldRecoveryWorkerProbeAccessor) upgrader)
                                    .minecraftProtocolProbe$thread()
                                    .isAlive())) {
                throw new IllegalStateException(
                        "The native optimizer worker or saved-data storage is not closed");
            }
            access.close();
            storageClosed = true;
            access = null;
            try (LevelStorageSource.LevelStorageAccess check =
                    storage.validateAndCreateAccess(activeCase)) {
                if (!check.getLevelPath(LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize()
                        .equals(auxiliaryRoot.resolve("worlds").resolve(activeCase)))
                    throw new IllegalStateException(
                            "Reacquired storage escaped the owned auxiliary copy");
            }
            lockReacquired =
                    !DirectoryLock.isLocked(auxiliaryRoot.resolve("worlds").resolve(activeCase));
            if (!lockReacquired)
                throw new IllegalStateException("Auxiliary directory lock was not released");
            activeOwner = null;
        } catch (IOException | ContentValidationException | RuntimeException problem) {
            failure = problem.toString();
            try {
                report();
            } catch (RuntimeException reportFailure) {
                problem.addSuppressed(reportFailure);
            }
            throw new IllegalStateException("Auxiliary storage cleanup failed", problem);
        }
    }

    private void validateAuxiliaryCopy(String name) throws IOException {
        if (!clientRoot.toRealPath().equals(clientRoot)
                || !auxiliaryRoot.toRealPath().equals(auxiliaryRoot))
            throw new IOException("Linked validation directories are not accepted");
        JsonObject request = json(auxiliaryRoot.resolve("request.json"), 4096);
        JsonObject owner = json(auxiliaryRoot.resolve("owner.json"), 4096);
        if (request.get("version").getAsInt() != 1
                || owner.get("version").getAsInt() != 1
                || !request.get("target").getAsString().equals(TARGET)
                || !request.get("fixtureId").getAsString().equals(fixtureId)
                || request.get("processId").getAsLong() != ProcessHandle.current().pid()
                || !request.get("sourceLevelId").getAsString().equals(sourceLevelId)
                || !request.get("auxRootRelative").getAsString().equals(AUX)
                || !request.get("worldContainerRelative").getAsString().equals(AUX + "/worlds")
                || !request.get("recoverLevelId").getAsString().equals("recover")
                || !request.get("optimizeLevelId").getAsString().equals("optimize")
                || request.get("eraseCache").getAsBoolean()
                || request.get("maximumOptimizeAttempts").getAsInt() != 1
                || !owner.get("runActive").getAsBoolean()
                || owner.get("resourcesReleased").getAsBoolean()
                || !owner.get("validation").getAsString().equals("conformance-vanilla_ui")
                || !owner.get("target").getAsString().equals(TARGET)
                || !owner.get("sourceLevelId").getAsString().equals(sourceLevelId)
                || !owner.get("auxRootRelative").getAsString().equals(AUX)
                || !owner.get("fixtureId").getAsString().equals(fixtureId)
                || owner.get("nativeProcessId").getAsLong() != ProcessHandle.current().pid()) {
            throw new IOException(
                    "Auxiliary copy request is stale, foreign or outside its fixed boundary");
        }
        Path manifestPath = auxiliaryRoot.resolve("copy-manifest.json");
        if (Files.size(manifestPath) > 524288)
            throw new IOException("Auxiliary manifest exceeded its bounded size");
        manifestHash = hash(manifestPath);
        if (!manifestHash.equals(request.get("copyManifestSha256").getAsString()))
            throw new IOException("Auxiliary manifest hash differs");
        JsonObject manifest = json(manifestPath, 524288);
        if (manifest.get("version").getAsInt() != 1
                || !manifest.get("validation").getAsString().equals("conformance-vanilla_ui")
                || !manifest.get("target").getAsString().equals(TARGET)
                || !manifest.get("sourceLevelId").getAsString().equals(sourceLevelId)
                || !manifest.get("sourceWorldName").getAsString().equals("UI-Conformance"))
            throw new IOException("Source identity differs");
        JsonArray files = manifest.getAsJsonArray("files");
        if (files.isEmpty()
                || files.size() > 1024
                || files.size() != request.get("fileCount").getAsInt()
                || files.size() != manifest.get("fileCount").getAsInt())
            throw new IOException("Auxiliary file budget differs");
        Path world = auxiliaryRoot.resolve("worlds").resolve(name);
        if (!world.toRealPath().equals(world))
            throw new IOException("Auxiliary worlds must not be linked");
        Set<String> expected = new HashSet<>();
        long total = 0;
        for (var item : files) {
            JsonObject fact = item.getAsJsonObject();
            String relative = fact.get("path").getAsString();
            Path path = world.resolve(relative).normalize();
            if (relative.contains("\\")
                    || !path.startsWith(world)
                    || path.equals(world)
                    || !expected.add(relative)
                    || relative.equals("session.lock")
                    || !Files.isRegularFile(path)
                    || !path.toRealPath().equals(path))
                throw new IOException("Auxiliary manifest contains an invalid world file");
            long bytes = Files.size(path);
            total += bytes;
            if (bytes > 64 * 1024 * 1024
                    || bytes != fact.get("bytes").getAsLong()
                    || total > 256 * 1024 * 1024
                    || !hash(path).equals(fact.get("sha256").getAsString()))
                throw new IOException("Auxiliary file bytes/hash budget differs");
        }
        if (!expected.contains("level.dat")
                || !expected.contains("level.dat_old")
                || total != request.get("totalBytes").getAsLong()
                || total != manifest.get("totalBytes").getAsLong())
            throw new IOException("Real primary/fallback data and exact byte budget are required");
        try (var entries = Files.walk(world)) {
            int count = 0;
            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (++count > 4096 || !path.toRealPath().equals(path) || Files.isSymbolicLink(path))
                    throw new IOException("Auxiliary entry budget or link rejected");
                if (Files.isRegularFile(path)) {
                    String key = world.relativize(path).toString().replace('\\', '/');
                    if (!key.equals("session.lock") && !expected.contains(key))
                        throw new IOException("Unexpected auxiliary world file");
                }
            }
        }
    }

    private static JsonObject json(Path file, int maximumBytes) throws IOException {
        if (!file.toRealPath().equals(file)
                || !Files.isRegularFile(file)
                || Files.size(file) > maximumBytes)
            throw new IOException("Auxiliary JSON input must be a bounded owned regular file");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static String hash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) >= 0)
                    if (count > 0) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException problem) {
            throw new IllegalStateException(problem);
        }
    }

    private void report() {
        Minecraft client = Minecraft.getInstance();
        JsonObject value = new JsonObject();
        value.addProperty("probeOnly", true);
        value.addProperty("arrangeAuthority", "DIAGNOSTIC_owned_aux_world");
        value.addProperty("target", TARGET);
        value.addProperty("fixtureId", fixtureId);
        value.addProperty("processId", ProcessHandle.current().pid());
        value.addProperty("sequence", sequence);
        value.addProperty("case", activeCase);
        value.addProperty("nativeClass", nativeClass);
        value.addProperty("sourceLevelId", sourceLevelId);
        value.addProperty(
                "sourceWorldName",
                sourceServer == null
                        ? ""
                        : sourceServer.getWorldData().getLevelSettings().levelName());
        value.addProperty(
                "integratedServerStopped",
                client.getSingleplayerServer() == null && sourceStopped());
        value.addProperty("playerAbsent", client.player == null);
        value.addProperty("connectionAbsent", client.getConnection() == null);
        value.addProperty("sourceSaveCompleted", sourceStopped());
        value.addProperty("sourceWorldWritten", false);
        value.addProperty("immutableBaselineTouched", false);
        value.addProperty("dailyInstancesTouched", false);
        value.addProperty("networkConnectionCreated", false);
        value.addProperty("actualCorruptionInduced", false);
        value.addProperty("worldFilesTouched", activeCase.isEmpty() ? "none" : "auxonly");
        value.addProperty("auxRootRelative", AUX);
        value.addProperty("auxLevelId", activeCase);
        value.addProperty("copyManifestSha256", manifestHash);
        value.addProperty("returnedToFixture", returned);
        value.addProperty("callbackAction", callbackAction);
        value.addProperty("storageAccessOpened", storageOpened);
        value.addProperty("storageAccessClosed", storageClosed);
        value.addProperty("directoryLockReacquiredAndClosed", lockReacquired);
        value.addProperty("healthyPrimary", primaryHealthy);
        value.addProperty("healthyFallback", fallbackHealthy);
        value.addProperty("restoreButtonActive", restoreActive);
        value.addProperty("nativeBusinessRepairPerformed", false);
        value.addProperty("realWorldUpgraderCreated", upgrader != null);
        value.addProperty("nativeFinishedBeforeCancel", finishedBeforeCancel);
        value.addProperty("nativeCancelReturned", nativeCancelReturned);
        value.addProperty("nativeCancelCalls", nativeCancelCalls);
        value.addProperty("workerJoined", workerJoined);
        value.addProperty(
                "workerTerminated",
                upgrader != null
                        && !((UiWorldRecoveryWorkerProbeAccessor) upgrader)
                                .minecraftProtocolProbe$thread()
                                .isAlive());
        value.addProperty("nativeRemovedCompleted", nativeRemovedCompleted);
        value.addProperty("savedDataStorageClosed", savedDataClosed);
        if (!failure.isEmpty()) value.addProperty("failure", failure);
        try {
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve(TARGET + "-world-recovery-ui-probe.json"),
                    value + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException problem) {
            throw new IllegalStateException(
                    "Native world recovery facts were not recorded", problem);
        }
    }

    @Override
    public void onClose() {
        if (access != null)
            throw new IllegalStateException(
                    "Unreleased auxiliary storage is retained for diagnosis");
        minecraft.gui.setScreen(previous);
    }
}
