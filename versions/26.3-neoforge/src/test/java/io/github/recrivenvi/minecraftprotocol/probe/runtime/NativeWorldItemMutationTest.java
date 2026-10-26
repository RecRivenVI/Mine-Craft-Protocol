package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NativeWorldItemMutationTest {
    @Test
    void actualProducerCapabilitiesMatchHandlersAndDoNotPromiseRemoteWithoutPeer()
            throws Exception {
        try (Phase9ASpikeEngine engine = new Phase9ASpikeEngine("contract-test")) {
            JsonObject value = engine.formalDebugCapabilities();
            assertTrue(value.get("requiresDebugArm").getAsBoolean());
            assertFalse(value.get("persistentStorageMutation").getAsBoolean());
            assertEquals(
                    "unavailable",
                    value.getAsJsonObject("deployments").get("remoteWithoutPeer").getAsString());
            var literals =
                    Arrays.stream(method(compiled(), "debugMutation").instructions.toArray())
                            .filter(LdcInsnNode.class::isInstance)
                            .map(LdcInsnNode.class::cast)
                            .map(node -> String.valueOf(node.cst))
                            .toList();
            for (var item : value.getAsJsonArray("operations")) {
                JsonObject operation = item.getAsJsonObject();
                if (!operation.get("status").getAsString().equals("available")
                        || operation.get("operation").getAsString().equals("provider.mutate"))
                    continue;
                assertTrue(literals.contains(operation.get("operation").getAsString()));
                assertTrue(operation.get("requiresArm").getAsBoolean());
                assertTrue(operation.get("resourceVersionPrecondition").getAsBoolean());
            }
        }
    }

    @Test
    void actualFormalizerPreservesNativeCaptureEffectsInsteadOfHardcodingNone() throws Exception {
        try (Phase9ASpikeEngine engine = new Phase9ASpikeEngine("contract-test")) {
            for (String effect : List.of("none", "serialization_hooks_invoked")) {
                JsonObject server = new JsonObject();
                server.addProperty("readEffects", effect);
                JsonObject result = engine.formalize(new JsonObject(), null, server, null).join();
                assertEquals(
                        effect,
                        result.getAsJsonObject("metadata").get("readEffects").getAsString());
                assertEquals(
                        effect, result.getAsJsonObject("server").get("readEffects").getAsString());
            }
        }
    }

    @Test
    void productionDispatchAndAllThreeCommitsKeepRevisionBeforeNativeMutation() throws Exception {
        ClassNode engine = compiled();
        List<String> literals =
                Arrays.stream(method(engine, "debugMutation").instructions.toArray())
                        .filter(LdcInsnNode.class::isInstance)
                        .map(LdcInsnNode.class::cast)
                        .map(value -> String.valueOf(value.cst))
                        .toList();
        assertTrue(
                literals.containsAll(
                        List.of(
                                "player.inventory.slot.set",
                                "player.equipment.set",
                                "block_entity.slot.set")));
        for (String[] route :
                List.of(
                        new String[] {"debugPlayerInventorySlot", "setItem"},
                        new String[] {"debugPlayerEquipment", "setItemSlot"},
                        new String[] {"debugBlockEntitySlot", "setItem"})) {
            List<String> calls = calls(method(engine, route[0]));
            assertTrue(calls.indexOf("verifyExpected") >= 0);
            assertTrue(calls.indexOf("verifyExpected") < calls.indexOf(route[1]));
            assertTrue(calls.indexOf("verifyExpectedItem") < calls.indexOf(route[1]));
            assertTrue(calls.contains("mutationResult"));
        }
        List<String> replacement = calls(method(engine, "debugReplacementItem"));
        List<String> container = calls(method(engine, "debugBlockEntitySlot"));
        assertTrue(container.indexOf("requireResolvedContainer") < container.indexOf("getItem"));
        assertTrue(
                replacement.containsAll(
                        List.of("parse", "getMaxStackSize", "isDamageableItem", "getMaxDamage")));
        assertFalse(
                replacement.stream()
                        .anyMatch(List.of("parseTag", "readCompressed", "setTag")::contains));
    }

    @Test
    void actualAttributeReadDoesNotInstantiateAndMutationValidatesBeforeNativeCommit()
            throws Exception {
        ClassNode engine = compiled();
        List<String> reads = calls(method(engine, "nativeAttributes"));
        assertTrue(reads.containsAll(List.of("getBaseValue", "getValue", "getModifiers")));
        assertFalse(
                reads.stream()
                        .anyMatch(
                                List.of("getInstance", "getAttribute", "setBaseValue")::contains));
        List<String> mutation = calls(method(engine, "debugAttribute"));
        assertTrue(mutation.indexOf("verifyExpected") < mutation.indexOf("getAttribute"));
        assertTrue(mutation.indexOf("sanitizeValue") < mutation.indexOf("setBaseValue"));
        assertTrue(mutation.indexOf("verifyDebugEntityType") < mutation.indexOf("setBaseValue"));
        List<String> equipment = calls(method(engine, "debugEntityEquipment"));
        assertTrue(equipment.indexOf("verifyExpected") < equipment.indexOf("setItemSlot"));
        assertTrue(equipment.indexOf("verifyExpectedItem") < equipment.indexOf("setItemSlot"));
    }

    @Test
    void actualEntityLifecycleUsesNativeFactoryLoadedQueriesAndRevisionBeforeCommit()
            throws Exception {
        ClassNode engine = compiled();
        List<String> spawn = calls(method(engine, "debugEntitySpawn"));
        assertTrue(spawn.indexOf("verifyExpected") < spawn.indexOf("create"));
        assertTrue(spawn.indexOf("requireEntityBoundsLoaded") < spawn.indexOf("create"));
        assertTrue(spawn.indexOf("create") < spawn.indexOf("addFreshEntity"));
        assertFalse(
                spawn.stream()
                        .anyMatch(
                                List.of("loadEntityRecursive", "finalizeSpawn", "parseTag")
                                        ::contains));
        List<String> remove = calls(method(engine, "debugEntityRemove"));
        assertTrue(remove.indexOf("verifyExpected") < remove.indexOf("discard"));
        List<String> registry = calls(method(engine, "entityRegistry"));
        assertTrue(registry.containsAll(List.of("getChunkNow", "getEntities")));
        assertFalse(
                registry.stream()
                        .anyMatch(List.of("getChunk", "getChunkAt", "getOrCreate")::contains));
    }

    private static ClassNode compiled() throws Exception {
        String resource =
                "io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.class";
        try (InputStream input =
                NativeWorldItemMutationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream()
                .filter(value -> value.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .map(value -> value.name)
                .toList();
    }
}
