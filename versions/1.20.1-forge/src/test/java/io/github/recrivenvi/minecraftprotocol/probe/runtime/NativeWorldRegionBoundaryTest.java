package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Actual compiled native adapters and owner dispatch, without bootstrapping game registries. */
final class NativeWorldRegionBoundaryTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/";
    private static final String RUNTIME = ROOT + "probe/runtime/ForgeProbeRuntime";
    private static final String SNAPSHOT = ROOT + "probe/runtime/WorldRegionSnapshot";
    private static final String PAGES = ROOT + "safety/WorldRegionPages";
    private static final String EPOCH = ROOT + "safety/InputContextEpoch$Snapshot";

    @Test
    void regionReadRunsOnNativeOwnerAndCarriesCancellationAuthorizationAndTrustedOwner()
            throws Exception {
        var runtime = compiled(RUNTIME);
        var entry = method(runtime, "clientWorldRegion");
        assertTrue(callIndex(entry, "com/google/gson/JsonObject", "deepCopy") >= 0);
        assertTrue(callIndex(entry, "net/minecraft/client/Minecraft", "execute") >= 0);
        assertEquals(0, entry.access & Opcodes.ACC_SYNCHRONIZED);
        var owner =
                callbacks(runtime, entry).stream()
                        .filter(callback -> callIndex(callback, PAGES, "read") >= 0)
                        .findFirst()
                        .orElseThrow();
        int refresh = callIndex(owner, RUNTIME, "refreshInputContext");
        assertTrue(refresh >= 0 && refresh < callIndex(owner, SNAPSHOT, "view"));
        assertTrue(callIndex(owner, "java/lang/Runnable", "run") < callIndex(owner, PAGES, "read"));
        var read = calls(owner, PAGES, "read");
        assertEquals(1, read.size());
        assertEquals(
                "(Lcom/google/gson/JsonObject;Ljava/lang/String;L"
                        + PAGES
                        + "$View;Ljava/lang/Runnable;)Lcom/google/gson/JsonObject;",
                read.get(0).desc);
        assertEquals(1, calls(owner, PAGES, "cancel").size());
        assertTrue(
                callIndex(owner, PAGES, "read")
                        < callIndex(owner, "java/util/concurrent/CompletableFuture", "complete"));
        var guard =
                callbacks(runtime, owner).stream()
                        .filter(callback -> callIndex(callback, "java/lang/Runnable", "run") >= 0)
                        .findFirst()
                        .orElseThrow();
        assertTrue(callIndex(guard, "java/util/concurrent/CompletableFuture", "isDone") >= 0);
        assertTrue(fieldIndex(guard, "shutdownStarted", Opcodes.GETFIELD) >= 0);
        assertTrue(callIndex(guard, "java/util/concurrent/atomic/AtomicBoolean", "get") >= 0);
        for (var candidate : List.of(entry, owner, guard))
            assertFalse(
                    calls(candidate).stream()
                            .anyMatch(
                                    call ->
                                            call.owner.equals(ROOT + "safety/AgentControlSession")
                                                    || call.owner.startsWith(
                                                            "net/minecraft/server/")));
    }

    @Test
    void nativeCellRequestsOnlyAnAlreadyLoadedChunkAndNeverSerializesInternalData()
            throws Exception {
        var nativeView = compiled(SNAPSHOT + "$1");
        var cell = method(nativeView, "cell");
        var chunks = calls(cell).stream().filter(call -> call.name.equals("getChunk")).toList();
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).desc.endsWith(";Z)Lnet/minecraft/world/level/chunk/LevelChunk;"));
        assertEquals(
                Opcodes.ICONST_0,
                previous(chunks.get(0)).getOpcode(),
                "Required/loading must be false at the actual native invocation");
        assertTrue(
                java.util.Arrays.stream(cell.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.owner.equals(
                                                        "net/minecraft/world/level/chunk/ChunkStatus")
                                                && field.name.equals("FULL")));
        int missing = callIndex(cell, PAGES + "$Cell", "unloaded");
        assertTrue(missing >= 0);
        assertTrue(
                missing
                        < calls(cell).stream()
                                .filter(call -> call.name.equals("getBlockState"))
                                .mapToInt(cell.instructions::indexOf)
                                .findFirst()
                                .orElseThrow());
        var snapshot = compiled(SNAPSHOT);
        var project = method(snapshot, "project");
        assertTrue(calls(project).stream().anyMatch(call -> call.name.equals("getProperties")));
        assertEquals(1, calls(project, PAGES + "$Cell", "known").size());
        var property = method(snapshot, "propertyValue");
        assertTrue(calls(property).stream().anyMatch(call -> call.name.equals("getValue")));
        assertTrue(
                calls(property).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(
                                                        "net/minecraft/world/level/block/state/properties/Property")
                                                && call.name.equals("getName")
                                                && call.desc.equals(
                                                        "(Ljava/lang/Comparable;)Ljava/lang/String;")));
        for (var type : List.of(snapshot, nativeView))
            for (var candidate : type.methods)
                for (var call : calls(candidate)) {
                    assertFalse(call.owner.startsWith("net/minecraft/server/"));
                    assertFalse(call.owner.startsWith("net/minecraft/nbt/"));
                    assertFalse(
                            List.of(
                                            "getBlockEntity",
                                            "save",
                                            "saveWithoutId",
                                            "load",
                                            "getSingleplayerServer")
                                    .contains(call.name),
                            call.name);
                }
    }

    @Test
    void nativeContextRequiresTheExactLiveTupleAndNativeLocationWithoutTrustingRequestData()
            throws Exception {
        var context = method(compiled(SNAPSHOT + "$1"), "context");
        assertTrue(callIndex(context, EPOCH, "worldAvailable") >= 0);
        assertTrue(callIndex(context, EPOCH, "opaqueId") >= 0);
        assertTrue(callIndex(context, "net/minecraft/client/Minecraft", "getConnection") >= 0);
        assertTrue(callIndex(context, "net/minecraft/network/Connection", "isConnected") >= 0);
        assertTrue(callIndex(context, "net/minecraft/resources/ResourceKey", "location") >= 0);
        assertTrue(callIndex(context, "java/util/function/LongSupplier", "getAsLong") >= 0);
        long identityChecks =
                java.util.Arrays.stream(context.instructions.toArray())
                        .filter(
                                instruction ->
                                        instruction.getOpcode() == Opcodes.IF_ACMPNE
                                                || instruction.getOpcode() == Opcodes.IF_ACMPEQ)
                        .count();
        assertTrue(identityChecks >= 3, "Level/player/connection all need actual reference checks");
    }

    @Test
    void clientTickPublishesOneActualPoseWithVelocityAfterRefreshingItsContext() throws Exception {
        var tick = method(compiled(RUNTIME), "tick");
        String transport = ROOT + "core/ProbeTransport";
        assertEquals(1, calls(tick, transport, "publishPlayerState").size());
        int publish = callIndex(tick, transport, "publishPlayerState");
        int refresh = -1;
        for (int index = 0; index < publish; index++)
            if (tick.instructions.get(index) instanceof MethodInsnNode call
                    && call.owner.equals(RUNTIME)
                    && call.name.equals("refreshInputContext")) refresh = index;
        assertTrue(refresh >= 0 && refresh < publish);
        assertTrue(fieldIndex(tick, "clientTick", Opcodes.PUTFIELD) < refresh);
        assertTrue(callIndex(tick, EPOCH, "worldAvailable") > refresh);
        for (String name :
                List.of(
                        "getDeltaMovement",
                        "getX",
                        "getY",
                        "getZ",
                        "getYRot",
                        "getXRot",
                        "onGround"))
            assertTrue(calls(tick).stream().anyMatch(call -> call.name.equals(name)), name);
        for (String component : List.of("x", "y", "z"))
            assertTrue(
                    java.util.Arrays.stream(tick.instructions.toArray())
                            .anyMatch(
                                    instruction ->
                                            instruction instanceof FieldInsnNode field
                                                    && field.owner.equals(
                                                            "net/minecraft/world/phys/Vec3")
                                                    && field.name.equals(component)
                                                    && field.getOpcode() == Opcodes.GETFIELD));
        var sample = calls(tick, ROOT + "core/PlayerStateStream$Sample", "<init>");
        assertEquals(1, sample.size());
        assertEquals("(JJDDDDDZDDD)V", sample.get(0).desc);
    }

    @Test
    void centralOwnerRefreshPrecedesSupplierAndShutdownClosesPagerAfterTransport()
            throws Exception {
        var runtime = compiled(RUNTIME);
        var entry = method(runtime, "onClient");
        var owner =
                callbacks(runtime, entry).stream()
                        .filter(
                                callback ->
                                        callIndex(callback, "java/util/function/Supplier", "get")
                                                >= 0)
                        .findFirst()
                        .orElseThrow();
        int refresh = callIndex(owner, RUNTIME, "refreshInputContext");
        assertTrue(
                refresh >= 0 && refresh < callIndex(owner, "java/util/function/Supplier", "get"));
        assertEquals(2, calls(owner, "java/util/concurrent/CompletableFuture", "isDone").size());
        var shutdown = method(runtime, "shutdown");
        assertTrue(
                callIndex(shutdown, ROOT + "core/ProbeTransport", "close")
                        < callIndex(shutdown, PAGES, "close"));
        assertTrue(callIndex(shutdown, PAGES, "close") >= 0);
        assertEquals(0, shutdown.access & Opcodes.ACC_SYNCHRONIZED);
        assertTrue(
                java.util.Arrays.stream(shutdown.instructions.toArray())
                        .noneMatch(instruction -> instruction.getOpcode() == Opcodes.MONITORENTER));
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        var previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        assertNotNull(previous);
        return previous;
    }

    private static List<MethodNode> callbacks(ClassNode type, MethodNode method) {
        var result = new ArrayList<MethodNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic)
                for (var argument : dynamic.bsmArgs)
                    if (argument instanceof Handle handle && handle.getOwner().equals(type.name))
                        result.add(
                                type.methods.stream()
                                        .filter(
                                                candidate ->
                                                        candidate.name.equals(handle.getName())
                                                                && candidate.desc.equals(
                                                                        handle.getDesc()))
                                        .findFirst()
                                        .orElseThrow());
        return result;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        return calls(method).stream()
                .filter(call -> call.owner.equals(owner) && call.name.equals(name))
                .toList();
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        return calls(method, owner, name).stream()
                .mapToInt(method.instructions::indexOf)
                .findFirst()
                .orElse(-1);
    }

    private static int fieldIndex(MethodNode method, String name, int opcode) {
        for (int index = 0; index < method.instructions.size(); index++)
            if (method.instructions.get(index) instanceof FieldInsnNode field
                    && field.name.equals(name)
                    && field.getOpcode() == opcode) return index;
        return -1;
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input =
                NativeWorldRegionBoundaryTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return type;
        }
    }
}
