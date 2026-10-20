package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class PlayerTabPresentationTest {
    private final Object world = new Object(),
            camera = new Object(),
            connection = new Object(),
            owner = new Object();

    private UiPresentationSnapshot.RenderedPlayerList begin() {
        var frame = new UiPresentationSnapshot.RenderedPlayerList();
        frame.begin(world, camera, connection, owner, null, null, 640, 480);
        return frame;
    }

    private JsonArray sample(UiPresentationSnapshot.RenderedPlayerList frame, long now) {
        return frame.sample(world, camera, connection, owner, null, null, 640, 480, now, true);
    }

    private void name(UiPresentationSnapshot.RenderedPlayerList frame, String text) {
        frame.name(text, 20, 20, 60, 9, 1, new Matrix3x2f(), null);
    }

    @Test
    void onlyCompletedFramesWithTheExactNativeOwnersAndViewportAreReadableFor250Millis() {
        var frame = begin();
        name(frame, "shown name");
        frame.sprite("minecraft:icon/ping_5", 100, 20, 10, 8, new Matrix3x2f(), null);
        assertEquals(0, sample(frame, 100).size());
        frame.finish(100);
        assertEquals(1, sample(frame, 100).size());
        assertEquals(1, sample(frame, 250_000_100).size());
        assertEquals(0, sample(frame, 250_000_101).size());
        assertEquals(0, sample(frame, 99).size());
        for (int changed = 0; changed < 6; changed++) {
            Object[] values = {world, camera, connection, owner, null, null};
            values[changed] = new Object();
            assertEquals(
                    0,
                    frame.sample(
                                    values[0], values[1], values[2], values[3], values[4],
                                    values[5], 640, 480, 100, true)
                            .size());
        }
        assertEquals(
                0,
                frame.sample(world, camera, connection, owner, null, null, 641, 480, 100, true)
                        .size());
        assertEquals(
                0,
                frame.sample(world, camera, connection, owner, null, null, 640, 481, 100, true)
                        .size());
        assertEquals(
                0,
                frame.sample(world, camera, connection, owner, null, null, 640, 480, 100, false)
                        .size());
        frame.begin(world, camera, connection, owner, null, null, 640, 480);
        assertEquals(0, sample(frame, 101).size());
        frame.finish(101);
        assertEquals(0, sample(frame, 101).size());
    }

    @Test
    void eightyActualRowsAggregatePartsWithoutIncreasingTheSeparate32HudBudget() {
        var frame = begin();
        frame.header("actually drawn header", 10, 1, 120, 9, 1, new Matrix3x2f(), null);
        for (int index = 0; index < 80; index++) {
            frame.name(
                    "display " + index,
                    10 + index / 20 * 150,
                    20 + index % 20 * 10,
                    60,
                    9,
                    1,
                    new Matrix3x2f(),
                    null);
            for (int part = 0; part < 64; part++)
                frame.sprite(
                        "minecraft:hud/heart/full",
                        90 + index / 20 * 150,
                        20 + index % 20 * 10,
                        9,
                        9,
                        new Matrix3x2f(),
                        null);
        }
        frame.footer("actually drawn footer", 10, 230, 120, 9, 1, new Matrix3x2f(), null);
        frame.finish(100);
        JsonArray captured = sample(frame, 100);
        assertEquals(82, captured.size());
        for (int index = 0; index < 80; index++) {
            var row = captured.get(index + 1).getAsJsonObject();
            assertEquals("hud.playerlist.row." + index, row.get("controlKey").getAsString());
            assertEquals("display " + index, row.get("label").getAsString());
            assertEquals(64, row.getAsJsonArray("children").size());
        }
        var raw = new JsonObject();
        raw.add("children", new JsonArray());
        var presentation = new JsonObject();
        presentation.add("children", captured);
        raw.add("presentation", presentation);
        var user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertEquals(82 + 80 * 64 + 2, user.get("presentationNodeCount").getAsInt());
        assertEquals("returned_projection_recursive", user.get("countSemantics").getAsString());
        var late = new UiPresentationSnapshot.RenderedHud();
        late.begin(world, camera, null, 640, 480);
        for (int index = 0; index < 33; index++)
            late.nativeText("late", "draw", 1, 1, 10, 9, 1, new Matrix3x2f(), null);
        late.finish(100);
        assertEquals(0, late.sample(world, camera, null, 640, 480, 100).size());
    }

    @Test
    void rowOrPartOverflowAndNestedNativeExtractionFailClosedAsAWholeFrame() {
        var rows = begin();
        for (int index = 0; index < 81; index++) name(rows, "row");
        rows.finish(100);
        assertEquals(0, sample(rows, 100).size());
        var parts = begin();
        name(parts, "row");
        for (int index = 0; index < 65; index++)
            parts.sprite("minecraft:hud/heart/full", 100, 20, 9, 9, new Matrix3x2f(), null);
        parts.finish(100);
        assertEquals(0, sample(parts, 100).size());
        var nested = begin();
        name(nested, "outer");
        nested.begin(world, camera, connection, owner, null, null, 640, 480);
        name(nested, "nested");
        nested.finish(100);
        nested.finish(101);
        assertEquals(0, sample(nested, 101).size());
        nested.begin(world, camera, connection, owner, null, null, 640, 480);
        name(nested, "next actual frame");
        nested.finish(102);
        assertEquals(1, sample(nested, 102).size());
    }

    @Test
    void textAndSpriteBoundsAreTheirOwnNativePoseAndScissorNotTheWholeRow() {
        var frame = begin();
        var pose = new Matrix3x2f().translate(10.25F, 4.5F);
        frame.name(
                "SECRET clipped name",
                0,
                20,
                60,
                9,
                .5F,
                pose,
                new ScreenRectangle(20, 20, 30, 30));
        frame.score("VISIBLE_FORMAT", 80, 20, 90, 9, 1, pose, null);
        frame.sprite("minecraft:icon/ping_2", 190, 20, 10, 8, pose, null);
        frame.finish(100);
        var row = sample(frame, 100).get(0).getAsJsonObject();
        assertEquals("", row.get("label").getAsString());
        assertEquals("withheld_partial_or_occluded", row.get("textPhase").getAsString());
        assertEquals(20, row.get("x").getAsDouble());
        assertEquals(30, row.get("width").getAsDouble());
        var score = row.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("VISIBLE_FORMAT", score.get("label").getAsString());
        assertEquals(90.25, score.get("x").getAsDouble());
        var ping = row.getAsJsonArray("children").get(1).getAsJsonObject();
        assertEquals("minecraft:icon/ping_2", ping.get("resourceId").getAsString());
        assertEquals(200.25, ping.get("x").getAsDouble());
        assertEquals(10, ping.get("width").getAsDouble());
        assertFalse(row.toString().contains("SECRET"));
        for (var part : row.getAsJsonArray("children")) {
            assertEquals(
                    "last_completed_native_player_list_draw",
                    part.getAsJsonObject().get("iconSource").getAsString());
            assertFalse(part.getAsJsonObject().get("active").getAsBoolean());
            assertEquals(0, part.getAsJsonObject().getAsJsonArray("actions").size());
            for (String forbidden :
                    List.of("latency", "score", "profile", "uuid", "health", "value"))
                assertFalse(part.getAsJsonObject().has(forbidden));
        }
    }

    @Test
    void nativeFormattedBlankAndObfuscatedTextAreNotRecoveredFromRawValues() {
        var frame = begin();
        String hidden =
                UiPresentationSnapshot.RenderedPlayerList.plain(
                        FormattedCharSequence.forward("SECRET", Style.EMPTY.withObfuscated(true)));
        assertEquals("", hidden);
        name(frame, hidden);
        frame.score("", 100, 20, 0, 9, 1, new Matrix3x2f(), null);
        frame.score(hidden, 100, 20, 60, 9, 1, new Matrix3x2f(), null);
        frame.sprite("minecraft:icon/ping_unknown", 170, 20, 10, 8, new Matrix3x2f(), null);
        frame.finish(100);
        var row = sample(frame, 100).get(0).getAsJsonObject();
        assertEquals("", row.get("label").getAsString());
        assertFalse(row.get("boundsAvailable").getAsBoolean());
        assertEquals(1, row.getAsJsonArray("children").size());
        assertFalse(row.toString().contains("SECRET"));
    }

    @Test
    void actualNativePingAndHeartSubmissionOrderIsRetainedIncludingOverlappingBlinkLayers() {
        var frame = begin();
        name(frame, "display");
        List<String> resources =
                List.of(
                        "minecraft:hud/heart/container",
                        "minecraft:hud/heart/full_blinking",
                        "minecraft:hud/heart/half",
                        "minecraft:icon/ping_5");
        for (String resource : resources)
            frame.sprite(resource, 100, 20, 9, 9, new Matrix3x2f(), null);
        frame.finish(100);
        var children = sample(frame, 100).get(0).getAsJsonObject().getAsJsonArray("children");
        for (int index = 0; index < resources.size(); index++) {
            var node = children.get(index).getAsJsonObject();
            assertEquals(resources.get(index), node.get("resourceId").getAsString());
            assertEquals(
                    "hud.playerlist.row.0.part." + index, node.get("controlKey").getAsString());
        }
    }

    @Test
    void userProjectionCannotRevealTheFullDebugHeaderValueBehindAnActuallyClippedDraw() {
        var frame = begin();
        frame.header(
                "SECRET clipped header",
                5,
                5,
                120,
                9,
                1,
                new Matrix3x2f(),
                new ScreenRectangle(5, 5, 30, 9));
        frame.finish(100);
        var captured = sample(frame, 100);
        var nativeHeader = captured.get(0).getAsJsonObject();
        assertEquals("", nativeHeader.get("label").getAsString());
        // UiPresentationDebug.describe adds the complete native value for authorized setters.
        nativeHeader.addProperty("value", "SECRET complete debug header");
        var raw = new JsonObject();
        raw.add("children", new JsonArray());
        var presentation = new JsonObject();
        presentation.add("children", captured);
        raw.add("presentation", presentation);
        var user = UiDataView.tree(raw, UiDataView.Category.USER);
        assertFalse(user.toString().contains("SECRET"));
        assertFalse(
                user.getAsJsonObject("presentation")
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .has("value"));
        var debug = UiDataView.tree(raw, UiDataView.Category.DEBUG);
        assertEquals(
                "SECRET complete debug header",
                debug.getAsJsonObject("presentation")
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("value")
                        .getAsString());
    }

    @Test
    void productionHooksObserveTheRealDrawsAfterForwardingOriginalArgumentsExactlyOnce()
            throws Exception {
        var mixin = compiled("mixin/PlayerTabPresentationMixin");
        Map<String, Integer> sites =
                Map.of(
                        "extractRenderState:textComponent",
                        1,
                        "extractRenderState:textSequence",
                        2,
                        "extractTablistScore:textComponent",
                        1,
                        "extractTablistHearts:textComponent",
                        1,
                        "extractTablistHearts:blitSprite",
                        6,
                        "extractPingIcon:blitSprite",
                        1);
        ClassNode nativeType = new ClassNode();
        try (InputStream bytes =
                getClass()
                        .getResourceAsStream(
                                "/net/minecraft/client/gui/components/PlayerTabOverlay.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(nativeType, 0);
        }
        for (var expected : sites.entrySet()) {
            String[] key = expected.getKey().split(":");
            var method =
                    nativeType.methods.stream()
                            .filter(value -> value.name.equals(key[0]))
                            .findFirst()
                            .orElseThrow();
            long actual = 0;
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode invoke
                        && invoke.owner.equals("net/minecraft/client/gui/GuiGraphicsExtractor")
                        && (key[1].equals("blitSprite")
                                ? invoke.name.equals("blitSprite")
                                : invoke.name.equals("text")
                                        && invoke.desc.contains(
                                                key[1].equals("textComponent")
                                                        ? "network/chat/Component;"
                                                        : "util/FormattedCharSequence;"))) actual++;
            assertEquals(expected.getValue().longValue(), actual, expected.getKey());
        }
        int observers = 0;
        for (var method : mixin.methods) {
            if (java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                    .filter(java.util.Objects::nonNull)
                    .flatMap(List::stream)
                    .noneMatch(annotation -> annotation.desc.endsWith("/Redirect;"))) continue;
            int nativeDraws = 0, records = 0;
            boolean forwarded = false;
            var instructions =
                    java.util.Arrays.stream(method.instructions.toArray())
                            .filter(instruction -> instruction.getOpcode() >= 0)
                            .toList();
            Type[] arguments = Type.getArgumentTypes(method.desc);
            int local = 1;
            for (int index = 0; index < arguments.length; index++) {
                var load = assertInstanceOf(VarInsnNode.class, instructions.get(index));
                assertEquals(arguments[index].getOpcode(Opcodes.ILOAD), load.getOpcode());
                assertEquals(local, load.var);
                local += arguments[index].getSize();
            }
            assertInstanceOf(MethodInsnNode.class, instructions.get(arguments.length));
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode invoke)) continue;
                if (invoke.owner.equals("net/minecraft/client/gui/GuiGraphicsExtractor")
                        && (invoke.name.equals("text") || invoke.name.equals("blitSprite"))) {
                    nativeDraws++;
                    forwarded = true;
                }
                if (invoke.owner.endsWith("UiPresentationSnapshot$RenderedPlayerList")
                        && List.of("header", "footer", "name", "score", "sprite")
                                .contains(invoke.name)) {
                    assertTrue(forwarded, method.name);
                    records++;
                }
                assertNotEquals("getLatency", invoke.name);
                assertNotEquals("getProfile", invoke.name);
            }
            assertEquals(1, nativeDraws, method.name);
            assertEquals(1, records, method.name);
            observers++;
        }
        assertEquals(5, observers);
        var producer = compiled("runtime/UiPresentationSnapshot");
        var capture =
                producer.methods.stream()
                        .filter(method -> method.name.equals("playerList"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                java.util.stream.StreamSupport.stream(capture.instructions.spliterator(), false)
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode invoke
                                                && invoke.owner.endsWith(
                                                        "UiPresentationSnapshot$RenderedPlayerList")
                                                && invoke.name.equals("sample")));
    }

    private ClassNode compiled(String name) throws Exception {
        try (InputStream bytes =
                getClass()
                        .getResourceAsStream(
                                "/io/github/recrivenvi/minecraftprotocol/probe/"
                                        + name
                                        + ".class")) {
            assertNotNull(bytes);
            ClassNode result = new ClassNode();
            new ClassReader(bytes).accept(result, 0);
            return result;
        }
    }
}
