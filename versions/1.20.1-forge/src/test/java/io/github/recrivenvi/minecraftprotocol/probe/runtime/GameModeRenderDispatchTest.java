package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.InputDispatchContext;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandGate;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class GameModeRenderDispatchTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/probe/";
    private static final String RUNTIME = ROOT + "runtime/ForgeProbeRuntime";
    private static final String PENDING = RUNTIME + "$PendingGameModeRelease";

    @Test
    void oneActualRenderRetainsItsOriginalPermitAndCompletesOnlyAfterBothScopesClose() {
        var control = new AgentControlSession();
        try (var gate = new PlayerCommandGate(control, (id, routed) -> {}, value -> {})) {
            var accepted = control.acquire();
            Screen owner = screen();
            var permit = new Permit();
            var evidence = new JsonObject();
            evidence.addProperty("nativeRelease", true);
            var future = new CompletableFuture<JsonObject>();
            var pending =
                    new ForgeProbeRuntime.PendingGameModeRelease(
                            owner, accepted, permit, evidence, future);
            assertSame(accepted, pending.accepted);
            assertSame(permit, pending.permit);
            assertFalse(future.isDone());
            assertNull(gate.dispatch());
            assertFalse(AgentInputContext.isAgentRouted());
            AtomicBoolean returnedOutsideScope = new AtomicBoolean();
            future.thenRun(
                    () ->
                            returnedOutsideScope.set(
                                    gate.dispatch() == null && !AgentInputContext.isAgentRouted()));
            AtomicInteger draws = new AtomicInteger();
            pending.render(
                    owner,
                    control,
                    gate,
                    () -> {
                        assertFalse(future.isDone());
                        assertTrue(AgentInputContext.isAgentRouted());
                        assertNotNull(gate.dispatch());
                        assertEquals(permit.gestureId(), gate.dispatch().gestureId());
                        draws.incrementAndGet();
                    });
            assertSame(evidence, future.join());
            assertTrue(returnedOutsideScope.get());
            assertEquals(1, draws.get());
            assertTrue(permit.checks.get() >= 3);
            pending.render(owner, control, gate, draws::incrementAndGet);
            assertEquals(1, draws.get());
            assertSame(evidence, future.join());
            assertFalse(AgentInputContext.isAgentRouted());
            assertNull(gate.dispatch());
        }
    }

    @Test
    void replacingTheScreenCannotRenderOrTransferThePendingPermitToItsReplacement() {
        var control = new AgentControlSession();
        try (var gate = new PlayerCommandGate(control, (id, routed) -> {}, value -> {})) {
            var pending = pending(screen(), control.acquire(), new Permit());
            var calls = new AtomicInteger();
            pending.render(screen(), control, gate, calls::incrementAndGet);
            assertEquals("UI_TARGET_INVALIDATED", failure(pending.completion).code());
            assertEquals(0, calls.get());
            assertNull(gate.dispatch());
            assertFalse(AgentInputContext.isAgentRouted());
        }
    }

    @Test
    void physicalRevocationAndANewSessionCannotReuseTheOriginalAdmission() {
        var control = new AgentControlSession();
        try (var gate = new PlayerCommandGate(control, (id, routed) -> {}, value -> {})) {
            Screen owner = screen();
            var pending = pending(owner, control.acquire(), new Permit());
            control.manuallyRevoke();
            pending.render(owner, control, gate, () -> fail("Revoked render must not run"));
            assertEquals("USER_MANUALLY_ENDED_CONTROL", failure(pending.completion).code());
            var replacement = new AgentControlSession();
            try (var replacementGate =
                    new PlayerCommandGate(replacement, (id, routed) -> {}, value -> {})) {
                replacement.acquire();
                var foreign = pending(owner, pending.accepted, new Permit());
                foreign.render(
                        owner,
                        replacement,
                        replacementGate,
                        () -> fail("A new session is not the old permit"));
                assertEquals("INPUT_DISPATCH_EXPIRED", failure(foreign.completion).code());
            }
            assertFalse(AgentInputContext.isAgentRouted());
            assertNull(gate.dispatch());
        }
    }

    @Test
    void existingOperationCancellationAndTimeoutFailClosedWithoutAnotherTimer() {
        var control = new AgentControlSession();
        try (var gate = new PlayerCommandGate(control, (id, routed) -> {}, value -> {})) {
            Screen owner = screen();
            var accepted = control.acquire();
            var expired = new Permit();
            expired.failure =
                    new ProtocolState.ProtocolException("OPERATION_TIMEOUT", 409, "expired");
            var timeout = pending(owner, accepted, expired);
            timeout.render(owner, control, gate, () -> fail("Expired operation must not render"));
            assertSame(expired.failure, failure(timeout.completion));
            var cancelled = pending(owner, accepted, new Permit());
            cancelled.completion.cancel(false);
            cancelled.render(owner, control, gate, () -> fail("Cancelled future must not render"));
            assertTrue(cancelled.completion.isCancelled());
            assertNull(gate.dispatch());
            assertFalse(AgentInputContext.isAgentRouted());
        }
    }

    @Test
    void nativeRenderAndGateErrorsRemainExceptionalAndNeverLeakRoutedState() {
        var control = new AgentControlSession();
        try (var gate = new PlayerCommandGate(control, (id, routed) -> {}, value -> {})) {
            Screen owner = screen();
            var accepted = control.acquire();
            var crash = pending(owner, accepted, new Permit());
            var original = new IllegalStateException("native render failure");
            crash.render(
                    owner,
                    control,
                    gate,
                    () -> {
                        throw original;
                    });
            assertSame(
                    original,
                    assertThrows(CompletionException.class, crash.completion::join).getCause());
            assertFalse(AgentInputContext.isAgentRouted());
            assertNull(gate.dispatch());
            var denied = pending(owner, accepted, new Permit());
            denied.render(
                    owner,
                    control,
                    gate,
                    () ->
                            assertFalse(
                                    gate.reject(
                                            "PLAYER_COMMAND_NOT_AVAILABLE",
                                            "Current native command rejected")));
            assertEquals("PLAYER_COMMAND_NOT_AVAILABLE", failure(denied.completion).code());
            assertFalse(AgentInputContext.isAgentRouted());
            assertNull(gate.dispatch());
        }
    }

    @Test
    void thePublicNativeWrapperHasOneVirtualRenderAndTheMixinForwardsEveryArgument()
            throws Exception {
        var nativeWrapper =
                method(compiled("net/minecraft/client/gui/screens/Screen"), "renderWithTooltip");
        assertTrue((nativeWrapper.access & Opcodes.ACC_FINAL) != 0);
        var nativeCalls = calls(nativeWrapper);
        assertEquals(
                1,
                nativeCalls.stream()
                        .filter(
                                call ->
                                        call.owner.equals("net/minecraft/client/gui/screens/Screen")
                                                && call.name.equals("render")
                                                && call.desc.equals(
                                                        "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
                        .count());
        int render = -1, tooltip = -1;
        for (int i = 0; i < nativeCalls.size(); i++) {
            if (nativeCalls.get(i).name.equals("render")) render = i;
            if (nativeCalls.get(i).name.equals("renderTooltip")) tooltip = i;
        }
        assertTrue(render >= 0 && tooltip > render);
        var mixin = compiled(ROOT + "mixin/GameModeScreenRenderMixin");
        var redirect =
                mixin.methods.stream()
                        .filter(value -> annotation(value, "Redirect"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1, mixin.methods.stream().filter(value -> annotation(value, "Redirect")).count());
        var description =
                annotations(redirect).stream()
                        .filter(value -> value.desc.endsWith("/Redirect;"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of("renderWithTooltip"), value(description, "method"));
        Object point = value(description, "at");
        if (point instanceof List<?> values) point = values.get(0);
        var at = assertInstanceOf(AnnotationNode.class, point);
        assertEquals(
                "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                value(at, "target"));
        var instructions =
                Arrays.stream(redirect.instructions.toArray())
                        .filter(i -> i.getOpcode() >= 0)
                        .toList();
        Type[] args = Type.getArgumentTypes(redirect.desc);
        int local = 1;
        for (int i = 0; i < args.length; i++) {
            var load = assertInstanceOf(VarInsnNode.class, instructions.get(i));
            assertEquals(args[i].getOpcode(Opcodes.ILOAD), load.getOpcode());
            assertEquals(local, load.var);
            local += args[i].getSize();
        }
        var dispatch = assertInstanceOf(MethodInsnNode.class, instructions.get(args.length));
        assertEquals(RUNTIME, dispatch.owner);
        assertEquals("renderGameModeScreen", dispatch.name);
        assertEquals(Type.getMethodDescriptor(Type.VOID_TYPE, args), dispatch.desc);
        assertEquals(1, calls(redirect).size());
    }

    @Test
    void realKeyReleaseKeepsItsFutureAndNativeCleanupCancelsBeforeReleasingF3() throws Exception {
        var runtime = compiled(RUNTIME);
        var guarded = method(runtime, "keyGuarded");
        assertTrue(
                calls(guarded).stream()
                        .anyMatch(
                                call ->
                                        call.owner.endsWith("/InputDispatchContext")
                                                && call.name.equals("capture")));
        assertTrue(
                calls(guarded).stream()
                        .anyMatch(
                                call ->
                                        call.name.equals("onControlledClient")
                                                && Type.getArgumentTypes(call.desc).length == 4));
        assertTrue(
                calls(guarded).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("java/util/concurrent/CompletableFuture")
                                                && call.name.equals("thenCompose")));
        var delay =
                runtime.methods.stream()
                        .filter(
                                candidate ->
                                        Arrays.stream(candidate.instructions.toArray())
                                                .anyMatch(
                                                        i ->
                                                                i instanceof TypeInsnNode type
                                                                        && type.desc.equals(
                                                                                PENDING)))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                Arrays.stream(delay.instructions.toArray())
                        .anyMatch(
                                i ->
                                        i instanceof TypeInsnNode type
                                                && type.desc.equals(
                                                        "net/minecraft/client/gui/screens/debug/GameModeSwitcherScreen")));
        assertTrue(calls(delay).stream().anyMatch(call -> call.name.equals("containsKey")));
        assertTrue(calls(delay).stream().anyMatch(call -> call.name.equals("keyGuardedNow")));
        var cleanup =
                runtime.methods.stream()
                        .filter(
                                candidate ->
                                        calls(candidate).stream()
                                                        .anyMatch(
                                                                call ->
                                                                        call.name.equals(
                                                                                "finishNativeInputCleanup"))
                                                && calls(candidate).stream()
                                                        .anyMatch(
                                                                call ->
                                                                        call.name.equals(
                                                                                "cancelOwnedGameMode"))
                                                && calls(candidate).stream()
                                                        .anyMatch(
                                                                call ->
                                                                        call.name.equals(
                                                                                "dispatchExtensionInput")))
                        .findFirst()
                        .orElseThrow();
        var cleanupCalls = calls(cleanup);
        int cancel = -1, key = -1;
        for (int i = 0; i < cleanupCalls.size(); i++) {
            if (cleanupCalls.get(i).name.equals("cancelOwnedGameMode")) cancel = i;
            if (cleanupCalls.get(i).owner.equals("net/minecraft/client/KeyboardHandler")
                    && cleanupCalls.get(i).name.equals("keyPress")) key = i;
        }
        // keyPress is in a dispatch lambda, so the owning cleanup must cancel before any dispatch.
        if (key < 0)
            for (int i = 0; i < cleanupCalls.size(); i++)
                if (cleanupCalls.get(i).name.equals("dispatchExtensionInput")) {
                    key = i;
                    break;
                }
        assertTrue(cancel >= 0 && key > cancel);
        var cancelOwner = method(runtime, "cancelOwnedGameMode");
        var renderEntry =
                runtime.methods.stream()
                        .filter(
                                m ->
                                        m.name.equals("renderGameModeScreen")
                                                && Type.getArgumentTypes(m.desc).length == 2)
                        .findFirst()
                        .orElseThrow();
        var renderCalls = calls(renderEntry);
        int refresh = -1, resume = -1;
        for (int i = 0; i < renderCalls.size(); i++) {
            if (renderCalls.get(i).name.equals("refreshInputContext")) refresh = i;
            if (renderCalls.get(i).owner.equals(PENDING)
                    && renderCalls.get(i).name.equals("render")) resume = i;
        }
        assertTrue(
                refresh >= 0 && resume > refresh,
                "Refresh real world/player/connection before the deferred native effect");
        assertTrue(
                calls(cancelOwner).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("net/minecraft/client/Minecraft")
                                                && call.name.equals("setScreen")));
        assertTrue(
                calls(cancelOwner).stream()
                        .anyMatch(call -> call.name.equals("completeExceptionally")));
        assertTrue(
                Arrays.stream(cancelOwner.instructions.toArray())
                        .anyMatch(
                                i ->
                                        i instanceof FieldInsnNode field
                                                && field.name.equals("abandonedGameModeScreen")
                                                && field.getOpcode() == Opcodes.PUTFIELD));
        for (String forbidden :
                List.of(
                        "checkToClose",
                        "switchToHoveredGameMode",
                        "sendUnsignedCommand",
                        "sendCommand",
                        "schedule"))
            assertFalse(
                    runtime.methods.stream()
                            .filter(m -> m.name.contains("GameMode") || m.name.equals("keyGuarded"))
                            .flatMap(m -> calls(m).stream())
                            .anyMatch(call -> call.name.equals(forbidden)));
    }

    private static ForgeProbeRuntime.PendingGameModeRelease pending(
            Screen owner, AgentControlSession.Snapshot accepted, Permit permit) {
        return new ForgeProbeRuntime.PendingGameModeRelease(
                owner, accepted, permit, new JsonObject(), new CompletableFuture<>());
    }

    private static ProtocolState.ProtocolException failure(CompletableFuture<JsonObject> future) {
        return assertInstanceOf(
                ProtocolState.ProtocolException.class,
                assertThrows(CompletionException.class, future::join).getCause());
    }

    private static Screen screen() {
        return new Screen(Component.literal("Owned native Screen fixture")) {};
    }

    private static final class Permit implements InputDispatchContext.Permit {
        final AtomicInteger checks = new AtomicInteger();
        ProtocolState.ProtocolException failure;

        public String gestureId() {
            return "owned-render-input";
        }

        public void check() {
            checks.incrementAndGet();
            if (failure != null) throw failure;
        }

        public void beforeInput(BiConsumer<Long, Long> validate) {
            fail("Render continuation must not admit a second input callback");
        }
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
    }

    private static boolean annotation(MethodNode method, String name) {
        return annotations(method).stream()
                .anyMatch(value -> value.desc.endsWith("/" + name + ";"));
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        return java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }

    private static ClassNode compiled(String name) throws Exception {
        try (InputStream in =
                GameModeRenderDispatchTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(in, name);
            ClassNode result = new ClassNode();
            new ClassReader(in).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
