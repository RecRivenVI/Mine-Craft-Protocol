package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class HudPresentationTest {
    @Test
    void publishesOnlyCompletedDrawsAtTheSameNativeContextWithinTheFrameBudget() {
        var source = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object(), screen = new Object();
        source.begin(world, camera, null, 320, 240);
        source.selectedName("actual native text", 20, 30, 100, 0x80FFFFFF);
        assertEquals(0, source.sample(world, camera, null, 320, 240, 100).size());
        source.finish(100);
        assertEquals(1, source.sample(world, camera, null, 320, 240, 100).size());
        assertEquals(0, source.sample(new Object(), camera, null, 320, 240, 100).size());
        assertEquals(0, source.sample(world, new Object(), null, 320, 240, 100).size());
        assertEquals(0, source.sample(world, camera, screen, 320, 240, 100).size());
        assertEquals(0, source.sample(world, camera, null, 321, 240, 100).size());
        assertEquals(0, source.sample(world, camera, null, 320, 241, 100).size());
        assertEquals(0, source.sample(world, camera, null, 320, 240, 99).size());
        assertEquals(1, source.sample(world, camera, null, 320, 240, 250_000_100).size());
        assertEquals(0, source.sample(world, camera, null, 320, 240, 250_000_101).size());
        source.sample(world, camera, null, 320, 240, 100)
                .get(0)
                .getAsJsonObject()
                .addProperty("label", "consumer change");
        assertEquals(
                "actual native text",
                source.sample(world, camera, null, 320, 240, 100)
                        .get(0)
                        .getAsJsonObject()
                        .get("label")
                        .getAsString());
        source.begin(world, camera, null, 320, 240);
        assertEquals(0, source.sample(world, camera, null, 320, 240, 100).size());
        source.finish(101);
        assertEquals(0, source.sample(world, camera, null, 320, 240, 101).size());
    }

    @Test
    void itemProgressIsOnlyTheDrawnPixelExtentAndContainsNoPrivateItemState() {
        var source = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object();
        source.begin(world, camera, null, 320, 240);
        source.durabilityBackground(10, 20, 12, 33, 13, 2);
        source.durabilityForeground(10, 20, 7);
        source.cooldown(30, 20, 30, 28, 16, 8, 0x7FFFFFFF);
        source.finish(100);
        // A subsequent callback cannot mutate a completed frame.
        source.durabilityForeground(10, 20, 12);
        JsonArray nodes = source.sample(world, camera, null, 320, 240, 100);
        assertEquals(2, nodes.size());
        var durability = nodes.get(0).getAsJsonObject();
        assertEquals(7, durability.get("value").getAsInt());
        assertEquals(13, durability.get("valueMaximum").getAsInt());
        assertEquals(2, durability.get("height").getAsInt());
        assertEquals(
                "drawn_fill_pixels_not_raw_item_state",
                durability.get("valueSemantics").getAsString());
        var cooldown = nodes.get(1).getAsJsonObject();
        assertEquals(8, cooldown.get("value").getAsInt());
        assertEquals(16, cooldown.get("valueMaximum").getAsInt());
        for (var entry : nodes) {
            var node = entry.getAsJsonObject();
            assertEquals(0, node.getAsJsonArray("actions").size());
            for (String forbidden :
                    List.of(
                            "damage",
                            "maxDamage",
                            "cooldownTicks",
                            "remainingTicks",
                            "nbt",
                            "components")) assertFalse(node.has(forbidden), forbidden);
        }
    }

    @Test
    void omitsTransparentOutOfBoundsAndOverflowingDrawsInsteadOfInventingVisibility() {
        var source = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object();
        source.begin(world, camera, null, 1000, 240);
        source.selectedName("not drawn", 0, 0, 50, 0x00FFFFFF);
        source.selectedName("clipped", -1, 0, 50, 0xFFFFFFFF);
        source.cooldown(0, 0, 0, 0, 16, 8, 0x00FFFFFF);
        source.finish(100);
        assertEquals(0, source.sample(world, camera, null, 1000, 240, 100).size());
        source.begin(world, camera, null, 1000, 240);
        for (int i = 0; i < 33; i++) source.cooldown(i * 20, 0, i * 20, 0, 16, 8, 0x7FFFFFFF);
        source.finish(101);
        assertEquals(0, source.sample(world, camera, null, 1000, 240, 101).size());
    }

    @Test
    void contextualBarsExposeActualSpriteExtentsAndResetWithEachNativeFrame() {
        var source = new UiPresentationSnapshot.RenderedHud();
        Object world = new Object(), camera = new Object();
        source.begin(world, camera, null, 320, 240);
        source.contextualSprite(
                "experience",
                "minecraft:hud/experience_bar_background",
                69,
                211,
                182,
                5,
                0,
                0xFFFFFFFF);
        source.contextualSprite(
                "experience",
                "minecraft:hud/experience_bar_progress",
                69,
                211,
                57,
                5,
                182,
                0xFFFFFFFF);
        source.experienceLevel("Level 3", 140, 205, 40);
        source.finish(100);
        var frame = source.sample(world, camera, null, 320, 240, 100);
        assertEquals(3, frame.size());
        assertEquals("status_icon", frame.get(0).getAsJsonObject().get("role").getAsString());
        var progress = frame.get(1).getAsJsonObject();
        assertEquals(57, progress.get("value").getAsInt());
        assertEquals(182, progress.get("valueMaximum").getAsInt());
        assertEquals(
                "drawn_sprite_extent_not_raw_player_statistics",
                progress.get("valueSemantics").getAsString());
        assertEquals("Level 3", frame.get(2).getAsJsonObject().get("label").getAsString());
        source.begin(world, camera, null, 320, 240);
        source.contextualSprite(
                "jump", "minecraft:hud/jump_bar_background", 69, 211, 182, 5, 0, 0xFFFFFFFF);
        source.contextualSprite(
                "jump", "minecraft:hud/jump_bar_progress", 69, 211, 91, 5, 182, 0xFFFFFFFF);
        source.finish(101);
        frame = source.sample(world, camera, null, 320, 240, 101);
        assertEquals(2, frame.size());
        for (var node : frame) {
            assertTrue(
                    node.getAsJsonObject()
                            .get("controlKey")
                            .getAsString()
                            .startsWith("hud.context.jump."));
            for (String hidden :
                    List.of(
                            "experiencePoints",
                            "experienceProgress",
                            "jumpCharge",
                            "jumpCooldown",
                            "waypointId",
                            "uuid",
                            "worldPosition"))
                assertFalse(node.getAsJsonObject().has(hidden), hidden);
        }
        source.begin(world, camera, null, 320, 240);
        source.contextualSprite(
                "locator", "minecraft:hud/locator_bar_background", 69, 211, 182, 5, 0, 0xFFFFFFFF);
        source.contextualSprite(
                "locator", "minecraft:hud/locator_bar_dot", 100, 212, 3, 3, 0, 0x80FFAA00);
        source.contextualSprite(
                "locator", "minecraft:hud/locator_bar_dot", 110, 212, 3, 3, 0, 0x00FFAA00);
        source.finish(102);
        frame = source.sample(world, camera, null, 320, 240, 102);
        assertEquals(2, frame.size());
        assertEquals(128 / 255.0, frame.get(1).getAsJsonObject().get("opacity").getAsDouble());
    }

    @Test
    void nativeDelegatesDrawExactlyOnceWithOriginalArgumentsBeforeObserving() throws Exception {
        int delegates = 0;
        for (String name :
                List.of(
                        "HudPresentationMixin",
                        "ItemDecorationPresentationMixin",
                        "ContextualBarPresentationMixin",
                        "LocatorBarPresentationMixin")) {
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
                for (MethodNode method : compiled.methods) {
                    if (java.util.stream.Stream.of(
                                    method.visibleAnnotations, method.invisibleAnnotations)
                            .filter(java.util.Objects::nonNull)
                            .flatMap(List::stream)
                            .noneMatch(
                                    a ->
                                            a.desc.equals(
                                                    "Lorg/spongepowered/asm/mixin/injection/Redirect;")))
                        continue;
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
                    boolean nativeStatic = draw.getOpcode() == Opcodes.INVOKESTATIC;
                    assertEquals(
                            nativeStatic
                                    ? "net/minecraft/client/gui/contextualbar/ContextualBar"
                                    : "net/minecraft/client/gui/GuiGraphicsExtractor",
                            draw.owner);
                    assertTrue(
                            List.of(
                                            "fill",
                                            "textWithBackdrop",
                                            "blitSprite",
                                            "extractExperienceLevel")
                                    .contains(draw.name));
                    Type[] forwarded = Type.getArgumentTypes(draw.desc);
                    Type[] original = Type.getArgumentTypes(method.desc);
                    int receiver = nativeStatic ? 0 : 1;
                    assertEquals(
                            List.of(original).subList(receiver, forwarded.length + receiver),
                            List.of(forwarded));
                    int local = 1;
                    for (int i = 0; i < forwarded.length + receiver; i++) {
                        VarInsnNode load = assertInstanceOf(VarInsnNode.class, instructions.get(i));
                        assertEquals(original[i].getOpcode(Opcodes.ILOAD), load.getOpcode());
                        assertEquals(local, load.var);
                        local += original[i].getSize();
                    }
                    assertSame(draw, instructions.get(forwarded.length + receiver));
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
        }
        assertEquals(9, delegates);
    }
}
