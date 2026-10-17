package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CommandLeaseLockTest {
    @Test
    void nativeCommandLeaseCheckNeverTakesTheProtocolStateMonitor() throws Exception {
        try (var state = new ProtocolState(Set.of("control"), "test", ignored -> {})) {
            String lease = state.acquireLease(60000).get("leaseId").getAsString();
            var acquired = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var holder =
                    new Thread(
                            () -> {
                                synchronized (state) {
                                    acquired.countDown();
                                    try {
                                        release.await(2, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
            holder.start();
            try {
                assertTrue(acquired.await(1, TimeUnit.SECONDS));
                assertTimeout(Duration.ofMillis(250), state::requireCommandLease);
            } finally {
                release.countDown();
                holder.join(3000);
            }
            assertFalse(holder.isAlive());
            state.releaseLease(lease, "test");
            assertThrows(ProtocolState.ProtocolException.class, state::requireCommandLease);
        }
    }
}
