package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.UiToastText;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AdvancementToast.class, SystemToast.class, TutorialToast.class, RecipeToast.class})
public abstract class ToastRenderTextMixin
        implements UiRenderedText.Source, UiToastText.FrameSource {
    @Unique private UiRenderedText mineCraftProtocol$text;
    @Unique private UiToastText.Frame mineCraftProtocol$frame;

    @Unique
    private final String mineCraftProtocol$contextKey = "toast:" + java.util.UUID.randomUUID();

    @Inject(method = "render", at = @At("RETURN"))
    private void mineCraftProtocol$capture(
            GuiGraphics graphics,
            ToastComponent manager,
            long elapsed,
            CallbackInfoReturnable<Toast.Visibility> ci) {
        mineCraftProtocol$text =
                UiToastText.capture((Toast) (Object) this, manager.getMinecraft().font, elapsed);
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
