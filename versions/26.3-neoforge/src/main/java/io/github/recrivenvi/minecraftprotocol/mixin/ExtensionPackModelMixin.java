package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.client.ExtensionPacksScreen;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PackSelectionModel.class)
public abstract class ExtensionPackModelMixin {
    @Inject(
            method = {"getSelected", "getUnselected"},
            at = @At("RETURN"),
            cancellable = true)
    private void mineCraftProtocol$sort(
            CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> ci) {
        if (Minecraft.getInstance().gui.screen() instanceof ExtensionPacksScreen)
            ci.setReturnValue(
                    ci.getReturnValue()
                            .sorted(
                                    Comparator.comparing(
                                            e -> e.getTitle().getString(),
                                            String.CASE_INSENSITIVE_ORDER)));
    }
}
