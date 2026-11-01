package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiToastText;
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
    @Unique private UiRenderedText mineCraftProtocol$text;
    @Unique private UiToastText.Frame mineCraftProtocol$frame;

    @Unique
    private final String mineCraftProtocol$contextKey = "toast:" + java.util.UUID.randomUUID();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mineCraftProtocol$capture(
            GuiGraphicsExtractor graphics, Font font, long elapsed, CallbackInfo ci) {
        mineCraftProtocol$text = UiToastText.capture((Toast) (Object) this, font, elapsed);
        mineCraftProtocol$frame =
                UiToastText.captureFrame(
                        (Toast) (Object) this,
                        graphics,
                        mineCraftProtocol$text,
                        mineCraftProtocol$contextKey);
    }

    @Override
    public UiRenderedText mineCraftProtocol$renderedText() {
        return mineCraftProtocol$text;
    }

    @Override
    public UiToastText.Frame mineCraftProtocol$toastFrame() {
        return mineCraftProtocol$frame;
    }
}
