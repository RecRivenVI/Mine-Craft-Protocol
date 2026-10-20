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
                        "ForgeHudMessagePresentationMixin",
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
                        List.of("blit", "drawString", "drawStringWithBackdrop", "drawInBatch")
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
                                                                || call.name.equals("bossAtlas"))));
            }
        }
        assertEquals(10, delegates);
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
                "net/minecraftforge/client/gui/overlay/ForgeGui",
                "renderHUDText",
                g,
                "drawString",
                "(" + font + "Ljava/lang/String;IIIZ)I",
                2);
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
                "net/minecraftforge/client/gui/overlay/ForgeGui",
                "renderRecordOverlay",
                g,
                "drawString",
                "(" + font + sequence + "III)I",
                1);
        for (String method : List.of("renderSavingIndicator"))
            assertNative(
                    "net/minecraft/client/gui/Gui",
                    method,
                    g,
                    "drawString",
                    "(" + font + component + "III)I",
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
        var overlay =
                read("mixin/ForgeHudMessagePresentationMixin").methods.stream()
                        .filter(method -> method.name.endsWith("$overlayMessage"))
                        .findFirst()
                        .orElseThrow();
        var redirect =
                annotations(overlay).stream()
                        .filter(value -> value.desc.endsWith("/Redirect;"))
                        .findFirst()
                        .orElseThrow();
        Object point = annotationValue(redirect, "at");
        if (point instanceof List<?> points) point = points.get(0);
        assertNull(annotationValue(assertInstanceOf(AnnotationNode.class, point), "ordinal"));
    }

    @Test
    void forgeDrawsF3DemoAndRecordOverlayOnlyThroughTheirActualConditionalNativePaths()
            throws Exception {
        String forge = "net/minecraftforge/client/gui/overlay/ForgeGui";
        var nativeForge = nativeClass(forge);
        for (String name : List.of("renderHUDText", "renderRecordOverlay")) {
            MethodNode nativeMethod =
                    nativeForge.methods.stream()
                            .filter(method -> method.name.equals(name))
                            .findFirst()
                            .orElseThrow();
            assertFalse(
                    Arrays.stream(nativeMethod.instructions.toArray())
                            .anyMatch(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && call.name.equals("renderLines")));
            assertTrue(
                    nativeClass("net/minecraftforge/client/gui/overlay/VanillaGuiOverlay")
                            .methods
                            .stream()
                            .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                            .anyMatch(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && call.owner.equals(forge)
                                                    && call.name.equals(name)
                                                    && call.desc.equals(
                                                            name.equals("renderHUDText")
                                                                    ? "(IILnet/minecraft/client/gui/GuiGraphics;)V"
                                                                    : "(IIFLnet/minecraft/client/gui/GuiGraphics;)V")));
        }
        MethodNode nativeText =
                nativeForge.methods.stream()
                        .filter(method -> method.name.equals("renderHUDText"))
                        .findFirst()
                        .orElseThrow();
        var nativeCalls =
                Arrays.stream(nativeText.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        assertTrue(
                nativeCalls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("net/minecraft/client/Minecraft")
                                                && call.name.equals("isDemo")
                                                && call.desc.equals("()Z")));
        assertTrue(
                nativeCalls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(
                                                        "net/minecraft/client/resources/language/I18n")
                                                && call.name.equals("get")));
        assertFalse(nativeCalls.stream().anyMatch(call -> call.name.equals("renderDemoOverlay")));
        var forgeDebug = nativeClass(forge + "$ForgeDebugScreenOverlay");
        for (String name : List.of("drawGameInformation", "drawSystemInformation")) {
            var noDraw =
                    forgeDebug.methods.stream()
                            .filter(method -> method.name.equals(name))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    0,
                    Arrays.stream(noDraw.instructions.toArray())
                            .filter(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && (call.name.equals("renderLines")
                                                            || call.name.startsWith("drawString")))
                            .count());
        }
        for (String mixinName :
                List.of("DebugTextPresentationMixin", "ForgeHudMessagePresentationMixin")) {
            var mixin = read("mixin/" + mixinName);
            var target =
                    java.util.stream.Stream.of(mixin.visibleAnnotations, mixin.invisibleAnnotations)
                            .filter(java.util.Objects::nonNull)
                            .flatMap(List::stream)
                            .filter(value -> value.desc.endsWith("/Mixin;"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(List.of(Type.getObjectType(forge)), annotationValue(target, "value"));
            var handler =
                    mixin.methods.stream()
                            .filter(method -> annotation(method, "Redirect"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    1,
                    mixin.methods.stream()
                            .filter(method -> annotation(method, "Redirect"))
                            .count());
            var redirect =
                    annotations(handler).stream()
                            .filter(value -> value.desc.endsWith("/Redirect;"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    List.of(
                            mixinName.equals("DebugTextPresentationMixin")
                                    ? "renderHUDText"
                                    : "renderRecordOverlay"),
                    annotationValue(redirect, "method"));
            assertEquals(false, annotationValue(redirect, "remap"));
            Object point = annotationValue(redirect, "at");
            if (point instanceof List<?> points) point = points.get(0);
            var at = assertInstanceOf(AnnotationNode.class, point);
            assertEquals(true, annotationValue(at, "remap"));
            assertNull(annotationValue(at, "ordinal"));
        }
        var f3 =
                read("mixin/DebugTextPresentationMixin").methods.stream()
                        .filter(method -> annotation(method, "Redirect"))
                        .findFirst()
                        .orElseThrow();
        var instructions =
                Arrays.stream(f3.instructions.toArray())
                        .filter(instruction -> instruction.getOpcode() >= 0)
                        .toList();
        int flag = -1, debugSample = -1, demoCheck = -1, demoSample = -1;
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index) instanceof FieldInsnNode field
                    && field.owner.equals("net/minecraft/client/Options")
                    && field.name.equals("renderDebug")) {
                assertEquals(-1, flag);
                assertEquals(Opcodes.GETFIELD, field.getOpcode());
                flag = index;
            }
            if (instructions.get(index) instanceof MethodInsnNode call
                    && call.owner.equals(
                            "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiNativeText")
                    && call.name.equals("literal")) {
                LdcInsnNode family =
                        assertInstanceOf(LdcInsnNode.class, instructions.get(index - 7));
                if (family.cst.equals("debug_text")) {
                    assertEquals(-1, debugSample);
                    debugSample = index;
                } else {
                    assertEquals("demo", family.cst);
                    assertEquals(-1, demoSample);
                    demoSample = index;
                }
            }
            if (instructions.get(index) instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/Minecraft")
                    && call.name.equals("isDemo")) {
                assertEquals(-1, demoCheck);
                assertEquals("()Z", call.desc);
                demoCheck = index;
            }
        }
        assertTrue(
                flag >= 0
                        && debugSample > flag
                        && demoCheck > debugSample
                        && demoSample > demoCheck);
        var branch = assertInstanceOf(JumpInsnNode.class, instructions.get(flag + 1));
        assertEquals(Opcodes.IFEQ, branch.getOpcode());
        var allInstructions = Arrays.stream(f3.instructions.toArray()).toList();
        assertTrue(
                allInstructions.indexOf(branch.label)
                                > allInstructions.indexOf(instructions.get(debugSample))
                        && allInstructions.indexOf(branch.label)
                                < allInstructions.indexOf(instructions.get(demoCheck)),
                "Disabled F3 must branch to the separate real demo check, never a debug observation");
        var debugExit = assertInstanceOf(JumpInsnNode.class, instructions.get(debugSample + 1));
        assertEquals(Opcodes.GOTO, debugExit.getOpcode());
        var nonDemoExit = assertInstanceOf(JumpInsnNode.class, instructions.get(demoCheck + 1));
        assertEquals(Opcodes.IFEQ, nonDemoExit.getOpcode());
        assertSame(debugExit.label, nonDemoExit.label);
        assertTrue(
                allInstructions.indexOf(nonDemoExit.label)
                        > allInstructions.indexOf(instructions.get(demoSample)),
                "Non-demo/non-F3 Forge event text must bypass both observations");
        var record =
                read("mixin/ForgeHudMessagePresentationMixin").methods.stream()
                        .filter(method -> annotation(method, "Redirect"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                Arrays.stream(record.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiNativeText")
                                                && call.name.equals("sequence")));
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
            if (call.owner.equals("net/minecraftforge/client/ForgeHooksClient")
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
    void actualLegacyBossAtlasRegionsRemainOrderedWithoutInventedSpriteIdsOrRawBossState()
            throws Exception {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object();
        frame.beginGui(world, camera, null, null, 640, 480);
        int[] rows = {20, 25, 90, 95, 10, 15};
        for (int index = 0; index < rows.length; index++)
            frame.bossAtlas(
                    "minecraft:textures/gui/bars.png",
                    229,
                    index < 4 ? 12 : 31,
                    0,
                    rows[index],
                    index % 2 == 0 ? 182 : 91,
                    5);
        frame.bossAtlas("mod:invented_bars", 229, 12, 0, 20, 182, 5);
        frame.bossAtlas("minecraft:textures/gui/bars.png", -1, 12, 0, 20, 182, 5);
        frame.bossAtlas("minecraft:textures/gui/bars.png", 229, 12, 0, 25, 0, 5);
        frame.bossAtlas("minecraft:textures/gui/bars.png", 229, 12, 200, 20, 182, 5);
        frame.finish(100);
        var nodes = frame.sampleGui(world, camera, null, null, 640, 480, 100);
        assertEquals(rows.length, nodes.size());
        for (int index = 0; index < rows.length; index++) {
            var node = nodes.get(index).getAsJsonObject();
            assertEquals("minecraft:textures/gui/bars.png", node.get("textureId").getAsString());
            assertEquals(0, node.get("textureU").getAsInt());
            assertEquals(rows[index], node.get("textureV").getAsInt());
            assertEquals(256, node.get("atlasWidth").getAsInt());
            assertEquals(256, node.get("atlasHeight").getAsInt());
            assertEquals("native_gui_bars_atlas_region", node.get("spriteSemantics").getAsString());
            assertEquals("boss_bar", node.get("renderPhase").getAsString());
            assertEquals(index % 2 == 0 ? 182 : 91, node.get("width").getAsInt());
            assertFalse(node.get("active").getAsBoolean());
            assertEquals(0, node.getAsJsonArray("actions").size());
            for (String hidden :
                    List.of(
                            "resourceId",
                            "value",
                            "progress",
                            "name",
                            "uuid",
                            "color",
                            "overlay",
                            "darkenScreen")) assertFalse(node.has(hidden));
        }
        var colors = nativeEnumConstants("net/minecraft/world/BossEvent$BossBarColor");
        assertEquals(List.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"), colors);
        var styles = nativeEnumConstants("net/minecraft/world/BossEvent$BossBarOverlay");
        assertEquals(
                List.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20"), styles);
        assertEquals(colors.indexOf("RED") * 10, rows[0]);
        assertEquals(colors.indexOf("BLUE") * 10, rows[4]);
        assertEquals(80 + (styles.indexOf("NOTCHED_10") - 1) * 10, rows[2]);
        assertEquals(rows[0] + 5, rows[1]);
        assertEquals(rows[2] + 5, rows[3]);
        assertEquals(rows[4] + 5, rows[5]);
    }

    private List<String> nativeEnumConstants(String owner) throws Exception {
        var values =
                nativeClass(owner).methods.stream()
                        .filter(method -> method.name.equals("$values"))
                        .findFirst()
                        .orElseThrow();
        return Arrays.stream(values.instructions.toArray())
                .filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast)
                .filter(
                        field ->
                                field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(owner))
                .map(field -> field.name)
                .toList();
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
                                                        "(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;II)V"))
                        .findFirst()
                        .orElseThrow();
        String descriptor = "(Lnet/minecraft/resources/ResourceLocation;IIIIII)V";
        assertEquals(
                2,
                Arrays.stream(overload.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/client/gui/GuiGraphics")
                                                && call.name.equals("blit")
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
                        .filter(call -> call.name.equals("blit") && call.desc.equals(descriptor))
                        .count());
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("activeGuiPresentation")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("bossAtlas")));
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
