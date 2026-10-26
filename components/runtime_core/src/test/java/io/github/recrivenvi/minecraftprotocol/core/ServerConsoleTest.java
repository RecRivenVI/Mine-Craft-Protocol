package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ServerConsoleTest {
    @Test
    void pagesAreBoundedAndNeverInventMissingOutput() {
        var console = new ServerConsole(new Host());
        for (int i = 0; i < 300; i++)
            console.publish("INFO", "test", "Server thread", "中文\n" + i, null);
        var page = console.read(0, 128);
        assertTrue(page.get("gap").getAsBoolean());
        assertEquals(128, page.getAsJsonArray("records").size());
        assertTrue(page.get("hasMore").getAsBoolean());
        var tail = console.read(page.get("nextSequence").getAsLong(), 128);
        assertEquals(128, tail.getAsJsonArray("records").size());
        assertFalse(tail.get("hasMore").getAsBoolean());
        assertFalse(tail.get("storageAccessed").getAsBoolean());
        assertThrows(ProtocolState.ProtocolException.class, () -> console.read(301, 1));
    }

    @Test
    void queuedCancelNeverDispatchesAndShutdownStopsAdmittedWork() {
        var host = new Host();
        var console = new ServerConsole(host);
        var session = new AgentControlSession(ignored -> {});
        session.select(AgentControlSession.Mode.OPERATE, "test");
        var work = session.beginOperate();
        var cancelled = console.execute("list", "cancel", work, () -> {});
        cancelled.cancel(false);
        host.queue.remove().run();
        assertEquals(0, host.calls.get());
        work.close();
        var next = session.beginOperate();
        var closed = console.execute("list", "closed", next, () -> {});
        console.close();
        host.queue.remove().run();
        assertTrue(closed.isCompletedExceptionally());
        assertEquals(0, host.calls.get());
        next.close();
    }

    @Test
    void authorizationIsRecheckedAtExecutionAndUnsupportedRootsAreRejectedBeforeQueue() {
        var host = new Host();
        var console = new ServerConsole(host);
        var session = new AgentControlSession(ignored -> {});
        session.select(AgentControlSession.Mode.OPERATE, "test");
        try (var work = session.beginOperate()) {
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> console.execute("execute run stop", "bad", work, () -> {}));
            assertTrue(host.queue.isEmpty());
            var denied =
                    console.execute(
                            "list",
                            "denied",
                            work,
                            () -> {
                                throw new IllegalStateException("revoked");
                            });
            host.queue.remove().run();
            assertTrue(denied.isCompletedExceptionally());
            assertEquals(0, host.calls.get());
        }
    }

    private static final class Host implements ServerConsole.Host {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger();

        public void schedule(Runnable action) {
            queue.add(action);
        }

        public void dispatch(
                String command,
                java.util.function.Consumer<String> output,
                java.util.function.Consumer<JsonObject> result) {
            calls.incrementAndGet();
            output.accept("native");
        }
    }
}
