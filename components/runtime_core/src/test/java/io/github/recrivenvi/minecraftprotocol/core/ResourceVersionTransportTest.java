package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceVersionTransportTest {
    @TempDir Path artifacts;

    private final class Fixture implements AutoCloseable {
        final ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        final HttpClient client = HttpClient.newHttpClient();
        final AtomicInteger mutationEntries = new AtomicInteger();
        final ProbeTransport transport;
        final int port;
        final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        final String fingerprint = "resource-version-world";
        final JsonObject initial;
        JsonObject current;
        String arm;

        Fixture(boolean mutationScope) throws Exception {
            initial = tracker.revision("block", "dimension@1,2,3", "instance-a", state(1));
            current = tracker.revision("block", "dimension@1,2,3", "instance-a", state(2));
            System.setProperty(
                    "minecraft.protocol.scopes",
                    "read,control,debug,debug.write,debug.world.read"
                            + (mutationScope ? ",debug.world" : ""));
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, args) -> {
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
                                        JsonObject value = new JsonObject();
                                        if (method.getName().equals("worldFingerprint"))
                                            value.addProperty("worldFingerprint", fingerprint);
                                        if (method.getName().equals("phase9cDebugCapabilities")) {
                                            value.addProperty(
                                                    "sessionEpoch", tracker.sessionEpoch());
                                            value.add(
                                                    "resourceInspection",
                                                    WorldDebugInspection.contract());
                                        }
                                        if (method.getName().equals("formalDeepObservation")) {
                                            JsonArray refs = new JsonArray();
                                            refs.add(current.deepCopy());
                                            value.add("resourceRevisionRefs", refs);
                                        }
                                        if (method.getName().equals("phase9cDebugMutation")) {
                                            mutationEntries.incrementAndGet();
                                            JsonObject request = (JsonObject) args[0];
                                            DebugMutationAuthorization authorization =
                                                    (DebugMutationAuthorization) args[1];
                                            try (var permit =
                                                    authorization.authorize(
                                                            fingerprint,
                                                            tracker.sessionEpoch(),
                                                            "world",
                                                            "world")) {
                                                ResourceVersionVerifier.verifyAuthorized(
                                                        request.getAsJsonObject(
                                                                "expectedResourceVersion"),
                                                        current);
                                                value.addProperty("type", "debug.mutation.result");
                                                value.addProperty("namespace", "world");
                                                value.addProperty("operation", "world.block.set");
                                                value.addProperty("evidenceContaminated", true);
                                            } catch (Throwable error) {
                                                return CompletableFuture.failedFuture(error);
                                            }
                                        }
                                        return CompletableFuture.completedFuture(value);
                                    });
            transport = new ProbeTransport(service, "resource-version-test", 0, "test-target");
            transport.startAsync();
            port = transport.boundPort().get(5, java.util.concurrent.TimeUnit.SECONDS);
            for (int i = 0; i < 100; i++) {
                try {
                    value("GET", "/v0/control/mode", null, 200);
                    return;
                } catch (Exception notReady) {
                    Thread.sleep(25);
                }
            }
            close();
            throw new AssertionError("Transport did not start");
        }

        JsonObject value(String method, String path, JsonObject body, int status) throws Exception {
            var request =
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .header("Authorization", "Bearer resource-version-test")
                            .header("x-mcp-debug-arm", arm == null ? "" : arm)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(5))
                            .method(
                                    method,
                                    body == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofString(body.toString()))
                            .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode(), response.body());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }

        void prepare() throws Exception {
            JsonObject mode = new JsonObject();
            mode.addProperty("mode", "OPERATE");
            mode.add(
                    "expectedModeVersion",
                    value("GET", "/v0/control/mode", null, 200).get("modeVersion"));
            value("POST", "/v0/control/mode", mode, 200);
            JsonObject body = new JsonObject();
            body.addProperty("worldFingerprint", fingerprint);
            body.addProperty("ttlMs", 60000);
            JsonArray namespaces = new JsonArray();
            namespaces.add("world");
            body.add("namespaces", namespaces);
            arm = value("POST", "/v0/debug/arm", body, 200).get("debugArmId").getAsString();
        }

        JsonObject mutation(JsonObject version) {
            JsonObject body = new JsonObject();
            body.addProperty("operation", "world.block.set");
            body.addProperty("worldFingerprint", fingerprint);
            body.add("expectedResourceVersion", version.deepCopy());
            return body;
        }

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    @Test
    void productionHttpErrorKeepsSafeStaleCountersButNotIdentityFailures() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.prepare();
            JsonObject query = new JsonObject();
            query.addProperty("category", "DEBUG");
            JsonObject observation = f.value("POST", "/v0/observe/deep", query, 200);
            assertEquals(f.current, observation.getAsJsonArray("resourceRevisionRefs").get(0));
            JsonObject stale = f.value("POST", "/v0/debug/mutations", f.mutation(f.initial), 409);
            assertEquals("STALE_RESOURCE_REVISION", stale.get("error").getAsString());
            assertCounters(stale, f.initial, f.current);
            JsonObject wrong = f.initial.deepCopy();
            wrong.addProperty("resourceKey", "other");
            JsonObject denied = f.value("POST", "/v0/debug/mutations", f.mutation(wrong), 409);
            assertEquals("RESOURCE_MISMATCH", denied.get("error").getAsString());
            assertFalse(denied.has("actualRevision"));
            f.arm = null;
            denied = f.value("POST", "/v0/debug/mutations", f.mutation(f.initial), 409);
            assertEquals("DEBUG_NOT_ARMED", denied.get("error").getAsString());
            assertFalse(denied.has("actualRevision"));
            assertEquals(2, f.mutationEntries.get());
        }
    }

    @Test
    void scopeDenialOccursBeforeTheServiceCanGenerateRevisionMetadata() throws Exception {
        try (Fixture f = new Fixture(false)) {
            JsonObject denied = f.value("POST", "/v0/debug/mutations", f.mutation(f.initial), 403);
            assertEquals("DEBUG_SCOPE_DENIED", denied.get("error").getAsString());
            assertFalse(denied.has("actualRevision"));
            assertEquals(0, f.mutationEntries.get());
        }
    }

    @Test
    void productionBatchOperationKeepsStaleCountersUnderDebugReadAuthorization() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.prepare();
            JsonObject request = new JsonObject();
            JsonArray items = new JsonArray();
            items.add(f.mutation(f.initial));
            request.add("items", items);
            JsonObject accepted = f.value("POST", "/v0/debug/batches", request, 200);
            JsonObject wait = new JsonObject();
            wait.addProperty("timeoutMs", 2000);
            String path = "/v0/operations/" + accepted.get("operationId").getAsString();
            JsonObject finished = f.value("POST", path + "/wait?category=DEBUG", wait, 200);
            JsonObject item =
                    finished.getAsJsonObject("result")
                            .getAsJsonArray("items")
                            .get(0)
                            .getAsJsonObject();
            assertEquals("STALE_RESOURCE_REVISION", item.get("error").getAsString());
            assertCounters(item.getAsJsonObject("errorDetails"), f.initial, f.current);
            JsonObject user = f.value("GET", path, null, 404);
            assertFalse(user.has("actualRevision"));
        }
    }

    @Test
    void productionFailedOperationPublishesOnlySanitizedRevisionMetadata() throws Exception {
        ObservationRevisionTracker tracker = new ObservationRevisionTracker();
        JsonObject before = tracker.revision("block", "one", "life", state(1));
        JsonObject after = tracker.revision("block", "one", "life", state(2));
        Throwable stale =
                assertThrows(
                        ProtocolState.ProtocolException.class,
                        () -> ResourceVersionVerifier.verifyAuthorized(before, after));
        try (ProtocolState state =
                new ProtocolState(
                        Set.of("read", "debug", "debug.world.read"), "test", ignored -> {})) {
            JsonObject started =
                    state.startOperation(
                            id -> CompletableFuture.failedFuture(stale), false, Set.of("WORLD"));
            String id = started.get("operationId").getAsString();
            JsonObject completed = state.operationStatus(id);
            assertEquals("failed", completed.get("state").getAsString());
            assertCounters(completed.getAsJsonObject("errorDetails"), before, after);
        }
    }

    private static void assertCounters(JsonObject value, JsonObject expected, JsonObject actual) {
        assertNotNull(value);
        assertEquals(expected.get("revision"), value.get("expectedRevision"));
        assertEquals(actual.get("revision"), value.get("actualRevision"));
        assertTrue(value.get("reinspectRequired").getAsBoolean());
        assertFalse(value.get("retrySafe").getAsBoolean());
        assertFalse(value.has("currentResourceVersion"));
    }

    private static JsonObject state(int number) {
        JsonObject result = new JsonObject();
        result.addProperty("value", number);
        return result;
    }
}
