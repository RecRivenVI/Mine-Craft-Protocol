package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiRenderedTextTest {
    @Test
    void expiresWithoutFreshNativeFramesAndRejectsResizeOrClockMismatch() {
        var sample = new UiRenderedText("Rendered", "static", 10, 640, 360);
        assertTrue(sample.current(10, 640, 360));
        assertTrue(sample.current(1_000_000_010L, 640, 360));
        assertFalse(sample.current(1_000_000_011L, 640, 360));
        assertFalse(sample.current(9, 640, 360));
        assertFalse(sample.current(10, 1280, 720));
    }

    @Test
    void textBudgetDoesNotSplitSupplementaryCharacters() {
        var sample =
                new UiRenderedText("a".repeat(2047) + "\uD83D\uDE00suffix", "static", 0, 640, 360);
        assertEquals(2047, sample.label().length());
    }
}
