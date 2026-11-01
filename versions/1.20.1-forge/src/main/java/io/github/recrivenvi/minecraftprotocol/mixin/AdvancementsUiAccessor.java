package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.screens.advancements.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementsScreen.class)
public interface AdvancementsUiAccessor {
    @Accessor("tabs")
    Map<Advancement, AdvancementTab> mineCraftProtocol$tabs();

    @Accessor("selectedTab")
    AdvancementTab mineCraftProtocol$selected();

    @Accessor(value = "tabPage", remap = false)
    static int mineCraftProtocol$page() {
        throw new AssertionError();
    }
}
