package io.github.recrivenvi.minecraftprotocol.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Component-local tests: no Minecraft classes, Loader, native window or storage IO. */
final class ComponentBoundaryTest {
    @Test void startsReadWithoutInventingAuthorization() {
        var session = new AgentControlSession();
        assertEquals(AgentControlSession.Mode.READ, session.snapshot().mode());
        assertThrows(AgentControlSession.ModeException.class,
                () -> session.select(AgentControlSession.Mode.TAKEOVER, "not_a_lease"));
    }

    @Test void manualLatchSurvivesOperateUntilExplicitAcquire() {
        var session = new AgentControlSession();
        session.acquire();
        session.manuallyRevoke();
        session.select(AgentControlSession.Mode.OPERATE, "explicit_operate");
        assertTrue(session.snapshot().reconsentRequired());
        session.acquire();
        assertFalse(session.snapshot().reconsentRequired());
    }

    @Test void runtimeSessionsDoNotAlias() {
        assertNotEquals(new AgentControlSession().snapshot().controlSessionId(),
                new AgentControlSession().snapshot().controlSessionId());
    }

    @Test void callbackTicketIsOneUseAndDoesNotLeak() {
        var event = AgentInputContext.Event.key(1, 256, 0, 1, 0);
        AgentInputContext.dispatch(event, () -> {
            assertTrue(AgentInputContext.consume(event));
            assertFalse(AgentInputContext.consume(event));
        });
        assertFalse(AgentInputContext.consume(event));
    }

    @Test void sharedBytecodeStillSupportsTheJava17Target() throws Exception {
        try (var input = AgentControlSession.class.getResourceAsStream("AgentControlSession.class")) {
            assertNotNull(input);
            byte[] header = input.readNBytes(8);
            assertEquals(61, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
}
