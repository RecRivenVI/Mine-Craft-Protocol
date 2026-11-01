package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementTab.class)
public interface AdvancementTabUiAccessor {
    @Accessor("centered")
    boolean mineCraftProtocol$centered();

    @Accessor("minX")
    int mineCraftProtocol$minX();

    @Accessor("maxX")
    int mineCraftProtocol$maxX();

    @Accessor("minY")
    int mineCraftProtocol$minY();

    @Accessor("maxY")
    int mineCraftProtocol$maxY();

    @Accessor("widgets")
    Map<AdvancementHolder, AdvancementWidget> mineCraftProtocol$widgets();

    @Accessor("scrollX")
    double mineCraftProtocol$scrollX();

    @Accessor("scrollY")
    double mineCraftProtocol$scrollY();
}
