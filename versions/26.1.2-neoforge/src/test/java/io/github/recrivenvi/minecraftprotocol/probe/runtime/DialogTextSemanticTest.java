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
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class DialogTextSemanticTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/probe/";
    private static final String WIDGET = "net/minecraft/client/gui/components/AbstractStringWidget";
    private static final String REGIONS = ROOT + "runtime/VanillaInteractionRegions";
    private static final String TREE = ROOT + "runtime/VanillaUiTree";

    @Test
    void nativeStringAccessorsAreReadOnlyAndMatchTheActualWidgetFields() throws Exception {
        ClassNode adapter = compiled(ROOT + "mixin/StringWidgetUiAccessor");
        AnnotationNode mixin =
                annotations(adapter.visibleAnnotations, adapter.invisibleAnnotations).stream()
                        .filter(
                                annotation ->
                                        annotation.desc.equals(
                                                "Lorg/spongepowered/asm/mixin/Mixin;"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of(Type.getObjectType(WIDGET)), value(mixin, "value"));
        assertAccessor(adapter, "componentClickHandler", "Ljava/util/function/Consumer;");
        assertAccessor(adapter, "font", "Lnet/minecraft/client/gui/Font;");
        assertEquals(2, adapter.methods.size());
    }

    @Test
    void productionTreeCollectsOnlyVisibleHandledTextThroughTheActualNativeClickVisitor()
            throws Exception {
        MethodNode collect = method(compiled(REGIONS), "textWidget");
        int handler =
                callIndex(
                        collect,
                        ROOT + "mixin/StringWidgetUiAccessor",
                        "minecraftProtocolProbe$clickHandler");
        int lines = callIndex(collect, WIDGET, "visitLines");
        assertTrue(handler >= 0 && lines > handler);
        assertTrue(
                Arrays.stream(collect.instructions.toArray())
                        .skip(handler + 1L)
                        .limit(lines - handler - 1L)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof JumpInsnNode jump
                                                && jump.getOpcode() == Opcodes.IFNONNULL),
                "The actual native click handler must exist before collecting a hit region");
        assertTrue(
                calls(
                        collect,
                        ROOT + "mixin/StringWidgetUiAccessor",
                        "minecraftProtocolProbe$font"));
        assertTrue(calls(collect, TREE, "intersect"));
        assertFalse(
                calls(collect, "java/util/function/Consumer", "accept"),
                "Reading a semantic link must not execute its native handler");
        MethodNode nativeClick = method(compiled(WIDGET), "onClick");
        assertTrue(calls(nativeClick, WIDGET, "visitLines"));
        assertTrue(
                calls(
                        nativeClick,
                        "net/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder",
                        "result"));
        assertTrue(calls(nativeClick, "java/util/function/Consumer", "accept"));

        ClassNode tree = compiled(TREE);
        MethodNode capture =
                tree.methods.stream()
                        .filter(
                                candidate ->
                                        candidate.name.equals("capture")
                                                && candidate.desc.startsWith(
                                                        "(Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(calls(capture, REGIONS, "textWidget"));
        assertTrue(constants(capture).contains("visible"));
        assertTrue(
                Arrays.stream(capture.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.getOpcode() == Opcodes.GETFIELD
                                                && field.owner.equals(TREE)
                                                && field.name.equals("interactionRegions")
                                                && field.desc.equals("L" + REGIONS + ";")));
        MethodNode rootCapture =
                tree.methods.stream()
                        .filter(
                                candidate ->
                                        candidate.name.equals("capture")
                                                && candidate.desc.startsWith(
                                                        "(Lnet/minecraft/client/gui/screens/Screen;"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1,
                Arrays.stream(rootCapture.instructions.toArray())
                        .filter(
                                instruction ->
                                        instruction instanceof TypeInsnNode type
                                                && type.getOpcode() == Opcodes.NEW
                                                && type.desc.equals(REGIONS))
                        .count());
        assertTrue(
                Arrays.stream(rootCapture.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.getOpcode() == Opcodes.PUTFIELD
                                                && field.owner.equals(TREE)
                                                && field.name.equals("interactionRegions")));

        ClassNode rich = compiled(ROOT + "runtime/RichTextUiRegions");
        assertTrue(
                calls(
                        method(rich, "accept"),
                        "net/minecraft/client/renderer/state/gui/GuiTextRenderState",
                        "ensurePrepared"));
        assertEquals(
                2,
                Arrays.stream(method(rich, "active").instructions.toArray())
                        .filter(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        ROOT + "runtime/RichTextUiRegions")
                                                && call.name.equals("clip"))
                        .count(),
                "Both native text scissor and inherited widget clip must constrain the actual glyph");
    }

    @Test
    void productionGlyphClippingPreservesFractionalCoordinatesAndRejectsHiddenRegions() {
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
    void nativeScrollableContainersConstrainTheInheritedClipBeforeVisitingChildLinks()
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
                compiled(TREE).methods.stream()
                        .filter(
                                candidate ->
                                        candidate.name.equals("capture")
                                                && candidate.desc.startsWith(
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
                    && call.owner.equals(TREE)
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
                "The real scroll-container branch must clip before traversing descendants");
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

    private static void assertAccessor(ClassNode adapter, String field, String descriptor)
            throws Exception {
        assertTrue(
                compiled(WIDGET).fields.stream()
                        .anyMatch(
                                candidate ->
                                        candidate.name.equals(field)
                                                && candidate.desc.equals(descriptor)));
        MethodNode getter =
                adapter.methods.stream()
                        .filter(
                                candidate ->
                                        annotations(
                                                        candidate.visibleAnnotations,
                                                        candidate.invisibleAnnotations)
                                                .stream()
                                                .anyMatch(
                                                        annotation ->
                                                                annotation.desc.equals(
                                                                                "Lorg/spongepowered/asm/mixin/gen/Accessor;")
                                                                        && field.equals(
                                                                                value(
                                                                                        annotation,
                                                                                        "value"))))
                        .findFirst()
                        .orElseThrow();
        assertEquals("()" + descriptor, getter.desc);
        assertTrue((getter.access & Opcodes.ACC_ABSTRACT) != 0);
    }

    private static List<AnnotationNode> annotations(
            List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        return java.util.stream.Stream.of(visible, invisible)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(key)) return annotation.values.get(index + 1);
        return null;
    }

    private static ClassNode compiled(String path) throws Exception {
        try (InputStream input =
                DialogTextSemanticTest.class.getResourceAsStream("/" + path + ".class")) {
            assertNotNull(input, path);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                .filter(candidate -> candidate.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        return callIndex(method, owner, name) >= 0;
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        var instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++)
            if (instructions[index] instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)) return index;
        return -1;
    }

    private static List<Object> constants(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast)
                .map(candidate -> candidate.cst)
                .toList();
    }
}
