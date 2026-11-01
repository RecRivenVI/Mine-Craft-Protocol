package io.github.recrivenvi.minecraftprotocol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.advancements.AdvancementTabType")
public interface AdvancementTypeUiAccessor {
    @Invoker("getX")
    int mineCraftProtocol$x(int index);

    @Invoker("getY")
    int mineCraftProtocol$y(int index);

    @Invoker("getWidth")
    int mineCraftProtocol$width();

    @Invoker("getHeight")
    int mineCraftProtocol$height();
}
