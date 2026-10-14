package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSelectionList.class)
public interface SelectionListAccessor {
    @Invoker("getScrollbarPosition")
    int minecraftProtocolProbe$scrollbarX();

    @Invoker("getMaxPosition")
    int minecraftProtocolProbe$contentHeight();

    @Invoker("getRowTop")
    int minecraftProtocolProbe$rowTop(int index);

    @Invoker("getRowBottom")
    int minecraftProtocolProbe$rowBottom(int index);
}
