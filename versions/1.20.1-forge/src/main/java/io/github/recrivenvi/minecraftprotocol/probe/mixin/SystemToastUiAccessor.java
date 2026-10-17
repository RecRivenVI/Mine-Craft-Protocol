package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SystemToast.class)
public interface SystemToastUiAccessor {
    @Accessor("lastChanged")
    long minecraftProtocolProbe$lastChanged();

    @Accessor("changed")
    boolean minecraftProtocolProbe$changed();

    @Accessor("title")
    Component minecraftProtocolProbe$title();

    @Accessor("messageLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> minecraftProtocolProbe$message();
}
