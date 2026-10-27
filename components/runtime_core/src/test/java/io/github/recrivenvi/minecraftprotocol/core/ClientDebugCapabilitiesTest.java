package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ClientDebugCapabilitiesTest {
    private static final String EPOCH = "11111111-1111-4111-8111-111111111111";
    private static final List<String> UI = List.of("ui.widget.active.set", "ui.text.set");

    @Test
    void unavailableWorldStillPublishesLocalOperationsAndStructuredReason() {
        for (String code :
                List.of(
                        "SERVER_PEER_UNAVAILABLE",
                        "PEER_DELEGATION_REQUIRED",
                        "PEER_AUTHORITY_REVOKED",
                        "SCOPE_DENIED",
                        "SERVER_PEER_TIMEOUT")) {
            var failure = new ProtocolState.ProtocolException(code, 403, "private remote message");
            var result =
                    ClientDebugCapabilities.combine(
                                    CompletableFuture.failedFuture(
                                            new CompletionException(failure)),
                                    EPOCH,
                                    UI)
                            .join();
            assertEquals(EPOCH, result.get("sessionEpoch").getAsString());
            assertEquals(2, result.getAsJsonArray("operations").size());
            assertFalse(result.getAsJsonObject("worldDiscovery").get("available").getAsBoolean());
            assertEquals(
                    code, result.getAsJsonObject("worldDiscovery").get("errorCode").getAsString());
            assertFalse(result.has("resourceInspection"));
            assertFalse(result.toString().contains("private remote message"));
            for (var item : result.getAsJsonArray("operations")) {
                var operation = item.getAsJsonObject();
                assertEquals("CLIENT_UI", operation.get("authority").getAsString());
                assertTrue(operation.get("requiresArm").getAsBoolean());
                assertEquals("debug.ui", operation.get("requiredScope").getAsString());
            }
        }
    }

    @Test
    void successfulWorldCatalogueIsPreservedAndNotMutated() {
        JsonObject world =
                JsonParser.parseString(
                                "{\"sessionEpoch\":\"remote-epoch\",\"operations\":[{\"operation\":\"world.block.set\",\"status\":\"available\"}]}")
                        .getAsJsonObject();
        var result =
                ClientDebugCapabilities.combine(CompletableFuture.completedFuture(world), EPOCH, UI)
                        .join();
        assertEquals("remote-epoch", result.get("sessionEpoch").getAsString());
        assertEquals(3, result.getAsJsonArray("operations").size());
        assertEquals(1, world.getAsJsonArray("operations").size());
        assertTrue(result.getAsJsonObject("worldDiscovery").get("available").getAsBoolean());
        assertTrue(result.has("resourceInspection"));
        assertFalse(result.get("uiRequiresWorldFingerprint").getAsBoolean());
    }

    @Test
    void cancellationShutdownAndProgrammingFailuresAreNotPartialSuccess() {
        var source = new CompletableFuture<JsonObject>();
        var result = ClientDebugCapabilities.combine(source, EPOCH, UI);
        assertTrue(result.cancel(false));
        assertTrue(source.isCancelled());
        for (RuntimeException failure :
                List.of(
                        new CancellationException(),
                        new ProtocolState.ProtocolException("RUNTIME_SHUTTING_DOWN", 409, "closed"),
                        new IllegalStateException("invalid catalogue"))) {
            var failed =
                    ClientDebugCapabilities.combine(
                            CompletableFuture.failedFuture(failure), EPOCH, UI);
            assertTrue(failed.isCompletedExceptionally());
            assertThrows(RuntimeException.class, failed::join);
        }
    }
}
