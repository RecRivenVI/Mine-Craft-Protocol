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
public abstract class ToastRenderTextMixin
        implements UiRenderedText.Source, UiToastText.FrameSource {
    @Unique private UiRenderedText minecraftProtocolProbe$text;
    @Unique private UiToastText.Frame minecraftProtocolProbe$frame;

    @Unique
    private final String minecraftProtocolProbe$contextKey = "toast:" + java.util.UUID.randomUUID();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minecraftProtocolProbe$capture(
            GuiGraphicsExtractor graphics, Font font, long elapsed, CallbackInfo ci) {
        minecraftProtocolProbe$text = UiToastText.capture((Toast) (Object) this, font, elapsed);
        minecraftProtocolProbe$frame =
                UiToastText.captureFrame(
                        (Toast) (Object) this,
                        graphics,
                        minecraftProtocolProbe$text,
                        minecraftProtocolProbe$contextKey);
    }

    @Override
    public UiRenderedText minecraftProtocolProbe$renderedText() {
        return minecraftProtocolProbe$text;
    }

    @Override
    public UiToastText.Frame minecraftProtocolProbe$toastFrame() {
        return minecraftProtocolProbe$frame;
    }
}
