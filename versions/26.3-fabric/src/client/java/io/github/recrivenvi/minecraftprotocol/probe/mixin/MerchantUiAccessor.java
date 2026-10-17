package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantUiAccessor {
    @Accessor("shopItem")
    int minecraftProtocolProbe$selectedTrade();

    @Accessor("scrollOff")
    int minecraftProtocolProbe$scrollOff();

    @Accessor("scrollOff")
    void minecraftProtocolProbe$scrollOff(int value);
}
