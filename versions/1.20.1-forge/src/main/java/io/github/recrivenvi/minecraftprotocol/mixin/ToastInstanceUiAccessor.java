package io.github.recrivenvi.minecraftprotocol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public interface ToastInstanceUiAccessor {
    @Accessor("visibility")
    net.minecraft.client.gui.components.toasts.Toast.Visibility mineCraftProtocol$visibility();

    @Accessor("toast")
    net.minecraft.client.gui.components.toasts.Toast mineCraftProtocol$toast();

    @Accessor("index")
    int mineCraftProtocol$index();

    @Invoker("getVisibility")
    float mineCraftProtocol$portion(long now);
}
