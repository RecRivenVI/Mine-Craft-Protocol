package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeUiAccessor {
    @Accessor("scrollOffs")
    float minecraftProtocolProbe$scrollOffs();

    @Accessor("scrollOffs")
    void minecraftProtocolProbe$scrollOffs(float value);

    @Accessor("searchBox")
    net.minecraft.client.gui.components.EditBox minecraftProtocolProbe$searchBox();

    @Invoker("canScroll")
    boolean minecraftProtocolProbe$canScroll();

    @Accessor("selectedTab")
    static CreativeModeTab minecraftProtocolProbe$selectedTab() {
        throw new AssertionError();
    }

    @Invoker("getTabX")
    int minecraftProtocolProbe$tabX(CreativeModeTab tab);

    @Invoker("getTabY")
    int minecraftProtocolProbe$tabY(CreativeModeTab tab);
}
