package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.AbstractScrollWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractScrollWidget.class)
public interface ScrollWidgetUiAccessor {
    @Invoker("scrollAmount")
    double minecraftProtocolProbe$scroll();

    @Invoker("getMaxScrollAmount")
    int minecraftProtocolProbe$maxScroll();

    @Invoker("getScrollBarHeight")
    int minecraftProtocolProbe$thumbHeight();

    @Invoker("scrollbarVisible")
    boolean minecraftProtocolProbe$visible();
}
