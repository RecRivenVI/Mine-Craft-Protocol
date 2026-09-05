package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.ForgeProbeRuntime;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private void charTyped(long window, int codepoint, int modifiers) { throw new AssertionError(); }


    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;setupKeyboardCallbacks(JLorg/lwjgl/glfw/GLFWKeyCallbackI;Lorg/lwjgl/glfw/GLFWCharModsCallbackI;)V"))
    private void minecraftProtocolProbe$nativeIngress(long window, GLFWKeyCallbackI key, GLFWCharModsCallbackI character) {
        InputConstants.setupKeyboardCallbacks(window,
                (w, k, s, a, m) -> this.minecraft.execute(AgentInputContext.nativeTask(() -> ((KeyboardHandler)(Object)this).keyPress(w, k, s, a, m))),
                (w, c, m) -> this.minecraft.execute(AgentInputContext.nativeTask(() -> this.charTyped(w, c, m))));
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        boolean agent = AgentInputContext.consume(AgentInputContext.Event.key(window, key, scanCode, action, modifiers));
        if (ForgeProbeRuntime.handleKeyIngress(window, key, action, agent)) ci.cancel();
    }
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$nativeCharacter(long window, int codepoint, int modifiers, CallbackInfo ci) {
        boolean agent = AgentInputContext.consume(new AgentInputContext.Event(AgentInputContext.Kind.CHARACTER, window, codepoint, modifiers, 0, 0));
        if (!agent && ForgeProbeRuntime.suppressNative(AgentInputContext.Kind.CHARACTER)) ci.cancel();
    }

}
