package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class PlayerStateStreamTest {
    private static final PlayerStateStream.Context FIRST =
            new PlayerStateStream.Context(1, "first");
    private static final PlayerStateStream.Context SECOND =
            new PlayerStateStream.Context(2, "second");

    @Test
    void projectsOnlyTypedUserPoseWithOriginalFractionalPrecision() {
        PlayerStateStream stream = stream();
        AtomicInteger authorized = new AtomicInteger();
        JsonObject event = stream.sample(FIRST, pose(1), authorized::incrementAndGet).orElseThrow();
        assertEquals(1, authorized.get());
        assertEquals(1, stream.sampleCount());
        assertTrue(PlayerStateStream.isStateEvent(event));
        assertTrue(stream.current(event));
        assertEquals(
                Set.of(
                        "type",
                        "stateStream",
                        "streamContext",
                        "domain",
                        "category",
                        "dataCategory",
                        "authority",
                        "source",
                        "dataSource",
                        "storageAccessed",
                        "serverAuthoritative",
                        "sourceTick",
                        "sampledAtMillis",
                        "x",
                        "y",
                        "z",
                        "yaw",
                        "pitch",
                        "onGround",
                        "velocityX",
                        "velocityY",
                        "velocityZ"),
                event.keySet());
        assertEquals("USER", event.get("dataCategory").getAsString());
        assertEquals("world", event.get("category").getAsString());
        assertEquals("CLIENT_KNOWN", event.get("authority").getAsString());
        assertEquals("LIVE", event.get("dataSource").getAsString());
        assertFalse(event.get("storageAccessed").getAsBoolean());
        assertFalse(event.get("serverAuthoritative").getAsBoolean());
        assertEquals(1.125, event.get("x").getAsDouble());
        assertEquals(-2.75, event.get("z").getAsDouble());
        assertEquals(720.375, event.get("yaw").getAsDouble());
        assertEquals(.03125, event.get("velocityX").getAsDouble());
        assertTrue(event.get("onGround").getAsBoolean());
        assertEquals(1, event.get("sourceTick").getAsLong());
        assertEquals(1001, event.get("sampledAtMillis").getAsLong());
        assertFalse(event.has("sequence"), "Only EventHub assigns its delivery sequence");
    }

    @Test
    void repeatedAndReverseTicksDoNotCreateFramesOrConsumeAuthorization() {
        PlayerStateStream stream = stream();
        AtomicInteger authorized = new AtomicInteger();
        assertTrue(stream.sample(FIRST, pose(2), authorized::incrementAndGet).isPresent());
        stream.setContext(FIRST);
        assertTrue(stream.sample(FIRST, pose(2), authorized::incrementAndGet).isEmpty());
        assertTrue(stream.sample(FIRST, pose(1), authorized::incrementAndGet).isEmpty());
        assertEquals(1, stream.sampleCount());
        assertEquals(1, authorized.get());
        assertTrue(stream.sample(FIRST, pose(3), authorized::incrementAndGet).isPresent());
        assertEquals(2, stream.sampleCount());
    }

    @Test
    void failedAuthorizationDoesNotAdvanceTheSuccessfulTickOrCounter() {
        PlayerStateStream stream = stream();
        assertThrows(
                SecurityException.class,
                () ->
                        stream.sample(
                                FIRST,
                                pose(1),
                                () -> {
                                    throw new SecurityException("read revoked");
                                }));
        assertEquals(0, stream.sampleCount());
        assertTrue(stream.sample(FIRST, pose(1), () -> {}).isPresent());
        assertEquals(1, stream.sampleCount());
    }

    @Test
    void replacementInvalidatesOldFramesAndLateCaptureWithoutASecondRing() {
        PlayerStateStream stream = stream();
        JsonObject old = stream.sample(FIRST, pose(100), () -> {}).orElseThrow();
        stream.setContext(SECOND);
        assertFalse(stream.current(old));
        assertTrue(
                stream.sample(
                                FIRST,
                                pose(101),
                                () -> {
                                    throw new AssertionError("old context must not read");
                                })
                        .isEmpty());
        assertTrue(stream.sample(SECOND, pose(0), () -> {}).isPresent());
        assertEquals(2, stream.sampleCount());
        assertThrows(IllegalArgumentException.class, () -> stream.setContext(FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> stream.setContext(new PlayerStateStream.Context(2, "different")));
    }

    @Test
    void missingWorldPreventsFramesAndCannotReactivateAnOldGeneration() {
        PlayerStateStream stream = stream();
        JsonObject event = stream.sample(FIRST, pose(1), () -> {}).orElseThrow();
        stream.setContext(null);
        stream.setContext(null);
        assertFalse(stream.current(event));
        assertTrue(stream.sample(FIRST, pose(2), () -> {}).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> stream.setContext(FIRST));
        stream.setContext(SECOND);
        assertTrue(stream.sample(SECOND, pose(0), () -> {}).isPresent());
    }

    @Test
    void publicationContextIsRecheckedAfterLockFreeAuthorization() {
        PlayerStateStream stream = stream();
        assertTrue(
                stream.sample(
                                FIRST,
                                pose(1),
                                () -> {
                                    assertFalse(Thread.holdsLock(stream));
                                    stream.setContext(SECOND);
                                })
                        .isEmpty());
        assertEquals(0, stream.sampleCount());
        assertTrue(stream.sample(SECOND, pose(1), () -> {}).isPresent());
    }

    @Test
    void concurrentCloseWhileAuthorizedCaptureIsPendingCannotProduceLateFrame() throws Exception {
        PlayerStateStream stream = stream();
        CountDownLatch authorized = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> created = new AtomicReference<>();
        Thread work =
                new Thread(
                        () -> {
                            try {
                                created.set(
                                        stream.sample(
                                                        FIRST,
                                                        pose(1),
                                                        () -> {
                                                            authorized.countDown();
                                                            try {
                                                                if (!finish.await(
                                                                        2, TimeUnit.SECONDS))
                                                                    throw new AssertionError(
                                                                            "authorization blocked");
                                                            } catch (
                                                                    InterruptedException
                                                                            interrupted) {
                                                                Thread.currentThread().interrupt();
                                                                throw new AssertionError(
                                                                        interrupted);
                                                            }
                                                        })
                                                .isPresent());
                            } catch (Throwable error) {
                                failure.set(error);
                            }
                        });
        work.start();
        try {
            assertTrue(authorized.await(2, TimeUnit.SECONDS));
            stream.close();
        } finally {
            finish.countDown();
            work.join(3000);
        }
        assertFalse(work.isAlive());
        assertEquals(null, failure.get());
        assertEquals(Boolean.FALSE, created.get());
        assertEquals(0, stream.sampleCount());
        assertThrows(IllegalStateException.class, () -> stream.setContext(SECOND));
        assertTrue(stream.sample(FIRST, pose(2), () -> {}).isEmpty());
    }

    @Test
    void currentOnlyFiltersStateFramesAndRejectsMalformedContextMarkers() {
        PlayerStateStream stream = stream();
        JsonObject valid = stream.sample(FIRST, pose(1), () -> {}).orElseThrow();
        JsonObject ordinary = new JsonObject();
        ordinary.addProperty("type", "event.pipeline.terminal");
        assertTrue(stream.current(ordinary));
        for (String generation : new String[] {"1.25", "9223372036854775808", "-1"}) {
            JsonObject wrong = valid.deepCopy();
            wrong.getAsJsonObject("streamContext")
                    .add("generation", com.google.gson.JsonParser.parseString(generation));
            assertFalse(stream.current(wrong));
        }
        JsonObject stringGeneration = valid.deepCopy();
        stringGeneration.getAsJsonObject("streamContext").addProperty("generation", "1");
        assertFalse(stream.current(stringGeneration));
        JsonObject wrongMarker = valid.deepCopy();
        wrongMarker.addProperty("stateStream", "unknown");
        assertFalse(stream.current(wrongMarker));
        JsonObject missing = valid.deepCopy();
        missing.remove("streamContext");
        assertFalse(stream.current(missing));
        stream.close();
        assertFalse(stream.current(valid));
        assertTrue(stream.current(ordinary));
    }

    @Test
    void sourceTickOrdersFramesIndependentlyOfWallClockCorrections() {
        PlayerStateStream stream = stream();
        stream.sample(FIRST, pose(1), () -> {}).orElseThrow();
        PlayerStateStream.Sample earlierClock =
                new PlayerStateStream.Sample(2, 10, 0, 0, 0, 0, 0, false, 0, 0, 0);
        assertEquals(
                10,
                stream.sample(FIRST, earlierClock, () -> {})
                        .orElseThrow()
                        .get("sampledAtMillis")
                        .getAsLong());
    }

    @Test
    void rejectsInvalidTypedNativeValuesInsteadOfInventingZeroOrTruncating() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerStateStream.Context(-1, "negative"));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStateStream.Context(1, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerStateStream.Context(1, "a".repeat(129)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerStateStream.Sample(-1, 0, 0, 0, 0, 0, 0, false, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerStateStream.Sample(1, -1, 0, 0, 0, 0, 0, false, 0, 0, 0));
        for (double invalid :
                new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PlayerStateStream.Sample(1, 1, invalid, 0, 0, 0, 0, false, 0, 0, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PlayerStateStream.Sample(1, 1, 0, 0, 0, invalid, 0, false, 0, 0, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PlayerStateStream.Sample(1, 1, 0, 0, 0, 0, 0, false, 0, invalid, 0));
        }
    }

    private static PlayerStateStream stream() {
        PlayerStateStream result = new PlayerStateStream();
        result.setContext(FIRST);
        return result;
    }

    private static PlayerStateStream.Sample pose(long tick) {
        return new PlayerStateStream.Sample(
                tick,
                1000 + tick,
                1.125,
                64.5,
                -2.75,
                720.375,
                -12.125,
                true,
                .03125,
                -.0625,
                .125);
    }
}
