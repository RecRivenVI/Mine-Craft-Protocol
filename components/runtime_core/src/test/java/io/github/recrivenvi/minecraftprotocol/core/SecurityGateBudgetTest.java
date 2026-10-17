package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SecurityGateBudgetTest {
    @Test
    void worldQuotaIsPrincipalWideAndRefillsWithoutLoadingOrDispatching() {
        AtomicLong clock = new AtomicLong();
        SecurityGate gate = new SecurityGate("private-test-principal", clock::get);
        EmbeddedChannel first = new EmbeddedChannel(), second = new EmbeddedChannel();
        try {
            for (int i = 0; i < 60; i++)
                gate.admit(i % 2 == 0 ? first : second, "GET", "/v0/world/block");
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> gate.admit(first, "GET", "/v0/world/block"));
            assertEquals("EXPENSIVE_OPERATION_RATE_LIMITED", error.code());
            JsonObject details = error.details(), budget = details.getAsJsonObject("rateLimit");
            assertEquals(34, details.get("retryAfterMillis").getAsLong());
            assertEquals("world", budget.get("category").getAsString());
            assertEquals("world", budget.get("operationCategory").getAsString());
            assertEquals("principal", budget.get("scope").getAsString());
            assertEquals(60, budget.get("capacity").getAsInt());
            assertEquals(0, budget.get("remaining").getAsInt());
            assertEquals(30.0, budget.get("refillPerSecond").getAsDouble());
            assertFalse(details.toString().contains("private-test-principal"));
            clock.set(TimeUnit.MILLISECONDS.toNanos(34));
            assertDoesNotThrow(() -> gate.admit(second, "GET", "/v0/world/block"));
        } finally {
            gate.remove(first);
            gate.remove(second);
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
    }

    @Test
    void pipelineRetryUsesTheActualFractionalTokenDeficit() {
        AtomicLong clock = new AtomicLong();
        SecurityGate gate = new SecurityGate("test", clock::get);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            for (int i = 0; i < 8; i++) gate.admit(channel, "POST", "/v0/pipelines");
            var rejected =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> gate.admit(channel, "POST", "/v0/pipelines"));
            assertEquals(500, rejected.details().get("retryAfterMillis").getAsLong());
            clock.set(TimeUnit.MILLISECONDS.toNanos(249));
            var later =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> gate.admit(channel, "POST", "/v0/pipelines"));
            assertEquals(251, later.details().get("retryAfterMillis").getAsLong());
            clock.set(TimeUnit.MILLISECONDS.toNanos(500));
            assertDoesNotThrow(() -> gate.admit(channel, "POST", "/v0/pipelines"));
        } finally {
            gate.remove(channel);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void categoryRejectionStillChargesEarlierRequestBuckets() {
        SecurityGate gate = new SecurityGate("test", () -> 0L);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            for (int i = 0; i < 8; i++) gate.admit(channel, "POST", "/v0/pipelines");
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> gate.admit(channel, "POST", "/v0/pipelines"));
            for (int i = 0; i < 111; i++) gate.admit(channel, "GET", "/v0/session");
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> gate.admit(channel, "GET", "/v0/session"));
            assertEquals("RATE_LIMITED", error.code());
            JsonObject budget = error.details().getAsJsonObject("rateLimit");
            assertEquals("requests", budget.get("category").getAsString());
            assertEquals("ordinary", budget.get("operationCategory").getAsString());
            assertEquals("connection", budget.get("scope").getAsString());
            assertEquals(120, budget.get("capacity").getAsInt());
            assertEquals(17, error.details().get("retryAfterMillis").getAsLong());
        } finally {
            gate.remove(channel);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void budgetErrorDetailsAreDefensiveSnapshots() {
        JsonObject original = new JsonObject();
        original.addProperty("retryAfterMillis", 34);
        var error = new ProtocolState.ProtocolException("RATE_LIMITED", 429, "limited", original);
        original.addProperty("retryAfterMillis", 999);
        error.details().addProperty("retryAfterMillis", 1);
        assertEquals(34, error.details().get("retryAfterMillis").getAsInt());
    }
}
