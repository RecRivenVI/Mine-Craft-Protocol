package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class NativeWorldTransferTest {
    @Test
    void actualTransferUsesFreshResourceConditionsAndNativeApiInsideTheNoLoadScope()
            throws Exception {
        List<String> calls = calls("debugTransfer");
        assertTrue(calls.indexOf("verifyExpected") < calls.indexOf("teleportTo"));
        assertTrue(calls.indexOf("requireEntityBoundsLoaded") < calls.indexOf("teleportTo"));
        assertTrue(calls.indexOf("open") < calls.indexOf("teleportTo"));
        assertTrue(
                calls.containsAll(
                        List.of(
                                "getCamera",
                                "isPassenger",
                                "isVehicle",
                                "blockedAccess",
                                "close")));
        assertFalse(
                calls.stream()
                        .anyMatch(
                                List.of(
                                                "setPos",
                                                "setYRot",
                                                "setXRot",
                                                "setServerLevel",
                                                "performPrefixedCommand",
                                                "getDataStorage",
                                                "save")
                                        ::contains));
    }

    @Test
    void actualCrossDimensionReadsUseOnlyExistingLevelsAndLoadedEntityLookup() throws Exception {
        assertTrue(
                calls("selectedEntities")
                        .containsAll(List.of("getLevel", "getEntity", "getChunkNow", "entity")));
        assertFalse(
                calls("selectedEntities").stream()
                        .anyMatch(
                                List.of(
                                                "getChunk",
                                                "getDataStorage",
                                                "load",
                                                "createLevels",
                                                "createWorlds")
                                        ::contains));
        assertTrue(calls("world").contains("getAllLevels"));
        assertTrue(calls("canonicalResourceRevisions").contains("entityRevision"));
    }

    private static List<String> calls(String name) throws Exception {
        try (InputStream input =
                NativeWorldTransferTest.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            var method =
                    node.methods.stream()
                            .filter(m -> m.name.equals(name))
                            .findFirst()
                            .orElseThrow();
            return Arrays.stream(method.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast)
                    .map(m -> m.name)
                    .toList();
        }
    }
}
