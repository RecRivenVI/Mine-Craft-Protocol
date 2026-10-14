package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiToastText;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AdvancementToast.class, SystemToast.class, TutorialToast.class, RecipeToast.class})
public abstract class ToastRenderTextMixin implements UiRenderedText.Source {
    @Unique private UiRenderedText minecraftProtocolProbe$text;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minecraftProtocolProbe$capture(
            GuiGraphicsExtractor graphics, Font font, long elapsed, CallbackInfo ci) {
        minecraftProtocolProbe$text = UiToastText.capture((Toast) (Object) this, font, elapsed);
    }

    @Override
    public UiRenderedText minecraftProtocolProbe$renderedText() {
        return minecraftProtocolProbe$text;
    }
}
