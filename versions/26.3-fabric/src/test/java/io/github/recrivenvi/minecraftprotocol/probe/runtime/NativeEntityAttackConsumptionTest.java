package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class NativeEntityAttackConsumptionTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/";
    private static final String RUNTIME = ROOT + "probe/runtime/FabricProbeRuntime";

    @Test
    void actualNativeEntityAttackReturnIsObservedWithoutChangingTheExistingGuard()
            throws Exception {
        ClassNode mixin = compiled(ROOT + "probe/mixin/WorldGestureMixin");
        MethodNode observer = method(mixin, "minecraftProtocolProbe$attackConsumed");
        var injection = annotation(observer, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("attack"), value(injection, "method"));
        assertEquals(
                "RETURN",
                value((AnnotationNode) ((List<?>) value(injection, "at")).get(0), "value"));
        assertNotEquals(Boolean.TRUE, value(injection, "cancellable"));
        assertEquals(1, calls(observer).size());
        MethodInsnNode call = calls(observer).get(0);
        assertEquals(RUNTIME, call.owner);
        assertEquals("nativeEntityAttackCompleted", call.name);
        assertEquals(
                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
                call.desc);
        MethodNode guard = method(mixin, "minecraftProtocolProbe$guardAttack");
        assertEquals(
                Boolean.TRUE,
                value(
                        annotation(guard, "Lorg/spongepowered/asm/mixin/injection/Inject;"),
                        "cancellable"));
        assertTrue(callIndex(guard, RUNTIME, "allowSemanticWorldInput") >= 0);
        MethodNode nativeAttack =
                method(compiled("net/minecraft/client/multiplayer/MultiPlayerGameMode"), "attack");
        assertEquals(call.desc, nativeAttack.desc);
        assertTrue(
                callIndex(nativeAttack, "net/minecraft/world/entity/player/Player", "attack") >= 0);
        assertTrue(
                callIndex(
                                nativeAttack,
                                "net/minecraft/client/multiplayer/ClientPacketListener",
                                "send")
                        >= 0);
        assertTrue(
                callIndex(
                                method(compiled(RUNTIME), "nativeEntityAttackCompleted"),
                                RUNTIME,
                                "recordNativeEntityAttack")
                        >= 0);
    }

    @Test
    void productionCounterRequiresOwnerTakeoverRoutedConsumptionAndCurrentNativeLease()
            throws Exception {
        MethodNode record = method(compiled(RUNTIME), "recordNativeEntityAttack");
        assertTrue(callIndex(record, "net/minecraft/client/Minecraft", "isSameThread") >= 0);
        assertTrue(
                callIndex(record, ROOT + "safety/AgentControlSession$Snapshot", "agentControlled")
                        >= 0);
        assertTrue(callIndex(record, ROOT + "safety/InputContextEpoch", "snapshot") >= 0);
        assertTrue(
                callIndex(record, ROOT + "safety/InputContextEpoch$Snapshot", "worldAvailable")
                        >= 0);
        assertTrue(
                callIndex(
                                record,
                                ROOT + "safety/AgentControlSession$Snapshot",
                                "transitionSequence")
                        >= 0);
        assertTrue(callIndex(record, ROOT + "safety/AgentInputContext", "isAgentRouted") >= 0);
        assertEquals(
                -1,
                callIndex(record, "java/util/Set", "contains"),
                "Vanilla may consume a real queued click after mouse-up");
        assertTrue(callIndex(record, "net/minecraft/client/Minecraft", "getConnection") >= 0);
        assertTrue(callIndex(record, "net/minecraft/world/entity/Entity", "level") >= 0);
        for (String identity :
                List.of(
                        "nativeEntityAttackContext",
                        "nativeEntityAttackControlGeneration",
                        "nativeEntityAttackWorld",
                        "nativeEntityAttackPlayer",
                        "nativeEntityAttackConnection"))
            assertTrue(fields(record).stream().anyMatch(field -> field.name.equals(identity)));
        int lease = callIndex(record, ROOT + "core/ProbeTransport", "nativeInputLeaseActive");
        assertTrue(lease >= 0);
        var increment =
                fields(record).stream()
                        .filter(
                                field ->
                                        field.name.equals("nativeEntityAttackCount")
                                                && field.getOpcode() == Opcodes.PUTFIELD)
                        .findFirst()
                        .orElseThrow();
        assertTrue(record.instructions.indexOf(increment) > lease);
        assertFalse((record.access & Opcodes.ACC_SYNCHRONIZED) != 0);
        for (MethodInsnNode call : calls(record)) {
            assertFalse(call.owner.equals(ROOT + "core/ProtocolState"));
            assertFalse(
                    List.of(
                                    "attack",
                                    "hurt",
                                    "swing",
                                    "send",
                                    "setBlock",
                                    "setYRot",
                                    "setXRot",
                                    "acquireLease",
                                    "renewLease",
                                    "releaseLease")
                            .contains(call.name));
        }
        assertEquals(
                1,
                fields(record).stream()
                        .filter(
                                field ->
                                        field.getOpcode() == Opcodes.PUTFIELD
                                                || field.getOpcode() == Opcodes.PUTSTATIC)
                        .count());
    }

    @Test
    void nativeConsumptionScopeAndQueuedClickOwnershipUseProductionPaths() throws Exception {
        ClassNode runtime = compiled(RUNTIME);
        for (String methodName : List.of("keyGuardedNow", "mouseButtonGuardedNow"))
            assertTrue(callIndex(method(runtime, methodName), RUNTIME, "trackNativeInput") >= 0);
        assertTrue(
                callIndex(
                                method(runtime, "trackNativeInput"),
                                ROOT + "safety/NativeInputOwnership",
                                "track")
                        >= 0);
        assertTrue(
                callIndex(
                                method(runtime, "pendingNativeClicks"),
                                ROOT + "probe/mixin/KeyBindingUiAccessor",
                                "minecraftProtocolProbe$pendingClicks")
                        >= 0);
        MethodNode cleanup = method(runtime, "finishNativeInputCleanup");
        assertTrue(callIndex(cleanup, ROOT + "safety/NativeInputOwnership", "releaseOwned") >= 0);
        assertTrue(callIndex(cleanup, ROOT + "safety/NativeInputOwnership", "retireConsumed") >= 0);
        assertTrue(
                java.util.stream.StreamSupport.stream(cleanup.instructions.spliterator(), false)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof LdcInsnNode literal
                                                && literal.cst.equals("pipeline_completed")));
        assertTrue(
                callIndex(
                                method(runtime, "discardContextInput"),
                                RUNTIME,
                                "finishNativeInputCleanup")
                        >= 0);
        assertTrue(
                runtime.methods.stream()
                        .filter(value -> value.name.startsWith("lambda$releaseAllInput$"))
                        .anyMatch(
                                value ->
                                        callIndex(value, RUNTIME, "finishNativeInputCleanup")
                                                >= 0));
        MethodNode routed =
                method(
                        compiled(ROOT + "probe/mixin/MinecraftMixin"),
                        "minecraftProtocolProbe$virtualKeymappingConsumption");
        assertTrue(callIndex(routed, ROOT + "safety/AgentInputContext", "routed") >= 0);
        assertTrue(callIndex(routed, RUNTIME, "ensureExclusiveOwnerState") >= 0);
    }

    @Test
    void nativeReleaseReallyClearsQueuedClicksAsWellAsHeldState() throws Exception {
        ClassNode mapping = compiled("net/minecraft/client/KeyMapping");
        MethodNode release = method(mapping, "release");
        var clicks =
                fields(release).stream()
                        .filter(
                                value ->
                                        value.name.equals("clickCount")
                                                && value.getOpcode() == Opcodes.PUTFIELD)
                        .findFirst()
                        .orElseThrow();
        var zero = clicks.getPrevious();
        while (zero.getOpcode() < 0) zero = zero.getPrevious();
        assertEquals(Opcodes.ICONST_0, zero.getOpcode());
        int setDown = callIndex(release, "net/minecraft/client/KeyMapping", "setDown");
        if (setDown >= 0) {
            zero = release.instructions.get(setDown).getPrevious();
            while (zero.getOpcode() < 0) zero = zero.getPrevious();
            assertEquals(Opcodes.ICONST_0, zero.getOpcode());
            assertTrue(
                    fields(method(mapping, "setDown")).stream()
                            .anyMatch(
                                    value ->
                                            value.name.equals("isDown")
                                                    && value.getOpcode() == Opcodes.PUTFIELD));
        } else {
            var down =
                    fields(release).stream()
                            .filter(
                                    value ->
                                            value.name.equals("isDown")
                                                    && value.getOpcode() == Opcodes.PUTFIELD)
                            .findFirst()
                            .orElseThrow();
            zero = down.getPrevious();
            while (zero.getOpcode() < 0) zero = zero.getPrevious();
            assertEquals(Opcodes.ICONST_0, zero.getOpcode());
        }
    }

    @Test
    void actualNativeContextRefreshResetsAttributionForEpochAndTakeoverGeneration()
            throws Exception {
        ClassNode runtime = compiled(RUNTIME);
        MethodNode refresh = method(runtime, "refreshInputContext");
        assertTrue(callIndex(refresh, RUNTIME, "refreshNativeEntityAttackContext") >= 0);
        MethodNode reset = method(runtime, "refreshNativeEntityAttackContext");
        assertTrue(
                callIndex(reset, ROOT + "safety/AgentControlSession$Snapshot", "transitionSequence")
                        >= 0);
        var resets =
                fields(reset).stream()
                        .filter(
                                field ->
                                        field.name.equals("nativeEntityAttackCount")
                                                && field.getOpcode() == Opcodes.PUTFIELD)
                        .toList();
        assertEquals(1, resets.size());
        var zero = resets.get(0).getPrevious();
        while (zero.getOpcode() < 0) zero = zero.getPrevious();
        assertEquals(Opcodes.LCONST_0, zero.getOpcode());
        for (String binding :
                List.of(
                        "nativeEntityAttackContext",
                        "nativeEntityAttackControlGeneration",
                        "nativeEntityAttackWorld",
                        "nativeEntityAttackPlayer",
                        "nativeEntityAttackConnection"))
            assertTrue(
                    fields(reset).stream()
                            .anyMatch(
                                    field ->
                                            field.name.equals(binding)
                                                    && field.getOpcode() == Opcodes.PUTFIELD));
        assertFalse(
                calls(reset).stream()
                        .anyMatch(call -> call.owner.equals(ROOT + "core/ProtocolState")));
    }

    @Test
    void actualInputStatePublishesTheBoundedCounterAndHonestUnits() throws Exception {
        ClassNode runtime = compiled(RUNTIME);
        MethodNode producer =
                linked(runtime, method(runtime, "inputState")).stream()
                        .filter(
                                method ->
                                        callIndex(method, RUNTIME, "addNativeEntityAttackState")
                                                >= 0)
                        .findFirst()
                        .orElseThrow();
        assertTrue(callIndex(producer, RUNTIME, "addNativeEntityAttackState") >= 0);
        MethodNode projection = method(runtime, "addNativeEntityAttackState");
        assertTrue(
                literals(projection)
                        .containsAll(
                                Set.of(
                                        "nativeEntityAttackCount",
                                        "nativeEntityAttackScope",
                                        "nativeEntityAttackCounterUnits",
                                        "nativeEntityAttackContextId",
                                        "nativeEntityAttackContextGeneration",
                                        "nativeEntityAttackControlGeneration",
                                        "nativeEntityAttackLeaseActive",
                                        "nativeEntityAttackServerEffectConfirmed")));
        assertTrue(literals(projection).contains("native_multiplayer_game_mode_attack_return"));
        assertTrue(
                literals(projection)
                        .contains("native_entity_attack_invocations_not_damage_or_swing"));
        assertTrue(
                callIndex(projection, ROOT + "core/ProbeTransport", "nativeInputLeaseActive") >= 0);
        var instructions = projection.instructions.toArray();
        for (int index = 0; index < instructions.length; index++)
            if (instructions[index] instanceof LdcInsnNode literal
                    && literal.cst.equals("nativeEntityAttackServerEffectConfirmed")) {
                var flag = literal.getNext();
                while (flag.getOpcode() < 0) flag = flag.getNext();
                assertEquals(Opcodes.ICONST_0, flag.getOpcode());
            }
        assertFalse(
                calls(projection).stream()
                        .anyMatch(call -> call.owner.startsWith("net/minecraft/server/")));
    }

    @Test
    void actualPublicManifestDisclosesPassiveEntityAttackReturnObservation() throws Exception {
        ClassNode runtime = compiled(RUNTIME);
        MethodNode producer =
                linked(runtime, method(runtime, "hookManifest")).stream()
                        .filter(
                                method ->
                                        literals(method)
                                                .contains("world_entity_attack_consumption"))
                        .findFirst()
                        .orElseThrow();
        var arguments = new ArrayList<String>();
        boolean collecting = false, published = false;
        for (var instruction : producer.instructions) {
            if (instruction instanceof LdcInsnNode literal
                    && literal.cst.equals("world_entity_attack_consumption")) collecting = true;
            if (!collecting) continue;
            if (instruction instanceof LdcInsnNode literal && literal.cst instanceof String text)
                arguments.add(text);
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("com/google/gson/JsonArray")
                    && call.name.equals("add")) {
                published = true;
                break;
            }
        }
        assertEquals(
                List.of(
                        "world_entity_attack_consumption",
                        "MIXIN_INJECT",
                        "MultiPlayerGameMode.attack",
                        "RETURN",
                        "read_observation",
                        "input.mouse"),
                arguments);
        assertTrue(published);
    }

    private static List<MethodNode> linked(ClassNode owner, MethodNode method) {
        var result = new ArrayList<MethodNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic)
                for (Object value : dynamic.bsmArgs)
                    if (value instanceof Handle handle && handle.getOwner().equals(owner.name))
                        result.add(method(owner, handle.getName()));
        return result;
    }

    private static List<String> literals(MethodNode method) {
        var result = new ArrayList<String>();
        for (var instruction : method.instructions)
            if (instruction instanceof LdcInsnNode literal && literal.cst instanceof String text)
                result.add(text);
        return result;
    }

    private static List<FieldInsnNode> fields(MethodNode method) {
        var result = new ArrayList<FieldInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field) result.add(field);
        return result;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        return calls(method).stream()
                .filter(call -> call.owner.equals(owner) && call.name.equals(name))
                .mapToInt(method.instructions::indexOf)
                .findFirst()
                .orElse(-1);
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        var result = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result.stream()
                .filter(annotation -> annotation.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(name)) return annotation.values.get(index + 1);
        return null;
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input =
                NativeEntityAttackConsumptionTest.class.getResourceAsStream(
                        "/" + name + ".class")) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
