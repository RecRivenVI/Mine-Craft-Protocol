package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractScrollArea.class)
public interface ScrollAreaUiAccessor {
    @Invoker("scrollBarX")
    int minecraftProtocolProbe$scrollbarX();

    @Invoker("scrollerHeight")
    int minecraftProtocolProbe$thumbHeight();
}
