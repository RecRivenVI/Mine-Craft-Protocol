package io.github.recrivenvi.minecraftprotocol.core.extension;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources;
import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources.Lifetime;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NativeLifetimeContextsTest {
    @Test
    void unavailableCleanupBindingIsReportedAndNeverClosedOnTheWrongThread() {
        var resources = new ExtensionResources("runtime", Duration.ofSeconds(1));
        var scope =
                resources.open(
                        new ExtensionResources.Owner("runtime", "package", Lifetime.WORLD, "gone"),
                        () -> true);
        var wrongCleanup = new AtomicInteger();
        var thread =
                new ExtensionResources.ThreadOwner(
                        "server_thread",
                        Runnable::run,
                        () -> false,
                        () -> {
                            throw new IllegalStateException("Original server gone");
                        });
        assertThrows(
                IllegalStateException.class,
                () -> scope.own("native", thread, wrongCleanup::incrementAndGet));
        assertEquals(0, wrongCleanup.get());
        var report = resources.cleanupReports().get(0).join();
        assertFalse(report.released());
        assertEquals("owner_binding_failed", report.reason());
        assertEquals("unconfirmed", report.resources().get(0).status());
        resources.close();
    }

    @Test
    void nativeIdentityAndLayoutRetireOnlyTheirOwnLifetimesBeforeCleanup() {
        var contexts = new NativeLifetimeContexts();
        Object world = new Object(), connection = new Object(), screen = new Object();
        contexts.observe(world, connection, screen, "layout:1");
        var before = contexts.snapshot();
        contexts.observe(world, connection, screen, "layout:1");
        assertEquals(before, contexts.snapshot());
        var resources = new ExtensionResources("runtime", Duration.ofSeconds(1));
        String id = before.identity(Lifetime.SCREEN);
        var scope =
                resources.open(
                        new ExtensionResources.Owner("runtime", "package", Lifetime.SCREEN, id),
                        () -> id.equals(contexts.snapshot().identity(Lifetime.SCREEN)));
        var cleanup = new AtomicInteger();
        scope.own(
                "view",
                new ExtensionResources.ThreadOwner("actual", Runnable::run, () -> true),
                cleanup::incrementAndGet);
        contexts.observe(world, connection, screen, "layout:2");
        assertEquals(before.identity(Lifetime.WORLD), contexts.snapshot().identity(Lifetime.WORLD));
        assertEquals(
                before.identity(Lifetime.CONNECTION),
                contexts.snapshot().identity(Lifetime.CONNECTION));
        assertThrows(IllegalStateException.class, () -> scope.publish(() -> "late"));
        resources.expireInvalidGrants();
        assertEquals(1, cleanup.get());
        contexts.observe(new Object(), connection, null, "gameplay");
        assertNotEquals(
                before.identity(Lifetime.WORLD), contexts.snapshot().identity(Lifetime.WORLD));
        assertEquals(
                before.identity(Lifetime.CONNECTION),
                contexts.snapshot().identity(Lifetime.CONNECTION));
        assertNull(contexts.snapshot().identity(Lifetime.SCREEN));
        contexts.observe(null, null, null, "closed");
        assertTrue(contexts.snapshot().identities().isEmpty());
        resources.close();
    }

    @Test
    void cleanupBindsToTheOriginalNativeOwnerInsteadOfTheNextWorld() {
        var owner = new AtomicInteger(1);
        var calledOn = new AtomicInteger();
        var resources = new ExtensionResources("runtime", Duration.ofSeconds(1));
        var scope =
                resources.open(
                        new ExtensionResources.Owner("runtime", "package", Lifetime.WORLD, "one"),
                        () -> true);
        var dynamic =
                new ExtensionResources.ThreadOwner(
                        "server",
                        task -> {
                            calledOn.set(owner.get());
                            task.run();
                        },
                        () -> true,
                        () -> {
                            int captured = owner.get();
                            return new ExtensionResources.ThreadOwner(
                                    "server",
                                    task -> {
                                        calledOn.set(captured);
                                        task.run();
                                    },
                                    () -> true);
                        });
        scope.own("native", dynamic, () -> {});
        owner.set(2);
        resources.close();
        assertEquals(1, calledOn.get());
    }
}
