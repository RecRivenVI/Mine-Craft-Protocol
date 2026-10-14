package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.MerchantScreen$TradeOfferButton")
public interface MerchantTradeUiAccessor {
    @Accessor("index")
    int minecraftProtocolProbe$index();
}
