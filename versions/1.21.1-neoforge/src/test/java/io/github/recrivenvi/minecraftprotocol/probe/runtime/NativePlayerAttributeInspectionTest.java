package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NativePlayerAttributeInspectionTest {
    private static final String ATTRIBUTES = "net/minecraft/world/entity/ai/attributes/";

    @Test
    void productionPlayerInspectionUsesBothNativeDefaultReadersWithoutCreatingInstances()
            throws Exception {
        MethodNode player =
                compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine")
                        .methods
                        .stream()
                        .filter(method -> method.name.equals("player"))
                        .findFirst()
                        .orElseThrow();
        List<AbstractInsnNode> instructions =
                Arrays.stream(player.instructions.toArray())
                        .filter(instruction -> instruction.getOpcode() >= 0)
                        .toList();
        for (String getter : List.of("getBaseValue", "getValue")) {
            List<MethodInsnNode> calls =
                    instructions.stream()
                            .filter(MethodInsnNode.class::isInstance)
                            .map(MethodInsnNode.class::cast)
                            .filter(
                                    call ->
                                            call.owner.equals(ATTRIBUTES + "AttributeMap")
                                                    && call.name.equals(getter))
                            .toList();
            assertEquals(1, calls.size(), getter);
            int at = instructions.indexOf(calls.get(0));
            FieldInsnNode argument =
                    assertInstanceOf(FieldInsnNode.class, instructions.get(at - 1));
            assertEquals(ATTRIBUTES + "Attributes", argument.owner);
            assertEquals("MAX_HEALTH", argument.name);
        }
        List<MethodInsnNode> calls =
                instructions.stream()
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        assertTrue(
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("java/util/stream/Stream")
                                                && call.name.equals("noneMatch")),
                "Existing native id must be checked before adding the supplier-backed default");
        assertTrue(
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("java/util/List")
                                                && call.name.equals("sort")));
        assertFalse(
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.name.equals("getInstance")
                                                || call.name.equals("setBaseValue")));
    }

    @Test
    void actualNativeDefaultReadersOnlyLookUpExistingInstancesOrSupplierValues() throws Exception {
        ClassNode attributeMap = compiled(ATTRIBUTES + "AttributeMap");
        for (String getter : List.of("getBaseValue", "getValue")) {
            MethodNode method =
                    attributeMap.methods.stream()
                            .filter(m -> m.name.equals(getter))
                            .findFirst()
                            .orElseThrow();
            List<MethodInsnNode> calls =
                    Arrays.stream(method.instructions.toArray())
                            .filter(MethodInsnNode.class::isInstance)
                            .map(MethodInsnNode.class::cast)
                            .toList();
            assertTrue(
                    calls.stream()
                            .anyMatch(
                                    call ->
                                            call.owner.equals("java/util/Map")
                                                    && call.name.equals("get")));
            assertTrue(
                    calls.stream()
                            .anyMatch(
                                    call ->
                                            call.owner.equals(ATTRIBUTES + "AttributeSupplier")
                                                    && call.name.equals(getter)));
            assertFalse(
                    calls.stream()
                            .anyMatch(
                                    call ->
                                            List.of(
                                                            "getInstance",
                                                            "computeIfAbsent",
                                                            "put",
                                                            "createInstance",
                                                            "setBaseValue")
                                                    .contains(call.name)),
                    getter);
        }
    }

    private static ClassNode compiled(String name) throws Exception {
        try (InputStream input =
                NativePlayerAttributeInspectionTest.class.getResourceAsStream(
                        "/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
