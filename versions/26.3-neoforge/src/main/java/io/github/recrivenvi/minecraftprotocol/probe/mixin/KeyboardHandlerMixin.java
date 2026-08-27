package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeKey(
            long window, int action, KeyEvent event, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        AgentInputContext.Event.key(
                                window, event.key(), event.keycode(), action, event.modifiers()));
        if (NeoForgeProbeRuntime.handleKeyIngress(window, event.key(), action, agent)) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeCharacter(
            long window, CharacterEvent event, CallbackInfo ci) {
        boolean agent =
                AgentInputContext.consume(
                        new AgentInputContext.Event(
                                AgentInputContext.Kind.CHARACTER,
                                window,
                                event.codepoint(),
                                0,
                                0,
                                0));
        if (!agent && NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.CHARACTER))
            ci.cancel();
    }

    @Inject(method = "textInput", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeText(long window, String text, CallbackInfo ci) {
        if (NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.CHARACTER)) ci.cancel();
    }

    @Inject(method = "textEditing", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativePreedit(
            long window, PreeditEvent event, CallbackInfo ci) {
        if (NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.PREEDIT)) ci.cancel();
    }

    @Inject(method = "resubmitLastPreeditEvent", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$preeditReplay(GuiEventListener listener, CallbackInfo ci) {
        if (NeoForgeProbeRuntime.suppressNative(AgentInputContext.Kind.PREEDIT)) ci.cancel();
    }
}
