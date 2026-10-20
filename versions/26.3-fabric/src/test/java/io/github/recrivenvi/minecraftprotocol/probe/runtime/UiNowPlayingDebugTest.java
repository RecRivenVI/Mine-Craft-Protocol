package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.probe.mixin.ToastManagerUiAccessor;
import java.util.List;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.gen.Accessor;

class UiNowPlayingDebugTest {
    @Test
    void typedAccessorUsesTheActualNativeInstanceClassWithoutAnObjectSubstitute() throws Exception {
        var getter = ToastManagerUiAccessor.class.getMethod("minecraftProtocolProbe$nowPlaying");
        assertEquals("nowPlayingToast", getter.getAnnotation(Accessor.class).value());
        assertEquals(ToastManager.ToastInstance.class, getter.getReturnType());
        assertTrue(getter.getGenericReturnType().getTypeName().contains("NowPlayingToast"));
        assertNotEquals(Object.class, getter.getReturnType());
    }

    @Test
    void realNativeBytecodeDisablesTheTimedVisibilityWriterWithoutFakingAnFmlRuntime()
            throws Exception {
        // Native execution requires the Loader and is checked by the six-tick Runtime scenario.
        var source = compiled("net/minecraft/client/gui/components/toasts/NowPlayingToast");
        var finished =
                source.methods.stream()
                        .filter(method -> method.name.equals("onFinishedRendering"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                List.of(Opcodes.ALOAD, Opcodes.ICONST_0, Opcodes.PUTFIELD, Opcodes.RETURN),
                java.util.Arrays.stream(finished.instructions.toArray())
                        .filter(instruction -> instruction.getOpcode() >= 0)
                        .map(instruction -> instruction.getOpcode())
                        .toList());
        var disabledField =
                java.util.Arrays.stream(finished.instructions.toArray())
                        .filter(instruction -> instruction instanceof FieldInsnNode)
                        .map(instruction -> (FieldInsnNode) instruction)
                        .findFirst()
                        .orElseThrow();
        assertEquals("updateToast", disabledField.name);
        assertEquals("Z", disabledField.desc);
        var update =
                source.methods.stream()
                        .filter(method -> method.name.equals("update"))
                        .findFirst()
                        .orElseThrow();
        JumpInsnNode gated = null;
        int visibilityWrite = -1;
        for (var instruction : update.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                if (field.getOpcode() == Opcodes.GETFIELD && field.name.equals("updateToast")) {
                    assertInstanceOf(JumpInsnNode.class, field.getNext());
                    gated = (JumpInsnNode) field.getNext();
                    assertEquals(Opcodes.IFEQ, gated.getOpcode());
                }
                if (field.getOpcode() == Opcodes.PUTFIELD
                        && field.name.equals("wantedVisibility")) {
                    assertEquals(
                            -1, visibilityWrite, "Only one native visibility writer is expected");
                    visibilityWrite = update.instructions.indexOf(field);
                }
            }
        }
        assertNotNull(gated, "Native update must test updateToast before changing visibility");
        assertTrue(visibilityWrite > update.instructions.indexOf(gated));
        assertTrue(
                update.instructions.indexOf(gated.label) > visibilityWrite,
                "The false flag must skip the real native visibility writer");
    }

    @Test
    void nativeHideUsesOnlyTheTwoApprovedLocalCallsInOrder() throws Exception {
        var source = compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/UiToastDebug");
        var hide =
                source.methods.stream()
                        .filter(method -> method.name.equals("hideNowPlaying"))
                        .findFirst()
                        .orElseThrow();
        var calls = new java.util.ArrayList<String>();
        for (var instruction : hide.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            assertEquals("net/minecraft/client/gui/components/toasts/NowPlayingToast", call.owner);
            calls.add(call.name);
        }
        assertEquals(List.of("onFinishedRendering", "setWantedVisibility"), calls);
        var resident =
                source.methods.stream()
                        .filter(method -> method.name.equals("residentInstances"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                java.util.Arrays.stream(resident.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.name.equals(
                                                        "minecraftProtocolProbe$nowPlaying")));
        assertTrue(
                source.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("mutate")
                                                || method.name.startsWith("lambda$mutate$"))
                        .anyMatch(
                                method ->
                                        java.util.Arrays.stream(method.instructions.toArray())
                                                .anyMatch(
                                                        instruction ->
                                                                instruction
                                                                                instanceof
                                                                                MethodInsnNode call
                                                                        && call.name.equals(
                                                                                "hideNowPlaying"))));
    }

    private static ClassNode compiled(String className) throws Exception {
        String name = "/" + className + ".class";
        try (var input = UiNowPlayingDebugTest.class.getResourceAsStream(name)) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
