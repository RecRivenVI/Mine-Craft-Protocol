package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiToastText;
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
    @Unique private UiRenderedText minecraftProtocolProbe$text;
    @Unique private UiToastText.Frame minecraftProtocolProbe$frame;

    @Unique
    private final String minecraftProtocolProbe$contextKey = "toast:" + java.util.UUID.randomUUID();

    @Inject(method = "render", at = @At("RETURN"))
    private void minecraftProtocolProbe$capture(
            GuiGraphics graphics,
            ToastComponent manager,
            long elapsed,
            CallbackInfoReturnable<Toast.Visibility> ci) {
        minecraftProtocolProbe$text =
                UiToastText.capture((Toast) (Object) this, manager.getMinecraft().font, elapsed);
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
