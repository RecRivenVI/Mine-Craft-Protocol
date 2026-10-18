package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.ObservationRevisionTracker;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiToastResetSource;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SystemToastResetMixin;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class UiSystemToastResetTest {
    @Test
    void productionHookCountsEachNativeResetEntryWithoutCancellingIt() throws Exception {
        var hook =
                SystemToastResetMixin.class.getDeclaredMethod(
                        "minecraftProtocolProbe$reset",
                        Component.class,
                        Component.class,
                        CallbackInfo.class);
        var injection = hook.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertArrayEquals(
                new String[] {
                    "reset(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V"
                },
                injection.method());
        assertEquals("HEAD", injection.at()[0].value());
        assertFalse(injection.cancellable());
        var source = new SystemToastResetMixin() {};
        hook.setAccessible(true);
        for (long expected = 1; expected <= 3; expected++) {
            var callback = new CallbackInfo("reset", false);
            hook.invoke(source, null, null, callback);
            assertEquals(expected, source.minecraftProtocolProbe$resetGeneration());
            assertFalse(callback.isCancelled());
        }
    }

    @Test
    void resetGenerationKeepsStableReceiptsButRejectsSameValueResetsAndAba() {
        var sequence = new AtomicLong(1);
        UiToastResetSource source = sequence::get;
        var tracker = new ObservationRevisionTracker();
        var state = new JsonObject();
        state.addProperty("content", "complete styled native content");
        state.addProperty("hideRequested", false);
        UiToastDebug.systemResetState(state, source);
        var receipt = tracker.revision("ui_node", "toast-owner", state);
        UiToastDebug.systemResetState(state, source);
        assertEquals(receipt, tracker.revision("ui_node", "toast-owner", state));
        sequence.incrementAndGet();
        UiToastDebug.systemResetState(state, source);
        var reset = tracker.revision("ui_node", "toast-owner", state);
        assertEquals(
                "STALE_RESOURCE_REVISION",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () -> ResourceVersionVerifier.verify(receipt, reset))
                        .code());
        state.addProperty("content", "temporary native content");
        sequence.incrementAndGet();
        UiToastDebug.systemResetState(state, source);
        tracker.revision("ui_node", "toast-owner", state);
        state.addProperty("content", "complete styled native content");
        sequence.incrementAndGet();
        UiToastDebug.systemResetState(state, source);
        var aba = tracker.revision("ui_node", "toast-owner", state);
        assertNotEquals(receipt, aba);
        assertEquals(
                "STALE_RESOURCE_REVISION",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () -> ResourceVersionVerifier.verify(receipt, aba))
                        .code());
    }

    @Test
    void productionCanonicalUsesGenerationAndPassiveHookOnlyWritesItsOwnCounter() throws Exception {
        var canonical =
                compiled("runtime/UiToastDebug").methods.stream()
                        .filter(method -> method.name.equals("canonicalState"))
                        .findFirst()
                        .orElseThrow();
        int generationCalls = 0;
        for (var instruction : canonical.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.name.equals("systemResetState")) generationCalls++;
            assertNotEquals("minecraftProtocolProbe$changed", call.name);
            assertNotEquals("minecraftProtocolProbe$lastChanged", call.name);
        }
        assertEquals(1, generationCalls);
        var mixin = compiled("mixin/SystemToastResetMixin");
        assertEquals(1, mixin.fields.size());
        assertEquals("J", mixin.fields.get(0).desc);
        assertEquals(0, mixin.fields.get(0).access & Opcodes.ACC_STATIC);
        var hook =
                mixin.methods.stream()
                        .filter(method -> method.name.equals("minecraftProtocolProbe$reset"))
                        .findFirst()
                        .orElseThrow();
        int writes = 0;
        for (var instruction : hook.instructions) {
            assertFalse(
                    instruction instanceof MethodInsnNode,
                    "Passive reset counter must not call native/input/cancel methods");
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD) {
                assertEquals("minecraftProtocolProbe$resetGeneration", field.name);
                writes++;
            }
        }
        assertEquals(1, writes);
    }

    private static ClassNode compiled(String localName) throws Exception {
        String name = "/io/github/recrivenvi/minecraftprotocol/probe/" + localName + ".class";
        try (var input = UiSystemToastResetTest.class.getResourceAsStream(name)) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
