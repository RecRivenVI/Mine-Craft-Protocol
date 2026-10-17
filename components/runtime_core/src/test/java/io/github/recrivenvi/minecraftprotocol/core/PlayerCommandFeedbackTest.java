package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlayerCommandFeedbackTest {
    private final AtomicLong clock = new AtomicLong();
    private final PlayerCommandFeedback sampler = new PlayerCommandFeedback(clock::get);

    private PlayerCommandFeedback.Message message(String text) {
        return new PlayerCommandFeedback.Message(new Object(), text);
    }

    @Test
    void excludesBaselineKeepsIdenticalNewTextAndDeduplicatesOnlyByIdentity() {
        var old = message("same");
        var first = message("same");
        var second = message("same");
        sampler.begin(List.of(old), true);
        var list = List.of(second, first, old);
        assertEquals(2, sampler.poll(list, true).getAsJsonArray("messages").size());
        assertEquals(2, sampler.poll(list, true).getAsJsonArray("messages").size());
        clock.set(TimeUnit.MILLISECONDS.toNanos(500));
        var result = sampler.poll(list, true);
        assertTrue(result.get("complete").getAsBoolean());
        assertEquals("temporal_not_guaranteed", result.get("association").getAsString());
        assertFalse(result.has("effectConfirmed"));
    }

    @Test
    void deadlineDoesNotIncludeLateMessagesAndClearPreventsReuse() {
        sampler.begin(List.of(), true);
        clock.set(TimeUnit.MILLISECONDS.toNanos(501));
        var result = sampler.poll(List.of(message("too late")), true);
        assertTrue(result.get("complete").getAsBoolean());
        assertTrue(result.getAsJsonArray("messages").isEmpty());
        sampler.clear();
        assertThrows(ProtocolState.ProtocolException.class, () -> sampler.poll(List.of(), true));
    }

    @Test
    void boundsCountAndUnicodeLengthAndDoesNotInterpretUntrustedText() {
        sampler.begin(List.of(), true);
        var incoming = new ArrayList<PlayerCommandFeedback.Message>();
        for (int i = 0; i < 20; i++) incoming.add(message("😀".repeat(600)));
        var result = sampler.poll(incoming, true);
        assertTrue(result.get("truncated").getAsBoolean());
        assertEquals(16, result.getAsJsonArray("messages").size());
        String text =
                result.getAsJsonArray("messages")
                        .get(0)
                        .getAsJsonObject()
                        .get("text")
                        .getAsString();
        assertEquals(512, text.codePointCount(0, text.length()));
        assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 1)));
    }

    @Test
    void worldChangeTerminatesTheWindowWithoutSamplingTheReplacementWorld() {
        sampler.begin(List.of(message("old history")), true);
        clock.set(TimeUnit.MILLISECONDS.toNanos(120));
        sampler.poll(List.of(message("before transition")), true);
        var result = sampler.finishContextChanged();
        assertEquals("client_context_changed", result.get("terminationReason").getAsString());
        assertTrue(result.get("complete").getAsBoolean());
        assertEquals(120, result.get("observedMillis").getAsInt());
        assertEquals(1, result.getAsJsonArray("messages").size());
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> sampler.poll(List.of(message("new world")), true));
    }

    @Test
    void hiddenAndFilteredChatCannotLeakThroughTheFeedbackWindow() {
        sampler.begin(List.of(), false);
        assertTrue(
                sampler.poll(List.of(message("secret")), true)
                        .getAsJsonArray("messages")
                        .isEmpty());
        sampler.begin(List.of(), true);
        assertTrue(sampler.poll(List.of(message(null)), true).getAsJsonArray("messages").isEmpty());
        sampler.poll(List.of(message("previously visible")), true);
        assertTrue(sampler.poll(List.of(), false).getAsJsonArray("messages").isEmpty());
    }
}
