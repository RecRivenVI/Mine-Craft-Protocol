package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.ForgeRuntime;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void mineCraftProtocol$observeMenuDispatch(
            int containerId,
            int slotId,
            int mouseButton,
            ClickType clickType,
            Player player,
            CallbackInfo callbackInfo) {
        ForgeRuntime.observeMenuDispatch(containerId, slotId, clickType.name());
    }
}
