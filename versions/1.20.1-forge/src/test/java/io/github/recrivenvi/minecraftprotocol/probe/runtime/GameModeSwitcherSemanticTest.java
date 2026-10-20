package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class GameModeSwitcherSemanticTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/probe/";
    private static final String REGIONS = ROOT + "runtime/VanillaInteractionRegions";
    private static final String SWITCHER =
            "net/minecraft/client/gui/screens/debug/GameModeSwitcherScreen";

    @Test
    void nativeFourChoicesArePrivateSlotsAndNotRegisteredAsOrdinaryWidgets() throws Exception {
        MethodNode init = method(compiled(SWITCHER), "init");
        assertTrue(calls(init, "java/util/List", "add"));
        assertFalse(
                Arrays.stream(init.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && (call.name.startsWith("addRenderable")
                                                        || call.name.equals("addWidget"))));
        ClassNode icons = compiled(SWITCHER + "$GameModeIcon");
        assertEquals(
                4,
                icons.fields.stream()
                        .filter(field -> (field.access & Opcodes.ACC_ENUM) != 0)
                        .count());
        assertTrue(
                Arrays.stream(init.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.owner.equals(SWITCHER + "$GameModeIcon")
                                                && field.name.equals("VALUES")));
    }

    @Test
    void actualProductionCaptureReadsNativeLabelsBoundsAndSelectionButOnlyAdvertisesHover()
            throws Exception {
        ClassNode regions = compiled(REGIONS);
        assertTrue(calls(method(regions, "capture"), REGIONS, "gameModes"));
        assertTrue(
                compiled(ROOT + "runtime/VanillaUiTree").methods.stream()
                        .anyMatch(method -> calls(method, REGIONS, "capture")));
        MethodNode collect = method(regions, "gameModes");
        assertTrue(
                calls(
                        collect,
                        ROOT + "mixin/GameModeSwitcherUiAccessor",
                        "minecraftProtocolProbe$slots"));
        assertTrue(
                calls(
                        collect,
                        ROOT + "mixin/GameModeSlotUiAccessor",
                        "minecraftProtocolProbe$selected"));
        for (String getter : List.of("getMessage", "getX", "getY", "getWidth", "getHeight"))
            assertTrue(calls(collect, SWITCHER + "$GameModeSlot", getter), getter);
        assertTrue(calls(collect, "net/minecraft/network/chat/Component", "getString"));
        assertTrue(
                calls(
                        collect,
                        "net/minecraft/network/chat/contents/TranslatableContents",
                        "getKey"));
        List<Object> values = constants(collect);
        for (String value :
                List.of(
                        "game_mode",
                        "hover",
                        "selected",
                        "translationKey",
                        "effect",
                        "hover_then_release_debug_modifier"))
            assertTrue(values.contains(value), value);
        for (String action : List.of("click", "mouse_down", "mouse_up", "drag"))
            assertFalse(
                    values.contains(action), "Native choices are not clickable buttons: " + action);
        assertFalse(
                Arrays.stream(collect.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && (call.name.equals("switchToHoveredGameMode")
                                                        || call.name.equals("setSelected")
                                                        || call.name.equals("send")
                                                        || call.name.equals(
                                                                "sendUnsignedCommand"))));
    }

    @Test
    void accessorsOnlyReadTheActualNativeSlotsAndSelectedFlag() throws Exception {
        assertAccessor("GameModeSwitcherUiAccessor", SWITCHER, "slots", "Ljava/util/List;");
        assertAccessor("GameModeSlotUiAccessor", SWITCHER + "$GameModeSlot", "isSelected", "Z");
    }

    @Test
    void nativeRenderObservesDebugModifierReleaseAndRetainsItsPermissionCheckedCommandPath()
            throws Exception {
        ClassNode nativeClass = compiled(SWITCHER);
        assertTrue(calls(method(nativeClass, "render"), SWITCHER, "checkToClose"));
        MethodNode close = method(nativeClass, "checkToClose");
        assertTrue(calls(close, "com/mojang/blaze3d/platform/InputConstants", "isKeyDown"));
        assertTrue(
                Arrays.stream(close.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof IntInsnNode integer
                                                && integer.operand == 292));
        assertTrue(calls(close, SWITCHER, "switchToHoveredGameMode"));
        assertTrue(calls(close, "net/minecraft/client/Minecraft", "setScreen"));
        MethodNode submit =
                nativeClass.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("switchToHoveredGameMode")
                                                && (method.access & Opcodes.ACC_STATIC) != 0)
                        .findFirst()
                        .orElseThrow();
        assertTrue(calls(submit, "net/minecraft/client/player/LocalPlayer", "hasPermissions"));
        assertTrue(
                calls(
                        submit,
                        "net/minecraft/client/multiplayer/ClientPacketListener",
                        "sendUnsignedCommand"));
        assertFalse(calls(method(compiled(REGIONS), "gameModes"), SWITCHER, "checkToClose"));
    }

    private static void assertAccessor(String name, String owner, String field, String descriptor)
            throws Exception {
        ClassNode nativeClass = compiled(owner);
        assertTrue(
                nativeClass.fields.stream()
                        .anyMatch(
                                value ->
                                        value.name.equals(field) && value.desc.equals(descriptor)));
        ClassNode adapter = compiled(ROOT + "mixin/" + name);
        assertNotNull(adapter.invisibleAnnotations);
        AnnotationNode mixin =
                adapter.invisibleAnnotations.stream()
                        .filter(
                                annotation ->
                                        annotation.desc.equals(
                                                "Lorg/spongepowered/asm/mixin/Mixin;"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                mixin.values.stream()
                        .anyMatch(
                                value ->
                                        value instanceof List<?> list
                                                && list.contains(Type.getObjectType(owner))));
        assertEquals(1, adapter.methods.size());
        MethodNode getter = adapter.methods.get(0);
        assertNotNull(getter.visibleAnnotations);
        assertTrue(
                getter.visibleAnnotations.stream()
                        .anyMatch(
                                annotation ->
                                        annotation.desc.equals(
                                                        "Lorg/spongepowered/asm/mixin/gen/Accessor;")
                                                && annotation.values.contains(field)));
        assertEquals("()" + descriptor, getter.desc);
        assertTrue((getter.access & Opcodes.ACC_ABSTRACT) != 0);
        assertEquals(0, getter.instructions.size());
    }

    private static ClassNode compiled(String path) throws Exception {
        try (InputStream input =
                GameModeSwitcherSemanticTest.class.getResourceAsStream("/" + path + ".class")) {
            assertNotNull(input, path);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                .filter(value -> value.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        return Arrays.stream(method.instructions.toArray())
                .anyMatch(
                        instruction ->
                                instruction instanceof MethodInsnNode call
                                        && call.owner.equals(owner)
                                        && call.name.equals(name));
    }

    private static List<Object> constants(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast)
                .map(value -> value.cst)
                .toList();
    }
}
