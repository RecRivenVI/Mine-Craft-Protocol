package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void mineCraftProtocol$observeSlotClick(
            Slot slot,
            int slotId,
            int mouseButton,
            ContainerInput input,
            CallbackInfo callbackInfo) {
        NeoForgeRuntime.observeScreenSlotClick(slotId, mouseButton, input.toString());
    }
}
