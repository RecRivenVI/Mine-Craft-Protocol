package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SystemToast.class)
public interface SystemToastUiAccessor {
    @Accessor("lastChanged")
    long minecraftProtocolProbe$lastChanged();

    @Accessor("changed")
    boolean minecraftProtocolProbe$changed();

    @Accessor("forceHide")
    boolean minecraftProtocolProbe$forceHide();

    @Accessor("titleLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> minecraftProtocolProbe$title();

    @Accessor("messageLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> minecraftProtocolProbe$message();
}
