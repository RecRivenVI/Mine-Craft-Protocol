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
public abstract class ToastRenderTextMixin implements UiRenderedText.Source {
    @Unique private UiRenderedText minecraftProtocolProbe$text;

    @Inject(method = "render", at = @At("RETURN"))
    private void minecraftProtocolProbe$capture(
            GuiGraphics graphics,
            ToastComponent manager,
            long elapsed,
            CallbackInfoReturnable<Toast.Visibility> ci) {
        // HIDE can be an early return without drawing; fail closed on that path.
        minecraftProtocolProbe$text =
                ci.getReturnValue() == Toast.Visibility.SHOW
                        ? UiToastText.capture(
                                (Toast) (Object) this, manager.getMinecraft().font, elapsed)
                        : null;
    }

    @Override
    public UiRenderedText minecraftProtocolProbe$renderedText() {
        return minecraftProtocolProbe$text;
    }
}
