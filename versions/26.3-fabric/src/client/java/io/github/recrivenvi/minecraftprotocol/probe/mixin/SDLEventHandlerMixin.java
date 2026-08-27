package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep SDL provenance across the native event queue, even during routed callback re-entry. */
@Mixin(SDLEventHandler.class)
abstract class SDLEventHandlerMixin {
    @Redirect(
            method = {
                "handleKeymapChangedEvent", "handleKeyEvent", "handleTextInputEvent",
                "handleTextEditingEvent", "handleMouseMotionEvent", "handleMouseButtonEvent",
                "handleMouseWheelEvent", "handleDropCompleteEvent"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
    private void minecraftProtocolProbe$nativeTask(Minecraft client, Runnable task) {
        client.execute(AgentInputContext.nativeTask(task));
    }
}
