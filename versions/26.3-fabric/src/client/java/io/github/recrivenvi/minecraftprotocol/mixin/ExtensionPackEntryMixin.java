package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.client.ExtensionPacksScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public abstract class ExtensionPackEntryMixin implements PackSelectionModel.Entry {
    @Inject(method = "toggleSelection", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$selection(CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof ExtensionPacksScreen screen) {
            var entry = (PackSelectionModel.Entry) (Object) this;
            if (!screen.toggle(entry.getId(), !entry.isSelected())) ci.cancel();
        }
    }

    @Inject(
            method = {"canMoveUp", "canMoveDown"},
            at = @At("HEAD"),
            cancellable = true)
    private void mineCraftProtocol$fixed(CallbackInfoReturnable<Boolean> ci) {
        if (Minecraft.getInstance().gui.screen() instanceof ExtensionPacksScreen)
            ci.setReturnValue(false);
    }

    @Override
    public boolean canSelect() {
        var entry = (PackSelectionModel.Entry) (Object) this;
        if (Minecraft.getInstance().gui.screen() instanceof ExtensionPacksScreen screen) {
            var state = screen.entry(entry.getId());
            return !entry.isSelected() && state != null && state.available();
        }
        return !entry.isSelected();
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$noReorder(int direction, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof ExtensionPacksScreen) ci.cancel();
    }
}
