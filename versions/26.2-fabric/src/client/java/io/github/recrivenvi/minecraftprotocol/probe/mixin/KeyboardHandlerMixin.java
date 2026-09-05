package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.lwjgl.glfw.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private void charTyped(long window, CharacterEvent event) { throw new AssertionError(); }
    @Shadow private void preeditCallback(long window, PreeditEvent event) { throw new AssertionError(); }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;setupKeyboardCallbacks(Lcom/mojang/blaze3d/platform/Window;Lorg/lwjgl/glfw/GLFWKeyCallbackI;Lorg/lwjgl/glfw/GLFWCharCallbackI;Lorg/lwjgl/glfw/GLFWPreeditCallbackI;Lorg/lwjgl/glfw/GLFWIMEStatusCallbackI;)V"))
    private void minecraftProtocolProbe$nativeIngress(Window window, GLFWKeyCallbackI key, GLFWCharCallbackI character, GLFWPreeditCallbackI preedit, GLFWIMEStatusCallbackI ime) {
        InputConstants.setupKeyboardCallbacks(window,
                (w, k, s, a, m) -> this.minecraft.execute(AgentInputContext.nativeTask(() -> ((KeyboardHandlerInvoker)(Object)this).minecraftProtocolProbe$keyPress(w, a, new KeyEvent(k, s, m)))),
                (w, c) -> this.minecraft.execute(AgentInputContext.nativeTask(() -> this.charTyped(w, new CharacterEvent(c)))),
                (w, size, ptr, count, sizes, focused, caret) -> {
                    PreeditEvent event = PreeditEvent.createFromCallback(size, ptr, count, sizes, focused, caret);
                    this.minecraft.execute(AgentInputContext.nativeTask(() -> this.preeditCallback(w, event)));
                },
                w -> this.minecraft.execute(AgentInputContext.nativeTask(() -> { if (!FabricProbeRuntime.suppressNative(AgentInputContext.Kind.PREEDIT)) ime.invoke(w); })));
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        boolean agent = AgentInputContext.consume(AgentInputContext.Event.key(window, event.key(), event.scancode(), action, event.modifiers()));
        if (FabricProbeRuntime.handleKeyIngress(window, event.key(), action, agent)) ci.cancel();
    }
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeCharacter(long window, CharacterEvent event, CallbackInfo ci) {
        boolean agent = AgentInputContext.consume(new AgentInputContext.Event(AgentInputContext.Kind.CHARACTER, window, event.codepoint(), 0, 0, 0));
        if (!agent && FabricProbeRuntime.suppressNative(AgentInputContext.Kind.CHARACTER)) ci.cancel();
    }
    @Inject(method = "preeditCallback", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativePreedit(long window, PreeditEvent event, CallbackInfo ci) {
        boolean agent = AgentInputContext.consume(new AgentInputContext.Event(AgentInputContext.Kind.PREEDIT, window, event == null ? 0 : System.identityHashCode(event), 0, 0, 0));
        if (!agent && FabricProbeRuntime.suppressNative(AgentInputContext.Kind.PREEDIT)) ci.cancel();
    }
    @Inject(method = "resubmitLastPreeditEvent", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$preeditReplay(GuiEventListener listener, CallbackInfo ci) {
        if (FabricProbeRuntime.suppressNative(AgentInputContext.Kind.PREEDIT)) ci.cancel();
    }
}
