package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class EventHubTest {
    @Test
    void typedFilterDeliversOnlyMatchingEvents() {
        EventHub hub = hub();
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            hub.register(channel, new QueryStringDecoder("/v0/events?type=screen.changed"));
            channel.runPendingTasks();
            assertEquals("event.hello", read(channel).get("type").getAsString());
            hub.publish(event("entity.changed"));
            hub.publish(event("screen.changed"));
            channel.runPendingTasks();
            assertEquals("screen.changed", read(channel).get("type").getAsString());
            assertNull(channel.readOutbound());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void resumeBeforeRingStartProducesExplicitGapAndResync() {
        EventHub hub = hub();
        for (int index = 0; index < EventHub.RING_CAPACITY + 8; index++) hub.publish(event("tick"));
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            hub.register(channel, new QueryStringDecoder("/v0/events?resumeFromSequence=0"));
            channel.runPendingTasks();
            assertEquals("event.hello", read(channel).get("type").getAsString());
            JsonObject gap = read(channel);
            assertEquals("event.gap", gap.get("type").getAsString());
            assertTrue(gap.get("fullResyncRequired").getAsBoolean());
            hub.accept(channel, "{\"type\":\"event.resync\"}");
            channel.runPendingTasks();
            JsonObject resync = read(channel);
            assertEquals("event.resync.snapshot", resync.get("type").getAsString());
            assertFalse(resync.get("fullResyncRequired").getAsBoolean());
            assertNotNull(resync.getAsJsonObject("snapshot").get("session"));
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void stalledConsumerQueueIsBoundedAndReportsGap() {
        EventHub hub = hub();
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            hub.register(channel, new QueryStringDecoder("/v0/events"));
            channel.runPendingTasks();
            read(channel);
            for (int index = 0; index < EventHub.CLIENT_QUEUE_CAPACITY + 32; index++) {
                hub.publish(event("input.changed"));
            }
            channel.runPendingTasks();
            JsonObject gap = read(channel);
            assertEquals("event.gap", gap.get("type").getAsString());
            assertTrue(gap.get("fullResyncRequired").getAsBoolean());
        } finally {
            hub.close();
            channel.finishAndReleaseAll();
        }
    }

    private static EventHub hub() {
        return new EventHub("test-target", () -> {
            JsonObject snapshot = new JsonObject();
            snapshot.add("session", new JsonObject());
            snapshot.add("capabilities", new JsonObject());
            return CompletableFuture.completedFuture(snapshot);
        });
    }

    private static JsonObject event(String type) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        return event;
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
