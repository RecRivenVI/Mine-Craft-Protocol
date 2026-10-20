package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.RecipeToastUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.ToastInstanceUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.ToastRenderTextMixin;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.components.toasts.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.Inject;

class UiToastLifecycleFrameTest {
    private static final String CONTEXT = "toast:77a9fedf-fb7d-4d42-bf90-67a623781b3a";

    @Test
    void theActualObservedToastExitPreservesBoundedDebugTextWithoutLeakingItIntoUserDraws()
            throws Exception {
        String nativeDebugText = "pending native title\n" + "unrevealed message ".repeat(180);
        for (boolean partial : new boolean[] {false, true}) {
            var frame =
                    new UiToastText.Frame(
                            CONTEXT,
                            new Object(),
                            null,
                            null,
                            null,
                            null,
                            text("actual rendered phase", "static"),
                            partial ? 240.25 : 160,
                            32,
                            160,
                            32);
            JsonObject observed =
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0",
                            "native.SystemToast",
                            frame,
                            true,
                            partial ? .5F : 1,
                            false,
                            320,
                            240);
            JsonArray produced = new JsonArray();
            UiPresentationSnapshot.appendObservedToast(produced, observed, nativeDebugText);
            assertSame(observed, produced.get(0));
            assertEquals(
                    nativeDebugText.substring(0, 2048), observed.get("debugLabel").getAsString());

            JsonObject captured = new JsonObject(), presentation = new JsonObject();
            captured.add("children", new JsonArray());
            presentation.add("children", produced);
            captured.add("presentation", presentation);
            JsonObject debug =
                    UiDataView.tree(captured, UiDataView.Category.DEBUG)
                            .getAsJsonObject("presentation")
                            .getAsJsonArray("children")
                            .get(0)
                            .getAsJsonObject();
            JsonObject user =
                    UiDataView.tree(captured, UiDataView.Category.USER)
                            .getAsJsonObject("presentation")
                            .getAsJsonArray("children")
                            .get(0)
                            .getAsJsonObject();
            assertEquals(observed.get("debugLabel"), debug.get("debugLabel"));
            assertFalse(user.has("debugLabel"));
            assertFalse(user.toString().contains("pending native title"));
            assertEquals(partial ? "" : "actual rendered phase", user.get("label").getAsString());
            assertEquals(
                    partial ? "withheld_partial_or_occluded" : "static",
                    user.get("textPhase").getAsString());
        }
        JsonObject unicode = new JsonObject();
        UiPresentationSnapshot.appendObservedToast(
                new JsonArray(), unicode, "x".repeat(2047) + "\uD83D\uDE00");
        assertEquals("x".repeat(2047), unicode.get("debugLabel").getAsString());

