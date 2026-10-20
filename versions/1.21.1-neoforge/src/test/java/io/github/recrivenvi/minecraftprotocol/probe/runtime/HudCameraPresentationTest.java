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
    void crosshairAndAttackBarsAreOnlyTheActualNativeSpritesAndPixelExtents() {
        var frame = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object();
        frame.begin(world, camera, null, 320, 240);
        frame.crosshairSprite("minecraft:hud/hotbar", 60, 210, 182, 22, 0, 0);
        frame.crosshairSprite("minecraft:hud/crosshair", 152, 112, 15, 15, 0, 0);
        frame.crosshairSprite(
                "minecraft:hud/crosshair_attack_indicator_background", 152, 129, 16, 4, 0, 0);
        frame.crosshairSprite(
                "minecraft:hud/crosshair_attack_indicator_progress", 152, 129, 9, 4, 16, 4);
        frame.crosshairSprite(
                "minecraft:hud/hotbar_attack_indicator_progress", 260, 220, 18, 8, 18, 18);
        frame.finish(100);
        var nodes = frame.sample(world, camera, null, 320, 240, 100);
        assertEquals(4, nodes.size());
        assertEquals("crosshair", nodes.get(0).getAsJsonObject().get("renderPhase").getAsString());
        assertEquals("status_icon", nodes.get(1).getAsJsonObject().get("role").getAsString());
        var horizontal = nodes.get(2).getAsJsonObject();
        assertEquals(9, horizontal.get("value").getAsInt());
        assertEquals(16, horizontal.get("valueMaximum").getAsInt());
        var vertical = nodes.get(3).getAsJsonObject();
        assertEquals(8, vertical.get("value").getAsInt());
        assertEquals(18, vertical.get("valueMaximum").getAsInt());
        assertEquals(
                "drawn_fill_pixels_not_raw_attack_strength",
                vertical.get("valueSemantics").getAsString());
        for (var value : nodes) {
            var node = value.getAsJsonObject();
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
        try (InputStream input =
                getClass()
                        .getResourceAsStream(
                                "/io/github/recrivenvi/minecraftprotocol/probe/mixin/HudCameraPresentationMixin.class")) {
            assertNotNull(input);
            ClassNode compiled = new ClassNode();
            new ClassReader(input)
                    .accept(compiled, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            int delegates = 0;
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
                assertTrue(List.of("blit", "blitSprite", "fill").contains(draw.name));
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
            assertEquals(7, delegates);
        }
    }
}
