package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.recrivenvi.minecraftprotocol.safety.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class ControlInputFoundationTest {
    @Test void ambientRoutedScopeIsNotCallbackPermission() {
        var key = AgentInputContext.Event.key(1, 256, 0, 1, 0);
        AgentInputContext.routed(() -> assertFalse(AgentInputContext.consume(key)));
        AgentInputContext.dispatch(key, () -> {
            assertTrue(AgentInputContext.consume(key));
            assertFalse(AgentInputContext.consume(key), "re-entry cannot reuse a consumed ticket");
        });
        assertFalse(AgentInputContext.isAgentRouted());
        assertFalse(AgentInputContext.consume(key));
    }
    @Test void nativeReentryCannotStealEvenAnUnconsumedOuterTicket() {
        var key = AgentInputContext.Event.key(1, 87, 17, 1, 0);
        Runnable nativeWork = AgentInputContext.nativeTask(() -> {
            assertFalse(AgentInputContext.isAgentRouted());
            assertFalse(AgentInputContext.consume(key));
        });
        AgentInputContext.dispatch(key, () -> {
            nativeWork.run();
            assertTrue(AgentInputContext.consume(key));
            AgentInputContext.dispatch(key, () -> assertTrue(AgentInputContext.consume(key)));
            assertFalse(AgentInputContext.consume(key));
        });
        nativeWork.run();
        assertFalse(AgentInputContext.isAgentRouted());
    }
    @Test void fingerprintAndExceptionsDoNotLeakOriginAcrossTasks() throws Exception {
        var move = AgentInputContext.Event.point(AgentInputContext.Kind.MOVE, 2, 10, 20);
        assertThrows(IllegalStateException.class, () -> AgentInputContext.dispatch(move, () -> {
            assertFalse(AgentInputContext.consume(AgentInputContext.Event.point(AgentInputContext.Kind.MOVE, 3, 10, 20)));
            throw new IllegalStateException();
        }));
        try (var worker = Executors.newSingleThreadExecutor()) {
            assertFalse(worker.submit(AgentInputContext::isAgentRouted).get(1, TimeUnit.SECONDS));
            worker.submit(AgentInputContext.nativeTask(() -> assertFalse(AgentInputContext.consume(move)))).get(1, TimeUnit.SECONDS);
        }
    }
    @Test void exclusivePolicyCoversAllNativeKindsButNotOsActions() {
        for (var kind : AgentInputContext.Kind.values()) {
            assertEquals(ExclusiveInputPolicy.Decision.SUPPRESS, ExclusiveInputPolicy.decide(true, false, kind, 65, 1));
            assertEquals(ExclusiveInputPolicy.Decision.PASS, ExclusiveInputPolicy.decide(false, false, kind, 65, 1));
            assertEquals(ExclusiveInputPolicy.Decision.PASS, ExclusiveInputPolicy.decide(true, true, kind, 65, 1));
        }
        assertEquals(ExclusiveInputPolicy.Decision.MANUAL_REVOKE, ExclusiveInputPolicy.decide(true, false, AgentInputContext.Kind.KEY, 256, 1));
        assertEquals(ExclusiveInputPolicy.Decision.PASS, ExclusiveInputPolicy.decide(true, true, AgentInputContext.Kind.KEY, 256, 1));
    }
    @Test void queueOwnsCleanupUntilDrainAndCancelsWaitingWithoutTouchingOwner() {
        try (InputSequenceQueue queue = new InputSequenceQueue()) {
            var active = new CompletableFuture<Void>();
            var waiting = new CompletableFuture<Void>();
            var third = new CompletableFuture<Void>();
            List<String> started = new ArrayList<>();
            AtomicInteger activeCancel = new AtomicInteger();
            queue.submit(() -> started.add("first"), activeCancel::incrementAndGet, active);
            queue.submit(() -> started.add("cancelled"), () -> waiting.complete(null), waiting);
            waiting.complete(null);
            queue.submit(() -> started.add("third"), () -> third.complete(null), third);
            assertEquals(List.of("first"), started);
            assertEquals(0, activeCancel.get());
            active.complete(null);
            assertEquals(List.of("first", "third"), started);
            third.complete(null);
            assertFalse(queue.busy());
        }
    }
    @Test void inputQueueBoundAndFailedCleanupFailClosed() {
        InputSequenceQueue queue = new InputSequenceQueue();
        List<CompletableFuture<Void>> drains = new ArrayList<>();
        AtomicInteger started = new AtomicInteger();
        for (int i = 0; i < InputSequenceQueue.CAPACITY; i++) {
            var drain = new CompletableFuture<Void>(); drains.add(drain);
            queue.submit(started::incrementAndGet, () -> drain.complete(null), drain);
        }
        assertThrows(RejectedExecutionException.class, () -> queue.submit(() -> {}, () -> {}, new CompletableFuture<>()));
        assertEquals(1, started.get());
        drains.get(0).completeExceptionally(new IllegalStateException("cleanup failed"));
        assertEquals(1, started.get());
        assertThrows(RejectedExecutionException.class, () -> queue.submit(() -> {}, () -> {}, new CompletableFuture<>()));
        queue.close();
        assertEquals(0, queue.pendingCount());
    }
    @Test void queueCloseCancelsAllAndNeverStartsAnotherOwner() {
        InputSequenceQueue queue = new InputSequenceQueue();
        AtomicInteger starts = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            var drain = new CompletableFuture<Void>();
            queue.submit(starts::incrementAndGet, () -> drain.complete(null), drain);
        }
        queue.close();
        assertEquals(1, starts.get()); assertFalse(queue.busy());
        assertThrows(RejectedExecutionException.class, () -> queue.submit(() -> {}, () -> {}, new CompletableFuture<>()));
    }
    @Test void pointerEasingIsDeterministicBoundedAndEndpointExact() {
        double previous = 0;
        for (int i = 0; i <= 120; i++) {
            double x = AgentPointer.interpolate(0, 100, i / 120.0);
            assertEquals(x, AgentPointer.interpolate(0, 100, i / 120.0));
            assertTrue(x >= previous && x <= 100); previous = x;
        }
        assertEquals(0, AgentPointer.ease(-1)); assertEquals(1, AgentPointer.ease(2));
        AgentPointer pointer = new AgentPointer();
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 10, 20);
        assertEquals(10, pointer.x()); assertEquals(20, pointer.y());
        pointer.move(30, 40); pointer.plane(AgentPointer.Plane.GAMEPLAY_RELATIVE, 0, 0);
        assertEquals(30, pointer.x());
        assertThrows(IllegalArgumentException.class, () -> pointer.move(Double.NaN, 1));
    }
    @Test void threePresenceModesShareFadeWithoutOwningAuthority() {
        ControlChrome chrome = new ControlChrome();
        long time = 1;
        chrome.update(AgentControlSession.Mode.READ, false, false, time);
        chrome.update(AgentControlSession.Mode.READ, true, false, time += ControlChrome.FADE_NANOS);
        float read = chrome.edgeAlpha(); assertEquals(0, chrome.pointerAlpha());
        chrome.update(AgentControlSession.Mode.OPERATE, true, false, time += ControlChrome.FADE_NANOS);
        float operate = chrome.edgeAlpha();
        chrome.update(AgentControlSession.Mode.TAKEOVER, true, true, time += ControlChrome.FADE_NANOS);
        assertTrue(chrome.edgeAlpha() > operate && operate > read);
        assertEquals(1, chrome.pointerAlpha()); assertEquals(ControlChrome.MESSAGE, chrome.message());
        chrome.update(AgentControlSession.Mode.READ, true, false, time += ControlChrome.FADE_NANOS);
        assertEquals(1, chrome.alpha()); assertEquals(0, chrome.pointerAlpha());
        chrome.update(AgentControlSession.Mode.READ, false, false, time += ControlChrome.FADE_NANOS);
        assertEquals(0, chrome.alpha());
    }
    @Test void asynchronousEvidenceNeverContainsPixelChromeOrPointerAcrossModesAndResize() throws Exception {
        try (FrameCaptureQueue queue = new FrameCaptureQueue()) {
            ControlChrome chrome = new ControlChrome();
            for (int frame = 0; frame < 60; frame++) {
                int width = 80 + frame, height = 60 + frame;
                int[] pixels = new int[width * height]; java.util.Arrays.fill(pixels, 0xFF152535);
                byte[] content = new byte[pixels.length * 4];
                var expected = java.nio.ByteBuffer.wrap(content); for (int pixel : pixels) expected.putInt(pixel);
                var copied = new java.util.concurrent.atomic.AtomicReference<byte[]>();
                var capture = queue.request(); var delayed = new CompletableFuture<byte[]>();
                queue.beforeOperatorChrome(() -> {
                    var snapshot = java.nio.ByteBuffer.allocate(pixels.length * 4);
                    for (int pixel : pixels) snapshot.putInt(pixel);
                    copied.set(snapshot.array()); return delayed;
                });
                chrome.update(AgentControlSession.Mode.values()[frame % 3], true, true, 1L + frame * 20_000_000L);
                ControlChrome.Rectangles draw = (x0,y0,x1,y1,color) -> {
                    for (int y = Math.max(0,y0); y < Math.min(height,y1); y++)
                        for (int x = Math.max(0,x0); x < Math.min(width,x1); x++) pixels[y * width + x] = color;
                };
                ControlChrome.edges(draw, width, height, chrome.edgeAlpha());
                ControlChrome.panel(draw, 20, 8, 40, 20, chrome.alpha());
                ControlChrome.pointer(draw, 30, 40, chrome.pointerAlpha());
                delayed.complete(copied.get());
                assertArrayEquals(content, capture.get(1, TimeUnit.SECONDS));
            }
            assertEquals(60, queue.readbacks());
        }
    }
}
