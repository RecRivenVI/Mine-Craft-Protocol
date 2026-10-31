package io.github.recrivenvi.minecraftprotocol.packaging;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.extensions.modernui.nativebridge.ModernBridge;
import io.github.recrivenvi.minecraftprotocol.probe.extension.LogicalInput;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FrameworkInputTest {
    public static final class VisibleView {
        public boolean isShown() {
            return true;
        }

        public boolean isEnabled() {
            return true;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void actualOwnerRejectsReplacedTargetRevisionBeforeReadingNativeLayout() throws Exception {
        var field = ModernBridge.class.getDeclaredField("IDS");
        field.setAccessible(true);
        var ids = (java.util.Map<Object, Long>) field.get(null);
        var view = new VisibleView();
        ids.put(view, 123L);
        try {
            var method =
                    ModernBridge.class.getDeclaredMethod(
                            "targetCurrent", LogicalInput.Target.class);
            method.setAccessible(true);
            var old =
                    new LogicalInput.Target(
                            "modern_ui:visible_views",
                            "view:123",
                            "replaced-version",
                            1,
                            2,
                            20,
                            30,
                            480,
                            270,
                            1920,
                            1080,
                            false);
            assertEquals(false, method.invoke(null, old));
        } finally {
            ids.remove(view);
        }
    }

    @Test
    void nativeFrameworkPollingCannotBorrowAgentHeldState() {
        var state =
                new LogicalInput.Snapshot(
                        "runtime",
                        3,
                        2,
                        1,
                        LogicalInput.Kind.BUTTON,
                        Set.of(340),
                        Set.of(0, 1),
                        4,
                        5,
                        false);
        ModernBridge.presence(
                () ->
                        new io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext
                                .ControlState("TAKEOVER", "session", 3, false));
        try {
            assertTrue(ModernBridge.rejectUnownedInput());
            assertEquals(0, ModernBridge.mouseButton(1, 0));
            try (var binding =
                    new LogicalInput.Binding(
                            state,
                            () -> true,
                            () -> true,
                            () -> true,
                            future -> fail("No event should be deferred"))) {
                assertFalse(ModernBridge.rejectUnownedInput());
                assertEquals(1, ModernBridge.mouseButton(1, 0));
                assertEquals(1, ModernBridge.mouseButton(1, 1));
                assertEquals(0, ModernBridge.mouseButton(1, 2));
            }
            assertTrue(ModernBridge.rejectUnownedInput());
        } finally {
            ModernBridge.presence(
                    () ->
                            new io.github.recrivenvi.minecraftprotocol.probe.extension
                                    .PackageContext.ControlState("READ", "session", 4, false));
        }
        assertFalse(ModernBridge.rejectUnownedInput());
    }

    public static final class ReleasedEvent {
        int recycled;

        public void recycle() {
            recycled++;
        }
    }

    @Test
    void releaseOnlyCallbacksCannotCreateAnotherDeliveryWhileResetIsDraining() {
        var pending = new AtomicInteger();
        var released =
                new LogicalInput.Snapshot(
                        "runtime",
                        9,
                        3,
                        1,
                        LogicalInput.Kind.BUTTON,
                        Set.of(),
                        Set.of(),
                        1,
                        2,
                        true);
        var first = new ReleasedEvent();
        var second = new ReleasedEvent();
        try (var binding =
                new LogicalInput.Binding(
                        released,
                        () -> true,
                        () -> true,
                        () -> true,
                        future -> pending.incrementAndGet())) {
            ModernBridge.queue(new Object(), first);
            ModernBridge.queue(new Object(), second);
            assertEquals(0, pending.get());
        }
        assertEquals(1, first.recycled);
        assertEquals(1, second.recycled);
        assertNull(LogicalInput.current());
    }
}
