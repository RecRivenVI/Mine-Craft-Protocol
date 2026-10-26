package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class NativeWorldLoadingTest {
    @Test
    void actualScheduledTickProducerUsesNativeCountsWithoutUnpackingOrScheduling()
            throws Exception {
        List<String> calls = calls("scheduledTicks");
        assertTrue(calls.containsAll(List.of("getAll", "count", "metadata")));
        assertFalse(
                calls.stream()
                        .anyMatch(List.of("unpack", "schedule", "poll", "getChunk")::contains));
        assertTrue(calls("chunks").contains("getChunkNow"));
        List<String> simulation = calls("simulation");
        assertTrue(simulation.contains("minecraftProtocol$isPaused"));
        assertFalse(simulation.contains("tickRateManager"));
        assertFalse(
                simulation.stream()
                        .anyMatch(
                                List.of(
                                                "setFrozen",
                                                "setTickRate",
                                                "stepGameIfPaused",
                                                "requestGameToSprint",
                                                "tick")
                                        ::contains));
    }

    @Test
    void actualBlockMutationAndNativeChunkEntryEnforceTheLoadedBoundary() throws Exception {
        assertTrue(
                calls("debugWorldBlock")
                        .containsAll(
                                List.of(
                                        "open",
                                        "getChunkNow",
                                        "setBlockAndUpdate",
                                        "blockedAccess",
                                        "close")));
        assertTrue(
                calls(
                                "io/github/recrivenvi/minecraftprotocol/probe/mixin/DebugLoadedChunkAccessMixin.class",
                                "minecraftProtocol$loadedDebugDependency")
                        .containsAll(
                                List.of("current", "getChunkNow", "reject", "setReturnValue")));
        try (InputStream json =
                NativeWorldLoadingTest.class
                        .getClassLoader()
                        .getResourceAsStream("minecraft_protocol_probe.mixins.json")) {
            assertNotNull(json);
            assertTrue(
                    new String(json.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("\"DebugLoadedChunkAccessMixin\""));
        }
    }

    private static List<String> calls(String name) throws Exception {
        return calls(
                "io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.class",
                name);
    }

    private static List<String> calls(String resource, String name) throws Exception {
        try (InputStream input =
                NativeWorldLoadingTest.class.getClassLoader().getResourceAsStream(resource)) {
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
