package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.HudTitleLifecycleMixin;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class UiTitleLifecycleDebugTest {
    @Test
    void newTimingRequiresTheWholeBoundedIntegerTriple() {
        assertEquals(
                UiTitleLifecycleDebug.times(0, 12000, 3),
                UiTitleLifecycleDebug.validateTimes(UiTitleLifecycleDebug.times(0, 12000, 3)));
        for (String invalid :
                List.of(
                        "null",
                        "true",
                        "[]",
                        "\"times\"",
                        "{}",
                        "{\"fadeInTicks\":0,\"stayTicks\":1}",
                        "{\"fadeInTicks\":0,\"stayTicks\":1,\"fadeOutTicks\":2,\"extra\":0}",
                        "{\"fadeInTicks\":-1,\"stayTicks\":1,\"fadeOutTicks\":2}",
                        "{\"fadeInTicks\":12001,\"stayTicks\":1,\"fadeOutTicks\":2}",
                        "{\"fadeInTicks\":0.5,\"stayTicks\":1,\"fadeOutTicks\":2}",
                        "{\"fadeInTicks\":null,\"stayTicks\":1,\"fadeOutTicks\":2}",
                        "{\"fadeInTicks\":\"0\",\"stayTicks\":1,\"fadeOutTicks\":2}",
                        "{\"fadeInTicks\":1e999,\"stayTicks\":1,\"fadeOutTicks\":2}"))
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiTitleLifecycleDebug.validateTimes(
                                                    JsonParser.parseString(invalid)))
                            .code(),
                    invalid);
    }

    @Test
    void clearIsOneWholeGroupActionAndNeverAnEmptyTextAlias() {
        for (String invalid : List.of("false", "null", "0", "\"true\"", "{}", "[]"))
            assertEquals(
                    "INVALID_UI_DEBUG_VALUE",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiTitleLifecycleDebug.prepare(
                                                    null,
                                                    UiTitleLifecycleDebug.CLEAR,
                                                    JsonParser.parseString(invalid)))
                            .code());
        assertEquals(
                "UI_DEBUG_UNSUPPORTED",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () ->
                                        UiTitleLifecycleDebug.prepare(
                                                null, "ui.text.set", new JsonPrimitive("")))
                        .code());
        assertTrue(UiTitleLifecycleDebug.handles("hud.titles"));
        assertFalse(UiTitleLifecycleDebug.handles("hud.title"));
    }

    @Test
    void productionDescriptionSeparatesVolatileCountdownFromStableLifecycleReceipt() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var node = node(tree);
            var state = state();
            UiTitleLifecycleDebug.describeState(tree, node, revisions, state);
            var initial = node.get("resourceVersion").deepCopy();
            state.addProperty("remainingTicks", 78);
            node.getAsJsonObject("debugMutation").addProperty("notNative", true);
            UiTitleLifecycleDebug.describeState(tree, node, revisions, state);
            assertEquals(initial, node.get("resourceVersion"));
            assertEquals(78, node.get("debugTitleRemainingTicks").getAsInt());
            assertEquals(
                    Set.of(UiTitleLifecycleDebug.TIMES, UiTitleLifecycleDebug.CLEAR),
                    node
                            .getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .asList()
                            .stream()
                            .map(value -> value.getAsString())
                            .collect(Collectors.toSet()));
            assertEquals(UiTitleLifecycleDebug.times(10, 70, 20), node.get("debugTitleTimes"));
        }
    }

    @Test
    void sameValueNativeSetterAndFullContentOwnerChangesInvalidateOldTokens() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var node = node(tree);
            UiTitleLifecycleDebug.describeState(tree, node, revisions, state());
            var old = node.getAsJsonObject("resourceVersion").deepCopy();
            for (String key :
                    List.of(
                            "hudOwner",
                            "worldOwner",
                            "titleOwner",
                            "subtitleOwner",
                            "generation",
                            "titleFingerprint",
                            "subtitleFingerprint",
                            "times",
                            "cleared")) {
                var changed = state();
                if (key.endsWith("Fingerprint"))
                    changed.addProperty(key, "changed-full-native-component");
                else if (key.equals("times"))
                    changed.add("times", UiTitleLifecycleDebug.times(11, 70, 20));
                else if (key.equals("cleared")) changed.addProperty(key, true);
                else changed.addProperty(key, changed.get(key).getAsLong() + 1);
                UiTitleLifecycleDebug.describeState(tree, node, revisions, changed);
                assertEquals(
                        key.equals("hudOwner") ? "RESOURCE_MISMATCH" : "STALE_RESOURCE_REVISION",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () ->
                                                ResourceVersionVerifier.verify(
                                                        old,
                                                        node.getAsJsonObject("resourceVersion")))
                                .code(),
                        key);
            }
            UiTitleLifecycleDebug.describeState(tree, node, revisions, state());
            assertNotEquals(
                    old,
                    node.get("resourceVersion"),
                    "An observed ABA must not restore the old receipt");
        }
    }

    @Test
    void fullNativeOldTimingRemainsObservableEvenAboveTheNewWriteLimit() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            var state = state();
            state.add("times", UiTitleLifecycleDebug.times(13000, Integer.MAX_VALUE, 0));
            UiTitleLifecycleDebug.describeState(tree, node(tree), revisions, state);
            assertEquals(state.get("times"), node(tree).get("debugTitleTimes"));
            assertTrue(node(tree).has("resourceVersion"));
            state.addProperty("guardAvailable", false);
            UiTitleLifecycleDebug.describeState(tree, node(tree), revisions, state);
            assertFalse(node(tree).has("resourceVersion"));
            assertTrue(
                    node(tree)
                            .getAsJsonObject("debugMutation")
                            .getAsJsonArray("supportedOperations")
                            .isEmpty());
        }
    }

    @Test
    void userProjectionCannotExposeTheHiddenDebugLifecycleGroup() {
        try (var revisions = new Phase9ASpikeEngine("test")) {
            var tree = tree();
            UiTitleLifecycleDebug.describeState(tree, node(tree), revisions, state());
            var user = UiDataView.tree(tree, UiDataView.Category.USER);
            var debug = UiDataView.tree(tree, UiDataView.Category.DEBUG);
            for (String field :
                    List.of(
                            "debugTitleTimes",
                            "debugTitleCleared",
                            "debugTitleRemainingTicks",
                            "debugTitleGuardAvailable",
                            "resourceVersion",
                            "debugMutation")) {
                assertFalse(user.toString().contains(field), field);
                assertTrue(node(debug).has(field), field);
            }
        }
    }

    @Test
    void passiveNativeHookCountsOnlyTheFiveApprovedSetters() throws Exception {
        var hook = new HudTitleLifecycleMixin() {};
        var changed =
                HudTitleLifecycleMixin.class.getDeclaredMethod(
                        "minecraftProtocolProbe$titleChanged", CallbackInfo.class);
        changed.setAccessible(true);
        var injection = changed.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertEquals(
                Set.of(
                        "setTitle(Lnet/minecraft/network/chat/Component;)V",
                        "setSubtitle(Lnet/minecraft/network/chat/Component;)V",
                        "setTimes(III)V",
                        "resetTitleTimes()V",
                        "clear()V"),
                Set.of(injection.method()));
        assertFalse(injection.cancellable());
        assertEquals("HEAD", injection.at()[0].value());
        assertEquals(0, hook.minecraftProtocolProbe$titleLifecycleGeneration());
        for (int index = 1; index <= 5; index++) {
            changed.invoke(hook, new Object[] {null});
            assertEquals(index, hook.minecraftProtocolProbe$titleLifecycleGeneration());
        }
        var compiled =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/mixin/HudTitleLifecycleMixin");
        for (var method : compiled.methods)
            if (method.name.equals("minecraftProtocolProbe$titleChanged"))
                for (var instruction : method.instructions)
                    assertFalse(
                            instruction instanceof MethodInsnNode,
                            "Passive observation must not invoke a native setter or cancel the callback");
    }

    @Test
    void productionDispatchUsesTheActualNativeTimingAndWholeClearMethods() throws Exception {
        var helper =
                compiled(
                        "io/github/recrivenvi/minecraftprotocol/probe/runtime/UiTitleLifecycleDebug");
        int times = 0, clear = 0, guardedApply = 0;
        for (var method : helper.methods)
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.owner.equals("net/minecraft/client/gui/Gui")
                        && call.name.equals("setTimes")
                        && call.desc.equals("(III)V")) times++;
                if (call.owner.equals("net/minecraft/client/gui/Gui")
                        && call.name.equals("clear")
                        && call.desc.equals("()V")) clear++;
                if (method.name.equals("mutate") && call.name.equals("authorize")) guardedApply++;
                assertNotEquals("setTitle", call.name);
                assertNotEquals("setSubtitle", call.name);
                assertFalse(call.owner.startsWith("net/minecraft/network/protocol/"));
                assertFalse(call.owner.contains("InputDispatchContext"));
            }
        assertEquals(1, times);
        // clear is an invokedynamic method reference, whose bootstrap handle must target the
        // real native method rather than a field write or an empty text replacement.
        assertEquals(0, clear);
        assertTrue(
                helper.methods.stream()
                        .flatMap(
                                method ->
                                        java.util.stream.StreamSupport.stream(
                                                method.instructions.spliterator(), false))
                        .filter(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance)
                        .map(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::cast)
                        .flatMap(call -> java.util.Arrays.stream(call.bsmArgs))
                        .filter(org.objectweb.asm.Handle.class::isInstance)
                        .map(org.objectweb.asm.Handle.class::cast)
                        .anyMatch(
                                handle ->
                                        handle.getOwner().equals("net/minecraft/client/gui/Gui")
                                                && handle.getName().equals("clear")
                                                && handle.getDesc().equals("()V")));
        assertEquals(1, guardedApply);
        var nativeHud = compiled("net/minecraft/client/gui/Gui");
        var nativeClear =
                nativeHud.methods.stream()
                        .filter(method -> method.name.equals("clear"))
                        .findFirst()
                        .orElseThrow();
        var fields = new java.util.HashSet<String>();
        for (var instruction : nativeClear.instructions)
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD) fields.add(field.name);
        assertEquals(Set.of("title", "subtitle", "titleTime"), fields);
        var engine = compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/UiDebugEngine");
        assertTrue(
                engine.methods.stream()
                        .filter(method -> method.name.equals("mutate"))
                        .anyMatch(
                                method -> {
                                    for (var instruction : method.instructions)
                                        if (instruction instanceof MethodInsnNode call
                                                && call.owner.endsWith("/UiTitleLifecycleDebug")
                                                && call.name.equals("mutate")) return true;
                                    return false;
                                }));
    }

    private static JsonObject tree() {
        return JsonParser.parseString(
                        """
                {"screenIdentity":0,"screenRevision":1,"width":480,"height":270,"guiScale":4,"overlayIdentity":0,"children":[],"presentation":{"children":[
                {"nodeId":"hud.titles","controlKey":"hud.titles","role":"group","label":"","visible":false,"active":false,"boundsAvailable":false,"actions":[]}]}}
                """)
                .getAsJsonObject();
    }

    private static JsonObject node(JsonObject tree) {
        return tree.getAsJsonObject("presentation")
                .getAsJsonArray("children")
                .get(0)
                .getAsJsonObject();
    }

    private static JsonObject state() {
        return JsonParser.parseString(
                        """
                {"hudOwner":11,"worldOwner":12,"titleOwner":13,"subtitleOwner":14,"generation":5,"guardAvailable":true,"titleFingerprint":"full-title","subtitleFingerprint":"full-subtitle","times":{"fadeInTicks":10,"stayTicks":70,"fadeOutTicks":20},"remainingTicks":80,"cleared":false}
                """)
                .getAsJsonObject();
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input =
                UiTitleLifecycleDebugTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
