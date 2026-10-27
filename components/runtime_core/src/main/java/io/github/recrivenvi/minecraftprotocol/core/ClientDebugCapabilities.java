package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Local UI discovery remains independent of the remote world's availability and authority. */
public final class ClientDebugCapabilities {
    private ClientDebugCapabilities() {}

    private static final Set<String> UNAVAILABLE_WORLD =
            Set.of(
                    "SERVER_PEER_UNAVAILABLE",
                    "SERVER_PEER_DISCONNECTED",
                    "SERVER_PEER_TIMEOUT",
                    "SERVER_PEER_OWNER_UNAVAILABLE",
                    "PEER_DELEGATION_REQUIRED",
                    "PEER_DELEGATION_NOT_FOUND",
                    "PEER_AUTHORITY_REVOKED",
                    "PEER_CONNECTION_MISMATCH",
                    "PEER_OPERATION_UNSUPPORTED",
                    "SCOPE_DENIED",
                    "DEBUG_SCOPE_DENIED");

    public static CompletableFuture<JsonObject> combine(
            CompletableFuture<JsonObject> world, String localEpoch, List<String> uiOperations) {
        return CancellableWork.handle(
                world,
                (value, failure) -> {
                    JsonObject discovery = new JsonObject();
                    JsonObject result;
                    if (failure == null) {
                        result = value.deepCopy();
                        discovery.addProperty("available", true);
                        result.add("resourceInspection", WorldDebugInspection.contract());
                    } else {
                        Throwable cause = failure;
                        while ((cause instanceof CompletionException
                                        || cause instanceof ExecutionException)
                                && cause.getCause() != null) cause = cause.getCause();
                        // Cancellation, shutdown and unexpected implementation failures must still
                        // fail.
                        if (!(cause instanceof ProtocolState.ProtocolException error)
                                || !UNAVAILABLE_WORLD.contains(error.code())) {
                            if (cause instanceof RuntimeException runtime) throw runtime;
                            throw new CompletionException(cause);
                        }
                        result = new JsonObject();
                        result.addProperty("type", "debug.capabilities");
                        result.addProperty("schemaVersion", "phase9c-debug-v0");
                        result.addProperty("formal", true);
                        result.addProperty("sessionEpoch", localEpoch);
                        result.addProperty("requiresDebugArm", true);
                        result.addProperty("requiresWorldFingerprint", true);
                        result.addProperty("requiresResourceVersion", true);
                        result.addProperty("persistentStorageMutation", false);
                        result.addProperty("arbitraryReflection", false);
                        result.addProperty("arbitraryPacketInjection", false);
                        result.add("operations", new JsonArray());
                        JsonObject deployments = new JsonObject();
                        deployments.addProperty("clientUi", "available");
                        deployments.addProperty("dedicatedServerPeer", "unavailable");
                        result.add("deployments", deployments);
                        discovery.addProperty("available", false);
                        discovery.addProperty("errorCode", error.code());
                        discovery.addProperty("httpStatus", error.httpStatus());
                    }
                    result.add("worldDiscovery", discovery);
                    for (String operation : uiOperations) {
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
                    return result;
                });
    }
}
