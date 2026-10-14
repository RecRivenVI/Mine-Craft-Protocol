package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.advancements.AdvancementTabType")
public interface AdvancementTypeUiAccessor {
    @Invoker("getX")
    int minecraftProtocolProbe$x(int index);

    @Invoker("getY")
    int minecraftProtocolProbe$y(int index);

    @Invoker("getWidth")
    int minecraftProtocolProbe$width();

    @Invoker("getHeight")
    int minecraftProtocolProbe$height();
}
