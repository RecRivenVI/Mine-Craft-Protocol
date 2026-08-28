package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;

interface ProbeService {
    CompletableFuture<JsonObject> session();
    CompletableFuture<JsonObject> capabilities();
    CompletableFuture<JsonObject> readiness();
    CompletableFuture<JsonObject> hookManifest();
    CompletableFuture<JsonObject> trace();
    CompletableFuture<JsonObject> renderFacts();
    CompletableFuture<JsonObject> uiTree();
    CompletableFuture<JsonObject> mouseMove(double guiX, double guiY);
    CompletableFuture<JsonObject> mouseButton(int button, int action, int modifiers);
    CompletableFuture<JsonObject> mouseScroll(double xOffset, double yOffset);
    CompletableFuture<JsonObject> key(int key, int scanCode, int action, int modifiers);
    CompletableFuture<JsonObject> playerCommand(String command);
    CompletableFuture<JsonObject> playerState();
    CompletableFuture<JsonObject> blockState(int x, int y, int z);
    CompletableFuture<JsonObject> entities(double radius);
    CompletableFuture<JsonObject> serverPlayerState();
    CompletableFuture<JsonObject> serverBlockState(int x, int y, int z);
    CompletableFuture<JsonObject> serverEntities(double radius);
    CompletableFuture<byte[]> capturePng();
    CompletableFuture<JsonObject> captureInfo();
    Path artifactRoot();
    CompletableFuture<JsonObject> worldFingerprint();
    CompletableFuture<JsonObject> fixtureTeleport(double x, double y, double z);
    CompletableFuture<JsonObject> debugSetHealth(float health);
    CompletableFuture<JsonObject> debugSetBlock(int x, int y, int z, String blockId, String expectedBlockId);
    CompletableFuture<JsonObject> phase9aInventory();
    CompletableFuture<JsonObject> phase9aObserve(JsonObject request);
    CompletableFuture<JsonObject> phase9aStorageRead(JsonObject request);
    CompletableFuture<JsonObject> phase9aDebugAttribute(String attributeId, double value);
    CompletableFuture<JsonObject> phase9aDebugEntityState(String entityUuid, String state, boolean value);
    CompletableFuture<JsonObject> phase9aDebugScenario(JsonObject request);
    CompletableFuture<JsonObject> phase9aKeyframe(JsonObject request);
    CompletableFuture<JsonObject> phase9aDelta(String baseSnapshotId);
    CompletableFuture<JsonObject> phase9aReconstruct(JsonObject request);
    CompletableFuture<JsonObject> formalObservationCapabilities();
    CompletableFuture<JsonObject> formalDeepObservation(
            JsonObject request, DeepObservationRequestContext requestContext);
    CompletableFuture<JsonObject> peerStatus();
    CompletableFuture<JsonObject> peerProbe();
    CompletableFuture<JsonObject> waitForScreen(String classContains, long timeoutMillis);
    CompletableFuture<JsonObject> validatePreconditions(Long expectedScreenRevision, Long expectedMenuRevision);
    CompletableFuture<JsonObject> inputState();
    CompletableFuture<JsonObject> threadProbe(String affinity);
    CompletableFuture<JsonObject> openAutomationProbeScreen();
    CompletableFuture<JsonObject> releaseAllInput(String reason);
}
