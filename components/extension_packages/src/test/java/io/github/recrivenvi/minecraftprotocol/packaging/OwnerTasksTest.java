package io.github.recrivenvi.minecraftprotocol.packaging;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.extensions.create.support.OwnerTasks;
import io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OwnerTasksTest {
    @Test
    void deferredNativeWorkUsesActualOwnerAndCannotPublishAfterProcessClose() throws Exception {
        var actual = new AtomicReference<Thread>();
        var owner =
                Executors.newSingleThreadExecutor(task -> new Thread(task, "native-owner-test"));
        var resources = new ExtensionResources("test", Duration.ofSeconds(1));
        var scope =
                resources.open(
                        new ExtensionResources.Owner(
                                "test", "sample", ExtensionResources.Lifetime.PROCESS, "process"),
                        () -> true);
        PackageContext context =
                (PackageContext)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {PackageContext.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "packageId" -> "sample";
                                            case "lifetimeContexts" ->
                                                    io.github.recrivenvi.minecraftprotocol.probe
                                                            .extension.LifetimeContexts.empty();
                                            case "processResources" -> scope;
                                            case "threadOwners" ->
                                                    Map.of(
                                                            "server_thread",
                                                            new ExtensionResources.ThreadOwner(
                                                                    "server_thread",
                                                                    owner,
                                                                    () ->
                                                                            actual.get()
                                                                                    == Thread
                                                                                            .currentThread()));
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        try {
            owner.submit(() -> actual.set(Thread.currentThread())).get();
            var tasks = new OwnerTasks(context, "read");
            assertEquals(
                    "native-owner-test",
                    tasks.submit(
                                    "server_thread",
                                    System.currentTimeMillis() + 5000,
                                    () -> Thread.currentThread().getName())
                            .get(2, TimeUnit.SECONDS));
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            owner.submit(
                    () -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException stopped) {
                            Thread.currentThread().interrupt();
                        }
                    });
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            var late =
                    tasks.submit(
                            "server_thread",
                            System.currentTimeMillis() + 5000,
                            () -> {
                                fail("Retired native work must not run");
                                return "late";
                            });
            resources.close();
            assertTrue(late.isCancelled());
            release.countDown();
            owner.submit(() -> {}).get(1, TimeUnit.SECONDS);
            assertThrows(CancellationException.class, late::join);
        } finally {
            resources.close();
            owner.shutdownNow();
        }
    }
}
