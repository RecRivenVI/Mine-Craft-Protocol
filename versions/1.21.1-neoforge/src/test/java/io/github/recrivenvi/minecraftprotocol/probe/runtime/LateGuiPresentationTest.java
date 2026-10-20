package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
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
        assertEquals("42", UiNativeText.plain("\u00a7e42"));
        assertEquals("", UiNativeText.plain("\u00a7kSECRET"));
    }

    @Test
    void onlyCompletedGuiFramesBindWorldCameraScreenOverlayViewportAndAge() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(),
                camera = new Object(),
                screen = new Object(),
                overlay = new Object();
        frame.beginGui(world, camera, screen, overlay, 320, 240);
        frame.nativeText("debug_text", "actual F3 draw", 5, 5, 80, 9, 1, new Matrix4f(), null);
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
    void titleGuiMayHaveNoWorldButOrdinaryHudCannotInventAWorldFrame() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object screen = new Object();
        frame.begin(null, null, screen, 320, 240);
        frame.nativeText("tooltip", "no HUD context", 1, 1, 100, 9, 1, new Matrix4f(), null);
        frame.finish(100);
        assertEquals(0, frame.sample(null, null, screen, 320, 240, 100).size());
        frame.beginGui(null, null, screen, null, 320, 240);
        frame.nativeText(
                "tooltip", "actually drawn title tooltip", 1, 1, 100, 9, 1, new Matrix4f(), null);
        frame.finish(100);
        assertEquals(1, frame.sampleGui(null, null, screen, null, 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(new Object(), null, screen, null, 320, 240, 100).size());
    }

    @Test
    void actualMatrixScissorOpacityAndFractionalTooltipCoordinatesWithholdClippedText() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object(), screen = new Object();
        frame.beginGui(world, camera, screen, null, 320, 240);
        var pose = new Matrix4f().translate(10.25F, 20.5F, 0).scale(2);
        frame.nativeText("inventory_effect", "actually visible", 0, 0, 50, 9, .5F, pose, null);
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
        frame.nativeText("tooltip", "fractional", 1.5, 2.25, 10, 9, 1, pose, null);
        frame.finish(100);
        var nodes = frame.sampleGui(world, camera, screen, null, 320, 240, 100);
        assertEquals(3, nodes.size());
        var full = nodes.get(0).getAsJsonObject();
        assertEquals("actually visible", full.get("label").getAsString());
        assertEquals(10.25, full.get("x").getAsDouble());
        assertEquals(20.5, full.get("y").getAsDouble());
        assertEquals(100, full.get("width").getAsDouble());
        assertEquals(18, full.get("height").getAsDouble());
        assertEquals(.5, full.get("opacity").getAsDouble());
        var clipped = nodes.get(1).getAsJsonObject();
        assertEquals("", clipped.get("label").getAsString());
        assertEquals("withheld_partial_or_occluded", clipped.get("textPhase").getAsString());
        assertEquals(20, clipped.get("x").getAsDouble());
        assertEquals(50, clipped.get("width").getAsDouble());
        assertEquals(13.25, nodes.get(2).getAsJsonObject().get("x").getAsDouble());
        assertEquals(25, nodes.get(2).getAsJsonObject().get("y").getAsDouble());
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
    void independentLateBudgetFailsClosedWithoutChangingPlayerListOrOrdinaryHudBudgets() {
        Object world = new Object(), camera = new Object();
        var late = new UiPresentationSnapshot.RenderedHud(128);
        late.beginGui(world, camera, null, null, 320, 240);
        for (int i = 0; i < 128; i++)
            late.nativeText("debug_text", "line", 1, 1, 20, 9, 1, new Matrix4f(), null);
        late.finish(100);
        assertEquals(128, late.sampleGui(world, camera, null, null, 320, 240, 100).size());
        late.beginGui(world, camera, null, null, 320, 240);
        for (int i = 0; i < 129; i++)
            late.nativeText("debug_text", "line", 1, 1, 20, 9, 1, new Matrix4f(), null);
        late.finish(101);
        assertEquals(0, late.sampleGui(world, camera, null, null, 320, 240, 101).size());
        var ordinary = new UiPresentationSnapshot.RenderedHud();
        ordinary.begin(world, camera, null, 320, 240);
        for (int i = 0; i < 33; i++)
            ordinary.nativeText("ordinary", "line", 1, 1, 20, 9, 1, new Matrix4f(), null);
        ordinary.finish(101);
        assertEquals(0, ordinary.sample(world, camera, null, 320, 240, 101).size());
        assertThrows(
                IllegalArgumentException.class, () -> new UiPresentationSnapshot.RenderedHud(257));
    }

    @Test
    void directNativeDrawsKeepTheirArgumentsIntegerReturnAndExactlyOneInvocation()
            throws Exception {
        int delegates = 0;
        for (String name :
                List.of(
                        "BossBarPresentationMixin",
                        "ChatPresentationMixin",
                        "DebugTextPresentationMixin",
                        "EffectsInventoryPresentationMixin",
                        "HudMessagePresentationMixin",
                        "SubtitlePresentationMixin",
                        "TooltipPresentationMixin")) {
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
                                        "net/minecraft/client/gui/GuiGraphics",
                                        "net/minecraft/client/gui/Font")
                                .contains(draw.owner));
                assertTrue(
                        List.of("blitSprite", "drawString", "drawStringWithBackdrop", "drawInBatch")
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
                if (Type.getReturnType(draw.desc).equals(Type.INT_TYPE)) {
                    var stored =
                            assertInstanceOf(VarInsnNode.class, instructions.get(args.length + 1));
                    var loaded =
                            assertInstanceOf(
                                    VarInsnNode.class, instructions.get(instructions.size() - 2));
                    assertEquals(Opcodes.ISTORE, stored.getOpcode());
                    assertEquals(Opcodes.ILOAD, loaded.getOpcode());
                    assertEquals(stored.var, loaded.var);
                    assertEquals(
                            Opcodes.IRETURN, instructions.get(instructions.size() - 1).getOpcode());
                }
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
        assertEquals(11, delegates);
    }

    @Test
    void reviewedDescriptorsExistInTheActualOldNativeClassesNotModernLookalikes() throws Exception {
        String g = "net/minecraft/client/gui/GuiGraphics";
        String font = "Lnet/minecraft/client/gui/Font;",
                sequence = "Lnet/minecraft/util/FormattedCharSequence;";
        String component = "Lnet/minecraft/network/chat/Component;";
        assertNative(
                "net/minecraft/client/gui/components/ChatComponent",
                "render",
                g,
                "drawString",
                "(" + font + sequence + "III)I",
                1);
        assertNative(
                "net/minecraft/client/gui/components/ChatComponent",
                "render",
                g,
                "drawString",
                "(" + font + component + "III)I",
                1);
        assertNative(
                "net/minecraft/client/gui/components/DebugScreenOverlay",
                "renderLines",
                g,
                "drawString",
                "(" + font + "Ljava/lang/String;IIIZ)I",
                1);
        assertNative(
                "net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen",
                "renderLabels",
                g,
                "drawString",
                "(" + font + component + "III)I",
                2);
        assertNative(
                "net/minecraft/client/gui/components/SubtitleOverlay",
                "render",
                g,
                "drawString",
                "(" + font + "Ljava/lang/String;III)I",
                2);
        assertNative(
                "net/minecraft/client/gui/components/SubtitleOverlay",
                "render",
                g,
                "drawString",
                "(" + font + component + "III)I",
                1);
        assertNative(
                "net/minecraft/client/gui/Gui",
                "renderOverlayMessage",
                g,
                "drawStringWithBackdrop",
                "(" + font + component + "IIII)I",
                1);
        for (String method : List.of("renderSavingIndicator", "renderDemoOverlay"))
            assertNative(
                    "net/minecraft/client/gui/Gui",
                    method,
                    g,
                    "drawStringWithBackdrop",
                    "(" + font + component + "IIII)I",
                    1);
        assertNative(
                "net/minecraft/client/gui/screens/inventory/tooltip/ClientTextTooltip",
                "renderText",
                "net/minecraft/client/gui/Font",
                "drawInBatch",
                "("
                        + sequence
                        + "FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
                1);
        assertNative(
                g,
                "renderTooltipInternal",
                "net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent",
                "renderText",
                "("
                        + font
                        + "IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
                1);
    }

    @Test
    void rendererOuterFrameIncludesNativeHudAndScreenTooltipAndDoesNotReadHiddenCaches()
            throws Exception {
        var frame = read("mixin/GuiLatePresentationMixin");
        assertTrue(
                frame.interfaces.contains(
                        "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiPresentationSnapshot$GuiPresentationSource"));
        assertEquals(
                2, frame.methods.stream().filter(method -> annotation(method, "Inject")).count());
        for (String method : List.of("beginGui", "finish"))
            assertTrue(
                    frame.methods.stream()
                            .flatMap(value -> Arrays.stream(value.instructions.toArray()))
                            .anyMatch(
                                    value ->
                                            value instanceof MethodInsnNode call
                                                    && call.name.equals(method)));
        var mixin =
                java.util.stream.Stream.of(frame.visibleAnnotations, frame.invisibleAnnotations)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(value -> value.desc.endsWith("/Mixin;"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                List.of(Type.getObjectType("net/minecraft/client/renderer/GameRenderer")),
                annotationValue(mixin, "value"));
        var nativeRenderer = nativeClass("net/minecraft/client/renderer/GameRenderer");
        var render =
                nativeRenderer.methods.stream()
                        .filter(method -> method.name.equals("render"))
                        .findFirst()
                        .orElseThrow();
        var renderCalls =
                Arrays.stream(render.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        int hud = -1, screen = -1;
        for (int i = 0; i < renderCalls.size(); i++) {
            var call = renderCalls.get(i);
            if (call.owner.equals("net/minecraft/client/gui/Gui") && call.name.equals("render"))
                hud = i;
            if (call.owner.equals("net/neoforged/neoforge/client/ClientHooks")
                    && call.name.equals("drawScreen")) screen = i;
        }
        assertTrue(
                hud >= 0 && screen > hud,
                "Screen tooltip drawing happens after Gui.render and must not be cleared by it");
        var capture = read("runtime/UiNativeText");
        var calls =
                capture.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        for (String source : List.of("pose", "last", "getShaderColor", "scissor", "opacity"))
            assertTrue(calls.stream().anyMatch(call -> call.name.equals(source)), source);
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
                                value ->
                                        value instanceof FieldInsnNode field
                                                && List.of(
                                                                "allMessages",
                                                                "trimmedMessages",
                                                                "serverChunk",
                                                                "audibleSubtitles")
                                                        .contains(field.name)));
        var producer = read("runtime/UiPresentationSnapshot");
        assertTrue(
                producer.methods.stream()
                        .filter(method -> method.name.equals("activeGuiPresentation"))
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .anyMatch(
                                value ->
                                        value instanceof FieldInsnNode field
                                                && field.name.equals("gameRenderer")));
    }

    @Test
    void tooltipOwnerScopeIsReentrantAndAlwaysUnwindsWithoutResubmittingNativeDraw()
            throws Exception {
        var field = UiNativeText.class.getDeclaredField("TOOLTIP");
        field.setAccessible(true);
        var scope = assertInstanceOf(ThreadLocal.class, field.get(null));
        ClientTooltipComponent outer = tooltipComponent(), inner = tooltipComponent();
        var calls = new AtomicInteger();
        assertNull(scope.get());
        UiNativeText.tooltipDraw(
                null,
                outer,
                () -> {
                    Object previous = scope.get();
                    assertNotNull(previous);
                    calls.incrementAndGet();
                    UiNativeText.tooltipText(new Object(), null, null, 0, 0, 0, null);
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    UiNativeText.tooltipDraw(
                                            null,
                                            inner,
                                            () -> {
                                                assertNotSame(previous, scope.get());
                                                calls.incrementAndGet();
                                                throw new IllegalStateException(
                                                        "native draw failed");
                                            }));
                    assertSame(previous, scope.get());
                });
        assertEquals(2, calls.get());
        assertNull(scope.get());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UiNativeText.tooltipDraw(
                                null,
                                outer,
                                () -> {
                                    calls.incrementAndGet();
                                    throw new IllegalArgumentException("native error");
                                }));
        assertEquals(3, calls.get());
        assertNull(scope.get());
        UiNativeText.tooltipText(outer, null, null, 0, 0, 0, null);
    }

    @Test
    void typedTooltipWrapperForwardsTheNativeInterfaceCallOnceAndClearsItsActualOwnerInFinally()
            throws Exception {
        var wrapper = read("mixin/GuiGraphicsTooltipPresentationMixin");
        var lambda =
                wrapper.methods.stream()
                        .filter(method -> method.name.startsWith("lambda$"))
                        .findFirst()
                        .orElseThrow();
        var instructions =
                Arrays.stream(lambda.instructions.toArray())
                        .filter(value -> value.getOpcode() >= 0)
                        .toList();
        Type[] args = Type.getArgumentTypes(lambda.desc);
        int local = 0;
        for (int index = 0; index < args.length; index++) {
            var load = assertInstanceOf(VarInsnNode.class, instructions.get(index));
            assertEquals(args[index].getOpcode(Opcodes.ILOAD), load.getOpcode());
            assertEquals(local, load.var);
            local += args[index].getSize();
        }
        var draw = assertInstanceOf(MethodInsnNode.class, instructions.get(args.length));
        assertEquals(
                "net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent",
                draw.owner);
        assertEquals("renderText", draw.name);
        assertEquals(
                List.of(args).subList(1, args.length), List.of(Type.getArgumentTypes(draw.desc)));
        assertEquals(
                1,
                wrapper.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(draw.owner)
                                                && call.name.equals(draw.name))
                        .count());
        var capture = read("runtime/UiNativeText");
        var scope =
                capture.methods.stream()
                        .filter(method -> method.name.equals("tooltipDraw"))
                        .findFirst()
                        .orElseThrow();
        assertFalse(scope.tryCatchBlocks.isEmpty());
        assertEquals(
                1,
                Arrays.stream(scope.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals("java/lang/Runnable")
                                                && call.name.equals("run"))
                        .count());
        assertTrue(
                Arrays.stream(scope.instructions.toArray())
                        .anyMatch(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals("java/lang/ThreadLocal")
                                                && call.name.equals("remove")));
        var observer =
                capture.methods.stream()
                        .filter(method -> method.name.equals("tooltipText"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                Arrays.stream(observer.instructions.toArray())
                        .anyMatch(
                                value ->
                                        value.getOpcode() == Opcodes.IF_ACMPEQ
                                                || value.getOpcode() == Opcodes.IF_ACMPNE));
    }

    @Test
    void actualNativeBossSpriteLayersRemainOrderedWithoutReadingRawBossState() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object();
        frame.beginGui(world, camera, null, null, 640, 480);
        String[] resources = {
            "red_background",
            "red_progress",
            "notched_10_background",
            "notched_10_progress",
            "blue_background",
            "blue_progress"
        };
        for (int index = 0; index < resources.length; index++)
            frame.bossSprite(
                    "minecraft:boss_bar/" + resources[index],
                    229,
                    index < 4 ? 12 : 31,
                    index % 2 == 0 ? 182 : 91,
                    5);
        frame.bossSprite("minecraft:boss_bar/red_background", -1, 12, 182, 5);
        frame.bossSprite("minecraft:boss_bar/red_progress", 229, 12, 0, 5);
        frame.finish(100);
        var nodes = frame.sampleGui(world, camera, null, null, 640, 480, 100);
        assertEquals(resources.length, nodes.size());
        for (int index = 0; index < resources.length; index++) {
            var node = nodes.get(index).getAsJsonObject();
            assertEquals(
                    "minecraft:boss_bar/" + resources[index], node.get("resourceId").getAsString());
            assertEquals("boss_bar", node.get("renderPhase").getAsString());
            assertEquals(index % 2 == 0 ? 182 : 91, node.get("width").getAsInt());
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
    void realNativeBossOverloadOwnsTwoReviewedDrawSitesAndTheAdapterReadsOnlyDrawArguments()
            throws Exception {
        var nativeBoss = nativeClass("net/minecraft/client/gui/components/BossHealthOverlay");
        var overload =
                nativeBoss.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("drawBar")
                                                && method.desc.equals(
                                                        "(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/ResourceLocation;[Lnet/minecraft/resources/ResourceLocation;)V"))
                        .findFirst()
                        .orElseThrow();
        String descriptor = "(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V";
        assertEquals(
                2,
                Arrays.stream(overload.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/client/gui/GuiGraphics")
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

    private static ClientTooltipComponent tooltipComponent() {
        return new ClientTooltipComponent() {
            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getWidth(Font font) {
                return 0;
            }
        };
    }

    private void assertNative(
            String owner, String name, String drawOwner, String draw, String descriptor, int count)
            throws Exception {
        var nativeType = nativeClass(owner);
        var method =
                nativeType.methods.stream()
                        .filter(value -> value.name.equals(name))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                count,
                Arrays.stream(method.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(drawOwner)
                                                && call.name.equals(draw)
                                                && call.desc.equals(descriptor))
                        .count(),
                owner + "." + name);
    }

    private static boolean annotation(MethodNode method, String name) {
        return annotations(method).stream()
                .anyMatch(
                        value ->
                                value.desc.equals(
                                        "Lorg/spongepowered/asm/mixin/injection/" + name + ";"));
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        return java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(key)) return annotation.values.get(index + 1);
        return null;
    }

    private ClassNode nativeClass(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private ClassNode read(String name) throws Exception {
        return nativeClass("io/github/recrivenvi/minecraftprotocol/probe/" + name);
    }
}
