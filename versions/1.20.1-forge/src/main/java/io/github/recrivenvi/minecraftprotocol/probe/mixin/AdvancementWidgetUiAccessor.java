package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.advancements.*;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementWidget.class)
public interface AdvancementWidgetUiAccessor {
    @Accessor("display")
    DisplayInfo minecraftProtocolProbe$display();

    @Accessor("advancement")
    Advancement minecraftProtocolProbe$advancement();
}
