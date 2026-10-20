package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.GuiGraphicsScissorMixin;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        frame.name(text, 20, 20, 60, 9, 1, new Matrix4f(), null);
    }

    @Test
    void onlyCompletedFramesWithTheExactNativeOwnersAndViewportAreReadableFor250Millis() {
        var frame = begin();
        name(frame, "shown name");
        icon(frame, "minecraft:icon/ping_5", 100, 20, 10, 8, new Matrix4f(), null);
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
    void eightyActualRowsAggregatePartsWithoutIncreasingTheSeparate128LateTextBudget() {
        var frame = begin();
        frame.header("actually drawn header", 10, 1, 120, 9, 1, new Matrix4f(), null);
        for (int index = 0; index < 80; index++) {
            frame.name(
                    "display " + index,
                    10 + index / 20 * 150,
                    20 + index % 20 * 10,
                    60,
                    9,
                    1,
                    new Matrix4f(),
                    null);
            for (int part = 0; part < 64; part++)
                icon(
                        frame,
                        "minecraft:hud/heart/full",
                        90 + index / 20 * 150,
                        20 + index % 20 * 10,
                        9,
                        9,
                        new Matrix4f(),
                        null);
        }
        frame.footer("actually drawn footer", 10, 230, 120, 9, 1, new Matrix4f(), null);
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
        var late = new UiPresentationSnapshot.RenderedHud(128);
        late.begin(world, camera, null, 640, 480);
        for (int index = 0; index < 129; index++)
            late.nativeText("late", "draw", 1, 1, 10, 9, 1, new Matrix4f(), null);
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
            icon(parts, "minecraft:hud/heart/full", 100, 20, 9, 9, new Matrix4f(), null);
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
        var pose = new Matrix4f().translate(10.25F, 4.5F, 0);
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
        icon(frame, "minecraft:icon/ping_2", 190, 20, 10, 8, pose, null);
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
        frame.score("", 100, 20, 0, 9, 1, new Matrix4f(), null);
        frame.score(hidden, 100, 20, 60, 9, 1, new Matrix4f(), null);
        icon(frame, "minecraft:icon/ping_unknown", 170, 20, 10, 8, new Matrix4f(), null);
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
            icon(frame, resource, 100, 20, 9, 9, new Matrix4f(), null);
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
                new Matrix4f(),
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

    private void icon(
            UiPresentationSnapshot.RenderedPlayerList frame,
            String resource,
            int x,
            int y,
            int width,
            int height,
            Matrix4fc pose,
            ScreenRectangle scissor) {
        frame.sprite(resource, x, y, width, height, 1, pose, scissor);
    }

    @Test
    void legacyGeometryRejectsProjectiveOrTransparentDrawsRatherThanInventingVisibleText() {
        var frame = begin();
        frame.name("SECRET perspective", 1, 1, 10, 9, 1, new Matrix4f().m03(1), null);
        frame.score("SECRET transparent", 1, 1, 10, 9, 0, new Matrix4f(), null);
        frame.finish(100);
        var node = sample(frame, 100).get(0).getAsJsonObject();
        assertEquals("", node.get("label").getAsString());
        assertFalse(node.get("boundsAvailable").getAsBoolean());
        assertEquals(0, node.getAsJsonArray("children").size());
    }

    @Test
    void nativeFontReallyPromotesLegacyRgbBeforeDrawingAndTheObserverUsesThatOpacity()
            throws Exception {
        var adjust = Font.class.getDeclaredMethod("adjustColor", int.class);
        adjust.setAccessible(true);
        for (int color : new int[] {0, 0xFFFFFF, 0x03FFFFFF, 0x04FFFFFF, 0x80FFFFFF, 0xFFFFFFFF}) {
            int nativeColor = (Integer) adjust.invoke(null, color);
            assertEquals((nativeColor >>> 24) / 255.0F, UiNativeText.opacity(color));
        }
        assertEquals(1, UiNativeText.opacity(16777215));
        var font = compiled("net/minecraft/client/gui/Font");
        assertTrue(
                font.methods.stream()
                        .filter(method -> method.name.equals("drawInternal"))
                        .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                        .anyMatch(
                                value ->
                                        value instanceof MethodInsnNode invoke
                                                && invoke.name.equals("adjustColor")));
    }

    @Test
    void scissorSamplesAreUnknownBeforeTheNativeBoundaryAndBoundToTheExactGraphicsOwner()
            throws Exception {
        var carrier = new GuiGraphicsScissorMixin() {};
        assertNull(carrier.minecraftProtocolProbe$scissor());
        assertNull(UiNativeText.validScissor(carrier, carrier.minecraftProtocolProbe$scissor()));
        var initial =
                GuiGraphicsScissorMixin.class.getDeclaredMethod(
                        "minecraftProtocolProbe$initialScissor", CallbackInfo.class);
        initial.setAccessible(true);
        initial.invoke(carrier, new Object[] {null});
        var first = UiNativeText.validScissor(carrier, carrier.minecraftProtocolProbe$scissor());
        assertNotNull(first);
        assertNull(first.rectangle());
        assertSame(carrier, first.owner());
        assertEquals(UiNativeText.ScissorOrigin.NATIVE_INITIAL_EMPTY_STACK, first.origin());
        assertNull(UiNativeText.validScissor(new Object(), first));
        assertNull(
                UiNativeText.validScissor(
                        carrier, new UiNativeText.ScissorSample(carrier, null, null)));
        var applied =
                GuiGraphicsScissorMixin.class.getDeclaredMethod(
                        "minecraftProtocolProbe$appliedScissor",
                        ScreenRectangle.class,
                        CallbackInfo.class);
        applied.setAccessible(true);
        var actual = new ScreenRectangle(10, 20, 30, 40);
        applied.invoke(carrier, actual, null);
        var sampled = UiNativeText.validScissor(carrier, carrier.minecraftProtocolProbe$scissor());
        assertNotNull(sampled);
        assertSame(actual, sampled.rectangle());
        assertEquals(UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN, sampled.origin());
        applied.invoke(carrier, new Object[] {null, null});
        assertNull(
                UiNativeText.validScissor(carrier, carrier.minecraftProtocolProbe$scissor())
                        .rectangle());
    }

    @Test
    void nativeConstructionAndApplyScissorFactsSupportOnlyTheObservedSameGraphicsState()
            throws Exception {
        String graphicsOwner = "net/minecraft/client/gui/GuiGraphics";
        String stackOwner = graphicsOwner + "$ScissorStack";
        String constructor =
                "(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";
        var graphics = compiled(graphicsOwner);
        var initializer =
                graphics.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("<init>")
                                                && method.desc.equals(constructor))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1,
                java.util.Arrays.stream(initializer.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof TypeInsnNode create
                                                && create.getOpcode() == Opcodes.NEW
                                                && create.desc.equals(stackOwner))
                        .count());
        assertEquals(
                1,
                java.util.Arrays.stream(initializer.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.owner.equals(stackOwner)
                                                && call.name.equals("<init>")
                                                && call.desc.equals("()V"))
                        .count());
        assertEquals(
                1,
                java.util.Arrays.stream(initializer.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof FieldInsnNode field
                                                && field.getOpcode() == Opcodes.PUTFIELD
                                                && field.owner.equals(graphicsOwner)
                                                && field.name.equals("scissorStack")
                                                && field.desc.equals("L" + stackOwner + ";"))
                        .count());
        assertFalse(
                java.util.Arrays.stream(initializer.instructions.toArray())
                        .anyMatch(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && List.of("push", "applyScissor", "enableScissor")
                                                        .contains(call.name)));
        var stack = compiled(stackOwner);
        var stackInitializer =
                stack.methods.stream()
                        .filter(method -> method.name.equals("<init>"))
                        .findFirst()
                        .orElseThrow();
        var constructorCalls =
                java.util.Arrays.stream(stackInitializer.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .toList();
        assertEquals(
                List.of("java/lang/Object.<init>()V", "java/util/ArrayDeque.<init>()V"),
                constructorCalls.stream()
                        .map(call -> call.owner + "." + call.name + call.desc)
                        .toList());
        assertEquals(
                1,
                java.util.Arrays.stream(stackInitializer.instructions.toArray())
                        .filter(
                                value ->
                                        value instanceof FieldInsnNode field
                                                && field.getOpcode() == Opcodes.PUTFIELD
                                                && field.name.equals("stack")
                                                && field.desc.equals("Ljava/util/Deque;"))
                        .count());
        for (String name : List.of("enableScissor", "disableScissor")) {
            var method =
                    graphics.methods.stream()
                            .filter(value -> value.name.equals(name))
                            .findFirst()
                            .orElseThrow();
            var nativeCalls =
                    java.util.Arrays.stream(method.instructions.toArray())
                            .filter(MethodInsnNode.class::isInstance)
                            .map(MethodInsnNode.class::cast)
                            .toList();
            assertTrue(
                    nativeCalls.stream()
                            .anyMatch(
                                    call ->
                                            call.owner.equals(stackOwner)
                                                    && call.name.equals(
                                                            name.equals("enableScissor")
                                                                    ? "push"
                                                                    : "pop")));
            assertEquals("applyScissor", nativeCalls.get(nativeCalls.size() - 1).name);
        }
        var mixin =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/mixin/GuiGraphicsScissorMixin");
        int observed = 0;
        for (var method : mixin.methods) {
            var inject =
                    annotations(method).stream()
                            .filter(value -> value.desc.endsWith("/Inject;"))
                            .findFirst();
            if (inject.isEmpty()) continue;
            observed++;
            var annotation = inject.get();
            assertNotEquals(Boolean.TRUE, annotationValue(annotation, "cancellable"));
            assertEquals(
                    List.of(
                            method.name.endsWith("$initialScissor")
                                    ? "<init>" + constructor
                                    : "applyScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"),
                    annotationValue(annotation, "method"));
            Object point = annotationValue(annotation, "at");
            if (point instanceof List<?> points) {
                assertEquals(1, points.size());
                point = points.get(0);
            }
            assertEquals(
                    "RETURN",
                    annotationValue(assertInstanceOf(AnnotationNode.class, point), "value"));
            assertFalse(
                    annotations(method).stream()
                            .anyMatch(
                                    value ->
                                            value.desc.endsWith("/Redirect;")
                                                    || value.desc.endsWith("/Overwrite;")));
        }
        assertEquals(2, observed);
        var decoder = compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/UiNativeText");
        assertTrue(
                decoder.methods.stream()
                        .filter(method -> method.name.equals("scissor"))
                        .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                        .anyMatch(
                                value ->
                                        value instanceof MethodInsnNode call
                                                && call.name.equals("validScissor")));
    }

    @Test
    void productionHooksObserveNativeOverloadsAndForwardOriginalArgumentsExactlyOnceBeforeReading()
            throws Exception {
        var mixin =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/mixin/PlayerTabPresentationMixin");
        Map<String, Integer> sites =
                Map.of(
                        "render:sequence",
                        2,
                        "render:component",
                        1,
                        "renderTablistScore:component",
                        1,
                        "renderTablistHearts:component",
                        1,
                        "renderPingIcon:icon",
                        1,
                        "renderTablistHearts:icon",
                        6);
        var nativeType = compiled("net/minecraft/client/gui/components/PlayerTabOverlay");
        for (var expected : sites.entrySet()) {
            String[] key = expected.getKey().split(":");
            var method =
                    nativeType.methods.stream()
                            .filter(value -> value.name.equals(key[0]))
                            .findFirst()
                            .orElseThrow();
            long actual =
                    java.util.Arrays.stream(method.instructions.toArray())
                            .filter(
                                    value ->
                                            value instanceof MethodInsnNode invoke
                                                    && invoke.owner.equals(
                                                            "net/minecraft/client/gui/GuiGraphics")
                                                    && (key[1].equals("icon")
                                                            ? invoke.name.equals("blitSprite")
                                                            : invoke.name.equals("drawString")
                                                                    && invoke.desc.contains(
                                                                            key[1].equals(
                                                                                            "component")
                                                                                    ? "network/chat/Component;"
                                                                                    : key[1].equals(
                                                                                                    "sequence")
                                                                                            ? "util/FormattedCharSequence;"
                                                                                            : "java/lang/String;")))
                            .count();
            assertEquals(expected.getValue().longValue(), actual, expected.getKey());
        }
        int observers = 0;
        for (var method : mixin.methods) {
            if (annotations(method).stream()
                    .noneMatch(annotation -> annotation.desc.endsWith("/Redirect;"))) continue;
            observers++;
            var instructions =
                    java.util.Arrays.stream(method.instructions.toArray())
                            .filter(value -> value.getOpcode() >= 0)
                            .toList();
            Type[] arguments = Type.getArgumentTypes(method.desc);
            int local = 1;
            for (int index = 0; index < arguments.length; index++) {
                var load = assertInstanceOf(VarInsnNode.class, instructions.get(index));
                assertEquals(arguments[index].getOpcode(Opcodes.ILOAD), load.getOpcode());
                assertEquals(local, load.var);
                local += arguments[index].getSize();
            }
            var draw = assertInstanceOf(MethodInsnNode.class, instructions.get(arguments.length));
            assertEquals("net/minecraft/client/gui/GuiGraphics", draw.owner);
            assertTrue(List.of("drawString", "blitSprite").contains(draw.name));
            assertEquals(
                    List.of(arguments).subList(1, arguments.length),
                    List.of(Type.getArgumentTypes(draw.desc)));
            assertEquals(
                    1,
                    java.util.Arrays.stream(method.instructions.toArray())
                            .filter(
                                    value ->
                                            value instanceof MethodInsnNode call
                                                    && call.owner.equals(draw.owner)
                                                    && call.name.equals(draw.name))
                            .count());
            if (Type.getReturnType(draw.desc).equals(Type.INT_TYPE)) {
                var stored =
                        assertInstanceOf(VarInsnNode.class, instructions.get(arguments.length + 1));
                assertEquals(Opcodes.ISTORE, stored.getOpcode());
                var loaded =
                        assertInstanceOf(
                                VarInsnNode.class, instructions.get(instructions.size() - 2));
                assertEquals(Opcodes.ILOAD, loaded.getOpcode());
                assertEquals(stored.var, loaded.var);
                assertEquals(
                        Opcodes.IRETURN, instructions.get(instructions.size() - 1).getOpcode());
            }
            var calls =
                    java.util.Arrays.stream(method.instructions.toArray())
                            .filter(MethodInsnNode.class::isInstance)
                            .map(MethodInsnNode.class::cast)
                            .toList();
            assertTrue(
                    calls.stream()
                            .anyMatch(
                                    call ->
                                            call.owner.endsWith("/UiNativeText")
                                                    && call.name.equals("scissor")));
            assertTrue(
                    instructions.stream().anyMatch(value -> value.getOpcode() == Opcodes.IFNULL),
                    "Unknown native scissor must suppress collection");
            for (var call : calls)
                assertFalse(
                        List.of(
                                        "getLatency",
                                        "getProfile",
                                        "getScore",
                                        "getHealth",
                                        "setShaderColor",
                                        "getServer")
                                .contains(call.name));
        }
        assertEquals(5, observers);
        var producer =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiPresentationSnapshot");
        var capture =
                producer.methods.stream()
                        .filter(method -> method.name.equals("playerList"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                java.util.Arrays.stream(capture.instructions.toArray())
                        .anyMatch(
                                value ->
                                        value instanceof MethodInsnNode invoke
                                                && invoke.owner.endsWith(
                                                        "UiPresentationSnapshot$RenderedPlayerList")
                                                && invoke.name.equals("sample")));
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        return java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

    private ClassNode compiled(String name) throws Exception {
        try (InputStream bytes = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(bytes);
            ClassNode result = new ClassNode();
            new ClassReader(bytes).accept(result, 0);
            return result;
        }
    }
}
