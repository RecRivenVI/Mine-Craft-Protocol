package io.github.recrivenvi.minecraftprotocol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public interface ToastInstanceUiAccessor {
    @Accessor("toast")
    net.minecraft.client.gui.components.toasts.Toast mineCraftProtocol$toast();

    @Accessor("firstSlotIndex")
    int mineCraftProtocol$index();

    @Accessor("visiblePortion")
    float mineCraftProtocol$portion();

    @Accessor("visibility")
    net.minecraft.client.gui.components.toasts.Toast.Visibility mineCraftProtocol$visibility();
}
