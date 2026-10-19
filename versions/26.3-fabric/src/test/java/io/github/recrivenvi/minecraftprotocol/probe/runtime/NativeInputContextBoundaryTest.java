package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class NativeInputContextBoundaryTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/";
    private static final String RUNTIME = ROOT + "probe/runtime/FabricProbeRuntime";
    private static final String MIXIN = ROOT + "probe/mixin/";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String MINECRAFT_ARGUMENT = "(L" + MINECRAFT + ";)";

    @Test
    void registeredTickHeadFenceCallsTheActualRuntimeBeforeNativeTickWork() throws Exception {
        assertRegisteredHeadFence(
                "MinecraftMixin", MINECRAFT, "tick", "beforeClientInput", MINECRAFT_ARGUMENT + "V");
        var entry = method(compiled(RUNTIME), "beforeClientInput", MINECRAFT_ARGUMENT + "V");
        assertEquals(1, calls(entry, RUNTIME, "refreshInputContext").size());
        assertTrue(
                fieldIndex(entry, "minecraft", Opcodes.PUTSTATIC) < 0,
                "The Runtime client belongs to its instance, not a second static context");
        int client = fieldIndex(entry, "minecraft", Opcodes.PUTFIELD);
        assertTrue(client >= 0 && client < callIndex(entry, RUNTIME, "refreshInputContext"));
    }

    @Test
    void registeredPlayerHeadFenceRefreshesAgainBeforeTheNativeMovementConsumer() throws Exception {
        String player = "net/minecraft/client/player/LocalPlayer";
        assertRegisteredHeadFence("LocalPlayerMixin", player, "aiStep", "beforePlayerInput", "()V");
        var nativeStep = method(compiled(player), "aiStep", "()V");
        assertTrue(
                callIndex(nativeStep, "net/minecraft/client/player/ClientInput", "tick") >= 0,
                "The fenced native method must really consume player input");
        var entry = method(compiled(RUNTIME), "beforePlayerInput", "()V");
        int current = callIndex(entry, MINECRAFT, "getInstance");
        int refresh = callIndex(entry, RUNTIME, "beforeClientInput");
        assertTrue(current >= 0 && refresh > current);
        assertEquals(
                MINECRAFT_ARGUMENT + "V", calls(entry, RUNTIME, "beforeClientInput").get(0).desc);
    }

    @Test
    void ownerDispatchRefreshesOutsideAndBeforeTheTakeoverMonitor() throws Exception {
        var runtime = compiled(RUNTIME);
        var ensure = method(runtime, "ensureExclusiveOwnerState", "()V");
        int refresh = callIndex(ensure, RUNTIME, "refreshInputContext");
        assertTrue(refresh >= 0 && refresh < callIndex(ensure, RUNTIME, "controlPresenceChanged"));
        assertNoMonitor(ensure);
        var dispatch =
                method(
                        runtime,
                        "onControlledClient",
                        "(Ljava/util/function/Supplier;Z)Ljava/util/concurrent/CompletableFuture;");
        var ownerCallbacks = linkedCallbacks(runtime, dispatch);
        var owner =
                ownerCallbacks.stream()
                        .filter(
                                callback ->
                                        callIndex(callback, RUNTIME, "refreshInputContext") >= 0)
                        .findFirst()
                        .orElseThrow();
        assertTrue(callIndex(dispatch, RUNTIME, "onClient") >= 0);
        int before = callIndex(owner, RUNTIME, "refreshInputContext");
        int takeover = callIndex(owner, ROOT + "safety/AgentControlSession", "withTakeover");
        assertTrue(before >= 0 && takeover > before);
        assertTrue(callIndex(owner, RUNTIME, "ensureExclusiveOwnerState") < takeover);
        assertNoMonitor(owner);
        var protectedCallbacks = linkedCallbacks(runtime, owner);
        assertFalse(protectedCallbacks.isEmpty(), "The real withTakeover body must be inspected");
        for (var callback : protectedCallbacks) {
            assertEquals(-1, callIndex(callback, RUNTIME, "refreshInputContext"));
            assertTrue(
                    calls(callback).stream()
                            .noneMatch(call -> call.owner.equals(ROOT + "core/ProtocolState")),
                    "Native input cannot acquire ProtocolState while owning the takeover monitor");
        }
    }

    @Test
    void nativeIdentityRefreshNotifiesTheInputFenceBeforeSynchronousDiscardAndPublication()
            throws Exception {
        var refresh = method(compiled(RUNTIME), "refreshInputContext", MINECRAFT_ARGUMENT + "V");
        assertTrue(callIndex(refresh, MINECRAFT, "getConnection") >= 0);
        assertTrue(
                callIndex(
                                refresh,
                                "net/minecraft/client/multiplayer/ClientPacketListener",
                                "getConnection")
                        >= 0);
        assertTrue(callIndex(refresh, "net/minecraft/network/Connection", "isConnected") >= 0);
        assertTrue(fieldIndex(refresh, "level", Opcodes.GETFIELD) >= 0);
        assertTrue(fieldIndex(refresh, "player", Opcodes.GETFIELD) >= 0);
        assertEquals(
                3L,
                java.util.Arrays.stream(refresh.instructions.toArray())
                        .filter(instruction -> instruction.getOpcode() == Opcodes.ACONST_NULL)
                        .count(),
                "Disconnected native identities must be replaced with null, not stale objects");
        var observe = calls(refresh, ROOT + "safety/InputContextEpoch", "observe");
        assertEquals(1, observe.size());
        assertEquals(
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)L"
                        + ROOT
                        + "safety/InputContextEpoch$Snapshot;",
                observe.get(0).desc);
        int notify = callIndex(refresh, ROOT + "core/ProbeTransport", "nativeInputContextChanged");
        int discard = callIndex(refresh, RUNTIME, "discardContextInput");
        int published = fieldIndex(refresh, "publishedInputContext", Opcodes.PUTFIELD);
        assertTrue(
                callIndex(refresh, ROOT + "safety/InputContextEpoch", "observe") < notify
                        && notify < discard
                        && discard < published,
                "Generation rejection precedes synchronous native cleanup and publication");
        assertTrue(notify >= 0);
        assertTrue(
                java.util.Arrays.stream(refresh.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction.getOpcode() == Opcodes.IF_ACMPEQ
                                                || instruction.getOpcode() == Opcodes.IF_ACMPNE),
                "Unchanged snapshots must be compared by identity");
        for (var instruction : refresh.instructions) {
            if (instruction instanceof FieldInsnNode field)
                assertFalse(
                        Set.of("screen", "gui", "screenRevision", "menuRevision")
                                .contains(field.name),
                        "Ordinary GUI navigation must not become a world/player context change");
            if (instruction instanceof MethodInsnNode call)
                assertFalse(
                        Set.of("getGuiScale", "screen", "refreshScreen", "refreshMenu")
                                .contains(call.name));
        }
        assertNoMonitor(refresh);
    }

    @Test
    void contextDiscardInvokesNativeReleaseOnlyForTrackedMappingsAndTheOwnedChatKey()
            throws Exception {
        var runtime = compiled(RUNTIME);
        var discard =
                method(
                        runtime,
                        "discardContextInput",
                        MINECRAFT_ARGUMENT + "Lcom/google/gson/JsonObject;");
        String accessor = MIXIN + "KeyBindingUiAccessor";
        assertEquals(1, calls(discard, accessor, "minecraftProtocolProbe$release").size());
        assertTrue(callIndex(discard, "java/util/Map", "containsKey") >= 0);
        assertTrue(callIndex(discard, ROOT + "probe/runtime/NativeInputMapping", "wireKey") >= 0);
        assertTrue(fieldIndex(discard, "pressedKeys", Opcodes.GETFIELD) >= 0);
        assertTrue(fieldIndex(discard, "pressedButtons", Opcodes.GETFIELD) >= 0);
        int buttonMatch = callIndex(discard, "java/util/stream/Stream", "anyMatch");
        int release = callIndex(discard, accessor, "minecraftProtocolProbe$release");
        var guards = new ArrayList<JumpInsnNode>();
        for (int index = buttonMatch + 1; index < release; index++)
            if (discard.instructions.get(index) instanceof JumpInsnNode branch) guards.add(branch);
        assertTrue(guards.size() >= 3);
        var releaseGuards = guards.subList(guards.size() - 3, guards.size());
        assertEquals(
                List.of(Opcodes.IFNE, Opcodes.IFNE, Opcodes.IF_ACMPNE),
                releaseGuards.stream().map(AbstractInsnNode::getOpcode).toList(),
                "Native release must remain guarded by owned key/button/chat identity");
        assertSame(releaseGuards.get(0).label, releaseGuards.get(1).label);
        int ownedRelease = discard.instructions.indexOf(releaseGuards.get(0).label);
        assertTrue(
                ownedRelease > discard.instructions.indexOf(releaseGuards.get(2))
                        && ownedRelease < release,
                "Both tracked-input conditions must branch to the guarded native release");
        assertTrue(fieldIndex(discard, "commandChatOpening", Opcodes.GETFIELD) > buttonMatch);
        assertTrue(discard.instructions.indexOf(releaseGuards.get(2).label) > release);
        var buttonPredicate = linkedCallbacks(runtime, discard);
        assertEquals(1, buttonPredicate.size());
        assertTrue(
                callIndex(
                                buttonPredicate.get(0),
                                ROOT + "probe/runtime/NativeInputMapping",
                                "button")
                        >= 0);
        assertTrue(
                callIndex(
                                buttonPredicate.get(0),
                                "com/mojang/blaze3d/platform/InputConstants$Key",
                                "getValue")
                        >= 0);
        var invoker = method(compiled(accessor), "minecraftProtocolProbe$release", "()V");
        assertEquals(
                "release",
                value(annotation(invoker, "Lorg/spongepowered/asm/mixin/gen/Invoker;"), "value"));
    }

    @Test
    void synchronousDiscardClearsOnlyOwnedStateWithoutDispatchingIntoTheReplacementScreen()
            throws Exception {
        var runtime = compiled(RUNTIME);
        var discard =
                method(
                        runtime,
                        "discardContextInput",
                        MINECRAFT_ARGUMENT + "Lcom/google/gson/JsonObject;");
        assertTrue(callIndex(discard, ROOT + "probe/runtime/WorldTargeting", "end") >= 0);
        assertTrue(callIndex(discard, ROOT + "safety/AgentPointer", "releaseButtons") >= 0);
        assertTrue(callIndex(discard, RUNTIME, "clearMouseButtonState") >= 0);
        for (String field :
                List.of(
                        "pressedKeys",
                        "pressedButtons",
                        "buttonScreens",
                        "buttonFrames",
                        "commandChat",
                        "commandFeedback")) assertFieldCleared(discard, field);
        assertTrue(fieldIndex(discard, "commandChatOpening", Opcodes.PUTFIELD) >= 0);
        var mouse = method(runtime, "clearMouseButtonState", MINECRAFT_ARGUMENT + "V");
        for (String setter :
                List.of(
                        "setLeftPressed",
                        "setRightPressed",
                        "setMiddlePressed",
                        "setActiveButton",
                        "setMousePressedTime"))
            assertEquals(
                    1,
                    calls(mouse, MIXIN + "MouseHandlerAccessor", "minecraftProtocolProbe$" + setter)
                            .size());
        assertTrue(fieldIndex(mouse, "pressedButtons", Opcodes.GETFIELD) >= 0);
        assertEquals(1, calls(mouse, "net/minecraft/client/KeyMapping", "set").size());
        var cleanup = new ArrayList<>(linkedCallbacks(runtime, discard));
        cleanup.add(discard);
        cleanup.add(mouse);
        for (var callback : cleanup) {
            assertNoMonitor(callback);
            for (var call : calls(callback)) {
                assertFalse(call.owner.startsWith("net/minecraft/client/gui/screens/"));
                assertFalse(call.owner.equals(MIXIN + "KeyboardHandlerInvoker"));
                assertFalse(call.owner.equals(MIXIN + "MouseHandlerInvoker"));
                assertFalse(call.owner.equals(ROOT + "safety/AgentInputContext"));
                assertFalse(
                        call.name.equals("releaseAll"), "Do not globally release human mappings");
                assertFalse(call.owner.startsWith("org/lwjgl/"));
                assertFalse(call.owner.equals(ROOT + "core/ProtocolState"));
            }
        }
    }

    @Test
    void actualPublicManifestDisclosesBothNativeHeadFences() throws Exception {
        var runtime = compiled(RUNTIME);
        var producer =
                linkedCallbacks(
                                runtime,
                                method(
                                        runtime,
                                        "hookManifest",
                                        "()Ljava/util/concurrent/CompletableFuture;"))
                        .stream()
                        .filter(callback -> literals(callback).contains("input_context_tick"))
                        .findFirst()
                        .orElseThrow();
        assertHook(producer, "input_context_tick", "Minecraft.tick");
        assertHook(producer, "input_context_player", "LocalPlayer.aiStep");
    }

    @Test
    void nativeTickPublishesTypedPoseOnlyAfterItsOwnerContextRefresh() throws Exception {
        var tick = method(compiled(RUNTIME), "tick", MINECRAFT_ARGUMENT + "V");
        var refreshes = calls(tick, RUNTIME, "refreshInputContext");
        assertEquals(2, refreshes.size());
        int refresh = tick.instructions.indexOf(refreshes.get(1));
        int publish = callIndex(tick, ROOT + "core/ProbeTransport", "publishPlayerState");
        int advance = fieldIndex(tick, "clientTick", Opcodes.PUTFIELD);
        assertTrue(advance >= 0 && advance < refresh);
        assertTrue(callIndex(tick, RUNTIME, "refreshMenu") < refresh);
        assertTrue(refresh < publish);
        assertEquals(1, calls(tick, ROOT + "core/PlayerStateStream$Sample", "<init>").size());
        assertEquals(
                "(JJDDDDDZDDD)V",
                calls(tick, ROOT + "core/PlayerStateStream$Sample", "<init>").get(0).desc);
        assertTrue(calls(tick).stream().anyMatch(call -> call.name.equals("getDeltaMovement")));
        assertTrue(calls(tick).stream().anyMatch(call -> call.name.equals("onGround")));
        assertTrue(callIndex(tick, "java/lang/System", "currentTimeMillis") > refresh);
        assertTrue(
                callIndex(tick, ROOT + "safety/InputContextEpoch$Snapshot", "worldAvailable")
                        > refresh);
        assertTrue(
                calls(tick).stream()
                        .noneMatch(
                                call ->
                                        Set.of(
                                                        "renderFrame",
                                                        "runTick",
                                                        "beforePresent",
                                                        "getFrameTimeNs")
                                                .contains(call.name)));
    }

    @Test
    void nativeRegionViewUsesOnlyTheSameConnectedClientAndDoesNotLoadMissingChunks()
            throws Exception {
        String view = ROOT + "probe/runtime/WorldRegionSnapshot$1";
        var context =
                method(
                        compiled(view),
                        "context",
                        "()L" + ROOT + "safety/WorldRegionPages$Context;");
        assertTrue(callIndex(context, "net/minecraft/network/Connection", "isConnected") >= 0);
        assertTrue(
                callIndex(context, ROOT + "safety/InputContextEpoch$Snapshot", "worldAvailable")
                        >= 0);
        assertTrue(fieldIndex(context, "level", Opcodes.GETFIELD) >= 0);
        assertTrue(fieldIndex(context, "player", Opcodes.GETFIELD) >= 0);
        assertTrue(callIndex(context, MINECRAFT, "getConnection") >= 0);
        var cell =
                method(compiled(view), "cell", "(III)L" + ROOT + "safety/WorldRegionPages$Cell;");
        var chunks = calls(cell, "net/minecraft/client/multiplayer/ClientChunkCache", "getChunk");
        assertEquals(1, chunks.size());
        assertEquals(
                "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
                chunks.get(0).desc);
        var required = chunks.get(0).getPrevious();
        while (required != null && required.getOpcode() < 0) required = required.getPrevious();
        assertNotNull(required);
        assertEquals(
                Opcodes.ICONST_0,
                required.getOpcode(),
                "Missing client chunks must never be loaded");
        assertEquals(1, calls(cell, ROOT + "safety/WorldRegionPages$Cell", "unloaded").size());
        assertEquals(
                1,
                calls(cell, "net/minecraft/world/level/chunk/LevelChunk", "getBlockState").size());
    }

    @Test
    void regionDispatchUsesNativeOwnerAuthorizationAndClosesItsPagesAtShutdown() throws Exception {
        var runtime = compiled(RUNTIME);
        var entry =
                method(
                        runtime,
                        "clientWorldRegion",
                        "(Ljava/lang/String;Lcom/google/gson/JsonObject;Ljava/lang/String;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;");
        assertEquals(1, calls(entry, MINECRAFT, "execute").size());
        var owner =
                linkedCallbacks(runtime, entry).stream()
                        .filter(
                                callback ->
                                        callIndex(
                                                        callback,
                                                        ROOT + "safety/WorldRegionPages",
                                                        "read")
                                                >= 0)
                        .findFirst()
                        .orElseThrow();
        int refresh = callIndex(owner, RUNTIME, "refreshInputContext");
        int view = callIndex(owner, ROOT + "probe/runtime/WorldRegionSnapshot", "view");
        assertTrue(refresh >= 0 && refresh < view);
        assertTrue(callIndex(owner, "java/lang/Runnable", "run") < view);
        assertEquals(1, calls(owner, ROOT + "safety/WorldRegionPages", "read").size());
        assertEquals(1, calls(owner, ROOT + "safety/WorldRegionPages", "cancel").size());
        assertEquals(
                1,
                calls(method(runtime, "shutdown", "()V"), ROOT + "safety/WorldRegionPages", "close")
                        .size());
        assertTrue(
                linkedCallbacks(runtime, owner).stream()
                        .anyMatch(
                                callback ->
                                        fieldIndex(callback, "shutdownStarted", Opcodes.GETFIELD)
                                                >= 0));
        var producer =
                linkedCallbacks(
                                runtime,
                                method(
                                        runtime,
                                        "capabilities",
                                        "()Ljava/util/concurrent/CompletableFuture;"))
                        .stream()
                        .filter(callback -> literals(callback).contains("world.user_region"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                literals(producer)
                        .containsAll(
                                List.of(
                                        "world.user_region",
                                        "bounded_cached_section_pages",
                                        "event.player_state",
                                        "explicit_client_tick_subscription")));
        assertTrue(calls(producer, "com/google/gson/JsonObject", "addProperty").size() > 4);
    }

    private static void assertRegisteredHeadFence(
            String mixin,
            String target,
            String nativeMethod,
            String runtimeMethod,
            String descriptor)
            throws Exception {
        try (var input =
                NativeInputContextBoundaryTest.class.getResourceAsStream(
                        "/minecraft_protocol_probe.client.mixins.json")) {
            assertNotNull(input);
            var config =
                    JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            assertTrue(
                    config.getAsJsonArray("client").asList().stream()
                            .anyMatch(entry -> entry.getAsString().equals(mixin)));
        }
        var type = compiled(MIXIN + mixin);
        assertEquals(
                List.of(Type.getObjectType(target)),
                value(annotation(type, "Lorg/spongepowered/asm/mixin/Mixin;"), "value"));
        var entry =
                type.methods.stream()
                        .filter(method -> method.name.equals("minecraftProtocolProbe$inputContext"))
                        .findFirst()
                        .orElseThrow();
        var injection = annotation(entry, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of(nativeMethod), value(injection, "method"));
        Object at = value(injection, "at");
        assertInstanceOf(List.class, at);
        assertEquals(1, ((List<?>) at).size());
        assertEquals("HEAD", value((AnnotationNode) ((List<?>) at).get(0), "value"));
        assertNotEquals(Boolean.TRUE, value(injection, "cancellable"));
        assertEquals(1, calls(entry).size());
        var call = calls(entry).get(0);
        assertEquals(RUNTIME, call.owner);
        assertEquals(runtimeMethod, call.name);
        assertEquals(descriptor, call.desc);
        method(compiled(target), nativeMethod, "()V");
    }

    private static void assertHook(MethodNode producer, String id, String target) {
        int start = -1;
        for (int index = 0; index < producer.instructions.size(); index++)
            if (producer.instructions.get(index) instanceof LdcInsnNode literal
                    && literal.cst.equals(id)) start = index;
        assertTrue(start >= 0, id);
        var arguments = new ArrayList<String>();
        int hook = -1;
        for (int index = start; index < producer.instructions.size(); index++) {
            var instruction = producer.instructions.get(index);
            if (instruction instanceof LdcInsnNode literal && literal.cst instanceof String text)
                arguments.add(text);
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(RUNTIME)
                    && call.name.equals("hook")) {
                hook = index;
                break;
            }
        }
        assertEquals(
                List.of(
                        id,
                        "MIXIN_INJECT",
                        target,
                        "HEAD",
                        "input_context_fence",
                        "input.context_epoch"),
                arguments);
        assertTrue(hook >= 0);
        boolean published = false;
        for (int index = hook + 1; index < producer.instructions.size(); index++) {
            var instruction = producer.instructions.get(index);
            if (instruction instanceof LdcInsnNode) break;
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("com/google/gson/JsonArray")
                    && call.name.equals("add")) {
                published = true;
                break;
            }
        }
        assertTrue(published, "A detached descriptor does not prove public manifest coverage");
    }

    private static void assertFieldCleared(MethodNode method, String name) {
        for (var instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode field)
                    || !field.owner.equals(RUNTIME)
                    || !field.name.equals(name)
                    || field.getOpcode() != Opcodes.GETFIELD) continue;
            var next = instruction.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (next instanceof MethodInsnNode call && call.name.equals("clear")) return;
        }
        fail("The tracked " + name + " collection must be cleared synchronously");
    }

    private static void assertNoMonitor(MethodNode method) {
        assertEquals(0, method.access & Opcodes.ACC_SYNCHRONIZED, method.name);
        for (var instruction : method.instructions)
            assertNotEquals(Opcodes.MONITORENTER, instruction.getOpcode(), method.name);
    }

    private static List<MethodNode> linkedCallbacks(ClassNode owner, MethodNode method) {
        var result = new ArrayList<MethodNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic)
                for (var argument : dynamic.bsmArgs)
                    if (argument instanceof Handle handle && handle.getOwner().equals(owner.name))
                        result.add(method(owner, handle.getName(), handle.getDesc()));
        return result;
    }

    private static List<String> literals(MethodNode method) {
        var result = new ArrayList<String>();
        for (var instruction : method.instructions)
            if (instruction instanceof LdcInsnNode literal && literal.cst instanceof String text)
                result.add(text);
        return result;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
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

    private static AnnotationNode annotation(ClassNode type, String descriptor) {
        var result = new ArrayList<AnnotationNode>();
        if (type.visibleAnnotations != null) result.addAll(type.visibleAnnotations);
        if (type.invisibleAnnotations != null) result.addAll(type.invisibleAnnotations);
        return result.stream()
                .filter(value -> value.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        var result = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result.stream()
                .filter(value -> value.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(name)) return annotation.values.get(index + 1);
        return null;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type.name + "." + name + descriptor));
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input =
                NativeInputContextBoundaryTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
