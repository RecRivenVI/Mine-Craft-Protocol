package io.github.recrivenvi.minecraftprotocol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(targets = "net.minecraft.client.gui.screens.advancements.AdvancementTabType")
public interface AdvancementTypeUiAccessor {
    @Invoker("getX")
    int mineCraftProtocol$x(int index);

    @Invoker("getY")
    int mineCraftProtocol$y(int index);

    @Accessor("width")
    int mineCraftProtocol$width();

    @Accessor("height")
    int mineCraftProtocol$height();
}
