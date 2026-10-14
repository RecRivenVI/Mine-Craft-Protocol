package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SystemToast.class)
public interface SystemToastUiAccessor {
    @Accessor("titleLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> minecraftProtocolProbe$title();

    @Accessor("messageLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> minecraftProtocolProbe$message();
}
