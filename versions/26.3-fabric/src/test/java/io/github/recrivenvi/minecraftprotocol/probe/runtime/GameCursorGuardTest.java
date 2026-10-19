package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.cursor.CursorType;
import io.github.recrivenvi.minecraftprotocol.core.AutomationEngine;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.safety.AgentPointer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

final class GameCursorGuardTest {
    private static final String WINDOW_MIXIN =
            "io/github/recrivenvi/minecraftprotocol/probe/mixin/WindowMixin";
    private static final String CURSOR_TYPE = "com/mojang/blaze3d/platform/cursor/CursorType";

    @Test
    void gameRequestInvalidatesPendingGestureWithoutChangingScreen() {
        var pointer = new AgentPointer();
        pointer.viewport(427, 240);
        pointer.plane(AgentPointer.Plane.GUI_ABSOLUTE, 213.5, 120);
        pointer.beginGameControl(false);
        JsonObject tree = new JsonObject(), guard = new JsonObject();
        tree.addProperty("screenRevision", 5);
        tree.addProperty("gameCursorRevision", pointer.gameCursorRevision());
        guard.add("frame", tree.deepCopy());
        AutomationEngine.validatePointerGuard(guard, tree);
        pointer.move(91.5, 77.25);
        assertFalse(pointer.gameCapture(false, 213.5, 120));
        tree.addProperty("gameCursorRevision", pointer.gameCursorRevision());
        AutomationEngine.validatePointerGuard(guard, tree);
        assertTrue(pointer.gameRequest(false, false, 10, 20));
        tree.addProperty("gameCursorRevision", pointer.gameCursorRevision());
        var error =
                assertThrows(
                        ProtocolState.ProtocolException.class,
                        () -> AutomationEngine.validatePointerGuard(guard, tree));
        assertEquals("UI_TARGET_INVALIDATED", error.code());
    }

    @Test
    void takeoverKeepsThePhysicalCursorDefaultAndInactivePreservesEveryRequestedShape()
            throws Exception {
        var constructor = CursorType.class.getDeclaredConstructor(String.class, long.class);
        constructor.setAccessible(true);
        var helper =
                Class.forName(WINDOW_MIXIN.replace('/', '.'))
                        .getDeclaredMethod(
                                "minecraftProtocolProbe$hostCursorShape",
                                CursorType.class,
                                boolean.class);
        helper.setAccessible(true);
        assertSame(CursorType.DEFAULT, helper.invoke(null, CursorType.DEFAULT, true));
        assertSame(CursorType.DEFAULT, helper.invoke(null, CursorType.DEFAULT, false));
        for (String name :
                new String[] {
                    "pointing_hand", "resize_ew", "resize_ns", "resize_nwse", "resize_nesw", "ibeam"
                }) {
            CursorType requested = constructor.newInstance(name, 0L);
            assertNotSame(CursorType.DEFAULT, requested);
            assertSame(CursorType.DEFAULT, helper.invoke(null, requested, true), name);
            assertSame(requested, helper.invoke(null, requested, false), name);
        }
    }

