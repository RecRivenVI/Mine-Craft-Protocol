package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlayerCommandChatTest {
    private final Object world = new Object(),
            player = new Object(),
            connection = new Object(),
            screen = new Object(),
            input = new Object();

    private PlayerCommandChat.Frame frame(boolean chat, String text) {
        return new PlayerCommandChat.Frame(
                world,
                player,
                connection,
                chat ? screen : null,
                chat ? input : null,
                chat,
                text,
                chat,
                text.length(),
                "");
    }

    @Test
    void nativeDraftIsTypedAndValidatedWithoutReplacingIt() {
        var guard = new PlayerCommandChat();
        guard.prepare(frame(false, ""), "help", "gesture", 256);
        guard.beforeOpen(frame(false, ""), "help", "gesture");
        assertFalse(guard.ready(frame(false, ""), "help", "gesture"));
        assertTrue(guard.ready(frame(true, ""), "help", "gesture"));
        assertEquals("/help", guard.nextChunk(frame(true, ""), "help", "gesture"));
        String draft = "";
        for (int cp : "/help".codePoints().toArray()) {
            draft += new String(Character.toChars(cp));
            guard.acceptCharacter(frame(true, draft), "help", "gesture", cp);
        }
        guard.beforeSubmit(frame(true, draft), "help", "gesture");
        assertTrue(guard.nextChunk(frame(true, draft), "help", "gesture").isEmpty());
        guard.clear();
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> guard.beforeSubmit(frame(true, "/help"), "help", "gesture"));
    }

    @Test
    void nativeLimitAndExistingScreensFailBeforeAnyInput() {
        var guard = new PlayerCommandChat();
        assertEquals(
                "PLAYER_COMMAND_CHAT_LIMIT",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        guard.prepare(
                                                frame(false, ""), "a".repeat(256), "gesture", 256))
                        .code());
        assertEquals(
                "PLAYER_COMMAND_SCREEN_ACTIVE",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () -> guard.prepare(frame(true, "draft"), "help", "gesture", 256))
                        .code());
    }

    @Test
    void changedDraftFocusSelectionWorldAndGestureRejectWithoutSubmit() {
        var guard = new PlayerCommandChat();
        guard.prepare(frame(false, ""), "help", "gesture", 256);
        assertTrue(guard.ready(frame(true, ""), "help", "gesture"));
        var candidates =
                List.of(
                        frame(true, "external"),
                        new PlayerCommandChat.Frame(
                                world, player, connection, screen, input, true, "", false, 0, ""),
                        new PlayerCommandChat.Frame(
                                world,
                                player,
                                connection,
                                new Object(),
                                input,
                                true,
                                "",
                                true,
                                0,
                                ""),
                        new PlayerCommandChat.Frame(
                                new Object(),
                                player,
                                connection,
                                screen,
                                input,
                                true,
                                "",
                                true,
                                0,
                                ""),
                        new PlayerCommandChat.Frame(
                                world, player, new Object(), screen, input, true, "", true, 0, ""));
        for (var frame : candidates)
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> guard.beforeSubmit(frame, "help", "gesture"));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> guard.beforeSubmit(frame(true, ""), "help", "stale"));
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> guard.beforeSubmit(frame(true, ""), "help", "gesture"));
    }

    @Test
    void truncationOrResponderChangesAreNotSilentlySubmitted() {
        var guard = new PlayerCommandChat();
        guard.prepare(frame(false, ""), "help", "gesture", 256);
        guard.ready(frame(true, ""), "help", "gesture");
        assertThrows(
                ProtocolState.ProtocolException.class,
                () -> guard.acceptCharacter(frame(true, ""), "help", "gesture", '/'));
    }

    @Test
    void engineOwnsAllPhasesAndAllowsPacketOnlyAtSubmit() throws Exception {
        var phases = new ArrayList<String>();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    JsonObject result = new JsonObject();
                                    if (method.getName().equals("playerCommand")) {
                                        String phase = (String) args[1];
                                        phases.add(phase);
                                        var permit = InputDispatchContext.capture();
                                        permit.check();
                                        assertEquals("help", permit.expectedPlayerCommand());
                                        assertEquals(
                                                phase.equals("submit"),
                                                permit.playerCommandSubmissionAllowed());
                                        result.addProperty("ready", true);
                                        result.addProperty("typed", true);
                                        result.addProperty("complete", true);
                                        if (phase.equals("submit"))
                                            result =
                                                    PlayerCommandEvidence.classified(
                                                            result,
                                                            PlayerCommandPolicy.Effect.INFORMATION,
                                                            "GAME_ROUTED_RAW");
                                    }
                                    return CompletableFuture.completedFuture(result);
                                });
        try (var engine = new AutomationEngine(service)) {
            assertTrue(
                    engine.playerCommand("/help", () -> {}, java.util.function.Supplier::get)
                            .get(3, TimeUnit.SECONDS)
                            .get("accepted")
                            .getAsBoolean());
            assertEquals(List.of("prepare", "open", "ready", "type", "submit", "feedback"), phases);
        }
    }

    @Test
    void cancellationDuringFeedbackStopsTheChildAndInvalidatesTheSubmissionPermit()
            throws Exception {
        var entered = new CountDownLatch(1);
        var returnChild = new CountDownLatch(1);
        var pending = new CompletableFuture<JsonObject>();
        var permit = new AtomicReference<InputDispatchContext.Permit>();
        var cleanups = new java.util.concurrent.atomic.AtomicInteger();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("releaseAllInput"))
                                        cleanups.incrementAndGet();
                                    JsonObject result = new JsonObject();
                                    if (method.getName().equals("playerCommand")) {
                                        String phase = (String) args[1];
                                        if (phase.equals("feedback")) {
                                            permit.set(InputDispatchContext.capture());
                                            assertFalse(
                                                    permit.get().playerCommandSubmissionAllowed());
                                            entered.countDown();
                                            assertTrue(returnChild.await(3, TimeUnit.SECONDS));
                                            return pending;
                                        }
                                        result.addProperty("ready", true);
                                        result.addProperty("typed", true);
                                        if (phase.equals("submit"))
                                            result =
                                                    PlayerCommandEvidence.classified(
                                                            result,
                                                            PlayerCommandPolicy.Effect.INFORMATION,
                                                            "GAME_ROUTED_RAW");
                                    }
                                    return CompletableFuture.completedFuture(result);
                                });
        try (var engine = new AutomationEngine(service)) {
            var result = engine.playerCommand("help", () -> {}, java.util.function.Supplier::get);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            result.cancel(false);
            assertThrows(
                    java.util.concurrent.CancellationException.class, () -> permit.get().check());
            returnChild.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!pending.isCancelled() && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(pending.isCancelled());
            assertTrue(cleanups.get() > 0);
        } finally {
            returnChild.countDown();
        }
    }

    @Test
    void cancellationWhileWaitingForChatNeverTypesOrSubmits() throws Exception {
        var ready = new CountDownLatch(1);
        var returnChild = new CountDownLatch(1);
        var childCancelled = new CountDownLatch(1);
        var pending = new CompletableFuture<JsonObject>();
        pending.whenComplete((value, error) -> childCancelled.countDown());
        var phases = java.util.Collections.synchronizedList(new ArrayList<String>());
        var permit = new AtomicReference<InputDispatchContext.Permit>();
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    if (method.getReturnType() == void.class) return null;
                                    if (method.getName().equals("playerCommand")) {
                                        String phase = (String) args[1];
                                        phases.add(phase);
                                        if (phase.equals("ready")) {
                                            permit.set(InputDispatchContext.capture());
                                            ready.countDown();
                                            // Exercise cancellation before effect() can register
                                            // the returned child.
                                            assertTrue(returnChild.await(3, TimeUnit.SECONDS));
                                            return pending;
                                        }
                                    }
                                    return CompletableFuture.completedFuture(new JsonObject());
                                });
        try (var engine = new AutomationEngine(service)) {
            var result = engine.playerCommand("help", () -> {}, java.util.function.Supplier::get);
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            result.cancel(false);
            var cancelled =
                    assertThrows(
                            java.util.concurrent.CancellationException.class,
                            () -> permit.get().check());
            assertTrue(cancelled.getMessage().contains("operation_cancelled"));
            returnChild.countDown();
            assertTrue(childCancelled.await(3, TimeUnit.SECONDS));
            assertTrue(pending.isCancelled());
            assertEquals(List.of("prepare", "open", "ready"), phases);
        } finally {
            returnChild.countDown();
        }
    }
}
