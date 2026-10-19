package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class EventHubStateStreamTest {
    private static final PlayerStateStream.Context FIRST =
            new PlayerStateStream.Context(1, "first");
    private static final PlayerStateStream.Context SECOND =
            new PlayerStateStream.Context(2, "second");

    @Test
    void poseRequiresExplicitTypeOrWorldNamespaceAndOtherFiltersStillApply() {
        PlayerStateStream stream = stream();
        EventHub hub = hub(stream, () -> {});
        var channels = new ArrayList<EmbeddedChannel>();
        try {
            for (String query :
                    List.of(
                            "",
                            "?type=world.player.state",
                            "?namespace=world",
                            "?type=world.player.state&eventCategory=not_present",
                            "?namespace=world&entity=not_present")) {
                EmbeddedChannel channel = new EmbeddedChannel();
                channels.add(channel);
                register(hub, channel, query);
            }
            assertTrue(hub.wantsPlayerState());
            hub.publish(pose(stream, FIRST, 1));
            for (var channel : channels) channel.runPendingTasks();
            assertNull(channels.get(0).readOutbound(), "Default all must not include a 20 Hz pose");
            assertEquals(
                    PlayerStateStream.EVENT_TYPE, read(channels.get(1)).get("type").getAsString());
            assertEquals(
                    PlayerStateStream.EVENT_TYPE, read(channels.get(2)).get("type").getAsString());
            assertNull(channels.get(3).readOutbound());
            assertNull(channels.get(4).readOutbound());
            hub.unregister(channels.get(1));
            hub.unregister(channels.get(2));
            assertFalse(hub.wantsPlayerState());
            hub.publish(event("screen.changed"));
            channels.get(0).runPendingTasks();
            assertEquals("screen.changed", read(channels.get(0)).get("type").getAsString());
        } finally {
            hub.close();
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }

    @Test
    void explicitPoseSubscriptionWithItsActualCategoryBothRequestsAndReceivesFrames() {
        PlayerStateStream stream = stream();
        EventHub hub = hub(stream, () -> {});
        long tick = 0;
        try {
            for (String selector :
                    List.of("type=" + PlayerStateStream.EVENT_TYPE, "namespace=world")) {
                EmbeddedChannel channel = new EmbeddedChannel();
                try {
                    register(
                            hub,
                            channel,
                            "?" + selector + "&eventCategory=" + PlayerStateStream.EVENT_CATEGORY);
                    assertTrue(hub.wantsPlayerState());
                    hub.publish(pose(stream, FIRST, ++tick));
                    channel.runPendingTasks();
                    JsonObject frame = read(channel);
                    assertEquals(PlayerStateStream.EVENT_TYPE, frame.get("type").getAsString());
                    assertEquals(
                            PlayerStateStream.EVENT_CATEGORY, frame.get("category").getAsString());
                    assertNull(channel.readOutbound());
                } finally {
                    hub.unregister(channel);
                    channel.finishAndReleaseAll();
                }
                assertFalse(hub.wantsPlayerState());
            }
        } finally {
            hub.close();
        }
    }

    @Test
    void changingTheExplicitSubscriptionTakesEffectForAlreadyQueuedFrames() {
        PlayerStateStream stream = stream();
        EventHub hub = hub(stream, () -> {});
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "?type=world.player.state");
            hub.publish(pose(stream, FIRST, 1));
            hub.accept(channel, "{\"type\":\"event.subscribe\",\"filter\":{}}");
            assertFalse(hub.wantsPlayerState());
            channel.runPendingTasks();
            assertEquals("event.subscription.accepted", read(channel).get("type").getAsString());
            assertNull(channel.readOutbound());
            hub.accept(
                    channel,
                    "{\"type\":\"event.subscribe\",\"filter\":{\"namespaces\":[\"world\"]}}");
            assertTrue(hub.wantsPlayerState());
            channel.runPendingTasks();
            read(channel);
            hub.publish(pose(stream, FIRST, 2));
            channel.runPendingTasks();
            assertEquals(PlayerStateStream.EVENT_TYPE, read(channel).get("type").getAsString());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectedPublicationConsumesNeitherSequenceNorConditionEvidence() {
        PlayerStateStream stream = stream();
        AtomicBoolean allowed = new AtomicBoolean(false);
        EventHub hub = hub(stream, () -> require(allowed));
        try {
            JsonObject frame = pose(stream, FIRST, 1);
            assertThrows(IllegalStateException.class, () -> hub.publish(frame));
            assertEquals(0, hub.currentSequence());
            allowed.set(true);
            assertFalse(hub.evaluateCondition(poseCondition()).get("passed").getAsBoolean());
            stream.setContext(SECOND);
            assertNull(hub.publish(frame));
            assertEquals(0, hub.currentSequence());
            assertFalse(hub.evaluateCondition(poseCondition()).get("passed").getAsBoolean());
        } finally {
            hub.close();
        }
    }

    @Test
    void contextIsCheckedAgainAtRingAdmissionAndAuthorizationRemainsOutsideItsMonitor() {
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<EventHub> owner = new AtomicReference<>();
        EventHub hub =
                new EventHub(
                        "test",
                        () -> CompletableFuture.completedFuture(new JsonObject()),
                        () -> assertNoHubMonitor(owner.get()),
                        event ->
                                !PlayerStateStream.isStateEvent(event)
                                        || checks.incrementAndGet() == 1);
        owner.set(hub);
        try {
            assertNull(hub.publish(event(PlayerStateStream.EVENT_TYPE)));
            assertEquals(2, checks.get());
            assertEquals(0, hub.currentSequence());
            assertFalse(hub.evaluateCondition(poseCondition()).get("passed").getAsBoolean());
        } finally {
            hub.close();
        }
    }

    @Test
    void authorizationLostBetweenCaptureAndRingAdmissionCannotPublish() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        EventHub hub =
                new EventHub(
                        "test",
                        () -> CompletableFuture.completedFuture(new JsonObject()),
                        () -> require(allowed),
                        event -> {
                            allowed.set(false);
                            return true;
                        });
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> hub.publish(event(PlayerStateStream.EVENT_TYPE)));
            assertEquals(0, hub.currentSequence());
        } finally {
            hub.close();
        }
    }

    @Test
    void epochReplacementDropsQueuedPoseWithAnExplicitGapAndKeepsTheNewContext() {
        PlayerStateStream stream = stream();
        EventHub hub = hub(stream, () -> {});
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "?type=world.player.state");
            hub.publish(pose(stream, FIRST, 1));
            stream.setContext(SECOND);
            hub.publish(pose(stream, SECOND, 1));
            channel.runPendingTasks();
            assertGap(read(channel), 1);
            JsonObject current = read(channel);
            assertEquals(
                    "second",
                    current.getAsJsonObject("streamContext").get("opaqueId").getAsString());
            assertEquals(2, current.get("sequence").getAsLong());
            assertNull(channel.readOutbound());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void contextReplacementDuringTheFinalSendAuthorizationCannotFlushOldPose() {
        PlayerStateStream stream = stream();
        AtomicInteger untilReplacement = new AtomicInteger(-1);
        EventHub hub =
                hub(
                        stream,
                        () -> {
                            if (untilReplacement.get() > 0
                                    && untilReplacement.decrementAndGet() == 0)
                                stream.setContext(SECOND);
                        });
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "?type=world.player.state");
            hub.publish(pose(stream, FIRST, 1));
            untilReplacement.set(2);
            channel.runPendingTasks();
            assertEquals(0, untilReplacement.get());
            assertGap(read(channel), 1);
            assertNull(channel.readOutbound());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void lostAuthorityAtFlushClosesTheSubscriptionWithoutDeliveringQueuedData() {
        PlayerStateStream stream = stream();
        AtomicBoolean allowed = new AtomicBoolean(true);
        EventHub hub = hub(stream, () -> require(allowed));
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "?type=world.player.state");
            hub.publish(pose(stream, FIRST, 1));
            allowed.set(false);
            channel.runPendingTasks();
            assertNull(channel.readOutbound());
            assertFalse(channel.isActive());
            assertFalse(hub.wantsPlayerState());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void replaySkipsOldContextRatherThanPretendingItIsCurrentState() {
        PlayerStateStream stream = stream();
        EventHub hub = hub(stream, () -> {});
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            hub.publish(pose(stream, FIRST, 1));
            stream.setContext(SECOND);
            hub.publish(pose(stream, SECOND, 1));
            register(hub, channel, "?type=world.player.state&resumeFromSequence=0");
            assertGap(read(channel), 1);
            JsonObject current = read(channel);
            assertEquals(2, current.get("sequence").getAsLong());
            assertEquals(
                    "second",
                    current.getAsJsonObject("streamContext").get("opaqueId").getAsString());
            assertNull(channel.readOutbound());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void conditionCannotMatchAnOldContextAndRechecksImmediatelyBeforeEvidencePublication() {
        PlayerStateStream stream = stream();
        AtomicInteger untilReplacement = new AtomicInteger(-1);
        EventHub hub =
                hub(
                        stream,
                        () -> {
                            if (untilReplacement.get() > 0
                                    && untilReplacement.decrementAndGet() == 0)
                                stream.setContext(SECOND);
                        });
        try {
            hub.publish(pose(stream, FIRST, 1));
            untilReplacement.set(2);
            JsonObject result = hub.evaluateCondition(poseCondition());
            assertFalse(result.get("passed").getAsBoolean());
            assertTrue(result.get("fullResyncRequired").getAsBoolean());
            assertFalse(result.has("evidence"));
            JsonObject repeated = hub.evaluateCondition(poseCondition());
            assertFalse(repeated.get("passed").getAsBoolean());
            assertTrue(repeated.get("fullResyncRequired").getAsBoolean());
            hub.publish(pose(stream, SECOND, 1));
            JsonObject fresh = hub.evaluateCondition(poseCondition());
            assertTrue(fresh.get("passed").getAsBoolean());
            assertEquals(
                    "second",
                    fresh.getAsJsonObject("evidence")
                            .getAsJsonObject("streamContext")
                            .get("opaqueId")
                            .getAsString());
        } finally {
            hub.close();
        }
    }

    @Test
    void successfulConditionEvidenceCannotBePublishedAfterItsPlayerContextChanges() {
        PlayerStateStream stream = stream();
        AtomicReference<EventHub> owner = new AtomicReference<>();
        EventHub hub = hub(stream, () -> assertNoHubMonitor(owner.get()));
        owner.set(hub);
        try {
            hub.publish(pose(stream, FIRST, 1));
            hub.publish(event("screen.changed"));
            JsonObject pose = hub.evaluateCondition(poseCondition());
            JsonObject ordinaryCondition = new JsonObject();
            ordinaryCondition.addProperty("eventType", "screen.changed");
            JsonObject ordinary = hub.evaluateCondition(ordinaryCondition);
            assertTrue(pose.get("passed").getAsBoolean());
            assertTrue(ordinary.get("passed").getAsBoolean());
            assertDoesNotThrow(() -> hub.validateConditionEvidence(pose));
            stream.setContext(SECOND);
            var changed =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> hub.validateConditionEvidence(pose));
            assertEquals("EVENT_CONTEXT_CHANGED", changed.code());
            assertDoesNotThrow(() -> hub.validateConditionEvidence(ordinary));
        } finally {
            hub.close();
        }
    }

    @Test
    void authorizationSupplierAndCancellationNeverRunUnderHubMonitors() {
        AtomicReference<EventHub> owner = new AtomicReference<>();
        AtomicReference<Object> subscription = new AtomicReference<>();
        AtomicInteger started = new AtomicInteger(), cancelled = new AtomicInteger();
        CompletableFuture<JsonObject> source =
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        assertNoHubMonitor(owner.get());
                        assertFalse(Thread.holdsLock(subscription.get()));
                        cancelled.incrementAndGet();
                        return super.cancel(interrupt);
                    }
                };
        EventHub hub =
                new EventHub(
                        "test",
                        () -> {
                            assertNoHubMonitor(owner.get());
                            started.incrementAndGet();
                            return source;
                        },
                        () -> assertNoHubMonitor(owner.get()));
        owner.set(hub);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "");
            subscription.set(subscription(hub, channel));
            hub.publish(event("screen.changed"));
            channel.runPendingTasks();
            read(channel);
            hub.evaluateCondition(new JsonObject());
            hub.accept(channel, "{\"type\":\"event.resync\"}");
            hub.accept(channel, "{\"type\":\"event.resync\"}");
            channel.runPendingTasks();
            assertEquals("RESYNC_BUSY", read(channel).get("error").getAsString());
            assertEquals(1, started.get());
            hub.unregister(channel);
            assertEquals(1, cancelled.get());
            assertTrue(source.isCancelled());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void closingWhileTheResyncSupplierStartsDoesNotBlockAndCancelsItsLateFuture() throws Exception {
        CountDownLatch started = new CountDownLatch(1),
                proceed = new CountDownLatch(1),
                removed = new CountDownLatch(1);
        CompletableFuture<JsonObject> source = new CompletableFuture<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger starts = new AtomicInteger();
        EventHub hub =
                new EventHub(
                        "test",
                        () -> {
                            starts.incrementAndGet();
                            started.countDown();
                            try {
                                if (!proceed.await(3, TimeUnit.SECONDS))
                                    throw new AssertionError("supplier did not resume");
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(interrupted);
                            }
                            return source;
                        });
        EmbeddedChannel channel = new EmbeddedChannel();
        register(hub, channel, "");
        Thread requester =
                new Thread(
                        () -> {
                            try {
                                hub.accept(channel, "{\"type\":\"event.resync\"}");
                            } catch (Throwable error) {
                                failure.set(error);
                            }
                        });
        Thread closer =
                new Thread(
                        () -> {
                            hub.unregister(channel);
                            removed.countDown();
                        });
        try {
            requester.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            hub.accept(channel, "{\"type\":\"event.resync\"}");
            channel.runPendingTasks();
            assertEquals("RESYNC_BUSY", read(channel).get("error").getAsString());
            assertEquals(1, starts.get());
            closer.start();
            assertTrue(
                    removed.await(1, TimeUnit.SECONDS),
                    "Closing must not wait on a supplier holding the subscription monitor");
        } finally {
            proceed.countDown();
            requester.join(4000);
            closer.join(4000);
            hub.close();
            channel.runPendingTasks();
            channel.finishAndReleaseAll();
        }
        assertFalse(requester.isAlive());
        assertFalse(closer.isAlive());
        assertNull(failure.get());
        assertTrue(source.isCancelled());
    }

    @Test
    void queuedResyncSnapshotMustPassTheFinalContextGateBeforeClearingItsGap() {
        PlayerStateStream stream = stream();
        CompletableFuture<JsonObject> source = new CompletableFuture<>();
        EventHub hub =
                new EventHub(
                        "test",
                        () -> source,
                        () -> {},
                        event ->
                                "event.resync.snapshot".equals(event.get("type").getAsString())
                                        ? stream.current(event.getAsJsonObject("snapshot"))
                                        : stream.current(event));
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "?type=world.player.state");
            JsonObject old = pose(stream, FIRST, 1);
            hub.publish(old);
            channel.runPendingTasks();
            read(channel);
            hub.accept(channel, "{\"type\":\"event.resync\"}");
            source.complete(old);
            stream.setContext(SECOND);
            channel.runPendingTasks();
            assertGap(read(channel), 1);
            assertNull(channel.readOutbound(), "The old nested snapshot must never be sent");
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void closedHubCannotRepopulateItsRingOrReactivatePoseInterest() {
        PlayerStateStream stream = stream();
        EventHub hub = hub(stream, () -> {});
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            register(hub, channel, "?type=world.player.state");
            hub.close();
            assertFalse(hub.wantsPlayerState());
            assertNull(hub.publish(pose(stream, FIRST, 1)));
            assertEquals(0, hub.currentSequence());
            assertThrows(
                    IllegalStateException.class,
                    () -> hub.register(channel, new QueryStringDecoder("/v0/events")));
            assertFalse(hub.evaluateCondition(poseCondition()).get("passed").getAsBoolean());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    private static void require(AtomicBoolean allowed) {
        if (!allowed.get()) throw new IllegalStateException("read authority revoked");
    }

    private static EventHub hub(PlayerStateStream stream, Runnable authorize) {
        return new EventHub(
                "test",
                () -> CompletableFuture.completedFuture(new JsonObject()),
                authorize,
                stream::current);
    }

    private static PlayerStateStream stream() {
        PlayerStateStream stream = new PlayerStateStream();
        stream.setContext(FIRST);
        return stream;
    }

    private static JsonObject pose(
            PlayerStateStream stream, PlayerStateStream.Context context, long tick) {
        return stream.sample(
                        context,
                        new PlayerStateStream.Sample(
                                tick, 1000 + tick, 1.25, 64, -2.5, 90, 0, true, 0, 0, 0),
                        () -> {})
                .orElseThrow();
    }

    private static JsonObject poseCondition() {
        JsonObject condition = new JsonObject();
        condition.addProperty("eventType", PlayerStateStream.EVENT_TYPE);
        return condition;
    }

    private static JsonObject event(String type) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        return event;
    }

    private static void register(EventHub hub, EmbeddedChannel channel, String query) {
        hub.register(channel, new QueryStringDecoder("/v0/events" + query));
        channel.runPendingTasks();
        assertEquals("event.hello", read(channel).get("type").getAsString());
    }

    private static void assertGap(JsonObject gap, long sequence) {
        assertEquals("event.gap", gap.get("type").getAsString());
        assertEquals(sequence, gap.get("fromSequence").getAsLong());
        assertEquals(sequence, gap.get("toSequence").getAsLong());
        assertTrue(gap.get("fullResyncRequired").getAsBoolean());
    }

    private static void assertNoHubMonitor(EventHub hub) {
        if (hub == null) return;
        try {
            var ring = EventHub.class.getDeclaredField("ring");
            ring.setAccessible(true);
            assertFalse(
                    Thread.holdsLock(ring.get(hub)), "authorization cannot hold the ring monitor");
            var subscriptions = EventHub.class.getDeclaredField("subscriptions");
            subscriptions.setAccessible(true);
            for (Object subscription : ((Map<?, ?>) subscriptions.get(hub)).values())
                assertFalse(
                        Thread.holdsLock(subscription),
                        "external callbacks cannot hold a subscription monitor");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Object subscription(EventHub hub, Channel channel) {
        try {
            var subscriptions = EventHub.class.getDeclaredField("subscriptions");
            subscriptions.setAccessible(true);
            return ((Map<?, ?>) subscriptions.get(hub)).get(channel);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static JsonObject read(EmbeddedChannel channel) {
        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        try {
            return JsonParser.parseString(frame.text()).getAsJsonObject();
        } finally {
            frame.release();
        }
    }
}
