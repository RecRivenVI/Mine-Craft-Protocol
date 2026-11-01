package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractScrollArea.class)
public interface ScrollAreaUiAccessor {
    @Invoker("scrollBarX")
    int mineCraftProtocol$scrollbarX();

    @Invoker("scrollerHeight")
    int mineCraftProtocol$thumbHeight();
}
