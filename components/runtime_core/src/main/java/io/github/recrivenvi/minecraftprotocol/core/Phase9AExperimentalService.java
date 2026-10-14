package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.util.concurrent.CompletableFuture;

/** Optional diagnostics implemented only by Targets that expose the existing spike routes. */
public interface Phase9AExperimentalService extends ProbeService {
    CompletableFuture<JsonObject> phase9aObserve(JsonObject request);

    CompletableFuture<JsonObject> phase9aDebugAttribute(
            String attributeId, double value, AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> phase9aDebugEntityState(
            String entityUuid, String state, boolean value, AgentControlSession.OperateWork work);

    CompletableFuture<JsonObject> phase9aKeyframe(JsonObject request);

    CompletableFuture<JsonObject> phase9aDelta(String baseSnapshotId);

    CompletableFuture<JsonObject> phase9aReconstruct(JsonObject request);
}
