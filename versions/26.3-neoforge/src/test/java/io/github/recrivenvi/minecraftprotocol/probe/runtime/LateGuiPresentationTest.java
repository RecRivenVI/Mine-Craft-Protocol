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
    void nativeBossSpritesAreActualBackgroundAndFillDrawsAndTheLateBudgetFailsClosed() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object();
        frame.beginGui(world, camera, null, null, 320, 240);
        frame.bossSprite("minecraft:boss_bar/red_background", 69, 12, 182, 5);
        frame.bossSprite("minecraft:boss_bar/red_progress", 69, 12, 91, 5);
        frame.bossSprite("minecraft:boss_bar/notched_10_background", 69, 12, 182, 5);
        frame.bossSprite("minecraft:boss_bar/notched_10_progress", 69, 12, 91, 5);
        frame.finish(100);
        var nodes = frame.sampleGui(world, camera, null, null, 320, 240, 100);
        assertEquals(4, nodes.size());
        for (var node : nodes)
            assertEquals("boss_bar", node.getAsJsonObject().get("renderPhase").getAsString());
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
        var capture = read("runtime/UiNativeText");
        var calls =
                capture.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        for (String parameter :
                List.of("pose", "peekScissorStack", "opacity", "scissor", "calculateLeft"))
            assertTrue(calls.stream().anyMatch(call -> call.name.equals(parameter)), parameter);
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
