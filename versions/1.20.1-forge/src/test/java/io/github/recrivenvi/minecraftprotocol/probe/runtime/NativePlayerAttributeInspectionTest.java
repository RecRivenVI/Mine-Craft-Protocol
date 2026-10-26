package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NativePlayerAttributeInspectionTest {
    private static final String ATTRIBUTES = "net/minecraft/world/entity/ai/attributes/";

    @Test
    void productionPlayerInspectionUsesNativeCatalogDefaultsWithoutCreatingInstances()
            throws Exception {
        var engine =
                compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine");
        MethodNode player =
                engine.methods.stream()
                        .filter(method -> method.name.equals("player"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                Arrays.stream(player.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .anyMatch(call -> call.name.equals("nativeAttributes")));
        MethodNode attributes =
                engine.methods.stream()
                        .filter(method -> method.name.equals("nativeAttributes"))
                        .findFirst()
                        .orElseThrow();
        var calls =
                Arrays.stream(attributes.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        for (String getter : List.of("hasAttribute", "getBaseValue", "getValue"))
            assertEquals(
                    1,
                    calls.stream()
                            .filter(
                                    call ->
                                            call.owner.equals(ATTRIBUTES + "AttributeMap")
                                                    && call.name.equals(getter))
                            .count(),
                    getter);
        assertTrue(
                Arrays.stream(attributes.instructions.toArray())
                        .filter(FieldInsnNode.class::isInstance)
                        .map(FieldInsnNode.class::cast)
                        .anyMatch(
                                field ->
                                        field.owner.equals(
                                                        "net/minecraft/core/registries/BuiltInRegistries")
                                                && field.name.equals("ATTRIBUTE")));
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
                                        List.of(
                                                        "getInstance",
                                                        "createInstance",
                                                        "setBaseValue",
                                                        "computeIfAbsent")
                                                .contains(call.name)));
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
