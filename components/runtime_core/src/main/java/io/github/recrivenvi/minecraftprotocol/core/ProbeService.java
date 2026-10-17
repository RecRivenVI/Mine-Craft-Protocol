package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface ProbeService {
    default void attachControlSession(AgentControlSession session) {}

    default void attachPlayerCommandGate(PlayerCommandGate gate) {}

    default void controlPresenceChanged(AgentControlSession.Snapshot snapshot) {}

    CompletableFuture<JsonObject> session();

    CompletableFuture<JsonObject> capabilities();

    CompletableFuture<JsonObject> readiness();

    CompletableFuture<JsonObject> hookManifest();

    CompletableFuture<JsonObject> trace();

    CompletableFuture<JsonObject> renderFacts();

    CompletableFuture<JsonObject> uiTree();

    default CompletableFuture<JsonObject> debugUiTree() {
        return uiTree();
    }

    CompletableFuture<JsonObject> pointerState();

    CompletableFuture<JsonObject> mouseMove(double guiX, double guiY);

    CompletableFuture<JsonObject> mouseMoveGuarded(double guiX, double guiY, JsonObject guard);

    CompletableFuture<JsonObject> mouseButtonGuarded(
            int button, int action, int modifiers, JsonObject guard);

    CompletableFuture<JsonObject> mouseScrollGuarded(
            double xOffset, double yOffset, JsonObject guard);

    CompletableFuture<JsonObject> mouseDelta(double dx, double dy);

    default void gestureEvent(String id, String phase, JsonObject detail) {}

    CompletableFuture<JsonObject> mouseButton(int button, int action, int modifiers);

    CompletableFuture<JsonObject> mouseScroll(double xOffset, double yOffset);

    CompletableFuture<JsonObject> key(int key, int scanCode, int action, int modifiers);

    default CompletableFuture<JsonObject> keyGuarded(
            int key, int scanCode, int action, int modifiers, JsonObject guard) {
        return key(key, scanCode, action, modifiers);
    }

    default CompletableFuture<JsonObject> character(int codePoint, JsonObject guard) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Character input unavailable"));
    }

    CompletableFuture<JsonObject> playerCommand(String command, String phase);

    CompletableFuture<JsonObject> playerState();

    CompletableFuture<JsonObject> blockState(int x, int y, int z);

    CompletableFuture<JsonObject> entities(double radius);

    default CompletableFuture<JsonObject> worldAimDelta(
            String targetRef, JsonObject guard, double dx, double dy) {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "WORLD_ACTION_UNAVAILABLE", 409, "Guarded world aiming unavailable"));
    }

    default CompletableFuture<JsonObject> worldActionFrame(
            String targetRef, String action, JsonObject guard, boolean press) {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "WORLD_ACTION_UNAVAILABLE", 409, "Semantic world input unavailable"));
    }

    default CompletableFuture<JsonObject> worldActionFrame(
            String targetRef, String action, JsonObject guard, boolean press, JsonObject options) {
        return worldActionFrame(targetRef, action, guard, press);
    }

    default CompletableFuture<JsonObject> worldActionProgress(
            String targetRef, JsonObject guard, boolean released) {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "WORLD_ACTION_UNAVAILABLE", 409, "World gesture monitoring unavailable"));
    }

    default CompletableFuture<JsonObject> worldActionEnd() {
        return CompletableFuture.completedFuture(new JsonObject());
    }

    default CompletableFuture<JsonObject> clientWorldRead(String kind, JsonObject query) {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "WORLD_USER_MODEL_UNAVAILABLE",
                        409,
                        "Bounded client world model unavailable"));
    }

    default CompletableFuture<JsonObject> worldPresentation() {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "WORLD_USER_PERCEPTION_UNAVAILABLE",
                        409,
                        "Native world presentation is unavailable"));
    }

    CompletableFuture<JsonObject> serverPlayerState();

    CompletableFuture<JsonObject> serverBlockState(int x, int y, int z);

    CompletableFuture<JsonObject> serverEntities(double radius);

    CompletableFuture<byte[]> capturePng();

    CompletableFuture<JsonObject> captureInfo();

    Path artifactRoot();

    CompletableFuture<JsonObject> worldFingerprint();

    default CompletableFuture<JsonObject> uiDebugContext() {
        return CompletableFuture.failedFuture(
                new ProtocolState.ProtocolException(
                        "CAPABILITY_UNAVAILABLE", 409, "Client UI Debug context unavailable"));
    }

    CompletableFuture<JsonObject> fixtureTeleport(
            double x, double y, double z, AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> debugSetHealth(
            float health, AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> debugSetBlock(
            int x,
            int y,
            int z,
            String blockId,
            String expectedBlockId,
            AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> phase9aInventory();

    CompletableFuture<JsonObject> phase9aStorageRead(JsonObject request);

    CompletableFuture<JsonObject> phase9aDebugScenario(
            JsonObject request, AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> formalObservationCapabilities();

    CompletableFuture<JsonObject> formalDeepObservation(
            JsonObject request, DeepObservationRequestContext requestContext);

    CompletableFuture<JsonObject> phase9cDebugCapabilities();

    CompletableFuture<JsonObject> phase9cDebugMutation(
            JsonObject request, DebugMutationAuthorization authorization);

    CompletableFuture<JsonObject> peerStatus();

    CompletableFuture<JsonObject> peerProbe();

    CompletableFuture<JsonObject> waitForScreen(String classContains, long timeoutMillis);

    CompletableFuture<JsonObject> validatePreconditions(
            Long expectedScreenRevision, Long expectedMenuRevision);

    CompletableFuture<JsonObject> inputState();

    CompletableFuture<JsonObject> threadProbe(String affinity);

    CompletableFuture<JsonObject> openAutomationProbeScreen(AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> releaseAllInput(String reason);
}
