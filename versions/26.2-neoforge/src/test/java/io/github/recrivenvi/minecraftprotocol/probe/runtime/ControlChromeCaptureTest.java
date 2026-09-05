package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.recrivenvi.minecraftprotocol.safety.ControlChrome;
import io.github.recrivenvi.minecraftprotocol.safety.FrameCaptureQueue;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ControlChromeCaptureTest {
    @Test void fadeChangesOnlyVisualAlphaAndOuterEdgeFadesToClearCentre() {
        ControlChrome chrome = new ControlChrome();
        assertEquals(0F, chrome.update(false, 1));
        assertEquals(0.5F, chrome.update(true, 1 + ControlChrome.FADE_NANOS / 2), 0.01);
        assertEquals(1F, chrome.update(true, 1 + ControlChrome.FADE_NANOS));
        assertEquals(0.5F, chrome.update(false, 1 + ControlChrome.FADE_NANOS * 3 / 2), 0.01);
        assertEquals(0F, chrome.update(false, 1 + ControlChrome.FADE_NANOS * 2));
        int[][] pixels = new int[240][320];
        ControlChrome.edges((x0,y0,x1,y1,c) -> {
            for(int y=y0;y<y1;y++) for(int x=x0;x<x1;x++) pixels[y][x]=c;
        },320,240,1F);
        assertEquals(235,pixels[0][0] >>> 24);
        assertEquals(pixels[0][0],pixels[239][319]);
        assertTrue((pixels[0][160] >>> 24) > (pixels[8][160] >>> 24));
        assertEquals(0,pixels[16][160]);
        assertEquals(0,pixels[120][160]);
    }

    @Test void concurrentReadbacksDuringFadeAlwaysCopyContentBeforeChrome() throws Exception {
        try (FrameCaptureQueue queue = new FrameCaptureQueue()) {
            ControlChrome fade = new ControlChrome();
            AtomicInteger surface = new AtomicInteger();
            List<CompletableFuture<byte[]>> submitted = new ArrayList<>();
            for (int frame=0;frame<60;frame++) {
                List<CompletableFuture<byte[]>> readbacks = new ArrayList<>();
                submitted.add(queue.request()); submitted.add(queue.request());
                surface.set(7); // freshly rendered ordinary UI, including Toasts
                queue.beforeOperatorChrome(() -> {
                    byte[] immutableCopy = {(byte)surface.get()};
                    CompletableFuture<byte[]> delayed = new CompletableFuture<>();
                    readbacks.add(delayed);
                    delayed.thenAccept(bytes -> assertArrayEquals(immutableCopy, bytes));
                    return delayed;
                });
                fade.update(frame % 8 < 4, 1L + frame * 20_000_000L);
                surface.set(99); // Operator pass, visible only on the host window
                readbacks.forEach(future -> future.complete(new byte[]{7}));
            }
            for(var capture:submitted) assertArrayEquals(new byte[]{7},capture.get(1,TimeUnit.SECONDS));
            assertEquals(120,queue.readbacks());
            assertEquals(0,queue.pendingCount());
        }
    }

    @Test void captureOverloadCancellationAndShutdownAreBounded() {
        FrameCaptureQueue queue = new FrameCaptureQueue();
        List<CompletableFuture<byte[]>> requests = new ArrayList<>();
        for(int i=0;i<FrameCaptureQueue.CAPACITY;i++) requests.add(queue.request());
        assertTrue(queue.request().isCompletedExceptionally());
        requests.get(0).cancel(false);
        assertEquals(FrameCaptureQueue.CAPACITY-1,queue.pendingCount());
        queue.beforeOperatorChrome(CompletableFuture::new);
        assertEquals(2,queue.inFlightCount());
        queue.close();
        assertTrue(requests.stream().allMatch(CompletableFuture::isDone));
        assertTrue(queue.request().isCompletedExceptionally());
    }

    @Test void cancelledStorageHandoffCannotStartOrPublishLateWork() {
        CompletableFuture<Integer> owner = new CompletableFuture<>();
        AtomicInteger io = new AtomicInteger();
        var response = CancellableWork.compose(owner, value -> {
            io.incrementAndGet(); return CompletableFuture.completedFuture(value);
        });
        response.cancel(false);
        owner.complete(1);
        assertEquals(0,io.get());
        CompletableFuture<Integer> child = new CompletableFuture<>();
        var active = CancellableWork.compose(CompletableFuture.completedFuture(1), value -> child);
        active.cancel(false);
        assertTrue(child.isCancelled());
        assertFalse(child.complete(2));
    }
}
