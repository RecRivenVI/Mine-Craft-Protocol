package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AgentControlSessionTest {
    @Test
    void transitionsShareOnePresenceAndManualLatch() {
        List<AgentControlSession.Snapshot> transitions = new ArrayList<>();
        AgentControlSession session = new AgentControlSession(transitions::add);

        assertEquals(AgentControlSession.State.IDLE, session.snapshot().state());
        session.acquire();
        assertEquals(AgentControlSession.State.AGENT_CONTROLLED, session.snapshot().state());
        assertFalse(session.snapshot().reconsentRequired());

        session.manuallyRevoke();
        AgentControlSession.Snapshot revoked = session.snapshot();
        assertEquals(AgentControlSession.State.MANUALLY_REVOKED, revoked.state());
        assertTrue(revoked.reconsentRequired());
        assertEquals("用户手动结束控制", revoked.message());

        session.acquire();
        assertEquals(AgentControlSession.State.AGENT_CONTROLLED, session.snapshot().state());
        assertFalse(session.snapshot().reconsentRequired());
        assertEquals(3, transitions.size());
    }

    @Test
    void routedInputMarkerDoesNotLeakAcrossNestedDispatch() {
        assertFalse(AgentInputContext.isAgentRouted());
        AgentInputContext.routed(() -> {
            assertTrue(AgentInputContext.isAgentRouted());
            AgentInputContext.routed(() -> assertTrue(AgentInputContext.isAgentRouted()));
            assertTrue(AgentInputContext.isAgentRouted());
        });
        assertFalse(AgentInputContext.isAgentRouted());
    }
}
