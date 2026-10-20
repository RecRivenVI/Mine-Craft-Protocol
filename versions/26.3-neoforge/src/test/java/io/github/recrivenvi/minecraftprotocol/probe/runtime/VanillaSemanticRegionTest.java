package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

final class VanillaSemanticRegionTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/probe/";
    private static final String REGIONS = ROOT + "runtime/VanillaInteractionRegions";
    private static final String SWITCHER =
            "net/minecraft/client/gui/screens/debug/GameModeSwitcherScreen";

    @Test
    void nativeGameModeChoicesReallyAreOutsideTheOrdinaryWidgetTree() throws Exception {
        MethodNode init = method(compiled(SWITCHER), "init");
        assertTrue(calls(init, "java/util/List", "add"));
        assertFalse(
                Arrays.stream(init.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.name.startsWith("addRenderable")));
        MethodNode collect = method(compiled(REGIONS), "gameModes");
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
        List<Object> values = constants(collect);
        assertTrue(values.contains("hover"));
        assertTrue(values.contains("hover_then_release_debug_modifier"));
        assertFalse(
                values.contains("click"), "Native game-mode slots are hover choices, not buttons");
        assertTrue(
                calls(
                        method(compiled(SWITCHER), "keyReleased"),
                        SWITCHER,
                        "switchToHoveredGameMode"));
        assertFalse(calls(collect, SWITCHER, "switchToHoveredGameMode"));
        assertFalse(
                calls(collect, "net/minecraft/client/multiplayer/ClientPacketListener", "send"));
    }

    @Test
    void nativeAccessorTargetsAreReadOnlyAndMatchTheActualFields() throws Exception {
        assertAccessor("GameModeSwitcherUiAccessor", SWITCHER, "slots", "Ljava/util/List;");
        assertAccessor("GameModeSlotUiAccessor", SWITCHER + "$GameModeSlot", "isSelected", "Z");
        assertAccessor(
                "StringWidgetUiAccessor",
                "net/minecraft/client/gui/components/AbstractStringWidget",
                "componentClickHandler",
                "Ljava/util/function/Consumer;");
        assertAccessor(
                "StringWidgetUiAccessor",
                "net/minecraft/client/gui/components/AbstractStringWidget",
                "font",
                "Lnet/minecraft/client/gui/Font;");
    }

    @Test
    void textRegionsReadTheActualHandlerAndUseTheSameNativeLineVisitorAsClickFinding()
            throws Exception {
        MethodNode collect = method(compiled(REGIONS), "textWidget");
        int handler =
                callIndex(
                        collect,
                        ROOT + "mixin/StringWidgetUiAccessor",
                        "minecraftProtocolProbe$clickHandler");
        int lines =
                callIndex(
                        collect,
                        "net/minecraft/client/gui/components/AbstractStringWidget",
                        "visitLines");
        assertTrue(handler >= 0 && lines > handler);
        assertTrue(
                Arrays.stream(collect.instructions.toArray())
                        .skip(handler + 1L)
                        .limit(lines - handler - 1L)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof JumpInsnNode jump
                                                && jump.getOpcode() == Opcodes.IFNONNULL),
                "A missing native click handler must return before glyph collection");
        assertTrue(
                calls(
                        collect,
                        ROOT + "mixin/StringWidgetUiAccessor",
                        "minecraftProtocolProbe$font"));
        assertTrue(calls(collect, ROOT + "runtime/VanillaUiTree", "intersect"));
        assertFalse(calls(collect, "java/util/function/Consumer", "accept"));
        MethodNode nativeClick =
                method(
                        compiled("net/minecraft/client/gui/components/AbstractStringWidget"),
                        "onClick");
        assertTrue(
                calls(
                        nativeClick,
                        "net/minecraft/client/gui/components/AbstractStringWidget",
                        "visitLines"));
        assertTrue(
                calls(
                        nativeClick,
                        "net/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder",
                        "result"));
        MethodNode capture =
                compiled(ROOT + "runtime/VanillaUiTree").methods.stream()
                        .filter(
                                value ->
                                        value.name.equals("capture")
                                                && value.desc.startsWith(
                                                        "(Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(calls(capture, REGIONS, "textWidget"));
        assertTrue(constants(capture).contains("visible"));
        assertTrue(
                calls(
                        method(compiled(ROOT + "runtime/RichTextUiRegions"), "accept"),
                        "net/minecraft/client/renderer/state/gui/GuiTextRenderState",
                        "ensurePrepared"));
    }

    @Test
    void glyphClippingPreservesFractionsAndNeverPublishesAHiddenHitArea() {
        var source = new RichTextUiRegions.Region(Style.EMPTY, "link", 9.25, 18.5, 30.75, 29.5);
        var nativeClip = new ScreenRectangle(10, 19, 20, 10);
        var parentClip = new ScreenRectangle(12, 20, 12, 8);
        var hit = RichTextUiRegions.clip(RichTextUiRegions.clip(source, nativeClip), parentClip);
        assertNotNull(hit);
        assertEquals(12, hit.left());
        assertEquals(20, hit.top());
        assertEquals(24, hit.right());
        assertEquals(28, hit.bottom());
        assertEquals("link", hit.text());
        assertEquals(source, RichTextUiRegions.clip(source, null));
        assertEquals(9.25, RichTextUiRegions.clip(source, null).left());
        assertNull(RichTextUiRegions.clip(source, ScreenRectangle.empty()));
        assertNull(RichTextUiRegions.clip(source, new ScreenRectangle(31, 0, 10, 40)));
        assertNull(RichTextUiRegions.clip(null, parentClip));
    }

    @Test
    void nativeScrollableContainersClipTheirDescendantsBeforeClickableTextCollection()
            throws Exception {
        MethodNode nativeRender =
                method(
                        compiled("net/minecraft/client/gui/components/ScrollableLayout$Container"),
                        "extractWidgetRenderState");
        assertTrue(
                calls(
                        nativeRender,
                        "net/minecraft/client/gui/GuiGraphicsExtractor",
                        "enableScissor"));
        MethodNode capture =
                compiled(ROOT + "runtime/VanillaUiTree").methods.stream()
                        .filter(
                                value ->
                                        value.name.equals("capture")
                                                && value.desc.startsWith(
                                                        "(Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
                        .findFirst()
                        .orElseThrow();
        var instructions = capture.instructions.toArray();
        int container = -1, scrolling = -1, intersection = -1, children = -1;
        for (int index = 0; index < instructions.length; index++) {
            if (instructions[index] instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals(
                            "net/minecraft/client/gui/components/events/ContainerEventHandler"))
                container = index;
            if (container >= 0
                    && instructions[index] instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("net/minecraft/client/gui/components/AbstractScrollArea"))
                scrolling = index;
            if (scrolling >= 0
                    && instructions[index] instanceof MethodInsnNode call
                    && call.owner.equals(ROOT + "runtime/VanillaUiTree")
                    && call.name.equals("intersect")) intersection = index;
            if (container >= 0
                    && instructions[index] instanceof MethodInsnNode call
                    && call.owner.equals(
                            "net/minecraft/client/gui/components/events/ContainerEventHandler")
                    && call.name.equals("children")) {
                children = index;
                break;
            }
        }
        assertTrue(
                container >= 0
                        && scrolling > container
                        && intersection > scrolling
                        && children > intersection,
                "The actual scroll-container branch must constrain the inherited clip before visiting its children");
        ScreenRectangle clip =
                VanillaUiTree.intersect(
                        new ScreenRectangle(0, 0, 640, 360), new ScreenRectangle(80, 60, 200, 70));
        assertNull(
                RichTextUiRegions.clip(
                        new RichTextUiRegions.Region(Style.EMPTY, "hidden", 90, 140, 150, 149),
                        clip));
        var partial =
                RichTextUiRegions.clip(
                        new RichTextUiRegions.Region(
                                Style.EMPTY, "partial", 90.25, 126.5, 150.75, 135.5),
                        clip);
        assertNotNull(partial);
        assertEquals(126.5, partial.top());
        assertEquals(130, partial.bottom());
        assertEquals(90.25, partial.left());
    }

    private static void assertAccessor(
            String accessor, String owner, String field, String descriptor) throws Exception {
        ClassNode nativeClass = compiled(owner);
        assertTrue(
                nativeClass.fields.stream()
                        .anyMatch(
                                value ->
                                        value.name.equals(field) && value.desc.equals(descriptor)));
        ClassNode adapter = compiled(ROOT + "mixin/" + accessor);
        MethodNode getter =
                adapter.methods.stream()
                        .filter(
                                value ->
                                        value.visibleAnnotations != null
                                                && value.visibleAnnotations.stream()
                                                        .anyMatch(
                                                                annotation ->
                                                                        annotation.desc.equals(
                                                                                        "Lorg/spongepowered/asm/mixin/gen/Accessor;")
                                                                                && annotation.values
                                                                                        .contains(
                                                                                                field)))
                        .findFirst()
                        .orElseThrow();
        assertEquals("()" + descriptor, getter.desc);
        assertTrue((getter.access & Opcodes.ACC_ABSTRACT) != 0);
    }

    private static ClassNode compiled(String path) throws Exception {
        try (InputStream input =
                VanillaSemanticRegionTest.class.getResourceAsStream("/" + path + ".class")) {
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
        return callIndex(method, owner, name) >= 0;
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        var values = method.instructions.toArray();
        for (int index = 0; index < values.length; index++)
            if (values[index] instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)) return index;
        return -1;
    }

    private static List<Object> constants(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast)
                .map(value -> value.cst)
                .toList();
    }
}
