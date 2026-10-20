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

class HudCameraPresentationTest {
    @Test
    void legacyCrosshairAndAttackBarsDescribeOnlyActualAtlasRegionsAndPixelExtents() {
        var frame = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object();
        frame.begin(world, camera, null, 320, 240);
        String texture = "minecraft:textures/gui/icons.png";
        frame.legacyCrosshairSprite("minecraft:textures/gui/widgets.png", 10, 10, 0, 0, 15, 15);
        frame.legacyCrosshairSprite(texture, 10, 10, 1, 0, 15, 15);
        frame.legacyCrosshairSprite(texture, 10, 10, 52, 94, 17, 4);
        frame.legacyCrosshairSprite(texture, 10, 10, 18, 94, 18, 8);
        frame.legacyCrosshairSprite(texture, 152, 112, 0, 0, 15, 15);
        frame.legacyCrosshairSprite(texture, 152, 129, 36, 94, 16, 4);
        frame.legacyCrosshairSprite(texture, 152, 129, 52, 94, 9, 4);
        frame.legacyCrosshairSprite(texture, 152, 129, 68, 94, 16, 16);
        frame.legacyCrosshairSprite(texture, 260, 210, 0, 94, 18, 18);
        frame.legacyCrosshairSprite(texture, 260, 220, 18, 104, 18, 8);
        frame.finish(100);
        var nodes = frame.sample(world, camera, null, 320, 240, 100);
        assertEquals(6, nodes.size());
        var crosshair = nodes.get(0).getAsJsonObject();
        assertEquals("crosshair", crosshair.get("renderPhase").getAsString());
        assertEquals("hud/crosshair", crosshair.get("spriteRegion").getAsString());
        assertEquals("status_icon", nodes.get(1).getAsJsonObject().get("role").getAsString());
        var horizontal = nodes.get(2).getAsJsonObject();
        assertEquals(9, horizontal.get("value").getAsInt());
        assertEquals(16, horizontal.get("valueMaximum").getAsInt());
        assertEquals(52, horizontal.get("textureU").getAsInt());
        assertEquals(94, horizontal.get("textureV").getAsInt());
        var vertical = nodes.get(5).getAsJsonObject();
        assertEquals(8, vertical.get("value").getAsInt());
        assertEquals(18, vertical.get("valueMaximum").getAsInt());
        assertEquals(104, vertical.get("textureV").getAsInt());
        assertEquals(
                "drawn_fill_pixels_not_raw_attack_strength",
                vertical.get("valueSemantics").getAsString());
        for (var value : nodes) {
            var node = value.getAsJsonObject();
            assertEquals(texture, node.get("textureId").getAsString());
            assertEquals(256, node.get("atlasWidth").getAsInt());
            assertEquals(256, node.get("atlasHeight").getAsInt());
            assertEquals(
                    "native_gui_icons_atlas_region", node.get("spriteSemantics").getAsString());
            assertFalse(node.has("resourceId"));
            assertFalse(node.get("active").getAsBoolean());
            assertEquals(0, node.getAsJsonArray("actions").size());
            for (String privateField :
                    List.of("attackStrength", "cooldownTicks", "targetEntity", "uuid", "nbt"))
                assertFalse(node.has(privateField));
        }
    }

