package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.InputContextEpoch;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlayerStateStreamTransportTest {
    @TempDir Path artifacts;

    @Test
    void actualUserWebSocketReceivesPoseInReadWithoutAnInputLeaseAndMatchesItsGetSource()
            throws Exception {
        try (Fixture fixture = new Fixture("read,event");
                Socket socket = fixture.connect("?type=world.player.state&eventCategory=world")) {
            JsonObject mode = fixture.get("/v0/control/mode");
            assertEquals("READ", mode.get("mode").getAsString());
            assertFalse(mode.get("takeoverActive").getAsBoolean());
            fixture.publish(1);
            JsonObject event = socket.await("world.player.state");
            JsonObject player = fixture.get("/v0/player?category=USER");
            for (String field :
                    new String[] {
                        "x",
                        "y",
                        "z",
                        "yaw",
                        "pitch",
                        "onGround",
                        "velocityX",
                        "velocityY",
                        "velocityZ"
                    }) assertEquals(player.get(field), event.get(field), field);
            assertEquals("USER", event.get("dataCategory").getAsString());
            assertEquals("CLIENT_KNOWN", event.get("authority").getAsString());
            assertEquals("world", event.get("category").getAsString());
            assertEquals(1, event.get("sourceTick").getAsLong());
            assertEquals(
                    fixture.context.opaqueId(),
                    event.getAsJsonObject("streamContext").get("opaqueId").getAsString());
            assertTrue(event.get("sequence").getAsLong() > 0);
            assertFalse(event.has("uuid"));
            assertFalse(event.has("health"));
            assertEquals(1, fixture.playerReads.get());
            assertEquals(0, fixture.inputCalls.get());
            assertEquals("READ", fixture.get("/v0/control/mode").get("mode").getAsString());
        }
    }

    @Test
    void ordinarySubscriptionDoesNotOptIntoHighFrequencyPlayerState() throws Exception {
        try (Fixture fixture = new Fixture("read,event");
                Socket socket = fixture.connect("")) {
            fixture.publish(1);
            socket.assertNoPose();
            fixture.transport.broadcast(event("event.screen.changed"));
            assertEquals(
                    "event.screen.changed",
                    socket.await("event.screen.changed").get("type").getAsString());
        }
    }

    @Test
    void contextReplacementRejectsLateFramesAndReplaySignalsGapInsteadOfOldWorldData()
            throws Exception {
        try (Fixture fixture = new Fixture("read,event");
                Socket socket = fixture.connect("?type=world.player.state")) {
            InputContextEpoch.Snapshot oldContext = fixture.context;
            fixture.publish(1);
            JsonObject old = socket.await("world.player.state");
            fixture.replaceContext(true);
            fixture.transport.publishPlayerState(oldContext, pose(2));
            socket.assertNoPose();
            fixture.publish(1);
            JsonObject fresh = socket.await("world.player.state");
            assertNotEquals(old.get("streamContext"), fresh.get("streamContext"));
            assertEquals(
                    fixture.context.opaqueId(),
                    fresh.getAsJsonObject("streamContext").get("opaqueId").getAsString());
            try (Socket replay = fixture.connect("?type=world.player.state&resumeFromSequence=0")) {
                assertTrue(replay.await("event.gap").get("fullResyncRequired").getAsBoolean());
                assertEquals(
                        fresh.get("streamContext"),
                        replay.await("world.player.state").get("streamContext"));
            }
            fixture.replaceContext(false);
            fixture.transport.publishPlayerState(oldContext, pose(3));
            fixture.publish(2);
            socket.assertNoPose();
            assertFalse(fixture.get("/v0/player?category=USER").get("available").getAsBoolean());
        }
    }

    @Test
    void missingReadOrEventScopeCannotSubscribeOrCauseInputEscalation() throws Exception {
        for (String scopes : new String[] {"read", "event"}) {
            try (Fixture fixture = new Fixture(scopes)) {
                Capture listener = new Capture();
                ExecutionException rejected =
                        assertThrows(
                                ExecutionException.class,
                                () ->
                                        fixture.http
                                                .newWebSocketBuilder()
                                                .connectTimeout(Duration.ofSeconds(3))
                                                .header("Authorization", "Bearer state-stream-test")
                                                .buildAsync(
                                                        fixture.uri(
                                                                "ws",
                                                                "/v0/events?type=world.player.state"),
                                                        listener)
                                                .get(5, TimeUnit.SECONDS));
                assertInstanceOf(WebSocketHandshakeException.class, rejected.getCause());
                assertEquals(
                        403,
                        ((WebSocketHandshakeException) rejected.getCause())
                                .getResponse()
                                .statusCode());
                assertDoesNotThrow(() -> fixture.publish(1));
                assertEquals(0, fixture.inputCalls.get());
                assertEquals(0, fixture.playerReads.get());
            }
        }
    }

    @Test
    void closedTransportDoesNotPublishOrReviveTheOldContext() throws Exception {
        try (Fixture fixture = new Fixture("read,event");
                Socket socket = fixture.connect("?type=world.player.state")) {
            fixture.publish(1);
            socket.await("world.player.state");
            fixture.transport.close();
            assertTrue(socket.capture.finished.await(5, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> fixture.publish(2));
            assertDoesNotThrow(() -> fixture.transport.nativeInputContextChanged(fixture.context));
            socket.assertNoPose();
            assertEquals(0, fixture.inputCalls.get());
        }
    }

    private final class Fixture implements AutoCloseable {
        private final String oldScopes = System.getProperty("minecraft.protocol.scopes");
        private final HttpClient http = HttpClient.newHttpClient();
        private final InputContextEpoch epoch = new InputContextEpoch();
        private final AtomicInteger inputCalls = new AtomicInteger();
        private final AtomicInteger playerReads = new AtomicInteger();
        private InputContextEpoch.Snapshot context;
        private final ProbeTransport transport;
        private final int port;

        Fixture(String scopes) throws Exception {
            System.setProperty("minecraft.protocol.scopes", scopes);
            ProbeService service =
                    (ProbeService)
                            Proxy.newProxyInstance(
                                    ProbeService.class.getClassLoader(),
                                    new Class<?>[] {ProbeService.class},
                                    (proxy, method, arguments) -> {
                                        if (method.getReturnType() == void.class) return null;
                                        if (method.getName().equals("artifactRoot"))
                                            return artifacts;
                                        if (method.getName().equals("clientWorldRead")) {
                                            playerReads.incrementAndGet();
                                            return CompletableFuture.completedFuture(player());
                                        }
                                        if (method.getName().equals("playerState"))
                                            throw new AssertionError(
                                                    "USER pose must not use the DEBUG player source");
                                        if (java.util.Set.of(
                                                        "key",
                                                        "mouseMove",
                                                        "mouseDelta",
                                                        "mouseButton",
                                                        "mouseScroll",
                                                        "playerCommand")
                                                .contains(method.getName()))
                                            inputCalls.incrementAndGet();
                                        JsonObject result = new JsonObject();
                                        result.addProperty("type", method.getName());
                                        if (method.getName().equals("releaseAllInput")) {
                                            result.addProperty("releasedKeys", 0);
                                            result.addProperty("releasedButtons", 0);
                                        }
                                        return CompletableFuture.completedFuture(result);
                                    });
            transport = new ProbeTransport(service, "state-stream-test", 0, "state-stream-test");
            transport.startAsync();
            port = transport.boundPort().get(5, TimeUnit.SECONDS);
            replaceContext(true);
        }

        void replaceContext(boolean available) {
            context =
                    available
                            ? epoch.observe(new Object(), new Object(), new Object())
                            : epoch.observe(null, null, null);
            transport.nativeInputContextChanged(context);
        }

        void publish(long tick) {
            transport.publishPlayerState(context, pose(tick));
        }

        JsonObject player() {
            JsonObject result = new JsonObject();
            result.addProperty("type", "player.state");
            result.addProperty("available", context.worldAvailable());
            result.addProperty("dataCategory", "USER");
            result.addProperty("authority", "CLIENT_KNOWN");
            if (!context.worldAvailable()) return result;
            PlayerStateStream.Sample value = pose(1);
            result.addProperty("x", value.x());
            result.addProperty("y", value.y());
            result.addProperty("z", value.z());
            result.addProperty("yaw", value.yaw());
            result.addProperty("pitch", value.pitch());
            result.addProperty("onGround", value.onGround());
            result.addProperty("velocityX", value.velocityX());
            result.addProperty("velocityY", value.velocityY());
            result.addProperty("velocityZ", value.velocityZ());
            return result;
        }

        URI uri(String scheme, String path) {
            return URI.create(scheme + "://127.0.0.1:" + port + path);
        }

        JsonObject get(String path) throws Exception {
            HttpResponse<String> result =
                    http.send(
                            HttpRequest.newBuilder(uri("http", path))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Authorization", "Bearer state-stream-test")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, result.statusCode(), result.body());
            return JsonParser.parseString(result.body()).getAsJsonObject();
        }

        Socket connect(String query) throws Exception {
            Capture capture = new Capture();
            WebSocket socket =
                    http.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .header("Authorization", "Bearer state-stream-test")
                            .buildAsync(uri("ws", "/v0/events" + query), capture)
                            .get(5, TimeUnit.SECONDS);
            Socket connected = new Socket(socket, capture);
            connected.await("event.hello");
            return connected;
        }

        @Override
        public void close() {
            transport.close();
            if (oldScopes == null) System.clearProperty("minecraft.protocol.scopes");
            else System.setProperty("minecraft.protocol.scopes", oldScopes);
        }
    }

    private record Socket(WebSocket socket, Capture capture) implements AutoCloseable {
        JsonObject await(String type) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < end) {
                JsonObject value =
                        capture.messages.poll(
                                Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
                assertNotNull(value, "Missing actual WebSocket event: " + type);
                if (value.has("type") && type.equals(value.get("type").getAsString())) return value;
            }
            throw new AssertionError("Missing actual WebSocket event: " + type);
        }

        void assertNoPose() throws Exception {
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(150);
            while (System.nanoTime() < end) {
                JsonObject value =
                        capture.messages.poll(
                                Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (value == null) return;
                assertFalse(PlayerStateStream.isStateEvent(value), "Unexpected pose: " + value);
            }
        }

        @Override
        public void close() {
            socket.abort();
        }
    }

    private static final class Capture implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        private final StringBuilder text = new StringBuilder();
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(JsonParser.parseString(text.toString()).getAsJsonObject());
                text.setLength(0);
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            finished.countDown();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            finished.countDown();
        }
    }

    private static PlayerStateStream.Sample pose(long tick) {
        return new PlayerStateStream.Sample(
                tick, 1000 + tick, 1.125, 64.5, -2.75, 30.125, -12.5, true, .03125, 0, -.0625);
    }

    private static JsonObject event(String type) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        return event;
    }
}
