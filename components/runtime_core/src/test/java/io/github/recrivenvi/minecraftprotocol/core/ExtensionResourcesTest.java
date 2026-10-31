package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources;
import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources.Lifetime;
import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources.Owner;
import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources.ThreadOwner;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExtensionResourcesTest {
    private final ThreadOwner inline =
            new ThreadOwner("detached_provider_worker", Runnable::run, () -> true);

    private static Owner owner(String name, Lifetime lifetime) {
        return new Owner("runtime-a", name, lifetime, "context-1");
    }

    @Test
    void twoPackagesFailIndependentlyAndTheOtherResourcesStillClose() throws Exception {
        var resources = new ExtensionResources("runtime-a", Duration.ofSeconds(1));
        var calls = new ArrayList<String>();
        for (String name : new String[] {"package_a", "package_b"}) {
            var scope = resources.open(owner(name, Lifetime.DEBUG_GRANT), () -> true);
            scope.own("normal", inline, () -> calls.add(name));
            scope.own(
                    "failing",
                    inline,
                    () -> {
                        throw new IllegalStateException("injected");
                    });
        }
        var reports = resources.invalidate(Lifetime.DEBUG_GRANT, "context-1", "disarm").get();
        assertEquals(2, calls.size());
        assertEquals(2, reports.size());
        assertTrue(reports.stream().noneMatch(ExtensionResources.Report::released));
        assertTrue(reports.stream().allMatch(report -> report.resources().size() == 2));
        assertEquals(0, resources.activeScopes());
        resources.close();
        assertEquals(2, calls.size());
    }

    @Test
    void invalidationIsImmediateWhileCleanupWaitsForTheActualOwner() throws Exception {
        var queue = new ArrayList<Runnable>();
        var resources = new ExtensionResources("runtime-a", Duration.ofSeconds(2));
        var scope = resources.open(owner("package_a", Lifetime.INPUT_LEASE), () -> true);
        AtomicInteger calls = new AtomicInteger();
        var nativeOwner = new ThreadOwner("client_thread", queue::add, () -> true);
        scope.own("held", nativeOwner, calls::incrementAndGet);
        var report = resources.invalidate(scope.owner(), "human_manual_revocation");
        assertFalse(report.isDone());
        assertThrows(IllegalStateException.class, () -> scope.publish(() -> "late"));
        assertEquals(0, calls.get());
        queue.remove(0).run();
        assertTrue(report.get().released());
        resources.invalidate(scope.owner(), "duplicate").get();
        assertEquals(1, calls.get());
        assertThrows(
                IllegalStateException.class,
                () -> scope.own("late", nativeOwner, calls::incrementAndGet));
        queue.remove(0).run();
        assertEquals(2, calls.get());
        assertEquals("late_registration", resources.cleanupReports().get(1).get().reason());
    }

    @Test
    void leaseEndDoesNotEndIndependentDebugButGrantLossDoes() throws Exception {
        var resources = new ExtensionResources("runtime-a", Duration.ofSeconds(1));
        var valid = new AtomicBoolean(true);
        var debug = resources.open(owner("package_a", Lifetime.DEBUG_GRANT), valid::get);
        var input = resources.open(owner("package_a", Lifetime.INPUT_LEASE), () -> true);
        resources.invalidate(input.owner(), "lease_lost").get();
        assertEquals("allowed", debug.publish(() -> "allowed"));
        valid.set(false);
        assertThrows(IllegalStateException.class, () -> debug.publish(() -> "late"));
        resources.expireInvalidGrants();
        assertEquals(0, resources.activeScopes());
    }

    @Test
    void rejectedAndWrongOwnerCleanupCannotHideFailuresOrSkipOtherPackages() throws Exception {
        var resources = new ExtensionResources("runtime-a", Duration.ofMillis(50));
        var scope = resources.open(owner("package_a", Lifetime.WORLD), () -> true);
        AtomicInteger calls = new AtomicInteger();
        scope.own(
                "rejected",
                new ThreadOwner(
                        "server_thread",
                        task -> {
                            throw new RejectedExecutionException();
                        },
                        () -> true),
                calls::incrementAndGet);
        scope.own(
                "wrong",
                new ThreadOwner("render_thread", Runnable::run, () -> false),
                calls::incrementAndGet);
        scope.own("normal", inline, calls::incrementAndGet);
        var report = resources.invalidate(scope.owner(), "world_disconnected").get();
        assertEquals(1, calls.get());
        assertFalse(report.released());
        assertEquals("dispatch_rejected", report.resources().get(2).status());
        assertEquals("failed", report.resources().get(1).status());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        resources.open(
                                new Owner("other-runtime", "package_a", Lifetime.WORLD, "world-1"),
                                () -> true));
    }

    @Test
    void aDeadExecutorIsUnconfirmedRatherThanReportedReleased() throws Exception {
        var resources = new ExtensionResources("runtime-a", Duration.ofMillis(10));
        var scope = resources.open(owner("package_a", Lifetime.SCREEN), () -> true);
        scope.own(
                "unrun",
                new ThreadOwner("framework_thread", task -> {}, () -> false),
                () -> fail("Must not run"));
        var report =
                resources.invalidate(scope.owner(), "screen_replaced").get(1, TimeUnit.SECONDS);
        assertFalse(report.released());
        assertEquals("unconfirmed", report.resources().get(0).status());
    }

    @Test
    void completedOwnerHistoryIsBoundedWithoutReusingCapturedScopes() {
        var resources = new ExtensionResources("runtime-a", Duration.ofSeconds(1));
        for (int i = 0; i < 300; i++) {
            var scope =
                    resources.open(
                            new Owner("runtime-a", "package_a", Lifetime.SCREEN, "screen-" + i),
                            () -> true);
            resources.invalidate(scope.owner(), "screen_replaced").join();
            assertThrows(IllegalStateException.class, scope::requireActive);
        }
        assertEquals(ExtensionResources.CLEANUP_HISTORY, resources.cleanupReports().size());
    }
}