    @Test
    void overlaysPreserveNativeFractionalTransformAndClipOnlyTheirVisibleQuadBounds() {
        var frame = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object();
        frame.begin(world, camera, null, 320, 240);
        frame.cameraOverlay(
                "spyglass",
                "minecraft:textures/misc/spyglass_scope.png",
                -20,
                -10,
                360,
                260,
                0x80FFFFFF,
                1,
                0,
                0,
                1,
                0.25F,
                0.5F);
        frame.cameraOverlay(
                "texture",
                "minecraft:textures/misc/nausea.png",
                0,
                0,
                100,
                80,
                0xFFFFFFFF,
                1.5F,
                0,
                0,
                1.5F,
                10.25F,
                20.5F);
        frame.cameraOverlay(
                "portal",
                "minecraft:block/nether_portal",
                0,
                0,
                320,
                240,
                0x00FFFFFF,
                1,
                0,
                0,
                1,
                0,
                0);
        frame.cameraOverlay("texture", "offscreen", 400, 0, 20, 20, 0xFFFFFFFF, 1, 0, 0, 1, 0, 0);
        frame.cameraOverlay(
                "texture", "invalid", 0, 0, 20, 20, 0xFFFFFFFF, Float.NaN, 0, 0, 1, 0, 0);
        frame.finish(100);
        var nodes = frame.sample(world, camera, null, 320, 240, 100);
        assertEquals(2, nodes.size());
        var spyglass = nodes.get(0).getAsJsonObject();
        assertEquals(0, spyglass.get("x").getAsDouble());
        assertEquals(0, spyglass.get("y").getAsDouble());
        assertEquals(320, spyglass.get("width").getAsDouble());
        assertEquals(240, spyglass.get("height").getAsDouble());
        assertEquals(128 / 255.0, spyglass.get("opacity").getAsDouble());
        assertEquals(
                "native_transformed_quad_visible_aabb",
                spyglass.get("boundsSemantics").getAsString());
        var nausea = nodes.get(1).getAsJsonObject();
        assertEquals(10.25, nausea.get("x").getAsDouble());
        assertEquals(20.5, nausea.get("y").getAsDouble());
        assertEquals(150, nausea.get("width").getAsDouble());
        assertEquals(120, nausea.get("height").getAsDouble());
        frame.begin(world, camera, null, 320, 240);
        frame.finish(101);
        assertEquals(0, frame.sample(world, camera, null, 320, 240, 101).size());
    }

    @Test
    void hudBoundVisibilityUsesNativeF1AndScreenRulesWithTheActuallyDrawnSleepException() {
        for (String phase : List.of("", "crosshair", "attack_indicator", "camera_overlay")) {
            assertTrue(
                    UiPresentationSnapshot.renderedHudVisible(phase, false, false, false, false));
            assertFalse(
                    UiPresentationSnapshot.renderedHudVisible(phase, true, false, false, false));
            assertFalse(UiPresentationSnapshot.renderedHudVisible(phase, false, true, true, false));
            assertFalse(
                    UiPresentationSnapshot.renderedHudVisible(phase, false, false, false, true));
        }
        assertTrue(
                UiPresentationSnapshot.renderedHudVisible(
                        "sleep_overlay", true, false, false, false));
        assertTrue(
                UiPresentationSnapshot.renderedHudVisible(
                        "sleep_overlay", true, true, true, false));
        assertFalse(
                UiPresentationSnapshot.renderedHudVisible(
                        "sleep_overlay", false, true, false, false));
        assertFalse(
                UiPresentationSnapshot.renderedHudVisible(
                        "sleep_overlay", false, true, true, true));
    }

