package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class LegacyNauseaPresentationTest {
    private static final String ROOT = "io/github/recrivenvi/minecraftprotocol/probe/";
    private static final String RENDERER = "net/minecraft/client/renderer/GameRenderer";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
    private static final String BLIT = "(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V";

    @Test
    void actualOldNativeNauseaDrawPrecedesTheGuiHideBranch() throws Exception {
        ClassNode nativeRenderer = read(RENDERER);
        MethodNode render = method(nativeRenderer, "render");
        int nausea = callIndex(render, RENDERER, "renderConfusionOverlay");
        int hud = callIndex(render, "net/minecraft/client/gui/Gui", "render");
        assertTrue(nausea >= 0 && hud > nausea);
        var instructions = render.instructions.toArray();
        assertTrue(
                Arrays.stream(instructions)
                        .skip(nausea + 1L)
                        .limit(hud - nausea - 1L)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.owner.equals(
                                                        "net/minecraft/client/Options")
                                                && field.name.equals("hideGui")),
                "The native GUI visibility decision comes after the real nausea call");
        MethodNode nativeNausea = method(nativeRenderer, "renderConfusionOverlay");
        assertEquals(
                1,
                Arrays.stream(nativeNausea.instructions.toArray())
                        .filter(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(GRAPHICS)
                                                && call.name.equals("blit")
                                                && call.desc.equals(BLIT))
                        .count());
    }

    @Test
    void exactNativeBlitIsDelegatedUnchangedOnceBeforeObservingTheRealResource() throws Exception {
        ClassNode adapter = read(ROOT + "mixin/LegacyNauseaPresentationMixin");
        AnnotationNode mixin =
                annotations(adapter.visibleAnnotations, adapter.invisibleAnnotations).stream()
                        .filter(annotation -> annotation.desc.endsWith("/Mixin;"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(((List<?>) value(mixin, "value")).contains(Type.getObjectType(RENDERER)));
        var redirects =
                adapter.methods.stream()
                        .filter(method -> annotation(method, "Redirect") != null)
                        .toList();
        assertEquals(1, redirects.size());
        MethodNode hook = redirects.get(0);
        AnnotationNode redirect = annotation(hook, "Redirect");
        assertEquals(List.of("renderConfusionOverlay"), value(redirect, "method"));
        AnnotationNode at = assertInstanceOf(AnnotationNode.class, value(redirect, "at"));
        assertEquals("Lorg/spongepowered/asm/mixin/injection/At;", at.desc);
        assertEquals("INVOKE", value(at, "value"));
        assertEquals("L" + GRAPHICS + ";blit" + BLIT, value(at, "target"));
        var instructions =
                Arrays.stream(hook.instructions.toArray())
                        .filter(instruction -> instruction.getOpcode() >= 0)
                        .toList();
        MethodInsnNode draw =
                instructions.stream()
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals(GRAPHICS, draw.owner);
        assertEquals("blit", draw.name);
        assertEquals(BLIT, draw.desc);
        Type[] arguments = Type.getArgumentTypes(hook.desc);
        assertEquals(
                List.of(arguments).subList(1, arguments.length),
                List.of(Type.getArgumentTypes(BLIT)));
        int local = 1;
        for (int index = 0; index < arguments.length; index++) {
            VarInsnNode load = assertInstanceOf(VarInsnNode.class, instructions.get(index));
            assertEquals(arguments[index].getOpcode(Opcodes.ILOAD), load.getOpcode());
            assertEquals(local, load.var);
            local += arguments[index].getSize();
        }
        assertSame(draw, instructions.get(arguments.length));
        assertEquals(
                1,
                instructions.stream()
                        .filter(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(GRAPHICS)
                                                && call.name.equals("blit"))
                        .count());
        assertTrue(
                instructions.stream()
                        .skip(arguments.length + 1L)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/resources/ResourceLocation")
                                                && call.name.equals("toString")));
        assertTrue(
                instructions.stream()
                        .skip(arguments.length + 1L)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(ROOT + "runtime/UiNativeText")
                                                && call.name.equals("nausea")));
    }

    @Test
    void productionObserverUsesOnlyTheActualLateOwnerNativeClipPoseAndShaderAlpha()
            throws Exception {
        MethodNode observer = method(read(ROOT + "runtime/UiNativeText"), "nausea");
        assertTrue(calls(observer, ROOT + "runtime/UiNativeText", "scissor"));
        assertTrue(
                calls(observer, ROOT + "runtime/UiPresentationSnapshot", "activeGuiPresentation"));
        assertFalse(
                calls(observer, ROOT + "runtime/UiPresentationSnapshot", "activeHudPresentation"));
        assertTrue(calls(observer, "com/mojang/blaze3d/systems/RenderSystem", "getShaderColor"));
        assertTrue(calls(observer, GRAPHICS, "pose"));
        assertTrue(calls(observer, ROOT + "runtime/UiNativeText$ScissorSample", "rectangle"));
        for (String matrix :
                List.of(
                        "m00", "m01", "m10", "m11", "m20", "m21", "m30", "m31", "m03", "m13", "m23",
                        "m33")) assertTrue(calls(observer, "org/joml/Matrix4f", matrix), matrix);
        assertTrue(
                calls(
                        observer,
                        ROOT + "runtime/UiPresentationSnapshot$RenderedHud",
                        "cameraOverlay"));
        assertTrue(
                Arrays.stream(observer.instructions.toArray())
                        .noneMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && (field.owner.contains("/player/")
                                                        || List.of(
                                                                        "spinningEffectIntensity",
                                                                        "oSpinningEffectIntensity",
                                                                        "portalTime")
                                                                .contains(field.name))));
        assertFalse(calls(observer, "net/minecraft/client/player/LocalPlayer", "hasEffect"));
    }

    @Test
    void actualProducerPreservesFractionalGeometryNativeScissorResourceAndSubmittedAlpha() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object();
        frame.beginGui(world, camera, null, null, 320, 240);
        frame.cameraOverlay(
                "nausea",
                "minecraft:textures/misc/nausea.png",
                0,
                0,
                160,
                120,
                0x80FFFFFF,
                1.5F,
                0,
                0,
                1.5F,
                10.25F,
                20.5F,
                new ScreenRectangle(40, 50, 80, 60));
        assertEquals(0, frame.sampleGui(world, camera, null, null, 320, 240, 100).size());
        frame.finish(100);
        var observed = frame.sampleGui(world, camera, null, null, 320, 240, 100);
        assertEquals(1, observed.size());
        var node = observed.get(0).getAsJsonObject();
        assertEquals("minecraft:textures/misc/nausea.png", node.get("resourceId").getAsString());
        assertEquals("camera_overlay", node.get("renderPhase").getAsString());
        assertEquals(40, node.get("x").getAsDouble());
        assertEquals(50, node.get("y").getAsDouble());
        assertEquals(80, node.get("width").getAsDouble());
        assertEquals(60, node.get("height").getAsDouble());
        assertEquals(128 / 255.0, node.get("opacity").getAsDouble());
        assertEquals(
                "native_transformed_quad_visible_aabb", node.get("boundsSemantics").getAsString());
        assertFalse(node.get("active").getAsBoolean());
        assertEquals(0, node.getAsJsonArray("actions").size());
        for (String hidden :
                List.of("nauseaIntensity", "spinningEffectIntensity", "rawColor", "nbt", "uuid"))
            assertFalse(node.has(hidden), hidden);
        assertEquals(0, frame.sampleGui(world, camera, new Object(), null, 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, null, new Object(), 320, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, null, null, 321, 240, 100).size());
        assertEquals(0, frame.sampleGui(world, camera, null, null, 320, 240, 250_000_101).size());
    }

    @Test
    void lateCameraFollowsTheActualOldF1ExceptionButNeverExposesScreenOrOverlayOccludedDraws()
            throws Exception {
        assertTrue(
                UiPresentationSnapshot.renderedLateVisible(
                        "camera_overlay", false, false, false, false, false));
        assertTrue(
                UiPresentationSnapshot.renderedLateVisible(
                        "camera_overlay", true, false, false, false, false));
        assertFalse(
                UiPresentationSnapshot.renderedLateVisible(
                        "camera_overlay", false, true, false, false, false));
        assertFalse(
                UiPresentationSnapshot.renderedLateVisible(
                        "camera_overlay", false, true, true, false, false));
        assertFalse(
                UiPresentationSnapshot.renderedLateVisible(
                        "camera_overlay", false, true, false, true, false));
        assertFalse(
                UiPresentationSnapshot.renderedLateVisible(
                        "camera_overlay", true, false, false, false, true));
        assertFalse(
                UiPresentationSnapshot.renderedHudVisible(
                        "camera_overlay", true, false, false, false));
        assertFalse(
                UiPresentationSnapshot.renderedLateVisible(
                        "chat_background", false, false, false, false, false));
        ClassNode snapshot = read(ROOT + "runtime/UiPresentationSnapshot");
        assertTrue(
                calls(
                        method(snapshot, "capture"),
                        ROOT + "runtime/UiPresentationSnapshot",
                        "renderedLateVisible"));
        assertTrue(
                calls(
                        method(snapshot, "capture"),
                        ROOT + "runtime/UiPresentationSnapshot$RenderedHud",
                        "sampleGui"));
    }

    @Test
    void emptyScissorTransparentAndNonFiniteNativeCameraGeometryFailClosed() {
        var frame = new UiPresentationSnapshot.RenderedHud(128);
        Object world = new Object(), camera = new Object();
        frame.beginGui(world, camera, null, null, 320, 240);
        frame.cameraOverlay(
                "nausea",
                "native:actual_resource",
                0,
                0,
                160,
                120,
                0xFFFFFFFF,
                1,
                0,
                0,
                1,
                0,
                0,
                ScreenRectangle.empty());
        frame.cameraOverlay(
                "nausea",
                "native:actual_resource",
                0,
                0,
                160,
                120,
                0x00FFFFFF,
                1,
                0,
                0,
                1,
                0,
                0,
                null);
        frame.cameraOverlay(
                "nausea",
                "native:actual_resource",
                0,
                0,
                160,
                120,
                0xFFFFFFFF,
                Float.NaN,
                0,
                0,
                1,
                0,
                0,
                null);
        frame.finish(100);
        assertEquals(0, frame.sampleGui(world, camera, null, null, 320, 240, 100).size());
    }

    private static List<AnnotationNode> annotations(
            List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        return java.util.stream.Stream.of(visible, invisible)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private static AnnotationNode annotation(MethodNode method, String kind) {
        return annotations(method.visibleAnnotations, method.invisibleAnnotations).stream()
                .filter(
                        annotation ->
                                annotation.desc.equals(
                                        "Lorg/spongepowered/asm/mixin/injection/" + kind + ";"))
                .findFirst()
                .orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(key)) return annotation.values.get(index + 1);
        return null;
    }

    private static ClassNode read(String owner) throws Exception {
        try (InputStream input =
                LegacyNauseaPresentationTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input, owner);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        var instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++)
            if (instructions[index] instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)) return index;
        return -1;
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        return callIndex(method, owner, name) >= 0;
    }
}
