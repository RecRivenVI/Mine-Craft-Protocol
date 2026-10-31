package io.github.recrivenvi.minecraftprotocol.core.extension;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.probe.extension.LogicalInput;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LogicalInputRouterTest {
    @Test
    void deferredTargetKeepsFractionalGeometryAndNativeFocusRequirement() {
        var router = new LogicalInputRouter();
        var guard =
                com.google.gson.JsonParser.parseString(
                                """
                {"textFocusRequired":true,"node":{"capabilitySource":"modern_ui:visible_views",
                "semanticState":{"extensionTargetKey":"view:4","extensionTargetRevision":"editor"},
                "x":12.125,"y":8.75,"width":30.5,"height":9.25}}
                """)
                        .getAsJsonObject();
        router.guard(guard, 480, 270, 1920, 1080);
        assertTrue(router.target().textFocusRequired());
        assertEquals(12.125, router.target().x());
        assertEquals(9.25, router.target().height());
        assertEquals("view:4", router.target().key());
        guard.addProperty("textFocusRequired", false);
        router.guard(guard, 480, 270, 1920, 1080);
        assertFalse(router.target().textFocusRequired());
        router.guard(null, 480, 270, 1920, 1080);
        assertNull(router.target());
    }

    private static LogicalInput.Snapshot state(boolean cleanup) {
        return new LogicalInput.Snapshot(
                "runtime",
                7,
                3,
                1,
                LogicalInput.Kind.BUTTON,
                cleanup ? Set.of() : Set.of(340),
                cleanup ? Set.of() : Set.of(0, 1),
                10.25,
                20.75,
                cleanup);
    }

    @Test
    void snapshotIsScopedAndNativeReentryCannotBorrowIt() {
        var router = new LogicalInputRouter();
        var buttons = new java.util.HashSet<Integer>(Set.of(0, 1));
        var snapshot =
                new LogicalInput.Snapshot(
                        "r", 1, 2, 3, LogicalInput.Kind.MOVE, Set.of(), buttons, 0, 0, false);
        buttons.clear();
        router.collect(
                        () -> {
                            router.dispatch(
                                    snapshot,
                                    () -> true,
                                    AgentInputContext.Event.button(1, 0, 1, 0),
                                    () -> {
                                        assertEquals(
                                                Set.of(0, 1), LogicalInput.current().buttons());
                                        AgentInputContext.nativeTask(
                                                        () -> assertNull(LogicalInput.current()))
                                                .run();
                                        assertNotNull(LogicalInput.current());
                                    });
                            return 1;
                        })
                .join();
        assertNull(LogicalInput.current());
        assertThrows(IllegalStateException.class, LogicalInput::defer);
    }

    @Test
    void asyncFrameworkConsumptionKeepsNativeFutureOpenAndIsOneUse() throws Exception {
        var router = new LogicalInputRouter();
        var delivery = new AtomicReference<LogicalInput.Delivery>();
        var result =
                router.collect(
                        () -> {
                            router.dispatch(
                                    state(false),
                                    () -> true,
                                    AgentInputContext.Event.button(1, 0, 1, 0),
                                    () -> delivery.set(LogicalInput.defer()));
                            return "native";
                        });
        assertFalse(result.isDone());
        assertNull(LogicalInput.current());
        Thread owner =
                new Thread(
                        () ->
                                delivery.get()
                                        .deliver(
                                                () -> true,
                                                () -> {
                                                    assertTrue(
                                                            LogicalInput.current().buttonDown(0));
                                                    assertTrue(
                                                            LogicalInput.current().buttonDown(1));
                                                    assertTrue(LogicalInput.current().keyDown(340));
                                                    AgentInputContext.nativeTask(
                                                                    () ->
                                                                            assertNull(
                                                                                    LogicalInput
                                                                                            .current()))
                                                            .run();
                                                    assertNotNull(LogicalInput.current());
                                                }));
        owner.start();
        owner.join();
        assertEquals("native", result.join());
        assertThrows(
                IllegalStateException.class,
                () -> delivery.get().deliver(() -> true, () -> fail("duplicate")));
    }

    @Test
    void operationCancellationFencesDeferredInputEvenWhenLeaseRemainsLive() {
        var cancelled = new AtomicBoolean();
        var delivery = new AtomicReference<LogicalInput.Delivery>();
        var router = new LogicalInputRouter();
        var result =
                router.collect(
                        () -> {
                            router.dispatch(
                                    state(false),
                                    () -> true,
                                    AgentInputContext.Event.button(1, 0, 1, 0),
                                    () -> delivery.set(LogicalInput.defer()));
                            return 1;
                        },
                        () -> {
                            if (cancelled.get())
                                throw new IllegalStateException("operation_cancelled");
                        });
        cancelled.set(true);
        assertFalse(delivery.get().deliver(() -> true, () -> fail("cancelled operation arrived")));
        assertTrue(result.isCompletedExceptionally());
        assertNull(LogicalInput.current());
    }

    @Test
    void frameworkTargetReplacementHasStableProtocolErrorAndNoEvent() {
        var delivery = new AtomicReference<LogicalInput.Delivery>();
        var router = new LogicalInputRouter();
        var result =
                router.collect(
                        () -> {
                            router.dispatch(
                                    state(false),
                                    () -> true,
                                    AgentInputContext.Event.button(1, 0, 1, 0),
                                    () -> delivery.set(LogicalInput.defer()));
                            return 1;
                        });
        delivery.get().targetInvalidated("view replaced");
        var failure = assertThrows(java.util.concurrent.CompletionException.class, result::join);
        assertInstanceOf(
                io.github.recrivenvi.minecraftprotocol.core.ProtocolState.ProtocolException.class,
                failure.getCause());
        assertEquals(
                "UI_TARGET_INVALIDATED",
                ((io.github.recrivenvi.minecraftprotocol.core.ProtocolState.ProtocolException)
                                failure.getCause())
                        .code());
        assertFalse(delivery.get().deliver(() -> true, () -> fail("stale target arrived")));
    }

    @Test
    void revokedDeferredInputCannotArriveButExplicitCleanupRemainsReleaseOnly() {
        var valid = new AtomicBoolean(true);
        var delivery = new AtomicReference<LogicalInput.Delivery>();
        var router = new LogicalInputRouter();
        var result =
                router.collect(
                        () -> {
                            router.dispatch(
                                    state(false),
                                    valid::get,
                                    AgentInputContext.Event.button(1, 0, 1, 0),
                                    () -> delivery.set(LogicalInput.defer()));
                            return 1;
                        });
        valid.set(false);
        assertFalse(delivery.get().deliver(() -> true, () -> fail("late event")));
        assertTrue(result.isCompletedExceptionally());
        router.collectCleanup(
                        () -> {
                            assertTrue(router.cleanupOnly());
                            router.dispatch(
                                    state(true),
                                    () -> true,
                                    AgentInputContext.Event.button(1, 0, 0, 0),
                                    () -> {
                                        assertTrue(LogicalInput.current().cleanupOnly());
                                        assertFalse(LogicalInput.current().buttonDown(0));
                                    });
                            return 1;
                        })
                .join();
        assertFalse(router.cleanupOnly());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LogicalInput.Snapshot(
                                "r",
                                1,
                                2,
                                3,
                                LogicalInput.Kind.KEY,
                                Set.of(87),
                                Set.of(),
                                0,
                                0,
                                true));
    }
}
