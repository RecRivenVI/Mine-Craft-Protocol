package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class ChatFocusedPresentationMixin {
    @Redirect(
            method = "handleMessage",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V"))
    private void minecraftProtocolProbe$text(
            ActiveTextCollector collector,
            TextAlignment alignment,
            int x,
            int y,
            ActiveTextCollector.Parameters parameters,
            FormattedCharSequence text) {
        collector.accept(alignment, x, y, parameters, text);
        UiNativeText.active("chat_focused", alignment, x, y, parameters, text);
    }
}