    @Test
    void legacyOverlayReadsTheActualShaderAlphaAndDepthAwareNativePoseWithoutTouchingRenderState()
            throws Exception {
        try (InputStream input =
                getClass()
                        .getResourceAsStream(
                                "/io/github/recrivenvi/minecraftprotocol/probe/mixin/HudCameraPresentationMixin.class")) {
            assertNotNull(input);
            ClassNode compiled = new ClassNode();
            new ClassReader(input)
                    .accept(compiled, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var calls =
                    compiled.methods.stream()
                            .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                            .filter(MethodInsnNode.class::isInstance)
                            .map(MethodInsnNode.class::cast)
                            .toList();
            for (String actual :
                    List.of(
                            "getShaderColor",
                            "last",
                            "pose",
                            "m20",
                            "m21",
                            "m30",
                            "m31",
                            "m03",
                            "m13",
                            "m23",
                            "m33"))
                assertTrue(calls.stream().anyMatch(call -> call.name.equals(actual)), actual);
            assertFalse(
                    calls.stream()
                            .anyMatch(
                                    call ->
                                            List.of(
                                                            "setShaderColor",
                                                            "getAttackStrengthScale",
                                                            "getPortalCooldown")
                                                    .contains(call.name)));
        }
    }

    @Test
    void everyNativeRedirectForwardsOriginalArgumentsOnceBeforeObservation() throws Exception {
        int delegates = 0;
        for (String mixin :
                List.of("HudCameraPresentationMixin", "ForgeHudSleepPresentationMixin")) {
            ClassNode compiled =
                    compiled("io/github/recrivenvi/minecraftprotocol/probe/mixin/" + mixin);
            for (MethodNode method : compiled.methods) {
                boolean redirect =
                        java.util.stream.Stream.of(
                                        method.visibleAnnotations, method.invisibleAnnotations)
                                .filter(java.util.Objects::nonNull)
                                .flatMap(List::stream)
                                .anyMatch(
                                        a ->
                                                a.desc.equals(
                                                        "Lorg/spongepowered/asm/mixin/injection/Redirect;"));
                if (!redirect) continue;
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
                assertEquals("net/minecraft/client/gui/GuiGraphics", draw.owner);
                assertTrue(List.of("blit", "fill").contains(draw.name));
                Type[] original = Type.getArgumentTypes(method.desc);
                assertEquals(
                        List.of(original).subList(1, original.length),
                        List.of(Type.getArgumentTypes(draw.desc)));
                int local = 1;
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
            }
        }
        assertEquals(6, delegates);
    }

    @Test
    void hooksMatchActualLegacyDrawOverloadsAndForgeOwnsTheSleepFill() throws Exception {
        ClassNode gui = compiled("net/minecraft/client/gui/Gui");
        String atlas = "(Lnet/minecraft/resources/ResourceLocation;IIIIII)V";
        String texture = "(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V";
        assertNativeCall(gui, "renderCrosshair", "blit", atlas);
        assertNativeCall(gui, "renderHotbar", "blit", atlas);
        for (String method :
                List.of("renderTextureOverlay", "renderVignette", "renderSpyglassOverlay"))
            assertNativeCall(gui, method, "blit", texture);
        assertNativeCall(
                gui,
                "renderSpyglassOverlay",
                "fill",
                "(Lnet/minecraft/client/renderer/RenderType;IIIIII)V");
        assertNativeCall(
                gui,
                "renderPortalOverlay",
                "blit",
                "(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V");
        assertFalse(
                gui.methods.stream().anyMatch(method -> method.name.equals("renderSleepOverlay")));
        assertNativeCall(
                compiled("net/minecraftforge/client/gui/overlay/ForgeGui"),
                "renderSleepFade",
                "fill",
                "(Lnet/minecraft/client/renderer/RenderType;IIIII)V");
        ClassNode sleep =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/mixin/ForgeHudSleepPresentationMixin");
        MethodNode hook =
                sleep.methods.stream()
                        .filter(method -> method.name.endsWith("$sleep"))
                        .findFirst()
                        .orElseThrow();
        AnnotationNode redirect =
                java.util.stream.Stream.of(hook.visibleAnnotations, hook.invisibleAnnotations)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(
                                annotation ->
                                        annotation.desc.equals(
                                                "Lorg/spongepowered/asm/mixin/injection/Redirect;"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(false, annotationValue(redirect, "remap"));
        AnnotationNode point =
                assertInstanceOf(AnnotationNode.class, annotationValue(redirect, "at"));
        assertEquals(true, annotationValue(point, "remap"));
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

    private static void assertNativeCall(
            ClassNode owner, String method, String name, String descriptor) {
        assertTrue(
                owner.methods.stream()
                        .filter(value -> value.name.equals(method))
                        .flatMap(value -> Arrays.stream(value.instructions.toArray()))
                        .anyMatch(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/client/gui/GuiGraphics")
                                                && call.name.equals(name)
                                                && call.desc.equals(descriptor)),
                method + " " + name + descriptor);
    }

    private static ClassNode compiled(String name) throws Exception {
        try (InputStream input =
                HudCameraPresentationTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode compiled = new ClassNode();
            new ClassReader(input)
                    .accept(compiled, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return compiled;
        }
    }
}
