package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Explicitly absent client surfaces; never substitutes console authority for a player. */
public abstract class HeadlessProbeService implements ProbeService {
    private final Path artifacts;

    protected HeadlessProbeService(Path directory) {
        artifacts = directory.resolve("minecraft-protocol/artifacts");
    }

    protected static <T> CompletableFuture<T> unavailable() {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "CAPABILITY_UNAVAILABLE",
                        409,
                        "This surface is not implemented on the dedicated server Runtime"));
    }

    @Override
    public CompletableFuture<JsonObject> session() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> capabilities() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> readiness() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> hookManifest() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> trace() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> renderFacts() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> uiTree() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> pointerState() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseMove(double guiX, double guiY) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseMoveGuarded(
            double guiX, double guiY, JsonObject guard) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseButtonGuarded(
            int button, int action, int modifiers, JsonObject guard) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseScrollGuarded(
            double xOffset, double yOffset, JsonObject guard) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseDelta(double dx, double dy) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseButton(int button, int action, int modifiers) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> mouseScroll(double xOffset, double yOffset) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> key(int key, int scanCode, int action, int modifiers) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> playerCommand(String command, String phase) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> playerState() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> blockState(int x, int y, int z) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> entities(double radius) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> serverPlayerState() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> serverBlockState(int x, int y, int z) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> serverEntities(double radius) {
        return unavailable();
    }

    @Override
    public CompletableFuture<byte[]> capturePng() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> captureInfo() {
        return unavailable();
    }

    @Override
    public Path artifactRoot() {
        return artifacts;
    }

    @Override
    public CompletableFuture<JsonObject> worldFingerprint() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> fixtureTeleport(
            double x, double y, double z, AgentControlSession.OperateWork work) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> debugSetHealth(
            float health, AgentControlSession.OperateWork work) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> debugSetBlock(
            int x,
            int y,
            int z,
            String blockId,
            String expectedBlockId,
            AgentControlSession.OperateWork work) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> phase9aInventory() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> phase9aStorageRead(JsonObject request) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> phase9aDebugScenario(
            JsonObject request, AgentControlSession.OperateWork work) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> formalObservationCapabilities() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> formalDeepObservation(
            JsonObject request, DeepObservationRequestContext requestContext) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugCapabilities() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> phase9cDebugMutation(
            JsonObject request, DebugMutationAuthorization authorization) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> peerStatus() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> peerProbe() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> waitForScreen(String classContains, long timeoutMillis) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> validatePreconditions(
            Long expectedScreenRevision, Long expectedMenuRevision) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> inputState() {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> threadProbe(String affinity) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> openAutomationProbeScreen(
            AgentControlSession.OperateWork work) {
        return unavailable();
    }

    @Override
    public CompletableFuture<JsonObject> releaseAllInput(String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("pressedKeyCount", 0);
        result.addProperty("pressedButtonCount", 0);
        return CompletableFuture.completedFuture(result);
    }
}
