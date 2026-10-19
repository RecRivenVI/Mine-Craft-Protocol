package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.WorldPlacementReceipt;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class WorldPlacementReceiptTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/";
    private static final String RUNTIME = ROOT + "probe/runtime/";
    private static final String TARGETING = RUNTIME + "WorldTargeting";
    private static final String RECEIPT = ROOT + "safety/WorldPlacementReceipt";
    private static final String RESULT = "net/minecraft/world/InteractionResult";

    @Test
    void nativeRejectionIsExplicitAndASecondGuardPassCannotReplaceTheReceipt() {
        var receipt = new WorldPlacementReceipt();
        JsonObject air = state("minecraft:air");
        assertTrue(receipt.begin(air, "minecraft:oak_door"));
        assertTrue(receipt.pending());
        assertTrue(receipt.record("fail", false, air));
        assertFalse(receipt.pending());
        JsonObject first = receipt.snapshot();
        assertEquals("native_rejected", first.get("placementOutcome").getAsString());
        assertEquals("native_use_on_failed", first.get("reason").getAsString());
        assertEquals("fail", first.get("nativeUseOnResult").getAsString());
        assertFalse(first.get("nativeConsumesAction").getAsBoolean());
        assertFalse(first.get("clientPlacementPredicted").getAsBoolean());
        assertFalse(first.get("effectConfirmed").getAsBoolean());
        assertFalse(first.get("serverValidationObserved").getAsBoolean());
        assertFalse(receipt.record("pass", false, air));
        assertFalse(receipt.begin(air, "minecraft:stone"));
        assertEquals(first, receipt.snapshot());
    }

    @Test
    void unconsumedNativePassHasAReasonAndDoesNotClaimPlacement() {
        var receipt = receipt(state("minecraft:air"), "minecraft:stone");
        receipt.record("pass", false, state("minecraft:air"));
        JsonObject result = receipt.snapshot();
        assertTrue(result.get("nativeUseOnResultObserved").getAsBoolean());
        assertEquals("native_not_consumed", result.get("placementOutcome").getAsString());
        assertEquals("native_use_on_did_not_consume_action", result.get("reason").getAsString());
        assertFalse(result.get("clientDestinationChanged").getAsBoolean());
        assertFalse(result.get("clientPlacementPredicted").getAsBoolean());
    }

    @Test
    void consumedInteractionWithoutDestinationChangeIsNotPredictedPlacement() {
        var receipt = receipt(state("minecraft:air"), "minecraft:stone");
        receipt.record("success", true, state("minecraft:air"));
        JsonObject result = receipt.snapshot();
        assertEquals("native_consumed_not_confirmed", result.get("placementOutcome").getAsString());
        assertTrue(result.get("nativeConsumesAction").getAsBoolean());
        assertFalse(result.get("clientDestinationChanged").getAsBoolean());
        assertFalse(result.get("clientPlacementPredicted").getAsBoolean());
        assertFalse(result.get("effectConfirmed").getAsBoolean());
    }

    @Test
    void matchingNativeDestinationChangeIsOnlyAClientPrediction() {
        var receipt = receipt(state("minecraft:air"), "minecraft:red_bed");
        JsonObject bed = state("minecraft:red_bed");
        bed.getAsJsonObject("properties").addProperty("part", "foot");
        bed.getAsJsonObject("properties").addProperty("facing", "north");
        receipt.record("success", true, bed);
        JsonObject result = receipt.snapshot();
        assertEquals("client_placement_predicted", result.get("placementOutcome").getAsString());
        assertTrue(result.get("clientDestinationChanged").getAsBoolean());
        assertTrue(result.get("clientPlacementPredicted").getAsBoolean());
        assertFalse(result.get("effectConfirmed").getAsBoolean());
        assertFalse(result.get("serverValidationObserved").getAsBoolean());
        assertEquals(bed, result.getAsJsonObject("placementState").getAsJsonObject("after"));
        assertFalse(result.has("pair"), "No block-specific partner coordinates are invented");
    }

    @Test
    void fullStatePropertiesDetectSameBlockSlabMerging() {
        JsonObject bottom = state("minecraft:oak_slab"), merged = state("minecraft:oak_slab");
        bottom.getAsJsonObject("properties").addProperty("type", "bottom");
        merged.getAsJsonObject("properties").addProperty("type", "double");
        bottom.getAsJsonObject("properties").addProperty("waterlogged", "false");
        merged.getAsJsonObject("properties").addProperty("waterlogged", "false");
        var receipt = receipt(bottom, "minecraft:oak_slab");
        receipt.record("consume", true, merged);
        JsonObject result = receipt.snapshot();
        assertTrue(result.get("clientDestinationChanged").getAsBoolean());
        assertTrue(result.get("clientPlacementPredicted").getAsBoolean());
        assertEquals(bottom, result.getAsJsonObject("placementState").getAsJsonObject("before"));
        assertEquals(merged, result.getAsJsonObject("placementState").getAsJsonObject("after"));
    }

    @Test
    void anotherBlockChangeOrUnconsumedChangeDoesNotBecomeAnAcceptedPlacement() {
        for (boolean consumed : List.of(false, true)) {
            var receipt = receipt(state("minecraft:air"), "minecraft:stone");
            receipt.record(consumed ? "success" : "pass", consumed, state("minecraft:water"));
            JsonObject result = receipt.snapshot();
            assertTrue(result.get("clientDestinationChanged").getAsBoolean());
            assertFalse(result.get("clientPlacementPredicted").getAsBoolean());
            assertFalse(result.get("effectConfirmed").getAsBoolean());
        }
        var rejected = receipt(state("minecraft:air"), "minecraft:stone");
        rejected.record("fail", false, state("minecraft:stone"));
        assertEquals("native_rejected", rejected.snapshot().get("placementOutcome").getAsString());
        assertTrue(rejected.snapshot().get("clientDestinationChanged").getAsBoolean());
        assertFalse(rejected.snapshot().get("clientPlacementPredicted").getAsBoolean());
    }

    @Test
    void receiptOwnsImmutableSnapshotsAndAnUnstartedCallbackCannotInventEvidence() {
        var receipt = new WorldPlacementReceipt();
        assertFalse(receipt.record("success", true, state("minecraft:stone")));
        assertFalse(receipt.snapshot().get("nativeUseOnResultObserved").getAsBoolean());
        assertEquals(
                "native_result_not_observed",
                receipt.snapshot().get("placementOutcome").getAsString());
        JsonObject before = state("minecraft:air"), after = state("minecraft:stone");
        assertTrue(receipt.begin(before, "minecraft:stone"));
        before.addProperty("block", "minecraft:water");
        assertTrue(receipt.record("success", true, after));
        after.addProperty("block", "minecraft:dirt");
        JsonObject snapshot = receipt.snapshot();
        assertEquals(
                "minecraft:air",
                snapshot.getAsJsonObject("placementState")
                        .getAsJsonObject("before")
                        .get("block")
                        .getAsString());
        assertEquals(
                "minecraft:stone",
                snapshot.getAsJsonObject("placementState")
                        .getAsJsonObject("after")
                        .get("block")
                        .getAsString());
        snapshot.addProperty("placementOutcome", "altered");
        assertEquals(
                "client_placement_predicted",
                receipt.snapshot().get("placementOutcome").getAsString());
    }

    @Test
    void productionMixinObservesTheActualReturnAndKeepsTheExistingHeadGuard() throws Exception {
        ClassNode mixin = compiled(ROOT + "probe/mixin/WorldGestureMixin");
        MethodNode observer = method(mixin, "minecraftProtocolProbe$useOnResult");
        AnnotationNode injection =
                annotation(observer, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("useItemOn"), value(injection, "method"));
        assertEquals(
                "RETURN",
                value((AnnotationNode) ((List<?>) value(injection, "at")).get(0), "value"));
        assertNotEquals(Boolean.TRUE, value(injection, "cancellable"));
        List<MethodInsnNode> calls = calls(observer);
        assertEquals(2, calls.size());
        assertEquals("getReturnValue", calls.get(0).name);
        assertEquals(RUNTIME + "NeoForgeProbeRuntime", calls.get(1).owner);
        assertEquals("semanticUseOnResult", calls.get(1).name);
        assertEquals(
                "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;L"
                        + RESULT
                        + ";)V",
                calls.get(1).desc);
        assertEquals(
                -1,
                callIndex(observer, RUNTIME + "NeoForgeProbeRuntime", "allowSemanticWorldInput"));
        MethodNode guard = method(mixin, "minecraftProtocolProbe$guardUseOn");
        AnnotationNode head = annotation(guard, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals("HEAD", value((AnnotationNode) ((List<?>) value(head, "at")).get(0), "value"));
        assertEquals(Boolean.TRUE, value(head, "cancellable"));
        assertTrue(
                callIndex(guard, RUNTIME + "NeoForgeProbeRuntime", "allowSemanticWorldInput") >= 0);
        assertTrue(
                callIndex(
                                guard,
                                "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
                                "setReturnValue")
                        >= 0);
        method(compiled("net/minecraft/client/multiplayer/MultiPlayerGameMode"), "useItemOn");
    }

    @Test
    void productionObserverIsPendingAndNativeIdentityBoundWithoutRepeatingPlacementOrInput()
            throws Exception {
        MethodNode observer = method(compiled(TARGETING), "nativeUseOnResult");
        assertTrue(callIndex(observer, RECEIPT, "pending") >= 0);
        assertTrue(callIndex(observer, "net/minecraft/client/Minecraft", "isSameThread") >= 0);
        assertTrue(callIndex(observer, TARGETING, "matches") >= 0);
        assertTrue(callIndex(observer, RECEIPT, "record") >= 0);
        for (String identity : List.of("world", "player", "camera"))
            assertTrue(
                    fields(observer).stream()
                            .anyMatch(
                                    field ->
                                            field.owner.equals(TARGETING + "$Target")
                                                    && field.name.equals(identity)));
        assertTrue(
                fields(observer).stream()
                        .anyMatch(
                                field ->
                                        field.owner.equals("net/minecraft/world/InteractionHand")
                                                && field.name.equals("MAIN_HAND")));
        MethodInsnNode chunk =
                calls(observer).stream()
                        .filter(call -> call.name.equals("getChunk"))
                        .findFirst()
                        .orElseThrow();
        var flag = chunk.getPrevious();
        while (flag.getOpcode() < 0) flag = flag.getPrevious();
        assertEquals(
                Opcodes.ICONST_0, flag.getOpcode(), "Receipt reads must not request chunk loading");
        for (MethodInsnNode call : calls(observer)) {
            assertFalse(
                    List.of(
                                    "valid",
                                    "validate",
                                    "getStateForPlacement",
                                    "getPlacementState",
                                    "useItemOn",
                                    "useOn",
                                    "place",
                                    "setBlock",
                                    "setBlockAndUpdate",
                                    "send",
                                    "setYRot",
                                    "setXRot")
                            .contains(call.name));
            assertFalse(call.owner.startsWith("net/minecraft/server/"));
        }
        for (String nativeConstant :
                List.of("FAIL", "PASS", "SUCCESS", "SUCCESS_SERVER", "CONSUME"))
            assertTrue(
                    fields(observer).stream()
                            .anyMatch(
                                    field ->
                                            field.owner.equals(RESULT)
                                                    && field.name.equals(nativeConstant)));
        assertTrue(callIndex(observer, RESULT, "consumesAction") >= 0);
    }

    @Test
    void actualPlacementGuardStartsTheReceiptAndProgressPublishesItsTypedProjection()
            throws Exception {
        ClassNode targeting = compiled(TARGETING);
        MethodNode permit = method(targeting, "permit");
        assertTrue(callIndex(permit, TARGETING, "placement") >= 0);
        assertTrue(callIndex(permit, RECEIPT, "begin") >= 0);
        assertTrue(callIndex(method(targeting, "progress"), RECEIPT, "snapshot") >= 0);
        MethodNode state = method(targeting, "placementState");
        assertTrue(
                callIndex(
                                state,
                                "net/minecraft/world/level/block/state/BlockState",
                                "getProperties")
                        >= 0);
        assertTrue(callIndex(state, TARGETING, "placementProperty") >= 0);
        MethodNode property = method(targeting, "placementProperty");
        assertTrue(
                callIndex(property, "net/minecraft/world/level/block/state/BlockState", "getValue")
                        >= 0);
        assertEquals(
                2, calls(property).stream().filter(call -> call.name.equals("getName")).count());
        for (MethodNode candidate : List.of(method(targeting, "placement"), state, property))
            assertFalse(
                    calls(candidate).stream()
                            .anyMatch(
                                    call ->
                                            List.of(
                                                            "getStateForPlacement",
                                                            "getPlacementState",
                                                            "placeBlock",
                                                            "place")
                                                    .contains(call.name)),
                    "The diagnostic path must not evaluate or execute native placement again");
        MethodNode adapter =
                method(compiled(RUNTIME + "NeoForgeProbeRuntime"), "semanticUseOnResult");
        assertTrue(callIndex(adapter, TARGETING, "nativeUseOnResult") >= 0);
    }

    private static WorldPlacementReceipt receipt(JsonObject before, String block) {
        var result = new WorldPlacementReceipt();
        assertTrue(result.begin(before, block));
        return result;
    }

    private static JsonObject state(String block) {
        JsonObject state = new JsonObject();
        state.addProperty("block", block);
        state.add("properties", new JsonObject());
        return state;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static List<FieldInsnNode> fields(MethodNode method) {
        var result = new ArrayList<FieldInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field) result.add(field);
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
                WorldPlacementReceiptTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
