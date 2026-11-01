package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeUiAccessor {
    @Accessor("scrollOffs")
    float mineCraftProtocol$scrollOffs();

    @Accessor("scrollOffs")
    void mineCraftProtocol$scrollOffs(float value);

    @Accessor("searchBox")
    net.minecraft.client.gui.components.EditBox mineCraftProtocol$searchBox();

    @Invoker("canScroll")
    boolean mineCraftProtocol$canScroll();

    @Accessor("selectedTab")
    static CreativeModeTab mineCraftProtocol$selectedTab() {
        throw new AssertionError();
    }

    @Invoker("getTabX")
    int mineCraftProtocol$tabX(CreativeModeTab tab);

    @Invoker("getTabY")
    int mineCraftProtocol$tabY(CreativeModeTab tab);
}