        ClassNode production = compiled(UiPresentationSnapshot.class);
        MethodNode toast =
                production.methods.stream()
                        .filter(method -> method.name.equals("toast"))
                        .findFirst()
                        .orElseThrow();
        List<AbstractInsnNode> instructions =
                Arrays.stream(toast.instructions.toArray())
                        .filter(instruction -> instruction.getOpcode() >= 0)
                        .toList();
        String owner = UiPresentationSnapshot.class.getName().replace('.', '/');
        int appended = 0;
        for (int index = 0; index < instructions.size(); index++) {
            if (!(instructions.get(index) instanceof MethodInsnNode call)) continue;
            assertFalse(
                    call.owner.equals("com/google/gson/JsonArray") && call.name.equals("add"),
                    "The observed producer must not bypass its bounded DEBUG metadata exit");
            if (!call.owner.equals(owner) || !call.name.equals("appendObservedToast")) continue;
            appended++;
            assertEquals(
                    "(Lcom/google/gson/JsonArray;Lcom/google/gson/JsonObject;Ljava/lang/String;)V",
                    call.desc);
            MethodInsnNode nativeText =
                    assertInstanceOf(MethodInsnNode.class, instructions.get(index - 1));
            assertEquals(owner, nativeText.owner);
            assertEquals("toastText", nativeText.name);
            assertEquals(
                    "(Lnet/minecraft/client/gui/components/toasts/Toast;)Ljava/lang/String;",
                    nativeText.desc);
            assertInstanceOf(VarInsnNode.class, instructions.get(index - 2));
            assertEquals(org.objectweb.asm.Opcodes.RETURN, instructions.get(index + 1).getOpcode());
        }
        assertEquals(
                1,
                appended,
                "The real observed branch must use this executed producer exactly once");
    }

    @Test
    void completedFramesRequireTheSameNativeOwnerAndEveryContextIdentitySizeAndFreshClock() {
        Object owner = new Object(),
                world = new Object(),
                camera = new Object(),
                screen = new Object(),
                overlay = new Object();
        var frame =
                new UiToastText.Frame(
                        CONTEXT,
                        owner,
                        world,
                        camera,
                        screen,
                        overlay,
                        text("drawn", "static"),
                        160,
                        32,
                        160,
                        32);
        assertSame(frame, frame.current(owner, world, camera, screen, overlay, 320, 240, 100));
        assertSame(
                frame,
                frame.current(owner, world, camera, screen, overlay, 320, 240, 250_000_100L));
        assertNull(frame.current(new Object(), world, camera, screen, overlay, 320, 240, 100));
        assertNull(frame.current(owner, new Object(), camera, screen, overlay, 320, 240, 100));
        assertNull(frame.current(owner, world, new Object(), screen, overlay, 320, 240, 100));
        assertNull(frame.current(owner, world, camera, new Object(), overlay, 320, 240, 100));
        assertNull(frame.current(owner, world, camera, screen, new Object(), 320, 240, 100));
        assertNull(frame.current(owner, null, camera, screen, overlay, 320, 240, 100));
        assertNull(frame.current(owner, world, camera, screen, null, 320, 240, 100));
        assertNull(frame.current(owner, world, camera, screen, overlay, 321, 240, 100));
        assertNull(frame.current(owner, world, camera, screen, overlay, 320, 241, 100));
        assertNull(frame.current(owner, world, camera, screen, overlay, 320, 240, 99));
        assertNull(frame.current(owner, world, camera, screen, overlay, 320, 240, 250_000_101L));
    }

    @Test
    void allFourNativeKindsExposeOnlyClippedBackgroundAndPhaseDuringEntryAndExit() {
        for (String kind : List.of("System", "Tutorial", "Advancement", "Recipe")) {
            Object owner = new Object();
            for (boolean exiting : new boolean[] {false, true}) {
                var frame =
                        new UiToastText.Frame(
                                CONTEXT,
                                owner,
                                null,
                                null,
                                null,
                                null,
                                text("partly hidden native title", "static"),
                                240.25,
                                32,
                                160,
                                32);
                JsonObject node =
                        UiPresentationSnapshot.renderedToastNode(
                                "toast.4",
                                "native." + kind + "Toast",
                                frame,
                                true,
                                .5F,
                                exiting,
                                320,
                                240);
                assertNotNull(node);
                assertTrue(node.get("visible").getAsBoolean());
                assertEquals(
                        exiting ? "exiting" : "entering", node.get("renderPhase").getAsString());
                assertEquals("", node.get("label").getAsString());
                assertEquals("withheld_partial_or_occluded", node.get("textPhase").getAsString());
                assertEquals(240.25, node.get("x").getAsDouble());
                assertEquals(79.75, node.get("width").getAsDouble());
                assertEquals(CONTEXT, node.get("contextKey").getAsString());
                assertEquals(
                        "native_toast_draw_viewport_intersection",
                        node.get("boundsSemantics").getAsString());
                assertFalse(node.get("active").getAsBoolean());
                assertTrue(node.getAsJsonArray("actions").isEmpty());
                for (String hidden : List.of("debugLabel", "uuid", "profile", "resourceVersion"))
                    assertFalse(node.has(hidden));
            }
        }
    }

    @Test
    void fullNativeTextKeepsItsEstablishedPhaseAndTheOpaqueOwnerSurvivesQueueReindexing() {
        Object owner = new Object();
        for (String phase :
                List.of("static", "header", "title", "header_and_title", "transparent", "empty")) {
            String label =
                    List.of("transparent", "empty").contains(phase)
                            ? ""
                            : "actual native phase text";
            var frame =
                    new UiToastText.Frame(
                            CONTEXT,
                            owner,
                            null,
                            null,
                            null,
                            null,
                            text(label, phase),
                            160,
                            32,
                            160,
                            32);
            JsonObject first =
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.4", "native.AdvancementToast", frame, true, 1, false, 320, 240);
            JsonObject moved =
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0", "native.AdvancementToast", frame, true, 1, false, 320, 240);
            assertEquals(label, first.get("label").getAsString());
            assertEquals(phase, first.get("textPhase").getAsString());
            assertEquals("fully_shown", first.get("renderPhase").getAsString());
            assertEquals(first.get("contextKey"), moved.get("contextKey"));
            assertNotEquals(first.get("nodeId"), moved.get("nodeId"));
        }
    }

    @Test
    void aHideRequestKeepsFullyVisibleNativeTextUntilItsSlideActuallyBecomesPartial() {
        Object owner = new Object();
        for (String phase : List.of("static", "header", "title", "header_and_title")) {
            var fullFrame =
                    new UiToastText.Frame(
                            CONTEXT,
                            owner,
                            null,
                            null,
                            null,
                            null,
                            text("still fully drawn", phase),
                            160,
                            32,
                            160,
                            32);
            JsonObject firstExit =
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0",
                            "native.AdvancementToast",
                            fullFrame,
                            true,
                            1,
                            true,
                            320,
                            240);
            assertNotNull(firstExit);
            assertEquals("exiting", firstExit.get("renderPhase").getAsString());
            assertEquals("still fully drawn", firstExit.get("label").getAsString());
            assertEquals(phase, firstExit.get("textPhase").getAsString());
            assertEquals(CONTEXT, firstExit.get("contextKey").getAsString());

            JsonObject sliding =
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0",
                            "native.AdvancementToast",
                            fullFrame,
                            true,
                            .5F,
                            true,
                            320,
                            240);
            assertEquals("exiting", sliding.get("renderPhase").getAsString());
            assertEquals("", sliding.get("label").getAsString());
            assertEquals("withheld_partial_or_occluded", sliding.get("textPhase").getAsString());

            var clippedFrame =
                    new UiToastText.Frame(
                            CONTEXT,
                            owner,
                            null,
                            null,
                            null,
                            null,
                            text("still fully drawn", phase),
                            240.25,
                            32,
                            160,
                            32);
            JsonObject clipped =
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0",
                            "native.AdvancementToast",
                            clippedFrame,
                            true,
                            1,
                            true,
                            320,
                            240);
            assertEquals(79.75, clipped.get("width").getAsDouble());
            assertEquals("", clipped.get("label").getAsString());
            assertEquals("withheld_partial_or_occluded", clipped.get("textPhase").getAsString());
            assertEquals("exiting", clipped.get("renderPhase").getAsString());

            var offscreenFrame =
                    new UiToastText.Frame(
                            CONTEXT,
                            owner,
                            null,
                            null,
                            null,
                            null,
                            text("still fully drawn", phase),
                            320,
                            32,
                            160,
                            32);
            assertNull(
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0",
                            "native.AdvancementToast",
                            offscreenFrame,
                            true,
                            .5F,
                            true,
                            320,
                            240));
        }
    }

    @Test
    void occludedOffscreenStaleAndInvalidGeometryCannotPublishLabelsOrOwnerIdentityToUser() {
        Object owner = new Object();
        var frame =
                new UiToastText.Frame(
                        CONTEXT,
                        owner,
                        null,
                        null,
                        null,
                        null,
                        text("hidden", "static"),
                        160,
                        32,
                        160,
                        32);
        JsonObject hidden =
                UiPresentationSnapshot.renderedToastNode(
                        "toast.0", "native.SystemToast", frame, false, 1, false, 320, 240);
        assertEquals("", hidden.get("label").getAsString());
        assertFalse(hidden.has("contextKey"));
        JsonObject root = new JsonObject();
        JsonArray children = new JsonArray();
        children.add(hidden);
        root.add("children", children);
        assertTrue(
                UiDataView.tree(root, UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .isEmpty());
        var outside =
                new UiToastText.Frame(
                        CONTEXT,
                        owner,
                        null,
                        null,
                        null,
                        null,
                        text("hidden", "static"),
                        320,
                        32,
                        160,
                        32);
        assertNull(
                UiPresentationSnapshot.renderedToastNode(
                        "toast.0", "native.SystemToast", outside, true, 0, false, 320, 240));
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1, 2})
            assertNull(
                    UiPresentationSnapshot.renderedToastNode(
                            "toast.0",
                            "native.SystemToast",
                            frame,
                            true,
                            invalid,
                            false,
                            320,
                            240));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
            var invalidFrame =
                    new UiToastText.Frame(
                            CONTEXT,
                            owner,
                            null,
                            null,
                            null,
                            null,
                            text("hidden", "static"),
                            invalid,
                            32,
                            160,
                            32);
            assertNull(invalidFrame.current(owner, null, null, null, null, 320, 240, 100));
        }
        var absent =
                new UiToastText.Frame(
                        CONTEXT, owner, null, null, null, null, null, 160, 32, 160, 32);
        assertNull(absent.current(owner, null, null, null, null, 320, 240, 100));
    }

    @Test
    void theExistingFourNativeReturnHooksCaptureActualTransformAndContextWithoutAnotherInjection()
            throws Exception {
        var hooks =
                Arrays.stream(ToastRenderTextMixin.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(Inject.class))
                        .toList();
        assertEquals(1, hooks.size());
        Inject hook = hooks.get(0).getAnnotation(Inject.class);
        assertArrayEquals(new String[] {"render"}, hook.method());
        assertEquals(1, hook.at().length);
        assertEquals("RETURN", hook.at()[0].value());
        ClassNode mixin = compiled(ToastRenderTextMixin.class);
        assertTrue(
                mixin.interfaces.contains(
                        UiToastText.FrameSource.class.getName().replace('.', '/')));
        assertTrue(
                mixin.methods.stream()
                        .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        UiToastText.class
                                                                .getName()
                                                                .replace('.', '/'))
                                                && call.name.equals("captureFrame")));
        ClassNode capture = compiled(UiToastText.class);
        MethodNode frame =
                capture.methods.stream()
                        .filter(method -> method.name.equals("captureFrame"))
                        .findFirst()
                        .orElseThrow();
        for (String nativeMethod :
                List.of(
                        "getCameraEntity",
                        "getOverlay",
                        "last",
                        "pose",
                        "m30",
                        "m31",
                        "width",
                        "height"))
            assertTrue(
                    Arrays.stream(frame.instructions.toArray())
                            .anyMatch(
                                    instruction ->
                                            instruction instanceof MethodInsnNode call
                                                    && call.name.equals(nativeMethod)),
                    nativeMethod);
        assertEquals(
                "visibility",
                ToastInstanceUiAccessor.class
                        .getMethod("minecraftProtocolProbe$visibility")
                        .getAnnotation(Accessor.class)
                        .value());
        var first = new ToastRenderTextMixin() {};
        var second = new ToastRenderTextMixin() {};
        assertNull(first.minecraftProtocolProbe$toastFrame());
        assertNull(first.minecraftProtocolProbe$renderedText());
        Field key =
                ToastRenderTextMixin.class.getDeclaredField("minecraftProtocolProbe$contextKey");
        key.setAccessible(true);
        assertEquals(key.get(first), key.get(first));
        assertNotEquals(key.get(first), key.get(second));
        assertTrue(((String) key.get(first)).matches("toast:[0-9a-f-]{36}"));
    }

    @Test
    void nativeLegacyHideAfterDrawIsNotConfusedWithTheRecipeEmptyEarlyReturn() throws Exception {
        for (Class<?> type :
                List.of(SystemToast.class, TutorialToast.class, AdvancementToast.class)) {
            MethodNode draw =
                    compiled(type).methods.stream()
                            .filter(method -> method.name.equals("render"))
                            .findFirst()
                            .orElseThrow();
            var instructions = Arrays.stream(draw.instructions.toArray()).toList();
            int background = firstBackground(instructions), firstReturn = firstReturn(instructions);
            assertTrue(background >= 0 && firstReturn > background, type.getSimpleName());
        }
        MethodNode recipe =
                compiled(RecipeToast.class).methods.stream()
                        .filter(method -> method.name.equals("render"))
                        .findFirst()
                        .orElseThrow();
        var instructions = Arrays.stream(recipe.instructions.toArray()).toList();
        int empty = -1;
        for (int index = 0; index < instructions.size(); index++)
            if (instructions.get(index) instanceof MethodInsnNode call
                    && call.owner.equals("java/util/List")
                    && call.name.equals("isEmpty")) {
                empty = index;
                break;
            }
        assertTrue(
                empty >= 0
                        && empty < firstReturn(instructions)
                        && firstReturn(instructions) < firstBackground(instructions));
        assertTrue(
                instructions.subList(empty, firstReturn(instructions)).stream()
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.owner.equals(
                                                        "net/minecraft/client/gui/components/toasts/Toast$Visibility")
                                                && field.name.equals("HIDE")));
        assertEquals(
                "recipes",
                RecipeToastUiAccessor.class
                        .getMethod("minecraftProtocolProbe$recipes")
                        .getAnnotation(Accessor.class)
                        .value());
        MethodNode capture =
                compiled(UiToastText.class).methods.stream()
                        .filter(method -> method.name.equals("capture"))
                        .findFirst()
                        .orElseThrow();
        var captureCalls =
                Arrays.stream(capture.instructions.toArray())
                        .filter(instruction -> instruction instanceof MethodInsnNode)
                        .map(instruction -> (MethodInsnNode) instruction)
                        .toList();
        int recipes = -1, readText = -1;
        for (int index = 0; index < captureCalls.size(); index++) {
            if (captureCalls.get(index).name.equals("minecraftProtocolProbe$recipes"))
                recipes = index;
            if (captureCalls.get(index).name.equals("toastText")) readText = index;
        }
        assertTrue(recipes >= 0 && readText > recipes);
    }

    @Test
    void productionCaptureRejectsEmptyNativeRecipeDataWithoutRunningItsRenderer() throws Exception {
        var empty = new EmptyNativeRecipe();
        assertTrue(empty.minecraftProtocolProbe$recipes().isEmpty());
        assertNull(UiToastText.capture(empty, null, 0));
        assertNull(UiToastText.captureFrame(empty, null, null, CONTEXT));
    }

    private static final class EmptyNativeRecipe extends RecipeToast
            implements RecipeToastUiAccessor {
        private final List<?> actualRecipes;

        EmptyNativeRecipe() throws Exception {
            super(null);
            Field field = RecipeToast.class.getDeclaredField("recipes");
            field.setAccessible(true);
            actualRecipes = (List<?>) field.get(this);
            actualRecipes.clear();
        }

        @Override
        public List<?> minecraftProtocolProbe$recipes() {
            return actualRecipes;
        }
    }

    private static int firstBackground(List<AbstractInsnNode> instructions) {
        for (int index = 0; index < instructions.size(); index++)
            if (instructions.get(index) instanceof MethodInsnNode call
                    && List.of("blit", "blitSprite", "renderBackgroundRow").contains(call.name))
                return index;
        return -1;
    }

    private static int firstReturn(List<AbstractInsnNode> instructions) {
        for (int index = 0; index < instructions.size(); index++)
            if (instructions.get(index).getOpcode() == org.objectweb.asm.Opcodes.ARETURN)
                return index;
        return -1;
    }

    private static UiRenderedText text(String label, String phase) {
        return new UiRenderedText(label, phase, 100, 320, 240);
    }

    private static ClassNode compiled(Class<?> type) throws Exception {
        try (InputStream input =
                type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
