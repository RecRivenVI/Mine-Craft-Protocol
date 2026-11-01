package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementsScreen.class)
public interface AdvancementsUiAccessor {
    @Accessor("tabs")
    Map<AdvancementHolder, AdvancementTab> mineCraftProtocol$tabs();

    @Accessor("selectedTab")
    AdvancementTab mineCraftProtocol$selected();

    @Accessor("tabPage")
    static int mineCraftProtocol$page() {
        throw new AssertionError();
    }
}
