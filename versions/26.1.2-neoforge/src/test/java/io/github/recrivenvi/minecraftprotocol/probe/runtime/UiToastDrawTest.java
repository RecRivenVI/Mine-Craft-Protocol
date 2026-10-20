package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.core.UiToastDraw;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class UiToastDrawTest {
    @Test
    void requiresACompletedResidentDrawAndTheSameOwnerWorldCameraScreenAndSize() {
        var draw = new UiToastDraw();
        Object graphics = new Object(),
                owner = new Object(),
                world = new Object(),
                camera = new Object(),
                screen = new Object();
        assertNull(draw.current(owner, world, camera, screen, 320, 240, 100));
        draw.capture(
                graphics,
                owner,
                world,
                camera,
                screen,
                320,
                240,
                0.25,
                0.5,
                1,
                false,
                () -> 100,
                () -> {
                    UiToastDraw.background(graphics, 0, 0, 137, 30);
                    UiToastDraw.text(graphics, "actually submitted", 0xFFFFFFFF);
                    assertNull(draw.current(owner, world, camera, screen, 320, 240, 100));
                });
        var frame = draw.current(owner, world, camera, screen, 320, 240, 100);
        assertNotNull(frame);
        assertEquals("actually submitted", frame.label());
        assertEquals(0.25, frame.x());
        assertEquals(0.5, frame.y());
        assertEquals(137, frame.width());
        assertEquals(30, frame.height());
        assertNull(draw.current(new Object(), world, camera, screen, 320, 240, 100));
        assertNull(draw.current(owner, new Object(), camera, screen, 320, 240, 100));
        assertNull(draw.current(owner, world, new Object(), screen, 320, 240, 100));
        assertNull(draw.current(owner, world, camera, new Object(), 320, 240, 100));
        assertNull(draw.current(owner, world, camera, null, 320, 240, 100));
        assertNull(draw.current(owner, world, camera, screen, 321, 240, 100));
        assertNull(draw.current(owner, world, camera, screen, 320, 241, 100));
        assertNull(draw.current(owner, world, camera, screen, 320, 240, 99));
        assertNotNull(draw.current(owner, world, camera, screen, 320, 240, 250_000_100));
        assertNull(draw.current(owner, world, camera, screen, 320, 240, 250_000_101));
    }

    @Test
    void previewsAndWrongGraphicsCannotPopulateResidentTextAndNestedScopesRestore() {
        var outer = new UiToastDraw();
        var inner = new UiToastDraw();
        Object graphics = new Object(),
                otherGraphics = new Object(),
                owner = new Object(),
                innerOwner = new Object();
        UiToastDraw.background(graphics, 0, 0, 160, 32);
        UiToastDraw.text(graphics, "settings preview", 0xFFFFFFFF);
        assertNull(outer.current(owner, null, null, null, 320, 240, 100));
        outer.capture(
                graphics,
                owner,
                null,
                null,
                null,
                320,
                240,
                160,
                0,
                1,
                false,
                () -> 100,
                () -> {
                    UiToastDraw.background(graphics, 0, 0, 160, 32);
                    UiToastDraw.text(otherGraphics, "wrong graphics", 0xFFFFFFFF);
                    UiToastDraw.text(graphics, "outer first", 0xFFFFFFFF);
                    inner.capture(
                            otherGraphics,
                            innerOwner,
                            null,
                            null,
                            null,
                            320,
                            240,
                            0,
                            0,
                            1,
                            false,
                            () -> 100,
                            () -> {
                                UiToastDraw.background(otherGraphics, 0, 0, 90, 30);
                                UiToastDraw.text(graphics, "wrong nested owner", 0xFFFFFFFF);
                                UiToastDraw.text(otherGraphics, "inner only", 0xFFFFFFFF);
                            });
                    UiToastDraw.text(graphics, "outer second", 0xFFFFFFFF);
                });
        UiToastDraw.text(graphics, "after resident return", 0xFFFFFFFF);
        assertEquals(
                "outer first\nouter second",
                outer.current(owner, null, null, null, 320, 240, 100).label());
        assertEquals(
                "inner only", inner.current(innerOwner, null, null, null, 320, 240, 100).label());
    }

    @Test
    void failedDrawInvalidatesPriorTextAndUnwindsNativeScopeEvenWithReentry() {
        var draw = new UiToastDraw();
        var other = new UiToastDraw();
        Object graphics = new Object(), owner = new Object();
        assertThrows(
                IllegalStateException.class,
                () ->
                        draw.capture(
                                graphics,
                                owner,
                                null,
                                null,
                                null,
                                320,
                                240,
                                0,
                                0,
                                1,
                                false,
                                () -> 100,
                                () -> {
                                    draw.capture(
                                            graphics,
                                            owner,
                                            null,
                                            null,
                                            null,
                                            320,
                                            240,
                                            0,
                                            0,
                                            1,
                                            false,
                                            () -> 100,
                                            () -> {
                                                UiToastDraw.background(graphics, 0, 0, 160, 32);
                                                UiToastDraw.text(
                                                        graphics, "nested sample", 0xFFFFFFFF);
                                            });
                                    throw new IllegalStateException("native draw failed");
                                }));
        assertNull(draw.current(owner, null, null, null, 320, 240, 100));
        UiToastDraw.text(graphics, "must not leak scope", 0xFFFFFFFF);
        other.capture(
                graphics,
                owner,
                null,
                null,
                null,
                320,
                240,
                0,
                0,
                1,
                false,
                () -> 101,
                () -> {
                    UiToastDraw.background(graphics, 0, 0, 160, 32);
                    UiToastDraw.text(graphics, "fresh draw", 0xFFFFFFFF);
                });
        assertEquals("fresh draw", other.current(owner, null, null, null, 320, 240, 101).label());
    }

    @Test
    void absentBackgroundTransparentTextAndOverflowDoNotExposeUndrawnLabels() {
        var draw = new UiToastDraw();
        Object graphics = new Object(), owner = new Object();
        draw.capture(
                graphics,
                owner,
                null,
                null,
                null,
                320,
                240,
                0,
                0,
                1,
                false,
                () -> 100,
                () -> UiToastDraw.text(graphics, "no song/background branch", 0xFFFFFFFF));
        assertNull(draw.current(owner, null, null, null, 320, 240, 100));
        draw.capture(
                graphics,
                owner,
                null,
                null,
                null,
                320,
                240,
                0,
                0,
                1,
                false,
                () -> 100,
                () -> {
                    UiToastDraw.background(graphics, 0, 0, 160, 32);
                    UiToastDraw.text(graphics, "transparent", 0x00FFFFFF);
                });
        assertEquals("", draw.current(owner, null, null, null, 320, 240, 100).label());
        draw.capture(
                graphics,
                owner,
                null,
                null,
                null,
                320,
                240,
                0,
                0,
                1,
                false,
                () -> 100,
                () -> {
                    UiToastDraw.background(graphics, 0, 0, 160, 32);
                    for (int i = 0; i < 65; i++) UiToastDraw.text(graphics, "drawn", 0xFFFFFFFF);
                });
        assertNull(draw.current(owner, null, null, null, 320, 240, 100));
    }

    @Test
    void partialNativeSlidesExposeOnlyStageAndClippedBackgroundNeverUnseenText() {
        for (boolean hiding : new boolean[] {false, true}) {
            var sample =
                    new UiToastDraw.Sample(
                            "not all characters visible",
                            -70.25,
                            0,
                            140,
                            30,
                            .5F,
                            hiding ? "exiting" : "entering");
            var node =
                    UiPresentationSnapshot.toastDrawNode(
                            "toast.now_playing",
                            "native.NowPlayingToast",
                            sample,
                            true,
                            .5F,
                            320,
                            240);
            assertNotNull(node);
            assertEquals(hiding ? "exiting" : "entering", node.get("renderPhase").getAsString());
            assertTrue(node.get("visible").getAsBoolean());
            assertEquals("", node.get("label").getAsString());
            assertEquals("withheld_partial_or_occluded", node.get("textPhase").getAsString());
            assertEquals(0, node.get("x").getAsDouble());
            assertEquals(69.75, node.get("width").getAsDouble());
            assertEquals(
                    "native_toast_draw_viewport_intersection",
                    node.get("boundsSemantics").getAsString());
        }
        var full = new UiToastDraw.Sample("actual complete text", 0, 0, 140, 30, 1, "fully_shown");
        assertEquals(
                "actual complete text",
                UiPresentationSnapshot.toastDrawNode(
                                "toast.now_playing",
                                "native.NowPlayingToast",
                                full,
                                true,
                                1,
                                320,
                                240)
                        .get("label")
                        .getAsString());
        assertEquals(
                "",
                UiPresentationSnapshot.toastDrawNode(
                                "toast.now_playing",
                                "native.NowPlayingToast",
                                full,
                                true,
                                .99F,
                                320,
                                240)
                        .get("label")
                        .getAsString());
        assertEquals(
                "",
                UiPresentationSnapshot.toastDrawNode(
                                "toast.now_playing",
                                "native.NowPlayingToast",
                                full,
                                false,
                                1,
                                320,
                                240)
                        .get("label")
                        .getAsString());
        assertEquals(
                "",
                UiPresentationSnapshot.toastDrawNode(
                                "toast.now_playing",
                                "native.NowPlayingToast",
                                full,
                                true,
                                1,
                                100,
                                240)
                        .get("label")
                        .getAsString());
        assertNull(
                UiPresentationSnapshot.toastDrawNode(
                        "toast.now_playing",
                        "native.NowPlayingToast",
                        new UiToastDraw.Sample("offscreen", -140, 0, 140, 30, 0, "entering"),
                        true,
                        .1F,
                        320,
                        240));
    }

    @Test
    void realNativeTextAndBackgroundDelegatesKeepArgumentsAndObserveAfterOneDraw()
            throws Exception {
        int delegates = 0;
        for (String name : List.of("NowPlayingToastPresentationMixin")) {
            ClassNode compiled = readMixin(name);
            assertTrue(
                    compiled.interfaces.contains(
                            "io/github/recrivenvi/minecraftprotocol/core/UiToastDraw$Source"));
            for (MethodNode method : compiled.methods) {
                if (!redirect(method)) continue;
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
                assertEquals("net/minecraft/client/gui/GuiGraphicsExtractor", draw.owner);
                assertTrue(List.of("text", "blitSprite").contains(draw.name));
                Type[] original = Type.getArgumentTypes(method.desc);
                assertEquals(
                        List.of(original).subList(1, original.length),
                        List.of(Type.getArgumentTypes(draw.desc)));
                int local = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                for (int i = 0; i < original.length; i++) {
                    VarInsnNode load = assertInstanceOf(VarInsnNode.class, instructions.get(i));
                    assertEquals(original[i].getOpcode(Opcodes.ILOAD), load.getOpcode());
                    assertEquals(local, load.var);
                    local += original[i].getSize();
                }
                assertSame(draw, instructions.get(original.length));
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
                                .anyMatch(
                                        i ->
                                                i instanceof MethodInsnNode call
                                                        && call.owner.equals(
                                                                "io/github/recrivenvi/minecraftprotocol/core/UiToastDraw")
                                                        && List.of("text", "background")
                                                                .contains(call.name)));
            }
        }
        assertEquals(2, delegates);
        ClassNode wrapper = readMixin("ToastResidentPresentationMixin");
        var guarded =
                wrapper.methods.stream()
                        .filter(UiToastDrawTest::redirect)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                Arrays.stream(guarded.instructions.toArray())
                        .anyMatch(
                                i ->
                                        i instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "io/github/recrivenvi/minecraftprotocol/core/UiToastDraw")
                                                && call.name.equals("capture")));
        for (String nativeContextMethod :
                List.of("getCameraEntity", "guiWidth", "guiHeight", "m20", "m21"))
            assertTrue(
                    Arrays.stream(guarded.instructions.toArray())
                            .anyMatch(
                                    i ->
                                            i instanceof MethodInsnNode call
                                                    && call.name.equals(nativeContextMethod)),
                    nativeContextMethod);
        assertTrue(
                Arrays.stream(guarded.instructions.toArray())
                        .anyMatch(
                                i ->
                                        i instanceof FieldInsnNode field
                                                && field.owner.equals(
                                                        "net/minecraft/client/Minecraft")
                                                && field.name.equals("screen")));
        assertEquals(
                2,
                wrapper.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .filter(
                                i ->
                                        i instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/client/gui/components/toasts/Toast")
                                                && call.name.equals("extractRenderState"))
                        .count());
    }

    @Test
    void descriptorsMatchActualNativeNowPlayingAndResidentDrawsWithoutInventingFriendSupport()
            throws Exception {
        String graphics = "net/minecraft/client/gui/GuiGraphicsExtractor";
        String pipeline = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;";
        String identifier = "Lnet/minecraft/resources/Identifier;";
        String font = "Lnet/minecraft/client/gui/Font;";
        assertNativeDraw(
                "NowPlayingToast",
                "extractToast",
                graphics,
                "blitSprite",
                "(" + pipeline + identifier + "IIII)V");
        assertNativeDraw(
                "NowPlayingToast",
                "extractToast",
                graphics,
                "text",
                "(" + font + "Lnet/minecraft/network/chat/Component;III)V");
        assertNativeDraw(
                "ToastManager$ToastInstance",
                "extractRenderState",
                "net/minecraft/client/gui/components/toasts/Toast",
                "extractRenderState",
                "(L" + graphics + ";" + font + "J)V");
        assertNull(
                getClass()
                        .getResourceAsStream(
                                "/net/minecraft/client/gui/components/toasts/FriendToast.class"));
    }

    private void assertNativeDraw(
            String owner, String method, String drawOwner, String draw, String descriptor)
            throws Exception {
        try (InputStream bytes =
                getClass()
                        .getResourceAsStream(
                                "/net/minecraft/client/gui/components/toasts/"
                                        + owner
                                        + ".class")) {
            assertNotNull(bytes, owner);
            ClassNode nativeType = new ClassNode();
            new ClassReader(bytes)
                    .accept(nativeType, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertEquals(
                    1,
                    nativeType.methods.stream()
                            .filter(value -> value.name.equals(method))
                            .flatMap(value -> Arrays.stream(value.instructions.toArray()))
                            .filter(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && call.owner.equals(drawOwner)
                                                    && call.name.equals(draw)
                                                    && call.desc.equals(descriptor))
                            .count(),
                    owner + "." + method + " " + descriptor);
        }
    }

    private static boolean redirect(MethodNode method) {
        return java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"));
    }

    private ClassNode readMixin(String name) throws Exception {
        try (InputStream input =
                getClass()
                        .getResourceAsStream(
                                "/io/github/recrivenvi/minecraftprotocol/probe/mixin/"
                                        + name
                                        + ".class")) {
            assertNotNull(input, name);
            ClassNode compiled = new ClassNode();
            new ClassReader(input)
                    .accept(compiled, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return compiled;
        }
    }
}
