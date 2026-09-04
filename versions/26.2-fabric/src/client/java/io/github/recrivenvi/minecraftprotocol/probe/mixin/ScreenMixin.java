package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.FabricProbeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minecraftProtocolProbe$renderControlChrome(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
        FabricProbeRuntime.renderControlChrome(graphics);
    }
}
