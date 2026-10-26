package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NativeWorldRelationTest {
    @Test
    void relationshipProducerAvoidsLazyOwnerOrLeashResolutionAndKeepsMovementOutOfItsResource()
            throws Exception {
        ClassNode engine = compiled("Phase9ASpikeEngine");
        List<String> read = calls(method(engine, "relationship"));
        assertTrue(
                read.containsAll(
                        List.of(
                                "getOwnerUUID",
                                "minecraftProtocol$getLeashHolder",
                                "getPassengers")));
        assertFalse(
                read.stream()
                        .anyMatch(
                                List.of(
                                                "getOwner",
                                                "getLeashHolder",
                                                "getEntity",
                                                "getChunk",
                                                "getX",
                                                "getY",
                                                "getHealth")
                                        ::contains));
        List<String> commit = calls(method(engine, "debugRelation"));
        assertTrue(commit.indexOf("verifyExpected") < commit.indexOf("startRiding"));
        assertTrue(
                commit.indexOf("verifyRelationshipParticipants") < commit.indexOf("startRiding"));
        assertTrue(
                commit.containsAll(
                        List.of(
                                "startRiding",
                                "stopRiding",
                                "dropLeash",
                                "setOwnerUUID",
                                "mutationResult")));
        ClassNode runtime = compiled("ForgeProbeRuntime");
        assertTrue(
                runtime.methods.stream()
                        .filter(m -> m.name.contains("lambda$phase9cDebugMutation"))
                        .anyMatch(m -> calls(m).contains("authorizeAll")));
    }

    private static ClassNode compiled(String name) throws Exception {
        try (InputStream input =
                NativeWorldRelationTest.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "io/github/recrivenvi/minecraftprotocol/probe/runtime/"
                                        + name
                                        + ".class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<String> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .map(m -> m.name)
                .toList();
    }
}
