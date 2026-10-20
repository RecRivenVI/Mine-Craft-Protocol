package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class NativeLogicalCaptureTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/";

    private static ClassNode runtime() throws IOException {
        var type = new ClassNode();
        try (var source =
                NativeLogicalCaptureTest.class
                        .getClassLoader()
                        .getResourceAsStream(ROOT + "probe/runtime/FabricProbeRuntime.class")) {
            assertNotNull(source);
            new ClassReader(source).accept(type, 0);
        }
        return type;
    }

    @Test
    void actualGameGrabUpdatesLogicalCaptureWithoutDependingOnHostFocus() throws Exception {
        var method =
                runtime().methods.stream()
                        .filter(value -> value.name.equals("allowHostMouseGrab"))
                        .findFirst()
                        .orElseThrow();
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        assertEquals(
                1, calls.stream().filter(call -> call.name.equals("requestGameCapture")).count());
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("agentControlled")));
        assertTrue(
                calls.stream()
                        .noneMatch(
                                call ->
                                        call.name.equals("isWindowActive")
                                                || call.name.equals("isAgentRouted")
                                                || call.owner.startsWith("org/lwjgl/")),
                "Logical game capture must neither require focus nor grab/warp the physical cursor");
    }

    @Test
    void relativeDispatchRefusesMissingLogicalCaptureBeforeAnyNativeMove() throws Exception {
        var method =
                runtime().methods.stream()
                        .filter(value -> value.name.equals("mouseDeltaNow"))
                        .findFirst()
                        .orElseThrow();
        int capture = -1, nativeMove = -1, rejection = -1;
        for (int index = 0; index < method.instructions.size(); index++) {
            var instruction = method.instructions.get(index);
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("gameCaptured")) capture = index;
                if (call.name.equals("movePointerNow")) nativeMove = index;
            }
            if (instruction instanceof LdcInsnNode constant
                    && "Relative input requires the game's logical capture request; host focus is not required"
                            .equals(constant.cst)) rejection = index;
        }
        assertTrue(
                capture >= 0 && rejection > capture && nativeMove > rejection,
                "Missing logical capture must not complete with silently ignored relative input");
    }
}
