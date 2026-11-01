package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SystemToast.class)
public interface SystemToastUiAccessor {
    @Accessor("lastChanged")
    long mineCraftProtocol$lastChanged();

    @Accessor("changed")
    boolean mineCraftProtocol$changed();

    @Accessor("forceHide")
    boolean mineCraftProtocol$forceHide();

    @Accessor("titleLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> mineCraftProtocol$title();

    @Accessor("messageLines")
    java.util.List<net.minecraft.util.FormattedCharSequence> mineCraftProtocol$message();
}
