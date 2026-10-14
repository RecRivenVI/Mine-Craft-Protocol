package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.screens.advancements.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementTab.class)
public interface AdvancementTabUiAccessor {
    @Accessor("minX")
    int minecraftProtocolProbe$minX();

    @Accessor("maxX")
    int minecraftProtocolProbe$maxX();

    @Accessor("minY")
    int minecraftProtocolProbe$minY();

    @Accessor("maxY")
    int minecraftProtocolProbe$maxY();

    @Accessor("widgets")
    Map<Advancement, AdvancementWidget> minecraftProtocolProbe$widgets();

    @Accessor("scrollX")
    double minecraftProtocolProbe$scrollX();

    @Accessor("scrollY")
    double minecraftProtocolProbe$scrollY();
}
