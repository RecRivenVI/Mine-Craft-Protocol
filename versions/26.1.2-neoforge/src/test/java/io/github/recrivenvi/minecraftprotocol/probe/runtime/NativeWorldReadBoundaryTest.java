package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class NativeWorldReadBoundaryTest {
    private static final String RUNTIME = "io/github/recrivenvi/minecraftprotocol/probe/runtime/";

    @Test
    void formalNativeSelectorRejectsIllegalRadiiBeforeCapturingAnyWorld() {
        assertEquals(0, Phase9ASpikeEngine.formalChunkRadius(new JsonObject()));
        for (String radius : List.of("0", "1", "2", "1.0")) {
            JsonObject request = request(radius);
            assertEquals(
                    request.getAsJsonObject("selector").get("chunkRadius").getAsInt(),
                    Phase9ASpikeEngine.formalChunkRadius(request));
        }
        for (String radius :
                List.of("-1", "3", "1.5", "2147483648", "\"1\"", "true", "null", "{}", "[]")) {
            JsonObject request = request(radius);
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> Phase9ASpikeEngine.formalChunkRadius(request));
            assertEquals(400, error.httpStatus());
            assertEquals("INVALID_DEEP_SELECTOR", error.code());
            try (var engine = new Phase9ASpikeEngine("26.1.2-neoforge")) {
                var captureError =
                        assertThrows(
                                ProtocolState.ProtocolException.class,
                                () -> engine.captureFormal(null, null, request));
                assertEquals("INVALID_DEEP_SELECTOR", captureError.code());
            }
        }
    }

    @Test
    void actualRuntimeValidatesBeforeEitherClientOrServerPerspectiveIsDispatched()
            throws Exception {
        MethodNode method =
                method(compiled(RUNTIME + "NeoForgeProbeRuntime"), "formalDeepObservation");
        List<MethodInsnNode> calls = calls(method);
        assertFalse(calls.isEmpty());
        assertEquals(RUNTIME + "Phase9ASpikeEngine", calls.get(0).owner);
        assertEquals("formalChunkRadius", calls.get(0).name);
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("playerState")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("onIntegratedServer")));
    }

    @Test
    void nativeItemAdapterReadsOnlyTheClientTrackedStackAndDoesNotSerializeIt() throws Exception {
        ClassNode snapshot = compiled(RUNTIME + "WorldClientSnapshot");
        List<ClassNode> classes = new ArrayList<>();
        classes.add(snapshot);
        for (var inner : snapshot.innerClasses)
            if (inner.name.startsWith(RUNTIME + "WorldClientSnapshot$"))
                classes.add(compiled(inner.name));
        List<MethodNode> candidateMethods =
                classes.stream().flatMap(type -> type.methods.stream()).toList();
        MethodNode projection =
                candidateMethods.stream()
                        .filter(
                                candidate ->
                                        calls(candidate).stream()
                                                .anyMatch(
                                                        call ->
                                                                call.owner.equals(
                                                                                "net/minecraft/world/entity/item/ItemEntity")
                                                                        && call.name.equals(
                                                                                "getItem")))
                        .findFirst()
                        .orElseThrow();
        List<AbstractInsnNode> instructions = Arrays.asList(projection.instructions.toArray());
        assertTrue(
                instructions.stream()
                        .filter(TypeInsnNode.class::isInstance)
                        .map(TypeInsnNode.class::cast)
                        .anyMatch(
                                instruction ->
                                        instruction.getOpcode()
                                                        == org.objectweb.asm.Opcodes.INSTANCEOF
                                                && instruction.desc.equals(
                                                        "net/minecraft/world/entity/item/ItemEntity")));
        for (String getter : List.of("isEmpty", "getItem", "getCount"))
            assertTrue(
                    calls(projection).stream()
                            .anyMatch(
                                    call ->
                                            call.owner.equals("net/minecraft/world/item/ItemStack")
                                                    && call.name.equals(getter)),
                    getter);
        assertTrue(
                instructions.stream()
                        .filter(FieldInsnNode.class::isInstance)
                        .map(FieldInsnNode.class::cast)
                        .anyMatch(
                                field ->
                                        field.owner.equals(
                                                        "net/minecraft/core/registries/BuiltInRegistries")
                                                && field.name.equals("ITEM")));
        assertFalse(
                calls(projection).stream()
                        .anyMatch(
                                call ->
                                        List.of("save", "saveWithoutId", "getComponents", "getTag")
                                                        .contains(call.name)
                                                || call.owner.startsWith("net/minecraft/server/")));
        assertTrue(
                calls(method(compiled(RUNTIME + "WorldClientSnapshot$1"), "entities")).stream()
                        .anyMatch(call -> call.name.equals("entitiesForRendering")));
        assertTrue(
                calls(method(snapshot, "capture")).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(
                                                        "io/github/recrivenvi/minecraftprotocol/safety/WorldClientModel")
                                                && call.name.equals("capture")));
    }

    @Test
    void nativeSlotOwnerUsesContainerIdentityWithoutReadingOrGuessingAnIndex() throws Exception {
        Container player = container(), menu = container();
        Slot same = new Slot(player, 37, 0, 0), other = new Slot(menu, 37, 0, 0);
        for (Object[] sample :
                List.of(
                        new Object[] {same, player, "player_inventory"},
                        new Object[] {other, player, "menu"},
                        new Object[] {new Slot(null, 37, 0, 0), player, "unknown"},
                        new Object[] {same, null, "unknown"})) {
            JsonObject node = new JsonObject();
            NeoForgeProbeRuntime.addSlotOwner(node, (Slot) sample[0], (Container) sample[1]);
            assertEquals(sample[2], node.get("slotOwner").getAsString());
        }
        assertTrue(
                calls(method(compiled(RUNTIME + "NeoForgeProbeRuntime"), "snapshotUiTree")).stream()
                        .anyMatch(call -> call.name.equals("addSlotOwner")));
    }

    private static Container container() {
        // A proxy resolves every return type and bootstraps legacy item registries.
        // Ownership only needs identity; any inventory access is a test failure.
        return new Container() {
            public int getContainerSize() {
                throw unexpectedContainerAccess();
            }

            public boolean isEmpty() {
                throw unexpectedContainerAccess();
            }

            public net.minecraft.world.item.ItemStack getItem(int slot) {
                throw unexpectedContainerAccess();
            }

            public net.minecraft.world.item.ItemStack removeItem(int slot, int count) {
                throw unexpectedContainerAccess();
            }

            public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
                throw unexpectedContainerAccess();
            }

            public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
                throw unexpectedContainerAccess();
            }

            public void setChanged() {
                throw unexpectedContainerAccess();
            }

            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                throw unexpectedContainerAccess();
            }

            public void clearContent() {
                throw unexpectedContainerAccess();
            }
        };
    }

    private static AssertionError unexpectedContainerAccess() {
        return new AssertionError("Classifying identity must not read or mutate inventory");
    }

    private static JsonObject request(String radius) {
        return JsonParser.parseString("{\"selector\":{\"chunkRadius\":" + radius + "}}")
                .getAsJsonObject();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassNode compiled(String name) throws Exception {
        try (InputStream input =
                NativeWorldReadBoundaryTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return type;
        }
    }
}
