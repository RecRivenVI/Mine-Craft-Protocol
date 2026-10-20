package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class BackgroundFocusProbeMixin {
    @Shadow private boolean windowActive;

    @Redirect(
            method = "isWindowActive",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;windowActive:Z"))
    private boolean minecraftProtocolProbe$nativeFocus(Minecraft client) {
        if (!"true".equals(System.getenv("MCP_VALIDATION_BACKGROUND_WINDOW")))
            return this.windowActive;
        // A never-focused test window need not deliver the callback that initializes this cache.
        // Replace only the native cached read, preserving the production routed-input RETURN hook.
        return GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_FOCUSED)
                == GLFW.GLFW_TRUE;
    }
}
