package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiToastTextTest {
    @Test
    void singleLineShowsBothNativeLines() {
        assertArrayEquals(
                new String[] {"Header\nTitle", "header_and_title"},
                UiToastText.advancement("Header", "Title", 1, 0));
    }

    @Test
    void multilineDoesNotPublishTitleBeforeNativeBranchSwitch() {
        for (long time : new long[] {0, 1199, 1400, 1498})
            assertArrayEquals(
                    new String[] {"Header", "header"},
                    UiToastText.advancement("Header", "Secret\nTitle", 2, time));
        assertArrayEquals(
                new String[] {"Secret\nTitle", "title"},
                UiToastText.advancement("Header", "Secret\nTitle", 2, 1800));
    }

    @Test
    void nativeLegacyAlphaFloorRetainsOnlyTheCorrectPhaseText() {
        assertArrayEquals(
                new String[] {"Header", "header"},
                UiToastText.advancement("Header", "Title", 2, 1499));
        assertArrayEquals(
                new String[] {"Title", "title"},
                UiToastText.advancement("Header", "Title", 2, 1500));
    }
}
