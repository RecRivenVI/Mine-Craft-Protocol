package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class AgentTaskLifecycleTest {
    @Test
    void activitySurvivesShortConnectionsButHasBoundedGrace() {
        var clock = new AtomicLong(1000);
        var task = new AgentTaskLifecycle(clock::get);
        assertFalse(task.present());
        task.activity();
        clock.addAndGet(14999);
        assertTrue(task.present());
        clock.incrementAndGet();
        assertFalse(task.present());
    }

    @Test
    void explicitTaskKeepsWaitingPresenceAndCannotOutliveItsBudget() {
        var clock = new AtomicLong(1000);
        var task = new AgentTaskLifecycle(clock::get);
        var started = task.begin(10000, 15000);
        clock.addAndGet(9000);
        assertTrue(task.present());
        assertEquals(started.generation(), task.heartbeat(started.taskId(), 60000).generation());
        assertEquals(16000, task.snapshot().expiresAtMillis());
        clock.set(16000);
        assertFalse(task.present());
        assertEquals("EXPIRED", task.expire().status());
        task.activity();
        assertFalse(task.present());
        assertThrows(
                AgentControlSession.ModeException.class,
                () -> task.heartbeat(started.taskId(), 1000));
    }

    @Test
    void endAndInterruptAreTerminalAndOldIdentityCannotEndANewTask() {
        var task = new AgentTaskLifecycle();
        var first = task.begin(60000, 300000);
        assertThrows(AgentControlSession.ModeException.class, () -> task.begin(60000, 300000));
        assertEquals("INTERRUPTED", task.interrupt().status());
        assertEquals("INTERRUPTED", task.end(first.taskId(), "task_ended").status());
        var second = task.begin(60000, 300000);
        assertNotEquals(first.taskId(), second.taskId());
        assertThrows(
                AgentControlSession.ModeException.class,
                () -> task.end(first.taskId(), "task_ended"));
        assertEquals("ENDED", task.end(second.taskId(), "task_ended").status());
        long generation = task.snapshot().generation();
        task.end(second.taskId(), "task_ended");
        assertEquals(generation, task.snapshot().generation());
        task.close();
        assertFalse(task.present());
        assertThrows(AgentControlSession.ModeException.class, () -> task.begin(60000, 300000));
    }

    @Test
    void invalidBudgetsFailBeforeStarting() {
        var task = new AgentTaskLifecycle();
        assertThrows(AgentControlSession.ModeException.class, () -> task.begin(999, 300000));
        assertThrows(AgentControlSession.ModeException.class, () -> task.begin(60000, 300001));
        assertEquals("IDLE", task.snapshot().status());
    }
}
