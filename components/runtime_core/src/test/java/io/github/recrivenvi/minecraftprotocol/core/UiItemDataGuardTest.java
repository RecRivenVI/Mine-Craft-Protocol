package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UiItemDataGuardTest {
    private UiItemDataGuard.Content capture(UiItemDataGuard guard, String text) {
        return guard.capture(
                new UiItemDataGuard.Budget(),
                bytes -> bytes.writeBytes(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void fullPayloadDetectsNativeHashCollisionsAndPublicTokensDoNotRevealThePrivateDigest() {
        UiItemDataGuard guard = new UiItemDataGuard(new byte[32]);
        assertEquals("Aa".hashCode(), "BB".hashCode());
        var first = capture(guard, "Aa");
        var changed = capture(guard, "BB");
        assertTrue(first.available());
        assertNotEquals(first.fingerprint(), changed.fingerprint());
        assertEquals(first, capture(guard, "Aa"));
        String revision = guard.revision("menu:1:slot:1", first);
        assertNotEquals(first.fingerprint(), revision);
        assertNotEquals(revision, guard.revision("menu:1:slot:2", first));
        assertNotEquals(revision, new UiItemDataGuard().revision("menu:1:slot:1", first));
        assertEquals(64, revision.length());
    }

    @Test
    void byteBudgetsFailClosedWithoutFurtherEncodingAndReleaseBuffers() {
        UiItemDataGuard guard = new UiItemDataGuard();
        var budget = new UiItemDataGuard.Budget(8);
        var buffer = new AtomicReference<ByteBuf>();
        var result =
                guard.capture(
                        budget,
                        bytes -> {
                            buffer.set(bytes);
                            bytes.writeBytes(new byte[9]);
                        });
        assertFalse(result.available());
        assertEquals(0, budget.remaining());
        assertEquals(0, buffer.get().refCnt());
        var calls = new AtomicInteger();
        assertFalse(guard.capture(budget, bytes -> calls.incrementAndGet()).available());
        assertEquals(0, calls.get());
        assertEquals("", guard.revision("slot", result));

        var frame = new UiItemDataGuard.Budget();
        var oversized =
                guard.capture(frame, bytes -> bytes.writeZero(UiItemDataGuard.MAX_ITEM_BYTES + 1));
        assertFalse(oversized.available());
        assertEquals(
                UiItemDataGuard.MAX_FRAME_BYTES - UiItemDataGuard.MAX_ITEM_BYTES,
                frame.remaining());
        assertTrue(guard.capture(frame, bytes -> bytes.writeByte(1)).available());
    }

    @Test
    void successAndEncoderFailureBothAccountForBoundedFrameWork() {
        UiItemDataGuard guard = new UiItemDataGuard();
        var budget = new UiItemDataGuard.Budget(12);
        var buffer = new AtomicReference<ByteBuf>();
        assertTrue(
                guard.capture(
                                budget,
                                bytes -> {
                                    buffer.set(bytes);
                                    bytes.writeInt(4);
                                })
                        .available());
        assertEquals(8, budget.remaining());
        assertEquals(0, buffer.get().refCnt());
        assertFalse(
                guard.capture(
                                budget,
                                bytes -> {
                                    buffer.set(bytes);
                                    throw new IllegalArgumentException("unencodable");
                                })
                        .available());
        assertEquals(0, budget.remaining());
        assertEquals(0, buffer.get().refCnt());
        assertThrows(IllegalArgumentException.class, () -> new UiItemDataGuard.Budget(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UiItemDataGuard.Budget(UiItemDataGuard.MAX_FRAME_BYTES + 1));
    }
}
