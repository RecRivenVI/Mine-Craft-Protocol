package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantUiAccessor {
    @Accessor("shopItem")
    int mineCraftProtocol$selectedTrade();

    @Accessor("scrollOff")
    int mineCraftProtocol$scrollOff();

    @Accessor("scrollOff")
    void mineCraftProtocol$scrollOff(int value);
}
