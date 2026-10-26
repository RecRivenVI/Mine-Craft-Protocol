package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeepBlockEntityPagesTest {
    @Test
    void denseNativeMapKeysRemainReachableAfterMapOrderChanges() {
        DeepBlockEntityPages pages = new DeepBlockEntityPages();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 300; i++) keys.add(i + ",24,8");
        var a = pages.select(this, this, "world/player/anchor/selector", null, keys, 128);
        Collections.reverse(keys);
        var b =
                pages.select(
                        this,
                        this,
                        "world/player/anchor/selector",
                        a.metadata().get("nextCursor").getAsString(),
                        keys,
                        128);
        var c =
                pages.select(
                        this,
                        this,
                        "world/player/anchor/selector",
                        b.metadata().get("nextCursor").getAsString(),
                        keys,
                        128);
        List<String> combined = new ArrayList<>(a.keys());
        combined.addAll(b.keys());
        combined.addAll(c.keys());
        assertEquals(keys.size(), combined.size());
        assertEquals(keys.size(), combined.stream().distinct().count());
        assertFalse(c.metadata().get("hasMore").getAsBoolean());
        assertFalse(c.metadata().has("nextCursor"));
    }

    @Test
    void worldSelectorExpiryAndCloseCannotReuseContinuation() {
        AtomicLong now = new AtomicLong(1000);
        DeepBlockEntityPages pages = new DeepBlockEntityPages(now::get);
        List<String> keys = List.of("0,24,8", "1,24,8");
        String cursor =
                pages.select(this, this, "world/player/anchor/selector", null, keys, 1)
                        .metadata()
                        .get("nextCursor")
                        .getAsString();
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> pages.select(this, this, "different", cursor, keys, 1));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () ->
                        pages.select(
                                new Object(),
                                this,
                                "world/player/anchor/selector",
                                cursor,
                                keys,
                                1));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () ->
                        pages.select(
                                this,
                                new Object(),
                                "world/player/anchor/selector",
                                cursor,
                                keys,
                                1));
        now.addAndGet(60000);
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> pages.select(this, this, "world/player/anchor/selector", cursor, keys, 1));
        String next =
                pages.select(this, this, "current", null, keys, 1)
                        .metadata()
                        .get("nextCursor")
                        .getAsString();
        pages.clear();
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> pages.select(this, this, "current", next, keys, 1));
    }

    @Test
    void disappearingPriorKeyDoesNotTurnSeekIntoAnIndexSkip() {
        DeepBlockEntityPages pages = new DeepBlockEntityPages();
        String cursor =
                pages.select(this, this, "live", null, List.of("a", "b", "c"), 1)
                        .metadata()
                        .get("nextCursor")
                        .getAsString();
        assertEquals(
                List.of("b", "c"),
                pages.select(this, this, "live", cursor, List.of("b", "c"), 128).keys());
        assertThrows(
                ProtocolState.ProtocolException.class,
                () ->
                        pages.select(
                                this, this, "live", null, Collections.nCopies(8193, "entry"), 128));
    }
}
