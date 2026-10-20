package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public interface ToastInstanceUiAccessor {
    @Accessor("visibility")
    net.minecraft.client.gui.components.toasts.Toast.Visibility minecraftProtocolProbe$visibility();

    @Accessor("toast")
    net.minecraft.client.gui.components.toasts.Toast minecraftProtocolProbe$toast();

    @Accessor("firstSlotIndex")
    int minecraftProtocolProbe$index();

    @Accessor("visiblePortion")
    float minecraftProtocolProbe$portion();
}
