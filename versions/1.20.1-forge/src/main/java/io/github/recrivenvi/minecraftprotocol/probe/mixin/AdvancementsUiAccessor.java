package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.screens.advancements.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementsScreen.class)
public interface AdvancementsUiAccessor {
    @Accessor("tabs")
    Map<Advancement, AdvancementTab> minecraftProtocolProbe$tabs();

    @Accessor("selectedTab")
    AdvancementTab minecraftProtocolProbe$selected();

    @Accessor(value = "tabPage", remap = false)
    static int minecraftProtocolProbe$page() {
        throw new AssertionError();
    }
}
