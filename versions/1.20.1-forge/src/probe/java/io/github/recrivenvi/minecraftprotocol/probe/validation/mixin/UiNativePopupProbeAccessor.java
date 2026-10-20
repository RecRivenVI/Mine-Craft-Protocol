package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.client.gui.screens.PopupScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PopupScreen.class)
public interface UiNativePopupProbeAccessor {
    @Invoker("<init>")
    static PopupScreen minecraftProtocolProbe$create(
            Component title,
            List<Component> message,
            ImmutableList<PopupScreen.ButtonOption> buttons) {
        throw new AssertionError();
    }
}
