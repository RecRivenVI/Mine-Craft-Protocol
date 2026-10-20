package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class LateGuiPresentationTest {
    @Test
    void obfuscatedSourceCharactersAreNotReportedAsVisibleNativeGlyphs() {
        assertEquals(
                "actually shown",
                UiNativeText.plain(FormattedCharSequence.forward("actually shown", Style.EMPTY)));
        assertEquals(
                "",
                UiNativeText.plain(
                        FormattedCharSequence.forward(
                                "hidden source", Style.EMPTY.withObfuscated(true))));
        assertEquals(
                "",
                UiNativeText.plain(
                        FormattedCharSequence.composite(
                                FormattedCharSequence.forward("visible prefix ", Style.EMPTY),
                                FormattedCharSequence.forward(
                                        "hidden suffix", Style.EMPTY.withObfuscated(true)))));
    }

    @Test
    void onlyCompletedGuiFramesAreReadableAndTheyBindWorldCameraScreenOverlaySizeAndAge() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(),
                camera = new Object(),
                screen = new Object(),
                overlay = new Object();
        frame.beginGui(world, camera, screen, overlay, 320, 240);
        frame.nativeText("debug_text", "actual F3 draw", 5, 5, 80, 9, 1, new Matrix3x2f(), null);
        assertEquals(0, frame.sampleGui(world, camera, screen, overlay, 320, 240, 100).size());
        frame.finish(100);
        assertEquals(1, frame.sampleGui(world, camera, screen, overlay, 320, 240, 100).size());
        assertEquals(
                0, frame.sampleGui(new Object(), camera, screen, overlay, 320, 240, 100).size());
        assertEquals(
                0, frame.sampleGui(world, new Object(), screen, overlay, 320, 240, 100).size());
        assertEquals(
                0, frame.sampleGui(world, camera, new Object(), overlay, 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, screen, new Object(), 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, screen, null, 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, screen, overlay, 321, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, screen, overlay, 320, 240, 99).size());
        assertEquals(
                1, frame.sampleGui(world, camera, screen, overlay, 320, 240, 250_000_100).size());
        assertEquals(
                0, frame.sampleGui(world, camera, screen, overlay, 320, 240, 250_000_101).size());
        frame.beginGui(world, camera, screen, overlay, 320, 240);
        frame.finish(101);
        assertEquals(0, frame.sampleGui(world, camera, screen, overlay, 320, 240, 101).size());
    }

    @Test
    void titleGuiDrawMayHaveNoWorldButTheOrdinaryHudStillCannotInventAWorldFrame() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object screen = new Object();
        frame.begin(null, null, screen, 320, 240);
        frame.nativeText("tooltip", "no HUD context", 1, 1, 100, 9, 1, new Matrix3x2f(), null);
        frame.finish(100);
        assertEquals(0, frame.sample(null, null, screen, 320, 240, 100).size());
        frame.beginGui(null, null, screen, null, 320, 240);
        frame.nativeText(
                "tooltip", "actually drawn title tooltip", 1, 1, 100, 9, 1, new Matrix3x2f(), null);
        frame.finish(100);
        assertEquals(1, frame.sampleGui(null, null, screen, null, 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(new Object(), null, screen, null, 320, 240, 100).size());
    }

    @Test
    void realTextPoseOpacityAndScissorAreRespectedWithoutLeakingClippedCharacters() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object(), screen = new Object();
        frame.beginGui(world, camera, screen, null, 320, 240);
        Matrix3x2f pose = new Matrix3x2f().translate(10.25F, 20.5F).scale(2);
        frame.nativeText(
                "inventory_effect",
                "visible clipped-by-native name...",
                0,
                0,
                50,
                9,
                .5F,
                pose,
                null);
        frame.nativeText(
                "tooltip",
                "SECRET OUTSIDE SCISSOR",
                0,
                0,
                100,
                9,
                1,
                pose,
                new ScreenRectangle(20, 20, 50, 30));
        frame.nativeText("chat_background", "transparent", 0, 0, 50, 9, 0, pose, null);
        frame.nativeText("debug_text", "outside", 500, 0, 50, 9, 1, pose, null);
        frame.finish(100);
        var nodes = frame.sampleGui(world, camera, screen, null, 320, 240, 100);
        assertEquals(2, nodes.size());
        var full = nodes.get(0).getAsJsonObject();
        assertEquals("visible clipped-by-native name...", full.get("label").getAsString());
        assertEquals(10.25, full.get("x").getAsDouble());
        assertEquals(20.5, full.get("y").getAsDouble());
        assertEquals(100, full.get("width").getAsDouble());
        assertEquals(18, full.get("height").getAsDouble());
        assertEquals(.5, full.get("opacity").getAsDouble());
        assertEquals("drawn", full.get("textPhase").getAsString());
        var clipped = nodes.get(1).getAsJsonObject();
        assertEquals("", clipped.get("label").getAsString());
        assertEquals("withheld_partial_or_occluded", clipped.get("textPhase").getAsString());
        assertEquals(20, clipped.get("x").getAsDouble());
        assertEquals(50, clipped.get("width").getAsDouble());
        assertFalse(nodes.toString().contains("SECRET"));
        for (var value : nodes) {
            var node = value.getAsJsonObject();
            assertFalse(node.get("active").getAsBoolean());
            assertEquals(0, node.getAsJsonArray("actions").size());
            assertEquals(
                    "last_completed_native_gui_text_draw", node.get("labelSource").getAsString());
            for (String hidden :
                    List.of(
                            "duration",
                            "amplifier",
                            "latency",
                            "allMessages",
                            "soundPosition",
                            "serverChunk",
                            "uuid",
                            "resourceVersion")) assertFalse(node.has(hidden));
        }
    }

    @Test
    void independentLateBudgetFailsClosedAndTheOrdinaryHudBudgetRemains32() {
        Object world = new Object(), camera = new Object();
        var hud = new UiPresentationSnapshot.RenderedHud();
        hud.begin(world, camera, null, 320, 240);
        for (int i = 0; i < 33; i++)
            hud.nativeText("selected_item_name", "line", 1, 1, 20, 9, 1, new Matrix3x2f(), null);
        hud.finish(100);
        assertEquals(0, hud.sample(world, camera, null, 320, 240, 100).size());
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        frame.beginGui(world, camera, null, null, 320, 240);
        for (int i = 0; i < 128; i++)
            frame.nativeText("debug_text", "line", 1, 1, 20, 9, 1, new Matrix3x2f(), null);
        frame.finish(100);
        assertEquals(128, frame.sampleGui(world, camera, null, null, 320, 240, 100).size());
        frame.beginGui(world, camera, null, null, 320, 240);
        for (int i = 0; i < 129; i++)
            frame.nativeText("debug_text", "line", 1, 1, 20, 9, 1, new Matrix3x2f(), null);
        frame.finish(101);
        assertEquals(0, frame.sampleGui(world, camera, null, null, 320, 240, 101).size());
        assertThrows(
                IllegalArgumentException.class, () -> new UiPresentationSnapshot.RenderedHud(257));
    }

    @Test
    void productionNativeDrawsForwardOriginalArgumentsExactlyOnceBeforeObserving()
            throws Exception {
        int delegates = 0;
        for (String name :
                List.of(
                        "BossBarPresentationMixin",
                        "EffectsInventoryPresentationMixin",
                        "TooltipPresentationMixin",
                        "DebugTextPresentationMixin",
                        "ChatBackgroundPresentationMixin",
                        "ChatFocusedPresentationMixin",
                        "SubtitlePresentationMixin",
                        "HudMessagePresentationMixin")) {
            ClassNode compiled = read("mixin/" + name);
            for (MethodNode method : compiled.methods) {
                if (!annotation(method, "Redirect")) continue;
                assertActualNativeRedirect(compiled, method);
                delegates++;
                var instructions =
                        Arrays.stream(method.instructions.toArray())
                                .filter(i -> i.getOpcode() >= 0)
                                .toList();
                MethodInsnNode draw =
                        instructions.stream()
                                .filter(MethodInsnNode.class::isInstance)
                                .map(MethodInsnNode.class::cast)
                                .findFirst()
                                .orElseThrow();
                assertTrue(
                        List.of(
                                        "net/minecraft/client/gui/GuiGraphicsExtractor",
                                        "net/minecraft/client/gui/ActiveTextCollector")
                                .contains(draw.owner));
                assertTrue(
                        List.of("blitSprite", "text", "textWithBackdrop", "accept")
                                .contains(draw.name));
                Type[] args = Type.getArgumentTypes(method.desc);
                assertEquals(
                        List.of(args).subList(1, args.length),
                        List.of(Type.getArgumentTypes(draw.desc)));
                int local = 1;
                for (int i = 0; i < args.length; i++) {
                    VarInsnNode load = assertInstanceOf(VarInsnNode.class, instructions.get(i));
                    assertEquals(args[i].getOpcode(Opcodes.ILOAD), load.getOpcode());
                    assertEquals(local, load.var);
                    local += args[i].getSize();
                }
                assertSame(draw, instructions.get(args.length));
                assertEquals(
                        1,
                        instructions.stream()
                                .filter(
                                        i ->
                                                i instanceof MethodInsnNode call
                                                        && call.owner.equals(draw.owner)
                                                        && call.name.equals(draw.name))
                                .count());
                assertTrue(
                        instructions.stream()
                                .skip(args.length + 1)
                                .anyMatch(
                                        i ->
                                                i instanceof MethodInsnNode call
                                                        && (call.owner.endsWith("/UiNativeText")
                                                                || call.name.equals(
                                                                        "bossSprite"))));
            }
        }
        assertEquals(12, delegates);
    }

    @Test
    void nativeBossSpritesPreserveActualBackgroundFillAndSegmentOrderWithoutRawState() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object();
        frame.beginGui(world, camera, null, null, 640, 480);
        String[] sprites = {
            "red_background",
            "red_progress",
            "notched_10_background",
            "notched_10_progress",
            "blue_background",
            "blue_progress"
        };
        for (int index = 0; index < sprites.length; index++) {
            int y = index < 4 ? 12 : 31;
            frame.bossSprite(
                    "minecraft:boss_bar/" + sprites[index], 229, y, index % 2 == 0 ? 182 : 91, 5);
        }
        frame.bossSprite("minecraft:boss_bar/red_background", -1, 12, 182, 5);
        frame.bossSprite("minecraft:boss_bar/red_progress", 229, 12, 0, 5);
        frame.bossSprite("minecraft:boss_bar/red_background", 229, 480, 182, 5);
        frame.finish(100);
        var nodes = frame.sampleGui(world, camera, null, null, 640, 480, 100);
        assertEquals(sprites.length, nodes.size());
        for (int index = 0; index < sprites.length; index++) {
            var node = nodes.get(index).getAsJsonObject();
            assertEquals(
                    "minecraft:boss_bar/" + sprites[index], node.get("resourceId").getAsString());
            assertEquals("boss_bar", node.get("renderPhase").getAsString());
            assertEquals("last_completed_native_hud_draw", node.get("iconSource").getAsString());
            assertEquals(index % 2 == 0 ? 182 : 91, node.get("width").getAsInt());
            assertEquals(index < 4 ? 12 : 31, node.get("y").getAsInt());
            assertFalse(node.get("active").getAsBoolean());
            assertEquals(0, node.getAsJsonArray("actions").size());
            for (String hidden :
                    List.of(
                            "value",
                            "progress",
                            "name",
                            "uuid",
                            "color",
                            "overlay",
                            "darkenScreen")) assertFalse(node.has(hidden));
        }
    }

    @Test
    void actualNativeBossOverloadHasTwoReviewedSpriteSitesAndTheAdapterNeverReadsBossFields()
            throws Exception {
        String selector =
                "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V";
        String descriptor =
                "(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V";
        var nativeBoss = nativeClass("net/minecraft/client/gui/components/BossHealthOverlay");
        var overload =
                nativeBoss.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("extractBar")
                                                && method.desc.equals(selector))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                2,
                Arrays.stream(overload.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/client/gui/GuiGraphicsExtractor")
                                                && call.name.equals("blitSprite")
                                                && call.desc.equals(descriptor))
                        .count());
        var adapter = read("mixin/BossBarPresentationMixin");
        assertEquals(
                1,
                adapter.methods.stream().filter(method -> annotation(method, "Redirect")).count());
        var calls =
                adapter.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        assertEquals(
                1,
                calls.stream()
                        .filter(
                                call ->
                                        call.name.equals("blitSprite")
                                                && call.desc.equals(descriptor))
                        .count());
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("activeGuiPresentation")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("bossSprite")));
        assertFalse(
                calls.stream()
                        .anyMatch(
                                call ->
                                        List.of(
                                                        "getProgress",
                                                        "getColor",
                                                        "getOverlay",
                                                        "getName",
                                                        "getId")
                                                .contains(call.name)));
        assertTrue(
                adapter.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .noneMatch(
                                value ->
                                        value instanceof FieldInsnNode field
                                                && field.owner.equals(
                                                        "net/minecraft/world/BossEvent")));
    }

    @Test
    void completeGuiBoundaryAndActualTextParametersDoNotReadHiddenCachesOrRequestServerData()
            throws Exception {
        var gui = read("mixin/GuiLatePresentationMixin");
        assertTrue(
                gui.interfaces.contains(
                        "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiPresentationSnapshot$GuiPresentationSource"));
        assertEquals(
                2, gui.methods.stream().filter(method -> annotation(method, "Inject")).count());
        for (String source : List.of("beginGui", "finish"))
            assertTrue(
                    gui.methods.stream()
                            .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                            .anyMatch(
                                    i ->
                                            i instanceof MethodInsnNode call
                                                    && call.name.equals(source)));

        var boundary =
                nativeClass("net/minecraft/client/gui/Gui").methods.stream()
                        .filter(method -> method.name.equals("extractRenderState"))
                        .findFirst()
                        .orElseThrow();
        for (String callName :
                List.of(
                        "extractRenderState",
                        "extractRenderStateWithTooltipAndSubtitles",
                        "extractDeferredSubtitles"))
            assertTrue(
                    Arrays.stream(boundary.instructions.toArray())
                            .anyMatch(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && call.name.equals(callName)),
                    callName);
        assertTrue(
                annotations(gui.visibleAnnotations, gui.invisibleAnnotations).stream()
                        .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                        .anyMatch(
                                value ->
                                        ((List<?>) annotationValue(value, "value"))
                                                .contains(
                                                        Type.getObjectType(
                                                                "net/minecraft/client/gui/Gui"))));
        var capture = read("runtime/UiNativeText");
        var calls =
                capture.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        MethodNode observe =
                capture.methods.stream()
                        .filter(method -> method.name.equals("observe"))
                        .findFirst()
                        .orElseThrow();
        String graphicsOwner = "net/minecraft/client/gui/GuiGraphicsExtractor";
        String stackOwner = graphicsOwner + "$ScissorStack";
        assertTrue(
                Arrays.stream(observe.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.getOpcode() == Opcodes.GETFIELD
                                                && field.owner.equals(graphicsOwner)
                                                && field.name.equals("scissorStack")
                                                && field.desc.equals("L" + stackOwner + ";")));
        assertTrue(
                nativeClass(graphicsOwner).fields.stream()
                        .anyMatch(
                                field ->
                                        field.name.equals("scissorStack")
                                                && field.desc.equals("L" + stackOwner + ";")
                                                && (field.access & Opcodes.ACC_PUBLIC) != 0));
        assertTrue(
                Arrays.stream(observe.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(stackOwner)
                                                && call.name.equals("peek")
                                                && call.desc.equals(
                                                        "()Lnet/minecraft/client/gui/navigation/ScreenRectangle;")));
        assertTrue(
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(graphicsOwner)
                                                && call.name.equals("pose")
                                                && call.desc.equals(
                                                        "()Lorg/joml/Matrix3x2fStack;")));
        String parameters = "net/minecraft/client/gui/ActiveTextCollector$Parameters";
        for (var parameter :
                java.util.Map.of(
                                "opacity",
                                "()F",
                                "pose",
                                "()Lorg/joml/Matrix3x2fc;",
                                "scissor",
                                "()Lnet/minecraft/client/gui/navigation/ScreenRectangle;")
                        .entrySet())
            assertTrue(
                    calls.stream()
                            .anyMatch(
                                    call ->
                                            call.owner.equals(parameters)
                                                    && call.name.equals(parameter.getKey())
                                                    && call.desc.equals(parameter.getValue())),
                    parameter.getKey());
        assertTrue(
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("net/minecraft/client/gui/TextAlignment")
                                                && call.name.equals("calculateLeft")
                                                && call.desc.equals("(II)I")));
        for (String forbidden :
                List.of(
                        "getServerChunk",
                        "getActiveEffects",
                        "getCurrentMusicTranslationKey",
                        "getLatency",
                        "getConnection"))
            assertFalse(calls.stream().anyMatch(call -> call.name.equals(forbidden)), forbidden);
        assertTrue(
                capture.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .noneMatch(
                                i ->
                                        i instanceof FieldInsnNode field
                                                && List.of(
                                                                "allMessages",
                                                                "trimmedMessages",
                                                                "serverChunk",
                                                                "audibleSubtitles")
                                                        .contains(field.name)));
    }

    private void assertActualNativeRedirect(ClassNode adapter, MethodNode redirect)
            throws Exception {
        AnnotationNode mixin =
                annotations(adapter.visibleAnnotations, adapter.invisibleAnnotations).stream()
                        .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                        .findFirst()
                        .orElseThrow();
        Object types = annotationValue(mixin, "value");
        String owner =
                types instanceof List<?> values && !values.isEmpty()
                        ? ((Type) values.getFirst()).getInternalName()
                        : ((String) ((List<?>) annotationValue(mixin, "targets")).getFirst())
                                .replace('.', '/');
        ClassNode nativeClass = nativeClass(owner);
        AnnotationNode annotation =
                annotations(redirect.visibleAnnotations, redirect.invisibleAnnotations).stream()
                        .filter(
                                value ->
                                        value.desc.equals(
                                                "Lorg/spongepowered/asm/mixin/injection/Redirect;"))
                        .findFirst()
                        .orElseThrow();
        var selectors = (List<?>) annotationValue(annotation, "method");
        var injectionPoints = assertInstanceOf(List.class, annotationValue(annotation, "at"));
        assertEquals(
                1,
                injectionPoints.size(),
                "Each exact native delegation observes one invocation kind");
        AnnotationNode at = assertInstanceOf(AnnotationNode.class, injectionPoints.getFirst());
        String target = (String) annotationValue(at, "target");
        int endOwner = target.indexOf(';'), beginDescriptor = target.indexOf('(', endOwner);
        String targetOwner = target.substring(1, endOwner),
                targetName = target.substring(endOwner + 1, beginDescriptor);
        String targetDescriptor = target.substring(beginDescriptor);
        for (Object raw : selectors) {
            String selector = (String) raw;
            var methods =
                    nativeClass.methods.stream()
                            .filter(
                                    method ->
                                            selector.equals(method.name)
                                                    || selector.equals(method.name + method.desc))
                            .toList();
            assertFalse(methods.isEmpty(), owner + "." + selector);
            assertTrue(
                    methods.stream()
                            .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                            .anyMatch(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && call.owner.equals(targetOwner)
                                                    && call.name.equals(targetName)
                                                    && call.desc.equals(targetDescriptor)),
                    owner + "." + selector + " must really invoke " + target);
        }
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(key)) return annotation.values.get(index + 1);
        return null;
    }

    private static List<AnnotationNode> annotations(
            List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        return java.util.stream.Stream.of(visible, invisible)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private ClassNode nativeClass(String owner) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input, owner);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static boolean annotation(MethodNode method, String name) {
        return java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .anyMatch(
                        a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/" + name + ";"));
    }

    private ClassNode read(String name) throws Exception {
        try (InputStream input =
                getClass()
                        .getResourceAsStream(
                                "/io/github/recrivenvi/minecraftprotocol/probe/"
                                        + name
                                        + ".class")) {
            assertNotNull(input, name);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
