package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.AbstractScrollWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractScrollWidget.class)
public interface ScrollWidgetUiAccessor {
    @Invoker("scrollAmount")
    double mineCraftProtocol$scroll();

    @Invoker("getMaxScrollAmount")
    int mineCraftProtocol$maxScroll();

    @Invoker("getScrollBarHeight")
    int mineCraftProtocol$thumbHeight();

    @Invoker("scrollbarVisible")
    boolean mineCraftProtocol$visible();
}
