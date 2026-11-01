package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSelectionList.class)
public interface SelectionListAccessor {
    @Invoker("getScrollbarPosition")
    int mineCraftProtocol$scrollbarX();

    @Invoker("getMaxPosition")
    int mineCraftProtocol$contentHeight();

    @Invoker("getRowTop")
    int mineCraftProtocol$rowTop(int index);

    @Invoker("getRowBottom")
    int mineCraftProtocol$rowBottom(int index);
}
