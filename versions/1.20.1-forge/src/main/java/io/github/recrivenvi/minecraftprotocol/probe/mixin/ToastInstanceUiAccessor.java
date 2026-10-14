package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public interface ToastInstanceUiAccessor {
    @Accessor("toast")
    net.minecraft.client.gui.components.toasts.Toast minecraftProtocolProbe$toast();

    @Accessor("index")
    int minecraftProtocolProbe$index();

    @Invoker("getVisibility")
    float minecraftProtocolProbe$portion(long now);
}
