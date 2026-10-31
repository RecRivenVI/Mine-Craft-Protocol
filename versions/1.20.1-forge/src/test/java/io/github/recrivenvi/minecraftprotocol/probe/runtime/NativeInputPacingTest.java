package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.ProbeService;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NativeInputPacingTest {
    private static final String RUNTIME =
            "io/github/recrivenvi/minecraftprotocol/probe/runtime/ForgeProbeRuntime";

    @Test
    void serviceDefaultUsesTheExistingFullPointerStateWithoutInventingAnotherInputPath() {
        AtomicInteger reads = new AtomicInteger();
        JsonObject full = new JsonObject();
        full.addProperty("menuRevision", 123);
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("pointerState")) {
                                        reads.incrementAndGet();
                                        return CompletableFuture.completedFuture(full);
                                    }
                                    if (method.getName().equals("inputPacingState"))
                                        return InvocationHandler.invokeDefault(
                                                proxy, method, arguments);
                                    throw new AssertionError("Unexpected service call: " + method);
                                });
        assertSame(full, service.inputPacingState().join());
        assertEquals(1, reads.get());
    }

    @Test
    void productionEntriesSelectPacingExplicitlyAndStillRunOnTheClientOwner() throws Exception {
        ClassNode runtime = compiled();
        for (String entry : List.of("pointerState", "inputPacingState")) {
            MethodNode method = method(runtime, entry);
            assertTrue(calls(method).stream().anyMatch(call -> call.name.equals("onClient")));
            Handle callback =
                    Arrays.stream(method.instructions.toArray())
                            .filter(InvokeDynamicInsnNode.class::isInstance)
                            .map(InvokeDynamicInsnNode.class::cast)
                            .flatMap(indy -> Arrays.stream(indy.bsmArgs))
                            .filter(Handle.class::isInstance)
                            .map(Handle.class::cast)
                            .filter(handle -> handle.getOwner().equals(RUNTIME))
                            .findFirst()
                            .orElseThrow();
            MethodInsnNode state =
                    calls(method(runtime, callback.getName())).stream()
                            .filter(call -> call.name.equals("pointerStateNow"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    entry.equals("inputPacingState") ? Opcodes.ICONST_1 : Opcodes.ICONST_0,
                    state.getPrevious().getOpcode());
        }
        MethodNode state = method(runtime, "pointerStateNow");
        List<AbstractInsnNode> instructions = Arrays.asList(state.instructions.toArray());
        MethodInsnNode viewport = call(state, "gameplayViewport");
        JumpInsnNode fallback = assertInstanceOf(JumpInsnNode.class, viewport.getNext());
        assertEquals(Opcodes.IFEQ, fallback.getOpcode());
        assertTrue(
                instructions.indexOf(call(state, "syncPointerContext"))
                        < instructions.indexOf(viewport));
        assertTrue(
                instructions.indexOf(call(state, "gameplayInputFrame"))
                        < instructions.indexOf(fallback.label));
        assertTrue(
                instructions.indexOf(fallback.label)
                        < instructions.indexOf(call(state, "snapshotUiTree")),
                "Any non-gameplay context must retain the full UI path");
    }

    @Test
    void productionCompactFrameDoesNotCaptureHudMenusOrSerializeItems() throws Exception {
        ClassNode runtime = compiled();
        MethodNode frame = method(runtime, "gameplayInputFrame");
        Set<String> required =
                Set.of(
                        "screenClass",
                        "screenIdentity",
                        "overlayIdentity",
                        "screenRevision",
                        "gameCursorRevision",
                        "width",
                        "height",
                        "windowWidth",
                        "windowHeight",
                        "guiScale");
        List<String> strings =
                Arrays.stream(frame.instructions.toArray())
                        .filter(LdcInsnNode.class::isInstance)
                        .map(LdcInsnNode.class::cast)
                        .map(instruction -> instruction.cst)
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
        assertTrue(strings.containsAll(required));
        assertTrue(calls(frame).stream().anyMatch(call -> call.name.equals("refreshScreen")));
        assertFalse(
                strings.contains("menuRevision"), "A skipped menu must not claim fresh content");
        for (MethodInsnNode call : calls(frame)) {
            assertFalse(
                    List.of("snapshotUiTree", "refreshMenu", "menuHash", "itemContent")
                            .contains(call.name));
            assertFalse(call.owner.contains("inventory/"));
            assertFalse(call.owner.contains("world/item/"));
            assertFalse(call.owner.endsWith("UiPresentationSnapshot"));
            assertFalse(call.owner.endsWith("UiDebugEngine"));
            assertFalse(call.owner.endsWith("SpectatorUiTree"));
        }
        MethodNode viewport = method(runtime, "gameplayViewport");
        List<FieldInsnNode> fields =
                Arrays.stream(viewport.instructions.toArray())
                        .filter(FieldInsnNode.class::isInstance)
                        .map(FieldInsnNode.class::cast)
                        .toList();
        for (String field : List.of("minecraft", "level", "player"))
            assertTrue(fields.stream().anyMatch(access -> access.name.equals(field)), field);
        assertTrue(fields.stream().anyMatch(access -> access.name.equals("screen")));
        assertTrue(calls(viewport).stream().anyMatch(call -> call.name.equals("getOverlay")));
    }

    @Test
    void explicitMenuPinStillRefreshesNativeContentsAndBothRevisionChecksRemain() throws Exception {
        MethodNode check = method(compiled(), "checkInputPreconditions");
        List<AbstractInsnNode> instructions = Arrays.asList(check.instructions.toArray());
        MethodInsnNode refresh = call(check, "refreshMenu");
        int refreshAt = instructions.indexOf(refresh);
        assertTrue(
                instructions.stream()
                        .filter(JumpInsnNode.class::isInstance)
                        .map(JumpInsnNode.class::cast)
                        .anyMatch(
                                branch ->
                                        branch.getOpcode() == Opcodes.IFNULL
                                                && instructions.indexOf(branch) < refreshAt
                                                && instructions.indexOf(branch.label) > refreshAt),
                "Only an absent expectedMenuRevision may skip the native menu refresh");
        assertTrue(calls(check).stream().anyMatch(call -> call.name.equals("refreshScreen")));
        List<String> messages =
                instructions.stream()
                        .filter(LdcInsnNode.class::isInstance)
                        .map(LdcInsnNode.class::cast)
                        .map(instruction -> instruction.cst)
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
        assertTrue(messages.contains("STALE_MENU_REVISION"));
        assertTrue(messages.contains("STALE_SCREEN_REVISION"));
    }

    @Test
    void relativeMouseStillUsesTheExistingRoutedVanillaProcessing() throws Exception {
        ClassNode runtime = compiled();
        MethodNode delta = method(runtime, "mouseDeltaNow");
        assertTrue(calls(delta).stream().anyMatch(call -> call.name.equals("gameplayViewport")));
        assertTrue(calls(delta).stream().anyMatch(call -> call.name.equals("movePointerNow")));
        List<MethodInsnNode> movement =
                runtime.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("movePointerNow")
                                                || method.name.startsWith("lambda$movePointerNow$"))
                        .flatMap(method -> calls(method).stream())
                        .toList();
        for (String nativeCall :
                List.of("dispatchExtensionInput", "minecraftProtocolProbe$onMove", "turnPlayer"))
            assertTrue(
                    movement.stream().anyMatch(call -> call.name.equals(nativeCall)), nativeCall);
        assertTrue(
                calls(method(runtime, "dispatchExtensionInput")).stream()
                        .anyMatch(
                                call ->
                                        call.owner.endsWith("/LogicalInputRouter")
                                                && call.name.equals("dispatch")));
        assertFalse(
                movement.stream()
                        .anyMatch(
                                call ->
                                        List.of(
                                                        "setYRot",
                                                        "setXRot",
                                                        "glfwSetCursorPos",
                                                        "SDL_WarpMouseInWindow")
                                                .contains(call.name)));
    }

    private static MethodInsnNode call(MethodNode method, String name) {
        return calls(method).stream()
                .filter(call -> call.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassNode compiled() throws Exception {
        try (InputStream input =
                NativeInputPacingTest.class.getResourceAsStream("/" + RUNTIME + ".class")) {
            assertNotNull(input);
            ClassNode type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return type;
        }
    }
}
