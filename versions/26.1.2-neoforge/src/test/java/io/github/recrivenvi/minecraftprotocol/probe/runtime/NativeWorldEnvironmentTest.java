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

class NativeWorldEnvironmentTest {
    @Test
    void actualNativeMutationsValidateBeforeCommitAndDoNotUseCommandsOrStorage() throws Exception {
        ClassNode engine = compiled("runtime/Phase9ASpikeEngine");
        for (String[] operation :
                List.of(
                        new String[] {"debugWorldTime", "setTotalTicks"},
                        new String[] {"debugWorldWeather", "setWeatherParameters"},
                        new String[] {"setGameRule", "set"})) {
            List<String> calls = calls(method(engine, operation[0]));
            assertTrue(calls.indexOf("verifyExpected") >= 0);
            assertTrue(calls.indexOf("verifyExpected") < calls.indexOf(operation[1]));
            assertFalse(
                    calls.stream()
                            .anyMatch(
                                    List.of(
                                                    "performPrefixedCommand",
                                                    "getDataStorage",
                                                    "save",
                                                    "saveAllChunks",
                                                    "getChunk")
                                            ::contains));
        }
        assertTrue(
                calls(method(engine, "setGameRule")).indexOf("parse")
                        < calls(method(engine, "setGameRule")).indexOf("set"));
        assertTrue(calls(method(engine, "world")).contains("environment"));
        assertFalse(
                calls(method(engine, "environment")).stream()
                        .anyMatch(
                                List.of("getChunk", "getDataStorage", "getInstance", "createRule")
                                        ::contains));
    }

    @Test
    void productionHooksCountNamedEditsInsteadOfNormalClockTicks() throws Exception {
        ClassNode clock = compiled("mixin/ClockControlRevisionMixin"),
                weather = compiled("mixin/WeatherControlRevisionMixin");
        assertTrue(
                calls(method(clock, "minecraftProtocol$recordClockEdit"))
                        .containsAll(List.of("minecraftProtocol$clocks", "timeEdited")));
        assertTrue(
                calls(method(weather, "minecraftProtocol$recordWeatherEdit"))
                        .contains("weatherEdited"));
    }

    private static ClassNode compiled(String suffix) throws Exception {
        try (InputStream input =
                NativeWorldEnvironmentTest.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "io/github/recrivenvi/minecraftprotocol/probe/"
                                        + suffix
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