    @Test
    void finalWindowArgumentHookUsesActualTakeoverStateBeforeNativeSelection() throws Exception {
        var handler =
                Class.forName(WINDOW_MIXIN.replace('/', '.'))
                        .getDeclaredMethod("minecraftProtocolProbe$hostCursor", CursorType.class);
        var injection = handler.getAnnotation(ModifyVariable.class);
        assertNotNull(injection);
        assertArrayEquals(
                new String[] {"selectCursor(L" + CURSOR_TYPE + ";)V"}, injection.method());
        assertEquals(1, injection.at().length);
        assertEquals("HEAD", injection.at()[0].value());
        assertTrue(injection.argsOnly());
        assertEquals(0, injection.ordinal());
        var hook =
                compiled(WINDOW_MIXIN).methods.stream()
                        .filter(method -> method.name.equals(handler.getName()))
                        .findFirst()
                        .orElseThrow();
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : hook.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
            if (instruction instanceof FieldInsnNode field)
                assertFalse(
                        field.getOpcode() == Opcodes.PUTFIELD
                                || field.getOpcode() == Opcodes.PUTSTATIC,
                        "Cursor selection must not change Vanilla configuration or input state");
        }
        assertEquals(2, calls.size());
        assertEquals(
                "io/github/recrivenvi/minecraftprotocol/probe/runtime/FabricProbeRuntime",
                calls.get(0).owner);
        assertEquals("isAgentControlActive", calls.get(0).name);
        assertEquals("()Z", calls.get(0).desc);
        assertEquals(WINDOW_MIXIN, calls.get(1).owner);
        assertEquals("minecraftProtocolProbe$hostCursorShape", calls.get(1).name);
        assertEquals("(L" + CURSOR_TYPE + ";Z)L" + CURSOR_TYPE + ";", calls.get(1).desc);
    }

    @Test
    void nativeWindowCachesTheSelectedArgumentBeforeApplyingTheOsShape() throws Exception {
        var selection =
                compiled("com/mojang/blaze3d/platform/Window").methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("selectCursor")
                                                && method.desc.equals("(L" + CURSOR_TYPE + ";)V"))
                        .findFirst()
                        .orElseThrow();
        int index = 0, cached = -1, applied = -1;
        for (var instruction : selection.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD
                    && field.name.equals("currentCursor")) cached = index;
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(CURSOR_TYPE)
                    && call.name.equals("select")) {
                assertEquals("()V", call.desc);
                applied = index;
            }
            index++;
        }
        assertTrue(cached >= 0 && applied > cached, "Vanilla cache and applied shape must agree");
    }

    @Test
    void productionManifestDeclaresTheActualArgumentReplacementWithoutCancellingInput()
            throws Exception {
        var runtime =
                compiled("io/github/recrivenvi/minecraftprotocol/probe/runtime/FabricProbeRuntime");
        var producer =
                runtime.methods.stream()
                        .filter(method -> method.name.startsWith("lambda$hookManifest$"))
                        .filter(
                                method -> {
                                    for (var instruction : method.instructions)
                                        if (instruction instanceof LdcInsnNode literal
                                                && literal.cst.equals("host_cursor_shape_guard"))
                                            return true;
                                    return false;
                                })
                        .findFirst()
                        .orElseThrow();
        var manifest =
                runtime.methods.stream()
                        .filter(method -> method.name.equals("hookManifest"))
                        .findFirst()
                        .orElseThrow();
        boolean connected = false;
        for (var instruction : manifest.instructions)
            if (instruction instanceof InvokeDynamicInsnNode factory)
                for (Object argument : factory.bsmArgs)
                    if (argument instanceof Handle handle
                            && handle.getOwner().equals(runtime.name)
                            && handle.getName().equals(producer.name)) connected = true;
        assertTrue(connected, "Only the actual hookManifest callback proves a public declaration");
        var instructions = producer.instructions.toArray();
        int start = 0;
        while (!(instructions[start] instanceof LdcInsnNode firstLiteral
                && firstLiteral.cst.equals("host_cursor_shape_guard"))) start++;
        var arguments = new ArrayList<String>();
        int call = start;
        for (; call < instructions.length; call++) {
            if (instructions[call] instanceof LdcInsnNode argumentLiteral)
                arguments.add((String) argumentLiteral.cst);
            if (instructions[call] instanceof MethodInsnNode invocation) {
                assertEquals(runtime.name, invocation.owner);
                assertEquals("hook", invocation.name);
                break;
            }
        }
        assertEquals(
                List.of(
                        "host_cursor_shape_guard",
                        "MIXIN_MODIFY_VARIABLE",
                        "Window.selectCursor(CursorType)",
                        "HEAD_ARGUMENT",
                        "default_host_shape_during_takeover",
                        "configured_pending_unified_acceptance",
                        "control.agent_presence"),
                arguments);
        Map<String, Object> properties =
                Map.of(
                        "plane",
                        "OPERATOR_CONTROL",
                        "replacement",
                        true,
                        "cancellable",
                        false,
                        "intentionalControlFlow",
                        true);
        var verified = new HashSet<String>();
        boolean published = false;
        for (int index = call + 1; index < instructions.length; index++) {
            if (instructions[index] instanceof MethodInsnNode invocation
                    && invocation.owner.equals("com/google/gson/JsonArray")
                    && invocation.name.equals("add")) {
                published = true;
                break;
            }
            if (!(instructions[index] instanceof LdcInsnNode propertyLiteral)
                    || !(propertyLiteral.cst instanceof String property)
                    || !properties.containsKey(property)) continue;
            int next = index + 1;
            while (instructions[next].getOpcode() < 0) next++;
            Object value = properties.get(property);
            if (value instanceof Boolean flag)
                assertEquals(
                        flag ? Opcodes.ICONST_1 : Opcodes.ICONST_0, instructions[next].getOpcode());
            else assertEquals(value, ((LdcInsnNode) instructions[next]).cst);
            assertTrue(verified.add(property));
        }
        assertEquals(properties.keySet(), verified);
        assertTrue(published, "The descriptor must actually be appended to public hooks");
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input = GameCursorGuardTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
